#!/usr/bin/env python3
"""Materialize the pinned GPL upstream and apply the reviewed Glass overlay.

The stable application is never modified. Repeated runs are deterministic.
"""
import hashlib
import json
import re
import shutil
import subprocess
import sys
import tarfile
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
PIN = json.loads((HERE / "upstream.json").read_text())
BUILD = HERE / "build"
SOURCE = BUILD / "upstream"


def replace(path, old, new):
    text = path.read_text()
    if text.count(old) != 1:
        raise RuntimeError(f"Upstream changed: {path.relative_to(SOURCE)}: {old[:70]!r}")
    path.write_text(text.replace(old, new))


def prepare():
    BUILD.mkdir(exist_ok=True)
    archive = BUILD / "ytdlnis-source.tar.gz"
    if not archive.exists():
        urllib.request.urlretrieve(
            f"https://codeload.github.com/{PIN['repository']}/tar.gz/{PIN['commit']}", archive
        )
    if hashlib.sha256(archive.read_bytes()).hexdigest() != PIN["archive_sha256"]:
        raise RuntimeError("Upstream source checksum mismatch")
    # Re-extract tracked upstream files without discarding Gradle's incremental outputs.
    SOURCE.mkdir(exist_ok=True)
    with tarfile.open(archive) as tar:
        members = tar.getmembers()
        for member in members:
            relative = Path(*Path(member.name).parts[1:])
            if not relative.parts:
                continue
            if relative.is_absolute() or ".." in relative.parts or member.issym() or member.islnk():
                raise RuntimeError("Unsafe source archive entry")
            target = SOURCE / relative
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            elif member.isfile():
                target.parent.mkdir(parents=True, exist_ok=True)
                content = tar.extractfile(member).read()
                if not target.exists() or target.read_bytes() != content:
                    target.write_bytes(content)
    replace(SOURCE / "settings.gradle", "include ':common', ':app', ':library', ':ffmpeg'", "include ':app'")
    gradle = SOURCE / "app/build.gradle"
    replace(gradle, 'applicationId "com.deniscerri.ytdl"', 'applicationId "com.fuhbe.ytdlpg.experimental"')
    replace(gradle, 'versionName "${versionMajor}.${versionMinor}.${versionPatch}${versionExt}"', f'versionName "{PIN["version"]}"')
    replace(gradle, 'targetSdk 36', 'targetSdk 35')
    replace(gradle, 'minSdk 24', 'minSdk 26')
    # One universal APK with the two 64-bit ABIs. Native runtimes stay bundled.
    replace(gradle, "include 'x86', 'x86_64', 'armeabi-v7a', 'arm64-v8a'", "include 'x86_64', 'arm64-v8a'")
    replace(gradle, "enable true", "enable false")
    with gradle.open("a") as f:
        f.write('''
dependencies {
    implementation 'androidx.activity:activity-compose:1.12.2'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0'
    implementation 'androidx.compose.material:material-icons-core:1.7.8'
    implementation 'io.coil-kt:coil-compose:2.7.0'
}
android.defaultConfig.ndk.abiFilters.addAll(['arm64-v8a', 'x86_64'])
android.defaultConfig.resourceConfigurations.addAll(['en', 'pt', 'pt-rBR', 'es'])
''')
    manifest = SOURCE / "app/src/main/AndroidManifest.xml"
    replace(manifest, 'android:label="YTDLnis"', 'android:label="Ytdlp-G Preview"')
    replace(manifest, '<activity\n            android:name=".MainActivity"', '''<activity android:name="com.fuhbe.ytdlpg.MainActivity"
            android:exported="true" android:windowSoftInputMode="adjustResize"
            android:theme="@style/GlassActivityTheme">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
        </activity>
        <activity
            android:name=".MainActivity"''')
    manifest.write_text(re.sub(r'\s*<activity-alias\b.*?</activity-alias>', '', manifest.read_text(), flags=re.S))
    replace(manifest, 'android:icon="@mipmap/ic_launcher"', 'android:icon="@drawable/g_ic_launcher"')
    replace(manifest, 'android:roundIcon="@mipmap/ic_launcher_round"', 'android:roundIcon="@drawable/g_ic_launcher"')
    share = r'(<activity\s+android:name="\.receiver.ShareActivity".*?</activity>)'
    manifest.write_text(re.sub(share, lambda m: re.sub(r'<intent-filter\b.*?</intent-filter>', '', m[0], flags=re.S), manifest.read_text(), flags=re.S))
    notification = SOURCE / "app/src/main/java/com/deniscerri/ytdl/util/NotificationUtil.kt"
    replace(notification, 'import com.deniscerri.ytdl.MainActivity', 'import com.fuhbe.ytdlpg.MainActivity')
    application = SOURCE / "app/src/main/java/com/deniscerri/ytdl/App.kt"
    replace(application, '                createNotificationChannels()', '''                // Recover only at process startup, before the runtime can accept new jobs.
                val recoveryDb = DBManager.getInstance(this@App)
                val interrupted = recoveryDb.downloadDao.getActiveDownloadsList()
                if (interrupted.isNotEmpty()) recoveryDb.downloadDao.setStatusMultiple(
                    interrupted.map { it.id }, "Queued")
                createNotificationChannels()''')
    # Version changes must update already extracted runtimes on upgrade too.
    runtime = SOURCE / "app/src/main/java/com/deniscerri/ytdl/core/RuntimeManager.kt"
    replace(runtime, 'if (!ytdlpBinary.exists()) {', '''val bundledVersion = "2026.10.21"
        val marker = File(ytdlpDir, "glass-runtime-version")
        if (!ytdlpBinary.exists() || marker.readTextOrNull() != bundledVersion) {''')
    replace(runtime, 'FileUtils.copyInputStreamToFile(inputStream, ytdlpBinary)', '''FileUtils.copyInputStreamToFile(inputStream, ytdlpBinary)
                marker.writeText(bundledVersion)''')
    with runtime.open("a") as f:
        f.write('\nprivate fun File.readTextOrNull(): String? = if (exists()) readText() else null\n')
    history = SOURCE / "app/src/main/java/com/deniscerri/ytdl/database/dao/HistoryDao.kt"
    replace(history, 'interface HistoryDao {', '''interface HistoryDao {
    @Query("SELECT * FROM history WHERE downloadId IN (:ids)")
    fun getGlassBatchHistory(ids: List<Long>): List<HistoryItem>
''')
    # NewPipe's progressive streams contain audio; upstream omits this field.
    newpipe = SOURCE / "app/src/main/java/com/deniscerri/ytdl/util/extractors/newpipe/NewPipeUtil.kt"
    replace(newpipe, 'val it = stream.videoStreams[f]\n', 'val it = stream.videoStreams[f]\n')
    text = newpipe.read_text()
    if text.count('container = it.format!!.name,') != 3:
        raise RuntimeError("NewPipe format adapter changed")
    text = text.replace('container = it.format!!.name,', 'container = it.format!!.suffix,')
    start = text.index('val it = stream.videoStreams[f]')
    before, after = text[:start], text[start:]
    after = after.replace('vcodec = it.codec,', 'vcodec = it.codec,\n                            acodec = "unknown",', 1)
    newpipe.write_text(before + after)
    # The complete upstream source and licenses remain in the prepared project.
    overlay = HERE / "overlay"
    if overlay.exists():
        shutil.copytree(overlay, SOURCE, dirs_exist_ok=True, ignore=shutil.ignore_patterns("__pycache__", "*.pyc"))
    for cache in (SOURCE / "app/src/main/assets").rglob("__pycache__"):
        shutil.rmtree(cache)
    # Reuse the approved stateless Glass layout; all actions use the new bridge.
    java = SOURCE / "app/src/main/java/com/fuhbe/ytdlpg"
    java.mkdir(parents=True, exist_ok=True)
    for name in ("MainActivity.kt", "MediaModels.kt", "ArchiveWriter.kt"):
        text = (ROOT / "app/src/main/java/com/fuhbe/ytdlpg" / name).read_text()
        if name == "MainActivity.kt":
            text = text.replace("package com.fuhbe.ytdlpg", "package com.fuhbe.ytdlpg\nimport com.deniscerri.ytdl.R")
            text = text.replace("R.string.", "R.string.g_").replace("R.drawable.", "R.drawable.g_")
            text = text.replace('context.startService(Intent(context, DownloadService::class.java).setAction("cancel"))', 'model.cancelDownload()')
            text = text.replace('0.3.0-beta.1\\nyt-dlp · Python · FFmpeg · QuickJS',
                                '0.4.0-preview.1\\nBased on YTDLnis (GPL-3.0)\\nyt-dlp · Python · FFmpeg · QuickJS')
        (java / name).write_text(text)
    for folder in ("values", "values-pt-rBR", "values-es"):
        text = (ROOT / "app/src/main/res" / folder / "strings.xml").read_text()
        text = text.replace('name="', 'name="g_')
        (SOURCE / "app/src/main/res" / folder / "glass_strings.xml").write_text(text)
    for file in (ROOT / "app/src/main/res/drawable").glob("*.xml"):
        shutil.copy2(file, SOURCE / "app/src/main/res/drawable" / ("g_" + file.name))
    shutil.copytree(ROOT / "app/src/main/assets/licenses", SOURCE / "app/src/main/assets/licenses", dirs_exist_ok=True)
    (SOURCE / "app/src/main/assets/licenses/YTDLnis-GPL-3.0.txt").write_bytes((SOURCE / "LICENSE").read_bytes())
    (SOURCE / "app/src/main/assets/licenses/YTDLnis-NOTICE.txt").write_text(
        f"Ytdlp-G Preview is a modified version of YTDLnis.\n"
        f"Copyright (C) Deniscerri and YTDLnis contributors.\n"
        f"Upstream: https://github.com/{PIN['repository']}/tree/{PIN['commit']}\n"
        "License: GNU GPL version 3. See YTDLnis-GPL-3.0.txt.\n"
        "Changes: Glass launcher, queue/export bridge, MP4 postprocessor, pinned runtime.\n"
        "Corresponding source: https://github.com/fuhbe/Ytdlp-G-Android/tree/experimental/ytdlnis-core\n")
    tests = SOURCE / "app/src/test/java/com/fuhbe/ytdlpg"
    tests.mkdir(parents=True, exist_ok=True)
    for name in ("MediaPolicyTest.kt", "ArchiveWriterTest.kt"):
        shutil.copy2(ROOT / "app/src/test/java/com/fuhbe/ytdlpg" / name, tests / name)
    # The upstream secrets plugin expects this file even with no API keys.
    local = SOURCE / "local.properties"
    if not local.exists():
        local.write_text("")
    subprocess.run([sys.executable, str(ROOT / "scripts/prepare_runtime.py"),
                    str(BUILD / "runtime")], check=True)
    (SOURCE / "app/src/main/res/raw").mkdir(exist_ok=True)
    shutil.copy2(BUILD / "runtime/raw/ytdlp", SOURCE / "app/src/main/res/raw/ytdlp")
    print(f"Prepared {SOURCE} from {PIN['repository']}@{PIN['commit']}")


if __name__ == "__main__":
    prepare()
