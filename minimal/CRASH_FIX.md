# 0.6.1 runtime bootstrap crash investigation

## Reproduced release defect

The optimized 0.6.0 APK lost a required upstream ProGuard rule:

```proguard
-keep class org.apache.commons.compress.archivers.zip.** { *; }
```

Apache Commons Compress 1.12 registers ZIP extra-field implementations in
`ExtraFieldUtils.<clinit>` through `Class.newInstance()`. R8 could not infer this
construction. Inspection of the previously published APK, resolving class names with the
matching local 0.6.0 build mapping, found all 13 registered implementations
abstract and without public no-argument constructors. This is an examination of the delivered DEX, not a
debug-build assumption.

Using the same Commons Compress dependency, the AGP 8.13.2 R8 implementation and
the actual Python/FFmpeg ZIP payloads extracted from that APK, the JVM regression
reproduced:

```
java.lang.ExceptionInInitializerError
Caused by: java.lang.RuntimeException: class b.b is not a concrete class
```

The unshrunk control opens both archives. R8 without the rule fails before
extraction. R8 with the restored rule reads every entry in both archives:
Python: 2,998 entries / 88,500,519 uncompressed bytes;
FFmpeg: 13 entries / 37,729,132 uncompressed bytes.
This harness uses R8 classfile output; the separate DEX guard checks the Android
APK itself for all 13 concrete classes and their public no-argument constructors.

Both Analyze link and Check/update yt-dlp call native runtime initialization.
Its previous `catch (Exception)` could not catch `ExceptionInInitializerError`,
which is a `LinkageError`. Thus the bootstrap defect can terminate either action
before a YouTube request. Updating the extractor alone cannot repair it because
the updater also needs the same bootstrap.

This identifies and reproduces a real crash defect in the shipped package.
The reported Redmi 10 Prime / Android 12 has not supplied a crash stack trace;
the exact device exception still needs correlation if the new APK fails there.
It would be inaccurate to claim a successful physical-device test.

## Changes

- `app/proguard-rules.pro`: restore the upstream ZIP reflection rule; retain
  source line attributes for retracing. R8/resource shrinking remain enabled.
- `app/build.gradle.kts`: version 0.6.1, code 61.
- `Diagnostics.kt`: bounded local structural logs with stages, version, SDK/ABI,
  exception classes and stack frames. No exception messages, URLs, titles,
  cookies, arguments or paths. A crash recorder delegates unexpected fatal
  errors to Android's previous handler; it never pretends those errors succeeded.
- `BundledRuntime.kt`: check all six bundled runtime files, executable ELF
  headers, supported ABI, readability and execute permission before native init.
- `Engine.kt`: validate and initialize on IO dispatchers; diagnose initialization,
  process and update stages; turn recoverable linkage/bootstrap failures into
  actionable failures while preserving their original cause.
- `AppModel.kt`: injectable backend for controlled tests, safe failure/cancellation
  transitions and diagnostics copying. Production still uses the real engine.
- `Store.kt`: install diagnostics during application startup.
- `MainActivity.kt`: add Copy diagnostics in Settings. Existing glass styling,
  screens and download features are preserved.
- `AnalyzeActionTest.kt`, `RuntimeSafetyTest.kt`, `GlassUiTest.kt`: Android 12
  action/recovery, cancellation, missing/incompatible native files, private-data
  exclusion, IO execution and loading UI on API 26/31/35.
- `dex_bootstrap.py`, `regression_zip.py`, `regression/ZipBootstrapProbe.java`:
  inspect the actual optimized DEX and run real archive/R8 negative and positive
  controls. A missing keep rule now fails the distribution checks.
- `verify.py`, `package.py`, `.github/workflows/minimal-glass.yml`: verify the
  final APK before publishing, deliver source/instructions and retain test
  reports, regression results and the exact R8 mapping for each build.

## What the checks establish

Controlled UI errors are deliberately injected to test app recovery. They do
not represent a successful YouTube request. The archive/R8 regression, in
contrast, exercises the real ZIP implementation and bundled runtime payloads.
Existing HTTP/HLS/FFmpeg host tests exercise genuine metadata extraction,
downloads, MP3 conversion, MP4 resolution selection, copy and conversion paths.

The final build must pass all three layers: automated tests, actual APK
signature/dependency/DEX inspection, and real archive/R8 regression. Actions
publishes the corresponding XML/JSON reports and R8 mapping. See
[VERIFICATION.md](VERIFICATION.md) for device-dependent acceptance checks and
[LOGCAT.md](LOGCAT.md) if the phone still fails.
