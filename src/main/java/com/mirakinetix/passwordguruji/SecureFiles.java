package com.mirakinetix.passwordguruji;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Secure sibling-file replacement. No non-atomic fallback is allowed. */
final class SecureFiles {
    interface Operations {
        void write(Path temporary, byte[] bytes) throws IOException;
        void replace(Path temporary, Path destination) throws IOException;
    }
    static final Operations SYSTEM = new Operations() {
        public void write(Path temporary, byte[] bytes) throws IOException {
            try (FileChannel channel=FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer=ByteBuffer.wrap(bytes);
                while(buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
        }
        public void replace(Path temporary,Path destination) throws IOException {
            Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }
    };
    private SecureFiles() {}
    static void rejectLink(Path path) throws IOException {
        if(Files.isSymbolicLink(path)) throw new IOException("Symbolic links are not supported for sensitive files.");
        if(Files.exists(path,LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Choose a regular file.");
        if(Files.exists(path) && Files.getFileStore(path).supportsFileAttributeView("unix")) {
            if(((Number)Files.getAttribute(path,"unix:nlink",LinkOption.NOFOLLOW_LINKS)).longValue()>1)
                throw new IOException("Hard-linked sensitive files are not supported.");
        }
    }
    static void ownerOnly(Path path) throws IOException {
        var posix=Files.getFileAttributeView(path,PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
        if(posix!=null) {
            posix.setPermissions(PosixFilePermissions.fromString("rw-------")); return;
        }
        var acl=Files.getFileAttributeView(path,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
        if(acl!=null) {
            var user=path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
            acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(user)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
            return;
        }
        throw new IOException("This filesystem cannot enforce private file permissions. Choose a local secure filesystem.");
    }
    static void writeAtomic(Path destination,byte[] bytes,Operations operations) throws IOException {
        destination=destination.toAbsolutePath().normalize();
        Files.createDirectories(destination.getParent()); rejectLink(destination);
        if(Files.exists(destination)) {
            var dos=Files.getFileAttributeView(destination,DosFileAttributeView.class);
            if(!Files.isWritable(destination)||(dos!=null&&dos.readAttributes().isReadOnly()))
                throw new AccessDeniedException(destination.toString(),null,"The destination is read-only.");
        }
        Path temp=Files.createTempFile(destination.getParent(),".password-guruji-", ".pending");
        try {
            ownerOnly(temp); operations.write(temp,bytes); operations.replace(temp,destination);
            // Directory fsync is supported by some Unix filesystems, not by Windows providers.
            if(Files.getFileAttributeView(destination,PosixFileAttributeView.class)!=null) {
                try(FileChannel directory=FileChannel.open(destination.getParent(),StandardOpenOption.READ)) { directory.force(true); }
                catch(IOException|UnsupportedOperationException unavailable) { /* File was forced; directory durability is platform-specific. */ }
            }
        } finally { Files.deleteIfExists(temp); }
    }
}
