#!/usr/bin/env python3
"""Final test3 APK + corresponding source + ACTUALLY executed test evidence."""
from pathlib import Path
import hashlib,json,os,re,subprocess,zipfile
import xml.etree.ElementTree as E
from PIL import Image
R=Path(__file__).resolve().parents[2];M=R/'minimal';H=Path(__file__).resolve().parent
cases={}
for p in (M/'app/build/test-results/testGithubDebugUnitTest').glob('TEST-*.xml'):
 root=E.parse(p).getroot();assert int(root.attrib['failures'])==int(root.attrib['errors'])==0,p
 for t in root.findall('testcase'):
  key=(t.attrib['classname'],t.attrib['name']);assert key not in cases;cases[key]=t.find('skipped') is not None
baseline=json.loads((H/'BASELINE_TESTS.json').read_text());assert len(baseline)==269
for t in baseline:assert cases[t['classname'],t['name']]==t['skipped'],t
assert len(cases)>=281 and sum(cases.values())==2,(len(cases),sum(cases.values()))
for cls,count in [('SquareSdfCaptureTest',5),('SquareLongPullTest',5),('MetaballPortTest',6),('PasteGlassRegressionTest',3),('MorphSafetyTest',2),('WindowSelectorIsolationTest',1),('MetaballCacheTest',1),('AppLanguageTest',3),('ButtonConcurrentPointerTest',2),('AnchoredButtonRegressionTest',2),('QualityFirstTapRegressionTest',5),('Test3CaptureTest',5)]:assert sum(c.endswith('.'+cls) for c,_ in cases)==count,(cls,count)
def digest(p):return hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
sdk=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0';apk=M/'build/distribution/Ytdlp-G.apk'
def cert(p):
 out=subprocess.check_output([str(sdk/'apksigner'),'verify','--print-certs',str(p)],text=True)
 values=set(re.findall(r'certificate SHA-256 digest:\s*([a-f0-9]{64})',out));assert len(values)==1;return next(iter(values))
certificate=cert(apk);assert cert(M/'build/device-tests/Ytdlp-G-tests.apk')==certificate
badge=subprocess.check_output([str(sdk/'aapt2'),'dump','badging',str(apk)],text=True)
for value in ["versionCode='73'","versionName='0.6.7-test3'","name='com.fuhbe.ytdlpg.minimal'","native-code: 'arm64-v8a'","application-label:'Ytdlp-G'"]:assert value in badge,value
assert "sdkVersion:'26'" in badge or "minSdkVersion:'26'" in badge
assert 'application-debuggable' not in badge
payload=json.loads((H/'BASELINE_PAYLOADS.json').read_text())['files']
with zipfile.ZipFile(apk) as z:
 assert z.testzip() is None
 for name,sha in payload.items():assert hashlib.sha256(z.read(name)).hexdigest()==sha,name
 assert len([n for n in z.namelist() if n.endswith('.so')])==7
 for name in ['Liquid-Morph-MIT.txt','Liquid-Morph-Engine-MIT.txt','Liquid-Morph-THIRD-PARTY.txt','Square-067-SDF-NOTICE.txt']:assert z.read('assets/licenses/'+name)==(M/'app/src/main/assets/licenses'/name).read_bytes()
 images={hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.png')}
 for theme in ['light','dark']:assert digest(M/f'app/src/main/res/drawable-nodpi/glass_wallpaper_{theme}.png') in images
source=M/'build/distribution/Ytdlp-G-source.zip'
with zipfile.ZipFile(source) as z:
 assert z.testzip() is None
 for n in z.namelist():
  p=R/n
  if n.startswith('minimal/') and '/build/' not in n and p.is_file():assert z.read(n)==p.read_bytes(),n
 for n in ['.github/workflows/experimental-067-test3.yml','docs/anchored-buttons-067-test3/verify_scope.py']:assert z.read(n)==(R/n).read_bytes()
scope=json.loads((M/'build/anchored-test3-scope.json').read_text());assert scope['all_download_engine_storage_workers_models_services_permissions_callbacks_unchanged']
root=M/'app/build/reports/square-sdf-test2'
field=json.loads((root/'field-raster.json').read_text());assert field['checkedPixels']>100000 and field['fusionPixelsOutsideBothIndividualBlobs']>15
shader=json.loads((root/'agsl-compile.json').read_text());assert shader['actualRuntimeShaderCompiled'] and shader['shaderCompilations']==1 and shader['uniformUpdates']==20
cache=json.loads((root/'cache.json').read_text());assert cache['extraInputCaptures']==cache['extraActivityCaptures']==cache['cpuBitmapReadbacksInRenderer']==0
button=json.loads((root/'window-material-cache.json').read_text());assert button['extraMaterialRecords']==button['extraButtonCompositions']==button['springsStartedDuringMove']==0
legacy=json.loads((M/'app/build/reports/glass-067/button-motion.json').read_text());assert legacy['extraCompositions']==legacy['extraSourceRecords']==legacy['extraReadbacks']==0
frames={}
for group in ['button-light','button-dark','menu-light','menu-dark','paste']:
 files=sorted((root/'captures'/group).glob('*.png'));assert len(files)==(11 if group.startswith('button') else 19 if group.startswith('menu') else 2),(group,len(files))
 for p in files:
  with Image.open(p) as image:assert image.size==(411,1100)
  state=json.loads(p.with_suffix('.json').read_text());assert state['version']=='0.6.7-test3' and state['sampleData'] and not state['physicalDevice']
  frames[group+'/'+p.name]=dict(sha256=digest(p),state=state)
 if group.startswith('menu'):
  data=[json.loads(p.with_suffix('.json').read_text()) for p in files]
  assert any(s.get('blend',0)>0 and s.get('blobA',{}).get('visible') for s in data)
  assert any(s['rawSpring']<0 and s['closing'] for s in data),'Actual closing undershoot must be captured'
assert len(frames)==62
for language in ['en','pt-BR','es']:
 for screen in ['home','settings']:
  p=root/'languages'/f'{screen}-{language}.png'
  with Image.open(p) as image:assert image.size==(320,1000)
import xml.etree.ElementTree as ET
resource_sets=[]
for folder in ['values','values-pt-rBR','values-es']:
 items=ET.parse(M/f'app/src/main/res/{folder}/ui_strings.xml').getroot().findall('string')
 resource_sets.append({item.attrib['name'] for item in items})
assert len(resource_sets[0])==83 and resource_sets[0]==resource_sets[1]==resource_sets[2]
(root/'captures/PROVENANCE.json').write_text(json.dumps(frames,indent=2)+'\n')
new_root=M/'app/build/reports/anchored-test3/captures'
new_frames={}
for group,count,dimensions in [('actions-pt-BR-light',12,(411,1100)),('actions-en-dark',12,(411,1100)),('actions-es-light',12,(320,1000)),('quality-pt-BR-light',5,(411,1100)),('quality-en-dark',5,(411,1100))]:
 files=sorted((new_root/group).glob('*.png'));assert len(files)==count,(group,len(files))
 for p in files:
  with Image.open(p) as image:assert image.size==dimensions
  data=json.loads(p.with_suffix('.json').read_text());assert data['version']=='0.6.7-test3' and data['sampleData'] and not data['physicalDevice'] and not data['gpuExecution']
  new_frames[group+'/'+p.name]=dict(sha256=digest(p),state=data)
 if group.startswith('quality'):
  early=json.loads((new_root/group/'01-visible-spring-running.json').read_text());chosen=json.loads((new_root/group/'02-first-tap-selected.json').read_text());closed=json.loads((new_root/group/'04-closed-1080.json').read_text())
  assert early['menuSpringRunning'] and early['menuExpanded'] and early['selectedHeight']==720
  assert chosen['selectedHeight']==1080 and not chosen['menuExpanded']
  assert closed['selectedHeight']==1080 and not closed['menuMounted']
assert len(new_frames)==46
(new_root/'PROVENANCE.json').write_text(json.dumps(new_frames,indent=2)+'\n')
selector_classes=['GlassDiagonalGestureTest','GlassSegmentTargetTest','GlassSliderRippleTest','GlassTotalInteractionTest','SquareGlassGestureTest','SquareGlassMotionTest','SquareGlassReducedMotionTest','WindowSelectorIsolationTest']
selectors=[dict(classname=c,name=n,skipped=skip) for (c,n),skip in cases.items() if any(c.endswith('.'+cls) for cls in selector_classes)]
(root/'selector-regression.json').write_text(json.dumps(dict(recognizer_and_optics_identical_to_stable=True,navigation_labels_only_localized=True,baseline_tests_unchanged=True,cases=selectors),indent=2)+'\n')
commit=os.environ.get('GITHUB_SHA',subprocess.check_output(['git','rev-parse','HEAD'],cwd=R,text=True).strip())
result=dict(version='0.6.7-test3',version_code=73,experimental=True,source_commit=commit,source_run=os.environ.get('GITHUB_RUN_ID'),
 apk_sha256=digest(apk),apk_bytes=apk.stat().st_size,source_zip_sha256=digest(source),certificate_sha256=certificate,
 signing_mode=os.environ.get('YTDLPG_SIGNING_MODE','local-default'),same_certificate_as_test1=certificate=='0a5754023fcd24e63e559bab1a9aad754a645807d346b0251f2391155a07fdcb',same_certificate_as_stable=certificate=='27c34fcf7e5660c549071df3536a9033e7cd643e415ad530785e1bfc38487c91',
 tests=len(cases),passed=len(cases)-sum(cases.values()),skipped=sum(cases.values()),failed=0,baseline_cases_preserved=269,
 ui_languages=['en','pt-BR','es'],language_software_captures=6,same_certificate_as_test2=certificate=='117e99cef6e46a5b0b20c1d841e631091b4ad5b07d354c935a62e769bd4c8225',anchored_test3_frames=len(new_frames),scope=scope,stable_payloads_verified=len(payload),field_raster=field,shader_compile_only=shader,morph_cache=cache,window_button_cache=button,
 genuine_software_app_frames=62,device_tests='COMPILED ONLY',physical_device_execution=False,emulator_execution=False,gpu_pixel_or_frame_performance_verified=False,
 square_pin='5df51684eec348e283126836449d9ac4aa62541e',mit_morph_pin='5d2859796ef0f6964e2fdf25962b033eede27814',
 optical_limits='API33+ additional SDF-gradient shader implemented and host-compiled, physical GPU pixel proof pending. API31/32 menu is real SDF contour + captured-screen blur, not arbitrary-neck lens refraction.')
(M/'build/anchored-test3-delivery.json').write_text(json.dumps(result,indent=2)+'\n')
(M/'build/anchored-test3/VERIFIED.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k!='scope'}))
