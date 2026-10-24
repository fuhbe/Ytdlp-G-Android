#!/usr/bin/env python3
"""Encode only actual captured app pixels; preserve historical tracked galleries."""
from pathlib import Path
import hashlib,json,shutil,subprocess
from PIL import Image,ImageDraw
R=Path(__file__).resolve().parents[2];M=R/'minimal';H=Path(__file__).resolve().parent
root=M/'app/build/reports/square-sdf-test2';source=root/'captures';out=root/'gallery';out.mkdir(parents=True,exist_ok=True)
manifest={}
for group in ['button-light','button-dark','menu-light','menu-dark','paste']:
 files=sorted((source/group).glob('*.png'));assert len(files)==(11 if group.startswith('button') else 19 if group.startswith('menu') else 2),(group,len(files))
 frames=[]
 for p in files:
  image=Image.open(p).convert('RGB');assert image.size==(411,1100)
  frame=Image.new('RGB',(411,1144),'#14253C');frame.paste(image,(0,44))
  d=ImageDraw.Draw(frame);d.text((8,4),'ACTUAL APP | SAMPLE DATA | HOST SOFTWARE',fill='white');d.text((8,23),p.stem,fill='white');frames.append(frame)
 frames[0].save(out/f'{group}.gif',save_all=True,append_images=frames[1:],duration=[650]+[160]*(len(frames)-2)+[800],loop=0)
 indices=list(range(len(frames))) if len(frames)<=2 else [0,2,3,5,len(frames)-2,len(frames)-1]
 thumb=[frames[i].resize((247,686)) for i in indices];strip=Image.new('RGB',(247*len(thumb),686),'#14253C')
 for i,im in enumerate(thumb):strip.paste(im,(i*247,0))
 strip.save(out/f'{group}-sequence.png')
 manifest[group]=[dict(file=str(p.relative_to(root)),sha256=hashlib.sha256(p.read_bytes()).hexdigest(),state=json.loads(p.with_suffix('.json').read_text())) for p in files]
(out/'PROVENANCE.json').write_text(json.dumps(dict(renderer='Actual AppContent and Popup windows; native Skia software',sampleData=True,physicalDevice=False,gpuExecution=False,playback='Captured logical frames; slowed replay is not real-time phone video/FPS',frames=manifest),indent=2)+'\n')
base=json.loads((H/'BASELINE.json').read_text())['commit']
for name in subprocess.check_output(['git','diff','--name-only',base],cwd=R,text=True).splitlines():
 if name.startswith('docs/') and not name.startswith('docs/square-sdf-067-test2/') and Path(name).suffix in ['.png','.gif']:
  path=R/name;dest=root/'historical-regression-output'/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,dest)
  path.write_bytes(subprocess.check_output(['git','show',base+':'+name],cwd=R))
print('Encoded 62 actual application frames; historical tracked galleries restored from baseline.')
