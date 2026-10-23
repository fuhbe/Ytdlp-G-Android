# Verified 0.6.6-test3 delivery

[Download APK package](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40018985216/artifacts/12530596238) ·
[Matching device-test APK](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40018985216/artifacts/12530915403) ·
[Full verification reports](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40018985216/artifacts/12529319020)

Extract **Ytdlp-G.apk** from the main Actions ZIP. The successful
[Actions run](https://github.com/fuhbe/Ytdlp-G-Android/actions/runs/40018985216)
compiled **eca84b671056349075db83ab2eab6a9590dcdeb3**. All three artifacts were
actually downloaded and verified. This report update does not rebuild or re-sign
the APK. Stable v0.6.4 and `main` remain untouched.

| Verification | Result |
| --- | --- |
| APK | 59,626,044 bytes; ARM64; Android 8+; version 0.6.6-test3/code68 |
| APK SHA-256 | `c4d7efd33a5b60eece2721508ee91679bd61d5daa73b0c2911f18b47905bea4a` |
| Signing certificate SHA-256 | `209656bb1c44b9a0858087b0b775f081c7b2494bb8c8e20566c4eed6e815c8ef` |
| Host suite | **172 passed, 2 skipped**, 174 cases; no failures |
| New edge regression cases | All 14 passed, including both edges/margins, initial options, widths, real host window sizes, API26/32/35, RTL, final UP, settling and all four actual controls |
| Ripple interactions | Scoped indication configuration and actual press/release/cancel/tap/drag/outside-button checks passed |
| Ripple pixel comparison | Explicitly skipped: the host did not draw its native RippleDrawable positive control. Identical reference images are not accepted as proof of ripple removal |
| Glass response | 7488 actual software pixels changed on press; custom glass feedback remains |
| Scope | 162 protected files unchanged; entire rendering/layout/deformation/optical program unchanged except the requested local Material indication |
| Native payload | All 7 `.so` payloads byte-identical to delivered test2; bundled engine checksum verified |
| Media | Unchanged real HTTP/HLS, MP3 and MP4 fixture checks passed; no new live YouTube claim |
| R8/ZIP | 13 bootstrap checks and actual positive/negative regression passed |
| Held-motion regression | 60 samples; zero extra composition/source recordings/readbacks in the host probe |
| Device tests | 10 methods compiled, signatures match app APK; **not executed** |
| Physical phone / emulator | **Not executed here**; visual ripple confirmation remains pending on the Redmi |

The other skipped pixel test is the pre-existing host AGSL limitation. All 158
previous cases remain represented, with one explicit requirement migration:
`releaseBeyondRightEdgeDoesNotNavigate` now asserts the captured endpoint selection
as `releaseBeyondRightEdgeSelectsTheExtremeDestination`. The other 157 identities
and expectations remain. This does not remove vertical, outside-start or
multi-pointer cancellation checks.

This build uses a different development certificate from test2 and stable 0.6.4.
Android may reject an in-place update. Preserve the working app/data and do not
blindly uninstall a copy with queue/history you cannot lose. The package ID is
unchanged and prevents side-by-side installation.

[Cause, precise changes and modified file list](REPORT.md) ·
[Phone acceptance instructions](DEVICE_CHECK.md) ·
[Actual failing test2 result](REPRODUCTION.json) ·
[Machine-readable downloaded-artifact verification](DELIVERY.json) ·
[Actual CI results](VERIFIED.json)
