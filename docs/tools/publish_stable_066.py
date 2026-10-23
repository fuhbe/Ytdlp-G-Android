#!/usr/bin/env python3
"""Publish the verified stable artifact without rebuilding, re-signing or moving old tags."""
from pathlib import Path
import hashlib,json,os,re,shutil,subprocess,sys,urllib.error,urllib.request,zipfile
import xml.etree.ElementTree as ET
from publish_verified_release import api,archive,digest,read_json,require,unique
R=Path(__file__).resolve().parents[2];H=R/'docs/releases/0.6.6'
REPO='fuhbe/Ytdlp-G-Android';TAG='v0.6.6'
DESCRIPTION='Download YouTube videos, music and playlists as MP3, MP4 or ZIP. Android app with an interactive Liquid Glass interface.'
ASSETS=['Ytdlp-G.apk','Ytdlp-G-source.zip','Ytdlp-G-verification.zip','Ytdlp-G-device-tests.zip',
 'Ytdlp-G-screenshots.zip','Ytdlp-G-icon.png','LICENSE.txt','CHANGELOG.md','BUILD_REPORT.md','release-info.json','SHA256SUMS.txt']
def config():return read_json(H/'PUBLICATION_INPUT.json')
def prepare(inputs,out):
 cfg=config();run=read_json(inputs/'run.json')
 require(run['id']==cfg['run'] and run['head_sha']==cfg['source_commit'] and run['conclusion']=='success','Stable build identity/conclusion mismatch')
 require(run['head_repository']['full_name']==REPO and run['head_branch']=='release/stable-066','Unexpected build origin')
 for key,a in cfg['artifacts'].items():
  meta=read_json(inputs/(key+'.json'))
  require(meta['id']==a['id'] and meta['name']==a['name'] and not meta['expired'],'Artifact identity mismatch')
  require(meta['workflow_run']['id']==cfg['run'] and meta['workflow_run']['head_sha']==cfg['source_commit'],'Artifact provenance mismatch')
  require(meta['digest']=='sha256:'+a['sha256'] and digest(inputs/(key+'.zip'))==a['sha256'],'Artifact outer checksum mismatch')
  archive(inputs/(key+'.zip'),inputs/key)
 main=inputs/'main';reports=inputs/'reports';device=inputs/'device'
 for folder in [main,device]:
  for line in (folder/'SHA256SUMS.txt').read_text().splitlines():
   expected,name=line.split();require(digest(folder/name)==expected,'Inner package checksum mismatch: '+name)
 ev=read_json(main/'VERIFIED.json')
 require(ev==read_json(unique(reports,'stable-066-delivery.json')),'Stable delivery differs from actual reports')
 require(ev['version']=='0.6.6' and ev['version_code']==70 and ev['source_commit']==cfg['source_commit'],'Stable version/source mismatch')
 require(digest(main/'Ytdlp-G.apk')==ev['apk_sha256'] and (main/'Ytdlp-G.apk').stat().st_size==ev['apk_bytes'],'APK identity mismatch')
 require(digest(main/'Ytdlp-G-source.zip')==ev['source_zip_sha256'],'Corresponding source identity mismatch')
 cases={}
 for p in reports.rglob('TEST-*.xml'):
  suite=ET.parse(p).getroot();require(int(suite.attrib['failures'])==int(suite.attrib['errors'])==0,'Failed stable test suite')
  for t in suite.findall('testcase'):
   key=(t.attrib['classname'],t.attrib['name']);require(key not in cases,'Duplicate test identity');cases[key]=t.find('skipped') is not None
 require(len(cases)==204 and sum(cases.values())==2,'Actual stable test totals differ')
 for t in read_json(H/'BASELINE_TESTS.json'):require(cases[(t['class'],t['name'])]==t['skipped'],'Approved regression missing/changed')
 require(ev['tests']==204 and ev['passed']==202 and ev['skipped']==2,'Recorded results differ')
 require(ev['scope']==read_json(unique(reports,'stable-066-scope.json')),'Source preservation evidence mismatch')
 require(ev['scope']['download_engine_workers_queue_storage_models_services_unchanged'] and ev['scope']['liquid_glass_gesture_optics_rendering_layout_animation_logic_unchanged'],'Implementation preservation failed')
 dex=read_json(unique(reports,'dex-bootstrap.json'));require(dex['pass'] and len(dex['checks'])==13,'Native bootstrap checks failed')
 checks=read_json(unique(reports,'zip-bootstrap-regression.json'))['checks']
 require(checks[0]['unshrunk']=='PASS' and checks[1]['without-rule']=='EXPECTED FAILURE reproduced' and checks[2]['with-rule']=='PASS','Archive regression controls failed')
 approved=read_json(inputs/'approved.json')
 require(approved['id']==12532028276 and approved['workflow_run']['id']==40024024762 and approved['workflow_run']['head_sha']=='ecf036bfcb2a1c5fa9c57197783cd9fe5e9d8d32','Approved artifact origin mismatch')
 require(digest(inputs/'approved.zip')=='04bf495e0376421be88ce9b23dd5ef5e9a78c539b7ab648ba8ec0385647f9be2','Approved artifact checksum mismatch')
 with zipfile.ZipFile(inputs/'approved.zip') as old:
  require(old.testzip() is None,'Approved archive CRC failure')
  apk=old.read('Ytdlp-G.apk');require(hashlib.sha256(apk).hexdigest()=='ee4714f57438782310f6179710d296345bc84439680222a4dbd4ab93b5abe43b','Approved APK changed')
  import io
  with zipfile.ZipFile(io.BytesIO(apk)) as before,zipfile.ZipFile(main/'Ytdlp-G.apk') as after:
   protected=[n for n in before.namelist() if n.startswith(('lib/','assets/licenses/','res/raw/'))]
   for n in protected:require(before.read(n)==after.read(n),'Bundled engine/native/license payload changed: '+n)
   native_count=sum(n.endswith('.so') for n in protected);require(native_count==7,'Unexpected native payload count')
 with zipfile.ZipFile(main/'Ytdlp-G-source.zip') as z:
  require(z.testzip() is None,'Corresponding-source CRC failure')
  gradle=z.read('minimal/app/build.gradle.kts').decode();require('versionName = "0.6.6"' in gradle and 'versionCode = 70' in gradle,'Archive source metadata mismatch')
  for n in ['LICENSE','NOTICE.md','docs/releases/0.6.6/verify_scope.py','docs/releases/0.6.6/verify_delivery.py','minimal/vendor/square/PROVENANCE.json']:
   require(len(z.read(n))>0,'Missing source/license provenance: '+n)
 gallery=R/'docs/screenshots/0.6.6';manifest=read_json(gallery/'PROVENANCE.json')
 require(manifest==ev['screenshots'] and manifest['version']=='0.6.6' and manifest['language']=='English','Final screenshot provenance mismatch')
 require(len(manifest['images'])==18,'Missing required final captures')
 capture_roots=[p.parent for p in reports.rglob('PROVENANCE.json') if p.parent.name=='release-066']
 require(len(capture_roots)==1,'Missing/ambiguous original final capture root')
 require(read_json(capture_roots[0]/'PROVENANCE.json')==manifest,'Capture manifest differs from CI')
 for name,info in manifest['images'].items():
  p=gallery/name;require(digest(p)==info['sha256'],'Screenshot digest mismatch')
  require(p.read_bytes()==(capture_roots[0]/name).read_bytes(),'Screenshot differs from final application capture')
 out.mkdir(exist_ok=True,parents=True)
 for n in ['Ytdlp-G.apk','Ytdlp-G-source.zip','LICENSE.txt']:shutil.copyfile(main/n,out/n)
 shutil.copyfile(inputs/'reports.zip',out/'Ytdlp-G-verification.zip');shutil.copyfile(inputs/'device.zip',out/'Ytdlp-G-device-tests.zip')
 shutil.copyfile(R/'docs/branding/icon.png',out/'Ytdlp-G-icon.png');shutil.copyfile(R/'CHANGELOG.md',out/'CHANGELOG.md')
 with zipfile.ZipFile(out/'Ytdlp-G-screenshots.zip','w',zipfile.ZIP_DEFLATED) as z:
  for p in sorted(gallery.glob('*.png'))+[gallery/'README.md',gallery/'PROVENANCE.json']:
   entry=zipfile.ZipInfo(p.name,date_time=(2026,10,23,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;z.writestr(entry,p.read_bytes())
 info=dict(version='0.6.6',version_code=70,tag=TAG,stable=True,prerelease=False,source_commit=cfg['source_commit'],release_commit=os.environ.get('GITHUB_SHA','local-validation'),
  source_run=cfg['run'],apk_sha256=ev['apk_sha256'],apk_bytes=ev['apk_bytes'],source_zip_sha256=ev['source_zip_sha256'],certificate_sha256=ev['certificate_sha256'],
  signing_mode=ev['signing_mode'],owner_release_signing_configured=ev['owner_release_signing_configured'],tests=dict(total=204,passed=202,skipped=2),approved_cases_preserved=186,
  final_captures=18,native_payloads_identical_to_approved=native_count,bundled_payload_entries_identical=len(protected),scope=ev['scope'],build_performed_by_publisher=False,resign_performed_by_publisher=False)
 (out/'release-info.json').write_text(json.dumps(info,indent=2)+'\n')
 (out/'BUILD_REPORT.md').write_text((H/'DELIVERY.md').read_text())
 notes=(R/'docs/releases/v0.6.6.md').read_text().replace('{{PRESENTATION_SHA}}',os.environ.get('GITHUB_SHA','local-validation'))
 (out/'release-notes.md').write_text(notes)
 (out/'SHA256SUMS.txt').write_text(''.join(digest(out/n)+'  '+n+'\n' for n in ASSETS if n!='SHA256SUMS.txt'))
 print('PASS: final version, original complete suite, actual captures, corresponding source, all native/runtime/license payloads and artifact checksums')
def check_apk(inputs,out):
 cfg=config();ev=read_json(out/'release-info.json');require(digest(out/'Ytdlp-G.apk')==ev['apk_sha256'],'APK changed after preparation')
 text=(out/'apk-signature.txt').read_text();found=set(re.findall(r'^(?:Signer #\d+|V[234] Signer):? certificate SHA-256 digest:\s*([a-f0-9]{64})\s*$',text,re.M))
 require(found=={ev['certificate_sha256']},'Final certificate differs from the built artifact')
 badge=(out/'apk-package.txt').read_text()
 for p in ["versionCode='70'","versionName='0.6.6'","application-label:'Ytdlp-G'","name='com.fuhbe.ytdlpg.minimal'","native-code: 'arm64-v8a'"]:require(p in badge,'APK metadata mismatch: '+p)
 require(("sdkVersion:'26'" in badge or "minSdkVersion:'26'" in badge) and 'application-debuggable' not in badge,'Unexpected Android/release configuration')
 print('PASS: actual stable APK signature, release metadata and architecture')
def preserve_previous():
 old=read_json(H/'PREVIOUS_RELEASES.json')
 for tag in old['tags']:require(api('GET','git/ref/tags/'+tag['tag'])['object']['sha']==tag['sha'],'Previous tag moved')
 for row in old['releases']:
  release=api('GET','releases/'+str(row['id']));require(release['tag_name']==row['tag'] and release['prerelease']==row['prerelease'],'Previous release changed')
  assets=[dict(id=a['id'],name=a['name'],size=a['size'],digest=a.get('digest')) for a in release['assets']]
  require(sorted(assets,key=lambda a:a['id'])==sorted(row['assets'],key=lambda a:a['id']),'Previous release assets changed')
def publish(inputs,out):
 check_apk(inputs,out);cfg=config();target=os.environ['GITHUB_SHA'];preserve_previous()
 comparison=api('GET','compare/'+cfg['source_commit']+'...'+target)
 require(comparison['merge_base_commit']['sha']==cfg['source_commit'] and comparison['status']=='ahead' and len(comparison['files'])<300,'Release commit diverged from compiled implementation')
 for p in comparison['files']:require(p['filename'].startswith('docs/') or p['filename'] in ['README.md','CHANGELOG.md','.github/workflows/publish-stable-066.yml'],'Runtime implementation changed after verified build')
 ref=api('GET','git/ref/tags/'+TAG,allow404=True)
 if ref is None:ref=api('POST','git/refs',dict(ref='refs/tags/'+TAG,sha=target))
 require(ref['object']['type']=='commit' and ref['object']['sha']==target,'Existing stable tag points elsewhere')
 notes=(out/'release-notes.md').read_text();release=api('GET','releases/tags/'+TAG,allow404=True)
 if release is None:release=api('POST','releases',dict(tag_name=TAG,target_commitish=target,name='Ytdlp-G v0.6.6',body=notes,draft=True,prerelease=False))
 existing={a['name']:a for a in release['assets']}
 for name in ASSETS:
  if name in existing:require(existing[name].get('digest')=='sha256:'+digest(out/name),'Existing asset differs: '+name)
  else:subprocess.run(['gh','release','upload',TAG,str(out/name),'--repo',REPO],check=True)
 release=api('GET','releases/'+str(release['id']));assets={a['name']:a for a in release['assets']}
 for name in ASSETS:
  require(name in assets and assets[name]['state']=='uploaded','Missing release asset')
  require(assets[name]['size']==(out/name).stat().st_size and assets[name].get('digest')=='sha256:'+digest(out/name),'Uploaded asset integrity mismatch: '+name)
 release=api('PATCH','releases/'+str(release['id']),dict(name='Ytdlp-G v0.6.6',body=notes,draft=False,prerelease=False,make_latest='true'))
 latest=api('GET','releases/latest');require(latest['id']==release['id'] and latest['tag_name']==TAG and not latest['draft'] and not latest['prerelease'],'Not the latest official stable release')
 preserve_previous()
 result=dict(release_url=release['html_url'],tag=TAG,tag_commit=target,source_commit=cfg['source_commit'],stable=True,latest=True,prerelease=False,
  apk_sha256=read_json(out/'release-info.json')['apk_sha256'],previous_releases_and_tags_preserved=True,
  assets=[dict(name=a['name'],bytes=a['size'],digest=a.get('digest'),url=a['browser_download_url']) for a in release['assets']])
 (out/'publication.json').write_text(json.dumps(result,indent=2)+'\n')
 with open(os.environ.get('GITHUB_STEP_SUMMARY',out/'summary.md'),'a') as f:f.write('## Official stable v0.6.6 published\n\n'+release['html_url']+'\n\nFinal APK signature, checksums, source, captures and preserved previous releases verified.\n')
 print('PASS: latest public stable release, exact title, tag target and all assets verified')
def about(inputs,out):
 request=urllib.request.Request('https://api.github.com/repos/'+REPO,data=json.dumps(dict(description=DESCRIPTION)).encode(),method='PATCH',headers={'Authorization':'Bearer '+os.environ['GH_TOKEN'],'Accept':'application/vnd.github+json','Content-Type':'application/json','X-GitHub-Api-Version':'2022-11-28'})
 try:
  with urllib.request.urlopen(request,timeout=30) as response:result=json.load(response)
  require(result.get('description')==DESCRIPTION,'Repository description differs')
  record=dict(updated=True,description=DESCRIPTION);print('Repository About description updated in English.')
 except urllib.error.HTTPError as error:
  if error.code not in [401,403]:raise
  record=dict(updated=False,http_status=error.code,description=DESCRIPTION,reason='Current publishing credentials cannot edit repository administration metadata; owner access is required.')
  print('Repository About update requires owner administration access; prepared English description recorded.')
 (out/'repository-about.json').write_text(json.dumps(record,indent=2)+'\n')
if __name__=='__main__':
 command=sys.argv[1];inputs=Path(sys.argv[2]);out=Path(sys.argv[3]);{'prepare':prepare,'check-apk':check_apk,'publish':publish,'about':about}[command](inputs,out)
