# Experimental 0.6.7-test2 delivery

This branch starts from compiled test1 `4f09a59c888d6d67800baeaff53f968fe9ced02b`; stable v0.6.6 and all previous releases/tags remain untouched. The workflow builds `Ytdlp-G.apk`, versionName0.6.7-test2/versionCode72, matching instrumentation, corresponding source, license information and SHA-256 receipts. The final build identity and actual CI results are recorded in `VERIFIED.json` after successful checks, not inferred from compilation.

[Source reuse and differences](PROVENANCE.md) · [Rendering/compatibility](README.md) · [Phone acceptance](TESTING.md).

The implementation replaces only experimental visual/button/morph code, adds namespace-only original Square shapes and ports MIT physics/SDF/normal math. MainActivity changes are restricted to a UI paint host, paste-icon padding and the owner’s additional English/Português (Brasil)/Español UI request; original action calls and clipboard assignment are verified independently. All protected downloads/runtime/workers/storage/models/services, signing/dependencies/API policy and accepted selector/optics sources are checked against stable (navigation label localization is normalized separately).

Tests/captures are executed on a software host. The actual AGSL program is compiled and its uniforms exercised, but no physical GPU output, phone performance or emulator execution is claimed. The new hardware pixel probe is compiled only unless explicitly run on a device. Stable promotion requires owner testing.
