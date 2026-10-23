#!/usr/bin/env python3
"""Collect actual test renders; GIFs contain only captured Compose frames."""
from pathlib import Path
import shutil,json,hashlib
from PIL import Image
ROOT=Path(__file__).resolve().parents[2];HERE=Path(__file__).resolve().parent
after=HERE/'after';after.mkdir(exist_ok=True)
for p in (ROOT/'docs/visual-glass-064/after').glob('*.png'):shutil.copy2(p,after/p.name)
assert len(list(after.glob('*.png')))==8
shutil.copytree(ROOT/'docs/visual-square-065/motion',HERE/'motion',dirs_exist_ok=True)
shutil.copytree(ROOT/'minimal/app/build/reports/glass-066',HERE/'host-metrics',dirs_exist_ok=True)
manifest={'renderer':'Robolectric native Skia, software window; fallback material',
 'hardware_refraction_verified':False,'motion':'Actual AppContent gestures; no synthesized frames or GPU/FPS claim',
 'sequences':[]}
for theme in ['light','dark']:
 for control in ['format','files','navigation','history']:
  paths=sorted((HERE/'motion'/theme).glob(control+'-*.png'));assert len(paths)==6
  images=[Image.open(p).convert('RGB') for p in paths]
  gif=HERE/'motion'/theme/(control+'.gif')
  images[0].save(gif,save_all=True,append_images=images[1:],duration=[500,100,100,100,200,800],loop=0,disposal=2)
  manifest['sequences'].append({'theme':theme,'control':control,'gif':str(gif.relative_to(HERE)),
   'frames':[{'path':str(p.relative_to(HERE)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in paths]})
(HERE/'EVIDENCE.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Collected 8 before/after states and 8 actual-gesture sequences (48 app frames). Native GPU acceptance pending.')
