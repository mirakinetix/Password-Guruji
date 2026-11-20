# Password Guruji — User Manual

**Brand:** Mirakinetix
**Build:** 0.1.0 manual-testing build

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

This manual-testing build temporarily accepts a master password of at least six characters to make local testing easier. That threshold is not suitable for a public production release; the planned minimum is 15 characters after testing.

The master password cannot be reset or recovered. If it is lost, the vault and its backups may become unusable. Keep encrypted backups and store the master password safely outside the vault.

For package extraction and Windows security prompts, follow the separate Installation Guide. The Manual Test Guide describes the test scope and areas testers should leave out of scope.

## 2. Create or open a vault

### Create your first vault

1. Start Password Guruji.
2. On the create-vault screen, optionally choose **Choose vault location** and select a new file location. The chosen file must not already exist.
3. Enter a master password and confirm it.
4. Select **Create my vault**.

The default Windows vault location is shown on the screen. Keep the vault somewhere you can back up, and avoid placing a test vault in a shared or synchronized folder.

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

Copied secrets are cleared from the clipboard after the configured timer when the clipboard still contains the copied value. Clipboard managers or other applications may retain their own copies, so do not copy secrets on a shared computer.

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

The backup remains encrypted and uses the vault's master password. A backup is sensitive; protect both the file and the password.

### Restore an encrypted backup

1. Choose **Restore from encrypted backup** and select a .pgv backup.
2. If needed, enter the backup's master password.
3. Review the record count and confirm replacement.

Restoring replaces the active vault contents. The restored records are saved using the current vault's master password. Create a backup of the current vault before restoring if you need to preserve it.

## 11. Import and export CSV

Choose **Import / export**. The app imports Password Guruji CSV exports; it does not import arbitrary CSV formats from other password managers.

- **Import Guruji CSV** asks you to confirm, choose a file, then confirm how many records to add. Imported records are added to the current vault.
- **Export plaintext CSV** asks for a separate warning confirmation, then lets you choose a destination. The CSV contains readable passwords and notes and is not encrypted.

Treat an exported CSV like an unprotected copy of your vault: save it only in a protected location, do not email or message it, and remove it when no longer needed. Deleting a file may not securely erase every copy from storage or backups. Make an encrypted vault backup before importing or exporting.

## 12. Settings and locking

Choose **Settings** to configure:

- **Inactivity lock:** 1, 5, or 10 minutes, or Never. The default is 5 minutes. Never disables automatic inactivity locking; use it only when appropriate.
- **Clipboard clearing:** 15, 30, or 60 seconds. The default is 30 seconds.
- **Change master password:** enter and confirm the new password. The current test build requires at least six characters; use a much longer passphrase.
- **Open vault folder:** opens the folder containing the active vault when supported by Windows.

Choose **Lock** to lock the vault immediately. Close the window to save and exit. Lock the vault before leaving the computer unattended.

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

For installation help, see the Installation Guide. For the tester's scope, see the Manual Test Guide. For vault-file details, see the Vault Format document.