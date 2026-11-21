# Password Guruji — Installation Guide

**Brand:** Mirakinetix. **Application:** 0.1.0 beta candidate. **Native package build:** 1.0.1.

Use synthetic test data only. These unsigned packages are not production-approved and have not received an independent security audit. The numeric native version is an installer build number, not a stable 1.0 application release. Hosted installation evidence is pending; consult [Release readiness](RELEASE-READINESS.md) for actual results.

## Download and verify

Download the artifact for your operating system and processor from the reviewed **Cross-platform beta candidate** workflow run. Extract the Actions ZIP. Keep the installer, its adjacent `.sha256` file, and `build-metadata.json` together. The metadata identifies the exact source commit, application version, native version, and application JAR hash. CI artifacts expire after seven days; retain a reviewed copy when needed. They are not a published release.

Compare the installer checksum with its matching file before opening it:

- Windows PowerShell: `Get-FileHash -Algorithm SHA256 'path-to-installer.msi'` (or `.exe`).
- macOS Terminal: `shasum -a 256 'path-to-installer.pkg'` (or `.dmg`).
- Linux Terminal: `sha256sum -c 'path-to-installer.deb.sha256'` from the installer directory (or `.rpm.sha256`).

A matching hash checks file consistency; it does not establish publisher identity. Verify the source of the artifact too. Never install the `upgrade-fixture` package: it is an internal same-source installer test, not an older release.

## Windows x64

1. Open either the MSI or the EXE installer; choose one format.
2. Complete installation for your Windows user account.
3. Open **Password Guruji** from the Start menu.
4. Choose a new disposable vault location before creating test data.

Java is included. No separate Java installation is required. Remove the app through **Settings → Apps**. Vault files, backups, and user settings are separate from the application and should be retained unless you deliberately remove disposable test data.

## macOS Intel or Apple Silicon

1. Choose **x64** for Intel or **aarch64** for Apple Silicon; check **About This Mac** if uncertain.
2. Open the PKG installer, or open the DMG and copy **Password Guruji.app** into Applications. Choose one method.
3. Start the app from Applications and choose a disposable vault location.
4. To uninstall, close the app and remove its application bundle from Applications. User vaults remain separate; PKG installation receipts may remain.

The runtime is included. Packages are unsigned and not notarized. Hosted installation tests do not prove that a downloaded artifact will pass consumer Gatekeeper checks.

## Linux x64

A graphical desktop is required. On Ubuntu/Debian, install the verified DEB with `sudo apt install ./installer.deb`. On Fedora, install the verified RPM with `sudo dnf install ./installer.rpm`. Substitute the actual downloaded filename. Do not force-install an RPM on Ubuntu or ignore dependency failures.

Start Password Guruji from the application menu. Remove with `sudo apt remove password-guruji` or `sudo dnf remove password-guruji`. The Java runtime is included. Native package installation, container compatibility, full desktop integration, and Wayland behavior have different test scopes; check the readiness report rather than assuming all distributions are supported.

## Security prompts

Windows or macOS may block an unsigned download. Record the exact message and stop if protection or organizational policy blocks installation. Do not disable Defender, SmartScreen, Gatekeeper, or antivirus, and do not remove quarantine attributes to bypass a block. Report the installer filename, operating system, architecture, and source commit without including secrets.

## First vault and demonstration mode

Normal launches require at least **15 characters** when creating a vault or changing its master password. Existing vaults with shorter passwords can still unlock; change those passwords in Settings. The master password cannot be reset or recovered.

Only explicit demonstration mode (`--sample-vault` followed by a disposable vault-file path) uses a six-character minimum and displays a demonstration notice. The supplied fictional sample vault password is `123456`; it is not a default password for new vaults. See [Sample vault](SAMPLE-VAULT.md) for copying and launching the sample. Use synthetic data in both modes while evaluating this beta.

## Storage and safe upgrades

Default vault locations:

- Windows: `%LOCALAPPDATA%\Mirakinetix\PasswordGuruji\vault.pgv`.
- macOS: `~/Library/Application Support/Mirakinetix/PasswordGuruji/vault.pgv`.
- Linux: `$XDG_DATA_HOME/Mirakinetix/PasswordGuruji/vault.pgv`, or `~/.local/share/Mirakinetix/PasswordGuruji/vault.pgv` when XDG is unset or relative.

Existing macOS/Linux vaults in the older `~/.local/share/...` location remain discoverable. The chooser can select a different location.

Use a private local folder on a filesystem supporting private file permissions and atomic file replacement. Shared folders, cloud-synchronized folders, symbolic links, and hard-linked sensitive files are unsuitable. A save fails rather than falling back to a non-atomic replacement. Removable storage without suitable permissions may also fail; keep the working vault on supported local storage.

A persistent sibling such as `vault.pgv.lock` coordinates access. Its presence is normal even after the app exits. Do not delete or edit it to bypass a conflict, and never select it as a vault or backup destination.

Before an upgrade, create an encrypted backup, record its location and the password that protects it, then close every app window. Keep a copy of the prior installer. Install the reviewed candidate and verify a disposable vault before proceeding with testing. Do not assume an older application can read a vault rewritten by a newer build.

If an update or save fails, preserve the existing vault and backup. Do not overwrite the only copy or repeatedly force a save. Close other instances and reopen the latest vault; for an unsupported filesystem, copy the closed vault to a private supported local folder and open that copy. A corrupt or wrong-password vault cannot be repaired by renaming its lock file. Restore a known-good encrypted backup to a separate test location first.

## Older portable Windows ZIP packages

Older manual-test ZIPs contain an app folder and runtime rather than a setup wizard. Extract the entire archive, preserve its folder layout, and run `Password Guruji\Password Guruji.exe`. They do not create Start menu entries. To remove one, close the app and remove its extracted application folder, retaining test vaults and backups separately. Their old behavior and password rules do not describe this candidate.

See the [User Manual](USER-MANUAL.md) for day-to-day use and [Manual Test Guide](MANUAL-TEST-GUIDE.md) for test scope. Never attach vaults, plaintext CSV exports, passwords, or process dumps to a bug report.
