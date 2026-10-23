#!/usr/bin/env python3
"""Check the actual visual build; a skipped host GPU test is never a pass."""
from pathlib import Path
import hashlib,json,subprocess,os,zipfile
import xml.etree.ElementTree as E
ROOT=Path(__file__).resolve().parents[2]
module=ROOT/'minimal'
rows=[]
for path in (module/'app/build/test-results').rglob('TEST-*.xml'):
 suite=E.parse(path).getroot()
 assert int(suite.attrib['failures'])==0 and int(suite.attrib['errors'])==0,path
 skipped=int(suite.attrib.get('skipped',0))
 if skipped:
  assert suite.attrib['name'].endswith('LiquidGlassInteractionTest') and skipped==1
  node=next(t for t in suite.findall('testcase') if t.find('skipped') is not None)
  assert node.attrib['name'].startswith('exactDependencyLensShaderCompilesAndRefractsRealPixels')
 rows.append({'name':suite.attrib['name'],'tests':int(suite.attrib['tests']),'skipped':skipped})
assert sum(r['tests'] for r in rows)==89
assert sum(r['skipped'] for r in rows) in (0,1)
apk=module/'build/distribution/Ytdlp-G.apk'
with zipfile.ZipFile(apk) as z:
 for name in ['Backdrop-APACHE-2.0.txt','Backdrop-NOTICE.txt']:assert z.read('assets/licenses/'+name)
with zipfile.ZipFile(module/'build/distribution/Ytdlp-G-source.zip') as z:
 assert z.read('minimal/vendor/backdrop-1.0.3-sources.jar')==(module/'vendor/backdrop-1.0.3-sources.jar').read_bytes()
images=list((module/'app/build/reports/liquid-glass').glob('*.png'))
assert len(images)>=21,len(images)
sdk=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
metadata=subprocess.check_output([str(sdk/'aapt2'),'dump','badging',str(apk)],text=True)
assert "versionName='0.6.3'" in metadata and "versionCode='63'" in metadata
assert "minSdkVersion:'26'" in metadata or "sdkVersion:'26'" in metadata
assert "native-code: 'arm64-v8a'" in metadata
result={'version':'0.6.3','apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'apk_bytes':apk.stat().st_size,'tests':sum(r['tests'] for r in rows),'passed':sum(r['tests']-r['skipped'] for r in rows),'skipped_gpu_tests':sum(r['skipped'] for r in rows),'ui_renders':len(images),'device_instrumentation':'Built; execution requires Android hardware','suites':rows}
(module/'build/visual-delivery.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))
