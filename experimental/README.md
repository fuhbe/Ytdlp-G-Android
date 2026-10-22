# Ytdlp-G Android — YTDLnis core preview

This experimental application combines the Ytdlp-G Glass interface with the
actual YTDLnis download engine, Room database, persistent queue, foreground
workers, extraction fallback and history. It runs independently of the existing
Ytdlp-G app: the application ID is `com.fuhbe.ytdlpg.experimental`.

The existing application and releases are preserved. The experiment lives on
[`experimental/ytdlnis-core`](https://github.com/fuhbe/Ytdlp-G-Android/tree/experimental/ytdlnis-core).

## Features

- Video links and playlists, with qualities shared by accessible items.
- Original audio or MP3 from the best available source, encoded at 320 kbps.
- MP4 video: select the requested resolution first, then copy H.264/AAC streams
  or convert other codecs using FFmpeg. Conversion uses H.264 CRF 18 and AAC
  320 kbps; it cannot recover detail missing from the source and can take time
  on a phone, especially at high resolutions.
- Regular files or a verified ZIP archive, saved through MediaStore or an
  Android document provider. Broad storage access is not required by the Glass
  download flow.
- Persistent batch records, genuine queue progress, cancellation and error logs.
- English, Brazilian Portuguese and Spanish in the Glass interface.
- Bundled Python, FFmpeg, QuickJS, yt-dlp and the JavaScript challenge solver.

The reference Glass layout is reused directly from the current application.
This is an integration preview for device validation, not a claim that changing
the download engine guarantees that YouTube will accept every request. Access
restrictions, authentication, unavailable media and extractor changes can still
produce errors. A visible error must never be counted as a saved download.

## Build

Requirements: JDK 21, Python 3, Android SDK platform 36 and build tools 36.0.0.

```sh
python3 experimental/prepare.py
cd experimental/build/upstream
bash gradlew :app:assembleIzzyDebug :app:testIzzyDebugUnitTest
```

`prepare.py` verifies the upstream source archive checksum, applies explicit
patches, copies the Glass layout and overlays, and verifies the bundled yt-dlp.
It fails if an expected patch no longer matches. `upstream.json` pins the exact
source revision and license. The `izzy` flavor bundles the native runtime;
users do not need to install another downloader or Python.

The experimental GitHub Actions workflow builds the APK and uploads it together
with SHA-256 checksums, the complete prepared application source and test
reports. The source archive excludes generated build outputs and local settings.

## Verification

```sh
python3 scripts/check_translations.py
python3 experimental/verify_mp4.py experimental/build/runtime/raw/ytdlp
python3 scripts/smoke_runtime.py experimental/build/runtime/raw/ytdlp
python3 scripts/check_apk.py path/to/compiled.apk
```

The conversion tests use real HTTP fixtures, yt-dlp, FFmpeg and FFprobe. They
check stream codecs, dimensions, duration, lossless copying of compatible video,
MP3 conversion and retention of the source after a conversion failure.

Device acceptance checks cover a single video, audio, MP3, a short playlist,
ZIP, folder selection, cancellation, backgrounding and relaunch during a batch.
The preview must pass these checks on a device before it replaces the current
release. A successful build or host conversion test alone is not evidence that
a YouTube download completed on Android.

## Source and licensing

This is a modified version of [YTDLnis](https://github.com/deniscerri/ytdlnis),
copyright Deniscerri and YTDLnis contributors, distributed under GPL-3.0.
Original notices, source and license are retained. See
[`upstream.json`](upstream.json) for the exact revision. The changes add the
Glass launcher, queue/export bridge, MP4 postprocessor and pinned runtime.
The original project's names and logos are not presented as this app's brand.

The prepared project and APK include the YTDLnis license and notice alongside
the existing dependency notices. Dependency source links are recorded in
[`DEPENDENCIES.md`](DEPENDENCIES.md). No reverse engineering is required to use
this publicly available source.
