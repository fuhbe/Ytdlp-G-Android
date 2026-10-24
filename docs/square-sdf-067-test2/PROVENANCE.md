# Actual source reuse and porting

Square: `Lelonio/Square@5df51684eec348e283126836449d9ac4aa62541e`.
Morph: `sdegenaar/liquid_glass_widgets@5d2859796ef0f6964e2fdf25962b033eede27814`.
Exact original paths/hashes are in both new vendor `PROVENANCE.json` files. Existing `minimal/vendor/square/` is unchanged.

| Original | Compiled incorporation | Necessary adaptations / license |
| --- | --- | --- |
| Square `shapes/*`, including ContinuousRoundedRectangle/ContinuousCapsule, G2Continuity and path/core | `buttonsquare/shapes/*` —14 actual Kotlin source files | **Package/import namespace replacement only**, verified byte-for-byte after normalization; Apache-2.0 Kyant0/Capsule headers retained |
| Square `LiquidButton.kt` layer matrix | `SquareButtonMaterial.kt::squareButtonTransform` + `ElasticGlassButton.kt` | Literal tanh0.05,4dp pressure/drag scale and directional sin/cos recipe; separate documented bounded long-pull extension. Decorative Canvas/HWUI matrix keeps the sampling layout/hit target fixed. No old improvised outline |
| Square `InteractiveHighlight.kt` | `SquareButtonMaterial.kt` radial RuntimeShader and cached optics | Original radial shader and0.08/0.15 light/radius1.5 gains; guarded API33 instantiation, compatible radial fallback; separate crisp label layer. Direct finger state avoids upstream per-MOVE coroutine snap |
| Square `GlassEffect.kt` | `squareButtonMaterial` with pinned Backdrop1.0.3 | ContinuousCapsule,2dp blur, bounded45% lens reach, film/rim, safe API tiers. Music settings, player/chrome globals and newer fork APIs are not imported |
| Square `DampedDragAnimation`, LiquidToggle/BottomTabs | Consulted originals retained; independent button motion, **no replacement of accepted selectors** | Original spring vocabulary; direct input and release-only jobs instead of catalogue springs chasing MOVE. Existing `squareglass/*` selector adapters remain byte-identical |
| MIT `liquid_morph_physics.dart` | `LiquidMorphPhysics.kt` | Phase/state fields, back-out amplitude2.5 with adaptive floor0.7, size easing, anchor decay/closing restoration, blend/attenuation, push and container pulse ported to Kotlin. Flutter SDK easing represented by Compose CubicBezierEasing with the same coefficients |
| MIT `glass_morph_controller.dart` | `GlassMorphMenuState`, one reversible Compose Animatable | Actual code open mass1/stiffness120/damping16, close200/21, close hint-3.2. Preserve current velocity on interruption. Android duration scale inherited; no Flutter runtime/library |
| MIT `glass_menu_internal.dart` | `GlassMorphMenu.kt`, `metaballScene` | Two blobs/ghost handoff, origin-aligned expanding menu, clipped readable items and one existing Popup. Native coordinate/inset/RTL measurement; no new menus or choices |
| MIT `shaders/sdf.glsl` | `GlassMetaballSdf.kt` + `METABALL_SHADER` | Actual rounded-box distance and `min(a,b)-max(k-abs(a-b),0)^2/(4k)` smooth union. Exactly two shapes, so no16-shape dispatcher or Flutter include/uniform arrays |
| MIT geometry/render shaders | `MetaballGlassSurface.kt` | Actual SDF central gradient, normalized distance, surface normal and Snell displacement sampling a real cached Android RenderNode input. Native AGSL syntax/compositing, bounded displacement and premultiplied coverage |

## Deliberate native extensions, not claimed upstream behavior

- Long pulls add bounded tanh travel on top of Square's0.05 derivative; scale limits1.48x/1.38x, translation terms are finite. No mesh/Bezier pinch or hole can form in one affine G2 body. The semantic rectangle never follows it.
- Root-window observation forwards the original event and return value unchanged, preserves vertical scrolling and survives local cancellation/clip changes. It is isolated from all four selector recognizers.
- Direction/placement changes reconcile the currently displayed blob geometry with the new physical curve. This avoids a discontinuity when switching between the reference's distinct open/close paths. Existing velocity is preserved rather than blindly reset.
- Coincident-blob blending tapers to zero near final containment to avoid smooth-min's k/4 inflated silhouette at handoff. The real quality trigger has18dp corners; the droplet transitions from/to those exact corners.
- The overlay stays until the close spring settles, retaining its undershoot instead of truncating it at the first zero crossing. This differs from the reference's first-crossing handoff latch because the native trigger has no additional catch-animation controller.
- Older systems extract the SDF zero isoline through marching squares with reusable bounded arrays. This is real SDF geometry, **not real optical lens refraction**.

## Documentation versus code

The required guide describes an older shared120/16 spring and close hint-2.5. The pinned executable Dart source now defines asymmetric close200/21 and hint-3.2, adaptive scaling, a different closing trajectory/anchor ramp and proximity blend. The port follows the actual pinned source, recording those differences rather than mixing incompatible generations.

No claim of pixel-identical Apple/iOS implementation is made. No new Weather/Edit reference video was attached to this TXT; available conversation stills, real open-source code and before/after app captures guide the comparison. Missing video/pixel/GPU evidence is stated in the delivery report.
