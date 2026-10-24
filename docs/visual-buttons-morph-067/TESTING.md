# Experimental 0.6.7-test1 verification

## Automated host checks

- Preserve all 204 baseline case identities and their outcomes (202 passed, two existing host rendering skips), including download/media/network/bootstrap/ZIP and the four slider edge/diagonal regressions.
- Elastic buttons: single tap/accessibility/keyboard action, horizontal/vertical/diagonal pulls, small jitter, long press, outside-and-return, pointer cancel, second pointer, disabled/loading transition, parent scrolling, fixed hit/glyph bounds, actual rendered outline change, motion counters.
- Morph: exact actual qualities and selected value across API26/31/32/35, Back/outside, stale double action, forming input blocker, rapid interruption, updated choices, RTL coordinates, long list/large font, frozen actual-screen source, clipped origin and state recreation.
- Geometry: multiple screen dimensions, top/bottom placement, RTL, tiny viewport fallback, actual raster alpha proving a connected neck at an intermediate frame and a separated silhouette at completion. This is shape evidence, not an optical/GPU claim.
- Reduced motion: buttons/menu settle without retaining an invisible input blocker.
- Genuine software application-window captures: both themes, pressure/pull/release and opening/bridge/menu/reverse closing, with sample metadata and frame-state provenance.
- Scope: all protected baseline hashes, exact callback substitutions and only version metadata changes in the build file. Dependencies, native preparation, signing policy, app ID and minSDK remain unchanged.

Read actual JUnit XML and `VERIFIED.json` for executed counts/outcomes. A compiled instrumentation APK is not a passed device test. Failed development iterations are corrected and rerun; only final-run receipts establish the delivered result.

## Redmi Android 12 acceptance

1. **Check signing before installing.** If Android reports an incompatible signature, stop. Do not uninstall your working app without preserving wanted files/settings/queue state. Use a separate test device if certificates differ. Android user/work profiles share package signing identity and do not bypass signature verification. The optional owner key was not replaced or exposed.
2. Install `Ytdlp-G.apk`; confirm version `0.6.7-test1`.
3. Analyze: normal tap still analyzes. Press and gently pull horizontally/diagonally; observe bounded shape and light response. A substantial pull must return without analysis. Verify page scrolling still works normally.
4. Download: with an inspected item, pull diagonally and release outside; no job should start. An ordinary tap must start the same download as before. Recheck an actual MP3 and MP4 and unchanged available qualities.
5. Open MP4 quality: inspect origin expansion, neck formation, slim vertical panel and reverse merge on Back/outside. Choose an actual available height. No extra quality should appear; reopening must show the selection.
6. Rapid open/close/open, scroll, landscape, keyboard visible, large text, TalkBack, light/dark, power saver and disabled Android animations: no clipped options, stuck overlays or accidental clicks.
7. Recheck all four sliding selectors at both edges and with diagonal release outside their visual area; they must retain the v0.6.6 behavior.
8. Compare advanced rendering on the actual device. On Android12 the new neck is captured-screen blur, not AGSL lens refraction. Report any flashing, ghost glyphs, optical discontinuity or delayed response before stable promotion.

## Optional real hardware measurement

No phone/emulator execution is claimed by the build. The matching test APK includes a new opt-in `Glass067DeviceTest` and retains all existing device tests. Pause active jobs first; the new visual probe uses sample metadata and performs no network/download actions.

```sh
adb install -r Ytdlp-G.apk
adb install -r Ytdlp-G-tests.apk
adb shell am instrument -w -e class com.fuhbe.ytdlpg.simple.Glass067DeviceTest com.fuhbe.ytdlpg.minimal.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/com.fuhbe.ytdlpg.minimal/files/glass-067-probe/
adb shell dumpsys gfxinfo com.fuhbe.ytdlpg.minimal reset
# Exercise buttons, menu and sliders on the phone, then:
adb shell dumpsys gfxinfo com.fuhbe.ytdlpg.minimal framestats > glass-067-framestats.txt
```

The probe reports actual Activity-window frame percentiles, refresh-budget misses and GPU duration when supplied by Android; it does not separately measure Popup GPU frames or touch-to-photon delay. For the popup use a system Perfetto FrameTimeline trace (Developer options/System Tracing, Graphics/View/FrameTimeline) while opening/closing, and screen-record with `adb shell screenrecord /sdcard/glass-067.mp4`, then `adb pull /sdcard/glass-067.mp4`. Treat emulator and physical-phone results separately. No stable 60-FPS claim follows from host tests.
