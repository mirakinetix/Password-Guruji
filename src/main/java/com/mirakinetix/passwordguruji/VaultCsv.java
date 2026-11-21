package com.mirakinetix.passwordguruji;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class VaultCsv {
    private static final List<String> HEADER = List.of("Name","Type","Username","Password","Website","Notes",
            "Folder","Favourite","Tags","CustomFields");
    private static final String MARKER="PGTEXT:";
    private static final List<String> V2_HEADER = List.of("Name [PGCSV2]","Type","Username","Password","Website","Notes","Folder","Favourite","Tags","CustomFields");
    private VaultCsv() {}

    static void exportFile(Path path, List<VaultEntry> entries) throws IOException {
        if(!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")) throw new IOException("Choose a .csv output file.");
        SecureFiles.rejectLink(path);
        if(Files.exists(path)) try(InputStream input=Files.newInputStream(path)) {
            if(Arrays.equals(input.readNBytes(8),new byte[]{'P','G','V','A','U','L','T',0})) throw new IOException("An encrypted vault cannot be replaced with plaintext CSV.");
        }
        if(entries.size()>100_000) throw new IOException("Too many CSV records.");
        WipingBytes bytes=new WipingBytes(); byte[] output=null;
        try {
            BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(bytes,StandardCharsets.UTF_8));
            writeRow(writer,V2_HEADER);
            for(VaultEntry entry:entries) {
                String tags="tags-v2:"+entry.tags().size()+":"+entry.tags().stream().map(value -> Base64.getUrlEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8))).collect(java.util.stream.Collectors.joining("|"));
                List<String> values=List.of(entry.title(),entry.category(),entry.username(),entry.password(),entry.url(),entry.notes(),entry.folder(),Boolean.toString(entry.favorite()),tags,encodeCustomFields(entry.customFields()));
                writeRow(writer,values.stream().map(value -> MARKER+value).toList());
                writer.flush();
                if(bytes.size()>32*1024*1024) throw new IOException("The CSV export is too large.");
            }
            writer.flush();output=bytes.toByteArray();SecureFiles.writeAtomic(path,output,SecureFiles.SYSTEM);
        } finally { if(output!=null) Arrays.fill(output,(byte)0);bytes.wipe(); }
    }
    private static final class WipingBytes extends ByteArrayOutputStream {
        void wipe() { Arrays.fill(buf,(byte)0);reset(); }
    }

    static List<VaultEntry> importFile(Path path) throws IOException {
        List<List<String>> rows;
        byte[] data;
        try(InputStream stream=Files.newInputStream(path)) { data=stream.readNBytes(32*1024*1024+1); }
        try {
            if(data.length>32*1024*1024) throw new IOException("The CSV file is too large.");
            try(Reader reader=new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data),StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)))) { rows=parse(reader); }
        } finally { Arrays.fill(data,(byte)0); }
        if (rows.isEmpty()) return List.of();
        List<String> header = rows.get(0);
        boolean version2=header.equals(V2_HEADER);
        if (!version2 && !header.equals(HEADER)) throw new IOException("CSV format doesn't match Password Guruji export format.");
        List<VaultEntry> entries = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.size() != HEADER.size()) throw new IOException("Invalid CSV row " + (i + 1) + ".");
            if(version2) {
                List<String> decoded=new ArrayList<>();
                for(String cell:row) {
                    if(!cell.startsWith(MARKER)) throw new IOException("Missing CSV v2 text marker.");
                    decoded.add(cell.substring(MARKER.length()));
                }
                row=decoded;
            }
            if(!row.get(7).equals("true")&&!row.get(7).equals("false")) throw new IOException("Invalid favorite value in CSV.");
            Map<String,String> custom = parseCustom(row.get(9));
            List<String> tags = row.get(8).isBlank() ? List.of() :
                    Arrays.stream(row.get(8).split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
            if(version2) {
                if(!row.get(8).startsWith("tags-v2:")) throw new IOException("Invalid CSV tags.");
                String body=row.get(8).substring(8);
                try {
                    int colon=body.indexOf(':');
                    if(colon<1) throw new IllegalArgumentException("Missing tag count");
                    int count=Integer.parseInt(body.substring(0,colon));
                    String encoded=body.substring(colon+1);
                    tags=count==0 && encoded.isEmpty()?List.of():Arrays.stream(encoded.split("\\|",-1)).map(value -> new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8)).toList();
                    if(count!=tags.size()) throw new IllegalArgumentException("Tag count mismatch");
                }
                catch(IllegalArgumentException invalid) { throw new IOException("Invalid CSV tag encoding.",invalid); }
            }
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
                if (split < 1) throw new IOException("Invalid custom fields in CSV.");
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
        boolean quoted = false, closedQuote = false;
        int ch;
        while ((ch = reader.read()) != -1) {
            char c = (char)ch;
            if(field.length()>2_000_000 || row.size()>HEADER.size() || rows.size()>100_000) throw new IOException("CSV limits exceeded.");
            if(closedQuote && c!=',' && c!='\r' && c!='\n') throw new IOException("Unexpected text after a quoted CSV field.");
            if (quoted) {
                if (c == '"') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') field.append('"');
                    else { quoted = false; closedQuote=true; if (next != -1) reader.reset(); }
                } else if(c=='"') throw new IOException("Unexpected quote in CSV field.");
            else field.append(c);
            } else if (c == '"' && field.length() == 0) quoted = true;
            else if (c == ',') { row.add(field.toString()); field.setLength(0); closedQuote=false; }
            else if (c == '\n') {
                if (field.length() > 0 && field.charAt(field.length()-1) == '\r') field.setLength(field.length()-1);
                row.add(field.toString()); field.setLength(0);
                if (!(row.size() == 1 && row.get(0).isEmpty())) rows.add(List.copyOf(row));
                row.clear();closedQuote=false;
            } else if(c=='"') throw new IOException("Unexpected quote in CSV field.");
            else field.append(c);
        }
        if(field.length()>2_000_000 || row.size()>HEADER.size() || rows.size()>100_000) throw new IOException("CSV limits exceeded.");
        if (quoted) throw new IOException("CSV has an unclosed quoted field.");
        if (!row.isEmpty() || field.length() > 0) { row.add(field.toString()); rows.add(List.copyOf(row)); }
        return rows;
    }
}
