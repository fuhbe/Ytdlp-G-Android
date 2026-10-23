#!/usr/bin/env python3
"""Check real APK/source/test identities and evidence; never count a GPU build as a run."""
from pathlib import Path
import hashlib,json,os,subprocess,zipfile
import xml.etree.ElementTree as E
ROOT=Path(__file__).resolve().parents[2];M=ROOT/'minimal';HERE=Path(__file__).resolve().parent
baseline=json.loads((HERE/'BASELINE_TESTS.json').read_text())
cases={}
for p in (M/'app/build/test-results/testGithubDebugUnitTest').glob('TEST-*.xml'):
 suite=E.parse(p).getroot();assert int(suite.attrib['failures'])==0 and int(suite.attrib['errors'])==0,p
 for t in suite.findall('testcase'):
  key=(t.attrib['classname'],t.attrib['name']);assert key not in cases
  cases[key]=t.find('skipped') is not None
assert len(baseline)==138
for t in baseline:
 key=(t['class'],t['name']);assert key in cases,'Original test missing: '+str(key)
 assert cases[key]==t['skipped'],'Baseline test outcome changed: '+str(key)
assert len(cases)==143,(len(cases),143)
assert sum(cases.values())==1
for key,skip in cases.items():
 if skip:assert key[0].endswith('LiquidGlassInteractionTest') and key[1].startswith('exactDependencyLensShaderCompilesAndRefractsRealPixels')
for cls,n in [('SquareGlassGestureTest',16),('SquareGlassMotionTest',2),('SquareGlassReducedMotionTest',1),('GlassOpticsRegressionTest',5)]:
 assert sum(c.endswith('.'+cls) for c,_ in cases)==n
apk=M/'build/distribution/Ytdlp-G.apk'
sdk=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
metadata=subprocess.check_output([str(sdk/'aapt2'),'dump','badging',str(apk)],text=True)
assert "versionCode='66'" in metadata and "versionName='0.6.6-test'" in metadata
assert "name='com.fuhbe.ytdlpg.minimal'" in metadata
assert "sdkVersion:'26'" in metadata or "minSdkVersion:'26'" in metadata
assert "native-code: 'arm64-v8a'" in metadata
def certificate(path):
 output=subprocess.check_output([str(sdk/'apksigner'),'verify','--print-certs',str(path)],text=True)
 return next(line.split(': ',1)[1] for line in output.splitlines() if line.startswith('Signer #1 certificate SHA-256 digest:'))
cert=certificate(apk)
assert certificate(M/'build/device-tests/Ytdlp-G-tests.apk')==cert
required=['Square-GPL-3.0.txt','Square-catalog-APACHE-2.0.txt','Square-NOTICE.txt','Backdrop-APACHE-2.0.txt','Backdrop-NOTICE.txt']
with zipfile.ZipFile(apk) as z:
 assert z.testzip() is None
 for name in required:assert z.read('assets/licenses/'+name)==(M/'app/src/main/assets/licenses'/name).read_bytes()
 pngs={hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.png')}
 for theme in ['light','dark']:
  assert hashlib.sha256((M/f'app/src/main/res/drawable-nodpi/glass_wallpaper_{theme}.png').read_bytes()).hexdigest() in pngs
with zipfile.ZipFile(M/'build/distribution/Ytdlp-G-source.zip') as z:
 assert z.testzip() is None
 for name in z.namelist():
  path=ROOT/name
  if name.startswith('minimal/') and '/build/' not in name and path.is_file():
   assert z.read(name)==path.read_bytes(),'Source differs from binary build tree: '+name
 for name in required:assert z.read('minimal/app/src/main/assets/licenses/'+name)
 assert z.read('minimal/vendor/square/PROVENANCE.json')==(M/'vendor/square/PROVENANCE.json').read_bytes()
evidence=json.loads((HERE/'EVIDENCE.json').read_text());assert not evidence['hardware_refraction_verified']
assert len(evidence['sequences'])==8
for sequence in evidence['sequences']:
 assert len(sequence['frames'])==6
 for frame in sequence['frames']:assert hashlib.sha256((HERE/frame['path']).read_bytes()).hexdigest()==frame['sha256']
 assert (HERE/sequence['gif']).stat().st_size>0
assert len(list((HERE/'before').glob('*.png')))==8 and len(list((HERE/'after').glob('*.png')))==8
scope=json.loads((M/'build/visual-066-scope.json').read_text())
assert scope['dependencies_unchanged'] and scope['original_tests_byte_identical']
metrics=json.loads((HERE/'host-metrics/invalidation.json').read_text())
assert metrics['extraCompositions']==metrics['extraSourceRecords']==metrics['extraReadbacks']==0
lag=json.loads((HERE/'host-metrics/spring-lag.json').read_text());assert lag['fixedValue']==lag['target'] and abs(lag['oldValue']-lag['target'])>.02
probe=json.loads((HERE/'optical-probe/result.json').read_text());assert probe['changedInteriorPixels']>100 and probe['changedExteriorPixels']==0
result={'version':'0.6.6-test','apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'apk_bytes':apk.stat().st_size,
 'certificate_sha256':cert,'same_key_as_published_064':cert=='35d6e13608735ab96e1b1c376900d106beeaffe00c12bc9e43d60b8372c1e5d4',
 'tests':len(cases),'passed':len(cases)-sum(cases.values()),'skipped':sum(cases.values()),'all_138_baseline_cases_preserved':True,
 'native_instrumentation':'9 compiled, not executed','hardware_refraction_acceptance':'Pending API31/32 mesh and API33+ native lens on real hardware; no GPU/FPS/thermal/live-network claim',
 'comparative_ui_states':8,'app_motion_frames':48,'app_motion_sequences':8,'scope':scope,'host_invalidation':metrics,'host_optical_pixels':probe,'host_old_new_input':lag}
(M/'build/visual-066-delivery.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
