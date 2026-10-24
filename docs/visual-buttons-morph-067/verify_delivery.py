#!/usr/bin/env python3
"""Inspect delivered test APK, source and executed regression results."""
from pathlib import Path
import hashlib,json,os,re,subprocess,zipfile
import xml.etree.ElementTree as E
R=Path(__file__).resolve().parents[2];M=R/'minimal';H=Path(__file__).resolve().parent
cases={}
for p in (M/'app/build/test-results/testGithubDebugUnitTest').glob('TEST-*.xml'):
 s=E.parse(p).getroot();assert int(s.attrib['failures'])==int(s.attrib['errors'])==0,p
 for t in s.findall('testcase'):
  key=(t.attrib['classname'],t.attrib['name']);assert key not in cases
  cases[key]=t.find('skipped') is not None
baseline=json.loads((H/'BASELINE_TESTS.json').read_text());assert len(baseline)==204
for t in baseline:assert cases[(t['class'],t['name'])]==t['skipped'],t
assert len(cases)>=240 and sum(cases.values())==2,(len(cases),sum(cases.values()))
assert sum(c.endswith('.Glass067CaptureTest') for c,_ in cases)==4
apk=M/'build/distribution/Ytdlp-G.apk';sdk=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
def digest(p):return hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
def cert(p):
 s=subprocess.check_output([str(sdk/'apksigner'),'verify','--print-certs',str(p)],text=True)
 found=set(re.findall(r'^(?:Signer #\d+|V[234] Signer):? certificate SHA-256 digest:\s*([a-f0-9]{64})\s*$',s,re.M));assert len(found)==1;return next(iter(found))
certificate=cert(apk);assert cert(M/'build/device-tests/Ytdlp-G-tests.apk')==certificate
badge=subprocess.check_output([str(sdk/'aapt2'),'dump','badging',str(apk)],text=True)
for part in ["versionCode='71'","versionName='0.6.7-test1'","name='com.fuhbe.ytdlpg.minimal'","native-code: 'arm64-v8a'","application-label:'Ytdlp-G'"]:assert part in badge,part
assert "sdkVersion:'26'" in badge or "minSdkVersion:'26'" in badge
assert 'application-debuggable' not in badge
payload=json.loads((H/'BASELINE_PAYLOADS.json').read_text())
with zipfile.ZipFile(apk) as z:
 assert z.testzip() is None
 for n,sha in payload['files'].items():assert hashlib.sha256(z.read(n)).hexdigest()==sha,'Stable runtime/license payload changed: '+n
 pngs={hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.png')}
 for theme in ['light','dark']:assert digest(M/f'app/src/main/res/drawable-nodpi/glass_wallpaper_{theme}.png') in pngs
with zipfile.ZipFile(M/'build/distribution/Ytdlp-G-source.zip') as z:
 assert z.testzip() is None
 for n in z.namelist():
  p=R/n
  if n.startswith('minimal/') and '/build/' not in n and p.is_file():assert z.read(n)==p.read_bytes(),n
 for n in ['.github/workflows/experimental-067.yml','docs/visual-buttons-morph-067/verify_scope.py']:assert z.read(n)==(R/n).read_bytes()
scope=json.loads((M/'build/experimental-067-scope.json').read_text());assert scope['download_engine_workers_queue_storage_models_services_unchanged']
button=json.loads((M/'app/build/reports/glass-067/button-motion.json').read_text())
assert button['extraCompositions']==button['extraSourceRecords']==button['extraReadbacks']==0
morph=json.loads((M/'app/build/reports/glass-067/morph-source.json').read_text());assert morph['extraSourceRecords']==morph['releasedSourceUsers']==morph['cpuBitmapReadbacks']==0
captures=M/'app/build/reports/glass-067/captures';images={}
from PIL import Image
for group in ['button-light','button-dark','menu-light','menu-dark']:
 expected=8 if group.startswith('button') else 9
 files=sorted((captures/group).glob('*.png'));assert len(files)==expected,(group,len(files))
 states=[]
 for p in files:
  with Image.open(p) as im:assert im.size==(411,1100)
  metadata=json.loads(p.with_suffix('.json').read_text());assert metadata['sampleData'] and not metadata['physicalDevice'];states.append(metadata)
  images[f'{group}/{p.name}']=dict(sha256=digest(p),**metadata)
 assert len({digest(p) for p in files})>=4,group
 if group.startswith('menu'):assert any(s['neckConnected'] for s in states) and any(s['progress']==1 for s in states)
(captures/'PROVENANCE.json').write_text(json.dumps(images,indent=2)+'\n')
source=os.environ.get('GITHUB_SHA',subprocess.check_output(['git','rev-parse','HEAD'],cwd=R,text=True).strip())
result=dict(version='0.6.7-test1',version_code=71,experimental=True,source_commit=source,source_run=os.environ.get('GITHUB_RUN_ID'),
 apk_sha256=digest(apk),apk_bytes=apk.stat().st_size,source_zip_sha256=digest(M/'build/distribution/Ytdlp-G-source.zip'),certificate_sha256=certificate,
 stable_certificate_sha256='27c34fcf7e5660c549071df3536a9033e7cd643e415ad530785e1bfc38487c91',
 signing_continuity_with_stable=certificate=='27c34fcf7e5660c549071df3536a9033e7cd643e415ad530785e1bfc38487c91',
 signing_mode=os.environ.get('YTDLPG_SIGNING_MODE','local-default'),tests=len(cases),passed=len(cases)-sum(cases.values()),skipped=sum(cases.values()),failed=0,
 approved_cases_preserved=204,scope=scope,stable_payloads_verified=len(payload['files']),button_motion=button,morph_source=morph,
 genuine_software_app_frames=len(images),physical_device_execution=False,emulator_execution=False,device_tests='COMPILED ONLY',gpu_fps_claim=False,
 optical_limits='Generic liquid neck: captured-screen blur/rim only. Pinned AGSL lens supports rounded shapes only. API31/32 new buttons reuse cached mesh when available; advanced GPU optics require physical-device acceptance.')
p=M/'build/experimental-067-delivery.json';p.write_text(json.dumps(result,indent=2)+'\n')
(M/'build/experimental-067/VERIFIED.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
