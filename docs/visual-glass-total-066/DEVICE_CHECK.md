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

The existing `material=BLUR` capability log on API31/32 describes the base blur
capability, not the new mesh backend. Use `hardwareMeshCanvas`, `meshDraws` and
the zero/nonzero-strength images to establish which optical path actually ran.
If a probe assertion stops early, collect the saved images/pixel JSON and Logcat;
missing metrics must not be interpreted as zero GPU time or successful optics.
After the isolated warm-up check, repeat manual gestures during a normal known
download/conversion and note thermal conditions; those workloads can affect frame
budgets even with unchanged download logic.


## Additional acceptance for 0.6.6-test2

Use both APKs from the **same new run**, not the older 0.6.6 artifacts.
The original optical/frame tests remain available. Run the new all-controls probe:

```sh
adb shell am instrument -w -e class com.fuhbe.ytdlpg.simple.GlassTotalDeviceTest com.fuhbe.ytdlpg.minimal.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/com.fuhbe.ytdlpg.minimal/files/glass-total-066-probe/
```

It uses fixture metadata, no media download, and requires no active jobs. It
checks MP3/MP4, Normal/ZIP, navigation, Completed, exact quality choices and
popup selection, then records real window frame times and screenshots. The
sample includes scrolling/transitions; it is not a pure drag benchmark. Missing
GPU duration remains null, never zero. The virtual-device hint is diagnostic.

Manually repeat slowly and quickly in both themes on the Redmi Android 12:

1. Start a drag near either edge or an unselected option on every bar. Near the
   middle/last destination, release a little before its center/old boundary.
   Confirm exactly one action on release and no action on cancellation.
2. Hold halfway: both neighboring labels/icons must be partially highlighted.
   Reverse direction and inspect actual glyph bending, no duplicate text.
3. Pull past each end: finite extra stretch, moving reflections and elastic
   relaxation must remain. Input must not chase a position spring.
4. Open quality near the bottom of the screen. It should expand from the
   trigger area, fit above or below, scroll, show only the inspected options,
   and dismiss by Back/outside touch. Repeat with large fonts, rotation, RTL,
   split-screen, and battery saver. Android 12 quality panel blur is not an AGSL
   lens; do not mark it as verified real refraction.
5. Compare optical-strength A/B images and FrameTimeline/gfxinfo measurements.
   Run again during a known normal download without altering the engine.

Report actual phone model, API, refresh rate, temperature/battery saver, images,
Logcat and total/GPU p95/p99. These checks have not been run here.
