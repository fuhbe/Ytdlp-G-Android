#!/usr/bin/env python3
"""Promote the approved test3 artifact without building or re-signing anything."""
from pathlib import Path
import hashlib, json, os, re, shutil, subprocess, sys, zipfile
import xml.etree.ElementTree as ET
from publish_verified_release import require, digest, read_json, archive, unique, api

ROOT=Path(__file__).resolve().parents[2]
REPO='fuhbe/Ytdlp-G-Android'
TAG='v0.6.6-test3'
SOURCE='eca84b671056349075db83ab2eab6a9590dcdeb3'
RUN=40018985216
APK_SHA='c4d7efd33a5b60eece2721508ee91679bd61d5daa73b0c2911f18b47905bea4a'
SOURCE_SHA='0ee9226dcf95cf7b75f62ebaa41bafffde8a3703b94a53d00c8e4337ef5bbf6e'
CERT='209656bb1c44b9a0858087b0b775f081c7b2494bb8c8e20566c4eed6e815c8ef'
STABLE='30a347f48cbf04ccf0973b3c5a2f6e869ecb81ed'
ARTIFACTS={
 'main':(12530596238,'Ytdlp-G-0.6.6-Test3-Edge-Fix','93f71cc3043c3608974a58d5460e5ec599e0e84c30cf6c1fd0dd77c65cb41c43'),
 'reports':(12529319020,'Slider-Edge-0.6.6-Test3-verification','e25af224d01d835ef2a24850c3977526cf850c8da23be0ca5c7e7e70c98ad680'),
 'device':(12530915403,'Slider-Edge-0.6.6-Test3-device-tests','cfab5a28fd55bc92ebb3ea0a793577ab2afefca7787de26da876e8b3f6c8465a')}
ASSETS=['Ytdlp-G.apk','Ytdlp-G-source.zip','Ytdlp-G-verification.zip','Ytdlp-G-device-tests.zip',
 'Ytdlp-G-icon.png','Ytdlp-G-screenshots.zip','LICENSE.txt','release-info.json','SHA256SUMS.txt']


def prepare(inputs, output):
 run=read_json(inputs/'run.json')
 require(run['id']==RUN and run['head_sha']==SOURCE and run['conclusion']=='success','Build provenance mismatch')
 require(run['head_repository']['full_name']==REPO and run['head_branch']=='fix/glass-edge-selection-066','Wrong build repository/branch')
 for key,(ident,name,sha) in ARTIFACTS.items():
  meta=read_json(inputs/(key+'.json'))
  require(meta['id']==ident and meta['name']==name and not meta['expired'],'Artifact identity mismatch')
  require(meta['workflow_run']['id']==RUN and meta['workflow_run']['head_sha']==SOURCE,'Artifact source mismatch')
  require(meta['digest']=='sha256:'+sha and digest(inputs/(key+'.zip'))==sha,'Artifact checksum mismatch')
  archive(inputs/(key+'.zip'),inputs/key)
 main=inputs/'main';reports=inputs/'reports'
 require(digest(main/'Ytdlp-G.apk')==APK_SHA and (main/'Ytdlp-G.apk').stat().st_size==59626044,'Approved APK changed')
 require(digest(main/'Ytdlp-G-source.zip')==SOURCE_SHA,'Corresponding source changed')
 tests=skipped=0
 for p in reports.rglob('TEST-*.xml'):
  suite=ET.parse(p).getroot()
  require(int(suite.attrib['failures'])==int(suite.attrib['errors'])==0,'Failed test suite')
  tests+=int(suite.attrib['tests']);skipped+=int(suite.attrib.get('skipped',0))
 require((tests,skipped)==(174,2),'Unexpected actual test totals')
 evidence=read_json(unique(reports,'glass-edge-delivery.json'))
 require(evidence==read_json(main/'VERIFIED.json'),'Delivered verification differs from actual reports')
 require(evidence['passed']==172 and evidence['apk_sha256']==APK_SHA and evidence['certificate_sha256']==CERT,'Verification identity mismatch')
 require(evidence['scope']['protected_files_unchanged']==162 and evidence['scope']['engine_and_download_system_unchanged'],'Scope verification failed')
 dex=read_json(unique(reports,'dex-bootstrap.json'))
 require(dex['pass'] and len(dex['checks'])==13,'APK bootstrap verification failed')
 checks=read_json(unique(reports,'zip-bootstrap-regression.json'))['checks']
 require(checks[0]['unshrunk']=='PASS' and checks[1]['without-rule']=='EXPECTED FAILURE reproduced' and checks[2]['with-rule']=='PASS','Archive regression failed')
 with zipfile.ZipFile(main/'Ytdlp-G-source.zip') as z:
  require(z.testzip() is None,'Source CRC failure')
  gradle=z.read('minimal/app/build.gradle.kts').decode()
  require('versionCode = 68' in gradle and 'versionName = "0.6.6-test3"' in gradle,'Source version mismatch')
 with zipfile.ZipFile(main/'Ytdlp-G.apk') as z:
  require(z.testzip() is None,'APK CRC failure')
  for n in ['YTDLnis-GPL-3.0.txt','yt-dlp.txt','Python.txt','quickjs.txt','GPL-3.0.txt','Square-GPL-3.0.txt','Square-catalog-APACHE-2.0.txt','Backdrop-APACHE-2.0.txt']:
   require(len(z.read('assets/licenses/'+n))>0,'Missing license '+n)
 output.mkdir(parents=True,exist_ok=True)
 for n in ['Ytdlp-G.apk','Ytdlp-G-source.zip','LICENSE.txt']:shutil.copyfile(main/n,output/n)
 shutil.copyfile(inputs/'reports.zip',output/'Ytdlp-G-verification.zip')
 shutil.copyfile(inputs/'device.zip',output/'Ytdlp-G-device-tests.zip')
 shutil.copyfile(ROOT/'docs/branding/icon.png',output/'Ytdlp-G-icon.png')
 gallery=ROOT/'docs/screenshots/0.6.6-test3'
 provenance=read_json(gallery/'PROVENANCE.json')
 pictures=sorted(gallery.glob('*.png'));require(len(pictures)==4,'Missing interface previews')
 for p in pictures:
  row=provenance['images'][p.name]
  require(digest(p)==row['sha256'],'Screenshot checksum mismatch')
  require(p.read_bytes()==(reports/'minimal/app/build/reports'/row['report_path']).read_bytes(),'Screenshot differs from approved build')
 with zipfile.ZipFile(output/'Ytdlp-G-screenshots.zip','w',zipfile.ZIP_DEFLATED) as z:
  for p in pictures+[gallery/'README.md',gallery/'PROVENANCE.json']:
   entry=zipfile.ZipInfo(p.name,date_time=(2026,10,23,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16
   z.writestr(entry,p.read_bytes())
 info=dict(version='0.6.6-test3',version_code=68,tag=TAG,source_commit=SOURCE,source_run=RUN,
  apk_sha256=APK_SHA,apk_bytes=59626044,signing_certificate_sha256=CERT,
  automated_tests=dict(total=174,passed=172,skipped=2),
  user_acceptance='Project owner approved this APK after testing on their Android 12 phone.',
  native_instrumentation='10 scenarios compiled; no laboratory device/GPU execution claimed.',
  screenshot_type='Actual Compose software renders from the approved CI run, with sample metadata; no live transfer or GPU claim.',
  build_performed=False,resign_performed=False,stable_064_preserved=True,
  artifact_digests={k:v[2] for k,v in ARTIFACTS.items()})
 (output/'release-info.json').write_text(json.dumps(info,indent=2)+'\n')
 notes=(ROOT/'docs/releases/v0.6.6-test3.md').read_text().replace('{{PRESENTATION_SHA}}',os.environ.get('GITHUB_SHA','local-preview'))
 (output/'release-notes.md').write_text(notes)
 (output/'SHA256SUMS.txt').write_text(''.join(digest(output/n)+'  '+n+'\n' for n in ASSETS if n!='SHA256SUMS.txt'))
 print('PASS: original approved APK, source, licenses, 172 passing tests, two explicit skips, bootstrap checks and exact CI screenshots')


def check_apk(inputs, output):
 require(digest(output/'Ytdlp-G.apk')==APK_SHA,'APK changed')
 certs=(output/'apk-signature.txt').read_text()
 found=set(re.findall(r'^Signer #\d+ certificate SHA-256 digest:\s*([a-f0-9]{64})$',certs,re.M))
 require(found=={CERT},'Signing certificate mismatch')
 package=(output/'apk-package.txt').read_text()
 for s in ["name='com.fuhbe.ytdlpg.minimal'","versionCode='68'","versionName='0.6.6-test3'","application-label:'Ytdlp-G'","native-code: 'arm64-v8a'"]:
  require(s in package,'Unexpected APK metadata: '+s)
 require("sdkVersion:'26'" in package or "minSdkVersion:'26'" in package,'Unexpected minimum Android version')
 print('PASS: unchanged signature, version and ARM64 Android package')


def publish(inputs, output):
 check_apk(inputs,output)
 target=os.environ['GITHUB_SHA']
 comparison=api('GET','compare/'+SOURCE+'...'+target)
 require(comparison['merge_base_commit']['sha']==SOURCE and comparison['status']=='ahead' and len(comparison['files'])<300,'Release target diverged')
 for p in comparison['files']:
  require(p['filename']=='README.md' or p['filename'].startswith('docs/') or p['filename']=='.github/workflows/publish-glass-066.yml','Runtime code changed after approved build')
 stable=api('GET','git/ref/tags/v0.6.4')
 require(stable['object']['sha']==STABLE,'Stable tag changed')
 ref=api('GET','git/ref/tags/'+TAG,allow404=True)
 if ref is None:ref=api('POST','git/refs',{'ref':'refs/tags/'+TAG,'sha':target})
 require(ref['object']==dict(sha=target,type='commit',url=f'https://api.github.com/repos/{REPO}/git/commits/{target}'),'Existing tag has another target')
 notes=(output/'release-notes.md').read_text()
 release=api('GET','releases/tags/'+TAG,allow404=True)
 if release is None:
  release=api('POST','releases',dict(tag_name=TAG,target_commitish=target,name='Ytdlp-G 0.6.6-test3 — Liquid Glass',body=notes,draft=True,prerelease=True))
 require(release['tag_name']==TAG,'Unexpected release')
 existing={a['name']:a for a in release['assets']}
 for name in ASSETS:
  p=output/name
  if name in existing:require(existing[name].get('digest')=='sha256:'+digest(p),'Existing asset differs: '+name)
  else:subprocess.run(['gh','release','upload',TAG,str(p),'--repo',REPO],check=True)
 release=api('GET','releases/'+str(release['id']))
 assets={a['name']:a for a in release['assets']}
 for name in ASSETS:
  require(name in assets and assets[name]['state']=='uploaded','Missing published asset')
  require(assets[name]['size']==(output/name).stat().st_size and assets[name].get('digest')=='sha256:'+digest(output/name),'Published asset integrity mismatch: '+name)
 release=api('PATCH','releases/'+str(release['id']),dict(name='Ytdlp-G 0.6.6-test3 — Liquid Glass',body=notes,draft=False,prerelease=True,make_latest='false'))
 require(not release['draft'] and release['prerelease'],'Release not publicly published')
 require(api('GET','git/ref/tags/v0.6.4')['object']['sha']==STABLE,'Stable tag changed during publication')
 require(api('GET','releases/latest')['tag_name']=='v0.6.4','Stable latest release changed')
 result=dict(tag=TAG,tag_commit=target,source_commit=SOURCE,release_url=release['html_url'],apk_sha256=APK_SHA,
  build_performed=False,resign_performed=False,assets=[dict(name=a['name'],bytes=a['size'],sha256=a['digest'],url=a['browser_download_url']) for a in release['assets']])
 (output/'publication.json').write_text(json.dumps(result,indent=2)+'\n')
 with open(os.environ.get('GITHUB_STEP_SUMMARY',output/'summary.md'),'a') as f:f.write(f'## Published {TAG}\n\n{release["html_url"]}\n\nExact approved APK reused without rebuilding or re-signing. Stable v0.6.4 preserved.\n')
 print('PASS: public release, tag and all uploaded assets verified')

if __name__=='__main__':
 command=sys.argv[1];inputs=Path(sys.argv[2]);output=Path(sys.argv[3])
 {'prepare':prepare,'check-apk':check_apk,'publish':publish}[command](inputs,output)
