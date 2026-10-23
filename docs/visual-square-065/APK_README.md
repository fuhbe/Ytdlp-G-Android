# Ytdlp-G 0.6.5 — Square Liquid Glass test

Install **Ytdlp-G.apk** on an ARM64 Android 8+ device. Python, yt-dlp, FFmpeg and
QuickJS remain bundled. This test changes existing selectors' visuals/gestures
only; it preserves the approved 0.6.4 download engine and the published release.

The artifact includes corresponding GPL source, Square/Backdrop licenses,
before/after images, actual gesture-frame sequences, verification reports and
SHA-256 checksums. The separately named device-test artifact contains the
instrumentation APK targeting this exact optimized app.

**Physical GPU/refraction and touch acceptance are pending.** Host captures show
the real fallback UI; they do not prove native AGSL or device performance.
Android 12 uses blur and compatible lighting. Native lens refraction needs a
capable Android 13+ device. See `SQUARE_GLASS.md` for the precise adaptation,
fallbacks, test scope, limitations and native checks.

Development signing keys can differ from your installed stable build. Back up
your queue/history/files before considering uninstalling after a signature
conflict. Do not remove a working installation just to inspect test artwork.
The original stable APK remains available under GitHub release v0.6.4.
