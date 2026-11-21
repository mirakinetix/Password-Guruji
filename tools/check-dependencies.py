"""Audit Maven dependency graphs and high-confidence credentials in tracked text files."""
import datetime, json, pathlib, re, subprocess, sys, urllib.request, xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[1]
ns={'m':'http://maven.apache.org/POM/4.0.0'}
pom=ET.parse(root/'pom.xml')
if '--prepare-provider' in sys.argv:
    # Resolve the dynamically selected JUnit Platform provider in its own Maven graph.
    surefire=next(p for p in pom.findall('.//m:build/m:plugins/m:plugin',ns)
                  if p.findtext('m:artifactId',namespaces=ns)=='maven-surefire-plugin')
    dependency_plugin=next(p for p in pom.findall('.//m:build/m:plugins/m:plugin',ns)
                  if p.findtext('m:artifactId',namespaces=ns)=='maven-dependency-plugin')
    project=ET.Element('project',xmlns=ns['m'])
    for tag,value in [('modelVersion','4.0.0'),('groupId','com.mirakinetix.security'),('artifactId','provider-inventory'),('version','1')]:
        ET.SubElement(project,tag).text=value
    dependencies=ET.SubElement(project,'dependencies'); dependency=ET.SubElement(dependencies,'dependency')
    for tag,value in [('groupId','org.apache.maven.surefire'),('artifactId','surefire-junit-platform'),('version',surefire.findtext('m:version',namespaces=ns))]:
        ET.SubElement(dependency,tag).text=value
    build=ET.SubElement(project,'build'); plugins=ET.SubElement(build,'plugins')
    plugin_copy=ET.fromstring(ET.tostring(dependency_plugin))
    for element in plugin_copy.iter(): element.tag=element.tag.split('}')[-1]
    plugins.append(plugin_copy)
    (root/'target').mkdir(exist_ok=True)
    ET.ElementTree(project).write(root/'target/security-provider-pom.xml',encoding='utf-8',xml_declaration=True)
    print('Prepared JUnit Platform provider dependency inventory.')
    raise SystemExit(0)
(root/'target').mkdir(exist_ok=True)
(root/'target/dependency-security.json').write_text(json.dumps({'status':'incomplete','scope':'Scan did not complete; do not interpret this report as a clean result.'}),encoding='utf-8')

# High-confidence credential patterns only; never echo matching content.
rules={
    'private-key':r'-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----',
    'github-token':r'\b(?:gh[pousr]_[A-Za-z0-9]{36,255}|github_pat_[A-Za-z0-9_]{80,255})\b',
    'aws-access-key':r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b',
}
findings=[]; scanned=0
tracked=subprocess.check_output(['git','ls-files','-z'],cwd=root).decode('utf-8').split('\0')
for name in tracked:
    path=root/name
    if not name or not path.is_file(): continue
    if path.name != '.env' and path.suffix.lower() not in {'.java','.py','.xml','.yml','.yaml','.md','.ps1','.sh','.properties','.txt','.json','.csv','.env'}: continue
    contents=path.read_text(encoding='utf-8-sig',errors='replace'); scanned+=1
    for line_number,line in enumerate(contents.splitlines(),1):
        for rule,pattern in rules.items():
            if re.search(pattern,line): findings.append({'path':name,'line':line_number,'rule':rule})
source_report={'status':'fail' if findings else 'pass','files_scanned':scanned,'findings':findings,
    'scope':'Tracked text files; high-confidence private-key, GitHub-token and AWS-access-key patterns. Does not detect every credential or inspect encrypted/binary files.'}
(root/'target/source-security.json').write_text(json.dumps(source_report,indent=2),encoding='utf-8')
if findings: raise SystemExit('Potential credentials detected. Inspect target/source-security.json; matching values are not logged.')
print(f'Source patterns: {scanned} tracked text files; zero high-confidence matches.')
packages=set()
for line in (root/'target/dependencies.txt').read_text(encoding='utf-8-sig').splitlines():
    match=re.search(r'([\w.\-]+):([\w.\-]+):jar:([^:\s]+):(test|compile|runtime|provided)',line)
    if match: packages.add((match[1]+':'+match[2],match[3]))
if not packages: raise SystemExit('No resolved dependencies found; refusing an empty scan.')
provider_packages=set()
for line in (root/'target/provider-dependencies.txt').read_text(encoding='utf-8-sig').splitlines():
    match=re.search(r'([\w.\-]+):([\w.\-]+):jar:([^:\s]+):(test|compile|runtime|provided)',line)
    if match: provider_packages.add((match[1]+':'+match[2],match[3]))
if not any(name=='org.apache.maven.surefire:surefire-junit-platform' for name,version in provider_packages):
    raise SystemExit('JUnit Platform provider inventory is missing or incomplete.')
packages.update(provider_packages)
plugins=set()
for plugin in pom.findall('.//m:build/m:plugins/m:plugin',ns):
    name=plugin.findtext('m:groupId',namespaces=ns)+':'+plugin.findtext('m:artifactId',namespaces=ns)
    plugins.add(name); packages.add((name,plugin.findtext('m:version',namespaces=ns)))
active=False; resolved=set()
for line in (root/'target/plugin-dependencies.txt').read_text(encoding='utf-8-sig').splitlines():
    match=re.search(r'([\w.\-]+):([\w.\-]+):(?:jar|maven-plugin):([^:\s]+)',line)
    if line.startswith('   ') and not line.startswith('      '):
        active=bool(match and match[1]+':'+match[2] in plugins)
        if active: resolved.add(match[1]+':'+match[2])
    if active and match: packages.add((match[1]+':'+match[2],match[3]))
if resolved != plugins: raise SystemExit('Incomplete build-plugin resolution; refusing incomplete scan.')
queries=[{'package':{'ecosystem':'Maven','name':name},'version':version} for name,version in sorted(packages)]
def request_batch(items):
    req=urllib.request.Request('https://api.osv.dev/v1/querybatch',data=json.dumps({'queries':items}).encode(),headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req,timeout=90) as response: data=json.load(response)
    if len(data.get('results',[])) != len(items): raise RuntimeError('OSV response count mismatch')
    return data['results']
results=[]
for start in range(0,len(queries),100):
    batch=queries[start:start+100]
    for query,result in zip(batch,request_batch(batch)):
        vulns=result.get('vulns',[])
        while result.get('next_page_token'):
            result=request_batch([dict(query,page_token=result['next_page_token'])])[0]
            vulns.extend(result.get('vulns',[]))
        results.append({'package':query['package']['name'],'version':query['version'],'vulnerabilities':vulns})
report={'status':'fail' if any(r['vulnerabilities'] for r in results) else 'pass','checked_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'results':results,
        'scope':'Resolved application/test Maven artifacts, declared Maven plugins, JUnit Platform Surefire provider and their resolved transitive dependencies. Excludes JDK, OS, installer tools, Maven process/core distribution and undeclared lifecycle plugins/providers.'}
(root/'target/dependency-security.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
count=sum(len(r['vulnerabilities']) for r in results)
print(f'OSV: {len(results)} package versions queried; {count} advisory matches.')
if count: raise SystemExit('Dependency advisories require review; see target/dependency-security.json')
