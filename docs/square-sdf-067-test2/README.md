# Ytdlp-G 0.6.7-test2 — Square buttons and native SDF morph

**Experimental build only.** Source baseline: compiled test1 `4f09a59c888d6d67800baeaff53f968fe9ced02b`. Stable `v0.6.6` remains untouched. VersionCode72, same package, dependencies, SDK/minSDK and signing policy. No download feature changes or universal master prompt.

[Technical provenance](PROVENANCE.md) · [Verification and download](DELIVERY.md) · [Device acceptance](TESTING.md) · [Actual app frame sequences](captures/README.md).

## Confirmed causes and corrections

- The test1 button used a custom cubic silhouette, with a deformation reach below6dp. Its Final-pass observer treated ancestor MOVE consumption as visual cancellation. Square's actual `ContinuousCapsule` and tanh/scale matrix replace that silhouette. A separate bounded long-pull extension increases travel to approximately one control height, with a single connected surface.
- The physical hit/semantic target remains fixed. `GlassButtonHost` observes the Activity's original `Window.Callback` stream and forwards every event unchanged. Only registered enabled action-button DOWNs are observed. It retains the same pointer through out-of-bounds movement and child/lazy disposal; scroll consumption invalidates the click rather than cancelling visual tracking. There is no new Compose pointer recognizer above the accepted selectors. A decorative overlay escapes TextField/lazy clipping; labels are translated crisply, not stretched. Normal taps, keyboard and accessibility still call the old callback once.
- The hypothesized `fillMaxWidth` inside the paste icon was **not present** in this baseline; it belongs to the whole text field. The actual compact icon lacked an explicit inset/space for its surface. It now has a48dp target and8dp end/4dp vertical padding, without changing a single clipboard-callback statement. Short/long exact Unicode/escaped URLs and large-font/narrow themes are tested.
- The test1 menu used `Path.combine` plus an explicit cubic bridge. The replacement uses two signed rounded-box fields and the MIT smooth-min formula. A cached RuntimeShader evaluates the field and its gradient on API33+, including in the neck, and refracts the actual cached activity source. Earlier systems use a contour extracted from the same field; no manually drawn neck remains.

## App languages (additional owner request)

Settings now offers **English**, **Português (Brasil)** and **Español** as three accessible Liquid Glass buttons. The UI-only preference is saved separately in `ytdlpg-ui`; changing it updates labels immediately without recreating the Activity, resetting the analysis/model or modifying jobs. Existing action identifiers, media titles, URLs, file names, metadata, original license texts and raw engine diagnostics are preserved. No worker, engine, database or storage source is changed. String resources have identical keys in all three languages. Navigation changes are limited to translated text/accessibility descriptions; the four gesture recognizers and optical materials remain unchanged.

`LOCALIZATION.patch` records the authorized label/provider/card-only differences separately from the visual baseline. `verify_scope.py` also compares original action calls and the exact clipboard assignment, and checks the navigation against stable after removing only the three localization expressions.

## Compatibility

| Platform/tier | Buttons | Morph menu |
| --- | --- | --- |
| API33+ / capable hardware | Square G2 capsule, real pinned Backdrop lens and original radial shader, cached shader instances | Additional two-blob AGSL SDF/normal/Snell shader samples real cached backdrop pixels; source is frozen during motion |
| API31–32 | Bounded Square capsule, RenderEffect blur and compatible optical material | Actual-screen blur clipped to the real SDF contour; **no arbitrary-neck optical refraction claim** |
| API26–30, power saver/low memory/software or rejected effects | Readable tint, rim, pointer light and finite spring | Same SDF geometry with readable tint; no advanced optical/refraction claim |

The pinned Backdrop lens still approximates the continuous capsule with its CornerBasedShape radii. It is **not** used as a fake arbitrary-neck lens. The new menu shader supplies that additional field/normal implementation independently. Hardware execution, phone pixel fidelity and90/120Hz performance require actual-device validation. Host shader compilation and software silhouettes do not prove them.

## Performance architecture

Pointer position is assigned directly; no spring/coroutine is launched per MOVE. Square's pressure/return spring uses damping0.5/stiffness300, and the morph port uses the pinned code's asymmetric open120/16 and close200/21 springs with close hint-3.2. Native morph uniforms change without recompiling the shader. One small RenderEffect wrapper may be created for updated uniforms; this is not shader recompilation. The active button material is frozen per press/source/geometry/theme revision and returns to ordinary live drawing at rest, so asynchronous wallpaper loading is not frozen into a cold frame. The morph background is recorded per source/geometry/theme revision, never per spring/pointer sample. No morph CPU bitmap readback occurs. The older contour renderer reuses bounded scalar/edge arrays and caches held geometry. No perpetual shimmer/timer is introduced.

## Source and license

Square is pinned at `5df51684eec348e283126836449d9ac4aa62541e`, GPL-3.0 with Apache-2.0 catalog/Capsule parts. `liquid_glass_widgets` is pinned at `5d2859796ef0f6964e2fdf25962b033eede27814`, MIT with Tim Lehmann and Sebastian Degenaar notices. Exact consulted originals, manifests and licenses ship in `minimal/vendor/`; APK assets retain every existing notice and include the new MIT/source notices. The corresponding source bundle contains all additions. Ytdlp-G remains GPL-3.0-only.

Build with JDK21 and the unchanged Android SDK36/Gradle wrapper:

```sh
python3 docs/square-sdf-067-test2/verify_scope.py
bash minimal/gradlew -p minimal :app:assembleGithubRelease :app:testGithubDebugUnitTest :app:assembleGithubReleaseAndroidTest --no-daemon --max-workers=1
```
