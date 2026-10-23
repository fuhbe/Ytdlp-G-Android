# 0.6.6-test3 — slider endpoints and Material ripple only

This test branch is based on delivered 0.6.6-test2. Stable v0.6.4 and `main`
remain untouched. The APK keeps the existing app identity and all download code.

## Reproduced cause and correction

The pointer observer was inside the existing **4dp internal padding**, and
`glassDragTarget` rejected `x < 0` and `x >= rowWidth`. A release at the shell's
right boundary or either internal margin therefore returned no destination. The
code then started `restore()` toward the previous selection before attempting
to notify the parent. `REPRODUCTION.json` records the actual test2 failure:
expected destination 1, actual destination 0.

The observer now includes that same padded shell. Pointer coordinates are
translated back into the unchanged measured row coordinate system for both
movement and highlights. End recognition uses the latest finger position and
actual measured option bounds, never the elastic pane center. A captured
horizontal drag at/past the left or right edge clamps to the corresponding
physical endpoint, including RTL and unequal child widths. Vertical escape,
explicit cancellation, multi-pointer cancellation and a gesture that starts
outside the control keep their cancellation behavior. Intermediate directional
38% tolerance is byte-for-byte unchanged.

On release, the existing callback is confirmed once and the existing spring is
given that explicit destination. It no longer deliberately starts toward the
old choice. No spring parameters, deformation, refraction, highlight, cache,
lighting, shader, material, dimensions or child layout were changed.

## Additional requested ripple suppression

The shared row now provides Material3 `LocalRippleConfiguration = null`. This
only suppresses Material's default indication within these slider children:
MP3/MP4, Downloads/Completed, Normal/ZIP and bottom navigation. Existing shared
glass pressure/specular response remains. Ordinary buttons, quality controls,
settings and other UI retain their existing indication configuration. Press,
release and cancellation interaction events, touch callbacks and drag ownership
remain active; no clickable or gesture recognizer is disabled.

## Regression checks

- The exact shell-right-boundary test fails against test2 and passes after the fix.
- Both ends and internal margins are exercised at 280/360/600dp control widths,
  from each initial option, with exact edge release and horizontal overshoot,
  on host API26/32/35 configurations. Separate actual window configurations
  cover 320x640, 411x891 and 800x1100dp.
- Additional real pointer tests cover margin-start acquisition, RTL/unequal
  options, final UP position without a final MOVE, spring settling and persistent
  selected destination, vertical escape and cancellation.
- Actual AppContent tests exercise both ends of all four existing controls and
  assert the existing model choice, ZIP, history filter and navigation outcomes.
- The Material configuration scope is checked alongside actual Press/Release/
  Cancel events, tap, drag, exactly-once callbacks and an ordinary outside button.
- The positive-control pixel test does **not** treat identical reference images
  as proof that ripple disappeared. Host Robolectric may not render Android's
  native RippleDrawable; in that case the pixel comparison is explicitly skipped
  and its JSON records that limit. The existing glass response must still change
  real software pixels. Confirm final flash appearance on the physical phone.
- All 158 previous cases remain represented. One obsolete test, which demanded
  that a captured horizontal overshoot never navigate, was explicitly migrated
  to assert the new endpoint behavior. Its method is renamed accordingly. The
  other 157 case identities and expectations remain, including the pre-existing
  unsupported host AGSL case. No cancellation test was removed.

`VERIFIED.json` records the actual final counts and APK checksum. The whole
suite, matching optimized phone-test APK, unchanged media fixture checks and
real R8/ZIP bootstrap checks run in Actions. Building tests is not running them
on Android hardware. No physical/emulator/FPS/GPU performance claim is made.

## Strict scope proof and modified files

`verify_scope.py` hashes 162 protected files against
`79e54fb5ff6da7e5019da9c2e79624cd1223a2d4`. It additionally compares the entire
shared rendering/layout program after removing only gesture registration and
the requested local ripple configuration. Illumination and rubber-band math,
animation sources, shaders, backdrops and all download implementations remain
identical. Dependencies, permissions, wallpapers and app resources are unchanged.

Production edits: `GlassSlidingSegments.kt` (pointer coordinates/commit/local
indication), `GlassSegmentOptics.kt` (endpoint recognition),
`minimal/app/build.gradle.kts` (version code68 / 0.6.6-test3 only).

Tests: new `GlassEdgeGestureTest.kt`, new `GlassSliderRippleTest.kt`, and the
single explicit horizontal-escape expectation in `GlassDockGestureTest.kt`.
Additional files are this report/verification directory and the isolated
`.github/workflows/glass-edge-066.yml`. No other production file is edited.

Square/Backdrop attribution, original source and all licenses remain unchanged.
Android compatibility and every optical path are inherited from test2; this
patch introduces no graphics feature, dependency or rendering path.
