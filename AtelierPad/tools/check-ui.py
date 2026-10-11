#!/usr/bin/env python3
"""Dependency-free static checks; not an Android build or visual/device test."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
xml_files = list((root / "app/src/main/res").rglob("*.xml")) + [root / "app/src/main/AndroidManifest.xml"]
for path in xml_files:
    ET.parse(path)
drawables = {path.stem for path in (root / "app/src/main/res/drawable").glob("*.xml")}
ids = {item.attrib["name"] for item in ET.parse(root / "app/src/main/res/values/ids.xml").getroot()}
for path in (root / "app/src").rglob("*.kt"):
    text = path.read_text(encoding="utf-8")
    for name in re.findall(r"(?<!android\.)R\.drawable\.(\w+)", text):
        assert name in drawables, f"{path}: missing drawable {name}"
    for name in re.findall(r"(?<!android\.)R\.id\.(\w+)", text):
        assert name in ids, f"{path}: missing view/action id {name}"
main = (root / "app/src/main/java/hu/atelierpad/app/MainActivity.kt").read_text(encoding="utf-8")
for removed in ['"Studio palette"', '"Apply HEX"', 'button("Clear layer")',
                '"ILLUSTRATION STUDIO', 'iconButton(R.drawable.ic_help', 'lateinit var inspector']:
    assert removed not in main, f"Removed UI element remains: {removed}"
order = ['tool(R.drawable.ic_brush', 'tool(R.drawable.ic_select', 'tool(R.drawable.ic_eraser',
         'top.addView(iconButton(R.drawable.ic_layers', 'top.addView(colorSwatch']
assert [main.index(item) for item in order] == sorted(main.index(item) for item in order)
startup = main[main.index('override fun onCreate'):main.index('private fun installContent')]
assert 'showHome(false)' in startup and 'lastProject' not in startup
assert 'versionCode = 4' in (root / 'app/build.gradle.kts').read_text()
assert 'preview04' in (root / 'app/build.gradle.kts').read_text()
print(f"PASS: {len(xml_files)} XML files parse; resource names resolve; requested toolbar order and removed labels checked; gallery is the startup screen.")
print("Static checks only: Android resource linking, rendering and input still require an Android build/device.")
