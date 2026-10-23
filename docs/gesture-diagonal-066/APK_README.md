# Ytdlp-G 0.6.6-test4

Install `Ytdlp-G.apk` on ARM64 Android 8.0+.

This test build corrects diagonal releases of an already captured horizontal slider gesture. It also keeps endpoint selection and slider-local ripple suppression from test3. The existing glass appearance and download system are preserved.

`VERIFIED.json` records the actual APK checksum, signature, automated results and scope verification. See `DEVICE_CHECK.md` for phone acceptance steps. `Ytdlp-G-source.zip` contains the corresponding source and dependency notices; it is not the app installer.

The signing key may differ from earlier CI test builds. If Android rejects an update, export needed files before uninstalling the old build, which clears private settings, queued jobs and partial files. Published test3 and stable 0.6.4 remain available unchanged.
