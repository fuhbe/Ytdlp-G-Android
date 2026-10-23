#!/usr/bin/env python3
"""Extend the corresponding-source package with the exact stable build instructions."""
from pathlib import Path
import zipfile,hashlib
R=Path(__file__).resolve().parents[3];M=R/'minimal';out=M/'build/distribution';source=out/'Ytdlp-G-source.zip'
paths=[R/n for n in ['README.md','CHANGELOG.md','LICENSE','NOTICE.md','.github/workflows/stable-066.yml']]
paths += [p for p in (R/'docs/releases/0.6.6').glob('*') if p.is_file() and p.suffix in ['.py','.md','.json','.txt']]
with zipfile.ZipFile(source,'a',zipfile.ZIP_DEFLATED) as z:
 existing=set(z.namelist())
 for p in sorted(paths):
  name=p.relative_to(R).as_posix();assert name not in existing,name
  z.write(p,name)
with zipfile.ZipFile(source) as z:assert z.testzip() is None
(out/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in [out/'Ytdlp-G.apk',source]))
print('Corresponding source includes exact stable build scripts, verification and license documentation.')
