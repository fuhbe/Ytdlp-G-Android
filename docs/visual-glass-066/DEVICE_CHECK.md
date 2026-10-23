# Physical Android 12 optical/performance acceptance

This procedure is pending execution here. Keep the stable app and your data safe.
CI development signing keys can differ, so Android may reject an in-place update.
Do not uninstall your working app without preserving its queue/history/files.
The test retains the same package ID and cannot be installed beside that copy.

Download both the main package and the matching device-test artifact from the
same Actions run. Use ARM64 Android 12 or newer with hardware acceleration, USB
debugging and battery saver off initially. Pause existing jobs. The visual probe
uses fixture metadata and never starts a media download or changes the engine.

```sh
adb devices
adb install -r Ytdlp-G.apk
adb install -r Ytdlp-G-tests.apk
adb shell am instrument -w -e class com.fuhbe.ytdlpg.simple.GlassPerformanceDeviceTest com.fuhbe.ytdlpg.minimal.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/com.fuhbe.ytdlpg.minimal/files/glass-066-probe/
```

Collect `frame-metrics.json`, `optical-pixels.json`, `optics-off.png`,
`optics-on.png`, and the actual screen. Record device model/Android version,
refresh rate and whether this is a physical phone or emulator. The JSON's
virtual-device hint is only diagnostic, not an attestation.

On Android 12, `hardwareMeshCanvas` must be true and `meshDraws` positive before
calling the mesh a verified GPU path on that phone. Compare zero/nonzero-strength
images inside the capsule: glyph outlines/grid/features must move, not just
change tint. A shader/mesh error must leave readable controls and log a generic
`YtdlpG-Glass` fallback warning. Do not report the fallback as successful optics.

Window FrameMetrics measure rendering duration, not complete touch-to-photon
latency or guaranteed presentation FPS. Compare total/GPU p50/p95/p99, refresh
budget (16.67ms at 60Hz), over-budget frames and dropped measurement callbacks.
Do not turn missing GPU durations into zero or claim a stable frame rate from
an insufficient sample. Repeat after warm-up and under normal temperature.

For manual acceptance, slowly drag/reverse/cancel each existing selector,
release outside, use two fingers, change theme/font scale and rotate. Check
that the capsule follows immediately, elastic release remains, labels bend
only under the lens and original labels are not doubled. Verify normal taps,
keyboard/TalkBack navigation and all unchanged download actions afterwards.

For presentation jank, Android 12 supports Perfetto FrameTimeline. Record a
System Trace with Graphics/View/FrameTimeline while repeating the gestures;
open it at https://ui.perfetto.dev and compare actual/expected frame timelines.
You can also collect window rendering statistics:

```sh
adb shell dumpsys gfxinfo com.fuhbe.ytdlpg.minimal reset
# Perform the same gestures manually for about 20 seconds.
adb shell dumpsys gfxinfo com.fuhbe.ytdlpg.minimal framestats > framestats.txt
adb shell dumpsys thermalservice > thermal.txt
adb logcat -d -s AndroidRuntime YtdlpG-Glass > glass-logcat.txt
```

Share these reports and cropped lens screenshots. Remove URLs, account names,
file paths and unrelated screen content before sharing. A screen recording can
help compare the finger and capsule, but video sampling alone does not measure
end-to-end latency. Repeat with battery saver to verify the explicit fallback.
