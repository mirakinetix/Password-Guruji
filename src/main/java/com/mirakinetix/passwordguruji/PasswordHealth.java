package com.mirakinetix.passwordguruji;

import java.util.*;

final class PasswordHealth {
    private static final Set<String> COMMON = Set.of("password", "password1", "123456", "12345678",
            "qwerty", "qwerty123", "letmein", "welcome", "admin", "iloveyou", "monkey");
    private PasswordHealth() {}

    static Report analyze(List<VaultEntry> entries) {
        List<String> weak = new ArrayList<>();
        Map<String, List<String>> byPassword = new HashMap<>();
        for (VaultEntry entry : entries) {
            String password = entry.password();
            if (password.isEmpty()) { if (entry.category().equals("Login")) weak.add(entry.title()); continue; }
            byPassword.computeIfAbsent(password, k -> new ArrayList<>()).add(entry.title());
            String normalized = password.toLowerCase(Locale.ROOT);
            if (password.length() < 12 || COMMON.contains(normalized) || repeated(password) || sequence(password)
                    || (!entry.username().isBlank() && normalized.equals(entry.username().toLowerCase(Locale.ROOT)))) {
                weak.add(entry.title());
            }
        }
        List<List<String>> reused = byPassword.values().stream().filter(names -> names.size() > 1).toList();
        return new Report(entries.size(), List.copyOf(weak), reused);
    }

    private static boolean repeated(String value) {
        if (value.length() < 6) return false;
        for (int i=1; i<value.length(); i++) if (value.charAt(i) != value.charAt(0)) return false;
        return true;
    }

    private static boolean sequence(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("123456") || lower.contains("abcdef") || lower.contains("qwerty");
    }

    record Report(int records, List<String> weakEntries, List<List<String>> reusedGroups) {
        int weakCount() { return weakEntries.size(); }
        int reusedCount() { return reusedGroups.stream().mapToInt(List::size).sum(); }
    }
}
