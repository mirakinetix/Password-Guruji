package com.mirakinetix.passwordguruji;

import java.nio.file.*;
import java.util.*;

/** Generates fictional demonstration data; never reads an existing user vault. */
public final class CreateSampleVault {
    public static void main(String[] args) throws Exception {
        Path destination = Path.of(args[0]);
        if (Files.exists(destination)) throw new IllegalStateException("Refusing to replace an existing sample vault.");
        List<VaultEntry> records = new ArrayList<>();
        String[] types = {"Login", "Secure note", "Identity", "Payment", "Recovery code", "License", "Other"};
        for (String type : types) {
            records.add(VaultEntry.create("SAMPLE - " + type, "demo@example.invalid", "123456",
                    "https://example.invalid", "Fictional demonstration only. Never use these credentials.\nSecond sample note line.",
                    type, type.equals("Login") ? "Personal" : "Examples", type.equals("Login"),
                    List.of("sample", "fictional"), Map.of("Sample field", "Example value", "Reference", "DEMO-001")));
        }
        records.add(VaultEntry.create("SAMPLE - Authenticator", "demo@example.invalid", "123456", "",
                "Public test seed, never use for a real account.", "Login", "Work", true,
                List.of("sample", "totp"), Map.of("TOTP secret", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")));
        VaultEntry history = VaultEntry.create("SAMPLE - History", "old@example.invalid", "123456", "", "Original notes", "Login");
        history = history.revised(history.title(), "demo@example.invalid", "123456", "", "Updated notes: restore the earlier snapshot to compare.",
                "Login", "Work", false, List.of("sample", "history"), Map.of("Revision example", "Updated"));
        records.add(history);
        records.add(VaultEntry.create("SAMPLE - Deleted record", "demo@example.invalid", "123456", "",
                "Restore this disposable record from Recycle bin.", "Login").withArchived(true));
        char[] password = "123456".toCharArray();
        try {
            VaultStore store = new VaultStore(destination);
            store.save(password, records);
            List<VaultEntry> loaded = store.load(password);
            if (loaded.size() != 10 || loaded.stream().noneMatch(VaultEntry::archived)
                    || loaded.stream().noneMatch(e -> !e.history().isEmpty())
                    || loaded.stream().anyMatch(e -> !e.password().equals("123456")))
                throw new IllegalStateException("Sample vault verification failed.");
            if (loaded.stream().map(VaultEntry::category).distinct().count() != types.length)
                throw new IllegalStateException("Sample record types are missing.");
            VaultEntry savedHistory = loaded.stream().filter(e -> e.title().equals("SAMPLE - History")).findFirst().orElseThrow();
            if (!savedHistory.restoreRevision(savedHistory.history().get(0)).username().equals("old@example.invalid"))
                throw new IllegalStateException("History did not preserve the previous record values.");
            Totp.currentCode(loaded.stream().filter(e -> e.title().equals("SAMPLE - Authenticator"))
                    .findFirst().orElseThrow().customFields().get("TOTP secret"));
            VaultCsv.exportFile(destination.resolveSibling("sample-data.csv"), loaded);
            Files.copy(destination, destination.resolveSibling("sample-backup.pgv"));
            System.out.println("Verified 10 fictional records, all 7 types, history, and recycle bin.");
        } finally { Arrays.fill(password, '\0'); }
    }
}
