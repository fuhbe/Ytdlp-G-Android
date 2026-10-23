<p align="center"><img src="docs/branding/icon.png" width="88" alt="Ytdlp-G app icon"></p>

# Ytdlp-G for Android

Download YouTube videos, music and playlists with a blue Liquid Glass interface. Paste a link, choose **MP3** or **MP4**, and save individual files or a playlist ZIP.

**[Download Ytdlp-G.apk](https://github.com/fuhbe/Ytdlp-G-Android/releases/download/v0.6.2/Ytdlp-G.apk)** · [Release notes](https://github.com/fuhbe/Ytdlp-G-Android/releases/tag/v0.6.2) · [Report an issue](https://github.com/fuhbe/Ytdlp-G-Android/issues)

Current release: **0.6.2** · **Android 8.0+** · **ARM64** · **GPL-3.0-only**

## What you can do

- Analyze a video, a playlist or multiple YouTube links, with a preview before downloading.
- Save **MP3** using the best available audio source and high-quality VBR encoding.
- Save **MP4** at an available resolution, up to 2160p. Compatible H.264/AAC streams are copied or remuxed; other codecs are converted when needed.
- Select playlist entries and export separate files or one ZIP. ZIP is available when at least two items are selected.
- Keep successful items when another item fails. Review each reason and retry eligible failures without repeating completed downloads.
- Pause, resume or cancel jobs, monitor progress by stage, and open saved files from the download manager.
- Choose a destination folder and switch between light and dark themes.

Python, yt-dlp, FFmpeg, FFprobe and the JavaScript runtime are included. No separate Python installation is required.

## Interface

These are renders of the application's actual Compose screens with **sample metadata and queue states**, not photos of a live download. They show the Brazilian Portuguese interface included in this release. Thumbnails use the app's placeholder in these examples.

| Video | Playlist | Download manager |
|:---:|:---:|:---:|
| <img src="docs/screenshots/single-video.png" width="260" alt="Single-video preview with MP3 and MP4 choices"> | <img src="docs/screenshots/playlist.png" width="260" alt="Playlist selection with MP3 audio options"> | <img src="docs/screenshots/downloads.png" width="260" alt="Running, paused and completed jobs in the download manager"> |

<details>
<summary>Dark theme</summary>

<img src="docs/screenshots/dark-theme.png" width="300" alt="Download manager in dark theme">

</details>

## Get started

1. Download **Ytdlp-G.apk** from [Releases](https://github.com/fuhbe/Ytdlp-G-Android/releases/latest). The source ZIP is for developers.
2. Install it on an ARM64 Android phone. If Android asks, allow installation from the app you used to download the APK.
3. Paste or share a YouTube link and tap **Analisar link** (Analyze link).
4. Choose MP3 or MP4, an available resolution, and a destination. Select playlist entries and ZIP if desired.
5. Tap **Baixar** (Download), then follow the job in **Downloads**.

Android 10+ can save directly to `Downloads/Ytdlp-G`. On Android 8/9, choose a folder first. MP4 conversion may take longer than downloading, especially for high-resolution VP9/AV1 video; lowering the selected resolution reduces processing work. Conversion does not improve the original source quality. Network conditions, source restrictions and device background policies can affect a job.

### Updating an older preview

This is the **same APK** as successful Actions run [39878394553](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39878394553). If you already installed and tested that 0.6.2 APK, you already have this version.

The certificate differs from 0.6.1, so Android refuses an in-place update from that preview. Finish/export what you need before uninstalling it: uninstall removes its private queue, settings and partial files. Already exported Downloads/folder files remain. See the [installation and verification record](minimal/DELIVERY_REPORT.md).

## What's new in 0.6.2

- Independent playlist results: unavailable items no longer stop the rest of the playlist.
- A useful ZIP from successful items, with a warning for anything not downloaded.
- Separate download, MP4 processing, integrity-check and export stages; unknown work uses indeterminate progress.
- Bounded retries, preserved partials and validated-output reuse after a destination failure.
- Conservative automatic fragment and item concurrency based on memory, connection, power and temperature.
- A guarded native-output reader that prevents a reproduced callback/pipe deadlock.

The exact GitHub build passed **57 automated tests**, real generated-media download/conversion checks, archive regressions and final APK inspection. The user also reported a successful video download/conversion retry on a Redmi 10 Prime running Android 12. This is one device report; it does not establish compatibility with every device or link. [Detailed results and performance scope](minimal/TURBO_REPORT.md).

## Source and development

The released application is in **[minimal/](minimal/README.md)**. Earlier application editions and design experiments remain in the repository; they are not the build target for this APK.

Use JDK 21, Python 3, Android SDK 36 and build tools 36.0.0:

```sh
bash minimal/gradlew -p minimal :app:assembleGithubRelease :app:testGithubDebugUnitTest --max-workers=1
```

The APK is written to `minimal/app/build/outputs/apk/github/release/`. Full packaging, runtime preparation, verification and device diagnostic instructions are documented in [minimal/README.md](minimal/README.md), [VERIFICATION.md](minimal/VERIFICATION.md) and [LOGCAT.md](minimal/LOGCAT.md).

Release 0.6.2 reuses an already verified artifact; its publication workflow does not rebuild or re-sign the APK. [Release provenance](docs/releases/v0.6.2.md) includes the source commit and checksum. [Screenshot rendering instructions](docs/screenshots/README.md) explain how the interface previews are produced.

## License and credits

GPL-3.0-only. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md). The native execution core is reused from [YTDLnis](https://github.com/deniscerri/ytdlnis); extraction uses [yt-dlp](https://github.com/yt-dlp/yt-dlp). Python, FFmpeg, FFprobe, QuickJS and other components retain their respective licenses. Corresponding source and license notices accompany the release.

Download only content you have permission to save and follow the source site's terms. Restricted or unavailable content is reported as a failure.
