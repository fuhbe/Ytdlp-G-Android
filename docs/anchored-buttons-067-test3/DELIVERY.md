# Ytdlp-G 0.6.7-test3 — verified experimental delivery

**Experimental only. Stablev0.6.6 and test2 remain untouched.**

- Branch: [`fix/anchored-buttons-quality-067-test3`](https://github.com/fuhbe/Ytdlp-G-Android/tree/fix/anchored-buttons-quality-067-test3).
- Exact compiled source: [`044f15a936b1c4640beec0c60b3903fd51fb71d9`](https://github.com/fuhbe/Ytdlp-G-Android/commit/044f15a936b1c4640beec0c60b3903fd51fb71d9). Baseline is test2 delivery156bff94b27b17dfa12bc3f749137d7d6e30e723 (compiled test2d87307b51215c1104db83a1111f3cb5f5185fd50), not stable0.6.6.
- [Successful Actions build](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40124836150).
- [Download APK, corresponding source, licenses and checksums](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40124836150/artifacts/12573435805). Sign in to GitHub, download the ZIP and extract **Ytdlp-G.apk**.
- [Full test/verification/capture artifact](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40124836150/artifacts/12573685609) · [Matching compiled instrumentation](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40124836150/artifacts/12573565698).
- [Actual screenshots and GIFs](captures/README.md) · [Exact compiled file-change list](SOURCE_FILES_CHANGED.json) · [Phone acceptance](TESTING.md).

VersionName **0.6.7-test3**, versionCode **73**, package `com.fuhbe.ytdlpg.minimal`, ARM64, Android8/API26+. Optimized release-mode APK; not debuggable. Artifact expiry currently2027-01-22.

## Three reproduced causes and the correction

| Problem | Confirmed before-fix cause/evidence | Test3 correction |
| --- | --- | --- |
| Button travels/looks detached | Test2 combined whole-body translation plus a long-pull extension approaching a control height. The root copy retained the press-time origin during scrolling; the geometry regression fails for excessive travel | Keep the Square single G2 capsule/material, replace only the matrix with a2dp tanh-limited directional extension, fixed opposite edge and1dp pressure response. At most2.55dp silhouette overscan; labels/icons/hit targets stay fixed. Scrolling moves the original control normally with its layout |
| Duplicate Download | The owner’s physical screenshots show two surfaces. A host test independently measures two live content compositions: original plus root clone, with composition-driven visibility and separate geometry/handoff | One material and one crisp content tree are drawn in-place. No root visual copy, alpha handoff, stale press-origin painting or floating catch-up remains. The input observer and root return-lifetime cleanup stay unchanged |
| First quality tap is ignored | A real pointer tap at visible progress0.97 fails while test2 waits for `!isRunning && progress>=.999`; rows are visible but disabled/blocked during the spring tail | Use the existing reveal threshold0.86 and couple item visibility/enabled state in the same composition. The original live callback guard selects once, then the same SDF animation reverses; no wait for spring settling |

[Before-fix failures](REPRODUCTION.json) are genuine executed tests, not assertions inferred from source. The same regression family passes after correction, including API26/32/35 first-tap variants. The two-composition host proof is not presented as a physical GPU double-pixel capture; the owner-provided images are the physical observation. Hardware-rendering acceptance is still pending.

## Exact scope

Only these **four existing production UI files** changed:

- `minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/SquareButtonMaterial.kt`: constrained matrix only. Every shader/material/highlight/cache statement below that function remains byte-identical.
- `minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/GlassButtonHost.kt`: remove the cloned root material/content. Registered bounds, window dispatch delegation, pointer tracking, cancellation, click safety and return-scope cleanup are unchanged.
- `minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/ElasticGlassButton.kt`: remove floating visibility/stored clone assignments; draw the existing material/content once in-place. Motion/spring, pointer observer, `.clickable` callback and hit target remain unchanged.
- `minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/GlassMorphMenu.kt`: first-visible input readiness and visibility coupling only. Options, resolution data, source capture, geometry, shaders, physics, focus/dismissal and original selection action remain unchanged.

Also changed: `minimal/app/build.gradle.kts` (only72→73/test2→test3); `StableReleaseCaptureTest.kt` (version literals only); `SquareLongPullTest.kt` (one obsolete >24px whole-button-travel assertion migrated to the new anchored bound, with identity and all gesture/callback checks preserved). New tests are `AnchoredButtonRegressionTest.kt`, `QualityFirstTapRegressionTest.kt` and `Test3CaptureTest.kt`. One experimental workflow and task-specific documentation/package/audit scripts accompany the patch. [Complete compiled list](SOURCE_FILES_CHANGED.json).

The scope guard verifies **208 original files byte-for-byte**. MainActivity and all actual analyze/Download/Paste/folder/update callbacks, engine, yt-dlp, FFmpeg, URL analysis, MP3/MP4, codecs/resolutions, playlists/ZIP, workers/services, queue controls, storage/database/files/permissions, languages/preferences and all four selector source files/effects are unchanged. SDF geometry/physics/shaders and Square material/highlight/cache are unchanged. No dependency, SDK, app ID, minSDK or signing-policy change occurred. [Scope proof](receipts/VERIFIED.json) · [Guard](verify_scope.py).

## Tests and actual rendering evidence

**281 tests executed:279 PASSED,2 SKIPPED,0 FAILED.** All269 test2 case identities/outcomes remain, including the two inherited hardware/host-rendering skips. The complete existing controlled-media/network/MP3/MP4/HLS/ZIP/bootstrap and four-slider suites ran unchanged, apart from the explicitly migrated visual travel expectation.

New checks exercise fixed opposite edges and nonlinear resistance across3 densities/3 widths, one content composition through press/pull/release, same-pointer long pulls, no accidental click, and first actual pointer taps on Best/1080/720 while the spring still runs. Existing tests cover keyboard/accessibility, loading/disabled state, cancellation, multitouch, scroll priority, lazy disposal, reduced motion, themes/languages, viewport constraints, edge/intermediate/RTL/diagonal slider acquisition, optical caches and no duplicate ripple.

**46 new actual AppContent/Popup frames** show Save to/Download at rest, hold, release and exact return, plus a first1080p tap and animated closure. English dark, Portuguese light and Spanish narrow/large-font cases ran. Frame JSON verifies that the quality spring is running when the menu is visible, that one tap updates720→1080, and that closure retains1080p. Existing62 optical-regression frames and6 language screens also remain verified. [Actual capture/GIF gallery](captures/README.md).

The new screenshot fixture was corrected to use an API26-compatible window path and scroll to uncomposed lazy items on narrow large-font screens; those initial harness failures were not application regressions. Final local and CI suites pass. The first independent archive-check script also needed its workflow-name comparison updated from test2 to test3; the correct test3 workflow was already in the published source bundle, and the complete independent check then passed.

No physical phone, emulator, live YouTube download or GPU frame-time test was executed here. Instrumentation was **COMPILED ONLY**. Native Skia screenshots and slowed GIFs are not GPU refraction proof, phone video or60/90/120FPS measurements. Existing host cache checks still show zero extra captures/compositions/MOVE springs, and controlled real-media fixture checks passed. Android13+ retains the unchanged AGSL path; Android12 retains its existing SDF contour/captured-screen blur fallback, which is not claimed as arbitrary-neck lens refraction. Owner phone acceptance remains required before stable promotion.

## Verified published files and signing

All three published archives were downloaded independently. Their SHA-256 hashes match GitHub’s artifact API and ZIP CRCs pass. APK metadata/signature, matching instrumentation certificate,83 localized resource keys/translations,218 corresponding minimal source files,20 original runtime/license payloads,7 native libraries,4 retained Square/MIT notices and all capture hashes were checked after download.

| File/identity | SHA-256 |
| --- | --- |
| Ytdlp-G.apk (59,701,292bytes) | `b698c8ad34bf72bb5cc5fe8a53f684ff82c2c05979eaa46b2a76f7928233e396` |
| Ytdlp-G-source.zip | `adff60bb1efd2dac9d9f02a69f9d84640bcca44299439a4c67587aa7dc554e97` |
| APK signing certificate | `472bf7be999fafa4356285e3a58d71766eebe6ddc279e55d0ddec071563383a0` |

Signing mode: **ci-default**. The signature is valid and the instrumentation APK matches it. This certificate differs from test2 and stablev0.6.6; no signing continuity is claimed. The owner-key/CI-default policy itself was not changed, and no private signing material is published. Android will block an in-place update over those different signatures. Uninstalling can erase private preferences, queue and partial downloads; do not discard your working installation/data without accepting that consequence. A separate test device avoids the conflict; work/user profiles do not bypass global package signatures.

[Independent verification](receipts/download-verification.json) · [Raw signature](receipts/apksigner.txt) · [CI receipt](receipts/VERIFIED.json) · [SHA256SUMS](receipts/SHA256SUMS.txt).

## Stable and test2 preservation

Main remains `80a1e93b9470f5b6ecfdab986311f41614510d07`; stable tagv0.6.6 remains `df74cb9fde1cf31d42870a3d905b4a99c72fa407`. Test2 branch remains `156bff94b27b17dfa12bc3f749137d7d6e30e723`. All4 previous releases/assets and all6 previous tags were compared and retained. Latest stable is stillv0.6.6; no stable release/tag was overwritten or newly promoted. [Preservation receipt](receipts/stable-preservation.json).

Square, Backdrop and MIT sources/pins/notices are unchanged and remain in the APK/source bundle. [Provenance](PROVENANCE.md). No new feature or generalized master prompt was added. This delivery is **0.6.7-test3**, ready for the owner’s three phone acceptance scenarios, not a stable promotion.
