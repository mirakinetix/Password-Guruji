package com.mirakinetix.passwordguruji;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

final class VaultStore {
    private static final byte[] MAGIC = {'P','G','V','A','U','L','T',0};
    private static final int VERSION = 1, ITERATIONS = 600_000, SALT_LENGTH = 16, NONCE_LENGTH = 12;
    private static final int MAX_FILE_BYTES = 32 * 1024 * 1024, MAX_FIELD_BYTES = 1_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Path path;

    VaultStore(Path path) { this.path = path; }
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
        if (!java.util.Arrays.equals(magic, MAGIC)) throw new IOException("This is not a Password Guruji vault.");
        int version = in.getInt();
        if (version != VERSION) throw new IOException("This vault version is not supported.");
        byte[] salt = new byte[SALT_LENGTH], nonce = new byte[NONCE_LENGTH];
        in.get(salt).get(nonce);
        byte[] encrypted = new byte[in.remaining()]; in.get(encrypted);
        byte[] plain = crypt(Cipher.DECRYPT_MODE, password, salt, nonce, encrypted);
        try { return decode(plain); }
        finally {
            java.util.Arrays.fill(plain, (byte)0);
            java.util.Arrays.fill(encrypted, (byte)0);
            java.util.Arrays.fill(file, (byte)0);
        }
    }

    void save(char[] password, List<VaultEntry> entries) throws IOException, GeneralSecurityException {
        byte[] salt = new byte[SALT_LENGTH], nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(salt); RANDOM.nextBytes(nonce);
        byte[] plain = encode(entries), encrypted;
        try { encrypted = crypt(Cipher.ENCRYPT_MODE, password, salt, nonce, plain); }
        finally { java.util.Arrays.fill(plain, (byte)0); }
        ByteBuffer file = ByteBuffer.allocate(MAGIC.length + 4 + salt.length + nonce.length + encrypted.length);
        file.put(MAGIC).putInt(VERSION).put(salt).put(nonce).put(encrypted);
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.write(temp, file.array(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally {
            java.util.Arrays.fill(encrypted, (byte)0);
            java.util.Arrays.fill(file.array(), (byte)0);
            Files.deleteIfExists(temp);
        }
    }

    private byte[] crypt(int mode, char[] password, byte[] salt, byte[] nonce, byte[] data)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        byte[] key = null;
        try {
            key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(MAGIC);
            cipher.updateAAD(ByteBuffer.allocate(4).putInt(VERSION).array());
            return cipher.doFinal(data);
        } finally {
            spec.clearPassword();
            if (key != null) java.util.Arrays.fill(key, (byte)0);
        }
    }

    private byte[] encode(List<VaultEntry> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(entries.size());
            for (VaultEntry e : entries) {
                writeString(out, e.id()); writeString(out, e.title()); writeString(out, e.username());
                writeString(out, e.password()); writeString(out, e.url()); writeString(out, e.notes());
                writeString(out, e.category()); out.writeLong(e.updatedAt());
            }
        }
        return bytes.toByteArray();
    }

    private List<VaultEntry> decode(byte[] plain) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(plain))) {
            int count = in.readInt();
            if (count < 0 || count > 100_000) throw new IOException("Invalid vault record count.");
            List<VaultEntry> result = new ArrayList<>(count);
            for (int i=0; i<count; i++) result.add(new VaultEntry(readString(in), readString(in), readString(in),
                    readString(in), readString(in), readString(in), readString(in), in.readLong()));
            if (in.available() != 0) throw new IOException("Unexpected data in vault.");
            return result;
        } catch (EOFException e) { throw new IOException("Vault data is incomplete.", e); }
    }

    private void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FIELD_BYTES) throw new IOException("A vault field is too large.");
        out.writeInt(bytes.length); out.write(bytes); java.util.Arrays.fill(bytes, (byte)0);
    }

    private String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_FIELD_BYTES || length > in.available()) throw new IOException("Invalid vault field.");
        byte[] bytes = in.readNBytes(length);
        try { return new String(bytes, StandardCharsets.UTF_8); }
        finally { java.util.Arrays.fill(bytes, (byte)0); }
    }

    static Path defaultPath() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = local == null || local.isBlank()
                ? Path.of(System.getProperty("user.home"), ".local", "share") : Path.of(local);
        return base.resolve("Mirakinetix").resolve("PasswordGuruji").resolve("vault.pgv");
    }
}
