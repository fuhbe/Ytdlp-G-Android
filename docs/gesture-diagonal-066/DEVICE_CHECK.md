# Android 12 phone acceptance — 0.6.6-test4

Use the new `Ytdlp-G.apk`. The previous approved test3 APK and stable 0.6.4 are preserved separately. If Android refuses the update because the signing key differs, finish/export needed files before uninstalling the older test build; uninstalling clears private jobs, settings and partial files.

On Redmi 10 Prime / Android 12, repeat these checks in MP3/MP4, Normal/ZIP, Downloads/Completed and the bottom navigation:

1. Touch the selected capsule and start moving horizontally. Then move diagonally upward, outside the bar, and release with your finger horizontally aligned to another option. That option must remain selected while the spring settles.
2. Repeat downward, and in both horizontal directions, including first/last options and middle navigation destinations.
3. Leave and reenter the bar while keeping the same finger down. No selection should be confirmed until finger-up; the last horizontal release position determines the destination.
4. Start a normal vertical scroll over a selector in a scrollable screen without first dragging horizontally. The screen should scroll and the selector should retain its choice.
5. After an owned horizontal drag, diagonal motion must not start scrolling the surrounding screen. A real OS gesture cancellation can still cancel the operation safely.
6. Check ordinary taps, edge overshoot, unchanged touch feedback, glass highlights, refraction and spring settlement. Verify no extra Material flash appears.

Screen recording with visible touches is useful if a gesture misbehaves. Include which selector, starting option, direction and app version. Do not include personal URLs or account data in reports.

This environment has no connected physical device or available accelerated emulator. The supplied automated pointer tests are distinct from this phone acceptance. Existing optional device instrumentation is compiled separately; building it does not mean it ran.
