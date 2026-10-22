#!/usr/bin/env python3
"""Publish the exact complete modified source, license and build instructions."""
import hashlib
import json
import shutil
import zipfile
from pathlib import Path
from prepare import HERE, PIN, SOURCE, BUILD

output = BUILD / "distribution"
output.mkdir(exist_ok=True)
version = PIN["version"]
apks = list((SOURCE / "app/build/outputs/apk/izzy/debug").glob("*.apk"))
assert len(apks) == 1
shutil.copy2(apks[0], output / f"Ytdlp-G-Glass-v{version}.apk")
source_zip = output / f"Ytdlp-G-Glass-v{version}-source.zip"
with zipfile.ZipFile(source_zip, "w", zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(SOURCE.rglob("*")):
        relative = path.relative_to(SOURCE)
        if not path.is_file() or any(p in {"build", ".gradle", ".kotlin", ".idea", "__pycache__"} for p in relative.parts):
            continue
        if path.name in {"local.properties", "keystore.properties"} or path.suffix in {".jks", ".keystore"}:
            continue
        archive.write(path, "Ytdlp-G-Glass/" + str(relative))
    for name in ("README.md", "upstream.json", "prepare.py", "audit.py"):
        archive.write(HERE / name, "build-instructions/" + name)
shutil.copy2(HERE / "README.md", output / "README.md")
shutil.copy2(SOURCE / "LICENSE", output / "LICENSE.txt")
shutil.copy2(BUILD / "source-audit.json", output / "source-audit.json")
shutil.copy2(SOURCE / "app/src/main/assets/licenses/YTDLnis-NOTICE.txt", output / "NOTICE.txt")
(output / "SHA256SUMS.txt").write_text("".join(
    f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in sorted(output.iterdir())
    if p.suffix in {".apk", ".zip"}
))
print(output)
