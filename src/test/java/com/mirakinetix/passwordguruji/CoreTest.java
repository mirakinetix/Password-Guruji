package com.mirakinetix.passwordguruji;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
    @TempDir Path dir;
    private final char[] password = "synthetic-test-password-2026".toCharArray();
    private VaultEntry entry() {
        return VaultEntry.create("Fictional café", "test@example.invalid", "dummy-secret-ONLY",
            "https://example.invalid", "quoted \"note\"\nsecond line", "Login", "Tests", true,
            List.of("test", "日本語"), Map.of("empty", "", "TOTP", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"));
    }
    @Test void createsAndReopensEmptyVault() throws Exception {
        VaultStore s = new VaultStore(dir.resolve("new.pgv")); s.create(password);
        assertTrue(s.exists()); assertTrue(s.load(password).isEmpty());
        assertThrows(FileAlreadyExistsException.class, () -> s.create(password));
    }
    @Test void roundTripsAllFieldsHistoryAndRecycleBin() throws Exception {
        VaultEntry original = entry();
        VaultEntry updated = original.updated("changed", "new user", "new dummy secret", "", "", "Login").withArchived(true);
        VaultStore s = new VaultStore(dir.resolve("vault.pgv")); s.save(password, List.of(updated));
        VaultEntry actual = s.load(password).getFirst();
        assertEquals(updated.id(), actual.id()); assertEquals("changed", actual.title());
        assertEquals(updated.username(), actual.username()); assertEquals(updated.password(), actual.password());
        assertEquals(updated.customFields(), actual.customFields()); assertEquals(updated.tags(), actual.tags());
        assertEquals(updated.folder(), actual.folder()); assertTrue(actual.favorite()); assertTrue(actual.archived());
        assertEquals(original.password(), actual.history().getFirst().password());
        assertEquals(updated.updatedAt(), actual.updatedAt());
    }
    @Test void rejectsWrongPasswordWithoutChangingFile() throws Exception {
        VaultStore s = new VaultStore(dir.resolve("vault.pgv")); s.save(password, List.of(entry()));
        byte[] before = Files.readAllBytes(s.path());
        assertThrows(GeneralSecurityException.class, () -> s.load("wrong".toCharArray()));
        assertArrayEquals(before, Files.readAllBytes(s.path()));
    }
    @Test void encryptsAndRandomizesEverySave() throws Exception {
        VaultStore s = new VaultStore(dir.resolve("vault.pgv")); s.save(password, List.of(entry()));
        byte[] first = Files.readAllBytes(s.path());
        assertFalse(new String(first, StandardCharsets.ISO_8859_1).contains(entry().password()));
        s.save(password, List.of(entry())); assertFalse(Arrays.equals(first, Files.readAllBytes(s.path())));
        try (var files = Files.list(dir)) { assertEquals(1, files.count()); }
    }
    @ParameterizedTest @CsvSource({"12", "16", "32", "48", "70"})
    void authenticatesHeaderAndCiphertext(int offset) throws Exception {
        VaultStore s = new VaultStore(dir.resolve("vault.pgv")); s.save(password, List.of(entry()));
        byte[] bytes = Files.readAllBytes(s.path()); bytes[offset] ^= 1; Files.write(s.path(), bytes);
        assertThrows(Exception.class, () -> s.load(password));
    }
    @Test void rejectsTruncationAndUnknownFormat() throws Exception {
        VaultStore s = new VaultStore(dir.resolve("vault.pgv"));
        Files.write(s.path(), new byte[12]); assertThrows(java.io.IOException.class, () -> s.load(password));
        Files.write(s.path(), new byte[100]); assertThrows(java.io.IOException.class, () -> s.load(password));
    }
    @Test void encryptedBackupRestoresUnderNewPassword() throws Exception {
        VaultStore s = new VaultStore(dir.resolve("vault.pgv")); s.save(password, List.of(entry()));
        Path backup = dir.resolve("backup.pgv"); Files.copy(s.path(), backup);
        List<VaultEntry> recovered = new VaultStore(backup).load(password);
        char[] replacement = "different-dummy-password".toCharArray(); s.save(replacement, recovered);
        assertEquals(entry().password(), s.load(replacement).getFirst().password());
        assertThrows(GeneralSecurityException.class, () -> s.load(password));
        assertEquals(entry().password(), new VaultStore(backup).load(password).getFirst().password());
    }
    @Test void ownerOnlyPermissionsWhereSupported() throws Exception {
        VaultStore s = new VaultStore(dir.resolve("vault.pgv")); s.create(password);
        if (Files.getFileStore(dir).supportsFileAttributeView("posix"))
            assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(s.path()));
        else assertTrue(Files.isReadable(s.path()), "Windows ACL isolation needs separate assessment");
    }
    @Test void csvPreservesQuotesUnicodeAndEmptyCustomFields() throws Exception {
        Path csv = dir.resolve("synthetic.csv"); VaultCsv.exportFile(csv, List.of(entry()));
        VaultEntry actual = VaultCsv.importFile(csv).getFirst();
        assertEquals(entry().title(), actual.title()); assertEquals(entry().notes(), actual.notes());
        assertEquals(entry().password(), actual.password()); assertEquals(entry().customFields(), actual.customFields());
    }
    @Test void csvRejectsInvalidHeader() throws Exception {
        Path csv = dir.resolve("invalid.csv"); Files.writeString(csv, "unexpected,header\n");
        assertThrows(java.io.IOException.class, () -> VaultCsv.importFile(csv));
    }
    @Test void generatedPasswordsSatisfyOptions() {
        for (int i = 0; i < 100; i++) {
            String value = PasswordGenerator.generate(24, true, true, true, true, "Aa2!");
            assertEquals(24, value.length()); assertTrue(value.matches(".*[A-Z].*"));
            assertTrue(value.matches(".*[a-z].*")); assertTrue(value.matches(".*[3-9].*"));
            assertTrue(value.chars().anyMatch(c -> !Character.isLetterOrDigit(c)));
            assertTrue(value.chars().noneMatch(c -> "Aa2!".indexOf(c) >= 0));
        }
    }
    @Test void generatorRejectsInvalidOptions() {
        assertThrows(IllegalArgumentException.class, () -> PasswordGenerator.generate(12,false,false,false,false));
        assertThrows(IllegalArgumentException.class, () -> PasswordGenerator.generate(3,true,true,true,true));
        assertThrows(IllegalArgumentException.class, () -> PasswordGenerator.generate(129,true,true,true,true));
        assertThrows(IllegalArgumentException.class, () -> PasswordGenerator.generate(12,false,false,true,false,"23456789"));
    }
    @Test void passphraseUsesBundledWordList() {
        assertEquals(7776, PasswordGenerator.wordCount());
        assertEquals(6, PasswordGenerator.passphrase(6, "-").split("-").length);
        assertThrows(IllegalArgumentException.class, () -> PasswordGenerator.passphrase(5, "-"));
    }
    // RFC 6238 SHA-1 vectors, reduced to this application's six displayed digits.
    @ParameterizedTest @CsvSource({"59,287082", "1111111109,081804", "1111111111,050471", "1234567890,005924", "2000000000,279037", "20000000000,353130"})
    void totpMatchesPublishedVectors(long seconds, String expected) throws Exception {
        assertEquals(expected, Totp.codeAt("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", seconds));
    }
    @Test void totpRejectsInvalidSecrets() {
        assertThrows(IllegalArgumentException.class, () -> Totp.codeAt("", 59));
        assertThrows(IllegalArgumentException.class, () -> Totp.codeAt("INVALID!", 59));
    }
    @Test void historyIsBoundedAndRestorable() {
        VaultEntry e = entry();
        for (int i=0;i<20;i++) e=e.updated("title","user","dummy-"+i,"","","Login");
        assertEquals(10,e.history().size());
        assertEquals("dummy-18",e.restoreRevision(e.history().getLast()).password());
    }
}
