# 0.6.6-test — direct gestures and real-content optics

This isolated visual/performance test is based on delivered **0.6.5-test**, commit
`44e4184201c4cb75a8a5286d3cde70aa492be07a`. It preserves the approved **0.6.4**
release, its tags, the default branch and all download implementations.

## Root causes established from code and regression tests

The old controller cancelled and launched a new position spring for each pointer
sample. A position-animation callback then cancelled/launched a velocity spring
per frame. The highlight launched a coroutine to copy each finger coordinate.
This made the visual position chase the finger rather than follow it directly.
A test-only exact copy of the original controller reproduces that behavior on
the same deterministic Compose clock: target **0.8**, after **32 test-clock ms**,
old position **0.5276055**, corrected position **0.8**. This is a reproduction of
algorithmic delay, **not a measurement of physical touch latency or FPS**.

The selector read motion state during composition, created/interpolated measured
widths there, and animated the width again. Its outer shell also followed the
motion and recaptured/composited two backdrop sources with a saveLayer. The old
refraction source deliberately excluded icons and text, so those features could
not bend. Blur could additionally obscure optical detail. GPU cost and the
relative share of each cause on the Redmi remain unmeasured here.

## Changes

- The pointer is authoritative while held: synchronous state updates with input
  timestamps, no position spring or coroutine per sample. Finite Square-derived
  pressure/deformation and release springs remain. A new press cancels a pending
  release transition. Existing release, cancellation and selection rules remain.
- Position/geometry are read in the drawing/measurement phase, not composition.
  The label row is isolated in its own graphics layer. The width follows physical
  measured bounds directly, without a second chasing animation.
- The shell is static. Its actual background and current real glyphs are recorded
  into one cached source with explicit revisions for selection, labels, geometry,
  theme and release. No recursive capture of the lens. Original glyphs under the
  capsule are masked when a valid optical source exists, avoiding doubled text.
- API31/32 uses Canvas/HWUI **drawBitmapMesh** to distort the actual source pixels.
  One bounded snapshot is prepared off the gesture path; bitmap conversion runs
  on a background dispatcher. The texture is reused during touch, and the 48×12
  mesh reuses its vertex array and paint. No per-frame bitmap capture, CPU image
  warp, RuntimeShader, extra window, TextureView or new dependency is required.
- API33+ uses the existing native Backdrop AGSL lens against the **real cached
  content**, including text/icons, with a crisp optical center and cached shader.
  Specular lighting follows the touch; deformation and release elasticity remain.

The mesh is a geometric lens approximation that magnifies interior details and
compresses its edge region. It genuinely displaces texture samples; it is not a
gradient presented as refraction. It does **not** claim optical equivalence to
Apple's proprietary effect. The supplied iOS reference is assessed by observable
features: real glyph/color displacement, transparent center, curved edge band,
moving specular and drag deformation. Native device images are still required
before accepting visual equivalence or performance.

## Square review and compatibility

Square HEAD was checked again: `17ed2b87974cf6fec9c531ef3f1438448bb2b729`.
Its LiquidBottomTabs combines actual content layers and lens effects, its
Backdrop caches RuntimeShader instances, and its newer fork exposes frozen
capture controls. That shader lens still checks RuntimeShader support; it does
not itself supply AGSL on Android 12. We retain the previously pinned library
and licenses, rather than changing the SDK/toolchain or download dependencies.

| Platform | Optical path |
| --- | --- |
| Android 8–11 / API26–30 | Preserved compatible glass, lighting and elastic gestures. No advanced refraction claim. |
| Android 12 / API31–32, capable hardware window | Actual cached control texture distorted by a mesh; HWUI can render this on GPU without AGSL. Physical device acceptance is pending. |
| Android 13+ / API33+ | Existing native AGSL lens now samples real background/text/icons. Physical device acceptance is pending. |
| Low RAM, power saver, software window, oversized source or rejected capture/draw | Preserved inexpensive visual fallback with working gestures. No fake successful refraction claim; no crash. |

The API31/32 source is a cache of this control's actual pixels, refreshed on real
content changes and frozen during touch. It is not a live full-screen camera or
a capture of unrelated animated downloads behind the app. Source area is capped
at two million pixels per control. A cold/rejected source leaves the readable
original content visible. No 60 FPS guarantee is made on unsupported hardware.

## Evidence and remaining acceptance

All **138 original JVM test identities** and their sources are retained. New
tests reproduce the old delay, check immediate input/release behavior, execute
actual drawing with a populated cache, verify no per-input composition/source
record/readback over 60 samples, and check finite/unfolded reusable mesh geometry.
The software pixel probe displaces **5,389 interior pixels**, with **zero changed
exterior pixels**, using actual source text/grid pixels. These values describe
the deterministic software fixture, not device speed or GPU throughput.

The original gesture/API/font/RTL/multitouch/queue/media/bootstrap regressions
continue to run. `BASELINE.json` and `verify_scope.py` enforce byte-identical
runtime, yt-dlp, FFmpeg, models, workers, storage, ZIP, playlists, acceleration,
folder selection, services, wallpaper resources and existing tests. MainActivity
only adds a source-content cache key; navigation actions/layout are unchanged.

**Automated host tests:** real Compose layouts and software Skia pixel rendering;
fixture HTTP/HLS/MP3/MP4 tests, R8/Dex bootstrap and ZIP regressions. Reports and
software captures are included in the build artifact.

**Emulator:** not executed here; no usable accelerated emulator/KVM is available.

**Physical device:** not executed here; no attached Android device. Therefore
GPU refraction, touch latency, frame times, jank, temperature and sustained 60 FPS
are **pending acceptance**, not verified successes. The test APK is provided to
perform this check together on the Redmi 10 Prime / Android 12.

The new matching `GlassPerformanceDeviceTest` records actual Window FrameMetrics
total/GPU percentiles, refresh-budget overruns, dropped metric callbacks, the
hardware Canvas flag and cache counters. It captures the same production control
with zero/nonzero optical strength while keeping source/material identical.
Existing eight device tests remain; nine device cases are compiled. See the
device instructions for precise commands and interpretation.

## Modified production files

Only `GlassSlidingSegments`, `SquareGlassMaterial`, `squareglass/DampedDragAnimation`,
`squareglass/InteractiveHighlight`, the label source-key wiring in MainActivity
and GlassNavigation, and version metadata change. New isolated visual files are
`GlassMeshLens` and `GlassContentBackdrop`. No functional download file changes.
