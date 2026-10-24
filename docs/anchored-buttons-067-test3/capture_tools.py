#!/usr/bin/env python3
"""Encode genuine test3 app frames and restore historical fixture output only."""
from pathlib import Path
import json,hashlib,shutil,subprocess
from PIL import Image,ImageDraw
R=Path(__file__).resolve().parents[2];H=Path(__file__).resolve().parent;M=R/'minimal'
root=M/'app/build/reports/anchored-test3';out=root/'gallery';out.mkdir(parents=True,exist_ok=True);manifest={}
for folder in sorted((root/'captures').iterdir()):
 if not folder.is_dir():continue
 paths=sorted(folder.glob('*.png'));assert len(paths)==(12 if folder.name.startswith('actions') else 5)
 frames=[]
 for p in paths:
  im=Image.open(p).convert('RGB');frame=Image.new('RGB',(im.width,im.height+44),'#14253C');frame.paste(im,(0,44));d=ImageDraw.Draw(frame)
  d.text((6,4),'ACTUAL APP / SAMPLE DATA / HOST SOFTWARE',fill='white');d.text((6,24),p.stem+' / slowed logical replay',fill='white');frames.append(frame)
 frames[0].save(out/(folder.name+'.gif'),save_all=True,append_images=frames[1:],duration=[700]+[220]*(len(frames)-2)+[900],loop=0)
 thumbs=[f.resize((247,round(f.height*247/f.width))) for f in frames]
 strip=Image.new('RGB',(247*len(thumbs),max(x.height for x in thumbs)),'#14253C')
 for i,t in enumerate(thumbs):strip.paste(t,(247*i,0))
 strip.save(out/(folder.name+'.png'))
 manifest[folder.name]=[dict(path=str(p.relative_to(root)),sha256=hashlib.file_digest(p.open('rb'),'sha256').hexdigest(),state=json.loads(p.with_suffix('.json').read_text())) for p in paths]
(out/'PROVENANCE.json').write_text(json.dumps(dict(actual_app=True,sample_data=True,physical_device=False,gpu_execution=False,playback='Real logical frames, slowed; not phone video/FPS',groups=manifest),indent=2)+'\n')
base=json.loads((H/'BASELINE.json').read_text())['commit']
for n in subprocess.check_output(['git','diff','--name-only',base],cwd=R,text=True).splitlines():
 if n.startswith('docs/') and not n.startswith('docs/anchored-buttons-067-test3/') and Path(n).suffix in ['.png','.gif']:
  src=R/n;dest=root/'historical-fixture-output'/n;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,dest);src.write_bytes(subprocess.check_output(['git','show',base+':'+n],cwd=R))
print('46 genuine test3 app captures encoded; historical tracked galleries restored.')
