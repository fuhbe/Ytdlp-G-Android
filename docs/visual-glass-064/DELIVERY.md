# Verified 0.6.4 visual test delivery

[Successful Actions run 39905174030](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39905174030)
built source commit `30a347f48cbf04ccf0973b3c5a2f6e869ecb81ed`.
The follow-up delivery record changes documentation only; it does not require another APK build.

## Download the actual application

[Download Ytdlp-G-0.6.4-Liquid-Glass-Test](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39905174030/artifacts/12481096555)

Sign in to GitHub, download the artifact ZIP and extract **Ytdlp-G.apk**.
The package also contains corresponding source, checksums, notices, before/after
screenshots and documentation. GitHub artifacts are subject to the run's retention
period. This remains a test build on `visual/liquid-glass-0.6.4`.

- APK size: **59,580,302 bytes**.
- APK SHA256: `dae229cff276bea3f9e0d51f7fa94275bf40d12be650987a6c36fb623ae9d334`.
- App: `com.fuhbe.ytdlpg.minimal`, versionName `0.6.4`, versionCode `64`, min API 26, ARM64.
- [Verification reports](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39905174030/artifacts/12480902203).
- [Matching instrumentation APK](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39905174030/artifacts/12480563621).
- [Comparison gallery, changed files, material/fallback policy and device instructions](REPORT.md).

## Checks completed after downloading

All three outer ZIP hashes match the GitHub artifact digests. ZIP integrity and
internal SHA256SUMS pass. The corresponding app source exactly matches the final
local implementation. Both exact original supplied PNG byte sequences are present
in the optimized APK, despite resource-name optimization. Backdrop license/NOTICE
and corresponding-source JAR are present. Runtime/ABI/signature inspection passes.
All 13 bootstrap constructor checks and the optimized R8 archive regression's
positive/negative controls pass. Application/test APK certificates match each other.

The published JUnit report contains **119 cases: 118 passed, zero failures/errors,
one explicit existing host-Skia child-shader skip**. All 89 baseline cases retain
their results; the original 57 functional tests are unchanged and passing. Actual
HTTP/HLS/DASH/FFmpeg media-fixture smoke checks pass. This is not a new live YouTube
transfer or a phone throughput claim.

The scope report confirms 82 protected files and all original test sources remain
unchanged, the exact allowed MainActivity material substitutions, unchanged download
dependencies, Backdrop 1.0.3, Android 8+ and the same app identity. There are 33 PNGs
in the downloaded screenshot tree: 21 API-matrix renders, eight updated UI previews
and four baseline comparison previews. These are actual software Compose renders
with sample metadata, not physical-device GPU screenshots.

Seven Android instrumentation scenarios compiled. **None executed on a device.**
No physical GPU, FPS, thermal, ANR, memory or exact-reference acceptance is claimed.
On Android 12 use the blur tier; the AGSL refraction tier requires Android 13+ and
supported acceleration. See REPORT.md for exact device acceptance commands.

## Signing and preservation

This APK certificate differs from both visual 0.6.3 and stable 0.6.2. Android will
reject an in-place update over those differently signed installations. Uninstalling
removes private queue/settings/partials; finish/export needed work first or use a
spare installation. This update does not change signing rules or migrate databases.

- New certificate SHA256: `35d6e13608735ab96e1b1c376900d106beeaffe00c12bc9e43d60b8372c1e5d4`.
- Visual 0.6.3 certificate SHA256: `4631c543a394e02adbbb61307358bad8ff4cd13f24db51574d749ee29f28873e`.
- Stable 0.6.2 certificate SHA256: `3e1df7424b2cb4589de10dc26ae20afe68eded24acc82b6a0298de2fdb98798b`.

Verified remotely after delivery: main remains `3064edecd8e3c1805ff629f6dcfd181c2b747479`,
visual 0.6.3 remains `acd71d2cfaddd8b5b626d6cfa8559e58937bb5d6`, and the latest stable
release remains **v0.6.2**. Neither existing branch nor the stable release was replaced.
