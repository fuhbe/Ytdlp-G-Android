#!/usr/bin/env python3
"""Reuse the pinned YTDLnis runtime, not its terminal or advanced UI."""
import hashlib
import json
import shutil
import subprocess
import sys
import tarfile
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
PIN = json.loads((HERE / "upstream.json").read_text())
GENERATED = HERE / "app/build/generated/upstream"
CORE = "app/src/main/java/com/deniscerri/ytdl/"

def main():
    archive = HERE / "build/upstream.tar.gz"
    archive.parent.mkdir(exist_ok=True)
    if not archive.exists():
        cached = ROOT / "full-glass/build/ytdlnis-source.tar.gz"
        if cached.exists():
            shutil.copy2(cached, archive)
        else:
            urllib.request.urlretrieve(f"https://codeload.github.com/{PIN['repository']}/tar.gz/{PIN['commit']}", archive)
    assert hashlib.sha256(archive.read_bytes()).hexdigest() == PIN["archive_sha256"]
    java = GENERATED / "java"
    sources = []
    with tarfile.open(archive) as tar:
        for member in tar.getmembers():
            name = "/".join(Path(member.name).parts[1:])
            if not member.isfile():
                continue
            if name.startswith(CORE + "core/") or name in [CORE + "database/models/PackageItem.kt", CORE + "database/models/GithubRelease.kt"]:
                data = tar.extractfile(member).read()
                sources.append({"path": name, "original_sha256": hashlib.sha256(data).hexdigest()})
                # Only references to application-local resources are redirected.
                data = data.replace(b"import com.deniscerri.ytdl.App\n", b"")
                data = data.replace(b"import com.deniscerri.ytdl.R", b"import com.fuhbe.ytdlpg.simple.R")
                data = data.replace(b"import com.deniscerri.ytdl.BuildConfig", b"import com.fuhbe.ytdlpg.simple.BuildConfig")
                target = java / name.removeprefix("app/src/main/java/")
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
            elif name == "LICENSE":
                target = GENERATED / "assets/licenses/YTDLnis-GPL-3.0.txt"
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(tar.extractfile(member).read())
    # File helpers implement app-private storage. No broad-storage permission,
    # terminal screen or advanced project dependency is imported.
    helper = java / "com/deniscerri/ytdl/util/FileUtil.kt"
    helper.parent.mkdir(parents=True, exist_ok=True)
    helper.write_text('''package com.deniscerri.ytdl.util
import android.content.Context
import com.fuhbe.ytdlpg.simple.SimpleApp
import java.io.File
object FileUtil {
 fun getBundledYTDLPPluginsPath(context: Context) = File(context.filesDir, "yt_dlp_plugins").absolutePath
 fun getDefaultTerminalPath() = SimpleApp.instance.filesDir.absolutePath
 fun getDefaultApksPath() = File(SimpleApp.instance.cacheDir, "updates").absolutePath
}
''')
    plugins = ROOT / "experimental/overlay/app/src/main/assets/yt_dlp_plugins"
    shutil.copytree(plugins, GENERATED / "assets/yt_dlp_plugins", dirs_exist_ok=True,
                    ignore=shutil.ignore_patterns("__pycache__", "*.pyc"))
    shutil.copytree(ROOT / "app/src/main/assets/licenses", GENERATED / "assets/licenses", dirs_exist_ok=True)
    (GENERATED / "assets/licenses/YTDLnis-NOTICE.txt").write_text(
        f"Ytdlp-G, a GPL-3.0 derivative using YTDLnis runtime components.\n"
        f"Copyright Deniscerri and YTDLnis contributors.\n"
        f"Source: https://github.com/{PIN['repository']}/tree/{PIN['commit']}\n"
        "Changes: separate minimal UI, persistent queue, SAF export and MP4 postprocessing.\n")
    runtime = HERE / "app/build/generated/runtime/res"
    subprocess.run([sys.executable, str(ROOT / "scripts/prepare_runtime.py"), str(runtime)], check=True)
    (HERE / "build/source-manifest.json").write_text(json.dumps({"commit": PIN["commit"], "sources": sources}, indent=2))
    assert len(sources) >= 17
    print(f"Prepared {len(sources)} pinned YTDLnis runtime source files; separate minimal application")

if __name__ == "__main__":
    main()
