#!/usr/bin/env python3
"""Inspect the final stable APK and actual complete suite, not compilation alone."""
from pathlib import Path
import hashlib,json,os,re,subprocess,zipfile
import xml.etree.ElementTree as E
R=Path(__file__).resolve().parents[3];M=R/'minimal';H=Path(__file__).resolve().parent
cases={}
for p in (M/'app/build/test-results/testGithubDebugUnitTest').glob('TEST-*.xml'):
 s=E.parse(p).getroot();assert int(s.attrib['failures'])==int(s.attrib['errors'])==0,p
 for t in s.findall('testcase'):
  key=(t.attrib['classname'],t.attrib['name']);assert key not in cases
  cases[key]=t.find('skipped') is not None
baseline=json.loads((H/'BASELINE_TESTS.json').read_text());assert len(baseline)==186
for t in baseline:assert cases[(t['class'],t['name'])]==t['skipped'],t
assert len(cases)==204 and sum(cases.values())==2,(len(cases),sum(cases.values()))
assert sum(c.endswith('.StableReleaseCaptureTest') for c,_ in cases)==18
apk=M/'build/distribution/Ytdlp-G.apk';sdk=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
def digest(p):return hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
def cert(p):
 s=subprocess.check_output([str(sdk/'apksigner'),'verify','--print-certs',str(p)],text=True)
 found=set(re.findall(r'^(?:Signer #\d+|V[234] Signer):? certificate SHA-256 digest:\s*([a-f0-9]{64})\s*$',s,re.M))
 assert len(found)==1;return next(iter(found))
certificate=cert(apk);assert cert(M/'build/device-tests/Ytdlp-G-tests.apk')==certificate
badge=subprocess.check_output([str(sdk/'aapt2'),'dump','badging',str(apk)],text=True)
for part in ["versionCode='70'","versionName='0.6.6'","name='com.fuhbe.ytdlpg.minimal'","native-code: 'arm64-v8a'","application-label:'Ytdlp-G'"]:assert part in badge,part
assert "sdkVersion:'26'" in badge or "minSdkVersion:'26'" in badge
assert 'application-debuggable' not in badge
with zipfile.ZipFile(apk) as z:
 assert z.testzip() is None
 native={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.so')};assert len(native)==7
 for n in ['Square-GPL-3.0.txt','Square-catalog-APACHE-2.0.txt','Square-NOTICE.txt','Backdrop-APACHE-2.0.txt','Backdrop-NOTICE.txt']:
  assert z.read('assets/licenses/'+n)==(M/'app/src/main/assets/licenses'/n).read_bytes()
 pngs={hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.png')}
 for theme in ['light','dark']:assert digest(M/f'app/src/main/res/drawable-nodpi/glass_wallpaper_{theme}.png') in pngs
with zipfile.ZipFile(M/'build/distribution/Ytdlp-G-source.zip') as z:
 assert z.testzip() is None
 for n in z.namelist():
  p=R/n
  if n.startswith('minimal/') and '/build/' not in n and p.is_file():assert z.read(n)==p.read_bytes(),n
folder=M/'app/build/reports/release-066'
expected={f'{screen}-{theme}.png' for screen in ['home','mp3','mp4','quality','playlist','downloads','completed','settings','about'] for theme in ['light','dark']}
assert {p.name for p in folder.glob('*.png')}==expected
from PIL import Image
images={}
for p in sorted(folder.glob('*.png')):
 with Image.open(p) as im:assert im.size==(411,1100) and im.mode=='RGBA',(p,im.size,im.mode)
 images[p.name]=dict(sha256=digest(p),bytes=p.stat().st_size)
manifest=dict(version='0.6.6',source_commit=os.environ.get('GITHUB_SHA',subprocess.check_output(['git','rev-parse','HEAD'],cwd=R,text=True).strip()),
 renderer='Actual Compose windows, including measured Popup window, Robolectric native Skia software; sample metadata and bundled-wallpaper thumbnails',language='English',physical_device_capture=False,images=images)
(folder/'PROVENANCE.json').write_text(json.dumps(manifest,indent=2)+'\n')
scope=json.loads((M/'build/stable-066-scope.json').read_text());assert scope['download_engine_workers_queue_storage_models_services_unchanged']
metrics=json.loads((M/'app/build/reports/glass-066/invalidation.json').read_text());assert metrics['extraCompositions']==metrics['extraSourceRecords']==metrics['extraReadbacks']==0
result=dict(version='0.6.6',version_code=70,source_commit=manifest['source_commit'],source_run=os.environ.get('GITHUB_RUN_ID'),
 apk_sha256=digest(apk),apk_bytes=apk.stat().st_size,source_zip_sha256=digest(M/'build/distribution/Ytdlp-G-source.zip'),certificate_sha256=certificate,
 owner_release_signing_configured=os.environ.get('YTDLPG_SIGNING_MODE')=='owner-configured',signing_mode=os.environ.get('YTDLPG_SIGNING_MODE','local-default'),
 tests=204,passed=202,skipped=2,approved_cases_preserved=186,final_capture_cases=18,scope=scope,native_payloads=native,
 screenshots=manifest,user_device_acceptance='Owner reported successful test4 behavior on Android 12; final changes preserve implementation logic and translate UI literals.',
 physical_device_or_emulator_execution_in_this_workflow=False,gpu_fps_claim=False,held_motion=metrics)
(M/'build/stable-066-delivery.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
