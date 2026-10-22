#!/usr/bin/env python3
"""Build the ENTIRE upstream app. Change presentation and package identity only."""
import hashlib
import json
import re
import shutil
import subprocess
import sys
import tarfile
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
PIN = json.loads((HERE / "upstream.json").read_text())
BUILD = HERE / "build"
SOURCE = BUILD / "upstream"
ARCHIVE = BUILD / "ytdlnis-source.tar.gz"
ANDROID = "http://schemas.android.com/apk/res/android"
APP = "http://schemas.android.com/apk/res-auto"
TOOLS = "http://schemas.android.com/tools"
for prefix, uri in (("android", ANDROID), ("app", APP), ("tools", TOOLS)):
    ET.register_namespace(prefix, uri)


def attr(namespace, name):
    return f"{{{namespace}}}{name}"


def write_xml(path, root):
    ET.indent(root, space="    ")
    path.write_text('<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(root, encoding="unicode") + '\n')


def replace(path, old, new):
    data = path.read_text()
    if data.count(old) != 1:
        raise RuntimeError(f"Unexpected upstream content: {path}: {old[:80]}")
    path.write_text(data.replace(old, new))


def extract():
    BUILD.mkdir(exist_ok=True)
    if not ARCHIVE.exists():
        cached = ROOT / "experimental/build/ytdlnis-source.tar.gz"
        if cached.exists() and hashlib.sha256(cached.read_bytes()).hexdigest() == PIN["archive_sha256"]:
            shutil.copy2(cached, ARCHIVE)
        else:
            urllib.request.urlretrieve(f"https://codeload.github.com/{PIN['repository']}/tar.gz/{PIN['commit']}", ARCHIVE)
    if hashlib.sha256(ARCHIVE.read_bytes()).hexdigest() != PIN["archive_sha256"]:
        raise RuntimeError("Upstream archive checksum mismatch")
    SOURCE.mkdir(exist_ok=True)
    with tarfile.open(ARCHIVE) as tar:
        for member in tar.getmembers():
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
                data = tar.extractfile(member).read()
                if not target.exists() or target.read_bytes() != data:
                    target.write_bytes(data)


def theme():
    for folder, mode in (("values", "light"), ("values-night", "dark")):
        path = SOURCE / "app/src/main/res" / folder / "themes.xml"
        tree = ET.parse(path).getroot()
        base = tree.find("style[@name='BaseTheme']")
        items = {
            "android:windowBackground": "?attr/liquidWallpaper",
            "liquidWallpaper": "@drawable/liquid_wallpaper",
            "liquidCardFill": "@color/liquid_card_fill",
            "liquidGlassEdge": "@color/liquid_edge",
            "liquidBarBackground": "@drawable/liquid_bar",
            "liquidFieldFill": "@color/liquid_field_fill",
            "liquidSheen": "@drawable/liquid_sheen",
            "materialButtonStyle": "@style/Liquid.Button",
            "textInputStyle": "@style/Liquid.TextInput",
            "materialCardViewStyle": "@style/Liquid.Card",
            "android:textColorPrimary": "?attr/colorOnSurface",
            "android:textColorSecondary": "?attr/colorOnSurfaceVariant",
            "android:colorControlHighlight": "@color/liquid_ripple",
            "selectableItemBackground": "@drawable/liquid_row",
            "android:selectableItemBackground": "@drawable/liquid_row",
        }
        for name, value in items.items():
            item = base.find(f"item[@name='{name}']")
            if item is None:
                item = ET.SubElement(base, "item", name=name)
            item.text = value
        pure = tree.find("style[@name='Pure']")
        if pure is not None:
            solid = "@android:color/white" if mode == "light" else "@android:color/black"
            edge = "@android:color/black" if mode == "light" else "@android:color/white"
            for name, value in {"liquidWallpaper": solid, "liquidCardFill": solid,
                                "liquidBarBackground": solid, "liquidFieldFill": solid,
                                "liquidGlassEdge": edge, "liquidSheen": "@android:color/transparent",
                                "android:windowBackground": solid}.items():
                ET.SubElement(pure, "item", name=name).text = value
        write_xml(path, tree)
    # Keep every accent setting. Only shared surface roles and the default blue
    # accent receive a new palette; selection/disabled state behavior is native.
    palettes = {
        "light": {"background": "#E8F0FA", "surface": "#F3F7FD", "surfaceVariant": "#DFEAF7",
                  "onSurface": "#152B45", "onBackground": "#152B45", "onSurfaceVariant": "#455D78",
                  "outline": "#748AA3", "primary": "#1768D4", "onPrimary": "#FFFFFF",
                  "primaryContainer": "#D6E8FF", "onPrimaryContainer": "#06366E"},
        "dark": {"background": "#0C172A", "surface": "#182A43", "surfaceVariant": "#273E59",
                 "onSurface": "#F2F7FF", "onBackground": "#F2F7FF", "onSurfaceVariant": "#BFCEE0",
                 "outline": "#8A9DB6", "primary": "#A3CCFF", "onPrimary": "#053362",
                 "primaryContainer": "#154C85", "onPrimaryContainer": "#E0EEFF"},
    }
    def recolor(match):
        prefix, mode, role = match[2], match[3], match[4]
        value = palettes[mode].get(role)
        if role in {"primary", "onPrimary", "primaryContainer", "onPrimaryContainer"} and prefix != "blue":
            value = None
        return match[1] + (value or match[5]) + match[6]
    for path in (SOURCE / "app/src/main/res").glob("values*/colors.xml"):
        data = path.read_text()
        path.write_text(re.sub(r'(<color name="([a-z_]+?)(?:_md)?_theme_(light|dark)_([A-Za-z]+)">)([^<]+)(</color>)', recolor, data))


def layouts():
    for path in (SOURCE / "app/src/main/res").glob("layout*/*.xml"):
        tree = ET.parse(path).getroot()
        changed = False
        if path.name.startswith("activity_") and tree.tag not in {"merge", "layout"}:
            tree.set(attr(ANDROID, "background"), "?attr/liquidWallpaper")
            changed = True
        for node in tree.iter():
            tag = node.tag
            if tag == "com.google.android.material.card.MaterialCardView":
                node.set(attr(APP, "cardCornerRadius"), "20dp")
                node.set(attr(APP, "cardElevation"), "0dp")
                node.set(attr(APP, "cardBackgroundColor"), "@color/liquid_card_tint")
                node.set(attr(ANDROID, "backgroundTint"), "@color/liquid_card_tint")
                node.set(attr(APP, "strokeColor"), "@color/liquid_card_edge")
                node.set(attr(APP, "strokeWidth"), "1dp")
                # A background on the existing content adds light without replacing
                # the card's native checked icon, ripple or touch handling.
                if len(node) and node[0].tag.endswith(("Layout", "ScrollView")):
                    node[0].set(attr(ANDROID, "background"), "?attr/liquidSheen")
                changed = True
            elif tag.endswith((".MaterialToolbar", ".Toolbar")):
                node.set(attr(ANDROID, "background"), "?attr/liquidBarBackground")
                node.set(attr(APP, "titleTextColor"), "?attr/colorOnSurface")
                node.set(attr(APP, "subtitleTextColor"), "?attr/colorOnSurfaceVariant")
                changed = True
            elif tag.endswith(".AppBarLayout"):
                node.set(attr(ANDROID, "background"), "@android:color/transparent")
                node.set(attr(APP, "elevation"), "0dp")
                changed = True
            elif tag.endswith((".BottomNavigationView", ".NavigationRailView")):
                node.set(attr(ANDROID, "background"), "?attr/liquidBarBackground")
                node.set(attr(APP, "itemIconTint"), "@color/liquid_navigation_text")
                node.set(attr(APP, "itemTextColor"), "@color/liquid_navigation_text")
                node.set(attr(APP, "itemActiveIndicatorStyle"), "@style/Liquid.NavigationIndicator")
                if tag.endswith(".BottomNavigationView"):
                    node.set(attr(ANDROID, "layout_marginHorizontal"), "12dp")
                    node.set(attr(ANDROID, "layout_marginBottom"), "10dp")
                changed = True
            elif tag.endswith(".TabLayout"):
                node.set(attr(ANDROID, "background"), "?attr/liquidBarBackground")
                node.set(attr(APP, "tabTextColor"), "?attr/colorOnSurfaceVariant")
                node.set(attr(APP, "tabSelectedTextColor"), "?attr/colorPrimary")
                changed = True
            # Several native badges are plain TextViews with an explicit tint.
            # Pair their foreground with that role instead of inheriting body text.
            if tag in {"TextView", "androidx.appcompat.widget.AppCompatTextView"}:
                tint = node.get(attr(ANDROID, "backgroundTint"), "")
                pair = {
                    "?attr/colorPrimary": "?attr/colorOnPrimary",
                    "?attr/colorPrimaryContainer": "?attr/colorOnPrimaryContainer",
                    "?attr/colorSecondary": "?attr/colorOnSecondary",
                    "?attr/colorSecondaryContainer": "?attr/colorOnSecondaryContainer",
                    "?attr/colorTertiary": "?attr/colorOnTertiary",
                    "?attr/colorTertiaryContainer": "?attr/colorOnTertiaryContainer",
                    "?attr/colorPrimaryInverse": "?attr/colorOnPrimaryContainer",
                }.get(tint)
                if pair:
                    node.set(attr(ANDROID, "textColor"), pair)
                    changed = True
        if changed:
            write_xml(path, tree)


def prepare():
    extract()
    replace(SOURCE / "settings.gradle", "include ':common', ':app', ':library', ':ffmpeg'", "include ':app'")
    gradle = SOURCE / "app/build.gradle"
    replace(gradle, 'applicationId "com.deniscerri.ytdl"', f'applicationId "{PIN["application_id"]}"')
    # Keep upstream's numeric version: its unmodified updater parses it as an Int.
    # The Glass edition number is a distribution label, not a native app version.
    # A single universal package keeps every ABI supplied by upstream.
    replace(gradle, 'enable true', 'enable false')
    with gradle.open("a") as stream:
        stream.write('''
// Host-side native widget verification; no production dependency is added.
dependencies { testImplementation 'org.robolectric:robolectric:4.16' }
android.testOptions.unitTests.includeAndroidResources = true
tasks.withType(Test).configureEach {
    maxHeapSize = '2g'
    systemProperty 'robolectric.dependency.repo.url', 'https://repo.maven.apache.org/maven2'
    ['http.proxyHost', 'http.proxyPort', 'https.proxyHost', 'https.proxyPort', 'javax.net.ssl.trustStore'].each { key ->
        if (System.getProperty(key)) systemProperty key, System.getProperty(key)
    }
}
''')
    manifest = SOURCE / "app/src/main/AndroidManifest.xml"
    replace(manifest, 'android:label="YTDLnis"', 'android:label="Ytdlp-G Glass"')
    theme_util = SOURCE / "app/src/main/java/com/deniscerri/ytdl/util/ThemeUtil.kt"
    replace(theme_util, "YTDL</span>nis", "Ytdlp</span>-G")
    shutil.copytree(HERE / "overlay", SOURCE, dirs_exist_ok=True,
                    ignore=shutil.ignore_patterns("__pycache__", "*.pyc"))
    theme()
    layouts()
    # Upstream's pipeline supplies this ignored resource separately as well.
    subprocess.run([sys.executable, str(ROOT / "scripts/prepare_runtime.py"), str(BUILD / "runtime")], check=True)
    raw = SOURCE / "app/src/main/res/raw"
    raw.mkdir(exist_ok=True)
    shutil.copy2(BUILD / "runtime/raw/ytdlp", raw / "ytdlp")
    licenses = SOURCE / "app/src/main/assets/licenses"
    licenses.mkdir(exist_ok=True)
    shutil.copytree(ROOT / "app/src/main/assets/licenses", licenses, dirs_exist_ok=True)
    shutil.copy2(SOURCE / "LICENSE", licenses / "YTDLnis-GPL-3.0.txt")
    (licenses / "YTDLnis-NOTICE.txt").write_text(
        f"Ytdlp-G Glass is a modified presentation of the complete YTDLnis app.\n"
        f"Copyright Deniscerri and YTDLnis contributors. License: GPL-3.0.\n"
        f"Original source: https://github.com/{PIN['repository']}/tree/{PIN['commit']}\n"
        "Changes: presentation resources and application name/package identity.\n"
        "Corresponding source: https://github.com/fuhbe/Ytdlp-G-Android/tree/design/full-ytdlnis-glass\n")
    # No secrets are needed, but the upstream secrets plugin requires this file.
    local = SOURCE / "local.properties"
    if not local.exists():
        local.write_text("")
    print(f"Prepared COMPLETE YTDLnis + Liquid Glass at {SOURCE}")


if __name__ == "__main__":
    prepare()
