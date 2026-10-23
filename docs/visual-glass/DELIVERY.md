# Verified visual test delivery — 0.6.3

Build: [GitHub Actions run 39896062660](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39896062660), completed successfully.
Source commit: `e2a1c8d6821aed521bdac9babae8738b7ef4e7a1`.
Branch: `visual/liquid-glass-0.6.3`. The public main branch and stable v0.6.2 release were not replaced.

## Download

- [Application package](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39896062660/artifacts/12477281594): extract **Ytdlp-G.apk** from the artifact ZIP. Includes corresponding source, SHA256SUMS, license notices, documentation and 25 UI renders.
- [Matching device tests](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39896062660/artifacts/12477565338).
- [Build and verification reports](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/39896062660/artifacts/12477316659).
- [Before/after comparison and technical report](REPORT.md).

GitHub artifact downloads require GitHub sign-in and remain available for the run's retention period. This is a visual test build, not a replacement stable release.

## Verified after downloading

The three artifact ZIP digests match GitHub's published SHA256 values. ZIP integrity, internal application/test checksums, exact corresponding app source, Backdrop source/license notices and optimized runtime bootstrap all passed verification after download. The application remains Android 8+ / ARM64 with the same application ID and runtime dependencies; Backdrop is the sole added visual dependency.

- APK: **56,247,290 bytes**.
- APK SHA256: `baa061bfff536be8e82590302ded01ddfc585a2e7943b77d4771c58dcea040ef`.
- All **57 original tests passed**. Entire suite: **89 cases, 88 passed, 0 failures/errors, 1 explicit host-renderer GPU skip**.
- Original actual HTTP/HLS/DASH and FFmpeg fixture smoke checks passed; this does not claim a new live YouTube transfer.
- Scope guard: 28 protected files, Activity entry points, file-opening helpers and 19 presenter contracts unchanged.
- 21 API-matrix UI renders plus 4 comparison previews; these are actual software Compose renders with sample metadata, not device GPU screenshots.
- Optimized DEX: all 13 bootstrap constructor checks passed; R8 archive regression positive/negative controls passed.
- Six device instrumentation scenarios compiled in the matching test APK. An additional API 35 x86_64/SwiftShader attempt without KVM did not finish a usable boot: installation returned `Can't find service: package`. No instrumentation executed. Physical-phone acceptance remains required; a built test is not a passed test.

## Signing and device acceptance

Application and test APK certificates match each other. This CI test certificate differs from stable 0.6.2, so Android may reject an in-place update. Do not delete an installation holding needed queue/settings/partials: uninstalling removes private app data. Finish/export needed work first or use a spare installation. Stable v0.6.2 remains available.

Test certificate SHA256: `4631c543a394e02adbbb61307358bad8ff4cd13f24db51574d749ee29f28873e`.
Stable certificate SHA256: `3e1df7424b2cb4589de10dc26ae20afe68eded24acc82b6a0298de2fdb98798b`.

On Redmi 10 Prime / Android 12, the expected tier is sampled blur with power saver off and sufficient available graphics capabilities. Refraction requires Android 13+. See REPORT.md for fallback policy and exact instrumentation/Logcat commands. Physical-device shader support, visual closeness to the reference, FPS, ANR, memory, heat and battery acceptance have not been claimed from JVM tests.
