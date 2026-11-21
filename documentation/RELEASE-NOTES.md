# Password Guruji 0.1.0 beta candidate

Native installer build: **1.0.1**. This is installer numbering, not application release 1.0.1. Unsigned, not notarized, and not approved for production. Publication and distribution remain pending owner approval.

## Changes

- New normal vaults and password changes require 15 Unicode characters; existing shorter passwords still unlock. Explicit fictional sample mode permits six.
- Owner-only file permissions, forced temporary-file writes, required atomic replacement, persistent lock sidecars and stale-writer detection reduce accidental data loss.
- Failed saves restore the previous in-memory records. Locking and closing clear secrets even after a save conflict.
- Cryptographic work runs outside the Swing event thread with progress feedback. Locking disposes sensitive dialogs and clears mutable fields.
- Clipboard ownership tokens avoid clearing unrelated later copies, including identical text.
- CSV v2 prefixes exported cells with PGTEXT: to prevent spreadsheet formula interpretation; imports remove exactly one prefix. Tag encoding preserves delimiters and empty values. Legacy exports still import. CSV remains plaintext and excludes history/deleted records; use encrypted backups for complete recovery.
- Invalid UTF-8, duplicate record IDs and malformed authenticated data are rejected. Version 1/2/3 compatibility is retained.
- Tests cover permissions, injected write failures, crashed writers, conflicting instances, legacy fixtures, large vaults and UI behavior. Native CI verifies upgrade/uninstall data preservation using fictional vaults.

## Limitations

Use fictional data only until independent security review and remaining release gates are completed. Java immutable strings, clipboard history and compromised host accounts cannot be securely erased or protected by this application. Local filesystems must support private permissions, file locking and atomic replacement. Network/shared-directory adversaries are outside the current storage threat model. Native packages are unsigned; do not bypass operating-system security protections to test them.

See RELEASE-READINESS.md for exact test evidence, artifact revision and platform outcomes. No claim of a completed independent audit, real-world power-loss validation or physical-device visual/accessibility validation is made.
