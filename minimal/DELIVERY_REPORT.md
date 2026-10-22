# Ytdlp-G 0.6.1 — runtime bootstrap correction

The release fixes an optimized ZIP initialization defect shared by Analyze link
and Check/update yt-dlp. See [CRASH_FIX.md](CRASH_FIX.md) for the reproduced cause,
changed files, positive/negative archive tests and APK DEX guard. The new version
keeps the Liquid Glass interface and existing download functionality.

[LOGCAT.md](LOGCAT.md) explains phone acceptance and diagnostic collection.
Automated results for the exact build, the R8 mapping and regression reports are
published by `.github/workflows/minimal-glass.yml` in **Minimal-verification**.
The **Ytdlp-G-Android-Minimal** artifact contains `Ytdlp-G.apk`, corresponding
source and instructions. Confirm the Actions run completed successfully before
using its artifacts. A physical Redmi test is still needed; no such success is
claimed here.

## Verified local build

- Optimized ARM64 release 0.6.1 (version code 61) built successfully.
- All 25 automated tests passed: 4 policy, 4 storage/persistence, 9 glass UI,
  4 analysis/update action and 4 runtime safety tests. Three loading-state cases
  use API 26, 31 and 35; the action/runtime regressions use API 31.
- All 13 registered ZIP implementations in the final DEX are concrete and retain
  public no-argument constructors.
- Real bundled Python and FFmpeg archives pass the unshrunk and corrected R8
  checks. The negative R8 control reproduces ExceptionInInitializerError.
- Seven real HTTP/HLS/FFmpeg host checks passed, including MP3, MP4 resolution
  fallback, stream copy/conversion and original preservation on failure.
- APK signature, application ID/name, bundled engine/EJS, Python, FFmpeg,
  FFprobe, QuickJS and license entries verified.

These local results must be checked again against the GitHub artifact. The
workflow records its own exact results and mapping. Physical-device acceptance
and live YouTube access are still pending.
