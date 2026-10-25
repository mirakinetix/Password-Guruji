package com.mirakinetix.passwordguruji;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.*;

final class VaultStore {
    private static final byte[] MAGIC = {'P','G','V','A','U','L','T',0};
    private static final int VERSION_1 = 1, VERSION_2 = 2, VERSION = 3;
    private static final int ITERATIONS = 600_000, SALT_LENGTH = 16, NONCE_LENGTH = 12;
    private static final int MAX_FILE_BYTES = 64 * 1024 * 1024, MAX_FIELD_BYTES = 2_000_000;
    private static final int MAX_RECORDS = 100_000, MAX_MAP_FIELDS = 512, MAX_TAGS = 128, MAX_HISTORY = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Path path;

    VaultStore(Path path) { this.path = path.toAbsolutePath().normalize(); }
    Path path() { return path; }
    boolean exists() { return Files.isRegularFile(path); }

    void create(char[] password) throws IOException, GeneralSecurityException {
        if (exists()) throw new FileAlreadyExistsException(path.toString());
        save(password, List.of());
    }

    List<VaultEntry> load(char[] password) throws IOException, GeneralSecurityException {
        long size = Files.size(path);
        if (size < MAGIC.length + 4 + SALT_LENGTH + NONCE_LENGTH + 16 || size > MAX_FILE_BYTES)
            throw new IOException("The vault file has an invalid size.");
        byte[] file = Files.readAllBytes(path);
        ByteBuffer in = ByteBuffer.wrap(file);
        byte[] magic = new byte[MAGIC.length]; in.get(magic);
        if (!Arrays.equals(MAGIC, magic)) throw new IOException("This is not a Password Guruji vault.");
        int version = in.getInt();
        if (version != VERSION && version != VERSION_2 && version != VERSION_1) throw new IOException("This vault version is not supported.");
        int iterations = version == VERSION_1 ? ITERATIONS : in.getInt();
        if (iterations < 100_000 || iterations > 2_000_000) throw new IOException("Vault key settings are invalid.");
        byte[] salt = new byte[SALT_LENGTH], nonce = new byte[NONCE_LENGTH];
        in.get(salt).get(nonce);
        byte[] encrypted = new byte[in.remaining()]; in.get(encrypted);
        byte[] plain = crypt(Cipher.DECRYPT_MODE, password, salt, nonce, encrypted, version, iterations);
        try {
            return version == VERSION_1 ? decodeV1(plain) : decodeRecords(plain, version >= VERSION);
        } finally {
            Arrays.fill(plain, (byte)0);
            Arrays.fill(encrypted, (byte)0);
            Arrays.fill(file, (byte)0);
        }
    }

    void save(char[] password, List<VaultEntry> entries) throws IOException, GeneralSecurityException {
        byte[] salt = new byte[SALT_LENGTH], nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(salt); RANDOM.nextBytes(nonce);
        byte[] plain = encodeV3(entries), encrypted;
        try { encrypted = crypt(Cipher.ENCRYPT_MODE, password, salt, nonce, plain, VERSION, ITERATIONS); }
        finally { Arrays.fill(plain, (byte)0); }

        ByteBuffer file = ByteBuffer.allocate(MAGIC.length + 4 + 4 + salt.length + nonce.length + encrypted.length);
        file.put(MAGIC).putInt(VERSION).putInt(ITERATIONS).put(salt).put(nonce).put(encrypted);
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, path.getFileName().toString(), ".pending");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer bytes = ByteBuffer.wrap(file.array());
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            try { Files.setPosixFilePermissions(temp, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)); }
            catch (UnsupportedOperationException ignored) {}
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally {
            Arrays.fill(encrypted, (byte)0);
            Arrays.fill(file.array(), (byte)0);
            Files.deleteIfExists(temp);
        }
    }

    private byte[] crypt(int mode, char[] password, byte[] salt, byte[] nonce, byte[] data, int version, int iterations)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        byte[] key = null;
        try {
            key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(MAGIC);
            cipher.updateAAD(ByteBuffer.allocate(4).putInt(version).array());
            if (version >= VERSION_2) cipher.updateAAD(ByteBuffer.allocate(4).putInt(iterations).array());
            return cipher.doFinal(data);
        } finally {
            spec.clearPassword();
            if (key != null) Arrays.fill(key, (byte)0);
        }
    }

    private byte[] encodeV3(List<VaultEntry> entries) throws IOException {
        if (entries.size() > MAX_RECORDS) throw new IOException("Too many records in the vault.");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(entries.size());
            for (VaultEntry e : entries) {
                write(out, e.id()); write(out, e.title()); write(out, e.username()); write(out, e.password());
                write(out, e.url()); write(out, e.notes()); write(out, e.category()); write(out, e.folder());
                out.writeBoolean(e.favorite());
                out.writeBoolean(e.archived());
                writeList(out, e.tags(), MAX_TAGS);
                writeMap(out, e.customFields());
                List<VaultEntry.Revision> history = e.history();
                int first = Math.max(0, history.size() - MAX_HISTORY);
                out.writeInt(history.size() - first);
                for (int i = first; i < history.size(); i++) {
                    VaultEntry.Revision h = history.get(i);
                    write(out, h.username()); write(out, h.password()); write(out, h.url()); write(out, h.notes());
                    writeMap(out, h.customFields()); out.writeLong(h.timestamp());
                }
                out.writeLong(e.updatedAt());
            }
        }
        if (bytes.size() > MAX_FILE_BYTES) throw new IOException("The vault is too large.");
        return bytes.toByteArray();
    }

    private List<VaultEntry> decodeV1(byte[] plain) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(plain))) {
            int count = checkedCount(in.readInt(), MAX_RECORDS, "record");
            List<VaultEntry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new VaultEntry(read(in), read(in), read(in), read(in), read(in), read(in), read(in), in.readLong()));
            }
            if (in.available() != 0) throw new IOException("Unexpected data in vault.");
            return entries;
        } catch (EOFException e) { throw new IOException("Vault data is incomplete.", e); }
    }

    private List<VaultEntry> decodeRecords(byte[] plain, boolean hasArchivedFlag) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(plain))) {
            int count = checkedCount(in.readInt(), MAX_RECORDS, "record");
            List<VaultEntry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String id = read(in), title = read(in), username = read(in), password = read(in);
                String url = read(in), notes = read(in), category = read(in), folder = read(in);
                boolean favorite = in.readBoolean();
                boolean archived = hasArchivedFlag && in.readBoolean();
                List<String> tags = readList(in, MAX_TAGS);
                Map<String, String> fields = readMap(in);
                int historyCount = checkedCount(in.readInt(), MAX_HISTORY, "history");
                List<VaultEntry.Revision> history = new ArrayList<>(historyCount);
                for (int h = 0; h < historyCount; h++) {
                    history.add(new VaultEntry.Revision(read(in), read(in), read(in), read(in), readMap(in), in.readLong()));
                }
                long updated = in.readLong();
                entries.add(new VaultEntry(id, title, username, password, url, notes, category, folder,
                        favorite, archived, tags, fields, history, updated));
            }
            if (in.available() != 0) throw new IOException("Unexpected data in vault.");
            return entries;
        } catch (EOFException e) {
            throw new IOException("Vault data is incomplete.", e);
        }
    }

    private int checkedCount(int count, int max, String kind) throws IOException {
        if (count < 0 || count > max) throw new IOException("Invalid " + kind + " count in vault.");
        return count;
    }

    private void writeList(DataOutputStream out, List<String> values, int max) throws IOException {
        if (values.size() > max) throw new IOException("Too many list items.");
        out.writeInt(values.size());
        for (String value : values) write(out, value);
    }

    private List<String> readList(DataInputStream in, int max) throws IOException {
        int count = checkedCount(in.readInt(), max, "list item");
        List<String> values = new ArrayList<>(count);
        for (int i=0; i<count; i++) values.add(read(in));
        return values;
    }

    private void writeMap(DataOutputStream out, Map<String,String> map) throws IOException {
        if (map.size() > MAX_MAP_FIELDS) throw new IOException("Too many custom fields.");
        TreeMap<String,String> sorted = new TreeMap<>(map);
        out.writeInt(sorted.size());
        for (var entry : sorted.entrySet()) { write(out, entry.getKey()); write(out, entry.getValue()); }
    }

    private Map<String,String> readMap(DataInputStream in) throws IOException {
        int count = checkedCount(in.readInt(), MAX_MAP_FIELDS, "custom field");
        Map<String,String> map = new LinkedHashMap<>();
        for (int i=0; i<count; i++) {
            String key = read(in);
            if (map.put(key, read(in)) != null) throw new IOException("Duplicate custom field.");
        }
        return map;
    }

    private void write(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FIELD_BYTES) throw new IOException("A vault field is too large.");
        out.writeInt(bytes.length); out.write(bytes); Arrays.fill(bytes, (byte)0);
    }

    private String read(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_FIELD_BYTES || length > in.available()) throw new IOException("Invalid vault field.");
        byte[] bytes = in.readNBytes(length);
        try { return new String(bytes, StandardCharsets.UTF_8); }
        finally { Arrays.fill(bytes, (byte)0); }
    }

    static Path defaultPath() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = local == null || local.isBlank()
                ? Path.of(System.getProperty("user.home"), ".local", "share") : Path.of(local);
        return base.resolve("Mirakinetix").resolve("PasswordGuruji").resolve("vault.pgv");
    }
}
