# Ytdlp-G for Android

A small video and playlist downloader for Android, with a translucent interface. Paste a link, inspect qualities that are actually available, and save regular files or one ZIP. The interface follows the device language in English, Brazilian Portuguese, and Spanish. On Android 13 and later, you can also choose the app language in system settings.

## Current status

This is an early Android build. The APK is built from source by GitHub Actions. Playlist inspection, native video and audio selection, high-quality MP3 conversion, foreground downloads, cancellation, ZIP output, and Android's folder picker are implemented. Broad site/device testing is still in progress; please report links that do not analyze or download.

The app bundles the [yt-dlp Android library](https://github.com/JunkFood02/youtubedl-android), Python, and FFmpeg through its dependencies. No separate Python installation is required. The APK can be large because it includes native runtimes. Android 8.0 or newer is required. Download only material you have permission to save and follow the source site's terms.

## Build

Open the project in Android Studio, or install Android SDK Platform 35 and run `./gradlew :app:assembleDebug`. Find the APK in `app/build/outputs/apk/debug/`. GitHub Actions uploads this APK as a workflow artifact. Debug builds carry a development certificate and may show an installation warning. This project does not yet offer a signed production release.

## License

GPL-3.0-only. See [LICENSE](LICENSE). Third-party components retain their respective licenses; see [NOTICE.md](NOTICE.md).
