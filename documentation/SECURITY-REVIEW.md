# Focused security review — 21 November 2026

This is a source review plus automated checks, not an independent audit or penetration-test certificate. Use dummy data only.

## Cryptographic design retained

VaultStore uses the JDK AES-256-GCM implementation with a 128-bit tag, fresh 16-byte salt and 12-byte nonce per save, and PBKDF2-HMAC-SHA-256 at 600,000 iterations. The magic/version/iteration header is authenticated. Parsing bounds record, field, tag, map, and history counts. Wrong-password and header/ciphertext mutation tests exercise authenticated failure. No custom replacement cipher or weakened work factor was introduced.

Master password arrays and derived key arrays are cleared on relevant paths; decrypted payload arrays are cleared after parsing. The tests verify wiping the application's held master-password array on locking and removing the reference on shutdown. They cannot prove provider/internal JVM copies are erased.

## Findings

| Priority | Finding | Status / next action |
| --- | --- | --- |
| High for real-secret use | Six-character minimum retained for the owner's dummy-data testing. Public sample master password is intentionally 123456. | Unresolved release gate: raise real-use minimum to 15 and separate demo policy before production. Never use sample vaults for real credentials. |
| High for real-secret use | Vault fields, revisions, Swing text models, ByteArrayOutputStream copies, and clipboard transfers use immutable Strings or retained buffers. GC does not guarantee erasure; crypto providers can copy keys. | Documented architectural limitation. A dedicated memory-lifecycle redesign and independent review are required; do not claim complete zeroization. |
| Medium | Immediately locking a newly created vault left a stale creation form. | Fixed by rebuilding the unlock form; actual create/lock/wrong-password/unlock test added. |
| Medium | Copied data was retained by separate timers and was not cleared immediately on lock/close. | Centralized clipboard lifecycle; timer, ownership-change, and shutdown/lock cleanup implemented. Real system clipboard timeout tested. OS clipboard history/managers, race conditions, and busy clipboard at process exit remain outside guaranteed control. |
| Medium | Windows vault file ACLs inherit destination-directory policy; no explicit user-only DACL is applied. Backup/export access depends on destination permissions. | Unresolved: use a private user folder; test inherited ACLs and design an explicit Windows ACL policy before real-secret use. POSIX vault files are set to 0600 and tested on Linux/macOS. |
| Medium | Save falls back to non-atomic replacement on filesystems without atomic moves; directory fsync/crash durability is not guaranteed. Concurrent writers are not locked. | Unresolved: single app instance and local filesystem only; crash/power-loss and concurrent-write tests still required. |
| Medium | Plaintext CSV export intentionally contains secrets and can contain spreadsheet formula strings. | Existing consent prompt retained. Treat as sensitive data, never open in spreadsheet software, and review a separate safe spreadsheet-export format before expanding use. |
| Low | CSV could export an empty custom-field value but rejected that value on import. | Fixed and covered by Unicode/quotes/newline/empty-field round trip. |
| Medium | Installers are unsigned; Mac notarization absent. | Expected candidate limitation; Gatekeeper/SmartScreen acceptance unverified. No protection bypasses recommended. |
| Medium | Password/crypto work occurs on Swing's event thread and can temporarily freeze the UI. Resource limits do not prevent all large-input memory pressure. | Stress/performance testing and background-worker design remain. |

No critical cryptographic break was demonstrated by this limited assessment. This is not evidence that none exists.

## Backup, integrity, logs and temporary files

Saves write encrypted bytes to a temporary sibling, flush the file, then replace the destination. The temporary file is deleted in finally. Tests check plaintext is absent from the encrypted file, repeated saves differ, tampering fails, no pending file remains after successful save, and an encrypted backup can be loaded and re-encrypted under a new master password. Full file-picker backup/restore UI and failure-injection scenarios remain manual/untested.

Source inspection found no application logging of master passwords or record contents. Error dialogs can expose filesystem paths. Test data is synthetic, and native installation checks use disposable vault paths. No real user vault was read by this work. CSV is intentionally plaintext. OS swap, crash dumps, clipboard history and filesystem snapshots are not sanitized by the app.

## Dependency review

`tools/check-dependencies.py` resolves Maven artifacts and queries OSV using public Maven package names and versions only. Advisory matches or service failures fail the security check. The timestamped JSON report is uploaded to the security workflow. This scope includes resolved application/test dependencies and explicitly declared Maven plugins, but excludes transitive build-plugin dependencies, JDK security advisories, operating-system packages, WiX and other packaging tools. The application has no external runtime Java libraries.

Actions are pinned to commit hashes, token permissions are read-only, and no secrets are passed to PR code. Standard public hosted runners are used; artifacts/cache have finite retention. Do not add paid/larger runners without approval. Signing keys and deployment credentials are intentionally absent.

## Evidence and sources

Actual run outcomes and pending gates are recorded in RELEASE-READINESS.md.

- [JDK 25 packaging requirements](https://docs.oracle.com/en/java/javase/25/jpackage/packaging-overview.html)
- [GitHub hosted runner platforms and public-repository pricing](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
- [OSV query API](https://google.github.io/osv.dev/post-v1-query/)
- [RFC 6238 TOTP vectors](https://www.rfc-editor.org/rfc/rfc6238)
