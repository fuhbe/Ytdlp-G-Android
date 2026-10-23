# Liquid Glass 0.6.6-test2 — presentation-only test build

This extends the delivered 0.6.6-test on `visual/liquid-glass-total-066`.
Version code 67 distinguishes the APK; the app remains Ytdlp-G with its existing
package ID, icon, wallpapers and screen structure. Stable **v0.6.4** and `main`
are not updated. This is a test build, pending physical-device acceptance.

## Changes

- The same shared elastic control serves bottom navigation, MP3/MP4,
  Downloads/Completed and Normal/ZIP. Other action buttons and the quality
  trigger/rows now have damped spring pressure feedback.
- A drag can acquire the pane anywhere on the measured track. A small grip
  offset avoids dragging a distant pane from an unselected destination.
  Directional snapping crosses at approximately 38% of the distance between
  physical centers instead of demanding the destination rectangle. Middle and
  end targets use the same rule; gaps, RTL and unequal widths are supported.
  Actual callbacks still occur only on a valid release. Vertical scroll,
  cancellation, multiple pointers and outside release keep their old behavior.
- Text/icon color weights follow physical pane position continuously. Adjacent
  destinations share complementary weights. Sixty-five precomputed tint filters
  avoid creating a color filter for each pointer sample. There is one semantic
  copy of each label/icon. Font weights stay stable to avoid text reflow.
- Background and neutral glyph source layers are separate. The cached actual
  glyph pixels are recolored during drawing, including inside refraction;
  glyphs are not frozen in the previous selection color. The original glyphs
  remain masked beneath the lens. Two bounded texture readbacks on Android 12
  occur outside held motion; no new source recording/readback per held sample.
- Rubber band displacement and bounded extra edge stretch deform the same
  optical pane and mesh. The source cache and mesh vertex array are reused.
  Input remains synchronous; only release/pressure use springs. The position
  release is now lightly underdamped, with cancellation on the next touch.
- The MP4 quality menu is a focusable custom glass popup anchored to the old
  trigger, with spring scale/fade opening and animated closing, elastic rows,
  current-selection radio/check, scrolling and window clamping. Back and outside
  taps dismiss it. Its exact list is still `listOf(0) + inspection.heights`;
  the existing callback still sets `choice.height`. No quality is fabricated.
- While that popup is open, an on-demand layer records actual underlying screen
  content plus wallpaper. It excludes the popup and is disabled on dismissal.
  No bitmap readback or CPU blur is used for this screen source. Separate popup
  window coordinates are translated through screen coordinates with safe guards.
  Rejected capture/shader paths retain compatible readable glass.

## Optical compatibility and limits

| Android | Available production path |
| --- | --- |
| 8–11 (API26–30) | Tinted translucent material, rim/reflections, elastic gestures, progressive ink and popup transitions. No advanced blur/refraction claim. |
| 12/12L (API31–32) | Real RenderEffect backdrop blur; existing reusable HWUI bitmap mesh refracts cached background and actual glyph textures in capsules. The quality panel blurs the actual underlying screen; it does **not** gain the API33 lens. |
| 13+ (API33+) | Existing Backdrop 1.0.3 AGSL lens/specular path, now drawing progressive source ink; expanded quality glass samples the actual underlying screen. |

Low-memory, power-save, software rendering and rejected shader/capture paths use
the established compatible fallback. Mesh optics are an approximation, not Apple's
optical implementation. Refraction of captured pixels is exercised in native
Skia software tests; GPU execution/appearance and sustained frame rate on the
Redmi 10 Prime Android 12 remain **unverified**. The quality panel's API12 blur
must not be described as real lens refraction. No universal 60 FPS claim is made.

## Verification

The complete previous JVM test source and all 143 baseline case identities are
preserved. New tests cover tolerant targets, actual full-track gestures on APIs
26/32/35, physical RTL illumination, finite bounded edge displacement, exact
quality options/callbacks, dismissal, popup placement, real opening/closing
frames, on-demand screen capture and separate cached glyph pixels. They also
render progressive color into actual mesh output without changing the texture.
The previous 60-sample invalidation probe still requires zero extra composition,
source recording or readback while held. Software frame sequences are actual
Compose renders, not design mockups or interpolated animation artwork.

The unchanged local HTTP/HLS/MP3/MP4 and ZIP/bootstrap checks remain in CI. These
are fixtures, not a new live YouTube/device-download verification. The device
package adds `GlassTotalDeviceTest` for all four controls, quality selection,
actual screenshots and Window FrameMetrics. Existing optical A/B and frame
probes remain intact. **Compiling device tests does not execute them.**

Read `VERIFIED.json` and `DELIVERY.md` for the actual completed results, final APK
hash, signature and Actions run. Read `DEVICE_CHECK.md` for phone acceptance.

## Preserved code and licenses

`verify_scope.py` checks 153 protected files byte-for-byte against
`28bdde369593f06445a78f61dfb1a7e5357f1b78`, including all previous test source,
engine, FFmpeg integration, playlist/ZIP, analysis, queue, acceleration,
pause/resume/cancel, workers, services, models, storage and wallpapers.
MainActivity is checked against exact permitted presentation substitutions;
quality options/callbacks and navigation callbacks remain exact. Dependencies,
permissions and minimum Android version are unchanged.

Square HEAD was rechecked as `17ed2b87974cf6fec9c531ef3f1438448bb2b729`.
The local adaptations retain the existing GPL-3.0 / Apache-2.0 provenance,
originals and notices under `minimal/vendor/square` and the APK licenses. No
third-party branding or paid/private implementation is copied.

## Modified source files

Existing: `minimal/app/build.gradle.kts` (test identity only),
`MainActivity.kt` (presentation wiring/pressure), `GlassNavigation.kt`
(neutral stable glyphs), `LiquidGlass.kt` (on-demand overlay wiring),
`GlassSlidingSegments.kt` (gesture/optical composition), `GlassContentBackdrop.kt`
(source glyph composition and cross-window coordinates), `GlassMeshLens.kt`
(progressive glyph tint), `squareglass/DampedDragAnimation.kt` (release damping).

New: `GlassSegmentOptics.kt`, `GlassQualityPicker.kt`, `GlassOverlaySource.kt`,
`GlassTotalInteractionTest.kt`, `GlassTotalDeviceTest.kt`, test-only workflow
`.github/workflows/glass-total-066.yml` and this report/evidence directory.
No pre-existing test implementation is edited. Historical test screenshot files
are restored after collecting the new renders; source history is not rewritten.
