package com.mirakinetix.passwordguruji;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent legacy fixtures: no production serializer or crypto helper is used to generate them. */
class CompatibilityTest {
    @TempDir Path directory;
    private static final byte[] MAGIC={'P','G','V','A','U','L','T',0};
    private static final char[] LEGACY_PASSWORD="123456".toCharArray();
    private static final long TIME=1_600_000_000_123L;
    @FunctionalInterface private interface PayloadWriter { void write(DataOutputStream output) throws IOException; }

    @ParameterizedTest @ValueSource(ints={1,2})
    void legacyShortPasswordLoadsAndMigratesWithoutChangingRecordValues(int version) throws Exception {
        byte[] payload=payload(out -> { out.writeInt(1); record(out,version,false); });
        Path path=fixture(version,payload);
        byte[] original=Files.readAllBytes(path);
        VaultStore store=new VaultStore(path);
        List<VaultEntry> entries=store.load(LEGACY_PASSWORD);
        assertArrayEquals(original,Files.readAllBytes(path),"Opening a legacy vault must not rewrite it");
        VaultEntry entry=entries.getFirst();
        assertEquals("legacy-record-id",entry.id());
        assertEquals("Café 日本語",entry.title()); assertEquals("legacy@example.invalid",entry.username());
        assertEquals("legacy-dummy-password",entry.password()); assertEquals("https://example.invalid",entry.url());
        assertEquals("A note\nwith two lines",entry.notes()); assertEquals("Login",entry.category());
        assertEquals(TIME,entry.updatedAt()); assertFalse(entry.archived());
        if(version==2) {
            assertEquals("Legacy folder",entry.folder()); assertTrue(entry.favorite());
            assertEquals(List.of("legacy","日本語"),entry.tags());
            assertEquals(Map.of("empty","","Reference","fictional"),entry.customFields());
            assertEquals("previous-dummy-password",entry.history().getFirst().password());
        } else { assertTrue(entry.tags().isEmpty()); assertTrue(entry.history().isEmpty()); }
        char[] replacement="new-synthetic-master-password".toCharArray();
        store.save(replacement,entries);
        byte[] upgraded=Files.readAllBytes(path);
        assertEquals(3,ByteBuffer.wrap(upgraded).getInt(8));
        assertEquals(600_000,ByteBuffer.wrap(upgraded).getInt(12));
        VaultEntry reopened=new VaultStore(path).load(replacement).getFirst();
        assertEntryEquals(entry,reopened);
        assertThrows(javax.crypto.AEADBadTagException.class,()->new VaultStore(path).load(LEGACY_PASSWORD));
        Arrays.fill(replacement,'\0');
    }

    @ParameterizedTest @ValueSource(ints={-1,100_001,Integer.MAX_VALUE})
    void rejectsAuthenticatedInvalidRecordCounts(int count) throws Exception {
        assertInvalid(3,payload(out->out.writeInt(count)));
    }
    @ParameterizedTest @ValueSource(ints={-1,2_000_001,Integer.MAX_VALUE})
    void rejectsAuthenticatedInvalidFieldLengths(int length) throws Exception {
        assertInvalid(1,payload(out->{out.writeInt(1);out.writeInt(length);}));
    }
    @Test void rejectsAuthenticatedTruncatedField() throws Exception {
        assertInvalid(1,payload(out->{out.writeInt(1);out.writeInt(20);out.writeByte(42);}));
    }
    @Test void rejectsAuthenticatedInvalidUtf8WithoutReplacingIt() throws Exception {
        assertInvalid(1,payload(out->{
            out.writeInt(1); text(out,"id"); out.writeInt(2); out.write(new byte[]{(byte)0xc3,0x28});
            text(out,"user");text(out,"dummy");text(out,"");text(out,"");text(out,"Login");out.writeLong(TIME);
        }));
    }
    @Test void rejectsAuthenticatedTrailingPayload() throws Exception {
        assertInvalid(3,payload(out->{out.writeInt(0);out.writeByte(1);}));
    }
    @Test void rejectsAuthenticatedDuplicateCustomFields() throws Exception {
        assertInvalid(2,payload(out->{
            out.writeInt(1); baseFields(out); text(out,"folder");out.writeBoolean(false);out.writeInt(0);
            out.writeInt(2);text(out,"same-key");text(out,"first");text(out,"same-key");text(out,"second");
            out.writeInt(0);out.writeLong(TIME);
        }));
    }
    @ParameterizedTest @ValueSource(ints={1,2,3})
    void rejectsAuthenticatedDuplicateRecordIds(int version) throws Exception {
        assertInvalid(version,payload(out->{out.writeInt(2);record(out,version,false);record(out,version,false);}));
    }
    @ParameterizedTest @ValueSource(ints={-1,129})
    void rejectsAuthenticatedInvalidTagCounts(int count) throws Exception {
        assertInvalid(2,payload(out->{out.writeInt(1);baseFields(out);text(out,"");out.writeBoolean(false);out.writeInt(count);}));
    }
    @ParameterizedTest @ValueSource(ints={-1,11})
    void rejectsAuthenticatedInvalidHistoryCounts(int count) throws Exception {
        assertInvalid(2,payload(out->{out.writeInt(1);baseFields(out);text(out,"");out.writeBoolean(false);out.writeInt(0);out.writeInt(0);out.writeInt(count);}));
    }
    @Test void truncatedEnvelopeIsRejectedAsAnIoError() throws Exception {
        byte[] valid=Files.readAllBytes(fixture(3,payload(out->out.writeInt(0))));
        Path truncated=directory.resolve("truncated.pgv"); Files.write(truncated,Arrays.copyOf(valid,59));
        assertThrows(IOException.class,()->new VaultStore(truncated).load(LEGACY_PASSWORD));
    }
    @Test void thousandRecordsPreserveEveryCategoryAndOrganization() throws Exception {
        String[] categories={"Login","Secure note","Identity","Payment","Recovery code","License","Other"};
        List<VaultEntry> expected=new ArrayList<>();
        for(int i=0;i<1000;i++) {
            VaultEntry entry=VaultEntry.create("Fictional "+i,"user-"+i+"@example.invalid","dummy-"+i,
                "https://example.invalid/"+i,"Synthetic note 日本語 "+i,categories[i%categories.length],
                "Folder "+i%5,i%3==0,List.of("synthetic","tag-"+i%11),Map.of("empty","","Index",Integer.toString(i)));
            if(i%10==0) entry=entry.updated(entry.title(),entry.username(),"revised-dummy-"+i,entry.url(),entry.notes(),entry.category());
            if(i%17==0) entry=entry.withArchived(true);
            expected.add(entry);
        }
        VaultStore store=new VaultStore(directory.resolve("large-synthetic.pgv"));
        char[] password="large-synthetic-master-password".toCharArray();
        store.save(password,expected); List<VaultEntry> actual=store.load(password);
        assertEquals(1000,actual.size());
        for(int i=0;i<expected.size();i++) assertEntryEquals(expected.get(i),actual.get(i));
        assertEquals(7,actual.stream().map(VaultEntry::category).distinct().count());
        Arrays.fill(password,'\0');
    }

    private void assertInvalid(int version,byte[] payload) throws Exception {
        Path path=fixture(version,payload);byte[] original=Files.readAllBytes(path);
        assertThrows(IOException.class,()->new VaultStore(path).load(LEGACY_PASSWORD));
        assertArrayEquals(original,Files.readAllBytes(path),"Rejecting invalid data must not modify the vault");
    }
    private Path fixture(int version,byte[] plain) throws Exception {
        int iterations=version==1?600_000:100_000;
        byte[] salt=new byte[16],nonce=new byte[12];SecureRandom random=new SecureRandom();random.nextBytes(salt);random.nextBytes(nonce);
        PBEKeySpec spec=new PBEKeySpec(LEGACY_PASSWORD,salt,iterations,256);byte[] key=null;
        try {
            key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(MAGIC);cipher.updateAAD(ByteBuffer.allocate(4).putInt(version).array());
            if(version>=2)cipher.updateAAD(ByteBuffer.allocate(4).putInt(iterations).array());
            byte[] encrypted=cipher.doFinal(plain);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(DataOutputStream out=new DataOutputStream(bytes)) {
                out.write(MAGIC);out.writeInt(version);if(version>=2)out.writeInt(iterations);
                out.write(salt);out.write(nonce);out.write(encrypted);
            }
            Path file=directory.resolve("synthetic-"+UUID.randomUUID()+".pgv"); Files.write(file,bytes.toByteArray());return file;
        } finally {spec.clearPassword();if(key!=null)Arrays.fill(key,(byte)0);Arrays.fill(plain,(byte)0);}
    }
    private static byte[] payload(PayloadWriter writer) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)){writer.write(out);}return bytes.toByteArray();
    }
    private static void text(DataOutputStream out,String value) throws IOException {
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);
    }
    private static void baseFields(DataOutputStream out) throws IOException {
        for(String value:List.of("legacy-record-id","Café 日本語","legacy@example.invalid","legacy-dummy-password",
            "https://example.invalid","A note\nwith two lines","Login"))text(out,value);
    }
    private static void record(DataOutputStream out,int version,boolean archived) throws IOException {
        baseFields(out);
        if(version>=2) {
            text(out,"Legacy folder");out.writeBoolean(true);if(version>=3)out.writeBoolean(archived);
            out.writeInt(2);text(out,"legacy");text(out,"日本語");
            out.writeInt(2);text(out,"empty");text(out,"");text(out,"Reference");text(out,"fictional");
            out.writeInt(1);text(out,"previous@example.invalid");text(out,"previous-dummy-password");text(out,"");text(out,"old note");out.writeInt(0);out.writeLong(TIME-1000);
        }
        out.writeLong(TIME);
    }
    private static void assertEntryEquals(VaultEntry expected,VaultEntry actual) {
        assertAll(()->assertEquals(expected.id(),actual.id()),()->assertEquals(expected.title(),actual.title()),
            ()->assertEquals(expected.username(),actual.username()),()->assertEquals(expected.password(),actual.password()),
            ()->assertEquals(expected.url(),actual.url()),()->assertEquals(expected.notes(),actual.notes()),
            ()->assertEquals(expected.category(),actual.category()),()->assertEquals(expected.folder(),actual.folder()),
            ()->assertEquals(expected.favorite(),actual.favorite()),()->assertEquals(expected.archived(),actual.archived()),
            ()->assertEquals(expected.tags(),actual.tags()),()->assertEquals(expected.customFields(),actual.customFields()),
            ()->assertEquals(expected.history(),actual.history()),()->assertEquals(expected.updatedAt(),actual.updatedAt()));
    }
}
