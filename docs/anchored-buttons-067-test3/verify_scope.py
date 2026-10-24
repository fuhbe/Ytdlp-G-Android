#!/usr/bin/env python3
"""Exact surgical scope: test2 is the baseline; preserve all runtime/selector code."""
from pathlib import Path
import hashlib,json,subprocess
R=Path(__file__).resolve().parents[2];H=Path(__file__).resolve().parent
baseline=json.loads((H/'BASELINE.json').read_text());base=baseline['commit']
P='minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/'
allowed={P+n for n in ['SquareButtonMaterial.kt','ElasticGlassButton.kt','GlassButtonHost.kt','GlassMorphMenu.kt']}
version='minimal/app/build.gradle.kts';capture='minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/StableReleaseCaptureTest.kt';pull='minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/SquareLongPullTest.kt'
def old(path):return subprocess.check_output(['git','show',base+':'+path],cwd=R,text=True)
unchanged={}
for n,sha in baseline['files'].items():
 if n in allowed or n in [version,capture,pull]:continue
 assert hashlib.sha256((R/n).read_bytes()).hexdigest()==sha,'Out-of-scope original source changed: '+n
 unchanged[n]=sha
assert (R/version).read_text()==old(version).replace('versionCode = 72','versionCode = 73').replace('versionName = "0.6.7-test2"','versionName = "0.6.7-test3"')
assert (R/capture).read_text()==old(capture).replace('0.6.7-test2','0.6.7-test3')
assert (R/pull).read_text()==old(pull).replace('assertTrue("Long pull must go beyond the former 6dp overscan",t.translation.getDistance()>24f)','assertTrue("Long finger tracking must keep the visual body anchored",t.translation.getDistance()<=2f)')
# Only the geometry function changed here. Every Square material/highlight/cache
# statement, including its shader, is byte-identical to the accepted test2.
n=P+'SquareButtonMaterial.kt';assert (R/n).read_text().split('@RequiresApi(33)',1)[1]==old(n).split('@RequiresApi(33)',1)[1]
# Existing button gesture/click callback code and motion/spring are untouched.
n=P+'ElasticGlassButton.kt';current=(R/n).read_text();original=old(n)
assert current[current.index('internal class ElasticButtonMotion'):current.index('@Composable internal fun GlassButton')]==original[original.index('internal class ElasticButtonMotion'):original.index('@Composable internal fun GlassButton')]
assert current[current.index('    val observer='):current.index('    val shape=')]==original[original.index('    val observer='):original.index('    val shape=')]
click='.clickable(interactionSource=interaction,indication=null,enabled=enabled,role=Role.Button) { if(!entry.blockPointerClick)action() }';assert click in current and click in original
n=P+'GlassButtonHost.kt';current=(R/n).read_text();original=old(n)
assert current[current.index('    fun register'):current.index('internal val LocalGlassButtonHost')]==original[original.index('    fun register'):original.index('internal val LocalGlassButtonHost')]
assert 'entry.content?.invoke' not in current and 'val floating=' not in (R/(P+'ElasticGlassButton.kt')).read_text()
# No geometry/physics/shader/menu choices/destination logic is allowed to change.
for n in ['LiquidMorphPhysics.kt','GlassMetaballSdf.kt','MetaballGlassSurface.kt','GlassMorphGeometry.kt','GlassQualityPicker.kt','GlassOverlaySource.kt','MainActivity.kt','AppLanguage.kt']:
 assert (R/(P+n)).read_text()==old(P+n),n
for n in subprocess.check_output(['git','diff','--name-only',base],cwd=R,text=True).splitlines():
 assert n in allowed or n in [version,capture,pull] or n.startswith('docs/anchored-buttons-067-test3/') or n=='.github/workflows/experimental-067-test3.yml' or n.startswith('minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/Test3') or n.endswith('/AnchoredButtonRegressionTest.kt') or n.endswith('/QualityFirstTapRegressionTest.kt'),'Unexpected change: '+n
result=dict(baseline=base,compiled_baseline=baseline['compiled_commit'],protected_files_unchanged=len(unchanged),protected_sha256=unchanged,
 all_download_engine_storage_workers_models_services_permissions_callbacks_unchanged=True,
 all_four_selector_sources_and_effects_byte_identical_to_test2=True,
 all_languages_preferences_and_main_activity_unchanged=True,
 sdf_geometry_physics_and_shaders_unchanged=True,square_material_shader_highlight_and_cache_unchanged=True,
 button_observer_click_and_release_spring_unchanged=True,only_four_existing_visual_production_files_changed=True,
 no_signing_dependency_sdk_app_id_or_min_sdk_changes=True,
 visual_test_migration='One obsolete >24px whole-button-translation assertion is replaced by <=2px anchored travel. Test identity and all gesture/callback assertions are retained.')
p=R/'minimal/build/anchored-test3-scope.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:v for k,v in result.items() if k!='protected_sha256'}))
