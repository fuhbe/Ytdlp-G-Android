#!/usr/bin/env python3
"""Prove the release-only ZIP bootstrap failure and its fix using real payloads.

R8 classfile mode reproduces the reflection failure on a host JVM. The separate
DEX guard checks the actual Android APK. Neither test simulates YouTube success.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

HERE = Path(__file__).resolve().parent

def main(apk):
    java_home = Path(os.environ["JAVA_HOME"])
    cache = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle"))) / "caches/modules-2/files-2.1"
    def jar(group, artifact, version):
        paths = list((cache / group / artifact / version).rglob("*.jar"))
        assert len(paths) == 1, (artifact, len(paths))
        return paths[0]
    compress = jar("org.apache.commons", "commons-compress", "1.12")
    io = jar("commons-io", "commons-io", "2.5")
    builder = jar("com.android.tools.build", "builder", "8.13.2")
    def run(args): return subprocess.run(list(map(str, args)), text=True, capture_output=True, timeout=120)
    report = {"type": "real runtime ZIPs + unshrunk/R8 JVM control", "checks": []}
    with tempfile.TemporaryDirectory(prefix="ytdlpg-zip-regression-") as directory:
        root = Path(directory)
        with zipfile.ZipFile(apk) as archive:
            archives = []
            for name in ["libpython.zip.so", "libffmpeg.zip.so"]:
                target = root / name
                target.write_bytes(archive.read("lib/arm64-v8a/" + name))
                archives.append(target)
        classes = root / "classes"; classes.mkdir()
        result = run([java_home / "bin/javac", "--release", "17", "-cp", os.pathsep.join(map(str, [compress, io])), "-d", classes, HERE / "regression/ZipBootstrapProbe.java"])
        assert result.returncode == 0, result.stderr
        harness = root / "probe.jar"
        result = run([java_home / "bin/jar", "--create", "--file", harness, "-C", classes, "."])
        assert result.returncode == 0, result.stderr
        control = run([java_home / "bin/java", "-cp", os.pathsep.join(map(str, [harness, compress, io])), "ZipBootstrapProbe", *archives])
        assert control.returncode == 0, control.stderr
        report["checks"].append({"unshrunk": "PASS", "output": control.stdout.strip()})
        zip_rule = "-keep class org.apache.commons.compress.archivers.zip.** { *; }"
        assert zip_rule in (HERE / "app/proguard-rules.pro").read_text()
        for name, rule in [("without-rule", ""), ("with-rule", zip_rule)]:
            config = root / f"{name}.pro"
            config.write_text("-keep class ZipBootstrapProbe { public static void main(java.lang.String[]); }\n-dontwarn **\n" + rule + "\n")
            shrunk = root / f"{name}.jar"
            result = run([java_home / "bin/java", "-Xmx1g", "-cp", builder, "com.android.tools.r8.R8", "--classfile", "--release", "--lib", java_home, "--pg-conf", config, "--output", shrunk, harness, compress, io])
            assert result.returncode == 0, result.stderr
            result = run([java_home / "bin/java", "-cp", shrunk, "ZipBootstrapProbe", *archives])
            if name == "without-rule":
                assert result.returncode != 0 and "ExceptionInInitializerError" in result.stderr and "not a concrete class" in result.stderr, result.stderr
                report["checks"].append({name: "EXPECTED FAILURE reproduced", "exception": result.stderr.strip()})
                print("PASS: old R8 configuration reproduces ExceptionInInitializerError before runtime extraction")
            else:
                assert result.returncode == 0, result.stderr
                report["checks"].append({name: "PASS", "output": result.stdout.strip()})
                print("PASS: restored upstream ZIP rule opens every Python/FFmpeg archive entry after R8")
    target = HERE / "build/zip-bootstrap-regression.json"
    target.write_text(json.dumps(report, indent=2))

if __name__ == "__main__":
    parser = argparse.ArgumentParser(); parser.add_argument("apk"); main(parser.parse_args().apk)
