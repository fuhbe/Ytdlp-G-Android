# Ytdlp-G 0.6.6 — Android application

This directory contains the application shipped as **Ytdlp-G.apk** in the official [v0.6.6 release](https://github.com/fuhbe/Ytdlp-G-Android/releases/tag/v0.6.6). Application ID: `com.fuhbe.ytdlpg.minimal`; version code: **70**.

## Requirements and behavior

Android 8.0+ (API 26), ARM64. Python, yt-dlp, FFmpeg, FFprobe and QuickJS are included. Videos and playlists can be saved as MP4, best-source high-quality VBR MP3, separate files or a multi-item ZIP. Persistent queue controls, per-item failure handling and destination-folder behavior are preserved.

The interface controls are English; preserved runtime components can emit messages in their original language. Light/dark glass rendering adapts to Android version, hardware rendering, available memory and power-saving mode. [Screenshots](../docs/screenshots/0.6.6/README.md) use real application windows with sample metadata.

## Reproducible build

Check out the version tag. Use JDK 21, Python 3, Android SDK 36 and build tools 36.0.0. Set `JAVA_HOME` and `ANDROID_HOME` for your installation.

```sh
python3 docs/releases/0.6.6/verify_scope.py
bash minimal/gradlew -p minimal :app:assembleGithubRelease :app:testGithubDebugUnitTest :app:assembleGithubReleaseAndroidTest --max-workers=1
python3 minimal/smoke.py
python3 minimal/verify_mp4.py minimal/app/build/generated/runtime/res/raw/ytdlp
python3 minimal/package.py
python3 minimal/verify.py minimal/build/distribution/Ytdlp-G.apk
python3 docs/releases/0.6.6/verify_delivery.py
python3 minimal/dex_bootstrap.py minimal/build/distribution/Ytdlp-G.apk --report minimal/build/dex-bootstrap.json
python3 minimal/regression_zip.py minimal/build/distribution/Ytdlp-G.apk
```

The release APK is produced under `app/build/outputs/apk/github/release/` and copied without changing its bytes to `build/distribution/Ytdlp-G.apk`. Packaging provides corresponding source, pinned runtime preparation inputs and bundled license notices.

The current scope check compares all implementation files against the device-approved source. Only version/signing metadata and explicitly enumerated English UI literals differ. Existing test identities are preserved, with translated selectors and additional real-window capture cases.

## Signing

To use a persistent owner-controlled signing key, supply these environment variables:

- `YTDLPG_KEYSTORE_PATH`
- `YTDLPG_KEYSTORE_PASSWORD`
- `YTDLPG_KEY_ALIAS`
- `YTDLPG_KEY_PASSWORD`

The stable workflow accepts corresponding repository secrets named `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`. It decodes the keystore into the runner's temporary directory and never includes private signing material in published assets.

If no owner credentials are configured, Gradle uses its existing default signing configuration. The actual certificate and mode are recorded in the verification report; that fallback does not establish key continuity with previous CI builds. Future in-place updates require the same private signing key.

## Verification and diagnostics

[Final build report](../docs/releases/0.6.6/DELIVERY.md) distinguishes automated software rendering from the owner's Android 12 acceptance. Optional instrumentation packages are compiled separately; compilation does not establish a device run or a GPU performance result.

[LOGCAT.md](LOGCAT.md) explains safe diagnostics. Settings includes **Copy diagnostics**, without URLs, cookies, titles or command arguments. Pause or finish queued work before using the existing explicit yt-dlp updater. App updates and engine updates are separate.

Files such as `TURBO_REPORT.md`, `CRASH_FIX.md` and earlier delivery records document historical changes. The final release report is the current distribution reference.

## Source and licenses

`upstream.json` pins the reused YTDLnis core. Runtime preparation records hashes. `vendor/square/` and the Backdrop notices preserve the upstream visual-component provenance. The launcher, queue and storage integration remain specific to Ytdlp-G. See the repository [LICENSE](../LICENSE), [NOTICE.md](../NOTICE.md) and corresponding source attached to the release.
