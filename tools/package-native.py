"""Build native packages on the current OS. Does not publish or install anything."""
import hashlib, json, os, pathlib, platform, shutil, subprocess
ROOT = pathlib.Path(__file__).resolve().parents[1]
os.chdir(ROOT)
OUT = ROOT / 'target' / 'native'
OUT.mkdir(parents=True, exist_ok=True)
INPUT = ROOT / 'target' / 'native-input'
INPUT.mkdir(exist_ok=True)
shutil.copy2(ROOT / 'target/password-guruji-0.1.0.jar', INPUT)
guides = INPUT / 'documentation'
guides.mkdir(exist_ok=True)
for name in ['USER-MANUAL.md', 'CROSS-PLATFORM-BETA.md', 'SECURITY-REVIEW.md', 'THIRD-PARTY-NOTICES.md', 'SAMPLE-VAULT.md']:
    shutil.copy2(ROOT / 'documentation' / name, guides)
demo = INPUT / 'samples'
demo.mkdir(exist_ok=True)
for name in ['sample-vault.pgv', 'sample-backup.pgv', 'sample-data.csv', 'README.md']:
    shutil.copy2(ROOT / 'samples' / name, demo)
java_home = pathlib.Path(os.environ['JAVA_HOME'])
jpackage = java_home / 'bin' / ('jpackage.exe' if os.name == 'nt' else 'jpackage')
system = platform.system()
# Apple's CFBundleVersion requires a positive first component. This is a package
# build number, not a declaration that the application has reached a stable release.
common = [str(jpackage), '--name', 'Password Guruji', '--app-version', '1.0.0', '--vendor', 'Mirakinetix',
          '--description', 'Unsigned beta candidate - synthetic credentials only']
image_args = common + ['--type', 'app-image', '--input', str(INPUT), '--main-jar', 'password-guruji-0.1.0.jar',
    '--main-class', 'com.mirakinetix.passwordguruji.PasswordGurujiApp', '--dest', str(OUT / 'image'),
    '--jlink-options', '--strip-debug --no-man-pages --no-header-files']
if system == 'Windows':
    image_args += ['--icon', str(ROOT / 'documentation/brand-assets/password-guruji/password-guruji.ico')]
elif system == 'Linux':
    image_args += ['--icon', str(ROOT / 'src/main/resources/com/mirakinetix/passwordguruji/brand/password-guruji-icon-256.png')]
subprocess.run(image_args, check=True)
image = OUT / 'image' / ('Password Guruji.app' if system == 'Darwin' else 'Password Guruji')
formats = {'Windows': ['msi', 'exe'], 'Darwin': ['pkg', 'dmg'], 'Linux': ['deb', 'rpm']}[system]
for fmt in formats:
    args = common + ['--type', fmt, '--app-image', str(image), '--dest', str(OUT)]
    if system == 'Windows': args += ['--win-per-user-install', '--win-menu', '--win-shortcut', '--win-upgrade-uuid', 'ffbd081c-56e1-43fa-a384-b17ea805b7de']
    if system == 'Linux': args += ['--linux-package-name', 'password-guruji', '--linux-shortcut', '--linux-app-category', 'Utility']
    if system == 'Darwin': args += ['--mac-package-identifier', 'com.mirakinetix.passwordguruji']
    subprocess.run(args, check=True)
metadata = {'os': system, 'architecture': platform.machine(), 'java_home': str(java_home), 'formats': formats,
            'application_version': '0.1.0', 'native_package_version': '1.0.0',
            'signed': False, 'notarized': False, 'commit': subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()}
(OUT / 'build-metadata.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
for package in sorted(OUT.iterdir()):
    if package.suffix in ['.msi','.exe','.pkg','.dmg','.deb','.rpm']:
        digest = hashlib.file_digest(package.open('rb'), 'sha256').hexdigest()
        package.with_name(package.name + '.sha256').write_text(f'{digest}  {package.name}\n', encoding='ascii')
print('Native packages and checksums:', OUT)
