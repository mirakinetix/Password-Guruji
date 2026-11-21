# Password Guruji — User Manual

**Brand:** Mirakinetix
**Application:** 0.1.0 beta candidate. **Native package build:** 1.0.1 (not a stable 1.0 release).

Password Guruji is an offline Java desktop password vault. The vault is stored on your device; the app has no account or cloud sync in this build.

> **Testing build:** This build is intended for local evaluation with synthetic data. It has not received an independent security audit. Do not store real passwords, financial information, identity details, recovery codes, or authenticator secrets in it.

## Contents

1. Before you begin
2. Create or open a vault
3. Find and organize records
4. Add or edit a record
5. View, reveal, and copy values
6. Generate a password or passphrase
7. Add an authenticator code to a record
8. Review password health
9. Use record history and the recycle bin
10. Back up and restore
11. Import and export CSV
12. Settings and locking
13. Offline behavior and limitations
14. Troubleshooting

## 1. Before you begin

Normal launches require at least **15 characters** for a new master password or a password change. Existing shorter passwords can still unlock older vaults; Settings lets you replace them with a longer password. Minimum length alone does not guarantee password strength.

Only explicit demonstration mode, launched with `--sample-vault` and a disposable vault path, accepts six characters and shows a demonstration notice. The supplied fictional sample uses `123456`; this is not a default master password for normal vaults. See [Sample vault](SAMPLE-VAULT.md). Use synthetic data in both modes during this beta evaluation.

The master password cannot be reset or recovered. If it is lost, the vault and its backups may become unusable. Keep encrypted backups and store the master password safely outside the vault.

For native installation on Windows, macOS, or Linux and security prompts, follow the [Installation Guide](INSTALLATION-GUIDE.md). Hosted installer evidence is pending; the [Release readiness](RELEASE-READINESS.md) report records actual results. The Manual Test Guide describes test scope.

## 2. Create or open a vault

### Create your first vault

1. Start Password Guruji.
2. On the create-vault screen, optionally choose **Choose vault location** and select a new file location. The chosen file must not already exist.
3. Enter a master password and confirm it.
4. Select **Create my vault**.

The selected vault location is shown on the screen. Default locations for each operating system are listed in the Installation Guide. Use a private local folder that supports private file permissions and atomic file replacement. Do not use a shared or cloud-synchronized working folder, a symbolic link, or a hard-linked sensitive file. Unsupported storage causes the save to fail; the app does not fall back to a non-atomic save.

A sibling file such as `vault.pgv.lock` remains beside the vault to coordinate file access. Its presence after closing the app is normal. Do not delete it to bypass a warning or use it as an export/backup destination. Avoid editing the same vault in multiple app instances; stale changes are rejected rather than silently overwriting newer records.

### Open an existing vault

1. Start the app and check the vault-file path shown on the locked screen.
2. Enter the master password and choose **Unlock vault**.
3. If you need a different vault file, choose **Open another vault**, select its .pgv file, and unlock it with that vault's master password.

An incorrect password will not unlock the vault. The app does not have a password-recovery service.

## 3. Find and organize records

The main screen has the record list on the left and the selected record's details on the right.

- **Search** checks record names, usernames, websites, notes, folders, tags, record types, and custom-field names. It deliberately does not search password values.
- The **record filter** can show all records, favorites, one record type, or the recycle bin.
- The **folder filter** narrows the list to a folder already used by a record.
- **Sort** offers Name A–Z, Recently updated, and Oldest updated.
- The record count at the bottom of the list reflects the current filters.

A record can be marked as a favorite from its detail view or its edit form. Folders and comma-separated tags can be set in the edit form. Search and filtering are local to the vault.

## 4. Add or edit a record

1. Choose **New entry**.
2. Give the record a name and choose a type: Login, Secure note, Identity, Payment, Recovery code, License, or Other.
3. Fill in any applicable fields:
   - **Username / value**
   - **Password / secret**
   - **Website**
   - **Folder**
   - **Tags (comma separated)**
   - **Other fields**, one name=value pair per line
   - **Notes**
   - **Favorite**
4. Choose **OK** to save.

The form uses common fields for all record types. Use the notes and custom fields to label information for Identity, Payment, Recovery code, License, Secure note, or Other records. For example, custom fields can hold labels such as “Account number” or “Expiry date.” Avoid entering live sensitive information while evaluating this test build.

To change a record, select it and choose **Edit**; double-clicking a row also opens the edit form. Save with **OK**. The record’s updated date changes when it is saved.

## 5. View, reveal, and copy values

Select a record to view it. Passwords, usernames, and custom-field values are masked by default. Choose **Show** to reveal a masked value, then **Hide** to mask it again. Choose **Copy** to copy a field. Website and notes are displayed as ordinary text and also have a Copy button.

The Copy button and keyboard copy from displayed record values use the same clipboard protection. The app clears its clipboard item on timeout, vault lock, and normal exit. A later copy by another app is left in place, even if the text is identical. Clipboard history, sync, and third-party managers may retain their own copies.

## 6. Generate a password or passphrase

Choose **Generate** in the top action bar.

### Random password

- Set the length from 8 to 128 characters.
- Choose uppercase, lowercase, numbers, and symbols. At least one character type must be selected.
- **Exclude ambiguous characters** is enabled by default.
- Optionally enter additional characters to exclude.
- Choose **Generate** to make a new password. The app ensures each selected character type is represented.
- Use **Show** or **Hide** to reveal or mask the result, **Copy** to copy it, or **Save as record** to create a Login record containing the generated password.

### Passphrase

Select **Use passphrase**, choose 6 to 12 words, and set a separator (the default is a hyphen). Choose **Generate** to create a passphrase from the word list bundled with the app. Generation happens offline.

The displayed strength is an approximate random-choice estimate based on the selected options. It is not a guarantee that a website will accept the generated value or that an account is secure.

## 7. Add an authenticator code to a record

Password Guruji can store a TOTP authenticator secret for an account and display rotating one-time codes. This is separate from using an authenticator to unlock Password Guruji; vault-unlock multifactor authentication is not included in this build.

1. Edit the account record.
2. In **Other fields**, add a line whose field name is exactly **TOTP secret**, followed by an equals sign and the account's Base32 setup key. For example, enter the field name, then paste the secret provided by the account's authenticator setup flow.
3. Save the record. The app validates the secret; invalid Base32 values are rejected.
4. Open the saved record. Its current **Authenticator code** appears and refreshes automatically. Choose **Copy code** to copy the current code.

Use only a synthetic secret during testing. Keep real authenticator setup keys private; anyone with the key can generate codes for that account. The app does not scan QR codes in this build.

## 8. Review password health

Choose **Health** to run the local vault check. The report shows the number of records scanned, potentially weak passwords, and reused-password groups, with record names to review.

The check uses local heuristics, including short passwords, a small set of common passwords, repeated or sequential patterns, a password matching its username, and Login records without a password. It does not contact the internet or check breach databases. A clean report does not certify account security.

## 9. Use record history and the recycle bin

### History

Choose **History** on a record to see available previous snapshots. Select a snapshot and confirm **Restore** to make it the current version. Restoring creates another history version. Up to ten earlier snapshots are retained per record; a new record has no history until it changes.

### Recycle bin

For a normal record, choose **Move to recycle bin** and confirm. To recover it, set the record filter to **Recycle bin**, select the record, and choose **Restore**. To remove an archived record permanently, choose **Delete permanently** and confirm; this cannot be undone.

## 10. Back up and restore

Choose **Backup / restore**.

### Create an encrypted backup

1. Choose **Create encrypted backup**.
2. Select a destination different from the active vault file.
3. Store the backup somewhere separate from the computer, such as protected removable storage.

The backup remains encrypted and uses the vault's master password at the time of backup. Changing the active vault's password does not change older backups. A backup is sensitive; protect both the file and the password. Choose a new filename. A backup copies the saved encrypted vault without changing the original and refuses to replace any existing file. The destination filesystem must support private permissions and atomic creation by hard link. If removable media does not support these, create the backup in a private local folder and copy the encrypted file to the media.

### Restore an encrypted backup

1. Choose **Restore from encrypted backup** and select a .pgv backup.
2. If needed, enter the backup's master password.
3. Review the record count and confirm replacement.

The backup can be read from read-only storage. The app authenticates it without creating a lock beside it. Restoring replaces the active vault contents only after confirmation. The restored records are saved using the current vault's master password. Create a backup of the current vault before restoring if you need to preserve it. For recovery testing, work with copies in separate locations first; never overwrite the only original while investigating a failed unlock or damaged file. File corruption and a lost master password have no automatic recovery service.

## 11. Import and export CSV

Choose **Import / export**. The app imports Password Guruji CSV exports; it does not import arbitrary CSV formats from other password managers.

- **Import Guruji CSV** asks you to confirm, choose a file, then confirm how many records to add. Imported records are added to the current vault.
- **Export plaintext CSV** asks for a separate warning confirmation, then lets you choose a destination. The CSV contains readable passwords and notes and is not encrypted.

New exports use the Password Guruji CSV v2 header `Name [PGCSV2]`. Every data cell starts with the literal marker `PGTEXT:` so formula-like values remain text when exported. The importer removes that marker and restores the original values; do not remove markers or change the header manually. Tag and custom-field encodings are for lossless app import, not encryption. Earlier Password Guruji CSV headers are also accepted, but old exports do not have these formula protections. The importer and exporter share a 32 MiB file limit and an 8 Mi UTF-16 code-unit cell limit. Larger exports fail before replacing a destination. The exporter requires a `.csv` destination and refuses to replace an encrypted vault or the active lock file.

CSV is a transfer format, not a complete vault backup: imports receive new record identities and do not restore record history or application settings. Use an encrypted backup when those vault records and histories must be preserved. Application settings remain separate from vault files.

Treat an exported CSV like an unprotected copy of your vault: save it only in a protected location, do not email or message it, and remove it when no longer needed. Avoid opening password exports in spreadsheet software; editing, conversions, or older exports can lose text protections. Deleting a file may not securely erase every copy from storage or backups. Make an encrypted vault backup before importing or exporting.

## 12. Settings and locking

Choose **Settings** to configure:

- **Inactivity lock:** 1, 5, or 10 minutes, or Never. The default is 5 minutes. Never disables automatic inactivity locking; use it only when appropriate.
- **Clipboard clearing:** 15, 30, or 60 seconds. The default is 30 seconds.
- **Change master password:** enter and confirm a password of at least 15 characters in normal mode, or six in explicit demonstration mode. Older backups retain their original password.
- **Open vault folder:** opens the folder containing the active vault when supported by the desktop.

Accepted record changes are saved when you confirm them. If saving fails, the app reports the failure and restores the previous records. Choose **Lock** to lock immediately, or close the window to exit; neither action requires another save. Lock the vault before leaving the computer unattended.

## 13. Offline behavior and limitations

Normal vault use, password generation, passphrase generation, TOTP code generation, and password-health checks work locally. The LinkedIn and dparab.me footer links open a web browser only when clicked.

This build does not provide cloud sync, browser autofill, live breach monitoring, account recovery, or multifactor authentication for unlocking the vault. Google Authenticator-style verification for vault unlock is planned separately; stored TOTP codes for other accounts are available as described above.

## 14. Troubleshooting

- **The two master passwords do not match:** re-enter the same password in both fields.
- **The vault does not unlock:** check that the selected vault path is correct, then try the master password used when that vault was created. There is no reset or recovery process.
- **The vault file already exists when creating a vault:** choose a new file location. Do not overwrite an existing vault.
- **A TOTP secret is rejected:** copy the Base32 setup key exactly, without spaces or an authenticator URI. Use a synthetic secret for testing.
- **A CSV file is rejected:** the importer accepts only Password Guruji CSV exports with the expected header and format.
- **A restore asks for a password:** enter the master password that protects the selected backup. Confirm carefully because restore replaces the active vault.
- **The app locks while you are using it:** adjust the inactivity interval in **Settings**.
- **Another instance is writing the vault:** let that operation finish and close extra instances. Do not remove the `.lock` file. Its continued presence is not itself an error.
- **The vault changed in another instance:** your stale changes were not saved. Close the app and reopen the latest vault before editing again. Do not overwrite it with an older copy.
- **Private permissions, atomic replacement, read-only, or access errors:** leave the original file intact. Check available disk space and folder access. With all app windows closed, copy the vault to a private supported local folder and open that copy. Do not weaken permissions or bypass the safe-save checks.
- **An interrupted save leaves a `.pending` file:** preserve the original vault and existing backups. Do not rename temporary files over the vault; they may be incomplete. Check a copy of the original or restore a known-good encrypted backup to a separate location first.
- **The updated app cannot open a vault:** verify the selected path and build identifier, preserve the original, and test a known-good backup copy. Do not assume an older application can read the newest vault format.

For reports, include the operating system, architecture, source commit from `build-metadata.json`, installer format, exact error, and steps using fictional data. Redact usernames and home-folder paths. Never upload a vault, backup, CSV export, master password, authenticator secret, heap dump, or full process dump.

For installation help, see the Installation Guide. For the tester's scope, see the Manual Test Guide. For vault-file details, see the Vault Format document.
