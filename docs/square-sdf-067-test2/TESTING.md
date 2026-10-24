# Test2 acceptance and truthful rendering evidence

Install the experimental `Ytdlp-G.apk` (0.6.7-test2/code72) only after checking the certificate in `VERIFIED.json`. The unchanged CI default policy can produce a different certificate from test1/stable. Android then blocks in-place updates. Do not uninstall your working app without preserving wanted settings/queue/partial data. Android user/work profiles share package signing identity; use a separate test device where necessary.

## On the phone

1. Normal Analyze/Download/folder/update/Paste taps must invoke their original action once. Pull deliberately instead: horizontal, vertical and diagonal, continuing beyond the original control. The single glass body and light must continue; release outside must not invoke an action.
2. Scroll vertically starting near/on a button: page scrolling retains priority. A continuing visual press must not launch analysis/download/update. Cancel/multitouch must reset without action.
3. Paste short/long URLs, narrow portrait/landscape, large fonts and keyboard open. The icon retains its48dp target, centered appearance and end inset; actual clipboard bytes remain exact.
4. Open MP4 quality and observe two blobs, field-created neck, source disappearance, narrow panel, readable actual choices and reversed absorption/undershoot. Choose an actually available height; reopen and confirm it. Rapid open/close, outside/Back, rotation, theme and visible-window changes must not strand an overlay.
5. Recheck all four unchanged selectors: both extremes/margins, tolerant intermediate choices, diagonal/out-of-bounds release, RTL, no default ripple and preserved progressive light/refraction.
6. Test light/dark, power saver and Android animations disabled. On API33+ verify actual arbitrary-neck refraction; on API31/32 expect SDF shape + blur, not that advanced lens.

## Automated results

The final full CI JUnit XML and delivery receipt identify PASSED/FAILED/SKIPPED. All241 test1 case identities/outcomes must be retained, including both old host-rendering skips. Two legacy geometry case bodies are explicitly migrated from the obsolete Bezier/two-persistent-body expectations to SDF/vanishing-anchor assertions; no functional or selector test is weakened.

New checks cover actual Window.Callback long pulls, outside cancellation, parent scrolling and all four selectors through the delegated window; exact paste and inset across themes/fonts; empty/dynamic choices; SDF contour/native-raster agreement and pixels created outside both input blobs; direction-change continuity; held geometry/cache reuse; shader compilation/uniform updates; actual activity-background/input capture counters; and62 real software application-window frames. Existing media/MP3/MP4/HTTP/HLS/ZIP/bootstrap suites remain intact.

Host RuntimeShader compilation validates syntax/uniform binding. It does not validate GPU pixels, driver behavior, frame times, touch latency or90/120Hz smoothness. The captures use actual AppContent/Popup windows and labelled sample metadata; they are not fabricated mockups or physical-device recordings. GIF replay is deliberately slowed for inspection.

## Optional API33+ hardware pixel probe

The matching instrumentation APK preserves all previous device tests and adds `SquareSdfDeviceTest`. It freezes the actual application's morph at a sample phase, compares real background pixels with displacement disabled/enabled, verifies a hardware Canvas and cached source, and saves genuine screenshots/counters. It uses sample metadata and never queues a download. Pause real jobs first.

```sh
adb install -r Ytdlp-G.apk
adb install -r Ytdlp-G-tests.apk
adb shell am instrument -w -e class com.fuhbe.ytdlpg.simple.SquareSdfDeviceTest com.fuhbe.ytdlpg.minimal.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/com.fuhbe.ytdlpg.minimal/files/square-sdf-test2-probe/
```

API26–32 intentionally skip that native AGSL pixel probe; compatible visual checks and prior device tests remain available. Compiling the APK does not mean this probe ran. `virtualDeviceReported` differentiates an emulator from a phone. A missing/flat-texture optical sample is a failed/inconclusive acceptance result, never proof of refraction.

## Real motion/frame measurements

No phone or emulator is connected to this build environment; GPU/frame/jank results are unavailable. Use a physical phone trace rather than extrapolating host test timing:

```sh
adb shell dumpsys gfxinfo com.fuhbe.ytdlpg.minimal reset
# Pull buttons, morph/open/close, scroll and exercise the four selectors.
adb shell dumpsys gfxinfo com.fuhbe.ytdlpg.minimal framestats > square-sdf-test2-frames.txt
adb shell screenrecord /sdcard/square-sdf-test2.mp4
# Stop recording with Ctrl+C, then:
adb pull /sdcard/square-sdf-test2.mp4
```

For popup timing/90–120Hz behavior, collect Perfetto FrameTimeline (Developer options > System Tracing, Graphics/View/FrameTimeline) while exercising those gestures. Existing Activity FrameMetrics probes do not separately measure Popup-window GPU duration or touch-to-photon latency. Keep physical and emulator results separate. There is no60/90/120FPS guarantee.

## Language acceptance

Open Settings and select English, Português (Brasil), then Español. Each selected glass button should remain selected after restarting the app. Navigate Home/Downloads/Settings and open the quality menu: labels should update, while media titles, URLs, available resolutions and downloads remain unchanged. Repeat during a paused/active job; no job should restart. Raw technical engine messages and original license texts retain their source wording.
