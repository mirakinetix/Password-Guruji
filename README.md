# Password Guruji

Password Guruji is a Java desktop password manager and encrypted personal vault from Mirakinetix. The current build is designed to run locally without an account, cloud service, telemetry, or application network calls.

## Current feature set

- Create, unlock, and manually lock an encrypted local vault.
- Store login credentials, secure notes, identity/payment/recovery/license records, and custom fields.
- Generate random passwords and passphrases offline.
- Organize records with folders, tags, favorites, sorting, and local search.
- Review password reuse and common weak-password patterns locally.
- Store and generate TOTP authenticator codes for other accounts.
- Keep up to ten prior record snapshots, restore a record, and recover archived records from the recycle bin.
- Create and restore encrypted vault backups.
- Import and export Password Guruji CSV files. CSV export is plaintext and requires an explicit warning confirmation.
- Clear copied secrets from the clipboard after a configurable timer.

The app does not provide cloud sync, live breach monitoring, account recovery, browser autofill, or vault-unlock multifactor authentication. Google Authenticator support for unlocking the vault is planned for a later stage. TOTP codes for records stored in this vault are a separate feature.

## Development

- Java 17 or later.
- Maven.
- Build command: mvn package
- Run command: java -jar target/password-guruji-0.1.0.jar

The passphrase word list is bundled in the application resources. Generation does not contact the internet.

## Vault location

By default, Windows stores the vault at %LOCALAPPDATA%\Mirakinetix\PasswordGuruji\vault.pgv. Other systems use ~/.local/share/Mirakinetix/PasswordGuruji/vault.pgv. The first-run screen allows a user-selected location.

## Local testing configuration

For the initial local evaluation, the master-password minimum is temporarily six characters. After local testing is complete, raise this minimum to 15 characters. This temporary setting is not intended for a public release. The master password cannot be recovered; users must retain it and maintain encrypted backups.

## Security status

Vault data is encrypted with AES-GCM and a PBKDF2-HMAC-SHA-256 derived key. The current work factor and file format are documented in the source. This project has not received an independent security audit; do not treat it as production-certified or make claims that it is unbreakable or security-audited.
