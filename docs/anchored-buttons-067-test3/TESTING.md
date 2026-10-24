# Test3 phone acceptance

This is an experimental APK. Check its final signing certificate and SHA-256 in DELIVERY.md before installation. The existing CI-default signing policy may produce a certificate different from your current APK, preventing an in-place update. Uninstalling can erase private settings, queue and partial downloads; do not discard your working installation/data without accepting that consequence. A separate test device avoids that conflict; Android user/work profiles do not bypass package signatures.

1. Open an actual video and pull Download or Save to horizontally, vertically and diagonally, well outside the original hit target. Finger tracking/light should continue, but the body should extend locally with resistance instead of following the finger across the screen. Text/icon and the layout remain anchored. Release/cancel must spring back exactly, with no action after an intentional drag. Normal scrolling still moves cards with the page.
2. Observe rest, press, hold, release, the full return and quick re-press. There must be one Download surface, label, icon and semantic target in every frame. Start scrolling on a button until its item leaves the list, then release; no floating residual or stuck input should remain. Recheck loading/disabled state, keyboard/TalkBack and multitouch.
3. Open MP4 quality and select1080p or another actually listed height with one tap as soon as rows appear, before the spring fully settles. The selected height should update once and close with the approved SDF reverse animation. Reopen, use Back/outside, vary fonts/orientation, and scroll a long list. Dismissal must not change selection.
4. Repeat in English, Português (Brasil), Español, light/dark, narrow width, large fonts, power saver and animations disabled. Existing saved preferences must remain.
5. Recheck all four unchanged sliders: both extremes/margins, tolerant middle acquisition, diagonal escape/release outside, RTL where applicable, no default flash, progressive illumination and elastic optics. Existing download/folder/queue controls must retain their original actions.

The automated captures use actual AppContent/Popup with sample metadata and native Skia SOFTWARE. They are not mockups, emulator screenshots, physical-phone recordings, GPU refraction evidence or FPS benchmarks. Device instrumentation is compiled separately; compilation does not mean execution. No live YouTube download or physical GPU timing is claimed in this environment. Existing controlled-media/MP3/MP4/ZIP/network-fixture suites are rerun unchanged.

For physical frame/visual evidence:

```sh
adb shell dumpsys gfxinfo com.fuhbe.ytdlpg.minimal reset
# Exercise the three scenarios and all four selectors.
adb shell dumpsys gfxinfo com.fuhbe.ytdlpg.minimal framestats > test3-frames.txt
adb shell screenrecord /sdcard/test3.mp4
# Stop with Ctrl+C, then:
adb pull /sdcard/test3.mp4
```

Use Perfetto FrameTimeline for actual jank/touch-to-photon analysis. Do not infer phone FPS from host unit-test durations or the slowed GIF playback.
