# Ytdlp-G 0.6.6-test3 — slider endpoints and no default click flash

**[Download the APK package](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40018985216/artifacts/12530596238)**
— extract `Ytdlp-G.apk` from the Actions ZIP. See the
[verified delivery and signing information](DELIVERY.md) before installation.

This is an isolated test build on `fix/glass-edge-selection-066`. Stable v0.6.4
and the download implementation remain untouched.

The captured finger release at either real edge, padding or horizontal overshoot
now confirms that physical endpoint. The existing spring relaxes to the confirmed
choice. Intermediate tolerance remains. Only Material's default indication inside
MP3/MP4, Downloads/Completed, Normal/ZIP and bottom navigation is disabled; actual
interaction events and the existing glass response remain.

No redesign: shaders, refraction, reflections, progressive ink, elastic animation,
wallpapers and layout remain unchanged. The strict scope checker proves that
separation against the delivered test2 source.

The final downloaded packages verified **172 passed / 2 skipped** host cases.
The skip for native RippleDrawable is explicitly inconclusive, not a pixel proof
of removal. Phone appearance must still be checked on the Redmi Android 12.
No emulator, physical-device or new FPS/GPU result is claimed.

[Root cause, modifications and scope proof](REPORT.md) ·
[APK/checksums/test evidence](DELIVERY.md) ·
[Physical-phone checks](DEVICE_CHECK.md) ·
[Actual pre-fix reproduction](REPRODUCTION.json)
