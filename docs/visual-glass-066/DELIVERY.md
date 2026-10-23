# Verified 0.6.6-test APK

[Download the final package containing Ytdlp-G.apk](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39974539418/artifacts/12511515876).
Extract the ZIP and locate **Ytdlp-G.apk**. GitHub may require login for Actions downloads.

[Successful final Actions run](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39974539418) · [Root causes and changes](REPORT.md) · [Software evidence](README.md) · [Phone verification](DEVICE_CHECK.md) · [Machine-readable checks](DELIVERY.json)

Final compiled source: `985ac36845028cc2202e8c5f52a63e66b43a3dc7`.
Use this exact run; earlier 0.6.6 builds are intermediate verification attempts.

- Version: **66 / 0.6.6-test**, ARM64, Android 8+.
- Application ID: `com.fuhbe.ytdlpg.minimal`.
- Downloaded APK: **59,616,524 bytes**.
- APK SHA-256: `857ed51fe620ca3c11b6f74b088f812f9a9736725c40223eb45bacc8f1435c16`.
- Corresponding source ZIP SHA-256: `c002918ac39cd61acaa0161ad8f4ca55cb34528ac5597735e520fe7bae9872b7`.
- Signing certificate SHA-256: `027cabed1749e811e49ee3f328eaa5d2537891bfe4630145c1720e921cdef888`.

## Confirmed results

All three artifacts were downloaded independently after the successful final run.
GitHub artifact SHA-256 digests, ZIP CRCs, internal APK/source checksums, Android
signature, native runtimes, exact supplied wallpaper PNGs, third-party notices,
corresponding source and matching instrumentation signature were checked.

**143 JVM cases: 142 passed, one pre-existing host-Skia shader limitation skipped.**
All 138 original case identities/outcomes remain. Original test sources and 114
protected files remain byte-identical. MainActivity only adds the source cache
key; navigation callbacks/layout and all nonvisual dependencies are preserved.

The deterministic clock reproduces old spring chasing (0.5276055 versus target
0.8 after 32 test-clock ms), while the corrected held position is already 0.8.
Sixty actual drawing/input samples produce zero extra composition, zero source
records and zero readbacks during hold, with a populated source cache. The pixel
probe displaces 5,389 interior pixels and zero exterior pixels. A drawing check
also verifies specular light stays visible above an opaque source texture.
These are **host/software measurements**, not physical touch latency or GPU FPS.

Actual HTTP/HLS media fixtures, MP3, MP4 remux/conversion and failed-conversion
source preservation pass. All 13 optimized DEX ZIP-bootstrap checks pass, as does
the positive/negative R8 ZIP regression. These do not claim a new live YouTube
result or a physical Android media test.

## Pending device acceptance

No emulator or physical device was executed here. **Nine device tests compiled;
none were counted as run.** Native API31/32 mesh, API33+ AGSL optics, touch feel,
frame time, jank, temperature and sustained 60 FPS remain pending actual hardware.
The Android 12 path uses a cached real-control texture and HWUI mesh; it does not
provide AGSL on Android 12 or claim identical Apple optical physics.

[Matching device-test APK](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39974539418/artifacts/12511825027) · [Run and collect evidence on the Redmi](DEVICE_CHECK.md).
The device probe records Window FrameMetrics and compares the same real control
at zero/nonzero optical strength. The UI software gallery is explicitly labeled.
The initial packaging run found a baseline-manifest schema error in the verifier;
that check was corrected and complete packaging passed before this delivery.

## Install without losing data

This development certificate **differs from both 0.6.4 and 0.6.5-test**. Android
cannot directly update an installed APK signed with another key. The same app ID
is preserved, so these builds cannot be installed side by side. Do not uninstall
a working copy with data you cannot preserve. Export/preserve your files before
considering an uninstall; app-private queue/history can otherwise be lost.

The published [stable 0.6.4](https://github.com/fuhbe/Ytdlp-G-Android/releases/tag/v0.6.4),
its exact APK hash, tags and default branch were rechecked and remain unchanged.
The final build is on `visual/glass-gesture-optics-066`; no stable release is replaced.

## Final artifacts

- [APK, corresponding source, licenses, images and reports](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39974539418/artifacts/12511515876)
- [Matching device tests](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39974539418/artifacts/12511825027)
- [JUnit XML, mapping and complete verification evidence](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39974539418/artifacts/12511082161)

Current Actions artifact expiry: January 21, 2027. This is a test build for joint
phone acceptance, not a newly declared stable release.
