# Password Guruji — Release-readiness report

Date: 21 November 2026. **Production and real-secret use remain blocked. The current hardening candidate is pending hosted verification and owner review before fictional-data beta testing.** No merge, public release, tester invitation or paid signing setup is authorized by this report.

## Repository and candidate identity

Project: `C:\Mirakinetix\mirakinetix-java\PasswordGuruji`.
Origin: https://github.com/mirakinetix/Password-Guruji.git.
Branch: `feature/cross-platform-beta-readiness`.
[Draft pull request #2](https://github.com/mirakinetix/Password-Guruji/pull/2).

Record the final reviewed commit and matching workflow IDs before handing installers to testers. Do not mix earlier artifacts with the current source revision.

## Current hardening

- Real-mode master-password creation/change minimum is 15 characters; explicit sample mode remains six and legacy short passwords can still unlock.
- Private file permissions, atomic-only replacement, persistent file coordination and stale-write rejection; failed edits roll back to persisted records.
- Modal dialogs clear on lock; lock no longer relies on saving. Clipboard ownership cleanup and background cryptographic tasks improve lifecycle behavior.
- Export/backup reject active vault/lock destinations. Versioned CSV encoding neutralizes spreadsheet-formula cells while preserving round trips.
- Strict malformed-vault parsing, legacy compatibility fixtures, failure injection, child-process lock/crash checks and 1,000-record fixture.
- Expanded security inventory includes Surefire's provider graph; limited tracked-source credential scanning added.
- Installer verification is being extended for uninstall/reinstall and data preservation. New hosted execution is still required.

## Current local evidence

The recorded local `mvn -B -Pdesktop verify` run passed **75 tests: 74 passed, zero failures/errors, one skipped (Windows symbolic-link creation privilege unavailable)**: CoreTest 27, CompatibilityTest 21, DesktopTest 4, FinalSafetyTest 6, StorageHardeningTest 12, UiHardeningTest 5. These reports include real Swing and Windows system clipboard interaction. This run includes destination-protection and disk-independent lock regressions.

The expanded local OSV scan passed: **96 resolved package versions, zero advisory matches**. The limited credential scan checked **52 tracked text files, zero matches**. Python compilation and scanner whitespace checks passed. These are local results, not hosted CI results.

Failure tests cover injected write/replace failure, unsupported atomic moves, read-only destination, a stale writer, removed vault, subprocess lock release and crash before commit. They do not prove every power-loss or filesystem behavior. Permission assertions apply to the current platform; the revised code needs Linux/macOS CI validation.

## Historical hosted evidence — superseded source only

Earlier source/package commit **812f110** passed [four-platform run 44867387200](https://github.com/mirakinetix/Password-Guruji/actions/runs/44867387200) and [security run 44867387246](https://github.com/mirakinetix/Password-Guruji/actions/runs/44867387246). Those runs recorded 31 tests per platform, seven installed-runtime checks and a 91-version dependency inventory.

These results establish the earlier baseline only. They do **not** verify current file locking, permissions, policy, CSV changes, new tests or revised installer lifecycle scripts. Existing downloaded artifacts in `target/cross-platform-evidence/` must not be presented as the new hardened build.

## Hosted and installer verification still pending

| Target | Candidate status | Required evidence |
| --- | --- | --- |
| Windows x64 | Pending new hosted run | Tests; MSI and EXE generation; installation; installed-runtime probe; launcher; uninstall/reinstall and synthetic-data preservation |
| Ubuntu x64 | Pending new hosted run | Tests under Xvfb; DEB install/runtime/launcher; uninstall/reinstall and data preservation; RPM generation/metadata |
| macOS Intel | Pending new hosted run | Tests; PKG/DMG generation; PKG installation/runtime/launcher; uninstall/reinstall; data preservation |
| macOS Apple Silicon | Pending new hosted run | Same checks on native ARM64 runner |

Hosted Windows Server is not consumer Windows 11. Hosted Mac installation is not consumer Gatekeeper/download-quarantine acceptance. RPM installation on Fedora/RHEL-family systems, Wayland, physical Macs, visual scaling/accessibility, native installer wizards and OS sleep/resume remain separate checks. Launcher survival is a smoke test, not visual proof of a usable UI.

## Artifact handoff

Use only installers, hashes, metadata, test reports and installation-verification JSON from the same successful candidate run. Actions artifacts expire after seven days. Native package version 1.0.1 is an Apple-compatible packaging build number; application version 0.1.0 remains a beta candidate, not a stable release claim. Each build records its source commit and architecture.

No installer should be handed off until all required candidate checks pass. The tester then follows [CROSS-PLATFORM-BETA.md](CROSS-PLATFORM-BETA.md), the installation guide and manual/user guides with disposable fictional data only.

## Remaining production gates

1. Complete final local rerun and current four-platform/security workflows; review exact commit and artifacts.
2. Complete manual consumer installation, uninstall/reinstall, upgrade/rollback, visual accessibility, sleep/resume, backup/restore file-picker and large-vault stress checks. Record gaps honestly.
3. Obtain independent security review, including secret-memory lifetime and cryptographic/storage design. Existing arrays are wiped where practical; complete JVM/OS secret erasure is not guaranteed.
4. Decide and approve signing/notarization identity, eligibility and cost; verify final downloadable installers on clean consumer machines. See [SIGNING-REQUIREMENTS.md](SIGNING-REQUIREMENTS.md).
5. Establish maintenance policy for JDK, Maven/toolchain and installer vulnerabilities excluded from the current OSV scope; decide licensing and distribution policy.
6. Owner explicitly approves merge, beta invitation and any later production release as separate decisions.

See [SECURITY-REVIEW.md](SECURITY-REVIEW.md) for resolved findings, severity, evidence and residual risks. No real vault data, paid services, billing changes or production deployment were used.
