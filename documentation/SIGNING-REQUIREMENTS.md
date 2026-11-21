# Public distribution signing requirements

Reviewed 21 November 2026. This document describes prerequisites; it does not certify that current artifacts are signed, notarized, or production ready. No paid account, certificate, or service has been provisioned as part of this work. Prices below are published USD list prices, subject to region, taxes and change; obtain owner approval before purchasing.

## Windows

Use publicly trusted Authenticode signing for distributed application executables and installers. Microsoft recommends Azure Artifact Signing (formerly Trusted Signing) for software distributed outside its Store. Signing identifies the publisher but does not guarantee that a newly distributed file will immediately avoid SmartScreen warnings. [Microsoft signing options](https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/code-signing-options)

Artifact Signing Basic is advertised at **USD 9.99 per month**, including 5,000 signatures, with additional signatures at **USD 0.005 each**. Confirm the subscription's applicable offer and regional price before enabling it. [Microsoft product pricing](https://azure.microsoft.com/en-us/products/artifact-signing/) · [Pricing details](https://azure.microsoft.com/en-us/pricing/details/artifact-signing/)

Public Trust eligibility is restricted by applicant type and geography. Microsoft currently lists organizations in the US, Canada, EU, UK, Australia, New Zealand, Japan, South Korea, Singapore, Switzerland, Norway and Israel; individuals are limited to the US and Canada. **India is not listed.** If Mirakinetix's applying legal entity or individual is based in India, do not assume eligibility. Identity validation, an Artifact Signing account, a Public Trust certificate profile and appropriate signer permissions are required. Private Trust and test profiles are not substitutes for publicly trusted distribution. [Microsoft setup and eligibility](https://learn.microsoft.com/en-us/azure/artifact-signing/quickstart) · [Trust models](https://learn.microsoft.com/en-us/azure/artifact-signing/concept-trust-models)

A paid Azure subscription is required; free, trial and sponsored subscriptions are not supported. [Artifact Signing FAQ](https://learn.microsoft.com/en-us/azure/artifact-signing/faq)

If the applicant is ineligible, obtain a publicly trusted code-signing certificate from a certificate authority that supports the applicant's country and identity. This route has **no single universal price**: the certificate, hardware token or cloud signing service, shipping and taxes require a vendor quote. Standard code-signing private keys require supported hardware protection rather than an ordinary exportable key file. DigiCert describes certified hardware tokens, HSMs and its managed signing alternatives. [Hardware protection requirements](https://knowledge.digicert.com/general-information/new-private-key-storage-requirement-for-standard-code-signing-certificates-november-2022)

Acceptance checklist for the selected signing implementation:

- Sign the final application launchers and applicable EXE/MSI installers with the approved publisher identity, SHA-256 and a trusted timestamp.
- Verify signatures and certificate chains on the final downloadable files; retain verification evidence and SHA-256 file hashes.
- Download and install the actual artifact on a clean Windows machine; record publisher display, SmartScreen behavior, install, launch and uninstall.
- Keep signing credentials outside this repository. Prefer short-lived delegated access and a protected release environment with explicit approval.
- Do not disable SmartScreen, remove security protections or claim that signing proves the application has no vulnerabilities.

## macOS

Apple Developer Program membership costs **USD 99 per membership year**, or local currency where available. Organization enrollment and identity requirements must be met by the actual publisher. [Apple enrollment](https://developer.apple.com/help/account/membership/program-enrollment/)

Distribution outside the Mac App Store requires the relevant **Developer ID Application** certificate; signed PKG installers require **Developer ID Installer** as well. The Account Holder creates these certificates. Provision the signing identities securely on the build Mac only after approval. [Apple Developer ID certificates](https://developer.apple.com/help/account/certificates/create-developer-id-certificates)

Apple's notarization requirements include valid Developer ID signatures, Hardened Runtime, secure timestamps and valid entitlements; distribution code must not enable the debugging entitlement `com.apple.security.get-task-allow`. Sign nested executable code as well as the application bundle. Submit using `notarytool`, inspect acceptance and logs, then staple the ticket to the supported distribution artifact. Notarization is an automated malware and signing check, not an independent security audit. [Apple notarization requirements](https://developer.apple.com/documentation/security/notarizing-macos-software-before-distribution)

Acceptance checklist for this Java application:

- Validate the bundled JDK, native libraries and launcher on each supported architecture; determine the minimum JIT/runtime entitlements experimentally rather than adding broad exceptions.
- Use a secure timestamp and verify the final distribution signatures. Apple provides distribution signing guidance for the Hardened Runtime and timestamp options. [Distribution signing](https://developer.apple.com/documentation/xcode/creating-distribution-signed-code-for-the-mac/)
- Supply notarization authentication through a protected credential store or CI secrets, never source files or chat. The owner must authorize the account and authentication method.
- Verify the notarization submission was accepted, staple and validate its ticket, and assess the final application with Gatekeeper.
- Download the final DMG/PKG on a clean consumer Mac, preserving quarantine metadata; check install, first launch, ordinary offline launch and uninstall. Keep physical-machine results separate from hosted-runner results.
- Do not use quarantine removal or Gatekeeper disabling as evidence of successful installation.

## Release gate and owner decisions

The application can remain completely offline during ordinary use. Publisher signing, timestamping, notarization and downloading releases are separate build/distribution activities that require network access.

Before public production installers can be approved, record the legal publisher and country, chosen Windows signing route and quote, Apple membership status, approved annual/monthly spending, credential custodian, and clean-machine validation results. Until those steps are completed, describe artifacts accurately as unsigned test builds where applicable. Do not publish a paid-service-backed or signed production release solely because an automated build succeeded.
