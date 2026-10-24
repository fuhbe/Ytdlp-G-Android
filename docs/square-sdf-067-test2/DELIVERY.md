# Ytdlp-G 0.6.7-test2 — verified experimental delivery

**Test build only. Stable v0.6.6 remains the latest stable release.**

- Branch: [`feature/liquid-glass-square-sdf-067-test2`](https://github.com/fuhbe/Ytdlp-G-Android/tree/feature/liquid-glass-square-sdf-067-test2).
- Exact compiled source: [`d87307b51215c1104db83a1111f3cb5f5185fd50`](https://github.com/fuhbe/Ytdlp-G-Android/commit/d87307b51215c1104db83a1111f3cb5f5185fd50). Baseline: compiled test1 `4f09a59c888d6d67800baeaff53f968fe9ced02b`.
- [Successful GitHub Actions build](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40117901599). VersionName **0.6.7-test2**, versionCode **72**, package `com.fuhbe.ytdlpg.minimal`, ARM64, Android8/API26+; optimized release-mode APK, not debuggable.
- [Download the APK/source/license/checksum bundle](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40117901599/artifacts/12570018485). Sign in to GitHub, download the ZIP and extract **Ytdlp-G.apk**. Artifacts currently expire on2027-01-22.
- [Matching compiled device tests](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40117901599/artifacts/12570448427).
- [Full test/rendering/verification evidence](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40117901599/artifacts/12569823458).

## Download integrity and signing

The actual published archives were downloaded independently. All three archive SHA-256 digests match GitHub’s artifact API; CRC checks passed. The APK and source hashes match the bundled SHA256SUMS and final verification receipt. APK metadata,83 retained UI string keys/translations,215 corresponding minimal source files,20 original runtime/license payloads,7 native libraries,4 added license notices,62 actual optical UI frames and6 language captures were checked again after download.

| Item | SHA-256 |
| --- | --- |
| Ytdlp-G.apk (59,704,476bytes) | `f9ba11d03129bb8bf94b0a9f3674539e411ca4316d720d4bfb18740eb9aef0db` |
| Ytdlp-G-source.zip | `9c5174cc3d6870ea3a9fccfaf299c94f7c8727446a2e45484061d15f062097cb` |
| APK signing certificate | `117e99cef6e46a5b0b20c1d841e631091b4ad5b07d354c935a62e769bd4c8225` |

APK and instrumentation signatures are valid and share the same certificate. **No owner key was configured:** the unchanged CI-default signing policy was used. This certificate differs from stable v0.6.6 and test1, so Android will block an in-place update over those signatures. No key continuity is claimed and no private key is published. Do not uninstall your working app unless you accept losing its private settings/queue/partial data or have a suitable migration. A separate test device avoids that conflict; Android work/user profiles do not bypass global package-signature checks.

[Raw signature evidence](receipts/apksigner.txt) · [Independent download verification](receipts/download-verification.json) · [CI verification](receipts/VERIFIED.json) · [Checksums](receipts/SHA256SUMS.txt).

## Confirmed causes and changes

1. **Long-pull clipping/cancellation:** test1’s custom cubic body had a sub6dp deformation reach; local child/scroll consumption could end its visual feedback. The actual upstream Square G2 ContinuousCapsule, tanh/scale recipe and InteractiveHighlight replace that outline, with a clearly separated bounded long-pull extension. A root paint host observes and delegates the original Window.Callback events, without moving hit targets or adding a parent pointer recognizer. It follows the same finger through long horizontal/vertical/diagonal pulls; only a true window UP/CANCEL ends the window-owned visual stream. Intentional drags cancel clicks, never trigger a new action. Two-button multitouch cancels both actions.
2. **Lazy-list lifetime:** a new real-window regression reproduced a floating body stranded after its LazyColumn source was disposed. The old item-owned coroutine scope cancelled its return spring. The release spring now belongs to the root paint host, and local synthetic cancellation/interaction events cannot override the acquired physical window stream. The regression now proves source disposal, continued physical tracking, a real UP, settled cleanup and zero unintended action.
3. **Paste spacing:** the suspected fillMaxWidth was not on the icon in this baseline; it belongs to the text field. The compact control now has an explicit48dp target,8dp end inset and4dp vertical inset. The clipboard assignment is unchanged, including exact Unicode/escaped multi-link text.
4. **Organic menu:** test1 used two explicit shapes plus a hand-drawn Bezier neck. Two rounded-box SDFs and the pinned MIT smooth-min now determine the fusion pixels. Actual code physics/state/J-curve/overshoot and native reversible handoff replace that bridge. No resolutions or callbacks are added or changed.
5. **Additional owner-requested languages:** Settings offers English, Português (Brasil) and Español using existing Liquid Glass buttons.83 matching string keys translate labels, counts and accessibility descriptions immediately. The UI-only preference survives a new UI state without recreating the Activity or changing jobs. User/media/folder names, URLs, raw engine diagnostics and original legal texts retain their source wording. Folder names are displayed directly, even if they match a translated UI label.

See [exact upstream-to-native mappings and differences](PROVENANCE.md), [rendering architecture](README.md), [authorized localization patch](LOCALIZATION.patch) and [actual captures/GIFs/reference comparison](captures/README.md).

## Actual automated results

**269 tests executed:267 PASSED,2 SKIPPED,0 FAILED.** All241 test1 case identities/outcomes were retained. The full existing media/network-fixture/MP3/MP4/HLS/ZIP/bootstrap suites ran. Two obsolete Bezier/two-permanent-body geometry assertions were explicitly migrated to SDF/vanishing-anchor expectations; no functional or selector test was weakened.

New tests cover real Window.Callback long pulls, same-pointer outside movement, both-button multitouch, true cancellation, source disposal and spring cleanup, normal taps, scrolling priority, all four selector gestures through the delegated window, compact/exact Paste across themes/fonts, empty/changing menus, continuous reversal, cached optics, three saved languages and unchanged stored queue/model choices. Existing accessibility, keyboard, reduced-motion, edge/diagonal/RTL/ripple and viewport regressions remain in the suite.

| Evidence actually executed | Result |
| --- | --- |
| Native Skia software contour/field agreement |218,243 sampled pixels;87 fusion pixels outside both individual blobs;815,300 SDF evaluations |
| Actual API35 RuntimeShader compilation/uniform updates |1 compilation,20 uniform updates; no GPU-pixel claim |
|40 actual window MOVE samples |0 extra material records,0 extra button compositions,0 new MOVE springs |
|18 morph frames with forced software-host blur |0 extra input captures,0 extra activity captures,0 CPU bitmap readbacks in the morph renderer |
| Actual captured UI |62 optical app-window frames +6 language screens; all source hashes verified |
| Release packaging |All native/runtime payloads intact; optimized APK signature, metadata, sources and checksums verified |

The two inherited host skips are `LiquidGlassInteractionTest.exactDependencyLensShaderCompilesAndRefractsRealPixels` and `GlassSliderRippleTest.standardRippleIsAbsentButGlassResponseAndOutsideButtonIndicationRemain`. They need hardware-specific pixel/indication evidence; they are not counted as passes.

The device instrumentation APK was **COMPILED ONLY**. No physical phone, emulator, real GPU displacement, phone frame time/jank or live YouTube download was executed in this environment. Host/software timings and compilation are not60/90/120FPS evidence. An earlier CI attempt failed before compilation because PyPI DNS resolution and artifact-service requests timed out; the final linked build succeeded and its actual published files passed independent verification.

## Android compatibility and honest optical limits

| Android/tier | Implemented behavior | Verified limit |
| --- | --- | --- |
| API33+ / capable GPU |Square/Backdrop capsule lens + original highlight; additional native arbitrary-SDF gradient/normal/Snell shader sampling cached real screen pixels | Shader compiled and bound on host; real GPU pixels and iPhone optical fidelity require device acceptance |
| API31–32, including Redmi Android12 |Square capsule motion/material; real SDF menu contour and captured-screen RenderEffect blur | Menu blur is **not arbitrary-neck lens refraction**; physical visual/performance acceptance pending |
| API26–30, software/low memory/power saver or unavailable effects |Readable tinted/translucent glass, rims/light, bounded shape and finite spring; real SDF geometry | No advanced refraction claim |

The pinned Backdrop lens alone supports rounded CornerBasedShape radii and does not refract arbitrary necks. The extra menu shader is the new implementation for that case. Buttons retain the pinned lens’s approximation of the G2 capsule. Captures use real Compose/AppContent/Popup pixels with labelled sample data, not fabricated mockups. GIFs are cropped/quantized/slowed authentic frame sequences, not real-time phone videos.

## Preserved source and releases

The independent scope check verifies **134 original source files byte-for-byte**, including all engine, runtime, FFmpeg integration, models, workers, queues, storage, permissions, database and services. **9 approved selector/optical files** remain byte-identical to stable; the navigation’s only differences are label/accessibility localization, normalized and compared separately. All four acquired-gesture recognizers, optical materials, edge/intermediate/diagonal selection and local ripple removal remain unchanged. Original action calls and the exact clipboard assignment are checked separately from the explicit UI localization patch. SDK/dependencies, app identity, minSDK and signing policy are unchanged.

Stable preservation was checked against GitHub after publication: `main` remains `80a1e93b9470f5b6ecfdab986311f41614510d07`; tagv0.6.6 remains `df74cb9fde1cf31d42870a3d905b4a99c72fa407`; all4 previous releases/assets and all6 previous tags remain unchanged. Latest stable is still v0.6.6. No experimental release was promoted or stable tag overwritten. [Preservation receipt](receipts/stable-preservation.json).

Square is pinned at5df51684eec348e283126836449d9ac4aa62541e; original GPLv3/Apache2 notices and namespace-only shape sources are retained. The MIT morph engine is pinned at5d2859796ef0f6964e2fdf25962b033eede27814, including Tim Lehmann/Sebastian Degenaar attribution. Original vendor files, hashes, licenses and exact corresponding source ship with this test APK; Ytdlp-G remains GPLv3. No generalized/master app prompt was produced.

## Redmi acceptance before stable promotion

Follow [TESTING.md](TESTING.md). Check the three languages and persistence, raw folder/media names, normal taps versus deliberate long pulls, real cancellation, source scrolling/disposal, rapid menu open/close/Back/outside, actual MP4 options and all four accepted sliders. Test light/dark, large fonts, power saver and animations disabled. Android12 should show the compatible contour/blur menu. The API33+ hardware probe is available separately; run it only on a compatible device and retain its real screenshots/metrics. Stable promotion requires owner acceptance; this build remains **0.6.7-test2**.
