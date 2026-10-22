# Third-party software

Ytdlp-G for Android uses [youtubedl-android](https://github.com/JunkFood02/youtubedl-android) (GPL-3.0), which packages [yt-dlp](https://github.com/yt-dlp/yt-dlp) (Unlicense), Python (PSF License), and FFmpeg. The bundled FFmpeg binary enables GPL and version 3 components and is distributed under GPL-3.0-or-later. AndroidX, Jetpack Compose, Kotlin, and Gradle retain their own licenses.

The application source is available under GPL-3.0-only. No project trademarks are granted by this license.

The APK overrides the Android library's older yt-dlp resource with the official
yt-dlp 2026.10.21 zipapp. Its SHA-256 is pinned in scripts/prepare_runtime.py and
checked before packaging. The zipapp includes yt-dlp-ejs (Unlicense); the Android
library includes QuickJS 2025-04-26 (MIT). These license texts are included in
app/src/main/assets/licenses. Sources:

- https://github.com/yt-dlp/yt-dlp/tree/2026.10.21
- https://github.com/yt-dlp/ejs
- https://bellard.org/quickjs/
- https://github.com/yausername/youtubedl-android/tree/0.18.1

Coil is licensed under Apache-2.0. Material icon paths are from Google's Material
Icons project (Apache-2.0). Paparazzi is a development-only dependency
(Apache-2.0) and is not included in the application runtime.
