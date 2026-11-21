# Privacy and offline operation

The application creates and reads local encrypted vault files. It needs no account, cloud storage, network connection or telemetry service. Password generation, health checks and per-record TOTP calculations run locally. TOTP requires an accurate system clock but does not fetch time from the internet itself.

The app stores vault location and preferences such as idle-lock and clipboard timers in Java Preferences outside the encrypted vault. Those settings are not secret and can be modified by software running as the same user. The default vault path is shown in the app. Backups and exports go to locations selected by the user.

Footer links open the user's browser only when clicked; visiting them sends normal browser requests to those sites under their privacy policies. The application does not require clicking these links. Build tools, dependency checks and CI download dependencies and are separate from offline application operation.

CSV exports are plaintext. OS clipboard history, cloud clipboard sync, backups, search indexers, antivirus and other programs may retain copies outside the app's control. File encryption does not protect records while another process can inspect the unlocked application. Avoid public/shared directories and unmanaged network or cloud-synchronized filesystems.

The application cannot recover a forgotten master password. Keep encrypted backups and retain their corresponding password. Uninstalling preserves user vaults and settings; remove them separately only when you deliberately intend to do so.
