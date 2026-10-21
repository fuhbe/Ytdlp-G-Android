"""Verify that supported Android locales have the same string and plural keys."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/res"
LOCALES = ("values", "values-pt-rBR", "values-es")


def entries(locale):
    root = ET.parse(ROOT / locale / "strings.xml").getroot()
    return {(item.tag, item.attrib["name"]): item for item in root}


def placeholders(item):
    return sorted(re.findall(r"%(?:\d+\$)?[ds]", " ".join(item.itertext())))


reference = entries(LOCALES[0])
errors = []
for locale in LOCALES[1:]:
    translated = entries(locale)
    if reference.keys() != translated.keys():
        errors.append(f"{locale}: key mismatch: {reference.keys() ^ translated.keys()}")
    for key in reference.keys() & translated.keys():
        if placeholders(reference[key]) != placeholders(translated[key]):
            errors.append(f"{locale}: placeholder mismatch: {key}")
        if key[0] == "plurals":
            source_quantities = {item.attrib["quantity"] for item in reference[key]}
            target_quantities = {item.attrib["quantity"] for item in translated[key]}
            if source_quantities != target_quantities:
                errors.append(f"{locale}: quantity mismatch: {key}")
if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print("English, Brazilian Portuguese, and Spanish resources match.")
