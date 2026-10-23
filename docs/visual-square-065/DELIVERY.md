# Verified test APK — 0.6.5

[Download the test package containing Ytdlp-G.apk](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39963042420/artifacts/12505069231).
Extract the ZIP and use **Ytdlp-G.apk**. A GitHub login may be required to download Actions artifacts.

[Successful Actions run](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39963042420) · [Preview gallery and actual gesture sequences](README.md) · [Scope, adaptation and native checks](REPORT.md) · [Machine-readable download verification](DELIVERY.json)

- Compiled source: `7670491fd731700e194c9c95c9c6aa8ff3ad2e76`.
- App: version 65 / 0.6.5-test; Android 8+; ARM64; `com.fuhbe.ytdlpg.minimal`.
- Downloaded APK size: **59,611,344 bytes**.
- APK SHA-256: `9672b0fcebe720f9e3202c354c467ae51cb38e6ba4b4404cfe638a59712ae3b1`.
- Corresponding source ZIP SHA-256: `9005734cb72a8dd512463ff66d4f8c00d3f26d1c0c68169ae160efa1038362de`.
- APK signing certificate SHA-256: `a45e0cfccbb5d492fd38c6ebac10cfbe395679e1b2b63ef2df698552093ec2af`.

All three actual Actions artifacts were downloaded and checked against GitHub's artifact digests and ZIP CRCs. The internal APK/source checksums, corresponding source files, pinned Square/Backdrop notices, exact supplied wallpaper PNGs, native runtimes, app identity and matching instrumentation signature were verified after download.

**138 JVM cases: 137 passed and one existing host-Skia shader limitation skipped.** All 119 original test identities and their outcomes are preserved, and original functional test sources remain unchanged. Real host HTTP/HLS media checks cover available resolutions, MP4 download/remux/conversion, best-source MP3, progress and failed-conversion source preservation. Thirteen optimized DEX ZIP-bootstrap checks and the real positive/negative R8 ZIP regression pass. These are host media fixtures, not a new live-YouTube or on-device performance result.

**Eight device tests compiled; none executed here.** The native moving-production-pane pixel probe, physical touch feel and GPU/frame-time/thermal acceptance remain pending a real device. Android 12 uses the captured-blur/lighting fallback; advanced AGSL refraction needs capable Android 13+ hardware. The gallery contains real software UI frames and does not claim otherwise.

## Install carefully

This APK's development signature **differs from the published 0.6.4 APK**. Android cannot update that installed copy in place with a different key. Back up your queue/history/downloads before considering any uninstall; uninstalling can remove app-private data. Do not delete a working installation solely to resolve a signature conflict. The same app ID is preserved, so these two APKs cannot run side by side.

The approved [v0.6.4 release](https://github.com/fuhbe/Ytdlp-G-Android/releases/tag/v0.6.4), its exact APK hash, existing tags and default branch were checked and remain unchanged. This test build has not replaced the stable release.

## Artifacts

- [APK, corresponding GPL source, licenses, images, motion frames and verification summary](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39963042420/artifacts/12505069231)
- [Matching device-test APK and instructions](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39963042420/artifacts/12506456138)
- [JUnit XML, renders, mapping and verification reports](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39963042420/artifacts/12504919600)

Actions artifacts currently expire on January 21, 2027. This is an isolated test delivery, not a new stable tag or release.
