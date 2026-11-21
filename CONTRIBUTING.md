# Contributing

Use Java 25 and Maven 3.9 or newer. Clone the repository and create a feature branch. Preserve Mirakinetix branding, offline operation, vault compatibility, and the LinkedIn/website footer. Do not add telemetry, accounts or automatic network requests.

Run `mvn -B -Pdesktop verify` in a desktop session. On Linux, use `xvfb-run -a mvn -B -Pdesktop verify`. All desktop tests must run for candidate verification; a headless core-only build is insufficient. Use synthetic credentials and temporary directories only.

Use `git diff --check` before committing. Add regression coverage for behavior changes. Do not reduce test assertions or cryptographic work factors to make a failing build pass. Describe any format migration, error paths, compatibility impact and tests in your pull request.

`python tools/package-native.py` builds native unsigned candidates on the target OS with jpackage and its required native tools. It requires fresh target/native-input and target/native/image output locations and refuses to overwrite them. Native installation verification scripts are restricted to disposable GitHub Actions runners because they install/uninstall packages. Do not set GITHUB_ACTIONS locally to bypass that restriction.

Use documentation/MANUAL-TEST-GUIDE.md for manual checks. Submit ordinary bugs with source commit, OS, reproduction and expected/observed results, using fictional data only. Report security concerns privately as described in SECURITY.md. Never commit personal vaults, exports, tokens, certificates or credentials; the samples directory contains intentionally public fictional examples only.

Merging, public releases and invitations require the repository owner's approval.
