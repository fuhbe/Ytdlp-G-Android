# Verified 0.6.6-test4 delivery

[Download the app package](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40024024762/artifacts/12532028276). Extract the ZIP and install **Ytdlp-G.apk**. The corresponding-source ZIP is separate and is not an installer.

## APK identity

- Version: **0.6.6-test4**, version code **69**.
- Package: `com.fuhbe.ytdlpg.minimal`; Android 8.0+; ARM64.
- APK size: **59,626,096 bytes**.
- APK SHA-256: `ee4714f57438782310f6179710d296345bc84439680222a4dbd4ab93b5abe43b`.
- Signing certificate SHA-256: `385e111ca244f67d4c283c22c344f1f34e7d56cfea0bbb1a1967b1766674a657`.
- Compiled source commit: `ecf036bfcb2a1c5fa9c57197783cd9fe5e9d8d32`.
- Successful build: [40024024762](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40024024762).

The actual published app, verification reports and matching device-test package were downloaded again. Outer artifact digests, ZIP CRCs, inner package checksums, signature, version and corresponding source were checked independently after download. [Receipt](DELIVERY.json) · [Original CI verification](VERIFIED.json).

## Cause and change

The old release-target function rejected the captured finger's vertical coordinate outside the slider. The original-code reproduction expected MP4 but retained MP3. Horizontal selection now remains valid when the same finger moves above or below the track. Once horizontal intent owns the pointer, events are consumed at Compose Initial before ordinary parent scrolling at Main. Android Compose also requests that its View parent not intercept when movement is consumed. Before horizontal recognition, normal vertical scrolling remains available; true cancellation still never commits a new destination.

Only three production gesture-related files and version metadata changed: `GlassSegmentOptics.kt`, `GlassSlidingSegments.kt`, `squareglass/DragGestureInspector.kt` and `minimal/app/build.gradle.kts`. The new `GlassDiagonalGestureTest` exercises real production Compose components. Earlier vertical-escape expectations are migrated explicitly in four existing test files; no unrelated tests or features are removed.

## Evidence

- **186 automated cases: 184 passed, two explicitly skipped rendering checks.**
- All 174 baseline case identities/outcomes remain accounted for, with three renamed obsolete gesture expectations and two migrated target-arithmetic checks.
- Twelve new diagonal cases include 216 width/direction/vertical-release matrix drags across software API configurations, plus cancellation, second pointers, exit/reentry, ancestor consumption, vertical scrolling, spring settlement, RTL and all four actual selectors.
- The SDK 31 dock regression also confirms the newly requested outside release behavior on Android 12's software configuration.
- The final APK passed 13 native ZIP/DEX bootstrap checks and the positive/negative archive regression controls. Real generated-media fixtures passed in CI.
- Seven native libraries, all 20 compared runtime/license entries and both wallpapers are byte-identical to test3.
- Ninety-one other protected baseline files are unchanged. Shared drawing, optics, deformation, lighting, caching, animations and download logic are additionally verified against the approved source.
- The host held-drag counter reports no additional compositions, source recordings or readbacks over 60 input samples. This is a host counter, not a phone frame-rate claim.

These are automated host/software results. No physical phone or emulator was available for this update, and no GPU/frame-rate result is claimed. Use the [Android 12 acceptance steps](DEVICE_CHECK.md) on the Redmi before promoting test4.

## Installation and preserved versions

This CI signing key differs from test3 and stable 0.6.4. If Android rejects the update, finish/export needed files before uninstalling an older differently signed build. Uninstalling clears private settings, queued jobs and partial files; already exported files remain outside the app.

Stable [0.6.4](https://github.com/fuhbe/Ytdlp-G-Android/releases/tag/v0.6.4) and the approved [0.6.6-test3 release](https://github.com/fuhbe/Ytdlp-G-Android/releases/tag/v0.6.6-test3) remain intact. Test3 was published without recompilation, with English release notes and [actual CI interface previews](https://github.com/fuhbe/Ytdlp-G-Android/tree/main/docs/screenshots/0.6.6-test3). Test4 remains a separate test build awaiting phone acceptance.
