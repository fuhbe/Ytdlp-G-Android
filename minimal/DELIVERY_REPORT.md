# Ytdlp-G 0.6.0 — implementation and verification report

This is an installable ARM64 test build for Android 8.0+. Its final installed
name is Ytdlp-G; its own application ID is com.fuhbe.ytdlpg.minimal.

## Implemented

Three focused areas (Home, Downloads, Settings), responsive light/dark Liquid
Glass styling, YouTube URL/multiple-link analysis, selectable playlists, MP3
from the best source, MP4 with real resolution options up to 4K and an explicit
lower-resolution fallback, streaming ZIP for two or more selections, native
folder selection, SQLite queue, partial-file continuation, process-tree
cancellation, foreground notifications and opening completed files.

The app reuses 19 pinned YTDLnis runtime/model files, plus an application-private
storage helper. GPLv3 notices and corresponding source are included. The original
projects and previous editions are preserved.

## Verified here

- Optimized Android release build completed successfully.
- All 14 automated tests passed: 4 policy, 4 metadata/storage/persistence, 6 UI.
- UI checks cover 320, 411, 720 and 800 dp layouts, landscape, dark/light mode,
  enlarged fonts, scroll reachability, playlist selection/ZIP availability and
  stored error/retry controls. Rendered fixtures use sample data only.
- Seven real host HTTP/HLS/FFmpeg checks passed: metadata formats, selected MP4
  resolution, lower-resolution fallback, MP3 conversion/progress, compatible
  stream copying, incompatible stream conversion, and conversion failure retaining
  the source. These are generated-media downloads, not simulated success results.
- APK signature, exact installed label and application ID, no broad storage
  permission, ARM64 Python/FFmpeg/QuickJS, bundled EJS and licenses verified.
- Corresponding source ZIP integrity and required build inputs verified.

## Errors found and addressed

- Old compiler daemons exhausted build memory: stopped the old daemons, limited
  Kotlin memory and Gradle workers, then rebuilt successfully.
- Compose window-capture API timed out under Robolectric: fixture rendering now
  draws the actual Android view to a Canvas; all UI interaction checks pass.
- The first clean GitHub run lacked an experimental-directory dependency: moved
  the MP4 postprocessor and its tests into this module and verified required
  repository inputs.
- Document-provider folder queries/validation could block the UI: moved them to
  IO dispatchers and disabled duplicate queue submissions while saving a job.
- Merged native stderr could hide the actual failure: the bridge now preserves
  the real ERROR line so HTTP 403 and storage failures receive meaningful messages.

## Remaining acceptance checks

The environment's proxy rejected a direct YouTube metadata request with
'Tunnel connection failed: 403 Forbidden'. No network bypass was attempted.
YouTube single/playlist MP3/MP4 downloads therefore remain unverified on Android.
Physical-device checks are also needed for persisted folder grants, background
service behavior, native pause/resume/cancel, process death, document-provider
ZIP export, real keyboard/system bars, disk-full errors and opening saved files.
A successful build and host tests are not a claim that those device checks passed.

The frost is a lightweight static gradient/transparency treatment, without
continuous background blur. The default binary targets ARM64; other ABIs are
supported through the documented Gradle build option. The release variant uses
a test signing key and is intended for device acceptance testing.

## Delivery

- APK path: minimal/build/distribution/Ytdlp-G.apk
- Size: 56,149,833 bytes (53.55 MiB)
- SHA256 (verified GitHub APK): 6edf144e7d762e017ade7b3b9f3795ed57a4b2d667ce078f7d3438ff6656d769
- Exact corresponding source: minimal/build/distribution/Ytdlp-G-source.zip
- Main files: minimal/app/src/main/java/com/fuhbe/ytdlpg/simple/
- Native-source preparation: minimal/prepare.py and minimal/upstream.json
- Build and engine update instructions: minimal/README.md
- CI workflow: .github/workflows/minimal-glass.yml

Code commit: ea670680c779b8551b27d068ae5ddb8e63905d7a.
GitHub build completed successfully. Both published artifact ZIP hashes, internal APK/source hashes, the APK signature/runtime/label and all 14 test reports were downloaded and verified.

Download: https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39848468570/artifacts/12456826567
Verification reports: https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39848468570/artifacts/12455304918

The download is a GitHub Actions ZIP; extract Ytdlp-G.apk to install. GitHub sign-in may be required.
