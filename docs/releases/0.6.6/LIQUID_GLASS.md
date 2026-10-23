# Liquid Glass in Ytdlp-G 0.6.6

The stable release preserves the implementation approved on the owner's Android 12 device. The final promotion changes version/signing metadata and English presentation literals; it does not change the renderer or interaction logic.

## Shared selectors

MP3/MP4, Normal/ZIP, Downloads/Completed and bottom navigation use the same measured-bounds sliding component. It handles physical option order, including RTL, tolerant intermediate selection and generous endpoint acquisition. Once horizontal touch slop claims a pointer, the same pointer remains owned through diagonal movement outside the visual bar. Release selects by its horizontal coordinate; real cancellation does not commit.

The original spring channels, deformation, progressive glyph illumination, reflection parameters and local Material ripple suppression are unchanged. Captured moves are consumed before ordinary ancestor scrolling, while unclaimed vertical intent can scroll the screen.

## Rendering paths

| Android and conditions | Existing path |
| --- | --- |
| Android 13+ with supported hardware rendering and sufficient resources | Backdrop lens shader / AGSL refraction |
| Android 12 / 12L with supported hardware rendering and sufficient resources | Cached real background/glyph textures and Canvas mesh sampling |
| Android 8–11, software rendering, low memory, reduced memory class or power saver | Compatible tinted glass with the existing gesture and animation behavior |

The shared content source includes actual application content. The Android 12 mesh path freezes cached textures while held; source capture does not run for every drag sample. Reflections and progressive icon tint are applied in the existing draw path. Compatibility failures preserve the fallback rather than requiring unavailable shaders.

The source-level preservation check and held-motion counters accompany the release. Software screenshots illustrate the compatible path; they do not prove hardware refraction or a particular frame rate. No GPU measurements are invented for the final promotion.

## Provenance

- Square: `Lelonio/Square`, pinned at `17ed2b87974cf6fec9c531ef3f1438448bb2b729`; original source and notices in `minimal/vendor/square/`.
- Backdrop: version 1.0.3, with the corresponding source archive and Apache-2.0 notices included.
- Square-derived and catalog-derived files retain their respective GPL-3.0 and Apache-2.0 notices. Bundled dependencies retain their own licenses.
