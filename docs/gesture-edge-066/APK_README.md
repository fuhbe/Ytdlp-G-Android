# Ytdlp-G 0.6.6-test3 — slider edge / indication test

Extract and install `Ytdlp-G.apk` (ARM64, Android 8+). Stable v0.6.4 is untouched.
This test build fixes captured endpoint release and suppresses only the default
Material ripple in the four existing shared selectors. Downloads and all glass
effects/animations/layout remain unchanged.

Read `VISUAL_REPORT.md`, `VERIFIED.json` and `DEVICE_CHECK.md` for scope, actual
test results and phone acceptance. Software ripple reference images may be
identical because the host does not render native RippleDrawable: they are not
proof of a successful physical-phone pixel comparison.

The app uses the same package ID. CI development signing keys can differ from
your installed APK, and Android may reject an in-place update. Preserve your
working app and its data before replacing anything; do not blindly uninstall.
Source and licenses are included; no APK recompilation after verification.
