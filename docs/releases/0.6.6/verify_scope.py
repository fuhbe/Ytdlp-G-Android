#!/usr/bin/env python3
"""Stable promotion: only version/signing metadata, English presentation text and docs."""
from pathlib import Path
import hashlib,json,subprocess
R=Path(__file__).resolve().parents[3];H=Path(__file__).resolve().parent
base=json.loads((H/'BASELINE.json').read_text());ui=json.loads((H/'UI_TRANSLATIONS.json').read_text());tests=json.loads((H/'UI_TEST_TRANSLATIONS.json').read_text())
def original(p):return subprocess.check_output(['git','show',base['approved_commit']+':'+p],cwd=R,text=True)
def translated(text,mapping):
 for a,b in mapping.items():text=text.replace(a,b) if a.startswith('\\nPython') else text.replace('"'+a+'"','"'+b+'"')
 return text
protected=translated_tests=0
for p,sha in base['files'].items():
 current=(R/p).read_bytes()
 if p=='minimal/app/build.gradle.kts':continue
 if p=='minimal/README.md':continue
 if p in ui:
  assert current.decode()==translated(original(p),ui[p]),'UI logic changed beyond approved English text: '+p
 elif '/src/test/' in p or '/src/androidTest/' in p:
  old=original(p);new=current.decode()
  assert new==old or new==translated(old,tests),'Test logic changed beyond translated selectors: '+p
  translated_tests+=new!=old
 else:
  assert hashlib.sha256(current).hexdigest()==sha,'Protected file changed: '+p
  protected+=1
p='minimal/app/build.gradle.kts';expected=original(p).replace('versionCode = 69','versionCode = 70').replace('0.6.6-test4','0.6.6')
block=(H/'SIGNING_CONFIG.txt').read_text()
expected=expected.replace('    buildTypes {',block+'    buildTypes {').replace('signingConfig = signingConfigs.getByName("debug")','signingConfig = signingConfigs.findByName("official") ?: signingConfigs.getByName("debug")')
assert (R/p).read_text()==expected,'Unexpected build/dependency change'
for p in subprocess.check_output(['git','diff','--name-only',base['approved_commit']],cwd=R,text=True).splitlines():
 assert p in base['files'] or p.startswith('docs/') or p in ['README.md','CHANGELOG.md','.github/workflows/stable-066.yml','.github/workflows/publish-stable-066.yml','minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/StableReleaseCaptureTest.kt'],'Unexpected new production path: '+p
result=dict(approved_commit=base['approved_commit'],approved_build_commit=base['approved_build_commit'],version='0.6.6',version_code=70,
 protected_files_unchanged=protected,translated_ui_sources=len(ui),existing_test_files_with_only_translated_literals=translated_tests,
 download_engine_workers_queue_storage_models_services_unchanged=True,dependencies_native_licenses_wallpapers_unchanged=True,
 liquid_glass_gesture_optics_rendering_layout_animation_logic_unchanged=True,scope='Version/signing metadata, English UI literals and selector literals, capture tests and documentation')
out=R/'minimal/build/stable-066-scope.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
