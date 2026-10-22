#!/usr/bin/env python3
"""Check the actual release DEX, not unshrunk unit-test classes.

Commons Compress 1.12 instantiates these ZIP extra fields through Class.newInstance
in ExtraFieldUtils.<clinit>. Removing their constructors crashes runtime extraction.
"""
import argparse
import json
import re
import struct
import zipfile
from pathlib import Path

REGISTERED = [
    "AsiExtraField", "X5455_ExtendedTimestamp", "X7875_NewUnix", "JarMarker",
    "UnicodePathExtraField", "UnicodeCommentExtraField", "Zip64ExtendedInformationExtraField",
    "X000A_NTFS", "X0014_X509Certificates", "X0015_CertificateIdForFile",
    "X0016_CertificateIdForCentralDirectory", "X0017_StrongEncryptionHeader",
    "X0019_EncryptionRecipientCertificateList",
]
PACKAGE = "org.apache.commons.compress.archivers.zip."

def classes(data):
    assert data[:4] == b"dex\x0a", "Unsupported DEX format"
    def u32(offset): return struct.unpack_from("<I", data, offset)[0]
    def leb(offset):
        value = shift = 0
        while True:
            byte = data[offset]; offset += 1
            value |= (byte & 127) << shift
            if byte < 128: return value, offset
            shift += 7
            assert shift <= 35
    def string(index):
        start = u32(u32(0x3c) + index * 4)
        _, start = leb(start)
        return data[start:data.index(b"\0", start)].decode("utf-8", "replace")
    def typename(index): return string(u32(u32(0x44) + index * 4))
    method_offset, proto_offset = u32(0x5c), u32(0x4c)
    for i in range(u32(0x60)):
        pos = u32(0x64) + 32 * i
        name, flags, offset = typename(u32(pos)), u32(pos + 4), u32(pos + 24)
        constructors = []
        if offset:
            counts = []
            for _ in range(4):
                count, offset = leb(offset); counts.append(count)
            for _ in range(counts[0] + counts[1]):
                _, offset = leb(offset); _, offset = leb(offset)
            for count in counts[2:]:
                method = 0
                for _ in range(count):
                    delta, offset = leb(offset); method += delta
                    access, offset = leb(offset); _, offset = leb(offset)
                    _, proto, name_id = struct.unpack_from("<HHI", data, method_offset + 8 * method)
                    if string(name_id) == "<init>":
                        parameters = u32(proto_offset + 12 * proto + 8)
                        if parameters == 0 or u32(parameters) == 0:
                            constructors.append(access)
        yield name, {"class_flags": flags, "public_noarg_constructor": any(f & 1 for f in constructors),
                     "concrete": not bool(flags & (0x400 | 0x200))}

def check(apk, mapping=None):
    rename = {}
    if mapping:
        for line in Path(mapping).read_text().splitlines():
            match = re.fullmatch(r"(\S+) -> (\S+):", line)
            if match: rename[match[1]] = match[2]
    found = {}
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if re.fullmatch(r"classes\d*\.dex", name): found.update(classes(archive.read(name)))
    checks = []
    for name in REGISTERED:
        original = PACKAGE + name
        descriptor = "L" + rename.get(original, original).replace(".", "/") + ";"
        info = found.get(descriptor, {})
        checks.append({"class": original, **info, "pass": bool(info.get("concrete") and info.get("public_noarg_constructor"))})
    return {"apk": Path(apk).name, "checks": checks, "pass": all(c["pass"] for c in checks)}

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk"); parser.add_argument("--mapping"); parser.add_argument("--report")
    args = parser.parse_args()
    report = check(args.apk, args.mapping)
    if args.report: Path(args.report).write_text(json.dumps(report, indent=2))
    for item in report["checks"]:
        print(("PASS" if item["pass"] else "FAIL"), item["class"], "concrete=", item.get("concrete"), "constructor=", item.get("public_noarg_constructor"))
    raise SystemExit(0 if report["pass"] else 1)
