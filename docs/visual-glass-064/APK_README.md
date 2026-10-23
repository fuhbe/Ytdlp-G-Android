# Ytdlp-G 0.6.4 — visual test APK

Extract and install **Ytdlp-G.apk**. This build derives from visual 0.6.3 and adds
only the draggable Liquid Glass dock, dynamic lighting, animated selection material
and the two supplied theme wallpapers. Stable main/v0.6.2 is preserved.

Android 8+ / ARM64, same application ID `com.fuhbe.ytdlpg.minimal`. All existing
download behavior, runtime, format choices, quality dropdown, queue, ZIP, storage,
updates and operational controls remain intact. Backdrop stays at 1.0.3.

The artifact includes corresponding source, dependency licenses/notices,
SHA256SUMS, comparative software UI captures and the detailed LIQUID_GLASS.md report.
The matching instrumentation APK is a separate Actions artifact. Compiled Android
instrumentation is not a passed device run. The existing host-GPU shader test is
explicitly skipped; physical GPU/performance acceptance remains required.

API 26–30 uses lightweight material; API 31–32 adds real sampled blur; API 33+
can add sampled AGSL lens refraction. Low RAM/power saver/software rendering uses
the lightweight tier. In particular, Redmi 10 Prime / Android 12 uses blur, not AGSL.

A CI test certificate can differ from your installed build and prevent an in-place
update. Uninstalling removes private queue/settings/partials. Finish/export needed
work first or use a spare installation; no signing rule or database migration was
changed. Check the downloaded delivery report for the actual certificate comparison.

See LIQUID_GLASS.md for acceptance steps and Android Logcat/instrumentation commands.
This visual test build does not claim iOS-identical rendering or universal GPU support.
