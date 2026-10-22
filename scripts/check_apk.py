"""Validate the exact runtime and native dependencies in the APK being distributed."""
import hashlib
import io
import pathlib
import sys
import zipfile
from prepare_runtime import SHA256, VERSION


def check(path):
    with zipfile.ZipFile(path) as apk:
        assert apk.testzip() is None, "Invalid APK archive"
        runtime = apk.read("res/raw/ytdlp")
        assert hashlib.sha256(runtime).hexdigest() == SHA256, "Old or unverified yt-dlp in APK"
        with zipfile.ZipFile(io.BytesIO(runtime)) as engine:
            assert VERSION in engine.read("yt_dlp/version.py").decode()
            assert "yt_dlp_ejs/yt/solver/core.min.js" in engine.namelist(), "Missing YouTube JS solver"
        for abi in ("arm64-v8a", "x86_64"):
            for library in ("libpython.so", "libpython.zip.so", "libffmpeg.so", "libffmpeg.zip.so", "libqjs.so"):
                data = apk.read(f"lib/{abi}/{library}")
                assert len(data) > 0, f"Missing {abi}/{library}"
    print(f"Verified yt-dlp {VERSION}, bundled JS solver, Python, FFmpeg and QuickJS in {pathlib.Path(path).name}")


if __name__ == "__main__":
    check(sys.argv[1])
