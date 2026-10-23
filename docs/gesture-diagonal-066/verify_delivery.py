#!/usr/bin/env python3
"""Check actual final APK, matching tests and preserved baseline; no device/GPU claims."""
from pathlib import Path
import hashlib,json,os,subprocess,zipfile
import xml.etree.ElementTree as E
R=Path(__file__).resolve().parents[2];M=R/'minimal';H=Path(__file__).resolve().parent
cases={}
for p in (M/'app/build/test-results/testGithubDebugUnitTest').glob('TEST-*.xml'):
 s=E.parse(p).getroot();assert int(s.attrib['failures'])==int(s.attrib['errors'])==0,p
 for t in s.findall('testcase'):
  key=(t.attrib['classname'],t.attrib['name']);assert key not in cases
  cases[key]=t.find('skipped') is not None
migrations={
 'releaseOutsideTheDockDoesNotNavigate':'capturedDragReleasedOutsideTheDockKeepsItsHorizontalDestination',
 'cancelledAndVerticallyEscapedEdgeDragsNeverCommit':'cancelledDragsNeverCommitButVerticalEscapeKeepsHorizontalSelection',
 'outsideReleaseAndVerticalGestureNeverCommit':'outsideReleaseCommitsButUnclaimedVerticalGestureDoesNot'}
base=json.loads((H/'BASELINE_TESTS.json').read_text());assert len(base)==174
for t in base:
 k=(t['class'],migrations.get(t['name'],t['name']))
 assert k in cases and cases[k]==t['skipped'],k
assert len(cases)==186 and sum(cases.values())==2,(len(cases),sum(cases.values()))
assert sum(c.endswith('.GlassDiagonalGestureTest') for c,_ in cases)==12
scope=json.loads((M/'build/glass-diagonal-scope.json').read_text())
assert scope['engine_downloads_workers_storage_services_unchanged'] and scope['rendering_layout_refraction_reflections_deformation_lighting_cache_unchanged']
apk=M/'build/distribution/Ytdlp-G.apk';sdk=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
meta=subprocess.check_output([str(sdk/'aapt2'),'dump','badging',str(apk)],text=True)
for fragment in ["versionCode='69'","versionName='0.6.6-test4'","name='com.fuhbe.ytdlpg.minimal'","native-code: 'arm64-v8a'"]:assert fragment in meta,fragment
assert "sdkVersion:'26'" in meta or "minSdkVersion:'26'" in meta
certs=subprocess.check_output([str(sdk/'apksigner'),'verify','--print-certs',str(apk)],text=True)
cert=next(x.split(': ',1)[1] for x in certs.splitlines() if x.startswith('Signer #1 certificate SHA-256 digest:'))
tests=subprocess.check_output([str(sdk/'apksigner'),'verify','--print-certs',str(M/'build/device-tests/Ytdlp-G-tests.apk')],text=True);assert cert in tests
with zipfile.ZipFile(apk) as z:
 assert z.testzip() is None
 native={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.so')}
 assert len(native)==7
 for n in ['Square-GPL-3.0.txt','Square-catalog-APACHE-2.0.txt','Square-NOTICE.txt','Backdrop-APACHE-2.0.txt','Backdrop-NOTICE.txt']:
  assert z.read('assets/licenses/'+n)==(M/'app/src/main/assets/licenses'/n).read_bytes()
 pngs={hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.png')}
 for theme in ['light','dark']:assert hashlib.sha256((M/f'app/src/main/res/drawable-nodpi/glass_wallpaper_{theme}.png').read_bytes()).hexdigest() in pngs
with zipfile.ZipFile(M/'build/distribution/Ytdlp-G-source.zip') as z:
 assert z.testzip() is None
 for n in z.namelist():
  p=R/n
  if n.startswith('minimal/') and '/build/' not in n and p.is_file():assert z.read(n)==p.read_bytes(),n
metrics=json.loads((M/'app/build/reports/glass-066/invalidation.json').read_text())
assert metrics['extraCompositions']==metrics['extraSourceRecords']==metrics['extraReadbacks']==0
result=dict(version='0.6.6-test4',apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),apk_bytes=apk.stat().st_size,
 certificate_sha256=cert,same_key_as_test3=cert=='209656bb1c44b9a0858087b0b775f081c7b2494bb8c8e20566c4eed6e815c8ef',
 tests=len(cases),passed=len(cases)-sum(cases.values()),skipped=sum(cases.values()),baseline_tests_preserved=174,
 obsolete_vertical_escape_expectations_updated=3,new_diagonal_cases=12,reproduction=json.loads((H/'REPRODUCTION.json').read_text()),
 scope=scope,native_payloads=native,held_motion=metrics,physical_device_or_emulator_execution=False,gpu_fps_claim=False)
(M/'build/glass-diagonal-delivery.json').write_text(json.dumps(result,indent=2)+'\n');(H/'VERIFIED.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
