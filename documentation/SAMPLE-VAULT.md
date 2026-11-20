# Sample vault

After extracting the Windows package, open **Start Sample Vault.cmd**.

**Sample master password: 123456**

Every sample record's password is also **123456**. All records are fictional and the authenticator seed is public test data. Never add real secrets to this sample vault.

The sample contains ten records covering Login, Secure note, Identity, Payment, Recovery code, License, and Other. It includes folders, tags, favorites, notes, custom fields, a TOTP example, a previous record version, and an archived record in Recycle bin. Health deliberately reports weak and reused passwords.

The launcher opens Sample/sample-vault.pgv directly. Sample settings use a separate preferences area from ordinary app settings: inactivity lock is one minute and clipboard clearing is 15 seconds. You can exercise every Settings option; these two defaults reset whenever you use the sample launcher. Settings are app preferences and are not stored inside vault files.

Sample/sample-backup.pgv is an untouched starting backup, also protected by 123456. Use Backup / restore to restore it. If you changed the working vault's master password, restoring retains that current password. To return to the original password too, close the app and replace only Sample/sample-vault.pgv with a copy of Sample/sample-backup.pgv.

Sample/sample-data.csv is a plaintext export for practising import. Import adds records and can create duplicates. Use only these fictional files when testing export, restore, deletion, or master-password changes.

Run Password Guruji/Password Guruji.exe normally to use the ordinary vault and preferences. The sample launcher does not select or overwrite your ordinary vault.
