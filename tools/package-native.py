"""Build unsigned beta candidates and clearly separate test-only upgrade fixtures."""
import hashlib, json, os, pathlib, platform, shutil, subprocess

ROOT = pathlib.Path(__file__).resolve().parents[1]
os.chdir(ROOT)
OUT = ROOT / 'target' / 'native'
INPUT = ROOT / 'target' / 'native-input'
APPLICATION_VERSION = '0.1.0'
PACKAGE_VERSION = '1.0.1'
FIXTURE_VERSION = '1.0.0'
NAME = 'Password Guruji'
JAR = f'password-guruji-{APPLICATION_VERSION}.jar'
SYSTEM = platform.system()
JAVA_HOME = pathlib.Path(os.environ['JAVA_HOME'])
JPACKAGE = JAVA_HOME / 'bin' / ('jpackage.exe' if os.name == 'nt' else 'jpackage')
COMMIT = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
if not (ROOT / 'target' / JAR).is_file():
    raise SystemExit('Build and test the application before packaging.')
if (OUT / 'image').exists() or INPUT.exists():
    raise SystemExit('Packaging output already exists. Use a fresh checkout/build directory; outputs are never silently replaced.')
OUT.mkdir(parents=True, exist_ok=True)
INPUT.mkdir()
shutil.copy2(ROOT / 'target' / JAR, INPUT)
for folder in ['documentation', 'samples']:
    (INPUT / folder).mkdir()
for name in ['USER-MANUAL.md', 'INSTALLATION-GUIDE.md', 'MANUAL-TEST-GUIDE.md', 'CROSS-PLATFORM-BETA.md', 'SECURITY-REVIEW.md', 'THIRD-PARTY-NOTICES.md', 'SAMPLE-VAULT.md']:
    shutil.copy2(ROOT / 'documentation' / name, INPUT / 'documentation')
for name in ['sample-vault.pgv', 'sample-backup.pgv', 'sample-data.csv', 'README.md']:
    shutil.copy2(ROOT / 'samples' / name, INPUT / 'samples')
# A human-readable identity is bundled inside every package, independent of the
# numeric installer version required by native package managers.
(INPUT / 'BETA-CANDIDATE.txt').write_text(
    f'Password Guruji {APPLICATION_VERSION} beta candidate\nNative package build: {PACKAGE_VERSION}\n'
    f'Source commit: {COMMIT}\nUnsigned; not production approved. Read documentation before using.\n', encoding='utf-8')

def run(args):
    subprocess.run([str(arg) for arg in args], check=True)

icon = ROOT / 'src/main/resources/com/mirakinetix/passwordguruji/brand/password-guruji-icon-256.png'
if SYSTEM == 'Windows':
    icon = ROOT / 'documentation/brand-assets/password-guruji/password-guruji.ico'
elif SYSTEM == 'Darwin':
    iconset = OUT / 'password-guruji.iconset'
    iconset.mkdir()
    for size in (16, 32, 128, 256, 512):
        for scale in (1, 2):
            suffix = '@2x' if scale == 2 else ''
            run(['sips', '-z', str(size * scale), str(size * scale), icon,
                 '--out', iconset / f'icon_{size}x{size}{suffix}.png'])
    icon = OUT / 'password-guruji.icns'
    run(['iconutil', '-c', 'icns', iconset, '-o', icon])

def common(version):
    return [JPACKAGE, '--name', NAME, '--app-version', version, '--vendor', 'Mirakinetix',
            '--description', f'Password Guruji {APPLICATION_VERSION} beta candidate; unsigned, not production approved']

def make_image(version, destination, runtime=None):
    args = common(version) + ['--type', 'app-image', '--input', INPUT, '--main-jar', JAR,
        '--main-class', 'com.mirakinetix.passwordguruji.PasswordGurujiApp', '--dest', destination, '--icon', icon]
    if runtime:
        args += ['--runtime-image', runtime]
    else:
        args += ['--jlink-options', '--strip-debug --no-man-pages --no-header-files']
    run(args)
    return destination / (NAME + '.app' if SYSTEM == 'Darwin' else NAME)

def make_installer(version, image, destination, fmt):
    args = common(version) + ['--type', fmt, '--app-image', image, '--dest', destination]
    if SYSTEM == 'Windows':
        args += ['--win-per-user-install', '--win-menu', '--win-shortcut',
                 '--win-menu-group', 'Mirakinetix', '--win-upgrade-uuid', 'ffbd081c-56e1-43fa-a384-b17ea805b7de']
    elif SYSTEM == 'Linux':
        args += ['--linux-package-name', 'password-guruji', '--linux-shortcut', '--linux-app-category', 'Utility']
    elif SYSTEM == 'Darwin':
        args += ['--mac-package-identifier', 'com.mirakinetix.passwordguruji']
    run(args)

image = make_image(PACKAGE_VERSION, OUT / 'image')
formats = {'Windows': ['msi', 'exe'], 'Darwin': ['pkg', 'dmg'], 'Linux': ['deb', 'rpm']}[SYSTEM]
for fmt in formats:
    make_installer(PACKAGE_VERSION, image, OUT, fmt)
# This prior numeric package is generated from the SAME source only to exercise
# native version-upgrade behavior. It is not a previous application release and
# is never uploaded with the candidate installers.
runtime = image / ({'Windows': 'runtime', 'Linux': 'lib/runtime', 'Darwin': 'Contents/runtime/Contents/Home'}[SYSTEM])
fixture_root = OUT / 'upgrade-fixture'
fixture_image = make_image(FIXTURE_VERSION, fixture_root / 'image', runtime)
fixture_formats = ['deb', 'rpm'] if SYSTEM == 'Linux' else ['msi' if SYSTEM == 'Windows' else 'pkg']
for fmt in fixture_formats:
    make_installer(FIXTURE_VERSION, fixture_image, fixture_root, fmt)
metadata = {'os': SYSTEM, 'architecture': platform.machine(), 'java_home': str(JAVA_HOME), 'formats': formats,
    'application_version': APPLICATION_VERSION, 'native_package_version': PACKAGE_VERSION,
    'upgrade_fixture_version': FIXTURE_VERSION, 'upgrade_fixture_scope': 'same application source; native version migration only',
    'status': 'beta candidate', 'signed': False, 'notarized': False, 'commit': COMMIT,
    'application_jar_sha256': hashlib.sha256((INPUT / JAR).read_bytes()).hexdigest()}
(OUT / 'build-metadata.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
for package in sorted(OUT.iterdir()):
    if package.suffix in ['.msi', '.exe', '.pkg', '.dmg', '.deb', '.rpm']:
        with package.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        package.with_name(package.name + '.sha256').write_text(f'{digest}  {package.name}\n', encoding='ascii')
print('Native beta candidates and checksums:', OUT)
