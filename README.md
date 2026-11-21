# Password Guruji

![Password Guruji — Your Guide to Stronger Passwords](documentation/brand-assets/password-guruji/Password-Guruji_logo_banner.png)

Password Guruji is an offline-first Java desktop password manager and encrypted personal vault from Mirakinetix.

> **Current status: manual-testing build.** Use synthetic data only. This project has not received an independent security audit and is not a production-certified password manager.

## Try the included sample

**Sample master password: `123456`**

**Every sample record password: `123456`**

Ready-made fictional data is in [samples](samples/README.md): [sample vault](samples/sample-vault.pgv), [encrypted backup](samples/sample-backup.pgv), and [plaintext CSV](samples/sample-data.csv).

With Java 25 and Maven installed, run these commands from the repository folder:

```powershell
mvn package
.\samples\Start-Sample.ps1
```

The launcher creates a working copy and opens it with separate sample settings. It preserves the supplied original vault. For the portable Windows ZIP, use **Start Sample Vault.cmd** instead. These public credentials are for fictional demonstrations only.

## Features in this build

- Create, unlock, and manually lock an encrypted local vault.
- Store login, secure note, identity, payment, recovery code, license, and other records with custom fields.
- Generate random passwords and offline passphrases.
- Organize records with folders, tags, favorites, local search, sorting, and filters.
- Review weak-password patterns and password reuse locally.
- Store TOTP secrets for other accounts and generate rotating codes offline.
- Keep up to ten prior record snapshots and restore records from history or the recycle bin.
- Create and restore encrypted vault backups.
- Import Password Guruji CSV exports and export plaintext CSV with a warning.
- Clear copied secrets from the clipboard after a configurable timer.

This build does not provide cloud sync, live breach monitoring, account recovery, browser autofill, or multifactor authentication for vault unlock. TOTP codes for records are supported; using Google Authenticator to unlock the vault remains deferred.

## Documentation

- [Cross-platform candidate installation and testing](documentation/CROSS-PLATFORM-BETA.md)
- [Release readiness and verified evidence](documentation/RELEASE-READINESS.md)
- [Focused security review](documentation/SECURITY-REVIEW.md)
- [Sample vault and password](documentation/SAMPLE-VAULT.md)
- [User Manual](documentation/USER-MANUAL.md)
- [Installation Guide](documentation/INSTALLATION-GUIDE.md)
- [Manual Test Guide](documentation/MANUAL-TEST-GUIDE.md)
- [Vault Format](documentation/VAULT-FORMAT.md)
- [Third-Party Notices](documentation/THIRD-PARTY-NOTICES.md)
- [Project Baseline](documentation/Password-Guruji-Project-Baseline.md)

## Build from source

Requirements: Java 25 and Maven 3.9 or later. The UI uses Swing, not JavaFX.

- Build the runnable JAR: **mvn package**
- Run it: **java -jar target/password-guruji-0.1.0.jar**
- Create a portable Windows manual-test ZIP with bundled Java runtime: **.\tools\package-manual-test.ps1**

The packaging script writes the ZIP and a SHA-256 checksum under target. It uses the installed JDK's jpackage tool and does not download a runtime.

## Vault storage and security

By default, Windows stores the vault at %LOCALAPPDATA%\Mirakinetix\PasswordGuruji\vault.pgv. Other systems use ~/.local/share/Mirakinetix/PasswordGuruji/vault.pgv. The first-run screen lets the user choose a different location.

The local-testing master-password minimum is temporarily six characters for ease of evaluation. Raise it to 15 after local testing; the six-character setting is not intended for a public production release. The master password cannot be recovered, so users must retain it and keep encrypted backups.

Vault data uses AES-GCM encryption with a PBKDF2-HMAC-SHA-256-derived key. See the Vault Format document for current parameters and compatibility. These implementation details do not constitute a security audit or guarantee of safety.
