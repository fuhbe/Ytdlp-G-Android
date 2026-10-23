# Ytdlp-G v0.6.6 — final build and verification report

[Official stable release](https://github.com/fuhbe/Ytdlp-G-Android/releases/tag/v0.6.6) · [Installable APK](https://github.com/fuhbe/Ytdlp-G-Android/releases/download/v0.6.6/Ytdlp-G.apk) · [Changelog](../../../CHANGELOG.md) · [Actual final-version captures](../../screenshots/0.6.6/README.md)

## Final identity

- Application version: **0.6.6**; Android version code: **70**.
- Package: `com.fuhbe.ytdlpg.minimal`; Android 8.0+; ARM64; optimized release variant.
- Compiled source commit: `649c12cc1e9bb42b9995bc22811d39248e7026ce`.
- Successful build: [run 40029350684](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40029350684).
- APK size: **59,625,880 bytes**.
- APK SHA-256: `d9020bc5bbf7aa415349f05df3d04912b25b2eaf904588cd1326fd0b4c6ae888`.
- Corresponding-source SHA-256: `03d366114252733c35716f45f81a33742f6ce0c13d7daa0ffbdceeb44b6c7184`.
- APK certificate SHA-256: `27c34fcf7e5660c549071df3536a9033e7cd643e415ad530785e1bfc38487c91`.
- Actual signing mode: **ci-default**; owner-controlled signing configured: **false**.

The publication step checks that the version tag contains the exact compiled application implementation. A documentation-only descendant adds the genuine PNGs and final verification records. `release-info.json` records both the compiled source commit and the tag's release commit. Publication reuses this verified APK without rebuilding or re-signing it.

## Preservation

The owner reported successful test4 use on Android 12, including animations and diagonal gestures. The final promotion changes version/signing metadata and explicitly enumerated English UI literals. [Source preservation record](VERIFIED.json) verifies that the download engine, yt-dlp/FFmpeg integration, playlists, ZIP, queue, models, workers, storage and services remain unchanged. Optical calculations, gesture recognition, refraction, reflection, illumination, cache behavior, deformation and springs are preserved.

Seven native libraries and all 20 compared runtime/license entries are byte-identical to the approved APK. Both wallpapers are unchanged. Existing test logic and identities remain, with translated UI selectors. No working implementation is replaced by a simulation.

## Actual verification

- **204 automated cases: 202 passed, two known software-rendering checks explicitly skipped.**
- All 186 approved baseline cases retain their identities and recorded outcomes.
- Eighteen additional capture cases exercise real final-version application windows, with English sample data, actual selectors, the separate quality popup and both themes.
- The complete downloaded artifact ZIPs passed their published SHA-256 and CRC checks. Inner checksums, source correspondence, release metadata, APK signature and matching instrumentation signature were checked independently after download.
- The final optimized APK passed the existing 13 ZIP/DEX bootstrap checks and the positive/negative archive regression controls.
- Unchanged real generated-media HTTP/HLS and MP3/MP4 fixtures passed in CI. These are controlled host fixtures, not a new live YouTube phone test.
- Held-motion counters retain zero extra compositions, source recordings and readbacks for the existing 60-sample probe. This is a host counter rather than a phone frame-rate guarantee.

The owner's Android 12 acceptance applies to the approved implementation. This environment did not run a physical device or emulator for the final metadata/text promotion. Software captures illustrate the compatible rendering path and do not establish a GPU benchmark or advanced hardware lens measurement. [Machine-readable independent receipt](DELIVERY.json).

## Signing and installation

A configured persistent owner key is used when available. The actual signing mode above is authoritative. If the mode is `ci-default`, no owner release credentials were configured and continuity with earlier or future CI certificates is not claimed. Private signing material is never included in source, APK assets, screenshots or reports. Future in-place updates require the same private key.

Android rejects an in-place update from a differently signed installation. Finish/export needed files before uninstalling it: uninstalling clears private settings, queued jobs and partial files. Already exported destination files remain. Full signing setup is documented in [minimal/README.md](../../../minimal/README.md).

## Published distribution

The release is titled **Ytdlp-G v0.6.6**, tagged **v0.6.6**, published as stable and marked latest. It includes the APK, complete SHA-256 sums, corresponding source/license information, original verification reports and genuine screenshots. The publisher validates these flags and asset digests before recording success. Earlier releases, tags and their asset identities are checked before and after publication and remain intact.

All public release descriptions and screenshot captions are English. Screenshots clearly label sample metadata. Repository source and documentation are promoted to the verified release tree after the public files have been checked.
