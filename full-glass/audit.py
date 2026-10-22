#!/usr/bin/env python3
"""Fail if a visual fork drops upstream logic, settings, navigation or controls."""
import hashlib
import json
import tarfile
import xml.etree.ElementTree as ET
from pathlib import Path
from prepare import ARCHIVE, BUILD, PIN, SOURCE, ANDROID, APP, BRANDING_DRAWABLES

VISUAL = {
    f"{{{ANDROID}}}{key}" for key in (
        "background", "backgroundTint", "layout_marginHorizontal", "layout_marginBottom", "textColor")
} | {f"{{{APP}}}{key}" for key in (
    "cardCornerRadius", "cardElevation", "cardBackgroundColor", "strokeColor", "strokeWidth",
    "titleTextColor", "subtitleTextColor", "elevation", "itemIconTint", "itemTextColor",
    "itemActiveIndicatorStyle", "tabTextColor", "tabSelectedTextColor")}


def contract_xml(old, new, name):
    before, after = list(ET.fromstring(old).iter()), list(ET.fromstring(new).iter())
    assert len(before) == len(after), f"UI elements removed or added: {name}"
    for left, right in zip(before, after):
        assert left.tag == right.tag, f"Widget class changed: {name}"
        old_attrs = {k: v for k, v in left.attrib.items() if k not in VISUAL}
        new_attrs = {k: v for k, v in right.attrib.items() if k not in VISUAL}
        assert old_attrs == new_attrs, f"Nonvisual widget attributes changed: {name}: {old_attrs.keys() ^ new_attrs.keys()}"
        assert (left.text or "").strip() == (right.text or "").strip(), f"Widget text changed: {name}"


def audit():
    assert hashlib.sha256(ARCHIVE.read_bytes()).hexdigest() == PIN["archive_sha256"]
    counts = {"source_files": 0, "unchanged_logic_files": 0, "native_layouts": 0,
              "settings_xml": 0, "navigation_xml": 0, "menu_xml": 0, "original_files": 0}
    changed = []
    with tarfile.open(ARCHIVE) as tar:
        for member in tar.getmembers():
            if not member.isfile():
                continue
            relative = Path(*Path(member.name).parts[1:])
            path = SOURCE / relative
            assert path.is_file(), f"Upstream file missing: {relative}"
            counts["original_files"] += 1
            old, new = tar.extractfile(member).read(), path.read_bytes()
            name = str(relative)
            if name.startswith("app/src/main/java/"):
                counts["source_files"] += 1
                if name.endswith("/util/ThemeUtil.kt"):
                    assert new == old.replace(b"YTDL</span>nis", b"Ytdlp</span>-G")
                else:
                    assert new == old, f"Native application logic changed: {name}"
                    counts["unchanged_logic_files"] += 1
            elif name.startswith("app/src/main/res/layout") and name.endswith(".xml"):
                contract_xml(old, new, name)
                counts["native_layouts"] += 1
            elif name.startswith("app/src/main/res/xml/"):
                assert old == new, f"Settings or provider configuration changed: {name}"
                counts["settings_xml"] += 1
            elif name.startswith("app/src/main/res/navigation/"):
                assert old == new, f"Navigation graph changed: {name}"
                counts["navigation_xml"] += 1
            elif name.startswith("app/src/main/res/menu/"):
                assert old == new, f"Menu/action removed: {name}"
                counts["menu_xml"] += 1
            elif name == "app/src/main/AndroidManifest.xml":
                assert new == old.replace(b'android:label="YTDLnis"', b'android:label="Ytdlp-G"')
            elif relative.parent == Path("app/src/main/res/drawable") and relative.stem in BRANDING_DRAWABLES:
                assert ET.fromstring(new).tag == "vector", f"Branding must stay a drawable: {name}"
            elif old != new and name not in {"app/build.gradle", "settings.gradle"}:
                assert name.endswith(("/themes.xml", "/colors.xml")), f"Unexpected source change: {name}"
            if old != new:
                changed.append(name)
    assert counts["source_files"] == 264
    assert not (SOURCE / "app/src/main/java/com/fuhbe/ytdlpg").exists(), "Simplified launcher was incorrectly included"
    result = {"upstream_commit": PIN["commit"], "counts": counts, "changed_upstream_files": changed,
              "result": "PASS: native logic, settings, navigation, menus, widget identities and actions preserved"}
    (BUILD / "source-audit.json").write_text(json.dumps(result, indent=2))
    print(result["result"])
    print(json.dumps(counts))


if __name__ == "__main__":
    audit()
