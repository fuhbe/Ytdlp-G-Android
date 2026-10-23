#!/usr/bin/env python3
"""Keep every existing functional implementation and test byte-for-byte."""
from pathlib import Path
import hashlib,json,subprocess
ROOT=Path(__file__).resolve().parents[2]
BASE=json.loads((ROOT/'docs/visual-square-065/BASELINE.json').read_text())
allowed={
 'minimal/app/build.gradle.kts',
 'minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/MainActivity.kt',
 'minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/GlassNavigation.kt',
 'minimal/app/src/main/assets/licenses/Backdrop-NOTICE.txt',
}
protected=[]
for name,digest in BASE['files'].items():
 if name in allowed:continue
 assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest,'Protected file changed: '+name
 protected.append(name)
def original(name):return subprocess.check_output(['git','show',BASE['source_commit']+':'+name],cwd=ROOT,text=True)
name='minimal/app/build.gradle.kts'
assert (ROOT/name).read_text().replace('versionCode = 65','versionCode = 64').replace('versionName = "0.6.5-test"','versionName = "0.6.4"')==original(name)
name='minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/MainActivity.kt'
text=(ROOT/name).read_text()
text=text.replace('import androidx.compose.ui.semantics.selected\n','')
text=text.replace('import androidx.compose.ui.semantics.role\n','')
text=text.replace('GlassSelectionTrack(selected, labels.size, onSelected)','GlassSelectionTrack(selected, labels.size)')
text=text.replace('.glassSegmentItem(index).semantics { this.selected = active; role = Role.Tab }','')
text=text.replace('if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface','if (active) Color.White else MaterialTheme.colorScheme.onSurface')
text=text.replace(' Componentes visuais adaptados do Square/Convx (2026, GPLv3) e do catálogo Backdrop (Apache 2.0).','')
assert text==original(name),'Unexpected MainActivity change outside material/semantics/legal substitutions'
new_visual={
 'minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/GlassSlidingSegments.kt',
 'minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/SquareGlassMaterial.kt',
}
new_visual.update(str(p.relative_to(ROOT)) for p in (ROOT/'minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/squareglass').glob('*.kt'))
for name in new_visual|{'minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/GlassNavigation.kt'}:
 text=(ROOT/name).read_text()
 for token in ['Engine.','RuntimeManager','QueueWorker','WorkManager','YTDLRequest','DocumentFile','SQLiteDatabase','model.','Store.']:
  assert token not in text,'Nonvisual dependency: '+name+': '+token
manifest=json.loads((ROOT/'minimal/vendor/square/PROVENANCE.json').read_text())
assert manifest['commit']=='17ed2b87974cf6fec9c531ef3f1438448bb2b729'
for name,item in manifest['files'].items():
 assert hashlib.sha256((ROOT/'minimal/vendor/square'/name).read_bytes()).hexdigest()==item['sha256'],name
assert len(manifest['files'])==12
result={'baseline':BASE['source_commit'],'protected_files_unchanged':len(protected),
 'all_original_test_sources_unchanged':True,'main_activity_presentation_and_legal_substitutions_only':True,
 'download_dependencies_unchanged':True,'square_commit':manifest['commit'],'visual_dependency':'io.github.kyant0:backdrop:1.0.3',
 'minSdk':26,'abi':'arm64-v8a','applicationId':'com.fuhbe.ytdlpg.minimal'}
out=ROOT/'minimal/build/visual-square-scope.json';out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
