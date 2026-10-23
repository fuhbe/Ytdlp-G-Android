# Physical-phone acceptance: endpoint selection and no extra click flash

These steps have not been executed here. Keep the working app and its data safe:
CI development signatures can differ. The package ID remains unchanged, so the
new APK cannot coexist with the old one and an in-place update may be rejected.
Do not uninstall a copy with queue/history you cannot afford to lose.

On the Redmi 10 Prime / Android 12, install the new `Ytdlp-G.apk` if compatible
with your installed signature/data preservation plan, then:

1. In MP3/MP4, begin in either option, slowly drag to the real left/right end,
   including its internal padding, and release. The matching choice must stay.
2. Repeat in Normal/ZIP using an existing inspected playlist with multiple items,
   in Downloads/Completed, and in all three bottom-navigation destinations.
3. Start from the middle destination and each extreme. Pull a little beyond the
   shell horizontally, reverse, and release. The final finger endpoint must win,
   with one callback; elasticity must settle visually at that destination.
4. Try an intermediate destination without reaching its exact center. Its
   existing tolerant selection should remain. Cancel a drag or leave vertically:
   it must not select a new option. Repeat with two fingers and large fonts.
5. Press an **unselected** option without dragging. There should be no additional
   standard Material click flash over the glass. Glass pressure, moving reflections,
   refraction, progressive ink and elastic release should still respond. Compare
   an ordinary button outside the sliders, whose indication was not disabled.
6. Repeat both themes and rotate. Verify the same normal download operations you
   used successfully in test2. The backend has not been changed by this patch.

The main ZIP includes actual host-test reports/reproduction metadata. Host
Robolectric cannot always draw native RippleDrawable positive controls, so a
skipped pixel comparison must not be interpreted as phone visual confirmation.

For optional diagnosis with a computer and USB debugging:

```sh
adb devices
adb install -r Ytdlp-G.apk
adb logcat -d -s AndroidRuntime YtdlpG-Glass > slider-logcat.txt
```

If Android reports incompatible update signatures, stop and preserve the old app's
data; this is not an edge-recognition failure. Share a short screen recording of
one gesture, device/API/theme and any exception, removing URLs/private content.
The matching instrumentation APK from the same run retains the previous optical
and performance probes; those tests were compiled, not run here. No new FPS or
GPU claim is made for this patch.
