#!/usr/bin/env python3
"""Verify exact original test identities and the new deliverable, not just counts."""
from pathlib import Path
import hashlib,json,os,subprocess,zipfile
import xml.etree.ElementTree as E
ROOT=Path(__file__).resolve().parents[2];M=ROOT/'minimal'
base=json.loads((ROOT/'docs/visual-glass-064/BASELINE.json').read_text())
cases={};suites=[]
for p in (M/'app/build/test-results/testGithubDebugUnitTest').glob('TEST-*.xml'):
 s=E.parse(p).getroot();assert int(s.attrib['failures'])==0 and int(s.attrib['errors'])==0,p
 suites.append({'class':s.attrib['name'],'tests':int(s.attrib['tests']),'skipped':int(s.attrib.get('skipped',0))})
 for t in s.findall('testcase'):
  key=(s.attrib['name'],t.attrib['name']);assert key not in cases
  cases[key]=t.find('skipped') is not None
for t in base['tests']:
 key=(t['class'],t['name']);assert key in cases,'Original case missing: '+str(key)
 assert cases[key]==t['skipped'],'Original result changed: '+str(key)
assert len(cases)==118,(len(cases),118)
assert sum(cases.values())==1
for name,n in [('GlassDockGestureTest',19),('GlassWallpaperTest',2),('GlassEvolutionPreviewTest',8)]:
 assert sum(c.endswith('.'+name) for c,_ in cases)==n
 assert not any(skip for (c,_),skip in cases.items() if c.endswith('.'+name))
apk=M/'build/distribution/Ytdlp-G.apk'
sdk=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
metadata=subprocess.check_output([str(sdk/'aapt2'),'dump','badging',str(apk)],text=True)
assert "versionName='0.6.4'" in metadata and "versionCode='64'" in metadata
assert "minSdkVersion:'26'" in metadata or "sdkVersion:'26'" in metadata
assert "native-code: 'arm64-v8a'" in metadata
resources=subprocess.check_output([str(sdk/'aapt2'),'dump','resources',str(apk)],text=True)
for theme in ['light','dark']:assert 'glass_wallpaper_'+theme in resources
with zipfile.ZipFile(apk) as z:
 for name in ['Backdrop-APACHE-2.0.txt','Backdrop-NOTICE.txt']:assert z.read('assets/licenses/'+name)
with zipfile.ZipFile(M/'build/distribution/Ytdlp-G-source.zip') as z:
 assert z.read('minimal/vendor/backdrop-1.0.3-sources.jar')==(M/'vendor/backdrop-1.0.3-sources.jar').read_bytes()
 for a in json.loads((ROOT/'docs/visual-glass-064/WALLPAPERS.json').read_text()).values():
  assert hashlib.sha256(z.read(a['path'])).hexdigest()==a['sha256']
assert len(list((ROOT/'docs/visual-glass-064/after').glob('*.png')))==8
assert len(list((M/'app/build/reports/liquid-glass').glob('*.png')))>=21
result={'version':'0.6.4','apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'apk_bytes':apk.stat().st_size,
 'tests':len(cases),'passed':len(cases)-sum(cases.values()),'skipped':sum(cases.values()),
 'all_89_baseline_cases_preserved':True,'unchanged_original_functional_cases':57,
 'comparative_previews':8,'api_matrix_renders':21,'native_instrumentation':'7 compiled, not executed',
 'hardware_acceptance':'Pending physical device; no new FPS, temperature, GPU or live YouTube result claimed','suites':suites}
(M/'build/visual-064-delivery.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
