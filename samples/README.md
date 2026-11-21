# Ready-made sample data

**Master password: `123456`**

**All record passwords: `123456`**

These are public, fictional examples. Do not put real credentials in these files.

- [sample-vault.pgv](sample-vault.pgv): ten records covering all seven types, favorites, folders, tags, custom fields, TOTP, history, and the recycle bin.
- [sample-backup.pgv](sample-backup.pgv): encrypted starting backup, also using `123456`.
- [sample-data.csv](sample-data.csv): plaintext examples for import/export practice.

From the repository root, build with `mvn package`, then run `.\samples\Start-Sample.ps1`. Java 25 and Maven are needed for this source checkout. The portable Windows test ZIP includes Java and has its own **Start Sample Vault.cmd** launcher.

On Linux/macOS, copy the sample vault to a disposable writable folder and run `java -jar target/password-guruji-0.1.0.jar --sample-vault /absolute/path/to/the-copy.pgv`. Never point the app at the original sample in an installation directory.

The source launcher creates `samples/working/sample-vault.pgv` on first use and preserves it on later launches. To reset, close the app and delete only that working copy, then launch again. The working folder is ignored by Git.

Sample settings use a separate preferences area: one-minute inactivity lock and 15-second clipboard clearing. These defaults reset on each sample launch. Ordinary vault settings are unaffected.

See the [sample guide](../documentation/SAMPLE-VAULT.md) for backup and settings details and the [user manual](../documentation/USER-MANUAL.md) for feature instructions.
