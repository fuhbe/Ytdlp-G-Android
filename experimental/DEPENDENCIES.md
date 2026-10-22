# Experimental core dependencies

Original copyright and license files remain in the prepared application.

| Component | Source | License |
| --- | --- | --- |
| YTDLnis core and database | https://github.com/deniscerri/ytdlnis/tree/b106c038aac63098e19ad30d8801bdf37433729c | GPL-3.0 |
| yt-dlp 2026.10.21 | https://github.com/yt-dlp/yt-dlp/tree/2026.10.21 | Unlicense; bundled components retain their own notices |
| yt-dlp EJS | https://github.com/yt-dlp/ejs | ISC |
| Android Python/FFmpeg/QuickJS packaging | https://github.com/junkfood02/youtubedl-android | GPL-3.0; native components have separate licenses |
| Python | https://www.python.org/downloads/source/ | PSF License |
| FFmpeg and codec libraries | https://ffmpeg.org/legal.html | GPL/LGPL as configured; bundled build includes GPL components |
| QuickJS | https://bellard.org/quickjs/ | MIT |
| NewPipe Extractor v0.26.5 | https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5 | GPL-3.0 |
| aria2 | https://github.com/aria2/aria2 | GPL-2.0-or-later |
| Termux components v0.118.3 | https://github.com/termux/termux-app/tree/v0.118.3 | GPL-3.0 |
| AndroidX, Compose, WorkManager, Room | https://cs.android.com/androidx/platform/frameworks/support | Apache-2.0 |
| Coil 2.7.0 | https://github.com/coil-kt/coil/tree/2.7.0 | Apache-2.0 |

The complete dependency declarations are retained in the prepared upstream
`app/build.gradle`. The prepared source archive distributed with each preview
contains the exact application source and modifications needed for that build.
Native dependency source/build recipes are available through the Android
packaging project above. The APK retains the bundled notices from Ytdlp-G and
the YTDLnis license; upstream assets, including postprocessors, remain credited.
