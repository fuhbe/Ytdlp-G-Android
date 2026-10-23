# Ytdlp-G 0.6.2 — verified GitHub delivery

Independent playlist outcomes, partial-success ZIP and supervised real MP4 processing are implemented. The Liquid Glass interface is preserved. See [TURBO_REPORT.md](TURBO_REPORT.md) for confirmed defects, changed implementation and remaining acceptance checks.

- Code commit: `4893e319b620436d8089ef43dde05c3ada52c9df`.
- Successful Actions run: https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39878394553
- APK and corresponding source: https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39878394553/artifacts/12469622631
- Exact test results, mappings and regressions: https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39878394553/artifacts/12469223108
- Matching instrumented test package: https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39878394553/artifacts/12470085925
- Final filename: `Ytdlp-G.apk`; version 0.6.2 / code 62; application ID `com.fuhbe.ytdlpg.minimal`; ARM64.
- APK size: 56,230,904 bytes.
- APK SHA256: `735b4ac44a0aad52816df1f2ebcf3a637fb901ad690ee5b395ffe468a0349387`.

Download and extract the main Actions artifact ZIP, then install Ytdlp-G.apk. GitHub sign-in may be required. The test APK is a separate diagnostic runner, not the application installer.

## Independently checked downloaded artifacts

All 3 outer artifact digests and ZIP CRCs passed. Internal APK/source/test checksums, exact corresponding editable source, package/version/label, APK signature, bundled runtimes/EJS/licenses, all 57 JUnit results and all 13 reflective ZIP constructors passed. The real archive regression passed its positive checks and reproduced the expected old R8 failure in its negative control.
The main and instrumented APK signing certificates match. Four instrumented scenarios were built but NOT EXECUTED; no device or emulator result is claimed. Native phone startup, live YouTube, background/manufacturer behavior and the user's long conversion still require the Redmi test. See DEVICE_TESTS.md and LOGCAT.md.

## Measured host results

Three repetitions per case; generated six-second 360p media on the GitHub host and a local server. Totals include analysis, transfer/postprocessing, validation and a local export. Negative changes are faster; positive changes are slower. These are not Android, live Internet, YouTube or Snaptube measurements.

| Case | Baseline median seconds | Optimized median seconds | Observed change |
|---|---:|---:|---:|
| mp3 | 2.861 | 2.869 | +0.3% |
| compatible-mp4 | 2.850 | 2.858 | +0.3% |
| convert-mp4 | 3.471 | 3.203 | -7.7% |
| hls-N1 | 3.909 | 3.903 | -0.2% |
| hls-N4 | 3.160 | 3.167 | +0.2% |

Controlled HLS N=1 → N=4 total-time change: -18.9%. The fixture deliberately adds 80ms per segment request. Raw JSON and performance-report.md in the artifact include first data, observed throughput, stage timings, CPU, sampled memory, output sizes and successful full decoding. No universal speed gain is claimed.

## Installation and data

This APK certificate SHA256 is `3e1df7424b2cb4589de10dc26ae20afe68eded24acc82b6a0298de2fdb98798b`. The 0.6.1 certificate is `cc4e42ae6f8285509dea9f29efab584853532e85d509f8919a02f3fb3cadff9b`.
The keys differ, so Android will refuse an in-place update. Do not uninstall while needed private partials or queue data remain. First finish/export what is needed: uninstall removes private queue/settings/partials, while already exported Downloads/SAF files remain. The code migration is nondestructive, but it cannot bypass Android signing rules. Future stable updates require an owner-controlled production signing key.

This delivery record is a documentation-only update after artifact verification and does not trigger another compilation. The corresponding-source archive retains the original pre-verification delivery-status snapshot. Other application editions and branches are preserved.

## Changed files in the implementation commits

```text
.github/workflows/minimal-glass.yml
minimal/DELIVERY_REPORT.md
minimal/DEVICE_TESTS.md
minimal/LOGCAT.md
minimal/README.md
minimal/TURBO_REPORT.md
minimal/VERIFICATION.md
minimal/app/build.gradle.kts
minimal/app/proguard-rules.pro
minimal/app/src/androidTest/assets/convert.webm.b64
minimal/app/src/androidTest/assets/copy.mp4.b64
minimal/app/src/androidTest/java/com/fuhbe/ytdlpg/simple/NativePipelineTest.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/ActivityGuard.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/AdaptiveAdmission.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/AppModel.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/BatchRunner.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/Diagnostics.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/DownloadCache.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/DownloadRequest.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/Engine.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/ExportStreams.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/Failures.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/MainActivity.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/MediaValidator.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/Models.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/ProcessOutput.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/QueueActions.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/QueueWorker.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/Storage.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/Store.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/TransferProtocol.kt
minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/TurboPolicy.kt
minimal/app/src/test/java/com/deniscerri/ytdl/core/stream/LegacyStreamProcessExtractor.kt
minimal/app/src/test/java/com/deniscerri/ytdl/core/stream/StreamReaderTest.kt
minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/BatchRunnerTest.kt
minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/DownloadCacheTest.kt
minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/ExportRecoveryTest.kt
minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/HostPipelineTest.kt
minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/TransferProtocolTest.kt
minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/TurboPolicyTest.kt
minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/TurboUiTest.kt
minimal/benchmark.py
minimal/benchmarks/baseline_glassmp4.py
minimal/compare_benchmarks.py
minimal/integration_fixture.py
minimal/package.py
minimal/plugins/glassmp4/yt_dlp_plugins/postprocessor/glassmp4.py
minimal/prepare.py
minimal/runtime/StreamProcessExtractor.kt
minimal/runtime_patch.py
```
