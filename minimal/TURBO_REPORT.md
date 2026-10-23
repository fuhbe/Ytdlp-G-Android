# 0.6.2 — playlist recovery and supervised media processing

## Confirmed defects and their limits

The previous QueueWorker had one exception boundary around the entire playlist.
An unavailable fifth item could exit the loop before items six onward were
attempted. Requiring every selected item to succeed also prevented a useful ZIP
from a partially successful playlist. Independent item records now retain the
original position, result, reason and retry eligibility; a warning distinguishes
partial success from complete success. Systemic storage/runtime failures stop
admission instead of repeating the same failure across every item.

The previous worker reset progress at the MP4 postprocessor marker and displayed
one generic conversion message. The FFmpeg wrapper buffered its output until
completion. This confirms why a long conversion could display 0% without useful
stage information; it does not establish the exact cause of an unfinished
conversion on the Redmi without its diagnostics. Real download bytes, FFmpeg
media time, validation, ZIP and export now have separate stages. Unknown work
is indeterminate. Weighted overall progress never falls back to zero between
video/audio/processing stages. There is no fabricated timer or success state.

A separate pipe deadlock was reproduced with the frozen 0.6.1 stream reader and
a real child process: an exception in a progress callback terminated the reader,
leaving a writer blocked on its full pipe. The patched reader bounds lines/logs,
delivers UTF-8 and final unterminated lines, and the bridge stops the process
while draining after a callback failure. This is a demonstrated failure mode,
not proof that this exact exception occurred on the user's phone.

## Behavior and recovery

- Native fragmented transfers use at most four fragments, two on metered
  connections and one under constrained memory, power or heat. At most two MP3
  items run together on an unmetered, cool device with sufficient memory; MP4
  processing remains serial. Admission is re-evaluated between items. Severe
  heat delays new work. Direct HTTP remains a native transfer. No aria2 binary,
  proxy, anti-bot workaround or additional user tuning panel is introduced.
- Retry/backoff is bounded. Permanent unavailable/access failures are reported
  and skipped; transient failures get limited retries. Three consecutive network
  or rate-limit failures stop the batch. Manual retry targets only eligible
  failed items and preserves completed files and original positions.
- H.264/AAC is preferred and copied/remuxed where compatible. Only incompatible
  streams are transcoded; video stays at its actual selected height, capped at
  2160p. The conversion preset changes from fast to veryfast at CRF18, trading
  compression efficiency for potential CPU savings. Audio conversion cannot
  restore detail missing from its source.
- Activity supervision uses observed bytes, CPU ticks or FFmpeg media time,
  rather than an absolute job deadline. Transfer inactivity is limited to 180s,
  processing inactivity to 600s; observable progress extends the deadline. A
  monitor failure is reported, not silently ignored. Pause/cancel stops owned
  native trees and closes owned export descriptors.
- FFprobe reads packet counts and checks codecs, duration, dimensions and
  expected stream/frame completeness before a file becomes completed. Validated
  output is cached so a destination failure does not require downloading and
  converting again. Rejected completed media is moved aside on an integrity
  retry; original sources and resumable partials remain available. Intermediate
  media is cleaned only after the completed record is committed.
- A ZIP requires at least two selected items but can contain one successful item
  when another failed. No empty ZIP is exported. Entry CRCs are verified, original
  files are kept and a changed partial-success ZIP preserves the prior exported
  revision. An atomic export journal avoids duplicate files after interruption.
- Existing database rows receive explicit defaults without changing IDs, saved
  URIs or file paths. No destructive schema migration is used. Liquid Glass,
  format choices, folder picker and existing screens remain in place. The
  manager adds phase details and a compact report of items not downloaded.
- Diagnostic events contain structural failure codes, stage and process counts;
  they omit URLs, titles, tokens, cookies, commands and exception messages.

## Verification evidence

The workflow runs the JVM suite, real HTTP/HLS/DASH downloads and FFmpeg work,
full host decoding, packet validation, paired benchmarks, packaging, final DEX
bootstrap inspection and the existing positive/negative R8 archive regression.
Download **Minimal-verification** from the exact successful Actions run to inspect
JUnit XML, mappings and JSON rather than relying on this prose.

New regression coverage includes a 16-item playlist with failures at 5 and 12
and fourteen real MP3 outputs; real compatible and split-stream MP4 copies,
VP9/Opus conversion, incompatible MP4 source preservation, absent HLS fragment
rejection, HTTP 503 retry and Range-based resume, no repeated completed downloads,
permanent versus eligible retry, systemic failure, monotonic bounded parallel
work, pause transitions, old database JSON, export journal recovery and the real
pipe-deadlock negative control. UI tests retain the original light/dark and
screen-width checks and add indeterminate progress and partial-result reports.

Four instrumented scenarios target the actual optimized ARM64 release: bundled
native MP3/MP4 processing, cancelling a real FFmpeg child, foreground/background
partial-success ZIP, and worker pause/resume. The test APK is built separately;
these scenarios have **not been executed on a device**. See DEVICE_TESTS.md.

The paired benchmark uses generated six-second 360p media and a local HTTP
server, three repetitions per case. It records analysis, first data, transfer,
postprocessing, validation, export, CPU, sampled process-family memory and output
size. HLS requests deliberately include 80ms latency. The report includes slower
results as well as faster results. It is not a Redmi, Internet, YouTube or
Snaptube comparison. Exact JSON and medians accompany each build. Hardware,
network and source codecs can dominate the result.

Still requiring device acceptance: the user's live YouTube link and sixteen-item
playlist, genuine blocked/unavailable entries, a long conversion, thermal and
battery behavior, background restrictions, storage-provider revocation/full disk,
process death/reboot, and the final visual/keyboard check on the Redmi 10 Prime
Android 12. Host YouTube access was rejected by the environment proxy; no live
success is claimed. Use LOGCAT.md if the phone still fails.

## Changed implementation

`QueueWorker.kt`, `BatchRunner.kt`, `Models.kt`, `Store.kt`, `QueueActions.kt` and
`AppModel.kt` implement independent queue results and recovery. `DownloadRequest.kt`,
`TransferProtocol.kt`, `ActivityGuard.kt`, `TurboPolicy.kt`, `AdaptiveAdmission.kt`,
`Engine.kt`, `ProcessOutput.kt`, `DownloadCache.kt`, `MediaValidator.kt`,
`Storage.kt` and `ExportStreams.kt` implement real transfer/processing/storage
work. `MainActivity.kt` and `Diagnostics.kt` expose the state safely.
`runtime_patch.py` and `runtime/StreamProcessExtractor.kt` patch the pinned GPL
bridge explicitly; the source manifest records prepared hashes. The GlassMP4
plugin, build rules, tests, benchmark scripts, packaging and minimal Actions
workflow are updated. Other application editions remain preserved.

## Signing and existing data

This workflow uses an automatically generated installation test key. A clean CI
runner creates a different certificate, so Android can refuse an in-place update
from 0.6.1. The old private key cannot be recovered from its APK. Do not uninstall
while needed private partials/queue data remain: uninstall removes them and
settings. First finish/export what is needed; already exported Downloads/SAF
files remain. Code-level database compatibility does not override Android's
signature rules. Stable production updates require an owner-controlled signing
key configured securely for future builds; no private key is committed here.
