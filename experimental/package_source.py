#!/usr/bin/env python3
"""Package the exact prepared corresponding application source for distribution."""
import pathlib
import sys
import zipfile

HERE = pathlib.Path(__file__).resolve().parent
source = HERE / "build/upstream"
output = pathlib.Path(sys.argv[1])
output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(source.rglob("*")):
        relative = path.relative_to(source)
        if not path.is_file() or any(part in {"build", ".gradle", ".idea", ".kotlin", "__pycache__"} for part in relative.parts):
            continue
        if path.name in {"local.properties", "keystore.properties"} or path.suffix in {"jks", ".keystore"}:
            continue
        archive.write(path, "Ytdlp-G-Preview/" + str(relative))
    for name in ("README.md", "DEPENDENCIES.md", "upstream.json", "prepare.py"):
        archive.write(HERE / name, "build-instructions/" + name)
print(output)
