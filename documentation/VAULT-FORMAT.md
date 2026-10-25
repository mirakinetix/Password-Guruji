# Password Guruji vault format

This document describes the current local file format. Vault files contain no readable record metadata outside the encrypted payload.

## Common cryptographic envelope

All versions use AES-256-GCM with a 128-bit authentication tag. A key is derived from the master password with PBKDF2-HMAC-SHA-256 and a unique 16-byte salt. Each save generates a fresh 12-byte GCM nonce. The format version, magic value, and, in versions 2 and 3, the stored iteration count are authenticated as additional data.

The current work factor is 600,000 PBKDF2 iterations. Versions 2 and 3 store this count in the header; version 1 uses the same fixed value. The reader rejects iteration values below 100,000 or above 2,000,000 before deriving a key.

The binary header is:

- 8 bytes: ASCII magic value PGVAULT followed by a zero byte
- 4 bytes: big-endian format version
- Version 2 and later: 4 bytes, big-endian PBKDF2 iteration count
- 16 bytes: random salt
- 12 bytes: random AES-GCM nonce
- Remaining bytes: authenticated ciphertext and GCM tag

## Payload versions

Payload strings use UTF-8 with a 4-byte big-endian byte-length prefix. Counts and field lengths are bounded before allocation.

- Version 1 stores each record's identifier, title, username, password, website, notes, category, and update timestamp.
- Version 2 adds folder, favorite, tags, custom fields, and up to ten history snapshots.
- Version 3 adds an encrypted archived flag for the recycle bin.

New saves use version 3. The reader accepts versions 1, 2, and 3; a successful save after opening an older vault writes the current format. Version 1 vaults are decoded into the current record model, and version 2 records are treated as not archived.

## Write behavior and limits

Vault updates are written to a temporary file, flushed, and atomically replaced when the file system supports atomic replacement. The file size limit is 64 MiB, the record limit is 100,000, and each encoded field is limited to 2,000,000 bytes. Authenticated decryption fails if the password is wrong or ciphertext/header data was modified.

This is an implementation description, not a security audit. PBKDF2 is used as the compatibility option in the product baseline; work-factor benchmarking and independent cryptographic review remain release-readiness work.
