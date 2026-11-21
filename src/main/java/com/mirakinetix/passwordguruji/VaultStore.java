package com.mirakinetix.passwordguruji;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.security.MessageDigest;
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

    private byte[] observedDigest;
    private final SecureFiles.Operations operations;

    VaultStore(Path path) { this(path, SecureFiles.SYSTEM); }
    VaultStore(Path path, SecureFiles.Operations operations) {
        this.path = path.toAbsolutePath().normalize(); this.operations=operations;
    }
    Path path() { return path; }
    boolean exists() { return Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS); }

    private Guard guard() throws IOException {
        Files.createDirectories(path.getParent());
        SecureFiles.rejectLink(path);
        Path lockPath=path.getParent().toRealPath().resolve(path.getFileName()+".lock");
        SecureFiles.rejectLink(lockPath);
        FileChannel channel=FileChannel.open(lockPath,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        try {
            SecureFiles.ownerOnly(lockPath);
            FileLock lock;
            try { lock=channel.tryLock(); }
            catch(OverlappingFileLockException busy) { throw new IOException("Another instance is writing this vault. Try again when it finishes.",busy); }
            if(lock==null) throw new IOException("Another instance is writing this vault. Try again when it finishes.");
            return new Guard(channel,lock);
        } catch(IOException|RuntimeException failure) { channel.close(); throw failure; }
    }
    private record Guard(FileChannel channel,FileLock lock) implements AutoCloseable {
        public void close() throws IOException { try { lock.release(); } finally { channel.close(); } }
    }
    private static byte[] digest(byte[] data) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }
    private byte[] readBounded() throws IOException {
        SecureFiles.rejectLink(path);
        try(InputStream input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)) {
            byte[] data=input.readNBytes(MAX_FILE_BYTES+1);
            if(data.length<56||data.length>MAX_FILE_BYTES) throw new IOException("The vault file has an invalid size.");
            return data;
        }
    }
    void create(char[] password) throws IOException, GeneralSecurityException {
        try(Guard ignored=guard()) {
            if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(path.toString());
            saveLocked(password,List.of());
        }
    }
    List<VaultEntry> load(char[] password) throws IOException, GeneralSecurityException {
        try(Guard ignored=guard()) {
            byte[] file=readBounded(), plain=null, encrypted=null;
            try {
                ByteBuffer in=ByteBuffer.wrap(file);
                byte[] magic=new byte[MAGIC.length]; in.get(magic);
                if(!Arrays.equals(MAGIC,magic)) throw new IOException("This is not a Password Guruji vault.");
                int version=in.getInt();
                if(version!=VERSION&&version!=VERSION_2&&version!=VERSION_1) throw new IOException("This vault version is not supported.");
                if(version!=VERSION_1&&file.length<60) throw new IOException("The vault is incomplete.");
                int iterations=version==VERSION_1?ITERATIONS:in.getInt();
                if(iterations<100_000||iterations>2_000_000) throw new IOException("Vault key settings are invalid.");
                byte[] salt=new byte[SALT_LENGTH],nonce=new byte[NONCE_LENGTH]; in.get(salt).get(nonce);
                encrypted=new byte[in.remaining()]; in.get(encrypted);
                plain=crypt(Cipher.DECRYPT_MODE,password,salt,nonce,encrypted,version,iterations);
                List<VaultEntry> result=version==VERSION_1?decodeV1(plain):decodeRecords(plain,version>=VERSION);
                Set<String> ids=new HashSet<>();
                for(VaultEntry entry:result) if(!ids.add(entry.id())) throw new IOException("Duplicate record identifier in vault.");
                observedDigest=digest(file); return result;
            } finally {
                if(plain!=null) Arrays.fill(plain,(byte)0);
                if(encrypted!=null) Arrays.fill(encrypted,(byte)0);
                Arrays.fill(file,(byte)0);
            }
        }
    }
    void save(char[] password,List<VaultEntry> entries) throws IOException,GeneralSecurityException {
        try(Guard ignored=guard()) {
            checkUnchanged(); saveLocked(password,entries);
        }
    }
    private void checkUnchanged() throws IOException,GeneralSecurityException {
        if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)) {
            byte[] current=readBounded();
            try {
                if(observedDigest==null||!MessageDigest.isEqual(observedDigest,digest(current)))
                    throw new IOException("The vault changed in another instance. Your changes were not saved. Close without saving and reopen the latest vault.");
            } finally { Arrays.fill(current,(byte)0); }
        } else if(observedDigest!=null) throw new IOException("The vault was moved or removed. Refusing to overwrite its location.");
    }
    void checkDestination(Path destination) throws IOException {
        Path target=destination.toAbsolutePath().normalize();
        Path lock=path.resolveSibling(path.getFileName()+".lock");
        if(target.equals(path)||target.equals(lock)||target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".lock")
                ||(Files.exists(target)&&((Files.exists(path)&&Files.isSameFile(path,target))||(Files.exists(lock)&&Files.isSameFile(lock,target)))))
            throw new IOException("Choose a separate output file; active vaults and lock files cannot be replaced.");
    }
    void backupTo(Path destination) throws IOException,GeneralSecurityException {
        checkDestination(destination);
        try(Guard ignored=guard()) {
            checkUnchanged(); byte[] data=readBounded();
            try { SecureFiles.writeAtomic(destination,data,operations); }
            finally { Arrays.fill(data,(byte)0); }
        }
    }
    private void saveLocked(char[] password,List<VaultEntry> entries) throws IOException,GeneralSecurityException {
        byte[] salt=new byte[SALT_LENGTH],nonce=new byte[NONCE_LENGTH]; RANDOM.nextBytes(salt); RANDOM.nextBytes(nonce);
        byte[] plain=encodeV3(entries),encrypted=null,file=null;
        try {
            encrypted=crypt(Cipher.ENCRYPT_MODE,password,salt,nonce,plain,VERSION,ITERATIONS);
            file=ByteBuffer.allocate(44+encrypted.length).put(MAGIC).putInt(VERSION).putInt(ITERATIONS).put(salt).put(nonce).put(encrypted).array();
            SecureFiles.writeAtomic(path,file,operations); observedDigest=digest(file);
        } finally {
            Arrays.fill(plain,(byte)0);
            if(encrypted!=null) Arrays.fill(encrypted,(byte)0);
            if(file!=null) Arrays.fill(file,(byte)0);
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
        WipingBuffer bytes = new WipingBuffer();
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
            if(bytes.size()>MAX_FILE_BYTES-60) throw new IOException("The vault is too large.");
            return bytes.toByteArray();
        } finally { bytes.wipe(); }
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
        try {
            if(bytes.length>MAX_FIELD_BYTES) throw new IOException("A vault field is too large.");
            if((long)out.size()+4+bytes.length>MAX_FILE_BYTES-60) throw new IOException("The vault is too large.");
            out.writeInt(bytes.length); out.write(bytes);
        } finally { Arrays.fill(bytes,(byte)0); }
    }

    private String read(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_FIELD_BYTES || length > in.available()) throw new IOException("Invalid vault field.");
        byte[] bytes = in.readNBytes(length);
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        finally { Arrays.fill(bytes, (byte)0); }
    }

    private static final class WipingBuffer extends ByteArrayOutputStream {
        private void reserve(int additional) {
            int required=Math.addExact(count,additional);
            if(required>buf.length) {
                byte[] old=buf;buf=Arrays.copyOf(old,Math.max(required,Math.min(MAX_FILE_BYTES,old.length*2)));
                Arrays.fill(old,(byte)0);
            }
        }
        @Override public synchronized void write(int value) { reserve(1);super.write(value); }
        @Override public synchronized void write(byte[] value,int offset,int length) { reserve(length);super.write(value,offset,length); }
        void wipe() { Arrays.fill(buf,(byte)0); reset(); }
    }

    static Path defaultPath() {
        return defaultPath(System.getProperty("os.name"), Path.of(System.getProperty("user.home")), System.getenv());
    }

    static Path defaultPath(String osName, Path home, Map<String,String> environment) {
        Path legacy = home.resolve(".local/share/Mirakinetix/PasswordGuruji/vault.pgv");
        String os = osName.toLowerCase(Locale.ROOT);
        // Keep pre-existing installations discoverable; never silently migrate vaults.
        if (!os.startsWith("windows") && Files.isRegularFile(legacy)) return legacy;
        String local = environment.get("LOCALAPPDATA");
        String xdg = environment.get("XDG_DATA_HOME");
        Path base;
        if (os.startsWith("windows")) base = local == null || local.isBlank() ? home.resolve("AppData/Local") : Path.of(local);
        else if (os.startsWith("mac")) base = home.resolve("Library/Application Support");
        else base = xdg != null && !xdg.isBlank() && Path.of(xdg).isAbsolute() ? Path.of(xdg) : home.resolve(".local/share");
        return base.resolve("Mirakinetix").resolve("PasswordGuruji").resolve("vault.pgv");
    }
}
