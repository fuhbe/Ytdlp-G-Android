#!/usr/bin/env python3
"""Freeze download/callback/selector sources against the compiled test1 + stable."""
from pathlib import Path
import hashlib,json,subprocess
R=Path(__file__).resolve().parents[2];H=Path(__file__).resolve().parent
baseline=json.loads((H/'BASELINE.json').read_text());commit=baseline['commit']
prefix='minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/'
ui={prefix+x for x in ['ElasticGlassButton.kt','GlassMorphGeometry.kt','GlassMorphMenu.kt','GlassOverlaySource.kt','MainActivity.kt','GlassNavigation.kt','GlassQualityPicker.kt']}
version='minimal/app/build.gradle.kts';stable_capture='minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/StableReleaseCaptureTest.kt'
geometry_test='minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/GlassMorphGeometryTest.kt'
def old(p,ref=commit):return subprocess.check_output(['git','show',ref+':'+p],cwd=R).decode()
preserved={}
for p,sha in baseline['files'].items():
 if p in ui or p in [version,stable_capture,geometry_test]:continue
 assert hashlib.sha256((R/p).read_bytes()).hexdigest()==sha,'Protected baseline changed: '+p
 preserved[p]=sha
expected=old(version).replace('versionCode = 71','versionCode = 72').replace('versionName = "0.6.7-test1"','versionName = "0.6.7-test2"')
assert (R/version).read_text()==expected,'Dependency/signing/SDK change'
p=prefix+'MainActivity.kt';expected=old(p)
for before,after in [
 ('GlassContentLayer { capture, dockBackdrop ->\n        Box(','GlassContentLayer { capture, dockBackdrop ->\n        GlassButtonHost {\n        Box('),
 ('        }\n        }\n    }\n}\n\n@Composable private fun Heading','        }\n        }\n        }\n    }\n}\n\n@Composable private fun Heading'),
 ('trailingIcon = { GlassIconButton(onClick = {','trailingIcon = { GlassIconButton(modifier = Modifier.padding(end = 8.dp, top = 4.dp, bottom = 4.dp).size(48.dp), onClick = {')]:
 assert expected.count(before)==1,before;expected=expected.replace(before,after,1)
localization=json.loads((H/'LOCALIZATION.json').read_text())
assert localization['authorization']=='Owner requested English, Brazilian Portuguese and Spanish in settings with Liquid Glass; downloads unchanged'
for name,change in localization['files'].items():
 path=prefix+name
 source=expected if name=='MainActivity.kt' else old(path)
 assert hashlib.sha256(source.encode()).hexdigest()==change['before_sha256']
 lines=source.splitlines(keepends=True)
 for op in reversed(change['operations']):
  assert lines[op['start']:op['end']]==op['before']
  lines[op['start']:op['end']]=op['after']
 assert ''.join(lines)==(R/path).read_text(),'Unexpected localization/source change: '+name
# Calls that affect real jobs, extraction, clipboard, folder access and navigation
# are separately checked against the compiled test1, independent of the UI patch.
import re
def action_calls(text):
 patterns=[r'model\.(?:analyze|enqueue|clear|select|setFolder|setAppearance|updateEngine|copyDiagnostics|cleanCompleted)\([^)]*\)',r'model::[A-Za-z]+',r'control\(job.id, "[a-z]+"\)',r'folderPicker.launch\([^\n]+',r'context.contentResolver.takePersistableUriPermission\([^\n]+']
 return {pattern:re.findall(pattern,text) for pattern in patterns}
assert action_calls((R/(prefix+'MainActivity.kt')).read_text())==action_calls(old(prefix+'MainActivity.kt')),'Functional action calls changed'
clipboard='model.clear(); model.url = clip.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()'
assert clipboard in (R/(prefix+'MainActivity.kt')).read_text() and clipboard in old(prefix+'MainActivity.kt')
assert (R/stable_capture).read_text()==old(stable_capture).replace('0.6.7-test1','0.6.7-test2'),'Unexpected baseline capture change'
selectors=['GlassSlidingSegments.kt','GlassNavigation.kt','GlassSegmentOptics.kt','GlassMeshLens.kt','GlassContentBackdrop.kt','SquareGlassMaterial.kt','LiquidGlass.kt']
selectors+=['squareglass/'+x for x in ['DampedDragAnimation.kt','DragGestureInspector.kt','InteractiveHighlight.kt']]
for name in selectors:
 p=prefix+name;text=(R/p).read_text()
 if name=='GlassNavigation.kt':
  text=text.replace('val localizedDescription=uiText(description)\n    ','').replace('contentDescription=localizedDescription','contentDescription=description').replace('Text(uiText(label),fontSize','Text(label,fontSize')
 assert text==old(p,baseline['stable']),'Approved selector/optical logic changed: '+p
for p in (R/'minimal/vendor/square-067-test2/shapes').rglob('*.kt'):
 dest=R/(prefix+'buttonsquare/shapes')/p.relative_to(R/'minimal/vendor/square-067-test2/shapes')
 assert dest.read_text()==p.read_text().replace('dev.lelonio.square.ui.glass.shapes','com.fuhbe.ytdlpg.simple.buttonsquare.shapes'),'Shape source changed beyond namespace'
for p in subprocess.check_output(['git','diff','--name-only',commit],cwd=R,text=True).splitlines():
 assert p.startswith('minimal/') or p.startswith('docs/square-sdf-067-test2/') or p=='.github/workflows/experimental-067-test2.yml','Unexpected root/engine script change: '+p
for vendor in ['square-067-test2','liquid-morph-067-test2']:
 root=R/'minimal/vendor'/vendor;manifest=json.loads((root/'PROVENANCE.json').read_text())
 for n,sha in manifest['files'].items():assert hashlib.sha256((root/n).read_bytes()).hexdigest()==sha,(vendor,n)
result=dict(baseline=commit,stable=baseline['stable'],protected_files_unchanged=len(preserved),protected_sha256=preserved,
 all_download_engine_workers_models_storage_services_unchanged=True,all_button_callbacks_exact=True,
 approved_selector_optics_sources_identical_to_stable=len(selectors)-1,
 navigation_labels_only_localized_with_gestures_optics_and_callbacks_unchanged=True,ui_languages=["en","pt-BR","es"],no_sdk_dependency_signing_or_app_id_changes=True,
 original_square_shapes_namespace_only=True,source_vendoring_hashes_verified=True,
 visual_test_migrations=['Two GlassMorphGeometryTest assertions now validate SDF fusion + vanishing Blob A, replacing obsolete Bezier/two-permanent-body expectations; case identities retained.'])
out=R/'minimal/build/square-sdf-test2-scope.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:v for k,v in result.items() if k!='protected_sha256'}))
