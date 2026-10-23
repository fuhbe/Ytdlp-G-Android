# Square visual adaptation

Source repository: https://github.com/Lelonio/Square

Inspected and pinned commit: `17ed2b87974cf6fec9c531ef3f1438448bb2b729`.
`PROVENANCE.json` records exact original file paths and SHA-256 hashes. These
originals are retained unmodified as review material and corresponding source;
this directory is **not** an additional compiled source set.

Square is GPL-3.0. Its Backdrop catalog component headers identify the Apache-2.0
origin, Kyant0/AndroidLiquidGlass commit
`b18eb0ff12c616546a68c72e7d0097f1ab286c87`, Copyright 2025 Kyant.
GlassEffect carries the Convx Project (C) 2026 GPL notice. FloatingTabBar retains
Elyes Mansour's Apache notice. Original headers and both license texts are kept.
The APK includes Square-GPL-3.0, Square-catalog-APACHE-2.0 and Square-NOTICE.
Ytdlp-G remains GPL-3.0-only. No Square branding or artwork is reused.

## What actually ships

| Consulted Square file | Adaptation in the compiled Ytdlp-G source |
| --- | --- |
| DampedDragAnimation.kt | `squareglass/DampedDragAnimation.kt`: the independent position, velocity, pressure, X-scale and Y-scale Animatables and spring parameters. Physical normalized coordinates, monotonic timing and cancellable jobs replace catalog assumptions and release waiters. |
| DragGestureInspector.kt | `squareglass/DragGestureInspector.kt`: same gesture inspector callbacks and lifecycle; waits for horizontal slop, consumes owned drags, separates cancellation from release and rejects extra pointers. A cancelled drag cannot turn into a second child's click. |
| InteractiveHighlight.kt | `squareglass/InteractiveHighlight.kt`: spring-driven pressure and finger position, additive white light and radial highlight. A compatible gradient replaces the separate radial-light RuntimeShader; gains are reduced for the bright wallpaper. The actual lens and directional native specular are retained independently. |
| LiquidBottomTabs.kt | `GlassSlidingSegments.kt` / `SquareGlassMaterial.kt`: layered external + pre-label surface backdrop, press-driven frosting/lens, real pane remeasurement, velocity-based X/Y deformation, spring settlement, highlight, shadow and inner shadow. |
| GlassEffect.kt and backdrop/ | `SquareGlassMaterial.kt`: native captured-background lens with bounded 45% edge reach and directional specular. Uses the already shipped Backdrop 1.0.3 API, with rejected-shader and older-Android fallbacks. No Square global settings/configuration is imported. |
| LiquidBottomTab.kt | Integration keeps the existing Ytdlp-G icons, labels and callbacks. Labels are drawn once above the glass, with selected semantics and readable colors. No captured label replicas. |
| LiquidSlider.kt / LiquidToggle.kt | Consulted for shared motion, velocity deformation, physical direction and latest-callback synchronization. The existing four segmented controls reuse one adapted component. No slider or toggle feature is added. |
| LiquidButton.kt / Pressable.kt | Consulted for finite press/release springs and touch-following light. Existing action buttons and download actions remain unchanged. No additional long-click actions or Square settings are copied. |
| floatingtabbar/FloatingTabBar.kt | Consulted for bounds/semantics compatibility. Not compiled or transplanted: its player, search, shared-transition layouts and destinations would change Ytdlp-G's structure. |

The exact Backdrop 1.0.3 source JAR is also retained in `../`. The published
library supplies the real captured layer, blur and AGSL refraction. Square's
newer fork requires a broader toolchain change; it is not silently substituted
for 1.0.3. Rounded CornerBasedShape capsules keep that lens implementation
compatible; the Square continuous-path shape package is not imported.

## Intentional local integration differences

Segment bounds are measured from the real child layout rather than dividing a
hardcoded width. This handles RTL, unequal children, font scaling and changing
download counts. Content or geometry changes cancel a stale gesture. Tap,
keyboard and accessibility actions stay with the existing clickable controls;
the decorative lens has no click action or spoken duplicate. Drag commits once
on a valid release, never on cancellation or the current selected destination.

Square's large catalog expansion is bounded to the app's reserved track. Each
pane is remeasured while deforming, so the optical source follows its physical
bounds rather than stretching a bitmap. The captured source excludes the lens
and labels, preventing recursive feedback and ghost text. No blue fill is used
for these pucks. Only the existing action buttons retain their blue styling.

Finite Compose animations honor Android animation duration scale. There is no
permanent shimmer loop, wallpaper recapture of progress text, new setting,
download/network operation, or change to the runtime/toolchain/minSdk/ABIs.

Host tests and motion frames verify the real layout and gestures. Hardware
AGSL/refraction, physical touch feel, frame time, temperature and battery
acceptance require an Android device; compilation alone does not verify them.
