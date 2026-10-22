#!/usr/bin/env python3
"""Rename without modifying APK bytes and deliver exact corresponding source."""
import hashlib
from pathlib import Path
import shutil
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
output = HERE / "build/distribution"
output.mkdir(parents=True, exist_ok=True)
candidates = list((HERE / "app/build/outputs/apk/github/release").glob("*.apk"))
assert len(candidates) == 1
apk = output / "Ytdlp-G.apk"
shutil.copy2(candidates[0], apk)
assert hashlib.sha256(apk.read_bytes()).digest() == hashlib.sha256(candidates[0].read_bytes()).digest()
source = output / "Ytdlp-G-source.zip"
with zipfile.ZipFile(source, "w", zipfile.ZIP_DEFLATED) as z:
    for path in sorted(HERE.rglob("*")):
        relative = path.relative_to(HERE)
        if not path.is_file() or any(part in {"build", ".gradle", ".kotlin", "__pycache__"} for part in relative.parts) or path.name == "local.properties":
            continue
        z.write(path, "minimal/" + str(relative))
    # These are the exact generated native core, licenses, plugin and engine
    # used in this binary, in addition to the reproducible preparation script.
    for subdir in ["upstream", "runtime"]:
        for path in (HERE / "app/build/generated" / subdir).rglob("*"):
            if path.is_file():
                z.write(path, "minimal/app/build/generated/" + str(path.relative_to(HERE / "app/build/generated")))
    for name in ["scripts/prepare_runtime.py"]:
        z.write(ROOT / name, name)
    for path in (ROOT / "app/src/main/assets/licenses").glob("*"):
        if path.is_file():
            z.write(path, str(path.relative_to(ROOT)))
    z.write(HERE / "build/upstream.tar.gz", "minimal/build/upstream.tar.gz")
    plugin = ROOT / "experimental/overlay/app/src/main/assets/yt_dlp_plugins"
    for path in plugin.rglob("*.py"):
        z.write(path, str(path.relative_to(ROOT)))
    z.write(HERE / "build/source-manifest.json", "minimal/source-manifest.json")
for name in ["README.md", "VERIFICATION.md"]:
    shutil.copy2(HERE / name, output / name)
shutil.copy2(HERE / "app/build/generated/upstream/assets/licenses/YTDLnis-GPL-3.0.txt", output / "LICENSE.txt")
if (HERE / "build/host-smoke.json").exists():
    shutil.copy2(HERE / "build/host-smoke.json", output / "host-smoke.json")
(output / "SHA256SUMS.txt").write_text("".join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in [apk, source]))
print(apk)
