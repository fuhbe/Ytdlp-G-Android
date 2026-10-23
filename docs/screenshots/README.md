# Interface previews

These PNGs are rendered from the actual 0.6.2 Compose interface using Robolectric
native graphics. They use sample metadata and queue states, with placeholder
thumbnails. They are not generated design mockups, physical-device captures or
proof of a successful network download. Interface text remains Brazilian
Portuguese, matching the APK. Public descriptions and captions are English.

`single-video.png`, `playlist.png`, `downloads.png` and `dark-theme.png` are
produced by `docs/tools/ReleasePreviewRender.kt`. To reproduce them with the
minimal module's documented JDK/SDK setup, temporarily copy that file to
`minimal/app/src/test/java/com/fuhbe/ytdlpg/simple/ReleasePreviewRender.kt` and run:

```sh
bash minimal/gradlew -p minimal :app:testGithubDebugUnitTest --tests com.fuhbe.ytdlpg.simple.ReleasePreviewRender --max-workers=1
```

Remove the temporary copy afterward. This renders documentation fixtures; it
does not build, modify or re-sign the release APK. No fixture metadata is used by
the production downloader. The 57-test release verification remains tied to the
original successful build, independently of these four documentation renders.

The icon in `../branding/icon.svg` is a direct export of the shipped Android
launcher vector; `icon.png` is its 512px raster export, drawn with Android's actual vector renderer by the same documentation fixture. The app icon itself has
not been changed.
