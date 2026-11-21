"""Actual RPM/DEB transactions in disposable, digest-pinned Linux target environments.
Containers are required here because Ubuntu cannot validate Fedora RPM transactions.
They are used only by the public hosted CI job, never installed on a user's computer.
"""
import hashlib, json, os, pathlib, subprocess, sys, tempfile, time
IMAGES = {
    'fedora': 'registry.fedoraproject.org/fedora@sha256:fda16b8254a181b9f010930f16f0b33c8dc7cb680a8f6c934bc9da5b3c3a5fed',
    'debian': 'debian@sha256:a72b4433de1cf7d6bae0c4cee74d0547f58601a643b3375f070c51600d63b6b2',
}
if os.environ.get('GITHUB_ACTIONS') != 'true': raise SystemExit('Disposable GitHub Actions runners only.')

def run(*args):
    print('Running:', *args, flush=True)
    subprocess.run([str(a) for a in args], check=True, timeout=180)

def inside(family):
    root=pathlib.Path('/workspace'); out=root/'target/native'; evidence=pathlib.Path('/evidence')
    fmt='rpm' if family=='fedora' else 'deb'
    fixture=next((out/'upgrade-fixture').glob('*.'+fmt)); candidate=next(out.glob('*.'+fmt))
    def install(package):
        if family=='fedora': run('dnf','install','-y',package)
        else: run('apt-get','install','-y','--no-install-recommends',package)
    def executable():
        return next(p for p in (pathlib.Path('/opt/password-guruji/bin/Password Guruji'),pathlib.Path('/opt/Password Guruji/bin/Password Guruji')) if p.is_file())
    def probe(*args):
        app=executable().parents[1]/'lib'
        jar=app/'app/password-guruji-0.1.0.jar'
        expected=json.loads((out/'build-metadata.json').read_text())['application_jar_sha256']
        if hashlib.sha256(jar.read_bytes()).hexdigest()!=expected: raise RuntimeError('Installed JAR hash mismatch')
        run(app/'runtime/bin/java','-cp',str(jar)+':'+str(out/'probe-classes'),'com.mirakinetix.passwordguruji.PackagingProbe',*args)
    vault=pathlib.Path.home()/'.local/share/Mirakinetix/PasswordGuruji/vault.pgv'
    if vault.exists(): raise RuntimeError('Unexpected pre-existing vault')
    install(fixture)
    probe('seed',vault)
    before=hashlib.sha256(vault.read_bytes()).hexdigest()
    install(candidate)
    probe('verify',vault); probe()
    if hashlib.sha256(vault.read_bytes()).hexdigest()!=before: raise RuntimeError('Upgrade changed vault')
    command=['rpm','-q','--qf','%{VERSION}','password-guruji'] if family=='fedora' else ['dpkg-query','-W','-f=${Version}','password-guruji']
    version=subprocess.check_output(command,text=True).strip()
    if not version.startswith('1.0.1'): raise RuntimeError('Wrong installed native version: '+version)
    launcher=executable()
    with tempfile.TemporaryDirectory(prefix='guruji-container-') as temporary:
        process=subprocess.Popen([str(launcher),'--sample-vault',str(pathlib.Path(temporary)/'dummy.pgv')])
        try:
            time.sleep(8)
            if process.poll() is not None: raise RuntimeError('Native launcher exited early')
        finally:
            if process.poll() is None: process.terminate()
            try: process.wait(timeout=15)
            except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=5)
    run('dnf' if family=='fedora' else 'apt-get','remove','-y','password-guruji')
    if launcher.exists(): raise RuntimeError('Uninstall left launcher')
    if hashlib.sha256(vault.read_bytes()).hexdigest()!=before: raise RuntimeError('Uninstall changed vault')
    (evidence/(family+'-installation.json')).write_text(json.dumps({
        'target':family,'image':IMAGES[family],'os_release':pathlib.Path('/etc/os-release').read_text(),
        'source_commit':json.loads((out/'build-metadata.json').read_text())['commit'],
        'native_upgrade':'1.0.0 fixture to 1.0.1 candidate, same application source',
        'installed_runtime_checks':7,'vault_preserved_through_upgrade_and_uninstall':True,
        'native_launcher_alive_seconds':8,'visual_rendering_asserted':False,'vault_sha256':before
    },indent=2))

if len(sys.argv)==3 and sys.argv[1]=='--inside':
    inside(sys.argv[2])
else:
    root=pathlib.Path(__file__).resolve().parents[1]; out=root/'target/native'
    bootstrap={
        'fedora': 'dnf install -y python3 xorg-x11-server-Xvfb xauth dejavu-sans-fonts fontconfig libX11 libXext libXi libXrender libXtst alsa-lib && mkdir -p /usr/share/desktop-directories && xvfb-run -a python3 /workspace/tools/verify-linux-containers.py --inside fedora',
        'debian': 'set -eu; export DEBIAN_FRONTEND=noninteractive; apt-get update; apt-get install -y --no-install-recommends python3 xvfb fonts-dejavu-core fontconfig libx11-6 libxext6 libxi6 libxrender1 libxtst6 libasound2t64; mkdir -p /usr/share/desktop-directories; echo Starting Debian virtual display; Xvfb :91 -screen 0 1280x800x24 -nolisten tcp & display_pid=$!; trap "kill $display_pid 2>/dev/null || true" EXIT; for attempt in $(seq 1 25); do test ! -S /tmp/.X11-unix/X91 || break; kill -0 $display_pid; sleep 0.2; done; test -S /tmp/.X11-unix/X91; DISPLAY=:91 python3 -u /workspace/tools/verify-linux-containers.py --inside debian',
    }
    for family,image in IMAGES.items():
        with (out/(family+'-container.log')).open('w',encoding='utf-8') as log:
            subprocess.run(['docker','run','--rm','--init','--env','GITHUB_ACTIONS=true',
                '--mount',f'type=bind,src={root},dst=/workspace,readonly',
                '--mount',f'type=bind,src={out},dst=/evidence',image,
                'bash','-c',bootstrap[family]],stdout=log,stderr=subprocess.STDOUT,check=True,timeout=900)
