# Focused security review — 21 November 2026

This is engineering source review and automated regression evidence, not an independent security audit. The current hardening candidate is not approved for real secrets or production distribution. Use fictional data only after its matching checks are green and the owner approves testing.

## Cryptographic design retained

VaultStore retains JDK AES-256-GCM with a 128-bit tag, fresh 16-byte salt and 12-byte nonce on each save, and PBKDF2-HMAC-SHA-256 at 600,000 iterations. Magic/version/iteration metadata is authenticated. The loader bounds file size, fields, records, lists and history; malformed UTF-8, duplicate identifiers/fields, truncation and unexpected trailing data are rejected. Existing short-password and version 1/2 vaults remain readable without weakening the policy for new passwords.

## Findings and resolution evidence

Severity describes potential impact, not an assertion that exploitation occurred.

| Severity | Evidence / finding | Resolution and regression evidence | Residual limitation |
| --- | --- | --- | --- |
| High | Six-character creation policy was suitable only for demonstrations. | New real-mode creation/password changes require 15 characters; explicit sample mode permits six. Policy and real Swing creation tests pass locally. | Legacy passwords still unlock; users must deliberately strengthen them. The committed fictional sample password is public. |
| High | Concurrent app instances could overwrite newer records. | Persistent sidecar file locks and an observed encrypted-file digest reject stale writers. Stale-write, removed-vault and child-process lock-release tests pass locally. | Cooperating local instances only; hostile processes and shared/network filesystems are not covered. |
| High | Failure after in-memory edits could leave UI and disk inconsistent. | Central rollback restores the last persisted immutable record snapshot; actual UI stale-writer regression passes locally. | Broader manual edit/import/restore failure combinations remain necessary. |
| High | Locking could depend on a successful redundant save; secret dialogs could remain visible. | Lock now clears state without requiring disk writes, wipes/disposes owned text dialogs, stops TOTP and clears owned clipboard. Dialog clearing test passes locally. | Final disk-conflict lock regression passes locally; session sleep/resume and OS lock integration remain manual. |
| High | Export could overwrite the active vault; output could replace coordination sidecar. | Shared destination validation rejects active vault aliases and lock destinations for export/backup. | Destination-protection regressions pass locally; users can still deliberately replace unrelated files after confirmation. |
| Medium | Windows permissions inherited from destination folders. | SecureFiles applies user-only Windows ACLs or POSIX 0600 before writing sensitive temporary files; fails closed if unsupported. Vault/backup/lock permission tests pass locally. | Admin/root access, filesystem policy and malicious writable parent-directory races are not prevented. Use private local directories. |
| Medium | Non-atomic fallback risked partial replacement. | Only atomic sibling replacement is accepted. File data is forced; directory force attempted where supported. Injected write/replace/unsupported-move failures, read-only file and crash-before-commit tests pass locally. | Sudden hardware power loss, controller caches and every filesystem are not simulated. A hard kill can leave a private encrypted pending file; do not claim automatic crash cleanup. |
| Medium | Clipboard cleanup could erase unrelated content or retain old owned data. | Ownership token and a single timer distinguish owned transfers, clear on lock/close and retry busy clipboard. Actual clipboard lifecycle and identical-text replacement tests pass locally. | OS history, external clipboard managers and busy clipboard at process exit cannot be guaranteed erased. |
| Medium | CSV export cells could be interpreted as formulas; import needed stricter validation. | Versioned CSV uses reversible text markers and stricter parsing; formula/delimiter/Unicode/empty-field and malformed-input tests pass locally. | CSV remains plaintext and highly sensitive. Other spreadsheet/import software may reinterpret edited files. |
| Medium | Expensive cryptographic work blocked Swing's event thread. | Vault tasks run in a background worker behind a modal progress window; accepted state updates stay on the event thread. | Large-vault responsiveness, unusual interruption and OS sleep/resume require further testing. |
| High residual for real-secret assurance | Immutable record strings, Swing models, provider/JVM copies and external OS buffers cannot all be erased reliably. | Explicit password/key/payload buffers and growable encoding buffers are cleared on relevant paths; held master-password clearing is tested. | No complete zeroization claim. A dedicated memory-lifecycle review and independent security assessment remain production gates. |
| Medium/High build-tool risk | Older plugin dependencies matched advisories. | Stable plugin updates and narrowly scoped fixed dependency overrides; expanded local OSV scan: 96 package versions, zero matches. | Time-bound inventory lookup, not proof of vulnerability absence. See scope below. |
| Medium distribution risk | Windows/Mac installers are unsigned and Mac notarization is absent. | Requirements, costs and eligibility documented in SIGNING-REQUIREMENTS.md. No credentials or paid setup added. | Consumer SmartScreen/Gatekeeper acceptance unverified; production distribution blocked. |

No critical cryptographic break was demonstrated. This limited assessment cannot establish that none exists.

## PR #2 review findings and regression coverage

- Record labels and folder renderers disable Swing HTML parsing before setting data. The styled list renderer escapes record strings before inserting them into its fixed template. A synthetic imported link with Unicode remains literal, has no HTML view, and produces no connection to the test's loopback listener.
- Backup publication refuses all existing destinations, including files that appear between validation and final publication. A private, flushed temporary file is published at the new name atomically by hard link. Backup does not resave the source. Tests verify a second vault remains unchanged, exercise the creation race, and check source bytes on cancellation.
- Restore reads and authenticates the backup snapshot without creating a sidecar. Ordinary opens and writes retain their existing locks and conflict checks. Read-only recovery, wrong-password rejection, and no-sidecar behavior are covered.
- CSV v2 keeps reversible formula markers and now shares the same file and cell limits between reader and writer. Tests exercise encoded Unicode at the vault field boundary, exact and oversized cells, and preservation of a prior destination on failure.
- Displayed values route keyboard copy through the same clipboard ownership mechanism as Copy. Desktop tests check timeout, locking, normal shutdown, and retention of a later clipboard item with identical text. OS clipboard history and external clipboard managers can still retain data.
- Filesystems that cannot create hard links fail closed when writing a backup. An interrupted write can leave a private temporary file. Ordinary writable-vault permissions and atomic replacement rules remain in force.

See [Release readiness](RELEASE-READINESS.md) for revision-specific results.

Tests use synthetic records and disposable paths. No personal vault or real password data was used. Source review found no intended application logging of master passwords or record contents; error messages can reveal local paths. OS swap, process dumps, backups, snapshots and clipboard history are not sanitized by this app.

## Dependency and source scan boundaries

The scanner sends public Maven coordinates/versions to OSV. It covers resolved application/test dependencies, declared Maven plugins, dynamically selected JUnit Platform Surefire provider and their resolved transitive dependencies. It excludes the Maven process/core distribution, undeclared lifecycle plugins/providers, JDK advisories, OS packages and installer tools. The application has no external runtime Java libraries.

Missing inventories, incomplete plugin/provider resolution, advisory matches and request failures fail the scan. An incomplete report is written before scanning, so it cannot be mistaken for a stale clean report. The workflow retains inventories and timestamped results. The local expanded scan returned zero advisory matches for 96 versions.

Tracked text files also receive high-confidence private-key/GitHub-token/AWS-access-key pattern checks. The local run checked 52 files with no matches. Results identify file/line/rule without revealing matched values. This is a limited detector, not exhaustive credential discovery; encrypted/binary files and repository history are not scanned.

Actions are commit-pinned, tokens read-only, and signing secrets absent. No paid runners, signing account, certificate or membership were configured. [Signing prerequisites](SIGNING-REQUIREMENTS.md) and [release gates](RELEASE-READINESS.md) remain authoritative for distribution.
