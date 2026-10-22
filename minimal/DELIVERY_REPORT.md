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

## Verified local and GitHub builds

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

The GitHub artifact was downloaded and verified independently: both outer
artifact digests, internal APK/source checksums, ZIP CRCs, corresponding source,
all 25 XML results, all 13 DEX checks and all archive/R8 controls passed.
Physical-device acceptance and live YouTube access are still pending.

## Verified delivery

- Code commit: `fd3aaa2070bee28beee56e82166f68a3fbe34988`.
- GitHub Actions run: https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39866020464
- APK and corresponding source: https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39866020464/artifacts/12464426250
- Test reports, ZIP/DEX regressions and exact R8 mapping: https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39866020464/artifacts/12463478986
- Final filename: `Ytdlp-G.apk`.
- APK size: 56,190,405 bytes.
- APK SHA256: `d8e11831642d873a9ebad35a7062b59e5179fda826462edbb0521abb7e6e86c7`.

GitHub sign-in may be required. Download and extract the Actions ZIP, then install
`Ytdlp-G.apk`. The included `Ytdlp-G-source.zip` is corresponding source, not an
installer. Existing editions and branches have been preserved.

The downloaded 0.6.1 test APK has a different signing certificate from the
previously published 0.6.0 APK. Android will refuse an in-place update between
those two test keys. Finish/export anything needed, then uninstall 0.6.0 before
installing 0.6.1. Uninstalling removes private queue/settings/partial files;
already exported files in Downloads or the selected external folder remain.
Production distribution needs a stable private signing key.

## Changed files

The code commit changes the following paths. This delivery report receives a
separate documentation-only update after artifact verification; it does not
trigger another compilation.

```
.github/workflows/minimal-glass.yml
minimal/CRASH_FIX.md
minimal/DELIVERY_REPORT.md
minimal/LOGCAT.md
minimal/README.md
minimal/VERIFICATION.md
minimal/app/build.gradle.kts
minimal/app/proguard-rules.pro
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/AppModel.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/BundledRuntime.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/Diagnostics.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/Engine.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/MainActivity.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/Store.kt
minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/AnalyzeActionTest.kt
minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/GlassUiTest.kt
minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/RuntimeSafetyTest.kt
minimal/dex_bootstrap.py
minimal/package.py
minimal/regression/0.6.0-bootstrap-dex.json
minimal/regression/ZipBootstrapProbe.java
minimal/regression_zip.py
minimal/verify.py
```
