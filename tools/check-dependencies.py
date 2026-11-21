"""Audit resolved Maven dependencies and the transitive libraries of declared build plugins."""
import datetime, json, pathlib, re, urllib.request, xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[1]
packages=set()
for line in (root/'target/dependencies.txt').read_text(encoding='utf-8-sig').splitlines():
    match=re.search(r'([\w.\-]+):([\w.\-]+):jar:([^:\s]+):(test|compile|runtime|provided)',line)
    if match: packages.add((match[1]+':'+match[2],match[3]))
if not packages: raise SystemExit('No resolved dependencies found; refusing an empty scan.')
ns={'m':'http://maven.apache.org/POM/4.0.0'}
pom=ET.parse(root/'pom.xml'); plugins=set()
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
report={'checked_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'results':results,
        'scope':'Resolved application/test Maven artifacts, declared Maven plugins and their resolved transitive dependencies. Excludes JDK, OS, installer tools, Maven core and undeclared lifecycle plugins.'}
(root/'target/dependency-security.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
count=sum(len(r['vulnerabilities']) for r in results)
print(f'OSV: {len(results)} package versions queried; {count} advisory matches.')
if count: raise SystemExit('Dependency advisories require review; see target/dependency-security.json')
