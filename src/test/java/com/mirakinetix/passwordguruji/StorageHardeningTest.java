package com.mirakinetix.passwordguruji;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.channels.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class StorageHardeningTest {
    @TempDir Path dir;
    final char[] password="synthetic-strong-master".toCharArray();
    VaultEntry record(String value) { return VaultEntry.create("dummy","test@example.invalid",value,"","","Login"); }
    @Test void masterPolicyPreservesLegacyUnlockButStrengthensNewPasswords() throws Exception {
        assertFalse(MasterPasswordPolicy.validNew("123456".toCharArray(),false));
        assertTrue(MasterPasswordPolicy.validNew("123456".toCharArray(),true));
        assertFalse(MasterPasswordPolicy.validNew("😀😀😀😀😀😀😀😀".toCharArray(),false));
        assertTrue(MasterPasswordPolicy.validNew("fifteen-or-more-dummy".toCharArray(),false));
        assertFalse(MasterPasswordPolicy.validNew(new char[1025],false));
        VaultStore s=new VaultStore(dir.resolve("legacy.pgv"));s.create("123456".toCharArray());
        assertTrue(s.load("123456".toCharArray()).isEmpty());
    }
    @Test void staleWriterCannotLoseAnotherInstancesChanges() throws Exception {
        Path file=dir.resolve("vault.pgv");VaultStore one=new VaultStore(file);one.create(password);
        VaultStore two=new VaultStore(file);two.load(password);
        one.save(password,List.of(record("new")));
        assertThrows(IOException.class,()->two.save(password,List.of(record("stale"))));
        assertEquals("new",new VaultStore(file).load(password).getFirst().password());
        assertThrows(IOException.class,()->new VaultStore(file).save(password,List.of()));
    }
    @Test void externallyRemovedVaultIsNotSilentlyRecreated() throws Exception {
        VaultStore s=new VaultStore(dir.resolve("dummy.pgv"));s.create(password);
        Files.move(s.path(),dir.resolve("moved-dummy.pgv"));
        assertThrows(IOException.class,()->s.save(password,List.of()));assertFalse(s.exists());
    }
    @Test void writeFailurePreservesOriginalAndRemovesOwnedTemporaryFile() throws Exception {
        fault(false,false);
    }
    @Test void unsupportedAtomicMoveFailsClosed() throws Exception { fault(true,false); }
    @Test void replacePermissionFailurePreservesOriginal() throws Exception { fault(true,true); }
    void fault(boolean onReplace,boolean denied) throws Exception {
        Path file=dir.resolve("dummy.pgv");VaultStore original=new VaultStore(file);original.save(password,List.of(record("original")));
        byte[] before=Files.readAllBytes(file);
        SecureFiles.Operations failure=new SecureFiles.Operations() {
            public void write(Path temp,byte[] bytes) throws IOException {
                if(onReplace) SecureFiles.SYSTEM.write(temp,bytes);
                else { Files.write(temp,Arrays.copyOf(bytes,20));throw new FileSystemException(temp.toString(),null,"No space left on device (fault injection)"); }
            }
            public void replace(Path temp,Path destination) throws IOException {
                if(denied) throw new AccessDeniedException(destination.toString());
                throw new AtomicMoveNotSupportedException(temp.toString(),destination.toString(),"injected unsupported filesystem");
            }
        };
        VaultStore s=new VaultStore(file,failure);s.load(password);
        assertThrows(IOException.class,()->s.save(password,List.of(record("replacement"))));
        assertArrayEquals(before,Files.readAllBytes(file));
        try(var paths=Files.list(dir)) { assertFalse(paths.anyMatch(p->p.toString().endsWith(".pending"))); }
        assertEquals("original",original.load(password).getFirst().password());
    }
    @Test void actualReadOnlyFileIsRejected() throws Exception {
        VaultStore s=new VaultStore(dir.resolve("read-only.pgv"));s.create(password);
        var dos=Files.getFileAttributeView(s.path(),DosFileAttributeView.class);
        if(System.getProperty("os.name").startsWith("Windows")) {
            dos.setReadOnly(true);
            try { assertThrows(AccessDeniedException.class,()->s.save(password,List.of(record("new")))); }
            finally { dos.setReadOnly(false); }
        } else {
            Files.setPosixFilePermissions(s.path(),PosixFilePermissions.fromString("r--------"));
            try { assertThrows(AccessDeniedException.class,()->s.save(password,List.of(record("new")))); }
            finally { Files.setPosixFilePermissions(s.path(),PosixFilePermissions.fromString("rw-------")); }
        }
    }
    @Test void vaultBackupAndLockHavePrivatePermissions() throws Exception {
        VaultStore s=new VaultStore(dir.resolve("vault.pgv"));s.create(password);
        Path backup=dir.resolve("backup.pgv");s.backupTo(backup);
        assertTrue(new VaultStore(backup).load(password).isEmpty());
        for(Path path:List.of(s.path(),backup,dir.resolve("vault.pgv.lock"))) {
            var posix=Files.getFileAttributeView(path,PosixFileAttributeView.class);
            if(posix!=null) assertEquals(PosixFilePermissions.fromString("rw-------"),posix.readAttributes().permissions());
            else {
                var acl=Files.getFileAttributeView(path,AclFileAttributeView.class).getAcl();
                assertEquals(1,acl.size());assertEquals(AclEntryType.ALLOW,acl.getFirst().type());
                assertTrue(acl.getFirst().principal().getName().toLowerCase(Locale.ROOT).endsWith(System.getProperty("user.name").toLowerCase(Locale.ROOT)));
            }
        }
    }
    @Test void childProcessLockIsReleasedOnUnexpectedExit() throws Exception {
        Path file=dir.resolve("vault.pgv");VaultStore s=new VaultStore(file);s.create(password);
        Process child=child("lock",file);
        try {
            assertEquals("LOCKED",new BufferedReader(new InputStreamReader(child.getInputStream())).readLine());
            assertThrows(IOException.class,()->s.save(password,List.of()));
        } finally { child.destroyForcibly();assertTrue(child.waitFor(10,TimeUnit.SECONDS)); }
        s.save(password,List.of(record("after-exit")));
        assertEquals("after-exit",s.load(password).getFirst().password());
    }
    @Test void crashBeforeAtomicCommitLeavesOriginalReadable() throws Exception {
        Path file=dir.resolve("vault.pgv");VaultStore s=new VaultStore(file);s.save(password,List.of(record("original")));
        byte[] before=Files.readAllBytes(file);Process child=child("crash",file);
        assertTrue(child.waitFor(30,TimeUnit.SECONDS));assertEquals(73,child.exitValue());
        assertArrayEquals(before,Files.readAllBytes(file));assertEquals("original",s.load(password).getFirst().password());
        s.save(password,List.of(record("recovered")));assertEquals("recovered",s.load(password).getFirst().password());
    }
    Process child(String mode,Path file) throws IOException {
        String executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        return new ProcessBuilder(executable,"-cp",System.getProperty("java.class.path"),WriterProcess.class.getName(),mode,file.toString()).redirectErrorStream(true).start();
    }
    public static final class WriterProcess {
        public static void main(String[] args) throws Exception {
            Path file=Path.of(args[1]);
            if(args[0].equals("lock")) {
                try(FileChannel channel=FileChannel.open(file.resolveSibling(file.getFileName()+".lock"),StandardOpenOption.WRITE);FileLock lock=channel.lock()) {
                    System.out.println("LOCKED");System.out.flush();System.in.read();
                }
            } else {
                SecureFiles.Operations crash=new SecureFiles.Operations() {
                    public void write(Path temporary,byte[] bytes) throws IOException { SecureFiles.SYSTEM.write(temporary,bytes);Runtime.getRuntime().halt(73); }
                    public void replace(Path temporary,Path destination) { throw new AssertionError("Must not reach commit"); }
                };
                VaultStore store=new VaultStore(file,crash);char[] p="synthetic-strong-master".toCharArray();store.load(p);store.save(p,List.of());
            }
        }
    }
    @Test void csvFormulaTextAndDelimitersRoundTripWithoutExecutableCells() throws Exception {
        Path file=dir.resolve("synthetic.csv");
        VaultEntry entry=VaultEntry.create("=1+1","\t@dummy","+dummy","-dummy","\n=not-a-command","Login","",true,List.of("a|b"," spaced ","日本語", ""),Map.of("=field","", "normal","=dummy"));
        VaultCsv.exportFile(file,List.of(entry));String raw=Files.readString(file);
        assertTrue(raw.contains("PGTEXT:=1+1"));assertFalse(raw.contains("\"=1+1\""));
        VaultEntry restored=VaultCsv.importFile(file).getFirst();
        assertEquals(entry.title(),restored.title());assertEquals(entry.password(),restored.password());
        assertEquals(entry.notes(),restored.notes());assertEquals(entry.tags(),restored.tags());assertEquals(entry.customFields(),restored.customFields());
    }
    @Test void malformedCsvIsRejectedWithoutChangingVault() throws Exception {
        Path csv=dir.resolve("bad.csv");Files.writeString(csv,"\"Name\"unexpected,Type\n");
        assertThrows(IOException.class,()->VaultCsv.importFile(csv));
    }
}
