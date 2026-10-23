# Ytdlp-G 0.6.6-test — gesture and optical correction

Extract this package and use **Ytdlp-G.apk** (ARM64, Android 8+). It preserves the
approved download engine, all functional implementations and stable 0.6.4 release.

Pointer motion now bypasses chasing springs. Source content is cached outside
the drag path. Android 12 has an actual-pixel mesh lens; Android 13+ uses native
AGSL against the real control content. Reflections and elastic release remain.

**Physical GPU/refraction and sustained frame-rate acceptance are pending.**
Automated host tests do not establish 60 FPS or physical touch latency. The
matching device-test artifact and `DEVICE_CHECK.md` explain the joint phone test.

Development signatures may differ from your installed APK. Preserve queue,
history and files before considering an uninstall after a signature conflict.
The same application ID is retained. Do not remove a working copy blindly.

See `VISUAL_REPORT.md`, source/third-party notices, SHA256SUMS and verification
JSON. Software render evidence is explicitly labeled and is not a device photo.
