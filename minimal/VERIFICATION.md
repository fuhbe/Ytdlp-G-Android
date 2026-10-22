# Verification record

This file distinguishes checks that can run here from device acceptance. The
workflow's reports contain the results for its exact commit and APK.

| Area | Available verification | Remaining device check |
|---|---|---|
| MP3 | Real HTTP/HLS source, best-source extraction, FFmpeg MP3, FFprobe stream check | YouTube link and playlist on the phone |
| MP4 | Real HTTP/HLS downloads, selected resolution and fallback, H.264/AAC streams | YouTube link and playlist on the phone |
| Formats | Actual HLS metadata plus parser tests excluding DRM and heights above 4K | Formats returned for the user's URLs |
| ZIP | Streaming archive, CRC, original retention, cancellation cleanup; selection policy | Playlist/multiple-link queue and document-provider export |
| Queue | SQLite state/destination persistence, native process-control implementation reused | Pause/resume/cancel during native downloads, process death and background execution |
| Storage | App-private temporary files and export implementation | Picker grants, provider errors, full disk and opening output on a phone |
| UI | Compose/Robolectric layout fixtures: individual, playlist, manager error; 320/411/720/800 dp, large fonts, dark/light, scrolling | Real keyboard, Android navigation bars, accessibility and manufacturer-specific scaling |
| Packaging | APK signature, installed label/application ID, bundled Python/FFmpeg/QuickJS/EJS and licenses | Installation and runtime startup on target hardware |

Host fixtures are generated media served over a real local HTTP server. They
exercise the actual yt-dlp executable, exact worker command/output protocol and
FFmpeg; no successful YouTube download is fabricated. UI fixtures deliberately
use sample metadata, and do not assert that the sample media was downloaded.

The mandatory end-to-end device scenarios from the request remain acceptance
checks until executed on a phone: single MP3/MP4, multiple available resolutions,
playlist MP3/MP4/ZIP, multiple links as ZIP, folder changes, paused/resumed jobs,
download/packing cancellation, recovery, background execution, real keyboard,
system bars and opening saved files. A passing build is not a substitute.

The direct host YouTube metadata attempt was rejected by the environment's
network proxy (`Tunnel connection failed: 403 Forbidden`). No proxy bypass was
attempted. The generated-media HTTP/HLS checks run locally and passed; they do
not prove that a particular YouTube URL will download on the user's device.
