package com.mirakinetix.passwordguruji;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FinalSafetyTest {
    @TempDir Path directory;
    private final char[] password = "synthetic-password-123456".toCharArray();

    private VaultStore vault(String name) throws Exception {
        VaultStore store = new VaultStore(directory.resolve(name));
        store.create(password);
        return store;
    }

    @Test void backupCannotReplaceItsPersistentLockFile() throws Exception {
        VaultStore store = vault("source.pgv");
        byte[] original = Files.readAllBytes(store.path());
        Path lock = directory.resolve("source.pgv.lock");
        byte[] lockContents = Files.readAllBytes(lock);
        assertThrows(IOException.class, () -> store.backupTo(lock));
        assertArrayEquals(original, Files.readAllBytes(store.path()));
        assertArrayEquals(lockContents, Files.readAllBytes(lock));
        store.save(password, List.of());
        assertTrue(new VaultStore(store.path()).load(password).isEmpty());
    }

    @Test void backupCannotReplaceAnotherVaultLockFile() throws Exception {
        VaultStore store = vault("source.pgv");
        Path lock = directory.resolve("other.pgv.lock");
        Files.writeString(lock, "synthetic lock sentinel");
        assertThrows(IOException.class, () -> store.backupTo(lock));
        assertEquals("synthetic lock sentinel", Files.readString(lock));
    }

    @Test void csvExportCannotDestroyEncryptedVaultWithCsvExtension() throws Exception {
        VaultStore store = vault("misleading.csv");
        byte[] original = Files.readAllBytes(store.path());
        assertThrows(IOException.class, () -> VaultCsv.exportFile(store.path(), List.of()));
        assertArrayEquals(original, Files.readAllBytes(store.path()));
        assertTrue(new VaultStore(store.path()).load(password).isEmpty());
    }

    @Test void symbolicLinkDestinationsCannotBeOverwritten() throws Exception {
        Path target = directory.resolve("sentinel.txt");
        Files.writeString(target, "untouched synthetic sentinel");
        Path link = directory.resolve("linked.csv");
        try { Files.createSymbolicLink(link, target); }
        catch (UnsupportedOperationException unavailable) {
            assumeTrue(false, "Filesystem does not support symbolic links: " + unavailable);
        } catch (FileSystemException unavailable) {
            String reason = String.valueOf(unavailable.getReason()).toLowerCase(java.util.Locale.ROOT);
            if (reason.contains("privilege") || reason.contains("not supported"))
                assumeTrue(false, "Symbolic-link creation unavailable: " + unavailable.getReason());
            throw unavailable;
        }
        VaultStore store = vault("source.pgv");
        assertThrows(IOException.class, () -> store.backupTo(link));
        assertThrows(IOException.class, () -> VaultCsv.exportFile(link, List.of()));
        assertEquals("untouched synthetic sentinel", Files.readString(target));
        assertTrue(Files.isSymbolicLink(link));
    }

    @Test void hardLinkDestinationsRejectOnUnixOrPreserveOriginalDuringAtomicReplacement() throws Exception {
        Path target = directory.resolve("sentinel.txt");
        Files.writeString(target, "untouched synthetic sentinel");
        Path link = Files.createLink(directory.resolve("linked.csv"), target);
        VaultStore store = vault("source.pgv");
        assertThrows(IOException.class, () -> store.backupTo(link));
        assertTrue(Files.isSameFile(target, link));
        if (Files.getFileStore(directory).supportsFileAttributeView("unix")) {
            assertThrows(IOException.class, () -> VaultCsv.exportFile(link, List.of()));
            assertTrue(Files.isSameFile(target, link));
        } else {
            VaultCsv.exportFile(link, List.of());
            assertFalse(Files.isSameFile(target, link));
            assertTrue(VaultCsv.importFile(link).isEmpty());
        }
        assertEquals("untouched synthetic sentinel", Files.readString(target));
    }

    @Test void backupPreservesSourceAndRefusesToReplaceAnotherVault() throws Exception {
        VaultStore source = vault("source.pgv");
        VaultStore other = vault("other.pgv");
        byte[] sourceBytes = Files.readAllBytes(source.path());
        byte[] otherBytes = Files.readAllBytes(other.path());
        assertThrows(FileAlreadyExistsException.class, () -> source.backupTo(other.path()));
        assertArrayEquals(otherBytes, Files.readAllBytes(other.path()));
        Path newBackup = directory.resolve("new.pgv");
        source.backupTo(newBackup);
        assertArrayEquals(sourceBytes, Files.readAllBytes(newBackup));
        assertArrayEquals(sourceBytes, Files.readAllBytes(source.path()));
    }

    @Test void backupDoesNotReplaceAFileThatAppearsDuringPublication() throws Exception {
        Path sourcePath = vault("source.pgv").path();
        Path destination = directory.resolve("another.pgv");
        byte[] otherBytes = Files.readAllBytes(vault("reserve.pgv").path());
        SecureFiles.Operations racingWriter = new SecureFiles.Operations() {
            public void write(Path temporary, byte[] content) throws IOException {
                SecureFiles.SYSTEM.write(temporary,content);
                Files.write(destination,otherBytes,StandardOpenOption.CREATE_NEW);
            }
            public void replace(Path temporary, Path target) {
                fail("Backups must never use replacement.");
            }
        };
        VaultStore source = new VaultStore(sourcePath,racingWriter);
        source.load(password);
        assertThrows(FileAlreadyExistsException.class, () -> source.backupTo(destination));
        assertArrayEquals(otherBytes, Files.readAllBytes(destination));
    }

    @Test void recoveryAuthenticatesReadOnlyBackupWithoutCreatingSidecar() throws Exception {
        VaultStore source = vault("source.pgv");
        Path media = Files.createDirectory(directory.resolve("media"));
        Path file = media.resolve("backup.pgv");
        source.backupTo(file);
        byte[] original = Files.readAllBytes(file);
        var posix = Files.getFileAttributeView(media,java.nio.file.attribute.PosixFileAttributeView.class);
        var dos = Files.getFileAttributeView(file,java.nio.file.attribute.DosFileAttributeView.class);
        if (posix != null) Files.setPosixFilePermissions(media,java.nio.file.attribute.PosixFilePermissions.fromString("r-x------"));
        else dos.setReadOnly(true);
        try {
            VaultStore recovery = new VaultStore(file);
            assertThrows(javax.crypto.AEADBadTagException.class, () -> recovery.readBackup("wrong".toCharArray()));
            assertTrue(recovery.readBackup(password).isEmpty());
            assertArrayEquals(original,Files.readAllBytes(file));
            assertFalse(Files.exists(media.resolve("backup.pgv.lock")));
        } finally {
            if (posix != null) Files.setPosixFilePermissions(media,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
            else dos.setReadOnly(false);
        }
    }

    @Tag("desktop")
    @Test void lockingClearsSecretsEvenAfterAnotherInstanceWrites() throws Exception {
        assertFalse(GraphicsEnvironment.isHeadless(), "Desktop test requires a display");
        Preferences preferences = Preferences.userRoot().node("password-guruji-tests/" + UUID.randomUUID());
        Throwable[] failure = new Throwable[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                PasswordGurujiApp app = null;
                try {
                    Path path = directory.resolve("synthetic.pgv");
                    app = new PasswordGurujiApp(new String[]{"--sample-vault", path.toString()}, preferences);
                    DesktopTest.invoke(app, "launch");
                    Container unlock = (Container) DesktopTest.field(app, "unlockPage");
                    DesktopTest.descendants(unlock).stream().filter(c -> c instanceof JPasswordField)
                            .forEach(c -> ((JPasswordField)c).setText(new String(password)));
                    DesktopTest.click(unlock, "Create my vault");
                    assertEquals(false, DesktopTest.field(app, "locked"));
                    char[] heldPassword = (char[]) DesktopTest.field(app, "masterPassword");
                    VaultStore external = new VaultStore(path);
                    external.load(password);
                    external.save(password, List.of(VaultEntry.create("External writer", "dummy", "123456", "", "", "Login")));
                    byte[] externalRevision = Files.readAllBytes(path);
                    DesktopTest.invoke(app, "lockVault");
                    assertEquals(true, DesktopTest.field(app, "locked"));
                    assertNull(DesktopTest.field(app, "masterPassword"));
                    assertArrayEquals(new char[heldPassword.length], heldPassword);
                    assertTrue(((List<?>)DesktopTest.field(app, "entries")).isEmpty());
                    assertArrayEquals(externalRevision, Files.readAllBytes(path));
                    assertEquals("External writer", new VaultStore(path).load(password).getFirst().title());
                } catch (Throwable error) { failure[0] = error; }
                finally {
                    if (app != null) try {
                        ((javax.swing.Timer)DesktopTest.field(app, "idleTimer")).stop();
                        ((JFrame)DesktopTest.field(app, "frame")).dispose();
                    } catch (Exception cleanup) { if (failure[0] == null) failure[0] = cleanup; }
                }
            });
        } finally { preferences.removeNode(); preferences.flush(); }
        if (failure[0] != null) throw new AssertionError("Conflict lock lifecycle failed", failure[0]);
    }
}
