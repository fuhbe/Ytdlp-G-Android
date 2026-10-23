# Visual-only baseline and scope

Baseline: main commit `3064edecd8e3c1805ff629f6dcfd181c2b747479`, release v0.6.2, code commit `4893e319b620436d8089ef43dde05c3ada52c9df`. The release publishes the minimal module; APK/application names and ID remain unchanged. Work is isolated in `visual/liquid-glass-0.6.3`.

## Existing screens and contracts

- Home: link editor, paste, analyze, cancel analysis, error, preview, MP3/MP4 selection, quality menu (best + extracted heights), destination picker and Download. Existing analyze button visibility and callbacks stay unchanged.
- Playlists: same preview, selected count, Select/Clear all, four-row preview, expand/collapse, per-item selection, MP3/MP4, same qualities/destination, Normal/ZIP when selection permits, Download playlist. No new format, resolution or ZIP rule.
- Downloads: existing Downloads/Completed selector, real preview/state/phase/progress/speed/ETA, pause, resume, cancel, open, not-downloaded report and eligible retry. Preserve all visibility conditions and callbacks.
- Settings: destination, automatic/light/dark theme, engine version/update, diagnostics, completed-temporary cleanup, version, existing license dialog and source links. Only material styling changes.
- Navigation: the existing Home/back, Downloads and Settings destinations. The two header destination controls may be relocated to a bottom dock. The existing conditional Back action is retained; selected destinations are indicators rather than additional actions. No duplicated or new navigation callback.
- Native Android picker/permissions and incoming-share processing remain unchanged.

## Protected files

All runtime, engine, worker, analysis, model, batch, policy, storage, persistence, ZIP, service, notification and permission files are protected by a before/after SHA256 manifest. Only MainActivity Compose blocks, reusable UI material/components, visual dependency/metadata, associated licenses, UI tests and delivery documentation/workflow may change. MainActivity platform action handlers and non-Compose helpers stay unchanged.

## Library decision

Square is GPL-3.0; its code/assets are not copied. Backdrop is Apache-2.0. The requested Backdrop 2.0.0 requires compile SDK 37, Compose 1.11.0 and Kotlin stdlib 2.3.21. Backdrop 1.0.3 uses Kotlin 2.3.0 and Compose 1.10.0, compatible with the existing Kotlin 2.3.0 / Compose 1.10.1 / SDK 36 stack. Prefer the compatible 1.0.3 dependency to avoid upgrading the application's toolchain or download dependencies. The same real layer sampling, RenderEffect blur and API 33 RuntimeShader lens API are available. Android minimum remains 26.

The original APK hash is `735b4ac44a0aad52816df1f2ebcf3a637fb901ad690ee5b395ffe468a0349387`; published binary is preserved. Baseline full compilation, 57 JVM tests and compiled instrumentation status will be recorded from the fresh baseline build before UI changes. No connected physical Android device is available.
