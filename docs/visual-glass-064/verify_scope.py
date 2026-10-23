#!/usr/bin/env python3
"""Exact nonvisual preservation against the user's working 0.6.3 branch."""
from pathlib import Path
import hashlib,json,subprocess
ROOT=Path(__file__).resolve().parents[2]
BASE=json.loads((ROOT/'docs/visual-glass-064/BASELINE.json').read_text())
allowed={
 'minimal/app/build.gradle.kts',
 'minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/MainActivity.kt',
 'minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/LiquidGlass.kt',
 'minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/GlassNavigation.kt',
 'minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/GlassWallpaper.kt',
 'minimal/app/src/main/res/drawable-nodpi/glass_wallpaper_light.png',
 'minimal/app/src/main/res/drawable-nodpi/glass_wallpaper_dark.png',
 'minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/GlassDockGestureTest.kt',
 'minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/GlassWallpaperTest.kt',
 'minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/GlassEvolutionPreviewTest.kt',
 'minimal/app/src/androidTest/java/com/fuhbe/ytdlpg/simple/GlassDockDeviceTest.kt',
 '.github/workflows/visual-glass-evolution.yml',
}
protected=[]
for name,digest in BASE['source_sha256'].items():
 if name in allowed:continue
 assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest,'Protected file changed: '+name
 protected.append(name)
def original(name):return subprocess.check_output(['git','show',BASE['source_commit']+':'+name],cwd=ROOT,text=True)
name='minimal/app/build.gradle.kts'
assert (ROOT/name).read_text().replace('versionCode = 64','versionCode = 63').replace('versionName = "0.6.4"','versionName = "0.6.3"')==original(name)
name='minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/MainActivity.kt'
current=(ROOT/name).read_text()
current=current.replace('isAppearanceLightStatusBars = !dark','isAppearanceLightStatusBars = false').replace('isAppearanceLightNavigationBars = !dark','isAppearanceLightNavigationBars = false')
current=current.replace(', interaction = { ((1f-scale)/.02f).coerceIn(0f,1f) }','')
current=current.replace('GlassSelectionTrack(selected, labels.size) {','Row(Modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(22.dp), prominent = true).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {')
current=current.replace('Row(Modifier.padding(horizontal = 10.dp, vertical = 12.dp),','Row(Modifier.then(if (active) Modifier.liquidGlass(RoundedCornerShape(19.dp), prominent = true, selected = true) else Modifier).padding(horizontal = 10.dp, vertical = 12.dp),')
assert current==original(name),'Unexpected MainActivity change outside exact material substitutions'
paths=subprocess.check_output(['git','diff',BASE['source_commit'],'--name-only'],cwd=ROOT,text=True).splitlines()
for name in paths:
 assert name in allowed or name.startswith('docs/visual-glass-064/'),'Unexpected path: '+name
for name in ['LiquidGlass','GlassNavigation','GlassWallpaper']:
 text=(ROOT/f'minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/{name}.kt').read_text()
 for token in ['Engine.','RuntimeManager','QueueWorker','WorkManager','YTDLRequest','DocumentFile','SQLiteDatabase','model.','Store.']:
  assert token not in text,'Nonvisual dependency: '+token
 if name!='GlassNavigation':assert 'pointerInput' not in text
result={'baseline':BASE['source_commit'],'protected_files_unchanged':len(protected),'all_original_test_sources_unchanged':True,'main_activity_exact_material_substitutions_only':True,'download_dependencies_unchanged':True,'visual_dependency':'io.github.kyant0:backdrop:1.0.3','minSdk':26,'abi':'arm64-v8a','applicationId':'com.fuhbe.ytdlpg.minimal'}
out=ROOT/'minimal/build/visual-064-scope.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))
