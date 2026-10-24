# Ytdlp-G 0.6.7-test3 — anchored buttons and first-tap quality

Experimental follow-up to the accepted test2 visuals. Baseline branch `feature/liquid-glass-square-sdf-067-test2`, delivery commit156bff94b27b17dfa12bc3f749137d7d6e30e723 (compiled sourced87307b51215c1104db83a1111f3cb5f5185fd50). Stablev0.6.6, previous releases/tags and test2 remain untouched. VersionName0.6.7-test3, versionCode73; no signing, identity, SDK, dependency or download change.

[Delivery and download](DELIVERY.md) · [Phone acceptance](TESTING.md) · [Before-fix reproduction](REPRODUCTION.json) · [Scope audit](verify_scope.py).

## Three surgical corrections

1. **Fixed visual anchor:** the test2 matrix combined a full-body translation with an added long-pull travel of approximately a control height. The root copy also retained the press-time window origin while its source moved with a scrolling list. Test3 retains the actual Square G2 capsule, material, lens, highlights and cache verbatim, replacing only that matrix with a2dp maximum directional extension and tanh resistance. The opposite edge is invariant during a pull, while text/icons and hit target remain fixed. The existing1dp pressure response produces a maximum2.55dp silhouette overscan, independent of finger travel. A normal page scroll still moves the control with its layout. The same pointer remains tracked outside bounds; the unchanged release spring returns to exact identity.
2. **One representation:** test2 composed the button content twice: its original plus a root copy, switching visibility with composition-driven alpha. The owner’s hardware images show the duplicated Download surfaces. A host regression independently reproduces two live content compositions. That architecture also allowed stale screen coordinates and independent handoff frames. Test3 paints one material and one label/icon tree in-place; there is no root copy, hidden original, alpha handoff or floating catch-up. The root host is retained only for the already verified input stream and return-scope lifetime. No click callback is changed.
3. **First visible tap:** test2 showed menu items before its underdamped spring finished but kept them disabled and consumed input until `!isRunning && progress>=.999`. A real pointer-tap regression reproduces the lost first tap at visible progress. Test3 enables at the existing row-reveal threshold0.86, coupling visibility and input in the same composition while allowing the spring to continue. Current option validation, the single original choose callback, reverse SDF animation, double-dispatch guard, Back/outside dismissal and focus remain unchanged.

The two-copy host test proves duplicated composition; it is not presented as a physical GPU pixel recording. The supplied owner screenshots are the physical observation. New screenshots are genuine software AppContent/Popup captures with labelled sample data. Phone GPU/frame-time fidelity remains subject to owner acceptance.

## Preserved byte-for-byte

`verify_scope.py` checks208 baseline files, including MainActivity and all its actual download/folder/Paste/analyze/update callbacks, app languages/preferences, engine, yt-dlp/FFmpeg integration, workers, services, storage, database and permissions. All four accepted selector implementations/effects and their gesture tests remain unchanged. SDF geometry, physics, shaders, capture source, quality options and Square material/highlight/cache are unchanged. Only four existing production UI files are edited, plus version metadata and tests. One obsolete >24px whole-button-travel assertion is migrated to the new anchored requirement; its identity and every gesture/callback assertion are retained.

Square/Backdrop and MIT engine originals, exact pins and licenses remain in the existing vendor directories and APK assets. No library is upgraded or integrated anew.

## Build

```sh
python3 docs/anchored-buttons-067-test3/verify_scope.py
bash minimal/gradlew -p minimal :app:assembleGithubRelease :app:testGithubDebugUnitTest :app:assembleGithubReleaseAndroidTest --no-daemon --max-workers=1
```

Android8/API26+ ARM64 remains supported. Android13+ retains the test2 AGSL path; Android12 retains its existing captured-screen blur/SDF contour compatibility path. Blur is not claimed as arbitrary-neck refraction. This correction changes neither renderer tier nor optical equations.
