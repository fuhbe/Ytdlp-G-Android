#!/usr/bin/env python3
"""Encode actual host-rendered application frames, never invent UI pixels."""
from pathlib import Path
from PIL import Image,ImageDraw
import hashlib,json,shutil,subprocess
R=Path(__file__).resolve().parents[2];M=R/'minimal';H=Path(__file__).resolve().parent
source=M/'app/build/reports/glass-067/captures';out=M/'app/build/reports/glass-067/gallery';out.mkdir(parents=True,exist_ok=True)
manifest={}
for group in ['button-light','button-dark','menu-light','menu-dark']:
 files=sorted((source/group).glob('*.png'));assert len(files)==(8 if group.startswith('button') else 9)
 frames=[]
 for p in files:
  frame=Image.open(p).convert('RGB');image=Image.new('RGB',(frame.width,frame.height+42),'#162337');image.paste(frame,(0,42))
  draw=ImageDraw.Draw(image);draw.text((8,4),'ACTUAL APP | SAMPLE DATA | HOST SOFTWARE',fill='white');draw.text((8,22),p.stem,fill='white');frames.append(image)
 frames[0].save(out/f'{group}.gif',save_all=True,append_images=frames[1:],duration=[600]+[180]*(len(frames)-2)+[700],loop=0)
 indices=[0,2,3,len(frames)-1] if group.startswith('button') else [0,3,5,7,8]
 thumbnails=[frames[i].resize((247,685)) for i in indices]
 strip=Image.new('RGB',(247*len(thumbnails),685),'#162337')
 for i,im in enumerate(thumbnails):strip.paste(im,(i*247,0))
 strip.save(out/f'{group}-sequence.png')
 manifest[group]=dict(renderer='Actual application windows / Robolectric native Skia software',physicalDevice=False,sampleData=True,
  frames=[dict(path=str(p.relative_to(M/'app/build/reports')),sha256=hashlib.sha256(p.read_bytes()).hexdigest(),state=json.loads(p.with_suffix('.json').read_text())) for p in files])
# Reuse the baseline's real application window capture, clearly labelled.
stable=R/'docs/screenshots/0.6.6/mp4-light.png'
if stable.exists():
 a=Image.open(stable).convert('RGB');b=Image.open(source/'menu-light/05-open.png').convert('RGB')
 canvas=Image.new('RGB',(a.width+b.width,max(a.height,b.height)+46),'#162337');canvas.paste(a,(0,46));canvas.paste(b,(a.width,46))
 d=ImageDraw.Draw(canvas);d.text((8,6),'v0.6.6 BASELINE (sample data)',fill='white');d.text((a.width+8,6),'0.6.7-test1 (sample data)',fill='white');d.text((8,26),'Both: actual host software application captures; not phone GPU screenshots',fill='white');canvas.save(out/'baseline-versus-test.png')
(out/'PROVENANCE.json').write_text(json.dumps(manifest,indent=2)+'\n')
# Old unchanged regression fixtures write PNGs into historical docs. Preserve
# their new output as test reports, then restore tracked historical bytes only.
base=json.loads((H/'BASELINE.json').read_text())['commit']
for name in subprocess.check_output(['git','diff','--name-only',base],cwd=R,text=True).splitlines():
 if name.startswith('docs/') and not name.startswith('docs/visual-buttons-morph-067/') and Path(name).suffix in ['.png','.gif']:
  p=R/name;dest=M/'app/build/reports/regression-generated'/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dest)
  p.write_bytes(subprocess.check_output(['git','show',base+':'+name],cwd=R))
print('Actual frame sequences encoded with explicit provenance. Historical tracked galleries preserved.')
