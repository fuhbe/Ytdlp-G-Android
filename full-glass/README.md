# Ytdlp-G Glass — complete YTDLnis application

This edition builds the **complete YTDLnis application** with a Liquid Glass
resource theme. It uses the original launcher, navigation, screens, format
renderer, download cards, database, queue, workers and settings.

It does **not** launch the simplified Ytdlp-G interface or use its download
bridge, quality model or custom conversion layer. The original YTDLnis code
controls video/audio options and conversion behavior.

## What changes

- Pearl and midnight glass palettes with readable text in light and dark mode.
- Translucent cards, fine edges, gentle highlights, rounded controls and a
  floating navigation bar.
- Glass presentation for native toolbars, tabs and preference rows.
- App label/package identity: **Ytdlp-G Glass** / `com.fuhbe.ytdlpg.fullglass`.

The Glass distribution is `0.5.0-glass.1`; the app retains the native numeric
version `1.9.1.1` so the original updater's version parser remains compatible.
No update algorithm or channel has been replaced.

The glass effect uses drawable gradients and transparency. It does not add
continuous background-blur processing or replace the original interaction code.

## What is preserved

All original application files are retained, including:

- Video/audio links, playlists and the original quality/format selection.
- Download cards, multi-selection, queue, history and background work.
- Audio extraction, conversion and all original postprocessing options.
- Subtitle, thumbnail, metadata and chapter controls.
- Cookies, authentication helpers, templates and the terminal.
- Scheduling, observed sources, storage and network settings.
- Engine/package updates, advanced settings and native permissions/onboarding.
- Every supplied language, including Brazilian Portuguese, English and Spanish.
- Accent, light/dark, high-contrast and app-icon choices.
- Original 32-bit and 64-bit native ABIs.

The original upstream update settings and project links are retained. This is a
separate GPL derivative and its independently signed packages are distributed
from the Ytdlp-G repository; an upstream YTDLnis APK is a different application.

## Build and verify

JDK 21, Android SDK platform 36/build tools 36.0.0 and Python 3 are required.

```sh
python3 full-glass/prepare.py
python3 full-glass/audit.py
cd full-glass/build/upstream
bash gradlew :app:assembleIzzyDebug :app:testIzzyDebugUnitTest
```

`prepare.py` downloads and verifies the exact full source archive pinned in
`upstream.json`. It copies every original file and applies the presentation
changes. The `izzy` flavor bundles Python, FFmpeg and QuickJS so another runtime
application is not required. The pinned yt-dlp includes its JavaScript solver.

`audit.py` rejects missing original files, changed native logic, changed settings,
changed navigation/actions, removed layout controls or changed nonvisual widget
attributes. The sole original Kotlin modification is the displayed header name.

The native resource tests inflate the real upstream format cards, call its
unmodified renderer, check that MP4/1080p stays a video label, exercise the
native selection state, check text contrast and high-contrast behavior, and
render light/dark widget fixtures. These fixtures are resource tests, not
screenshots of a completed download.

GitHub Actions publishes the APK, exact prepared source, original GPL license,
source audit, checksums, test reports and rendered widget fixtures. Installation
and live downloads still require device acceptance testing.

## License and attribution

Based on [YTDLnis](https://github.com/deniscerri/ytdlnis), copyright Deniscerri and
YTDLnis contributors, under GNU GPL-3.0. Original notices, all source files,
assets and license remain in the prepared project and corresponding source
archive. Runtime dependency notices are included with the APK.

Native runtime packaging and corresponding build recipes are provided by
[youtubedl-android](https://github.com/junkfood02/youtubedl-android): library
0.18.1, FFmpeg 0.17.2 and aria2c 0.18.1, as declared in the retained upstream
Gradle file. Python uses the PSF license, QuickJS the MIT license, yt-dlp the
Unlicense, its EJS component the ISC license, and the bundled FFmpeg build
includes GPL components. Their notices and the applicable GPL text are packaged
with the app. Source links: [Python](https://www.python.org/downloads/source/),
[QuickJS](https://bellard.org/quickjs/), [FFmpeg](https://ffmpeg.org/legal.html),
[yt-dlp](https://github.com/yt-dlp/yt-dlp/tree/2026.10.21),
[EJS](https://github.com/yt-dlp/ejs), [aria2](https://github.com/aria2/aria2).

The current Ytdlp-G app and the earlier experimental edition are preserved on
their existing branches. This edition lives on
[`design/full-ytdlnis-glass`](https://github.com/fuhbe/Ytdlp-G-Android/tree/design/full-ytdlnis-glass).
