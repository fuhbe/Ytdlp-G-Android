# Run the actual optimized ARM64 device tests

These four instrumented scenarios are **built, not executed by the host workflow**.
No emulator is required. Use an ARM64 Android 8+ device; the Redmi 10 Prime with
Android 12 is the intended acceptance target. Install Android Platform Tools,
enable USB debugging and accept the device prompt.

Download **Ytdlp-G-Android-Minimal** and **Android-device-tests** from the SAME
successful Actions run. Extract both ZIPs and check SHA256SUMS.txt. Finish or
pause other jobs first. A disposable test installation is recommended: these
scenarios create small queue entries and downloaded files. Do not uninstall an
existing installation containing needed private queue data or partial files.
The main/test APK certificates must match. Fresh CI test keys may differ from
an older installed version; see TURBO_REPORT.md before replacing it.

```sh
adb devices
adb install -r Ytdlp-G.apk
adb install -r Ytdlp-G-tests.apk
adb shell am instrument -w -r com.fuhbe.ytdlpg.minimal.test/androidx.test.runner.AndroidJUnitRunner
```

The runner exercises real bundled Python/yt-dlp/FFmpeg/FFprobe, compatible and
converted MP4, MP3, cancellation of a native FFmpeg child, a background worker
with one unavailable item and a nonempty ZIP, and worker pause/resume with a
partial download. All requests use a local fixture server on the device. Tests
fully decode generated output using the bundled Android FFmpeg. A compiled test
package does not count as a passing test run; retain the complete runner output.

Afterward test actual public YouTube links manually: MP3 and MP4 at available
heights; a playlist including an unavailable item in the middle; retrying only
eligible failures; ZIP partial success; folder changes; pause/cancel; background
execution; opening the saved outputs; and a long conversion that advances or
reports an actual failure. Confirm video is labeled MP4 rather than MP3.

For a failure, copy Settings → Copiar diagnóstico and capture the same attempt
with the instructions in LOGCAT.md. Include exact app version, device/Android
version, selected format/resolution, expected item count and resulting item
statuses. Review AndroidRuntime output for private information before sharing.
Do not send account tokens or browser cookies.
