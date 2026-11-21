"""Native install/upgrade/uninstall checks. Disposable GitHub Actions runners only."""
import hashlib, json, os, pathlib, platform, plistlib, shutil, subprocess, tempfile, time
if os.environ.get('GITHUB_ACTIONS') != 'true':
    raise SystemExit('Restricted to disposable GitHub Actions runners.')
root = pathlib.Path(__file__).resolve().parents[1]
out = root / 'target/native'
system = platform.system()
metadata = json.loads((out/'build-metadata.json').read_text())
phases = []
probe = out/'probe-classes'
probe.mkdir(exist_ok=True)
jar_name = 'password-guruji-0.1.0.jar'

def run(*args, accepted=(0,)):
    result = subprocess.run([str(a) for a in args], check=False)
    if result.returncode not in accepted:
        raise RuntimeError(f'Command failed ({result.returncode}): {args[0]}')

def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def paths(executable):
    if system == 'Darwin':
        app = executable.parents[1]
        return app/'runtime/Contents/Home/bin/java', app/'app'/jar_name
    app = executable.parents[1]/'lib' if system == 'Linux' else executable.parent
    return app/('runtime/bin/java.exe' if system == 'Windows' else 'runtime/bin/java'), app/'app'/jar_name

def installed_launcher():
    if system == 'Windows': return pathlib.Path(os.environ['LOCALAPPDATA'])/'Password Guruji/Password Guruji.exe'
    if system == 'Darwin': return pathlib.Path('/Applications/Password Guruji.app/Contents/MacOS/Password Guruji')
    candidates = [pathlib.Path('/opt/password-guruji/bin/Password Guruji'), pathlib.Path('/opt/Password Guruji/bin/Password Guruji')]
    return next((p for p in candidates if p.exists()), candidates[0])

def functional(executable, label, *arguments):
    runtime, jar = paths(executable)
    if not runtime.is_file() or not jar.is_file(): raise RuntimeError('Installed runtime/JAR missing')
    if digest(jar) != metadata['application_jar_sha256']: raise RuntimeError('Installed source artifact differs')
    if not (jar.parent/'BETA-CANDIDATE.txt').is_file(): raise RuntimeError('Bundled beta identity missing')
    with (out/(label+'.log')).open('w', encoding='utf-8') as log:
        subprocess.run([str(runtime), '-cp', str(jar)+os.pathsep+str(probe),
            'com.mirakinetix.passwordguruji.PackagingProbe', *map(str,arguments)],
            stdout=log, stderr=subprocess.STDOUT, check=True, timeout=120)

def launch(executable, label):
    with tempfile.TemporaryDirectory(prefix='password-guruji-ci-') as temp:
        with (out/(label+'-launcher.log')).open('w', encoding='utf-8') as log:
            process = subprocess.Popen([str(executable), '--sample-vault', str(pathlib.Path(temp)/'dummy.pgv')],stdout=log,stderr=log)
            try:
                time.sleep(8)
                if process.poll() is not None: raise RuntimeError(f'Launcher exited early: {process.returncode}')
            finally:
                if process.poll() is None: process.terminate()
                try: process.wait(timeout=15)
                except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=5)

def install(package, label):
    if system == 'Windows':
        if package.suffix == '.msi': run('msiexec.exe','/i',package,'/qn','/norestart','/L*v',out/(label+'.log'),accepted=(0,3010))
        else: run(package,'/qn','/norestart','/L*v',out/(label+'.log'),accepted=(0,3010))
    elif system == 'Darwin': run('sudo','installer','-pkg',package,'-target','/')
    else: run('sudo','apt-get','install','-y',package)
    phases.append(label)

def uninstall(package, label):
    if system == 'Windows':
        if package.suffix == '.msi': run('msiexec.exe','/x',package,'/qn','/norestart','/L*v',out/(label+'.log'),accepted=(0,3010))
        else: run(package,'uninstall',accepted=(0,3010))
    elif system == 'Darwin':
        # Exact package-owned application path on an ephemeral runner; never the data directory.
        run('sudo','/bin/rm','-rf','/Applications/Password Guruji.app')
    else: run('sudo','apt-get','remove','-y','password-guruji')
    if installed_launcher().exists(): raise RuntimeError('Uninstalled launcher remains')
    if digest(vault) != before: raise RuntimeError('Uninstall changed user vault')
    phases.append(label+'; synthetic user vault retained byte-for-byte')

fmt = {'Windows':'msi','Darwin':'pkg','Linux':'deb'}[system]
fixture = next((out/'upgrade-fixture').glob('*.'+fmt))
candidate = next(out.glob('*.'+fmt))
if installed_launcher().exists(): raise RuntimeError('Refusing to replace pre-existing application')
base = pathlib.Path(os.environ['LOCALAPPDATA']) if system == 'Windows' else pathlib.Path.home()/('Library/Application Support' if system == 'Darwin' else '.local/share')
vault = base/'Mirakinetix/PasswordGuruji/vault.pgv'
if vault.exists(): raise RuntimeError('Refusing to replace pre-existing user vault')
install(fixture,'fixture-install-1.0.0')
exe = installed_launcher()
javac = pathlib.Path(os.environ['JAVA_HOME'])/'bin'/('javac.exe' if system == 'Windows' else 'javac')
run(javac,'--release','25','-encoding','UTF-8','-cp',paths(exe)[1],'-d',probe,root/'tools/PackagingProbe.java')
functional(exe,'seed-upgrade','seed',vault)
before = digest(vault)
install(candidate,'candidate-upgrade-1.0.1')
exe = installed_launcher()
if digest(vault) != before: raise RuntimeError('Upgrade changed existing encrypted vault')
functional(exe,'verify-upgrade','verify',vault)
functional(exe,'installed-runtime-probe')
launch(exe,'installed')
if system == 'Linux':
    version = subprocess.check_output(['dpkg-query','-W','-f=${Version}','password-guruji'],text=True).strip()
    if not version.startswith('1.0.1'): raise RuntimeError('Wrong installed DEB version: '+version)
    run('rpm','-qip',next(out.glob('*.rpm')))
elif system == 'Darwin':
    info = plistlib.loads((exe.parents[1]/'Info.plist').read_bytes())
    if info.get('CFBundleShortVersionString') != '1.0.1': raise RuntimeError('Wrong installed app version')
    if not list((exe.parents[1]/'Resources').glob('*.icns')): raise RuntimeError('Missing native macOS icon')
    dmg = next(out.glob('*.dmg'))
    run('hdiutil','verify',dmg)
    with tempfile.TemporaryDirectory(prefix='guruji-dmg-') as mount:
        run('hdiutil','attach','-readonly','-nobrowse','-mountpoint',mount,dmg)
        try:
            source = pathlib.Path(mount)/'Password Guruji.app'
            if not source.is_dir(): raise RuntimeError('DMG application missing')
            copied = out/'dmg-drag-install/Password Guruji.app'
            copied.parent.mkdir(exist_ok=True)
            run('ditto',source,copied)
            copied_launcher = copied/'Contents/MacOS/Password Guruji'
            functional(copied_launcher,'dmg-runtime-probe')
            launch(copied_launcher,'dmg')
            phases.append('DMG mounted read-only; copied application runtime and launcher verified')
        finally: run('hdiutil','detach',mount)
else:
    version = subprocess.check_output(['powershell','-NoProfile','-Command',
        "(Get-Item -LiteralPath '"+str(exe).replace("'","''")+"').VersionInfo.ProductVersion"],text=True).strip()
    if not version.startswith('1.0.1'): raise RuntimeError('Wrong installed launcher version: '+version)
phases.append('upgrade preserved encrypted vault hash and decrypted synthetic record')
uninstall(candidate,'candidate-uninstall')
if system == 'Windows':
    candidate_exe = next(out.glob('*.exe'))
    install(candidate_exe,'exe-install')
    functional(installed_launcher(),'exe-runtime-probe')
    functional(installed_launcher(),'exe-preserved-vault','verify',vault)
    launch(installed_launcher(),'exe')
    uninstall(candidate_exe,'exe-uninstall')
(out/'installation-verification.json').write_text(json.dumps({
    'os':system,'source_commit':metadata['commit'],'application_version':'0.1.0 beta','native_version':'1.0.1',
    'upgrade_fixture':'1.0.0 built from same source; tests native version transition, not historical application migration',
    'phases':phases,'synthetic_vault_sha256':before,'functional_checks_per_probe':7,
    'startup':'process alive for 8 seconds; not a visual assertion',
    'shutdown':'harness termination; DesktopTest separately tests graceful close',
    'mac_uninstall':'application bundle removed; installer receipt may remain' if system == 'Darwin' else None
},indent=2),encoding='utf-8')
