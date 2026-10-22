#!/usr/bin/env python3
"""Inspect the APK actually delivered, including compressed Android runtimes."""
import hashlib
import io
import os
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
from prepare_runtime import SHA256, VERSION

def verify(path):
    path = Path(path)
    assert path.name == "Ytdlp-G.apk", "Final APK filename must be exact"
    sdk = Path(os.environ["ANDROID_HOME"])
    tools = sdk / "build-tools/36.0.0"
    subprocess.run([str(tools / "apksigner"), "verify", str(path)], check=True)
    badging = subprocess.check_output([str(tools / "aapt2"), "dump", "badging", str(path)], text=True)
    assert "application-label:'Ytdlp-G'" in badging
    assert "name='com.fuhbe.ytdlpg.minimal'" in badging
    assert "android.permission.MANAGE_EXTERNAL_STORAGE" not in badging
    with zipfile.ZipFile(path) as apk:
        assert apk.testzip() is None
        names = apk.namelist()
        candidates = [n for n in names if n.startswith("res/") and apk.getinfo(n).file_size > 1_000_000]
        engine = next((apk.read(n) for n in candidates if hashlib.sha256(apk.read(n)).hexdigest() == SHA256), None)
        assert engine is not None, "Bundled engine checksum mismatch"
        with zipfile.ZipFile(io.BytesIO(engine)) as z:
            assert "yt_dlp_ejs/yt/solver/core.min.js" in z.namelist()
        abis = sorted({n.split("/")[1] for n in names if n.startswith("lib/")})
        assert abis
        for abi in abis:
            for library in ["libpython.so", "libpython.zip.so", "libffmpeg.so", "libffmpeg.zip.so", "libqjs.so"]:
                assert len(apk.read(f"lib/{abi}/{library}")) > 0
        for license in ["YTDLnis-GPL-3.0.txt", "YTDLnis-NOTICE.txt", "Python.txt", "quickjs.txt", "yt-dlp.txt", "GPL-3.0.txt"]:
            assert len(apk.read("assets/licenses/" + license)) > 0
        assert any(n.endswith("glassmp4.py") for n in names)
    print(f"PASS: signed Ytdlp-G APK; own app ID; yt-dlp {VERSION}; EJS, Python, FFmpeg, QuickJS; ABIs {abis}")
    print(f"APK: {path.resolve()} ({path.stat().st_size:,} bytes)")

if __name__ == "__main__":
    verify(sys.argv[1])
