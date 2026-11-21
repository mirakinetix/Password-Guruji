# Password Guruji — Cross-platform beta candidate

**Fictional credentials only. Current hardening candidate is pending its new hosted checks and owner approval. Production and real-secret use remain blocked.**

## Scope and architecture

The inspected application is Java Swing, not JavaFX. This work preserves Swing and builds/tests with Temurin Java 25. There are no production third-party Java dependencies; the test framework is JUnit. JavaFX dependencies or native JavaFX libraries are not required.

Native packages include a Java runtime. Maven builds require JDK 25, Maven 3.9+, and network access to resolve build dependencies. Runtime vault operations remain offline. The dependency scan contacts OSV with public package names/versions only.

## Candidate artifacts

After the current candidate passes all required checks, open its matching successful **Cross-platform beta candidate** run linked from the draft PR. Earlier successful runs validate older source only. Download the installer artifact for your OS and architecture, extract the Actions ZIP, and compare the native installer against its adjacent SHA-256 file. Artifacts expire after seven days; retain a reviewed copy if needed. They are CI artifacts, not a published GitHub release.

| System | Runner | Files | Installation verification intended |
| --- | --- | --- | --- |
| Windows x64 | windows-2022 | MSI and EXE | MSI installation/runtime/startup; uninstall/reinstall and data preservation |
| Ubuntu x64 | ubuntu-24.04 | DEB and RPM | DEB upgrade/runtime/startup/removal; Fedora RPM and Debian DEB container transactions |
| macOS Intel | macos-15-intel | PKG and DMG | PKG install/runtime/startup; uninstall/reinstall; DMG integrity |
| macOS Apple Silicon | macos-15 | ARM64 PKG and DMG | PKG install/runtime/startup; uninstall/reinstall; DMG integrity |

Consult RELEASE-READINESS.md for actual results rather than treating this table as a success claim. Windows runners use Windows Server, not Windows 11. The local Windows 11 desktop test supplies separate evidence. Fedora/Debian container checks are separate from physical desktops; no RHEL, Wayland desktop, physical Mac, or consumer Gatekeeper test is implied.

Installation verification also runs seven functional checks against the installed JAR and its bundled runtime: vault creation, encrypted record round trip, wrong-password rejection, password generation, bundled passphrase word list, TOTP vector, and tamper rejection. The native GUI launcher is checked for early process exit; visual rendering and installer wizard interaction remain manual tests.

Native package version **1.0.1** is a packaging build number required by Apple's positive-major-version rule. The application remains **0.1.0, beta candidate**, not a stable 1.0 release. The artifact metadata records both versions.

## Install on Windows

1. Choose the x64 artifact and verify with `Get-FileHash -Algorithm SHA256 'path-to-installer.msi'`.
2. Open either the MSI or EXE; do not install both. The install is per user and creates a Start menu shortcut.
3. Open Password Guruji from Start. Choose a new disposable test-vault location before creating anything.
4. Uninstall through Windows Settings → Apps. User vaults and settings are separate and are not automatically erased.

The installers are unsigned. If SmartScreen or organizational policy blocks them, record the exact message and stop. Do not disable protection.

## Install on macOS

1. Select **x64 for Intel** or **aarch64 for Apple Silicon**. Check About This Mac if uncertain.
2. Verify the downloaded file using `shasum -a 256 'installer.pkg'` and its checksum file.
3. Open the PKG installer, or open the DMG and copy Password Guruji to Applications. Choose one installation method.
4. Start the app from Applications and create a disposable vault.
5. To uninstall, remove the app from Applications. Keep/delete synthetic vault files separately as appropriate. PKG receipts may remain.

These packages have no Developer ID signature or notarization. Gatekeeper may block downloaded builds, especially on unmanaged consumer machines. CI installation is not proof that downloaded packages pass Gatekeeper. Do not disable Gatekeeper or remove quarantine attributes to bypass a block; report it. Signing/notarization requires a separate owner decision and potentially paid Apple membership.

## Install on Linux

For Ubuntu/Debian with a graphical desktop, verify with `sha256sum -c 'installer.deb.sha256'`, then use `sudo apt install ./installer.deb`. Start Password Guruji from the application menu. Remove with `sudo apt remove password-guruji`.

RPM packages are tested in a pinned Fedora container by the candidate workflow; consult the matching run for the outcome. RHEL and other RPM distributions remain unverified. Do not force-install an RPM on Ubuntu. A graphical session is required; CI uses Xvfb. Wayland behavior and desktop integrations need manual testing.

## Data locations

- Windows: `%LOCALAPPDATA%\Mirakinetix\PasswordGuruji\vault.pgv`.
- macOS: `~/Library/Application Support/Mirakinetix/PasswordGuruji/vault.pgv`.
- Linux: `$XDG_DATA_HOME/Mirakinetix/PasswordGuruji/vault.pgv`, or `~/.local/share/Mirakinetix/PasswordGuruji/vault.pgv` when XDG is unset/relative.
- Existing macOS/Linux vaults at the older `~/.local/share/...` location are retained and discovered. No automatic migration occurs.

The vault chooser permits another location. Use local storage for tests, not shared/cloud-synchronized folders.

## Tester checklist

Use disposable passwords only. New vaults and password changes in ordinary mode require at least **15 characters**. Explicit `--sample-vault` demonstration mode allows six; the committed fictional sample password is `123456`. Existing short-password vaults can still unlock. Copy samples to a disposable location before testing and never store real credentials in them.

- Install, launch, resize, navigate by keyboard, and check text/button contrast, icons, and footer links.
- Create a vault, lock, reject a wrong password, unlock, restart, and confirm records persist.
- Exercise all seven record categories, custom fields including empty values/Unicode, search, tags, folders, favorites, sorting, history, and recycle bin.
- Generate passwords with each option/exclusion and six-to-twelve-word passphrases.
- Test a disposable TOTP secret against an independent authenticator with a correctly set system clock.
- Copy a dummy secret; check timer clearing, immediate clearing on lock/close, and preservation of unrelated later clipboard content.
- Test idle lock with edit/generator/history dialogs open; confirm secret text disappears and dialogs close. Repeat after another test instance changes the vault: file conflicts must not prevent locking. Also check sleep/resume and display scaling.
- Back up, edit, restore, reject a wrong backup password, and reject a deliberately corrupted COPY of a dummy vault. Never corrupt the only original.
- Export/import synthetic CSV with Unicode, quotes, newlines and formula-like values. CSV v2 uses reversible text markers; verify values round-trip. Treat every CSV as plaintext sensitive data. Verify export/backup cannot overwrite the active vault or its lock file.
- Change the master password and confirm the old one is rejected for the active vault; older backups retain their old password.
- Run two app instances against one disposable vault: save in the first, then attempt a stale edit in the second. Expect rejection and rollback; reopen to see the first save. Use a private local folder, not a network/cloud share.
- Uninstall/reinstall; verify user test vaults are preserved. Test upgrade behavior separately.

Do not test real credentials, cloud sync, breach lookups, browser autofill, vault-unlock MFA, account recovery, or unimplemented mobile features. Do not interpret per-record TOTP as vault-unlock MFA. No destructive security probing of other systems is authorized.

## Safe bug reports and logs

Use the repository bug-report form. Include OS version, CPU architecture, commit/build identifier, installer format, steps, expected/actual result, and whether a synthetic vault reproduces it. Crop/redact screenshots, usernames, home-directory paths, clipboard contents, and record fields.

For startup diagnostics, run the JAR using Java 25 from a terminal with a disposable copy:

`java -jar password-guruji-0.1.0.jar --sample-vault /path/to/disposable-copy.pgv`

The application has no dedicated diagnostic logging system. Capture terminal errors only after reviewing/redacting them. Do not upload vaults, backups, CSV exports, master passwords, authenticator secrets, heap dumps, full process dumps, environment dumps, or full system logs. Report vulnerabilities privately through the owner's contact at https://dparab.me; public issues are unsuitable for sensitive reports.

## Developer verification

- `mvn -B verify`: core tests; desktop group intentionally not selected.
- `mvn -B -Pdesktop verify`: core and real desktop/clipboard tests. Requires a graphical session.
- Linux: `xvfb-run -a mvn -B -Pdesktop verify`.
- `python tools/package-native.py`: build native installers on the current supported OS after verify. Requires JDK 25/JAVA_HOME; WiX on Windows, rpm/fakeroot on Linux, Apple command-line tools on macOS.
- `tools/verify-installation.py` refuses to run outside disposable GitHub Actions environments.

The recorded local hardening run passed 69 tests; final refinements require a final rerun and the new hosted matrix remains pending. See RELEASE-READINESS.md for the authoritative final count, commit and run IDs. Functional tests do not certify cryptography, measure all entropy properties, or prove absence of memory copies. See SECURITY-REVIEW.md and SIGNING-REQUIREMENTS.md.
