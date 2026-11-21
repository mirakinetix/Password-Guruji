# Third-party notices

## Java runtime and build tools

Native candidate installers bundle an Eclipse Temurin Java 25 runtime. Its license and legal notices are retained in the runtime's `legal` directory. The source checkout requires a separately installed JDK. JUnit and Maven plugin libraries are development/test tools and are not bundled in the application JAR. See the build inventory and security report for their resolved versions.

## EFF long word list

Password Guruji bundles the Electronic Frontier Foundation (EFF) long word list for offline passphrase generation.

- Source: EFF Dice-Generated Passphrases at https://www.eff.org/dice, linked file eff_large_wordlist.txt
- Original file: https://www.eff.org/files/2016/07/18/eff_large_wordlist.txt
- Attribution: Electronic Frontier Foundation; wordlist creator Joseph Bonneau
- License: Creative Commons Attribution 4.0 International (CC BY 4.0), consistent with EFF's copyright policy for original EFF website materials: https://www.eff.org/copyright
- The bundled copy is distributed for local use and does not imply EFF endorsement of Password Guruji.

The app reads the bundled resource locally and does not contact EFF or any network service when generating passphrases.
