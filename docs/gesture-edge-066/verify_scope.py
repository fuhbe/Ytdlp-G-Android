#!/usr/bin/env python3
"""Allow only edge recognition/commit, slider-local ripple suppression and build ID."""
from pathlib import Path
import hashlib,json,subprocess
R=Path(__file__).resolve().parents[2];H=Path(__file__).resolve().parent
base=json.loads((H/'BASELINE.json').read_text());prefix='minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/'
allowed={'minimal/app/build.gradle.kts',prefix+'GlassSlidingSegments.kt',prefix+'GlassSegmentOptics.kt','minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/GlassDockGestureTest.kt'}
protected=[]
for p,sha in base['files'].items():
 if p not in allowed:
  assert hashlib.sha256((R/p).read_bytes()).hexdigest()==sha,'Protected source changed: '+p
  protected.append(p)
def original(p):return subprocess.check_output(['git','show',base['commit']+':'+p],cwd=R,text=True)
p='minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/GlassDockGestureTest.kt'
old=original(p);a=old.index('    @Test fun releaseBeyondRightEdgeDoesNotNavigate()');b=old.index('    @Test fun verticalGestureDoesNotChangeDestinations()',a)
replacement='    @Test fun releaseBeyondRightEdgeSelectsTheExtremeDestination() {\n        show()\n        dock().performTouchInput { down(Offset(width/6f,centerY));moveTo(Offset(width+20f,centerY),300);up() }\n        assertEquals(listOf("settings"),calls)\n        compose.onNodeWithContentDescription("Configurações").assertIsSelected()\n    }\n'
assert (R/p).read_text()==old[:a]+replacement+old[b:],'Only the obsolete horizontal escape expectation may change'
p='minimal/app/build.gradle.kts'
assert (R/p).read_text().replace('versionCode = 68','versionCode = 67').replace('0.6.6-test3','0.6.6-test2')==original(p)
p=prefix+'GlassSegmentOptics.kt';old=original(p);new=(R/p).read_text()
assert old[:old.index('/** Directional 38%')]==new[:new.index('/** Captured horizontal')]
assert old[old.index('/** Bounded rubber band'):]==new[new.index('/** Bounded rubber band'):]
assert old[old.index('    val direction=sign'):old.index('/** Bounded rubber band')]==new[new.index('    val direction=sign'):new.index('/** Bounded rubber band')], 'Intermediate 38% tolerance must remain identical'
p=prefix+'GlassSlidingSegments.kt';old=original(p);new=(R/p).read_text()
# Remove only gesture registration/handlers for an exact comparison of every
# cache, drawing modifier, deformation calculation and original child layout.
a=old.index('        Box(Modifier.fillMaxWidth().pointerInput(')
b=old.index('\n        }) {\n            CompositionLocalProvider',a)
old=old[:a]+'        Box(Modifier.fillMaxWidth()) {'+old[b+len('\n        }) {'):]
new=new.replace('import androidx.compose.material3.ExperimentalMaterial3Api\n','').replace('import androidx.compose.material3.LocalRippleConfiguration\n','').replace('@OptIn(ExperimentalMaterial3Api::class)\n','')
a=new.index('    val edgePadding=with(density)');b=new.index('    Box(modifier.liquidGlass',a)
new=new[:a]+new[b:]
new=new.replace('.then(gestures).padding(4.dp)', '.padding(4.dp)')
new=new.replace('            // Suppress only Material\'s click indication in these selectors.\n            // Interaction events and the shared glass highlight stay intact.\n','').replace(',LocalRippleConfiguration provides null','')
assert new==old,'Drawing, layout, optics, illumination or deformation changed beyond the requested ripple configuration'
for p in ['GlassSlidingSegments.kt','GlassSegmentOptics.kt']:
 text=(R/prefix/p).read_text()
 for token in ['Engine.','RuntimeManager','WorkManager','Store.','DocumentFile','model.']:
  assert token not in text,(p,token)
result={'baseline':base['commit'],'protected_files_unchanged':len(protected),'previous_tests_preserved_except_one_obsolete_horizontal_escape_expectation':True,'drawing_layout_optics_illumination_deformation_unchanged':True,'animation_sources_unchanged':True,'intermediate_tolerance_unchanged':True,'engine_and_download_system_unchanged':True,'dependencies_permissions_wallpapers_unchanged':True,'ripple_suppression_scope':'Only Material indication inside shared slider content; existing glass highlight unchanged','version':'0.6.6-test3'}
out=R/'minimal/build/glass-edge-scope.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
