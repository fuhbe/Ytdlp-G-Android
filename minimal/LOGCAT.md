# Android 12 verification and crash diagnostics

## On the phone

1. Install the new **0.6.1 Ytdlp-G.apk**, not the older APK in download history.
   Confirm the version in Android Settings → Apps → Ytdlp-G.
2. Test Analyze link using one accessible public YouTube link. Wait for the first
   runtime extraction; check that the UI remains responsive and can cancel.
3. With no active jobs, test Settings → Check and update yt-dlp
   (Portuguese: “Verificar e atualizar yt-dlp”). Record the visible result.
4. If either action reports an error, Settings → Copy diagnostics
   (“Copiar diagnóstico”) copies a bounded report. Send it with the exact action,
   app version, phone model and Android version. This report omits URLs, titles,
   cookies, commands and exception messages.
5. If the app closes, reopen it and copy the saved diagnostic report. Unexpected
   fatal errors are recorded and still passed to Android's normal crash handler.

The release variant currently uses an installation test key, generated in each
clean CI environment. If Android refuses an update because the signatures differ,
uninstall the previous test app before installing this APK. This removes its
private queue, settings and temporary files; first finish/export anything needed.
Previously exported files in Downloads or your selected external folder remain.
Do not uninstall merely to troubleshoot a failing analysis before copying logs.

## Capture the actual crash with a computer

Install Google's Android SDK Platform Tools. Enable Developer options and USB
debugging on the Redmi, connect it by USB and accept the phone's debugging prompt.
Then run:

```sh
adb devices
adb logcat -v threadtime YtdlpG:I AndroidRuntime:E '*:S' > Ytdlp-G-logcat.txt
```

Keep the command running, open Ytdlp-G, reproduce exactly one failing action,
then stop capture with Ctrl+C. This retains the initialization stages and Android
fatal exception, including its `Caused by` chain. Unlike the app's sanitized
report, AndroidRuntime output may contain exception messages from third-party
code. Review it and remove URLs, tokens, file paths or account information before
sharing. Do not provide browser cookies or access tokens.

The test APK is named `Ytdlp-G.apk`; its application ID is
`com.fuhbe.ytdlpg.minimal`. Include version, action, Android version and time of
the attempt. For an unresponsive app, also state whether Cancel analysis works.

## Maintainer interpretation

- `stage=RUNTIME_INIT`: package preflight and native archive extraction.
- `stage=PROCESS`: Python/yt-dlp execution; failures here may be network or
  extractor errors rather than initialization failures.
- `stage=UPDATE`: updater/runtime initialization and engine update.
- `type=java.lang.ExceptionInInitializerError` or `NoClassDefFoundError`: inspect
  every cause. The 0.6.0 ZIP regression specifically involved ExtraFieldUtils.
- `UnsatisfiedLinkError`, ELF/ABI or execute failures: compare installed ABI and
  native package entries; do not replace a real error with a success state.

For obfuscated class names, download **Minimal-verification** from the same
Actions run as the APK. It contains `mapping/githubRelease/mapping.txt` (artifact
paths may omit the common prefix). Use Android Studio's Analyze Stack Trace /
Retrace with that exact mapping; a mapping from another build is insufficient.
The JSON bootstrap reports and XML test results are in the same artifact.

Live YouTube downloads, native startup and background execution on the Redmi
remain phone acceptance checks. Host/JVM test success does not assert those
checks have happened.
