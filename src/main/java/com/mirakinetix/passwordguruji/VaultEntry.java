package com.mirakinetix.passwordguruji;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class VaultEntry {
    private final String id, title, username, password, url, notes, category, folder;
    private final boolean favorite, archived;
    private final List<String> tags;
    private final Map<String, String> customFields;
    private final List<Revision> history;
    private final long updatedAt;

    VaultEntry(String id, String title, String username, String password, String url,
               String notes, String category, long updatedAt) {
        this(id, title, username, password, url, notes, category, "", false, false,
                List.of(), Map.of(), List.of(), updatedAt);
    }

    VaultEntry(String id, String title, String username, String password, String url, String notes,
               String category, String folder, boolean favorite, List<String> tags,
               Map<String, String> customFields, List<Revision> history, long updatedAt) {
        this(id, title, username, password, url, notes, category, folder, favorite, false,
                tags, customFields, history, updatedAt);
    }

    VaultEntry(String id, String title, String username, String password, String url, String notes,
               String category, String folder, boolean favorite, boolean archived, List<String> tags,
               Map<String, String> customFields, List<Revision> history, long updatedAt) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.title = safe(title);
        this.username = safe(username);
        this.password = safe(password);
        this.url = safe(url);
        this.notes = safe(notes);
        this.category = category == null || category.isBlank() ? "Login" : category;
        this.folder = safe(folder);
        this.favorite = favorite;
        this.archived = archived;
        this.tags = List.copyOf(tags == null ? List.of() : tags);
        this.customFields = Map.copyOf(customFields == null ? Map.of() : customFields);
        this.history = List.copyOf(history == null ? List.of() : history);
        this.updatedAt = updatedAt;
    }

    static VaultEntry create(String title, String username, String password, String url, String notes, String category) {
        return create(title, username, password, url, notes, category, "", false, List.of(), Map.of());
    }

    static VaultEntry create(String title, String username, String password, String url, String notes,
                             String category, String folder, boolean favorite, List<String> tags,
                             Map<String, String> fields) {
        return new VaultEntry(UUID.randomUUID().toString(), title, username, password, url, notes, category,
                folder, favorite, false, tags, fields, List.of(), System.currentTimeMillis());
    }

    VaultEntry updated(String title, String username, String password, String url, String notes, String category) {
        return revised(title, username, password, url, notes, category, folder, favorite, tags, customFields);
    }

    VaultEntry revised(String title, String username, String password, String url, String notes, String category,
                       String nextFolder, boolean nextFavorite, List<String> nextTags, Map<String, String> nextFields) {
        List<Revision> nextHistory = new ArrayList<>(history);
        if (!password.isEmpty() || !notes.isEmpty() || !username.isEmpty() || !customFields.isEmpty()) {
            nextHistory.add(new Revision(username, password, url, notes, customFields, updatedAt));
            while (nextHistory.size() > 10) nextHistory.remove(0);
        }
        return new VaultEntry(id, title, username, password, url, notes, category, nextFolder,
                nextFavorite, archived, nextTags, nextFields, nextHistory, System.currentTimeMillis());
    }

    VaultEntry withOrganization(String nextFolder, boolean nextFavorite, List<String> nextTags) {
        if (folder.equals(nextFolder) && favorite == nextFavorite && tags.equals(nextTags)) return this;
        List<Revision> nextHistory = new ArrayList<>(history);
        nextHistory.add(new Revision(username, password, url, notes, customFields, updatedAt));
        while (nextHistory.size() > 10) nextHistory.remove(0);
        return new VaultEntry(id, title, username, password, url, notes, category, nextFolder,
                nextFavorite, archived, nextTags, customFields, nextHistory, System.currentTimeMillis());
    }

    VaultEntry withArchived(boolean nextArchived) {
        if (archived == nextArchived) return this;
        return new VaultEntry(id, title, username, password, url, notes, category, folder,
                favorite, nextArchived, tags, customFields, history, System.currentTimeMillis());
    }

    VaultEntry restoreRevision(Revision revision) {
        List<Revision> nextHistory = new ArrayList<>(history);
        nextHistory.add(new Revision(username, password, url, notes, customFields, updatedAt));
        while (nextHistory.size() > 10) nextHistory.remove(0);
        return new VaultEntry(id, title, revision.username(), revision.password(), revision.url(),
                revision.notes(), category, folder, favorite, archived, tags, revision.customFields(),
                nextHistory, System.currentTimeMillis());
    }

    String id() { return id; }
    String title() { return title; }
    String username() { return username; }
    String password() { return password; }
    String url() { return url; }
    String notes() { return notes; }
    String category() { return category; }
    String folder() { return folder; }
    boolean favorite() { return favorite; }
    boolean archived() { return archived; }
    List<String> tags() { return tags; }
    Map<String, String> customFields() { return customFields; }
    List<Revision> history() { return history; }
    long updatedAt() { return updatedAt; }

    private static String safe(String value) { return value == null ? "" : value; }

    record Revision(String username, String password, String url, String notes,
                    Map<String, String> customFields, long timestamp) {
        Revision {
            username = safe(username);
            password = safe(password);
            url = safe(url);
            notes = safe(notes);
            customFields = Map.copyOf(customFields == null ? Map.of() : customFields);
        }
    }
}
