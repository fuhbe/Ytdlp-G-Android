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
assert len(baseline)==158
for t in baseline:
 key=(t['class'],t['name'])
 if key[0].endswith('GlassDockGestureTest') and key[1]=='releaseBeyondRightEdgeDoesNotNavigate':key=(key[0],'releaseBeyondRightEdgeSelectsTheExtremeDestination')
 assert key in cases,'Original or explicitly migrated test missing: '+str(key)
 assert cases[key]==t['skipped'],'Baseline test outcome changed: '+str(key)
assert len(cases)==174,(len(cases),174)
assert sum(cases.values()) in [1,2]
for key,skip in cases.items():
 if skip:assert (key[0].endswith('LiquidGlassInteractionTest') and key[1].startswith('exactDependencyLensShaderCompilesAndRefractsRealPixels')) or (key[0].endswith('GlassSliderRippleTest') and key[1]=='standardRippleIsAbsentButGlassResponseAndOutsideButtonIndicationRemain')
for cls,n in [('SquareGlassGestureTest',16),('SquareGlassMotionTest',2),('SquareGlassReducedMotionTest',1),('GlassOpticsRegressionTest',5),('GlassTotalInteractionTest',15),('GlassEdgeGestureTest',14),('GlassSliderRippleTest',2)]:
 assert sum(c.endswith('.'+cls) for c,_ in cases)==n
apk=M/'build/distribution/Ytdlp-G.apk'
sdk=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
metadata=subprocess.check_output([str(sdk/'aapt2'),'dump','badging',str(apk)],text=True)
assert "versionCode='68'" in metadata and "versionName='0.6.6-test3'" in metadata
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
scope=json.loads((M/'build/glass-edge-scope.json').read_text())
assert scope['drawing_layout_optics_illumination_deformation_unchanged'] and scope['engine_and_download_system_unchanged']
assert scope['previous_tests_preserved_except_one_obsolete_horizontal_escape_expectation']
reproduction=json.loads((HERE/'REPRODUCTION.json').read_text())
assert 'expected:<1> but was:<0>' in reproduction['failure']
assert not cases[('com.fuhbe.ytdlpg.simple.GlassEdgeGestureTest',reproduction['test'])]
metrics=json.loads((M/'app/build/reports/glass-066/invalidation.json').read_text())
assert metrics['extraCompositions']==metrics['extraSourceRecords']==metrics['extraReadbacks']==0
ripples=json.loads((HERE/'ripple/positive-control.json').read_text())
assert ripples['glassResponseChangedPixels']>100 and not ripples['hardwareVerified']
assert not cases[('com.fuhbe.ytdlpg.simple.GlassSliderRippleTest','disablingOnlySliderIndicationPreservesPressTapAndDragInteractions')]
result={'version':'0.6.6-test3','apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'apk_bytes':apk.stat().st_size,
 'certificate_sha256':cert,'same_key_as_published_064':cert=='35d6e13608735ab96e1b1c376900d106beeaffe00c12bc9e43d60b8372c1e5d4',
 'tests':len(cases),'passed':len(cases)-sum(cases.values()),'skipped':sum(cases.values()),
 'baseline_tests':158,'baseline_case_identities_unchanged':157,'obsolete_horizontal_escape_expectation_updated':True,
 'new_edge_cases':14,'new_ripple_cases':2,'native_instrumentation':'10 compiled, not executed',
 'reproduction':reproduction,'scope':scope,'held_motion':metrics,'ripple_positive_control':ripples,
 'physical_device_or_emulator_execution':False,'gpu_fps_or_phone_ripple_pixel_claim':False}
(M/'build/glass-edge-delivery.json').write_text(json.dumps(result,indent=2)+'\n');(HERE/'VERIFIED.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
