# Password Guruji — Project Baseline

**Brand:** Mirakinetix  
**Product:** Password Guruji  
**Document status:** Product baseline for review  
**Version:** 0.1  
**Prepared:** 23 October 2026

## 1. Executive summary

Password Guruji is a free, Java-based password manager and secure information vault designed to operate fully offline. Its core value is that a person can create a vault, generate credentials, organize and retrieve sensitive information, and maintain local backups without creating an account or connecting to a service.

The product will take inspiration from capabilities offered by leading premium password managers, while remaining an original product in its name, code, interface, and implementation. “Premium-level features” describes the intended breadth and polish of the feature set; it is not a claim of audited security parity with established vendors.

The default installation must not make network requests. If a later release offers an online capability, it must be optional, off by default, narrowly scoped, explained before activation, and usable without surrendering the offline core. Password Guruji must remain useful when the computer is permanently disconnected from the internet.

## 2. Product identity

- **Brand:** Mirakinetix
- **Software name:** Password Guruji
- **Category:** Offline-first password manager and encrypted personal vault
- **Platform direction:** Java desktop software; supported operating systems and Java distribution method remain to be selected in the requirements phase.
- **Price goal:** Free to use. No paid tier is required to access the product's planned core features.

The working product name and brand are project choices, not legal clearance. Before public distribution, conduct a trademark, company-name, and domain-name review.

## 3. Vision and product promise

Give people a practical way to create and keep unique passwords and other sensitive records under their own control, without requiring an online account or cloud service.

### Product promise

1. **Works offline:** Create, unlock, edit, search, generate, export, and back up a vault locally.
2. **Private by default:** No telemetry, account registration, synchronization, analytics, or background network calls in the offline product.
3. **Protected at rest:** Vault contents are encrypted with authenticated encryption and a deliberately expensive key derivation process.
4. **Useful day to day:** Find, edit, copy, organize, and generate credentials with a small number of clear actions.
5. **User-controlled connectivity:** If optional online features are introduced, the user chooses whether to enable each relevant connection.
6. **Transparent limitations:** Explain recovery, backup, clipboard, device compromise, and offline-check limitations in plain language.

## 4. Users and main problems

### Intended users

- Individuals who want password storage without a cloud account.
- People working in restricted, low-connectivity, or air-gapped environments.
- Users who prefer to keep their vault file and backups under their own control.
- Users learning to improve password uniqueness and use generated passwords.

### Problems to solve

- Reusing passwords because remembering many unique passwords is difficult.
- Keeping credentials in insecure notes, spreadsheets, or messages.
- Losing access to stored credentials because a provider connection is unavailable.
- Not knowing how to create strong, random passwords or passphrases.
- Having no clear process for making and restoring a secure local backup.

## 5. Reference products and baseline method

There is no objectively established single “best password manager in the world”; products optimize for different user needs. This baseline uses leading paid products as a **feature reference set**, not as a ranking or an instruction to copy a product. The first comparison set is 1Password, Bitwarden, Proton Pass, Dashlane, and Keeper, based on their current official plan pages and help documentation.

Common premium capabilities identified in these references include:

- Password and passkey records, secure notes, payment or identity records.
- Strong password generation and password-health feedback.
- Browser or app autofill, where the platform permits it.
- Multiple vaults, folders, tags, search, and item history.
- Secure sharing, family or group access controls, and emergency access.
- File attachments, authenticator codes, or other one-time-code support.
- Security alerts and breach or dark-web monitoring.
- Cross-device synchronization and account recovery.

Password Guruji should adopt useful local capabilities, but distinguish those from features whose purpose depends on a remote service. For example, local password-strength analysis can run offline; checking a live breach corpus, syncing between devices, email aliases, and remote account recovery require network services or an external data source and therefore are not part of the offline core.

Product references:

- [1Password personal plans](https://1password.com/pricing/personal)
- [Bitwarden personal plan comparison](https://bitwarden.com/help/password-manager-plans/)
- [Proton Pass plans](https://proton.me/pass/pricing)
- [Dashlane personal plan comparison](https://support.dashlane.com/hc/en-us/articles/360001166969-Compare-Dashlane-personal-plans)
- [Keeper personal and family plans](https://www.keepersecurity.com/pricing/personal-and-family.html)
- [Bitwarden offline behavior](https://bitwarden.com/help/using-bitwarden-offline/)
- [1Password offline availability after sync](https://support.1password.com/sync/)

The comparison is a snapshot gathered on 23 October 2026. Plan names and included features can change; the official product pages should be rechecked before a public product decision.

## 6. Product boundaries

### In scope for the offline product

- Local vault creation and unlock.
- Encrypted local persistence.
- Password, username, URL, notes, and user-defined sensitive records.
- Password and passphrase generation using a cryptographically secure random source.
- Search, sorting, folders or tags, favorites, and duplicate detection.
- Copy-to-clipboard with a configurable clear timer and visible feedback.
- Manual encrypted backup and restore, with integrity checks.
- Import/export workflows with clear warnings for any unencrypted export.
- Local password health checks (length, reuse within the vault, and common-pattern heuristics if an offline data set is bundled).
- Lock on demand, inactivity lock, and best-effort cleanup of temporary secret data.

### Not part of the offline core

- Cloud sync, online accounts, remote recovery, telemetry, or advertising.
- Live dark-web or breach monitoring.
- Email-alias creation or other third-party identity services.
- Automated website password changes.
- Sending records to another person over the network.

Some of these could be considered as optional future integrations, but only after a separate privacy, security, and consent design. The core app remains usable if a user never enables or configures network access.

### Desktop autofill boundary

Browser autofill is a valuable premium feature, but implementing it safely requires browser integration and careful origin matching. It is a later product decision, not a prerequisite for a useful offline vault. The baseline first supports deliberate copy actions; a future local browser extension or local-only integration needs a separate threat model and permission design.

## 7. Functional requirements

### 7.1 First run and vault lifecycle

- Let the user create a vault protected by a master password.
- Explain before creation that the master password cannot be reset by a vendor and that a lost password may make the vault unrecoverable.
- Encourage a long, memorable passphrase; allow spaces and broad character input.
- Never store the master password in plaintext or provide a hidden recovery bypass.
- Support unlock, manual lock, inactivity lock, and safe close behavior.
- Provide a clear vault location and a user-controlled way to choose or move it.

### 7.2 Credential records

- Support login records with service name, username, password, website address, notes, and timestamps.
- Support secure notes and extensible record types such as payment cards, identity details, software licenses, and recovery codes.
- Keep fields encrypted at rest, including titles, URLs, tags, and metadata that could reveal account relationships.
- Permit edits, duplication, archive/delete, and recovery from a recycle area where practical.
- Confirm destructive actions and avoid accidental deletion.

### 7.3 Password and passphrase generator

- Use the platform's cryptographically secure random generator.
- Support configurable length, character sets, ambiguous-character exclusions, and user-defined rules for sites with constraints.
- Provide a passphrase mode with a bundled, licensed word list and configurable word count/separators.
- Ensure selected requirements are actually met (e.g., at least one character from each selected class) without predictable placement.
- Show generated secrets only when requested and allow direct saving into a record.
- Do not claim an exact strength or entropy score unless the calculation assumptions are explained.

### 7.4 Search and organization

- Search common fields locally without network calls.
- Provide folders and/or tags, favorites, sorting, and filters for record type.
- Make clear when search includes archived items or notes.
- Never log search text or secret fields.

### 7.5 Password health

- Detect weak or reused passwords within the local vault.
- Give actionable guidance without requiring users to change passwords on an arbitrary schedule.
- Optional offline blocklists must be bundled transparently, have a documented source and license, and show their update date. They must not silently contact an online service.
- Clearly distinguish local checks from live breach monitoring, which requires an explicitly enabled online lookup and its own design review.

### 7.6 Backup, restore, and portability

- Create encrypted backups that can be moved to removable storage.
- Verify backup integrity before restore and avoid overwriting the only existing copy without confirmation.
- Document that offline-only use makes the user responsible for backups and for retaining the master password.
- Support a documented, versioned file format and safe migration path.
- Make any plaintext export an explicit, warned action and provide a way to remove the exported file from the app's recent-files list; explain that secure deletion cannot be guaranteed on all storage devices.

### 7.7 Clipboard and display behavior

- Hide secret values by default in list and detail screens.
- Require an intentional action to reveal or copy a secret.
- Clear the clipboard after a short configurable delay where supported, and explain that other apps or clipboard managers may retain copies.
- Avoid placing secrets in notifications, logs, crash reports, accessibility labels, or window titles.

## 8. Security and privacy baseline

This section describes requirements to guide design. It is not a security certification or substitute for expert review.

### 8.1 Threat model

The product should protect against:

- A person obtaining a copy of the locked vault file.
- Accidental file corruption or unauthorized modification.
- Shoulder-surfing and inadvertent disclosure in the user interface.
- Accidental exposure through clipboard history, logs, exports, and backups.
- Brute-force guessing against a stolen vault, to the extent that password-based derivation and master-password strength permit.

The product cannot by itself protect data while it is unlocked on a compromised computer, from keyloggers or screen capture, from a user who shares their master password, from coercion, or from an attacker with control of the operating system and running process. Offline operation reduces network exposure but does not remove endpoint risk.

### 8.2 Cryptographic design requirements

- Use established, maintained cryptographic primitives and libraries; do not invent cryptography or a file format cipher.
- Encrypt vault data using authenticated encryption so tampering is detected as well as disclosure prevented.
- Derive encryption keys from the master password using a modern password-based key derivation function with a unique random salt and an adjustable work factor.
- Prefer a memory-hard derivation function such as Argon2id where a well-maintained implementation is available and can be packaged reliably. Consider scrypt as an alternative. PBKDF2-HMAC-SHA-256 is a compatibility option, configured and benchmarked according to current guidance.
- Store algorithm identifiers and work parameters in the vault header to support safe future upgrades. Validate parameters and file lengths before allocating resources.
- Generate salts, nonces, and random passwords using a cryptographically secure random source. Never reuse a nonce with the same key for GCM.
- Use separate derived keys or a vetted key-derivation scheme for distinct purposes if the design needs more than one key.
- Protect all sensitive fields, not only the password field.
- Keep decrypted data in memory only as long as practical. Use mutable character/byte buffers and best-effort clearing, while acknowledging that Java garbage collection and operating-system swap make perfect zeroization impossible.
- Write vault updates safely (temporary file, flush/close, atomic replacement where supported) and retain a recoverable backup strategy.

OWASP currently recommends Argon2id for password hashing, with scrypt as an alternative and PBKDF2-HMAC-SHA-256 at a stated work factor for environments requiring it; its cryptographic-storage guidance recommends authenticated modes such as GCM or CCM. These are starting points for design, not parameters to copy without platform benchmarking and security review. A password manager encrypts recoverable secrets, so its vault encryption/key-derivation design is distinct from merely hashing a website password for login verification.

### 8.3 Master password and lock controls

- Encourage a long master passphrase and support at least 64 characters, spaces, and Unicode input subject to carefully documented normalization behavior.
- Do not impose arbitrary character-class rules on a user's master password; provide a strength explanation and encourage length.
- Add an optional second local unlock factor only if its recovery and threat model are clear. Do not imply that a local PIN or biometric protects a stolen vault file unless the cryptographic design actually binds it to the key.
- Allow a short lock timeout, lock on application minimize/close where selected, and a prominent manual-lock action.
- Avoid storing a password hint that could help an unauthenticated person guess the master password.

### 8.4 Offline guarantee and optional connections

- Offline mode is the complete default product mode, not a trial mode.
- The offline build should contain no background telemetry, update check, remote fonts, analytics, crash upload, remote content, or implicit DNS/network access.
- If optional networking is later introduced, show what data will leave the device, the destination, the purpose, and whether secrets or derived values are sent. Require an affirmative opt-in for each feature category.
- Make network status visible and provide a global offline setting that blocks all app-initiated connections.
- Never send a master password or decrypted vault contents to an online service.
- For any breach lookup, investigate privacy-preserving protocols and clearly state residual disclosure and false-positive risks before implementation.
- Package dependencies and updates must be obtainable without requiring the running app to connect. Publish a signed offline installer and verify release integrity through a separate channel where feasible.

### 8.5 Security review and release practice

- Maintain a written threat model and data-flow diagram.
- Keep dependencies minimal, pinned, and reviewable; include license and vulnerability review.
- Use a reproducible or documented release build process and sign releases where feasible.
- Conduct independent cryptographic and application security review before claiming production-grade security.
- Publish a vulnerability-reporting path and a process for fixing and communicating security issues.
- Never describe the product as “unhackable,” “military grade,” or security-audited unless that exact claim is substantiated.

## 9. User experience principles

- Keep the first-run flow short and explain the consequences of master-password loss before vault creation.
- Make the main view understandable to a first-time user: search, records, add, generate, and lock.
- Use plain language for cryptographic and backup choices.
- Ensure keyboard access, screen-reader labels, adjustable text, and high-contrast visibility.
- Provide sensible defaults and explain advanced options only when needed.
- Use neutral strength guidance that helps without shaming.
- Make destructive operations reversible when practical.
- Never interrupt normal use with online upgrade prompts or advertisements.

## 10. Non-functional requirements

- **Offline behavior:** all core actions remain available without a network interface.
- **Data integrity:** corrupted or unsupported vaults fail closed with a clear recovery message; never replace a valid vault with an empty one automatically.
- **Portability:** vault and backup files are portable between supported installations with clear version compatibility.
- **Performance:** unlock time should be intentionally nontrivial for an attacker but reasonable on supported user hardware; the product must benchmark and explain its selected settings.
- **Accessibility:** all primary workflows can be completed with keyboard and assistive technologies.
- **Maintainability:** cryptographic operations and persistence are separated from UI code; formats are versioned and documented.
- **Compatibility:** supported Java versions and operating systems are explicitly listed before release.
- **Localization readiness:** user-facing strings and date/number formatting can be localized; secret bytes must not be altered by display locale.

## 11. Proposed delivery phases

### Phase 0 — Baseline and decisions

Agree on product scope, target operating systems, Java distribution, vault location model, security review expectations, and whether the first version is personal-use only.

### Phase 1 — Local vault foundation

Create/unlock/lock a local encrypted vault; add, view, edit, delete, search, and organize login records; implement generator; define format versioning, backup, restore, and error handling. No network code.

### Phase 2 — Useful vault breadth

Add secure notes and selected record types; improve tags/folders, favorites, clipboard timer, password/passphrase generation, local reuse checks, and encrypted export/restore.

### Phase 3 — Release readiness

Accessibility and usability review; threat-model review; dependency and licensing review; security assessment; packaging, signed releases, documentation, and recovery/backup guidance.

### Phase 4 — Optional integrations (separate approval)

Consider browser integration, local device-to-device transfer, or privacy-preserving breach checks only after the offline product is complete and each integration has a separate consent flow, data-flow description, threat model, and opt-in decision.

## 12. Initial acceptance criteria

The baseline product is ready for an initial user evaluation when:

1. A user can create and unlock a vault without registering or connecting to the internet.
2. A user can add, edit, search, and delete a login record while offline.
3. Secrets and identifying record fields are encrypted at rest; invalid passwords and tampered data do not yield partial plaintext.
4. A user can generate a random password and passphrase with configurable options while offline.
5. A user can create an encrypted backup and restore it on a disconnected installation.
6. A user can manually lock the vault and configure inactivity lock.
7. The app has no automatic outbound network behavior in its offline release.
8. The user is told clearly that loss of the master password or all backups can mean permanent data loss.
9. A documented test and independent security-review plan exists before any broad production-security claims.

## 13. Decisions still open

- Which operating systems are in the first release: Windows only, or Windows/macOS/Linux?
- Should the first release use a native Java desktop UI, or another Java UI toolkit?
- Should a vault be a single portable file, or a managed directory with journal/backup files?
- Where should the default vault live, and how will multiple vaults work?
- Which record types are required in the first release?
- Is a local browser integration essential, or is copy-to-clipboard sufficient for the first milestone?
- Which offline word list and weak-password data set can be bundled with acceptable licensing and update guidance?
- What minimum computer age/performance should unlock derivation support?
- Will the first public release claim general personal use only, or include a more formal security assurance target?
- Who will conduct the independent security review?

## 14. Research references

Technical guidance and product details were reviewed on 23 October 2026. Standards and vendor plans can change; re-check before implementation and release.

- NIST, [SP 800-63B-4: Authentication and Authenticator Management](https://pages.nist.gov/800-63-4/sp800-63b.html) — password length, composition guidance, password-manager support, and verifier storage guidance.
- NIST, [SP 800-63-4 implementation FAQs](https://pages.nist.gov/800-63-4-Implementation-Resources/faqs/) — summary of updated password requirements and password-manager compatibility.
- OWASP, [Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) — adaptive password derivation and current Argon2id, scrypt, and PBKDF2 recommendations.
- OWASP, [Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html) — established algorithms and authenticated encryption guidance.
- OWASP, [Key Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html) — key lifecycle, compromise, storage, and best-effort zeroization considerations.
- NIST, [How Do I Create a Good Password?](https://www.nist.gov/cybersecurity-and-privacy/how-do-i-create-good-password) — length and master-password considerations.
- Vendor capability references are listed in Section 5.

---

**Baseline status:** Ready for review and scope decisions. This document does not authorize or begin implementation.
