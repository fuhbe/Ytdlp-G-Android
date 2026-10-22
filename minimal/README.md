# Ytdlp-G — Minimal Liquid Glass edition

A separate Android application for YouTube links and playlists, inspired by the
provided three-screen reference. The installed name and final APK filename are
**Ytdlp-G** and **Ytdlp-G.apk**. Application ID: `com.fuhbe.ytdlpg.minimal`.
The desktop application, previous Android app and complete YTDLnis edition are
preserved. This edition does not migrate their private databases.

## Interface and behavior

- Home: paste one or multiple HTTPS YouTube links, analyze, preview, choose MP3
  or MP4, select a destination, and download.
- Playlists: flat metadata first, select all or individual available items, and
  expand a virtualized list. Only the reference video's formats are probed at
  analysis time; the remaining items are resolved freshly when downloading.
- MP3: best available audio source, FFmpeg extraction and high-quality VBR.
  Source quality is the upper bound; no fixed 320 kbps promise is made.
- MP4: real available resolutions, capped at 2160p. "Best" selects the best up
  to 4K. A selected height is a ceiling: playlist items without it use their best
  lower resolution. H.264/AAC are preferred at the selected resolution and copied
  when possible; other codecs are converted with FFmpeg. Conversion takes CPU
  time and cannot increase source resolution or detail.
- ZIP appears only with at least two selected files. It streams with a fixed
  buffer, reports byte-based packing progress and verifies the archive's entries
  and CRC. Original temporary files are retained. Cancellation deletes only an
  incomplete archive/export, not the originals.
- SQLite queue, WorkManager foreground execution, per-job pause/resume/cancel,
  retries, saved URIs, background notifications and open-file actions.
- Pausing kills the complete registered yt-dlp/FFmpeg process tree and preserves
  `.part` files. Resume uses `--continue`; unsupported server resume can restart
  a stream. Packaging restarts from completed original files.
- Native document-tree picker, persisted grants and collision-safe filenames.
  Android 10+ can use Downloads/Ytdlp-G by default. Android 8/9 require choosing a
  folder. No broad storage permission is requested.
- Portuguese interface, automatic/manual light and dark themes, restrained
  glass gradients, native touch feedback and short content/progress animations.
  Static luminous backgrounds approximate frost without continuous GPU blur.
- Only Home, Downloads and Settings are exposed. Terminal, custom commands,
  SponsorBlock, trimming, schedules, channel monitoring and advanced metadata
  controls are absent from this application.

The native execution, runtime packages, update mechanism and process-tree
termination are reused from the pinned YTDLnis source. Only nineteen required
runtime/model files are imported. The launcher, queue, persistence, storage and
UI are specific to this application. Runtime preparation records source hashes.

## Build

Use JDK 21, Android SDK 36/build tools 36.0.0 and Python 3. The wrapper uses
Gradle 8.13. From this repository:

```sh
python3 minimal/prepare.py
bash minimal/gradlew -p minimal :app:assembleGithubRelease :app:testGithubDebugUnitTest --max-workers=1
python3 minimal/smoke.py
python3 minimal/package.py
ANDROID_HOME=/path/to/android-sdk python3 minimal/verify.py minimal/build/distribution/Ytdlp-G.apk
```

The default APK is **ARM64**, reducing bundled runtime size. Other architectures
can be built with `-Pabis=arm64-v8a,armeabi-v7a,x86_64,x86`. The minimum Android
version is 8.0 (API 26). The optimized release variant is signed with a test key
for installation; a stable private signing key is required for production updates.
No Python or separate download app needs to be installed by the end user.

Settings offers an explicit yt-dlp update using the original upstream updater.
Pause/finish queued downloads before updating. Application updates are separate
from engine updates. `scripts/prepare_runtime.py` pins the bundled initial engine
and checksum; `minimal/upstream.json` pins the original GPL runtime source.

## Storage policy

Downloads use app-private working directories and publish completed files through
MediaStore or a selected document provider. Partial exports are removed on errors.
Already exported files are not replaced or deleted. Completed temporary copies
may be removed explicitly in Settings; paused/error partials remain available.
The chosen destination is captured when a job enters the queue. If a persisted
folder becomes inaccessible, choose a new folder and create a new download.
Document providers may be remote or report no free-space estimate; write errors
are caught and reported. The initial internal free-space check is a lower bound,
not a guarantee that a large video will fit.

## Verification and limitations

See [VERIFICATION.md](VERIFICATION.md). Host HTTP/HLS downloads are real but do
not establish successful YouTube downloads or Android foreground-service behavior
on a physical device. UI fixtures use example metadata for rendering only.
Access restrictions are reported; this app does not bypass private-media or
authentication protections. YouTube compatibility also depends on the current
extractor, network and access permitted for the specific video.

## License and source

GNU GPLv3. Runtime components originate from
[YTDLnis](https://github.com/deniscerri/ytdlnis), copyright Deniscerri and
contributors, pinned at `b106c038aac63098e19ad30d8801bdf37433729c`.
Their notices and GPL text accompany the APK and exact corresponding source ZIP.
Changes include a separate simplified interface, queue/export layer and the
GlassMP4 FFmpeg postprocessor. No original project authorship is claimed.

Native Python 3.14.6 comes from
[ytdlnis-packages](https://github.com/deniscerri/ytdlnis-packages).
FFmpeg 0.17.2 and QuickJS from library 0.18.1 come from
[youtubedl-android](https://github.com/junkfood02/youtubedl-android), with native
build recipes and corresponding source references there. Python is PSF licensed;
QuickJS MIT; yt-dlp Unlicense; EJS ISC; the bundled FFmpeg includes GPL components.
Dependency notices are in `assets/licenses`.
