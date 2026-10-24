#!/usr/bin/env python3
"""Add exact experimental build source and package only the test distribution."""
from pathlib import Path
import hashlib,json,shutil,zipfile
R=Path(__file__).resolve().parents[2];M=R/'minimal';H=Path(__file__).resolve().parent;out=M/'build/distribution'
source=out/'Ytdlp-G-source.zip'
paths=[R/n for n in ['README.md','CHANGELOG.md','LICENSE','NOTICE.md','.github/workflows/experimental-067-test3.yml']]
paths += [p for p in H.glob('*') if p.is_file() and p.suffix in ['.py','.md','.json','.txt','.patch']]
with zipfile.ZipFile(source,'a',zipfile.ZIP_DEFLATED) as z:
 existing=set(z.namelist())
 for p in sorted(paths):
  n=p.relative_to(R).as_posix();assert n not in existing,n;z.write(p,n)
with zipfile.ZipFile(source) as z:assert z.testzip() is None
(out/'SHA256SUMS.txt').write_text(''.join(hashlib.file_digest(p.open('rb'),'sha256').hexdigest()+'  '+p.name+'\n' for p in [out/'Ytdlp-G.apk',source]))
# Historical stable packaging metadata is not presented as this build's receipt.
stage=M/'build/anchored-test3';stage.mkdir(parents=True,exist_ok=True)
for name in ['Ytdlp-G.apk','Ytdlp-G-source.zip','SHA256SUMS.txt','LICENSE.txt']:shutil.copy2(out/name,stage/name)
for name in ['README.md','TESTING.md','PROVENANCE.md']:shutil.copy2(H/name,stage/name)
print('Experimental APK, corresponding source, license and checksums packaged.')
