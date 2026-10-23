#!/usr/bin/env python3
"""Exact source preservation; permitted differences are presentation only."""
from pathlib import Path
import hashlib,json,subprocess
R=Path(__file__).resolve().parents[2];H=Path(__file__).resolve().parent
base=json.loads((H/'BASELINE.json').read_text());prefix='minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/'
visual=['GlassSlidingSegments.kt','GlassContentBackdrop.kt','GlassMeshLens.kt','squareglass/DampedDragAnimation.kt']
allowed={'minimal/app/build.gradle.kts',*(prefix+p for p in visual+['MainActivity.kt','GlassNavigation.kt','LiquidGlass.kt'])}
protected=[]
for p,sha in base['files'].items():
 if p not in allowed:
  assert hashlib.sha256((R/p).read_bytes()).hexdigest()==sha,'Protected implementation changed: '+p
  protected.append(p)
def original(p):return subprocess.check_output(['git','show',base['commit']+':'+p],cwd=R,text=True)
p='minimal/app/build.gradle.kts'
assert (R/p).read_text().replace('versionCode = 67','versionCode = 66').replace('0.6.6-test2','0.6.6-test')==original(p)
p=prefix+'LiquidGlass.kt'
assert (R/p).read_text().replace('GlassOverlayCapture(content)','content(Modifier, LocalGlassBackdrop.current)')==original(p)
p=prefix+'GlassNavigation.kt';expected=original(p).replace('val color=if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface','// Selection color is interpolated in the shared draw phase, including the lens.\n    val color=MaterialTheme.colorScheme.onSurface').replace('fontWeight=if(active) FontWeight.SemiBold else FontWeight.Normal','fontWeight=FontWeight.SemiBold')
assert (R/p).read_text()==expected,'Navigation callbacks/layout must remain exact'
p=prefix+'MainActivity.kt';expected=original(p)
expected=expected.replace('tween(120), label = "Button feedback"','androidx.compose.animation.core.spring(.65f, 500f), label = "Button feedback"').replace('scaleX = scale; scaleY = scale','scaleX = scale; scaleY = 2f-scale')
expected=expected.replace('tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface','tint = MaterialTheme.colorScheme.onSurface').replace('color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface','color = MaterialTheme.colorScheme.onSurface')
a=expected.index('    var expanded by remember { mutableStateOf(false) }',expected.index('@Composable private fun QualityCard('));b=expected.index('        Text(if (inspection.playlist)',a)
expected=expected[:a]+'    GlassCard {\n        Heading("Qualidade de vídeo")\n        GlassQualityPicker(listOf(0) + inspection.heights, selected, choose)\n'+expected[b:]
assert (R/p).read_text()==expected,'Quality options/callback and all model/engine code must remain exact'
for p in visual+['GlassSegmentOptics.kt','GlassQualityPicker.kt','GlassOverlaySource.kt']:
 text=(R/prefix/p).read_text()
 for token in ['Engine.','RuntimeManager','QueueWorker','WorkManager','YTDLRequest','DocumentFile','SQLiteDatabase','model.','Store.']:
  assert token not in text,(p,token)
result={'baseline':base['commit'],'protected_files_unchanged':len(protected),'all_previous_test_sources_unchanged':True,'dependencies_unchanged':True,'engine_models_workers_services_storage_wallpapers_unchanged':True,'quality_options_and_callback_unchanged':True,'navigation_callbacks_unchanged':True,'version':'0.6.6-test2'}
out=R/'minimal/build/glass-total-scope.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
