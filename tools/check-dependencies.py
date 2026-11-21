"""Query OSV for resolved Maven dependencies and declared build plugins. No vault data leaves this machine."""
import datetime, json, pathlib, re, urllib.request, xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[1]
packages=set()
for line in (root/'target/dependencies.txt').read_text(encoding='utf-8-sig').splitlines():
    match=re.search(r'([\w.\-]+):([\w.\-]+):jar:([^:\s]+):(test|compile|runtime|provided)',line)
    if match: packages.add((match[1]+':'+match[2],match[3]))
if not packages: raise SystemExit('No resolved dependencies found; refusing an empty scan.')
ns={'m':'http://maven.apache.org/POM/4.0.0'}
pom=ET.parse(root/'pom.xml')
for plugin in pom.findall('.//m:build/m:plugins/m:plugin',ns):
    packages.add((plugin.findtext('m:groupId',namespaces=ns)+':'+plugin.findtext('m:artifactId',namespaces=ns),plugin.findtext('m:version',namespaces=ns)))
results=[]
for name,version in sorted(packages):
    query={'package':{'ecosystem':'Maven','name':name},'version':version}
    vulns=[]
    while True:
        req=urllib.request.Request('https://api.osv.dev/v1/query',data=json.dumps(query).encode(),headers={'Content-Type':'application/json'})
        with urllib.request.urlopen(req,timeout=60) as response: result=json.load(response)
        vulns.extend(result.get('vulns',[]))
        token=result.get('next_page_token')
        if not token: break
        query['page_token']=token
    results.append({'package':name,'version':version,'vulnerabilities':vulns})
report={'checked_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'results':results,
        'scope':'Resolved application/test Maven artifacts and explicitly declared Maven plugins. Excludes JDK, OS, installer tools and transitive build-plugin dependencies.'}
(root/'target/dependency-security.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
count=sum(len(r['vulnerabilities']) for r in results)
print(f'OSV: {len(results)} package versions queried; {count} advisory matches.')
if count: raise SystemExit('Dependency advisories require review; see target/dependency-security.json')
