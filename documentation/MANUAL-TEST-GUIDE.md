# Password Guruji — Manual Test Guide

**Product:** Password Guruji
**Brand:** Mirakinetix
**Build:** Unsigned 0.1.0 beta candidate; native installer build 1.0.1 on Windows, Linux and macOS. Record the source commit from BETA-CANDIDATE.txt.
**Test scope:** Local desktop testing with a disposable vault and synthetic data

## Before testing

- Use a throwaway test vault and synthetic entries only. Do not enter real passwords, account recovery codes, payment details, identity data, or live TOTP secrets.
- The minimum for a new normal vault is **15 Unicode characters**. Only explicit sample mode permits six. Never reuse a real password. Existing short-password vaults must still unlock.
- Keep the test vault and test backups in a dedicated temporary folder.
- Record the build ID from START-HERE.txt, Windows version, and test-vault path. Never put a master password or secret in a screenshot or report.
- The app is designed to work offline. Footer links open a browser only when clicked.

## Test in scope

### 1. Launch and create a disposable vault

- Launch **Password Guruji\Password Guruji.exe** from the extracted ZIP.
- Confirm the create-vault screen, icon, dark theme, footer, and displayed vault path.
- Confirm passwords shorter than 15 characters and mismatched confirmation are rejected.
- Create a vault with a unique, disposable password of at least 15 characters.
- Choose a new custom vault path and confirm it is displayed.

**Expected:** The vault is created at the displayed path and the unlocked vault screen appears.

### 2. Locking and reopening

- Lock the vault, then unlock with the test password.
- Try an incorrect password and confirm it does not unlock the vault.
- Close and relaunch the app, then reopen the same test vault.

**Expected:** Correct credentials reopen the test vault; incorrect credentials do not reveal its contents.

### 3. Records and navigation

Create synthetic records with fake values such as Example test, tester@example.invalid, and example.invalid.

- Try each record type: Login, Secure note, Identity, Payment, Recovery code, License, and Other.
- Try folders, comma-separated tags, notes, custom fields, favorites, and editing.
- Confirm search finds names, usernames, websites, notes, folders, tags, and custom-field names, but not password values.
- Check category/folder filters, each sort option, and double-click editing.
- Check masked values, Show/Hide, and Copy using synthetic values only.
- Archive a dummy record, find it in the recycle bin, restore it, and permanently delete only a disposable record.
- Edit a record and inspect History; try restoring a prior snapshot.

**Expected:** Saved values persist, list controls work, archived records can be restored, and record history can be reviewed.

### 4. Password generator and health view

- Generate random passwords using different lengths, character groups, ambiguous-character exclusions, and additional excluded characters.
- Generate passphrases using different word counts and separators.
- Try Show/Hide, Copy, and Save as record with a synthetic result.
- Review the health report with intentionally weak and repeated test passwords.

**Expected:** Options affect generated values; the health report identifies local weak/reused test entries. It does not contact breach databases.

### 5. Per-record TOTP

Vault-unlock Google Authenticator/MFA is **not part of this build**. Per-record TOTP is a separate feature. Use only a synthetic secret or a published test vector; never use a live account seed.

- On a synthetic record, add a custom field named **TOTP secret** and enter a valid test Base32 secret as its value.
- Save and open the record. Confirm a code appears, refreshes, and can be copied.
- Optionally try an invalid value and confirm it is rejected.

**Expected:** The code updates on its timer; a live authenticator seed is never needed for testing.

### 6. Backup, restore, import, and export

- Create an encrypted backup of the disposable vault and restore it.
- Test the backup password prompt only with disposable test data.
- Import a Password Guruji CSV export and confirm the records are added.
- Export synthetic records to CSV and read the plaintext warning before proceeding.

**Expected:** Backup restore preserves test records. CSV is visibly identified as plaintext; do not use unrelated CSV formats.

### 7. Settings, clipboard, footer, and offline behavior

- Set a clipboard-clear interval and copy a synthetic value. Check whether it clears after the configured interval.
- Set a short inactivity-lock interval, leave the app idle, and check whether it locks.
- Confirm LinkedIn and dparab.me remain in the footer on locked and unlocked screens. Click a link only if willing to open a browser.
- If convenient, use the app while disconnected from the internet.

**Expected:** Settings follow their displayed values. Launching and using vault features should not initiate a network connection.

## Do not test in this phase

- Do not use production credentials, real vaults, live TOTP seeds, recovery codes, payment details, or identity data.
- Do not expect vault-unlock MFA, cloud sync, account sign-in, sharing, browser autofill, breach monitoring, online password checks, or automatic website connections.
- Do not assess this build as security-audited, production-certified, or ready for general release. A functional test is not a security audit.
- Do not permanently delete anything except a disposable test record. Do not remove test vaults or backups until the owner confirms.
- Do not treat the temporary six-character test minimum as the final password policy.

## Report a result

For each issue, include the test section, result (Pass/Fail/Blocked/Not tested), expected and actual behavior, build ID, Windows version, reproduction steps using synthetic data, and a screenshot with secrets removed.

Never include the master password, generated passwords, TOTP secret/code, vault contents, or plaintext CSV in a report.
## Hardening regression checklist

- Change a synthetic legacy six-character master password to 15 or more characters. Confirm the old password fails and the new one reopens the vault.
- Open the same disposable vault in two instances. Save in the first, then attempt a change in the second. The second must report a conflict and keep the first save intact. Lock must still work after the conflict.
- While an edit dialog contains fictional secrets, allow idle locking. The dialog must close and secrets must disappear. Unlock must show only saved records.
- Copy a fictional value, then copy unrelated text from another application, including identical text. Guruji must not clear the other application's copy. Check timeout, lock and graceful close separately.
- Make a disposable vault read-only and attempt a save. Confirm an error and unchanged file. Restore write access before continuing.
- Export CSV and inspect text markers: PGCSV2 header and PGTEXT: prefixes are intentional formula protection. Reimport and compare original values, Unicode, empty values, tags and custom fields. CSV does not preserve history or deleted records.
- Reject export/backup destinations that would replace the active vault or its .lock sidecar. Never remove a lock file while instances are open.
- Test keyboard-only navigation, focus visibility, long paths, 100%, 150% and 200% display scaling, small supported window sizes, and light/dark OS settings. Record visual problems with synthetic-only screenshots.
- Test installer upgrade and removal with disposable data. Confirm the vault survives. Do not bypass SmartScreen or Gatekeeper; record unsigned-package warnings.

Automated fault injection covers disk-full and failed atomic replacement. Physical power loss, real exhausted volumes, accessibility tooling, and downloaded-file OS trust prompts require separate controlled manual validation. Do not describe these as passed based on automated compilation or startup alone.
