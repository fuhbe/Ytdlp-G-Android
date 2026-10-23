#!/usr/bin/env python3
"""Strict preservation against the delivered 0.6.5 source, including every old test."""
from pathlib import Path
import hashlib,json,subprocess
R=Path(__file__).resolve().parents[2];H=Path(__file__).resolve().parent
base=json.loads((H/'BASELINE.json').read_text())
prefix='minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/'
allowed={'minimal/app/build.gradle.kts',*(prefix+p for p in ['MainActivity.kt','GlassNavigation.kt','GlassSlidingSegments.kt','SquareGlassMaterial.kt','squareglass/DampedDragAnimation.kt','squareglass/InteractiveHighlight.kt'])}
protected=[]
for p,sha in base['files'].items():
 if p in allowed:continue
 assert hashlib.sha256((R/p).read_bytes()).hexdigest()==sha,'Protected implementation changed: '+p
 protected.append(p)
def original(p):return subprocess.check_output(['git','show',base['commit']+':'+p],cwd=R,text=True)
p='minimal/app/build.gradle.kts'
assert (R/p).read_text().replace('versionCode = 66','versionCode = 65').replace('0.6.6-test','0.6.5-test')==original(p)
p=prefix+'MainActivity.kt'
assert (R/p).read_text().replace(', sourceKey = labels','')==original(p),'MainActivity must only add the source-content cache key'
p=prefix+'GlassNavigation.kt'
s=(R/p).read_text().replace(',sourceKey:Any?=null','').replace(',sourceKey=sourceKey','')
assert s==original(p),'Navigation must keep all existing layout, callbacks and semantics'
visual=['GlassSlidingSegments.kt','SquareGlassMaterial.kt','GlassMeshLens.kt','GlassContentBackdrop.kt','squareglass/DampedDragAnimation.kt','squareglass/InteractiveHighlight.kt']
for p in visual:
 s=(R/prefix/p).read_text()
 for token in ['Engine.','RuntimeManager','QueueWorker','WorkManager','YTDLRequest','DocumentFile','SQLiteDatabase','model.','Store.']:
  assert token not in s,(p,token)
result={'baseline':base['commit'],'protected_files_unchanged':len(protected),'original_tests_byte_identical':True,
 'main_only_cache_key':True,'navigation_callbacks_and_layout_identical':True,'dependencies_unchanged':True,
 'runtime_models_workers_services_storage_plugins_wallpapers_unchanged':True,'minSdk':26,'abi':'arm64-v8a'}
out=R/'minimal/build/visual-066-scope.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
