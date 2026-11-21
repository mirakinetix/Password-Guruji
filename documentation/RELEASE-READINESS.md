# Password Guruji — Release-readiness report

Date: 21 November 2026. **Production and real-secret use remain blocked. Fictional-data beta preparation is subject to exact-revision verification and owner review.** No merge, public release, tester invitation or paid signing setup is authorized by this report.

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
- Installer verification checks install, native startup, upgrade and removal with fictional vault preservation on hosted targets.

## Verification and PR #2 review fixes

The previous reviewed revision, `0bd8457`, passed 77 tests on each of the four hosted platforms and generated eight checksum-verified packages. Its dependency and source scan passed. [Previous platform run](https://github.com/mirakinetix/Password-Guruji/actions/runs/44881196259) · [Previous security run](https://github.com/mirakinetix/Password-Guruji/actions/runs/44881196268)

The follow-up implements literal rendering, non-overwriting backups, read-only recovery, matching CSV size limits, and managed keyboard copy. [Draft PR #2](https://github.com/mirakinetix/Password-Guruji/pull/2) tracks the local and hosted results, installer checksums, and final recommendation for the follow-up commit. Only use artifacts whose embedded source SHA matches the verified revision.

Hosted Windows Server differs from consumer Windows 11. Hosted Mac install does not establish consumer Gatekeeper acceptance. Visual scaling, accessibility, physical power-loss behavior, and consumer installer prompts require separate validation.

## Artifact handoff

Use only installers, hashes, metadata, test reports and installation-verification JSON from the same successful candidate run. Actions artifacts expire after seven days. Native package version 1.0.1 is an Apple-compatible packaging build number; application version 0.1.0 remains a beta candidate, not a stable release claim. Each build records its source commit and architecture.

No installer should be handed off until all required candidate checks pass. The tester then follows [CROSS-PLATFORM-BETA.md](CROSS-PLATFORM-BETA.md), the installation guide and manual/user guides with disposable fictional data only.

## Remaining production gates

1. Review exact-commit platform and security evidence in the draft PR before approving beta distribution.
2. Complete manual consumer installation, uninstall/reinstall, upgrade/rollback, visual accessibility, sleep/resume, backup/restore file-picker and large-vault stress checks. Record gaps honestly.
3. Obtain independent security review, including secret-memory lifetime and cryptographic/storage design. Existing arrays are wiped where practical; complete JVM/OS secret erasure is not guaranteed.
4. Decide and approve signing/notarization identity, eligibility and cost; verify final downloadable installers on clean consumer machines. See [SIGNING-REQUIREMENTS.md](SIGNING-REQUIREMENTS.md).
5. Establish maintenance policy for JDK, Maven/toolchain and installer vulnerabilities excluded from the current OSV scope; decide licensing and distribution policy.
6. Owner explicitly approves merge, beta invitation and any later production release as separate decisions.

See [SECURITY-REVIEW.md](SECURITY-REVIEW.md) for resolved findings, severity, evidence and residual risks. No real vault data, paid services, billing changes or production deployment were used.
