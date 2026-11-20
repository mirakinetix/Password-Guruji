# Password Guruji — Installation Guide

This is a portable Windows manual-testing package, not a setup wizard. It contains the app and a Java runtime. It does not add Start menu entries or require Java to be installed separately.

## What is in the ZIP

The package contains the Password Guruji app folder, a START-HERE file, the User Manual, this guide, the Manual Test Guide, and third-party notices. Keep the extracted folder structure intact.

## Install and launch

1. Save the ZIP and its matching SHA-256 file to a local folder.
2. Optionally verify the ZIP's SHA-256 checksum before extracting. In PowerShell, run **$zipPath = Read-Host 'Path to ZIP'; Get-FileHash -LiteralPath $zipPath -Algorithm SHA256** and compare the hash to the supplied .sha256 file.
3. Right-click the ZIP and choose **Extract All**.
4. Move the extracted package to a writable local folder. Avoid a network share or synchronized folder for the test vault.
5. Read START-HERE.txt and the Manual Test Guide.
6. Launch **Password Guruji\Password Guruji.exe**.

The app does not need an internet connection for normal vault use. The footer links open a browser only when clicked.

## Create a disposable test vault

Before creating a vault, select **Choose vault location** and choose a new dedicated test file, such as **PasswordGuruji-Test\test-vault.pgv**. This avoids accidentally using an existing vault.

Create the test vault with a unique, disposable master password of at least six characters. Do not reuse a personal or work password. Enter only synthetic records and test secrets.

The master password cannot be recovered. Do not use this manual-testing build to store live credentials.

## Windows security prompts

This manual-test build is unsigned. Windows may display a publisher or reputation warning. Verify the ZIP came from the project owner and compare the SHA-256 value if provided. Do not disable Microsoft Defender or other security software to run the app. If security software blocks it, stop and report the exact warning text.

## Remove the test build

1. Close Password Guruji.
2. Keep or delete the extracted package as directed by the project owner.
3. Delete the disposable test vault and test backups only after the test results are recorded and the owner confirms they are no longer needed.

The app image is portable and has no separate uninstaller.