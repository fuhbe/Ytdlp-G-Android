#!/usr/bin/env python3
"""Fail closed if experimental visuals change approved functional code."""
from pathlib import Path
import hashlib,json,subprocess
R=Path(__file__).resolve().parents[2];H=Path(__file__).resolve().parent
base=json.loads((H/'BASELINE.json').read_text());assert base['commit']=='df74cb9fde1cf31d42870a3d905b4a99c72fa407'
def original(p):return subprocess.check_output(['git','show',base['commit']+':'+p],cwd=R).decode()
def local(p):return (R/p).read_text()
prefix='minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/'
ui={prefix+n for n in ['MainActivity.kt','GlassQualityPicker.kt','GlassOverlaySource.kt']}
version='minimal/app/build.gradle.kts';capture='minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/StableReleaseCaptureTest.kt'
preserved=[]
for p,sha in base['files'].items():
 if p in ui or p in [version,capture]:continue
 assert hashlib.sha256((R/p).read_bytes()).hexdigest()==sha,'Protected file changed: '+p
 preserved.append(p)
assert local(version)==original(version).replace('versionCode = 70','versionCode = 71').replace('versionName = "0.6.6"','versionName = "0.6.7-test1"'),'Dependencies/signing/SDK changed'
expected=original(prefix+'MainActivity.kt')
for edit in json.loads((H/'UI_INTEGRATION.json').read_text()):
 assert expected.count(edit['before'])==1,edit
 expected=expected.replace(edit['before'],edit['after'])
assert local(prefix+'MainActivity.kt')==expected,'Unexpected action callback or screen changes'
assert local(capture)==original(capture).replace('0.6.6','0.6.7-test1'),'Baseline capture assertions changed'
# Retain the previously tested placement compatibility helper verbatim.
def legacy(s):return s[s.index('internal class GlassQualityPosition'):s.index('@Composable',s.index('internal class GlassQualityPosition'))]
assert legacy(local(prefix+'GlassQualityPicker.kt'))==legacy(original(prefix+'GlassQualityPicker.kt'))
new_production={prefix+n for n in ['ElasticGlassButton.kt','GlassMorphGeometry.kt','GlassMorphMenu.kt']}
allowed=ui|new_production|{version,capture,'.github/workflows/experimental-067.yml'}
for p in subprocess.check_output(['git','diff','--name-only',base['commit']],cwd=R,text=True).splitlines():
 assert p in allowed or p.startswith('docs/visual-buttons-morph-067/') or p.startswith('minimal/app/src/test/') or p=='minimal/app/src/androidTest/java/com/fuhbe/ytdlpg/simple/Glass067DeviceTest.kt','Out-of-scope tracked change: '+p
for p in new_production:
 text=local(p)
 for forbidden in ['QueueWorker','RuntimeManager','Engine.','Store.','AppModel','MediaBackend','WorkManager','FFmpeg','enqueue(']:assert forbidden not in text,(p,forbidden)
result=dict(baseline_tag=base['tag'],baseline_commit=base['commit'],version='0.6.7-test1',version_code=71,
 protected_files_unchanged=len(preserved),protected_sha256={p:base['files'][p] for p in preserved},
 download_engine_workers_queue_storage_models_services_unchanged=True,existing_four_selector_sources_and_optics_unchanged=True,
 dependencies_signing_policy_min_sdk_wallpapers_unchanged=True,main_activity_callbacks_preserved=True,
 exceptions=['UI-only quality popup and frozen overlay capture','Four exact action button wrapper substitutions','Version metadata','Capture version assertions'])
out=R/'minimal/build/experimental-067-scope.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2)+'\n')
print('PASS: '+str(len(preserved))+' baseline files byte-identical; exact callback/version substitutions verified.')
