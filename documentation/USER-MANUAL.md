# Password Guruji — User Manual

**Brand:** Mirakinetix  
**Build:** 0.1.0 manual-testing build

Password Guruji is an offline password vault for Windows. Your vault stays on your device. There is no account or cloud sync in this build.

## 1. Install and start

Follow `documentation/INSTALLATION-GUIDE.md` to extract and start the application. Keep the application folder together; it includes the runtime needed to launch it.

On first launch, choose a vault location if desired, then enter and confirm a master password. The master password protects the vault and cannot be recovered if forgotten. Store it somewhere safe outside the vault.

This manual-testing build temporarily accepts master passwords from six characters. Use a stronger password for any real data. The intended minimum will be raised to 15 characters after local testing.

## 2. Open and lock the vault

Enter your master password to open the vault. Use **Lock** when you are finished or before leaving the computer unattended. You will need the master password to unlock it again.

## 3. Add and manage entries

Choose **New entry**, select the entry type offered by the form, and fill in the fields you want to save. Give the entry a recognizable name. Save it to add it to the vault.

Select an entry to view its details. Use **Edit** to change it. Use the favorite, folder, tag, or history controls shown in the app to organize or review entries. Moving an entry to the recycle bin removes it from the main list; use the recycle-bin controls to review or restore items if needed.

Use the search box and the available type, folder, and sort filters to find entries.

## 4. View and copy a saved value

Sensitive values are hidden by default. Use **Show** to reveal a value and **Copy** to copy it to the clipboard. Avoid leaving copied passwords on a shared computer, and do not paste them into messages or documents that others can access.

## 5. Generate a password

Open **Generate** to create a password. Choose the length and character options available in the generator, then copy or use the generated result in the entry you are creating. Check the target website's password requirements before saving a generated password.

## 6. Review password health

Open **Health** to review the password-health results provided by the application. Treat these results as guidance: they do not prove that an account is secure or that a password has not been exposed.

## 7. Back up and restore

Use **Backup / restore** to create or restore a vault backup. Keep backups in a secure location, separate from your everyday working files. Test that you can locate a backup before relying on it.

A backup is sensitive even when encrypted. Protect access to it and do not share it casually.

## 8. Import and export

Use **Import / export** for the formats offered by the application. Review imported data before relying on it.

CSV files are not encrypted. If you export to CSV, save it only where you can protect it, do not send it through email or messaging, and securely remove it when you no longer need it. Keep an encrypted vault backup before importing or making bulk changes.

## 9. Offline operation and current limits

The application is designed to work offline. This build does not provide cloud sync or browser autofill. Google Authenticator integration is planned for a later stage and is not part of this manual-testing build.

## 10. Protect your vault

- Never share your master password.
- Do not forget the master password; it cannot be reset or recovered.
- Make encrypted backups and store them securely.
- Lock the vault when you step away.
- Avoid keeping unencrypted CSV exports after use.
- Use the application with test data during manual testing.

For testing procedures and known out-of-scope areas, see `documentation/MANUAL-TEST-GUIDE.md`. For setup details, see `documentation/INSTALLATION-GUIDE.md`.