# Ytdlp-G 0.6.7-test1 — verified experimental delivery

**TEST BUILD ONLY. Stable v0.6.6 remains unchanged and latest.**

- [Download the test bundle containing Ytdlp-G.apk](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40106009294/artifacts/12564728339)
- [Matching device tests — compiled, not executed](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40106009294/artifacts/12564857711)
- [Full verification artifact, original frames and GIFs](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40106009294/artifacts/12564658694)
- [Successful build and verification workflow](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40106009294)
- [Exact compiled source commit](https://github.com/fuhbe/Ytdlp-G-Android/commit/4f09a59c888d6d67800baeaff53f968fe9ced02b)
- [Genuine app captures and frame sequences](captures/README.md) · [Implementation/API matrix](README.md) · [Redmi acceptance](TESTING.md) · [Changelog](CHANGELOG.md)

GitHub Actions artifact downloads require a GitHub session and are ZIP bundles. Extract `Ytdlp-G.apk`. Artifact expiry is recorded in [ARTIFACTS.json](ARTIFACTS.json); this experimental build is not a stable release or a new tag.

## APK identity and signing

| Check | Verified result |
| --- | --- |
| Version / code | `0.6.7-test1` / `71` |
| Package / minimum / ABI | `com.fuhbe.ytdlpg.minimal` / Android8 API26 / ARM64 |
| APK bytes | 59,629,544 |
| APK SHA-256 | `fb43381e2403960dcb5f7270293550c0a80e4b346e83017db45244a41d342975` |
| Corresponding source ZIP SHA-256 | `f11fbd2b7dc743a4edcc096abe5c24e3ef802d6a0aad0a35a972aea7c15f94ba` |
| Signing certificate SHA-256 | `0a5754023fcd24e63e559bab1a9aad754a645807d346b0251f2391155a07fdcb` |
| Signing policy / actual mode | Unchanged optional owner-key policy / `ci-default` |
| Same certificate as stable0.6.6 | `False` |

APK signature, ZIP CRCs, checksums, source correspondence, package/version/minSDK/ABI and the matching instrumentation certificate were checked in CI and independently after downloading all three artifacts. [CI receipt](VERIFIED.json) · [Independent download verification](DOWNLOAD_VERIFICATION.json) · [Checksums](SHA256SUMS.txt).

**The default CI certificate differs from stable0.6.6.** An in-place update can be blocked. Do not uninstall the working app without preserving wanted private data: settings, queue and partial files can be lost. A separate Android profile does not bypass package-signature identity. Use a separate test device or retain the stable installation until signing continuity is available. No signing key was changed, exposed or included in any artifact.

## What changed

`ElasticGlassButton.kt` adds a reusable fixed-hit-target button/icon primitive: immediate pressure, bounded directional shape deformation, a moving localized highlight, preserved real-background material tiers and a single release spring. Glyphs stay crisp. Significant drags, outside releases, cancellation and extra pointers do not invoke actions; ordinary taps, keyboard/TalkBack semantics, disabled/loading state and ancestor scrolling remain. No overlapping Material ripple is added. Converted wrappers invoke exactly the old Analyze, Download, folder, update and clipboard callbacks.

`GlassMorphGeometry.kt` and `GlassMorphMenu.kt` coordinate one progress and one Popup: origin expansion, connected hourglass silhouette, slim vertical panel, readable item reveal and reverse merge. The actual existing choices/callback remain exact. Items are disabled while forming/closing; Back/outside/rapid interruption, clipping, RTL, long lists and recreation are handled. A cached activity-only source never captures the Popup recursively. The no-blur panel has its own translucent contrast layer, not a screen-wide opaque dialog.

Existing UI files changed: `GlassQualityPicker.kt`, `GlassOverlaySource.kt`, `MainActivity.kt`. Build metadata changes only versionName/code. New host/device tests and capture fixtures provide evidence. All original selector/navigation/optics sources remain unchanged.

## Executed verification

| Category | Result |
| --- | --- |
| JVM/Compose/native-Skia host cases | **239 PASSED, 0 FAILED, 2 existing SKIPPED** out of241 |
| Approved baseline cases | All204 identities/outcomes preserved (202passed, 2skipped) |
| Protected baseline source files | **130 byte-identical**; exact callback substitutions and version-only Gradle differences checked |
| Stable binary runtime/license payloads | **20 byte-identical**, including all7 native libraries |
| Real controlled HTTP/HLS MP3/MP4 fixtures | PASSED; actual media conversion/validation, not live-phone YouTube acceptance |
| R8 ZIP/bootstrap | PASSED, including expected failing negative control |
| Button motion counter probe | 60MOVE samples: **0 extra compositions, captures, readbacks or return springs**; one spring on release |
| High-density button texture | xxhdpi regression PASSED; wide phone button actually samples cache |
| Menu source probe | 12transition frames: **0 extra source records**, no CPU readback, all source users released |
| Genuine application captures | **34 frames**, light/dark, with sample metadata, actual app/Popup windows |
| No-blur menu contrast sample | Light 11.00:1; dark 13.30:1. One measured background sample each, not app-wide certification |
| Instrumentation APK | **BUILT ONLY — not executed** |
| Physical phone / emulator / GPU frame measurements | **NOT EXECUTED / unavailable** |

The two unchanged skips are `LiquidGlassInteractionTest.exactDependencyLensShaderCompilesAndRefractsRealPixels` and `GlassSliderRippleTest.standardRippleIsAbsentButGlassResponseAndOutsideButtonIndicationRemain`: host rendering cannot establish their GPU/ripple positive controls. No new test is disabled to obtain success. Development iterations exposed RTL alignment, unmeasured-Popup test timing, an overly small physical-pixel cache guard and no-blur contrast issues; these were corrected and the final workflow reran the complete suite.

The screenshots/GIFs are genuine **software host** captures, not physical-device recordings. Original PNGs, state/progress metadata and hashes are preserved. Cropped/slowed GIF derivatives are labelled and traceable. They demonstrate silhouette topology and state, not real-time smoothness, GPU refraction, touch latency or60FPS. No physical-performance claim is made.

## Optical compatibility and phone acceptance

- **API33+ / capable GPU:** reuse the pinned rounded Backdrop/AGSL lens for buttons/open panel. The arbitrary neck uses captured-screen blur/rim; the pinned lens accepts `CornerBasedShape`, not a generic neck.
- **API31–32 / Android12 Redmi:** button backgrounds can reuse the existing cached Canvas/HWUI mesh; menu body/neck uses captured-screen blur. That neck blur is **not real optical lens refraction**.
- **API26–30, power saver, low memory, software or unavailable effects:** readable translucent tint, rims, bounded deformation and safe spring/reduced-motion behavior.

Validate normal taps versus pulls, diagonal/outside cancellation, scrolling, open/close/reopen, actual MP4 selection, both themes/power saver/reduced motion and all four existing selector edge/diagonal gestures on the Redmi. Optional real-hardware FrameMetrics/Perfetto/screen-recording instructions are in [TESTING.md](TESTING.md). Stable promotion requires owner feedback; no generalized master prompt is included.

## Source and preservation

Source branch: `feature/liquid-glass-buttons-morph-067`. **Compiled source:** `4f09a59c888d6d67800baeaff53f968fe9ced02b`. A later documentation-only delivery commit adds receipts/captures with `[skip ci]` and does not change the APK implementation.

All download/model/runtime/yt-dlp/FFmpeg/network/media parsing/queue/worker/service/storage/playlist/ZIP/pause-resume-cancel code, pinned dependencies, app ID, minimum Android, signing policy, wallpapers and the four approved sliding selectors are preserved. Corresponding source and GPL/source/license notices are included in the bundle. Previous releases and tags are retained; main and stable v0.6.6 are not updated by this experiment.
