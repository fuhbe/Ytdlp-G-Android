# Ytdlp-G 0.6.6-test4 — diagonal slider gestures

This test update changes only captured-gesture tracking, release-target recognition and the test version identifier. Published 0.6.6-test3 and stable 0.6.4 remain unchanged.

## Root cause and fix

The test3 release-target function still rejected a pointer whose vertical coordinate was outside the slider height and padding. A horizontal drag could therefore reach its intended option and spring back on release because the target was rejected. `GlassDiagonalGestureTest.diagonalReleaseAboveTheTrackReproducesTheReportedReturn` reproduces the failure on the original production component: expected MP4, received the previous MP3 selection. [Original failure](REPRODUCTION.json).

Once the existing horizontal touch-slop recognizer claims a drag, it retains the same pointer ID. Owned moves and release are now processed at Compose's **Initial** event pass, before ordinary parent scroll handlers at Main. The release target uses the pointer's horizontal coordinate and measured option bounds, including padding and horizontal overshoot. A finite vertical coordinate may lie above or below the control; that alone never cancels an owned drag.

Before horizontal recognition, taps and vertical scrolling keep their existing event path. An actual Android cancellation, missing captured pointer or second-pointer takeover still cancels without selecting another option. The patch does not enlarge any visual component or change optical rendering, spring parameters or highlight animation.

## Preservation

The MP3/MP4, Normal/ZIP, Downloads/Completed and bottom-navigation selectors share the corrected component. `verify_scope.py` checks the approved baseline byte-for-byte outside four allowed production files. It additionally verifies that the shared drawing/layout source is identical apart from its release-target call, optical calculations remain identical, and only owned-event dispatch changes in the recognizer. Download engines, native dependencies, workers, services, storage, wallpaper assets and permissions are preserved.

Three earlier gesture tests explicitly asserted that vertical escape cancels an owned drag. Their expectations are migrated to the newly requested behavior; their independent vertical-intent and real-cancellation checks remain. Two older target-arithmetic assertions also migrate from rejecting vertical escape to retaining the horizontal destination. Endpoint, ripple, intermediate tolerance, accessibility, RTL and existing optics tests remain in the full suite.

## Validation

The new production-UI pointer tests cover upward/downward diagonals, releases inside/outside the track, 280/360/600 px widths, initial and target choices, API 26/32/35 software configurations, RTL, a final finger-up without a last move event, exit/reentry, true cancellation, multitouch, ancestor event consumption, normal parent scrolling, spring settlement and all four actual application selectors.

Automated UI results are Robolectric native Skia software results. They do not establish device GPU frame rates or physical-device behavior. See [phone checks](DEVICE_CHECK.md). The final `VERIFIED.json` is generated only from the actual complete suite and packaged APK; a successful compilation alone is insufficient.

## Verified APK

[Download and actual verification results](DELIVERY.md). The published artifact has been downloaded and checked independently. Phone acceptance is pending.
