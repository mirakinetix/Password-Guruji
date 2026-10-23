package com.mirakinetix.passwordguruji;

import java.util.UUID;

record VaultEntry(String id, String title, String username, String password,
                  String url, String notes, String category, long updatedAt) {
    static VaultEntry create(String title, String username, String password, String url, String notes, String category) {
        return new VaultEntry(UUID.randomUUID().toString(), title, username, password, url, notes, category, System.currentTimeMillis());
    }
    VaultEntry updated(String title, String username, String password, String url, String notes, String category) {
        return new VaultEntry(id, title, username, password, url, notes, category, System.currentTimeMillis());
    }
}
