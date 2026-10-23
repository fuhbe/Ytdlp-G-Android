# Verified 0.6.6-test2 delivery

[Download APK package](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40012530091/artifacts/12526504791) ·
[Matching phone test package](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40012530091/artifacts/12526549508) ·
[Complete verification reports](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40012530091/artifacts/12526973644)

Extract **Ytdlp-G.apk** from the main Actions ZIP.

The successful [Actions run](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40012530091)
compiled source **ee4288719c4baebfc971900c0631f0997e1fb948**. The final packages
were downloaded and verified after upload. This documentation update does not
rebuild, change or re-sign the APK. Stable v0.6.4 and `main` remain intact.

| Check | Verified result |
| --- | --- |
| APK | 59,625,448 bytes, ARM64, min API26, version 0.6.6-test2/code67 |
| APK SHA-256 | `e773fb37608c55d8bd48e254373b23a77d7f799716ade52f47778ed18c4b70a2` |
| Signing certificate SHA-256 | `d0c8ea17f4d4a2ee41743fc29c4b7e050aab869e833afef82e34b75f4d421195` |
| Host JVM suite | **157 passed, 1 skipped**, 158 case identities; all 143 previous cases/outcomes retained |
| New host tests | 15 passed, including API26/32/35 gestures and quality panel, RTL, tolerance, progressive pixels, large-font small-screen selection, popup source and cache |
| Scope | 153 protected files byte-identical; exact quality options, navigation/model callbacks and dependencies preserved |
| Native payload | All 7 packaged `.so` payloads byte-identical to the previous delivered 0.6.6 APK |
| Media fixtures | Real HTTP/HLS extraction, MP3, MP4 remux/conversion, progress and failed-conversion preservation passed |
| R8/ZIP | 13 bootstrap checks and the real positive/negative ZIP regression passed |
| Held motion | 60 input samples, zero additional compositions/source recordings/readbacks (host software probe) |
| Progressive optical ink | Actual mesh output changes through intermediate tint values; neutral source pixels unchanged |
| Phone instrumentation | 10 test methods compiled; app and test APK signatures match; **not executed** |
| GPU/emulator/physical phone | **Not tested here**; no measured FPS, thermal or touch-to-photon claim |

The one skipped case is the pre-existing native AGSL pixel test unsupported by
host Robolectric/Skia, not a new ignored regression. Full XML reports are in the
verification artifact. Software screenshots are actual Compose views with fixture
metadata; they do not demonstrate phone GPU effects or live network downloads.

The main package includes source, licenses, 86 actual software PNG captures,
8 app gesture sequences and popup animation captures. `EVIDENCE.json` hashes
all evidence. In this run its two raw `host-metrics` files reside in the separate
verification artifact under `app/build/reports/glass-066`; their actual JSON
values are also embedded in the main package's `VERIFIED.json`. All manifest
hashes were checked across the downloaded artifacts. The workflow now also copies
those two files into the main ZIP for future runs; no APK recompilation was
needed for this packaging-only correction.

CI uses a development key. This APK does not have the same key as stable 0.6.4
or the previous 0.6.6 APK, so Android may reject an in-place update. Preserve your
working app/data; do not uninstall a copy with queue/history you cannot lose.
The package ID remains unchanged and prevents side-by-side installation.

[Visual changes, compatibility and exact modified file list](REPORT.md) ·
[Phone acceptance / FrameMetrics / Logcat instructions](DEVICE_CHECK.md) ·
[Machine-readable verification](DELIVERY.json) · [CI test evidence](VERIFIED.json)
