#!/usr/bin/env python3
"""Inspect the exact preview APK, not a different local source tree."""
import pathlib
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
from check_apk import check


def verify(apk_path):
    check(apk_path)
    with zipfile.ZipFile(apk_path) as apk:
        names = apk.namelist()
        plugin = "assets/yt_dlp_plugins/glassmp4/yt_dlp_plugins/postprocessor/glassmp4.py"
        assert plugin in names, "MP4 conversion plugin missing from actual APK"
        assert apk.read(plugin) == (ROOT / "experimental/overlay/app/src/main" / plugin).read_bytes()
        assert "assets/licenses/YTDLnis-GPL-3.0.txt" in names
        assert "YTDLnis contributors" in apk.read("assets/licenses/YTDLnis-NOTICE.txt").decode()
        assert not any("__pycache__" in name for name in names)
        identity = "com.fuhbe.ytdlpg.experimental"
        manifest = apk.read("AndroidManifest.xml")
        assert identity.encode() in manifest or identity.encode("utf-16-le") in manifest
    print("Preview identity, exact MP4 plugin and upstream license verified in compiled APK")


if __name__ == "__main__":
    verify(sys.argv[1])
