package com.mirakinetix.passwordguruji;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class VaultCsv {
    private static final List<String> HEADER = List.of("Name","Type","Username","Password","Website","Notes",
            "Folder","Favourite","Tags","CustomFields");
    private VaultCsv() {}

    static void exportFile(Path path, List<VaultEntry> entries) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writeRow(writer, HEADER);
            for (VaultEntry entry : entries) {
                String tags = String.join("|", entry.tags());
                String custom = encodeCustomFields(entry.customFields());
                writeRow(writer, List.of(entry.title(), entry.category(), entry.username(), entry.password(),
                        entry.url(), entry.notes(), entry.folder(), Boolean.toString(entry.favorite()), tags, custom));
            }
        }
    }

    static List<VaultEntry> importFile(Path path) throws IOException {
        if (Files.size(path) > 32L * 1024 * 1024) throw new IOException("The CSV file is too large.");
        List<List<String>> rows;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { rows = parse(reader); }
        if (rows.isEmpty()) return List.of();
        List<String> header = rows.get(0);
        if (!header.equals(HEADER)) throw new IOException("CSV format doesn't match Password Guruji export format.");
        List<VaultEntry> entries = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.size() != HEADER.size()) throw new IOException("Invalid CSV row " + (i + 1) + ".");
            Map<String,String> custom = parseCustom(row.get(9));
            List<String> tags = row.get(8).isBlank() ? List.of() :
                    Arrays.stream(row.get(8).split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
            entries.add(new VaultEntry(UUID.randomUUID().toString(), row.get(0), row.get(2), row.get(3),
                    row.get(4), row.get(5), row.get(1), row.get(6), Boolean.parseBoolean(row.get(7)),
                    tags, custom, List.of(), System.currentTimeMillis()));
        }
        return entries;
    }

    private static String encodeCustomFields(Map<String,String> fields) {
        StringBuilder encoded = new StringBuilder("base64-v1:");
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        new TreeMap<>(fields).forEach((key, value) -> {
            if (encoded.length() > "base64-v1:".length()) encoded.append('\n');
            encoded.append(encoder.encodeToString(key.getBytes(StandardCharsets.UTF_8)))
                    .append(':')
                    .append(encoder.encodeToString(value.getBytes(StandardCharsets.UTF_8)));
        });
        return encoded.toString();
    }

    private static Map<String,String> parseCustom(String value) throws IOException {
        Map<String,String> result = new LinkedHashMap<>();
        if (value.isEmpty()) return result;
        if (value.startsWith("base64-v1:")) {
            String body = value.substring("base64-v1:".length());
            if (body.isEmpty()) return result;
            Base64.Decoder decoder = Base64.getUrlDecoder();
            for (String line : body.split("\\n", -1)) {
                int split = line.indexOf(':');
                if (split < 1 || split == line.length() - 1) throw new IOException("Invalid custom fields in CSV.");
                try {
                    String key = new String(decoder.decode(line.substring(0, split)), StandardCharsets.UTF_8);
                    String fieldValue = new String(decoder.decode(line.substring(split + 1)), StandardCharsets.UTF_8);
                    if (key.isEmpty() || result.containsKey(key)) throw new IOException("Duplicate or empty custom field in CSV.");
                    result.put(key, fieldValue);
                } catch (IllegalArgumentException ex) {
                    throw new IOException("Invalid custom field encoding in CSV.", ex);
                }
            }
            return result;
        }
        StringBuilder key = new StringBuilder(), fieldValue = new StringBuilder();
        boolean readingValue = false, escaped = false;
        for (int i=0; i<=value.length(); i++) {
            char c = i == value.length() ? '\n' : value.charAt(i);
            if (escaped) { (readingValue ? fieldValue : key).append(c); escaped = false; }
            else if (c == '\\') escaped = true;
            else if (c == '=' && !readingValue) readingValue = true;
            else if (c == '\n') {
                if (key.length() > 0 && readingValue) result.put(key.toString(), fieldValue.toString());
                key.setLength(0); fieldValue.setLength(0); readingValue = false;
            } else (readingValue ? fieldValue : key).append(c);
        }
        return result;
    }

    private static void writeRow(Writer out, List<String> fields) throws IOException {
        for (int i=0; i<fields.size(); i++) {
            if (i > 0) out.write(',');
            out.write('"');
            out.write(fields.get(i).replace("\"", "\"\""));
            out.write('"');
        }
        out.write("\r\n");
    }

    private static List<List<String>> parse(Reader reader) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int ch;
        while ((ch = reader.read()) != -1) {
            char c = (char)ch;
            if (quoted) {
                if (c == '"') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') field.append('"');
                    else { quoted = false; if (next != -1) reader.reset(); }
                } else field.append(c);
            } else if (c == '"' && field.length() == 0) quoted = true;
            else if (c == ',') { row.add(field.toString()); field.setLength(0); }
            else if (c == '\n') {
                if (field.length() > 0 && field.charAt(field.length()-1) == '\r') field.setLength(field.length()-1);
                row.add(field.toString()); field.setLength(0);
                if (!(row.size() == 1 && row.get(0).isEmpty())) rows.add(List.copyOf(row));
                row.clear();
            } else field.append(c);
        }
        if (quoted) throw new IOException("CSV has an unclosed quoted field.");
        if (!row.isEmpty() || field.length() > 0) { row.add(field.toString()); rows.add(List.copyOf(row)); }
        return rows;
    }
}
