# Ytdlp-G 0.6.3 — Liquid Glass visual test

Install **Ytdlp-G.apk** from this artifact. It is an ARM64 Android 8+ build of
the existing minimal application, with a visual-only update on a separate branch.
The 0.6.2 public release and main branch remain the known working baseline.

## Material behavior

- Android 8–11: translucent tinted material, edge highlights and gradients.
- Android 12/12L: actual sampled background blur when the view is accelerated
  and the device has sufficient memory and power saver is off.
- Android 13+: the same blur plus a RuntimeShader lens on essential controls,
  when supported. A rejected lens falls back to blur.
- Low memory, low RAM, software rendering or power saver: the lighter material.

A single static wallpaper layer is shared and cached. Animated progress/text is
not captured into a full-screen backdrop. The dock reserves layout space and
cannot cover the download controls. It only relocates existing destinations.

Download, extraction, formats, conversion, ZIP, storage, queue, database, workers,
services, permissions, callbacks and native packages are unchanged. Check
`visual-scope.json`, `LIQUID_GLASS.md` and the exact Actions verification artifact.
`Ytdlp-G-source.zip` includes corresponding source and the Apache-2.0 Backdrop
1.0.3 source JAR; license notices are also embedded in the APK.

## Installation and existing data

The app name and application ID stay the same. CI uses a fresh test signing key,
which can differ from 0.6.2. Android may therefore refuse an in-place update.
Do not uninstall a working installation holding needed queue data or partials:
uninstall removes its private queue, settings and temporary downloads. Finish or
export what you need first. Already exported Downloads/folder files remain.
Changing presentation does not change Android's signing requirements.

## Verification scope

The baseline built with all 57 original JVM tests passing. This build reruns
those original tests and adds real Compose rendering/interaction tests on APIs
26, 28, 31, 33 and 35. The known host-Skia child-shader limitation is explicitly
reported as a skipped GPU pixel test; it is not counted as a pass. Screenshots
in this artifact are actual UI renders with sample metadata, using the software
fallback material, not captures of a real network download or a device GPU.

A matching test APK is published in Liquid-Glass-device-tests. See
LIQUID_GLASS.md for native hardware shader/blur, physical Redmi, keyboard,
background download and performance acceptance instructions. Compilation of
instrumentation is not an executed device test. No universal FPS, memory,
battery, YouTube or device-success claim is made.
