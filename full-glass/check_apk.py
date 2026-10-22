#!/usr/bin/env python3
import sys
import zipfile
from pathlib import Path
from prepare import PIN, ROOT

sys.path.insert(0, str(ROOT / "scripts"))
from check_apk import check as check_runtime


def check(path):
    check_runtime(path)
    with zipfile.ZipFile(path) as apk:
        names = apk.namelist()
        manifest = apk.read("AndroidManifest.xml")
        identity = PIN["application_id"]
        assert identity.encode() in manifest or identity.encode("utf-16-le") in manifest
        assert "assets/licenses/YTDLnis-GPL-3.0.txt" in names
        assert "assets/licenses/YTDLnis-NOTICE.txt" in names
        # The legacy quality renderer/bridge must not accidentally become the app.
        dex = b"".join(apk.read(name) for name in names if name.endswith(".dex"))
        assert b"Lcom/fuhbe/ytdlpg/MainActivity;" not in dex
        for name in (b"Lcom/deniscerri/ytdl/MainActivity;", b"Lcom/deniscerri/ytdl/ui/adapter/FormatAdapter;",
                     b"Lcom/deniscerri/ytdl/ui/more/settings/SettingsActivity;", b"Lcom/deniscerri/ytdl/work/DownloadWorker;"):
            assert name in dex, f"Native feature missing: {name}"
        for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64"):
            for library in ("libpython.so", "libpython.zip.so", "libffmpeg.so", "libffmpeg.zip.so", "libqjs.so"):
                assert f"lib/{abi}/{library}" in names
            assert f"lib/{abi}/libbash.zip.so" in names
    print("Complete native launcher, format renderer, settings, worker and all four ABIs verified")


if __name__ == "__main__":
    check(sys.argv[1])
