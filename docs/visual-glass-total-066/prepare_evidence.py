#!/usr/bin/env python3
from pathlib import Path
import subprocess,shutil,json,hashlib
from PIL import Image
R=Path(__file__).resolve().parents[2];H=Path(__file__).resolve().parent
for p in (R/'docs/visual-glass-066/after').glob('*.png'):
 target=H/'before'/p.name;target.parent.mkdir(exist_ok=True)
 target.write_bytes(subprocess.check_output(['git','show','28bdde369593f06445a78f61dfb1a7e5357f1b78:'+str(p.relative_to(R))],cwd=R))
shutil.copytree(R/'docs/visual-glass-064/after',H/'after',dirs_exist_ok=True)
shutil.copytree(R/'docs/visual-square-065/motion',H/'motion',dirs_exist_ok=True)
shutil.copytree(R/'minimal/app/build/reports/glass-066',H/'host-metrics',dirs_exist_ok=True)
sequences=[]
for theme in ['light','dark']:
 for control in ['format','files','navigation','history']:
  frames=sorted((H/'motion'/theme).glob(control+'-*.png'));assert len(frames)==6
  imgs=[Image.open(p).convert('RGB') for p in frames];gif=H/'motion'/theme/(control+'.gif')
  imgs[0].save(gif,save_all=True,append_images=imgs[1:],duration=[500,100,100,100,200,800],loop=0,disposal=2)
  sequences.append({'theme':theme,'control':control,'gif':str(gif.relative_to(H)),'frames':[str(p.relative_to(H)) for p in frames]})
for theme in ['light','dark']:
 frames=[H/'quality'/f'{theme}-{state}-window1.png' for state in ['opening','open','closing']]
 assert all(p.is_file() for p in frames)
 imgs=[Image.open(p).convert('RGBA') for p in frames]
 imgs[0].save(H/'quality'/f'{theme}.gif',save_all=True,append_images=imgs[1:],duration=[200,1000,200],loop=0,disposal=2)
files={str(p.relative_to(H)):hashlib.sha256(p.read_bytes()).hexdigest() for folder in ['before','after','motion','quality','optics','host-metrics'] for p in (H/folder).rglob('*') if p.is_file()}
result={'renderer':'Robolectric native Skia software; not emulator or phone GPU','hardware_verified':False,'sequences':sequences,'files':files}
(H/'EVIDENCE.json').write_text(json.dumps(result,indent=2)+'\n')
print('Eight controls/themes gesture sequences, actual popup animation frames, optical ink and cache evidence collected')
