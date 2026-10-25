# Password Guruji — Installation Guide

This is a **portable Windows manual-test package**, not a setup wizard. It does not create Start menu entries, modify the registry, or require an installed Java runtime. The app bundle contains its own Java runtime.

## Install and launch

1. Save the ZIP supplied by the project owner to a local Windows folder.
2. Extract the ZIP with **Extract All**. Keep the full `Password Guruji` folder together; do not move or run only the `.exe` by itself.
3. Move the extracted package to a writable local folder, for example `C:\Users\<your-user>\Desktop\PasswordGuruji-Test`. Avoid a network share or synchronized folder for the test vault.
4. Read `START-HERE.txt` and `MANUAL-TEST-GUIDE.md` in the extracted folder.
5. Open `Password Guruji\Password Guruji.exe` to launch the app. No internet connection is needed for normal app use.

## Create an isolated test vault

Before creating the vault, use **Choose vault location** to select a new, dedicated test file such as `PasswordGuruji-Test\test-vault.pgv`. This helps avoid touching any existing vault on the tester’s computer. Create the vault with a unique, disposable master password of at least six characters; do not reuse a personal or work password.

Use only synthetic test entries. Never enter production passwords, identity or payment data, recovery codes, or live TOTP secrets.

## Windows security prompts

This manual-test build is unsigned. Windows may show a publisher or reputation warning. Verify the ZIP was provided by the project owner and, if a SHA-256 file was provided, compare the checksum before extracting. Do not disable Microsoft Defender or other security software to run the app. If security software blocks the package, stop and send the owner the exact warning text.

## Offline behavior

The application does not need the internet to create or use the vault. The LinkedIn and `dparab.me` footer links open a browser only when clicked. Leave them untouched if you want to keep the test fully offline.

## Remove the test build

1. Close Password Guruji.
2. Keep or delete the extracted test package as directed by the project owner.
3. Delete the disposable test vault and test backups only after test results have been recorded and the owner confirms they are no longer needed.

The app image is portable and does not need a separate uninstaller.
