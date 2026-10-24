# Ytdlp-G 0.6.7-test1 — experimental delivery

This build is confined to `feature/liquid-glass-buttons-morph-067`, based on stable `v0.6.6` (`df74cb9fde1cf31d42870a3d905b4a99c72fa407`). It is not approved for stable promotion. Stable main, tags and releases are preserved.

The build workflow publishes an optimized ARM64 `Ytdlp-G.apk` (versionCode 71), matching instrumentation APK, exact corresponding source, GPL/source notices, SHA-256 sums and machine-readable `VERIFIED.json`. The verification artifact retains JUnit results, genuine application-window frame sequences, rendering provenance, source-freeze hashes, motion counters and bootstrap/media results. Build success alone is not the acceptance criterion.

[Implementation and compatibility](README.md) · [Redmi acceptance and real-device measurement](TESTING.md)

Only UI components change: `ElasticGlassButton.kt`, `GlassMorphGeometry.kt`, `GlassMorphMenu.kt`, the quality-picker presenter, frozen overlay acquisition, four exact wrapper substitutions in `MainActivity.kt` and version metadata. Existing selectors/navigation/optical shaders and all engine/model/runtime/worker/service/storage/download files remain byte-identical. `verify_scope.py` checks the full protected baseline plus exact action callbacks and version-only Gradle differences.

Automated development checks exercise button taps/keyboard/accessibility, drag cancellation and scroll priority; menu choices, input protection, RTL/insets placement, long lists, reverse/interrupted transitions and cache lifetime; reduced motion; and actual software-rendered app frames with labeled sample data. The final CI receipt determines final counts and APK identity. The existing 204 baseline case outcomes must all be preserved.

No physical phone or emulator is connected to this build environment. Device-test compilation is reported separately from execution. Android 12 menu-neck blur is not claimed as real lens refraction; the pinned advanced lens cannot accept generic neck shapes. Host screenshots do not validate GPU refraction, frame time, jank or touch latency. Test this experimental build on the Redmi before any stable decision.

**Installation safety:** retain v0.6.6 and important user data. The existing optional owner key is used only if configured. Otherwise CI uses its unchanged default signing policy; compare certificate digests in `VERIFIED.json` before attempting an in-place update. A certificate mismatch prevents updating the installed app. Uninstalling removes private app state, including settings/queue/partial data; do not uninstall just to bypass that check without preserving wanted data. No signing key is published or requested.
