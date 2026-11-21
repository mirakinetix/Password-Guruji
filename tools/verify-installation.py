"""Installer checks for disposable GitHub-hosted runners ONLY; never run on user machines."""
import json, os, pathlib, platform, subprocess, tempfile, time
if os.environ.get('GITHUB_ACTIONS') != 'true':
    raise SystemExit('Installation verification is restricted to disposable GitHub Actions runners.')
root = pathlib.Path(__file__).resolve().parents[1]
out = root / 'target/native'
system = platform.system()
def run(*args): subprocess.run([str(a) for a in args], check=True)
if system == 'Linux':
    deb = next(out.glob('*.deb')); rpm = next(out.glob('*.rpm'))
    run('dpkg-deb','--info',deb); run('rpm','-qip',rpm)
    run('sudo','apt-get','install','-y',str(deb))
    executable = pathlib.Path('/opt/password-guruji/bin/Password Guruji')
    # jpackage uses the application name for the installation directory on some JDKs.
    if not executable.exists(): executable = pathlib.Path('/opt/Password Guruji/bin/Password Guruji')
    installed = 'DEB installed; RPM metadata inspected (RPM install requires an RPM-based OS)'
elif system == 'Darwin':
    pkg = next(out.glob('*.pkg')); dmg = next(out.glob('*.dmg'))
    run('pkgutil','--check-signature',pkg) if False else None  # unsigned by design
    run('pkgutil','--payload-files',pkg)
    run('hdiutil','verify',dmg)
    run('sudo','installer','-pkg',pkg,'-target','/')
    executable = pathlib.Path('/Applications/Password Guruji.app/Contents/MacOS/Password Guruji')
    installed = 'PKG installed; DMG image verified'
else:
    msi = next(out.glob('*.msi'))
    result = subprocess.run(['msiexec.exe','/i',str(msi),'/qn','/norestart','/L*v',str(out/'installer.log')])
    if result.returncode not in (0,3010): raise RuntimeError(f'MSI install failed: {result.returncode}')
    executable = pathlib.Path(os.environ['LOCALAPPDATA'])/'Password Guruji'/'Password Guruji.exe'
    installed = 'MSI installed; EXE generated but setup wizard not exercised'
if not executable.is_file(): raise RuntimeError(f'Installed launcher missing: {executable}')
with tempfile.TemporaryDirectory(prefix='password-guruji-ci-') as temp:
    # Always pass a disposable vault; never open an existing default vault.
    with (out/'launcher.log').open('w',encoding='utf-8') as log:
        process = subprocess.Popen([str(executable),'--sample-vault',str(pathlib.Path(temp)/'dummy.pgv')],stdout=log,stderr=log)
        try:
            time.sleep(8)
            if process.poll() is not None: raise RuntimeError(f'Installed launcher exited early: {process.returncode}')
        finally:
            if process.poll() is None: process.terminate()
            try: process.wait(timeout=15)
            except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=5)
(out/'installation-verification.json').write_text(json.dumps({'os':system,'installation':installed,
    'installed_launcher':str(executable),'startup':'process remained alive for 8 seconds; visual rendering not asserted',
    'shutdown':'terminated by harness; graceful window shutdown tested separately by DesktopTest'},indent=2),encoding='utf-8')
