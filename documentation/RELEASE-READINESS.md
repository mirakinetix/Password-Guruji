# Password Guruji — Release-readiness report

Date: 21 November 2026. Decision: **ready for controlled, synthetic-data functional testing after owner approval; not ready for real credentials or an unrestricted public release.** No release was published, no tester invitations were sent, and no branch was merged.

## Repository and inspection

Verified local directory: `C:\Mirakinetix\mirakinetix-java\PasswordGuruji`.
Verified origin: `https://github.com/mirakinetix/Password-Guruji.git` (public).
Working branch: `feature/cross-platform-beta-readiness`.
[Draft pull request #2](https://github.com/mirakinetix/Password-Guruji/pull/2).

The starting checkout was clean on `feature/footer-links-finalization`, commit `6c7c10f`, while remote main was `681202c`. Existing footer work was preserved in the new branch. Remote main was not changed.

Inspected the Maven configuration, all seven original production Java classes, source resources/word list, vault format, sample vault tooling, icon tools, Windows packaging script, existing documentation, branches/remotes and installed JDK/Maven. There were no tests or Actions workflows. The implemented UI is Swing, not JavaFX; no JavaFX dependencies exist. The old compiler target was Java 17 despite a local Java 25 JDK.

## Changes

- Java 25 build; pinned stable Maven plugins, JUnit functional tests, optional desktop-test profile enabled in every CI job.
- Four standard hosted runner targets, Maven caching, bounded jobs, read-only tokens, pinned Actions, seven-day evidence/installer artifacts, no release/deployment steps or signing secrets.
- Native Windows MSI/EXE, Ubuntu DEB/RPM, Intel Mac PKG/DMG and Apple Silicon PKG/DMG. Each includes a runtime, documentation and fictional samples.
- Actual installation checks on disposable runners, plus seven functional checks using the installed JAR and bundled runtime.
- Fixed stale creation form after first lock, empty CSV custom-field import, clipboard clearing on lock/close, and clipboard timer replacement behavior.
- Native macOS/Linux data locations with legacy-vault discovery; Windows behavior preserved.
- Resolved build-tool dependency advisories through stable plugin updates and two narrowly scoped dependency overrides. Added an OSV inventory scan and Dependabot configuration.
- Installation/testing guide, security review, safe bug-report template and updated source-build guidance.

## Verified results

The source/package implementation at **812f110** passed the following runs. Later documentation-only commits do not change application behavior; consult the PR's latest checks for their reruns.

- [Four-platform build, test, package and installation run](https://github.com/mirakinetix/Password-Guruji/actions/runs/44867387200): **success, all four jobs**.
- [Expanded dependency-security run](https://github.com/mirakinetix/Password-Guruji/actions/runs/44867387246): **success; 91 resolved package versions queried, zero advisory matches**.
- Local Windows 11, Temurin 25.0.4.1, Maven 3.9.16: `mvn -B -Pdesktop verify` — **31 tests, 0 failures, 0 errors, 0 skipped**.

| Environment | Automated tests | Installed runtime checks | Installer / startup evidence |
| --- | --- | --- | --- |
| Local Windows 11 x64 | 31 passed | Not run locally | Actual Swing lifecycle and Windows system clipboard tested |
| Windows Server 2022 x64 runner | 31 passed | 7 passed | MSI installed; installed launcher stayed running; EXE generated |
| Ubuntu 24.04 x64 / Xvfb | 31 passed | 7 passed | DEB installed; installed launcher stayed running; RPM metadata inspected |
| macOS 15 Intel | 31 passed | 7 passed | PKG installed; installed launcher stayed running; DMG integrity verified |
| macOS 15 Apple Silicon | 31 passed | 7 passed | ARM64 PKG installed; installed launcher stayed running; DMG integrity verified |

This confirms functional compatibility in those environments, not every version of Windows/Linux/macOS or every desktop configuration. The desktop lifecycle test opens a real Swing window and uses controls for create, lock, reject wrong password, unlock, idle-lock callback, and close. It checks password-array clearing and immediate clipboard cleanup. The idle timer's threshold path is exercised by setting last activity; this is not a wall-clock sleep/resume test.

Core coverage includes encrypted round trips with Unicode/custom fields/history/recycle bin, wrong-password and tamper rejection, encrypted backup re-encryption, generation options/invalid input, passphrase resources, six RFC 6238 vectors, CSV edge cases, sample compatibility, native/legacy paths, and POSIX permissions where supported. The Windows permission test checks readability only and is not a DACL-isolation test.

Installed-runtime checks cover create/load, encrypted record save/load, wrong password, password generator, passphrase word list, TOTP vector and tampering. The standalone native launcher check asserts that the process survives eight seconds, then terminates it. It does not assert screenshots or a graceful native-process exit. Graceful window shutdown is checked separately in DesktopTest.

## Artifacts and local handoff

The workflow run above has these downloadable Actions artifacts:

- `installers-windows-2022-x64`: MSI and EXE with SHA-256 files.
- `installers-ubuntu-24.04-x64`: DEB and RPM with SHA-256 files.
- `installers-macos-15-intel-x64`: Intel PKG and DMG with SHA-256 files.
- `installers-macos-15-aarch64`: ARM64 PKG and DMG with SHA-256 files.
- Matching `evidence-*` artifacts: JUnit XML/text, build metadata, installation-verification JSON, installed-runtime probe logs and launcher/installer logs.

Downloaded copies are under `target/cross-platform-evidence/44867387200/` in the local project. Dependency evidence is under `target/cross-platform-evidence/44867387246/`. These generated outputs are ignored by Git. GitHub artifacts expire after seven days; local copies are retained. For a later candidate, use its matching run artifacts and checksums, not a mixture of builds.

Native package build number 1.0.0 satisfies Apple's version rules; application version remains 0.1.0. This is not a declaration of a stable release. Source branch, commit and native architecture are recorded in each build-metadata JSON.

## Failures encountered and resolved

- Initial desktop checks exposed the stale creation form and an incompatible clipboard identity comparison on Windows. Both were corrected; the same tests then passed.
- Initial Mac packaging rejected version 0.1.0. Native package numbering was separated from the application version.
- Ubuntu installer setup failed because the minimal runner lacked a desktop-menu directory. The CI environment now supplies it; the DEB installs successfully.
- One runner could not resolve a Maven plugin from Central. Versions are pinned and Maven uses `-U` to avoid a cached missing-artifact result. Later runs resolved dependencies and passed; no failing tests were skipped.
- Expanded build-plugin scanning found advisory matches in six library families. Stable plugin updates and fixed-version overrides removed the matches in the final resolved scope.
- Earlier superseded runs were cancelled by workflow concurrency on new pushes. They are not counted as successful verification.

## Security and limitations

See [the focused security review](SECURITY-REVIEW.md) for prioritized findings, cryptographic design and scan boundaries. AES-256-GCM/PBKDF2 parameters were preserved. No real credentials or personal vault data were used.

Unresolved real-use gates: six-character testing minimum; immutable secret strings and retained buffers; inherited Windows ACLs; concurrent-writer risk; non-atomic fallback and untested crash durability; CSV plaintext/formula risk; unsigned/unnotarized packages; no independent cryptographic review. No critical break was demonstrated, but that is not proof of absence.

The clean OSV result is limited to application/test Maven artifacts, declared plugins and resolved transitive plugin libraries. It does not cover JDK vulnerabilities, Maven core, undeclared lifecycle plugins, dynamically selected test providers, OS packages or installer tools. Runtime/JDK update policy remains a release-maintenance task.

## Untested or only partly tested

- Consumer Mac Gatekeeper/download quarantine, Apple signing/notarization, SmartScreen reputation, physical Mac hardware and visual accessibility/scaling.
- EXE installer wizard, DMG drag-install behavior, RPM installation on an RPM distribution, uninstall/upgrade/rollback behavior.
- Wayland and other Linux desktops; OS sleep/resume/session-lock integration; platform clipboard-history managers.
- Full file-picker backup/restore workflows, large vault stress, disk-full/read-only errors, forced crashes/power loss, concurrent instances, malformed-input fuzzing and explicit legacy-v1/v2 fixtures.
- Vault-unlock MFA, cloud sync, browser autofill, breach monitoring and mobile apps are outside this implementation.

## Before inviting testers or releasing

1. Owner reviews the draft PR, known limitations, and exact candidate artifacts; explicitly approves any merge and tester invitation/public beta.
2. A designated tester follows [CROSS-PLATFORM-BETA.md](CROSS-PLATFORM-BETA.md) and the existing user/manual-test guides with dummy data only.
3. Record installer trust prompts and complete manual installation/upgrade/uninstall and visual checks on supported consumer environments; obtain RPM-family coverage separately.
4. Before real-password use: raise the minimum to 15, address high-priority security findings, perform independent review and crash/concurrency testing, decide signing/notarization and licensing/distribution policy.

No paid/larger runner, signing service or Apple membership was configured. Standard runners for this public repository are documented as free; billing/account budgets were not changed. Storage retention is bounded, and no production deployment or release workflow was added.
