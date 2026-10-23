#!/usr/bin/env python3
"""Prove the diagonal fix changes gesture tracking only, never glass rendering or downloads."""
from pathlib import Path
import hashlib,json,subprocess
R=Path(__file__).resolve().parents[2];H=Path(__file__).resolve().parent
baseline=json.loads((H/'BASELINE.json').read_text());P='minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/'
allowed={'minimal/app/build.gradle.kts',P+'GlassSegmentOptics.kt',P+'GlassSlidingSegments.kt',P+'squareglass/DragGestureInspector.kt'}
def old(p):return subprocess.check_output(['git','show',baseline['commit']+':'+p],cwd=R,text=True)
protected=[]
for p,sha in baseline['files'].items():
 if p not in allowed:
  assert hashlib.sha256((R/p).read_bytes()).hexdigest()==sha,'Protected file changed: '+p
  protected.append(p)
p='minimal/app/build.gradle.kts';assert (R/p).read_text().replace('versionCode = 69','versionCode = 68').replace('0.6.6-test4','0.6.6-test3')==old(p)
p=P+'GlassSlidingSegments.kt';assert (R/p).read_text().replace('glassDragTarget(rowPoint(change),registry.size,ordered,startX)','glassDragTarget(rowPoint(change),registry.size,ordered,startX,edgePadding)')==old(p),'Rendering/layout/animation/lighting/cache change detected'
p=P+'GlassSegmentOptics.kt';a=old(p);b=(R/p).read_text()
assert a[:a.index('/** Captured horizontal')]==b[:b.index('/** Captured horizontal')]
assert a[a.index('    val direction=sign'):]==b[b.index('    val direction=sign'):],'Intermediate tolerance or optical math changed'
p=P+'squareglass/DragGestureInspector.kt';b=(R/p).read_text().replace('import androidx.compose.ui.input.pointer.PointerEventPass\n','')
b=b.replace('// The horizontal recognizer already owns this pointer. Consume\n                    // at Initial before scroll/click handlers run at Main, even\n                    // when the same finger travels outside the visual bounds.\n                    val event = awaitPointerEvent(PointerEventPass.Initial)','val event = awaitPointerEvent()')
assert b==old(p),'Unexpected recognizer change outside owned-event dispatch phase'
allowed_tests={'GlassEdgeGestureTest.kt','GlassDockGestureTest.kt','SquareGlassGestureTest.kt','GlassDiagonalGestureTest.kt'}
changed=subprocess.check_output(['git','diff','--name-only',baseline['commit']],cwd=R,text=True).splitlines()
for p in changed:
 assert p in allowed or p.startswith('docs/') or p=='.github/workflows/glass-diagonal-066.yml' or (p.startswith('minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/') and Path(p).name in allowed_tests),'Unexpected changed file: '+p
result=dict(baseline=baseline['commit'],protected_files_unchanged=len(protected),engine_downloads_workers_storage_services_unchanged=True,
 rendering_layout_refraction_reflections_deformation_lighting_cache_unchanged=True,animation_sources_unchanged=True,
 ripple_suppression_and_edge_capture_padding_unchanged=True,intermediate_tolerance_unchanged=True,
 scope='Captured gesture release target and owned pointer dispatch only; version metadata and corresponding regressions',version='0.6.6-test4')
out=R/'minimal/build/glass-diagonal-scope.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
