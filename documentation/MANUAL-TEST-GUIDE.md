# Password Guruji — Manual Test Guide

**Product:** Password Guruji  
**Brand:** Mirakinetix  
**Build under review:** `target/password-guruji-0.1.0-vscode-theme.jar`  
**Test scope:** Local desktop testing with a disposable vault and synthetic data

## Before testing

- Use a throwaway test vault and synthetic entries only. Do not enter real passwords, account recovery codes, payment details, or live one-time-password secrets.
- The current local test minimum for a new master password is **6 characters**. This is temporary for testing; the planned minimum after local testing is **15 characters**. Do not reuse a real password.
- Keep the test vault and any test backups in a dedicated temporary folder. Note the path so the tester can remove the test data after testing.
- The app is designed to work offline. Opening the LinkedIn or personal-site footer links launches the browser only after the tester clicks a link; do not click them unless that behavior is being checked.
- Record the app build, operating system, Java version if known, and test-vault path. Never include the master password or any secret in a screenshot or report.

## Test in scope

### 1. Launch and create a disposable vault

- Launch the build from the command shown below.
- Confirm the create-vault screen, app icon, theme colors, and persistent footer are visible.
- Confirm a password shorter than six characters is rejected and a mismatched confirmation is rejected.
- Create a vault with a unique, disposable password of at least six characters.
- If testing a custom vault location, choose a new test file path and confirm the displayed path updates.

**Expected:** The vault is created at the displayed path, and the tester reaches the unlocked vault screen.

### 2. Locking and reopening

- Lock the vault, then unlock it with the test password.
- Try one incorrect password and confirm it does not unlock the vault.
- Close and relaunch the app, then unlock the same test vault.

**Expected:** Correct credentials reopen the test vault; incorrect credentials do not reveal its contents.

### 3. Synthetic records and navigation

Create dummy records with obviously fake values, for example `Example test`, `tester@example.invalid`, and `example.invalid`.

- Create and edit a login record. Try the supported categories, folders, tags, notes, and custom fields.
- Check search, category/folder filters, favorites, and each sort option.
- Verify password values are not searched by the search box.
- Check show/hide controls and copy buttons using only synthetic values.
- Archive a dummy record, find it in the recycle bin, restore it, and—only if needed—permanently delete a dummy record.

**Expected:** Values persist after save, the visible list responds to filters and sorting, and archived records can be restored. Permanent deletion should only be tried on disposable records.

### 4. Password generator and health view

- Generate random passwords and passphrases.
- Try changing length, character groups, excluded characters, ambiguous-character handling, word count, and separator options.
- If saving a generated value as a record, use a synthetic entry.
- Review the password-health report with test entries that are intentionally weak or repeated.

**Expected:** Generator settings affect the generated output; the health view reports the test data without exposing secrets in the search field.

### 5. Optional per-entry TOTP check

Vault-unlock Google Authenticator/MFA is **not part of this build**. The app has a separate per-entry TOTP feature. If checking it, use a disposable test secret or a published test vector—never a live account secret.

**Expected:** A time-based code is displayed and refreshes on its normal timer. Skip this section if no safe test secret is available.

### 6. Backup, restore, import, and export

- Create an encrypted backup of the disposable vault and restore it to a separate test location.
- If available, try restoring with the wrong test password and confirm it fails safely.
- Test CSV import/export only with synthetic entries. Read the plaintext-export warning before continuing.
- Keep the original test vault and backup until restore results are confirmed.

**Expected:** Encrypted backup/restore preserves test records. CSV export is clearly identified as plaintext and the round-trip does not silently lose supported fields.

### 7. Settings, clipboard, footer, and offline behavior

- Set a short clipboard-clear interval and copy a synthetic value; check whether it clears after the configured interval.
- Set a short idle-lock interval, then leave the app inactive and check that it locks.
- Confirm the LinkedIn and `dparab.me` links remain visible in the footer on the locked and unlocked screens. Click only if willing to open the browser.
- If convenient, launch and use the app while disconnected from the internet; do not count an explicitly clicked footer link as an offline-app failure.

**Expected:** Clipboard and idle-lock settings follow their displayed values. No connection is initiated simply by launching or using vault features.

## Do not test in this phase

- Do not use production credentials, real vault files, live TOTP seeds, recovery codes, personal identity data, or payment information.
- Do not expect Google Authenticator or any other second factor to protect vault unlock; vault-unlock MFA is deferred to a later stage.
- Do not expect cloud sync, account sign-in, sharing, browser extensions/autofill, breach monitoring, online password checks, or automatic website connections. These are outside this offline build's test scope.
- Do not assess this test build as security-audited, production-certified, or ready for distribution. A manual functional pass is not a security audit.
- Do not permanently delete anything other than a disposable test record. Do not delete the test vault or backups until the owner confirms they are no longer needed.
- Do not treat the temporary six-character test minimum as the final password policy.

## Launch command

Run in PowerShell:

```powershell
Set-Location 'C:\Mirakinetix\mirakinetix-java\PasswordGuruji'
java -jar .\target\password-guruji-0.1.0-vscode-theme.jar
```

## Report a result

For each issue, include:

- Test section and step
- Result: Pass, Fail, Blocked, or Not tested
- Expected behavior and what actually happened
- App build and Windows version
- Reproduction steps using only synthetic data
- Screenshot with all secrets and personal data removed

Do not include the master password, generated passwords, TOTP secret/code, vault contents, or unencrypted CSV data in a report.
