#!/usr/bin/env python3
"""Fail delivery if a visual-only patch changes the download/core contract."""
import hashlib,json,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
base=json.loads((ROOT/'docs/visual-glass/BASELINE.json').read_text())
changed=[]
for name,expected in base['protected_source_sha256'].items():
 if name in {'minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/MainActivity.kt','minimal/app/build.gradle.kts'}:continue
 path=ROOT/name
 assert path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest()==expected,'Protected source changed: '+name
 changed.append(name)
old=subprocess.check_output(['git','show',base['source_commit']+':minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/MainActivity.kt'],cwd=ROOT,text=True)
new=(ROOT/'minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/MainActivity.kt').read_text()
# Activity/platform entry points, share handling and file-opening helpers stay byte-identical.
assert old[old.index('class MainActivity'):old.index('private val Blue')]==new[new.index('class MainActivity'):new.index('private val Blue')]
assert old[old.index('private fun openFile'):]==new[new.index('private fun openFile'):]
# Business-facing expression contracts remain in the same presenter file.
contracts=['BackHandler(model.area != "home" || model.inspection?.playlist == true)',
 'val jobs by Store.jobs.collectAsState()', 'ActivityResultContracts.OpenDocumentTree()',
 'context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)',
 'model.setFolder(uri)', 'model.enqueue()', 'model::control', 'model::updateEngine', 'model::copyDiagnostics', 'model::cleanCompleted',
 'Policy.zipAllowed(model.selected.size)', 'model.choice = model.choice.copy(mp3 = it == 0)', 'model.choice = model.choice.copy(zip = it == 1)',
 'model.choice = model.choice.copy(height = it)', 'model.select(media.id)', 'if (inspection == null) ActionButton',
 '(listOf(0) + inspection.heights).forEach', 'model.setAppearance(value)', 'Storage.folderName(context, folder)']
for text in contracts:assert old.count(text)==new.count(text),'Presenter contract changed: '+text
old_gradle=subprocess.check_output(['git','show',base['source_commit']+':minimal/app/build.gradle.kts'],cwd=ROOT,text=True)
new_gradle=(ROOT/'minimal/app/build.gradle.kts').read_text().replace('versionCode = 63','versionCode = 62').replace('versionName = "0.6.3"','versionName = "0.6.2"').replace('    implementation("io.github.kyant0:backdrop:1.0.3")\n','')
assert old_gradle==new_gradle,'Nonvisual build/dependency change'
material=(ROOT/'minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/LiquidGlass.kt').read_text()
for token in ['Engine.','QueueWorker','RuntimeManager','YTDLRequest','WorkManager','DocumentFile','SQLiteDatabase','pointerInput']:
 assert token not in material,'Unexpected nonvisual material dependency: '+token
allowed = {
 'minimal/app/build.gradle.kts',
 'minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/MainActivity.kt',
 'minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/LiquidGlass.kt',
 'minimal/app/src/main/assets/licenses/Backdrop-APACHE-2.0.txt',
 'minimal/app/src/main/assets/licenses/Backdrop-NOTICE.txt',
 'minimal/vendor/backdrop-1.0.3-sources.jar',
 'minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/LiquidGlassUiPreviewTest.kt',
 'minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/LiquidGlassInteractionTest.kt',
 'minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/GlassApiMatrixTest.kt',
 'minimal/app/src/androidTest/java/com/fuhbe/ytdlpg/simple/LiquidGlassDeviceTest.kt',
 'minimal/app/src/androidTest/assets/backdrop-lens.agsl',
 '.github/workflows/visual-liquid-glass.yml'}
paths=subprocess.check_output(['git','diff',base['source_commit'],'--name-only'],cwd=ROOT,text=True).splitlines()
for name in paths:assert name in allowed or name.startswith('docs/visual-glass/'),'Unexpected nonvisual change: '+name
result={'baseline':base['source_commit'],'protected_files_unchanged':len(changed),'activity_entrypoints_unchanged':True,'file_opening_helpers_unchanged':True,'presenter_contracts_unchanged':len(contracts),'only_added_dependency':'io.github.kyant0:backdrop:1.0.3','minSdk':26,'applicationId':'com.fuhbe.ytdlpg.minimal'}
out=ROOT/'minimal/build/visual-scope.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))
