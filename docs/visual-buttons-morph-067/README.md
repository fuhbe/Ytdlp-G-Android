# Ytdlp-G 0.6.7-test1 — experimental buttons and morphing quality menu

**Experimental only.** Baseline: stable `v0.6.6`, versionCode 70. This branch builds `0.6.7-test1`, versionCode 71, package `com.fuhbe.ytdlpg.minimal`. No stable release or tag is replaced. The four approved sliders, navigation, wallpapers, engine, queue, media formats, storage and download callbacks are preserved.

## Interaction architecture

`ElasticGlassButton.kt` adapts the pinned Square `LiquidButton`/catalog material into a fixed semantic/click target with a separately painted, bounded, asymmetric elastic silhouette. Finger samples update render-phase state directly. A single reusable spring runs on release/cancel; no spring job is launched per MOVE. Labels/icons remain stationary and crisp. A passive Initial/Final pointer observer does not consume scrolling moves. Significant motion, an extra pointer, system cancellation or release outside cancels the action; ordinary taps, small jitter, long-press release, keyboard and accessibility retain the standard button callback. Foundation click indication is null; glass rim, pressure light and optics remain. Disabled controls retain their semantics.

Conversions are limited to the existing Analyze/Download action wrapper, folder chooser, update-yt-dlp button and clipboard icon. Existing callbacks are substituted exactly, checked by `verify_scope.py`. No engine or worker calls occur in the new components.

`GlassMorphMenu.kt` uses one focusable Popup window and one reversible spring progress for the real trigger mirror, a continuous path union (origin, hourglass neck, emerging panel), and progressively revealed crisp menu content. The body changes shape rather than merely scaling/fading. The original activity trigger is hidden only after the overlay has measured its screen-space anchor. Physical coordinates, visible-window bounds, RTL alignment and constrained menu height control placement. Invalid/clipped anchors close safely. The final panel is slim (232dp, 268dp for large text), scrollable and limited to the original supplied qualities/callback. Forming/closing items cannot dispatch actions, and a second stale click cannot choose twice. Back/outside closes without changing quality; state recreation starts closed.

The activity-only `GlassOverlaySource` is acquired/released once and frozen while the menu transitions; Popup contents never feed their own capture. Android 12 button optics reuse the existing cached mesh with a background-only sample acquired on demand once per background revision. No bitmap readback/capture occurs for each MOVE. The 2,000,000-pixel capture guard matches the existing selectors, so ordinary high-density phone buttons are not accidentally excluded; an xxhdpi regression verifies this path. The existing sliding selectors and optical code are byte-identical to v0.6.6.

## Android rendering capabilities

| System | Existing material reused | New shape behavior and truthful limits |
| --- | --- | --- |
| Android 13+, API 33+, supported GPU | Pinned Backdrop 1.0.3 AGSL rounded lens and blur | Elastic button silhouette/reflection; rounded open menu lens. The arbitrary liquid neck uses the actual captured screen with blur/rim, **not arbitrary-shape AGSL lens refraction**. |
| Android 12/12L, API 31–32 | RenderEffect blur; cached Canvas/HWUI mesh for buttons when capture is available | Morph body/neck samples the actual screen through blur; the menu neck is **not real optical lens refraction**. Existing selector mesh is unchanged. |
| Android 8–11, API 26–30 | Translucent tinted glass, rim and controlled pressure light | Same bounded shape/return spring and functional menu. No advanced optical lens claim. |
| Software rendering, low memory, power saver, shader/capture failure | Existing compatible material tier | Readable glass silhouette and intact actions. No real-time blur/refraction claim. Reduced motion settles directly. |

The pinned lens rejects `Outline.Generic`: it requires `CornerBasedShape`. Sending a custom neck to that API would crash; this build deliberately does not do that. No Kotlin, Compose, SDK, minimum Android, dependency or signing-policy upgrade was needed. Square/vendor license notices remain intact.

## Evidence and limits

See [DELIVERY.md](DELIVERY.md), [TESTING.md](TESTING.md), the workflow's verification artifact and [captures](captures/). Captures are actual application/Popup windows with **sample metadata**, rendered by Robolectric native Skia in software. They are not fabricated mockups, physical-device screenshots, a GPU benchmark or proof of the advanced shader path. Frame sequences/GIFs retain those provenance labels.

This is an adaptation of observable iOS interactions, not Apple's proprietary implementation or a pixel-identical/native-iPhone physics claim. User-provided stills and pinned Square source guide shape/continuity; no new source video was attached to this request.

## Build and licenses

Use JDK 21, Android SDK/build-tools 36 and the pinned Gradle wrapper:

```sh
python3 docs/visual-buttons-morph-067/verify_scope.py
bash minimal/gradlew -p minimal :app:assembleGithubRelease :app:testGithubDebugUnitTest :app:assembleGithubReleaseAndroidTest --no-daemon --max-workers=1
```

The experimental workflow packages `Ytdlp-G.apk`, exact corresponding source, SHA-256 sums, GPL-3.0 license and verification receipts. Existing optional owner signing is reused without publishing keys; otherwise the actual CI-default certificate is recorded. Corresponding source includes pinned runtime/native payload preparation and upstream source/license information. Read the root [NOTICE](../../NOTICE.md), [LICENSE](../../LICENSE) and `minimal/vendor/square/` notices before redistributing.
