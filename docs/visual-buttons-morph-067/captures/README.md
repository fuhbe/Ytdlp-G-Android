# Actual experimental app captures

These are genuine application/Popup-window captures with **sample metadata**, rendered by **Robolectric native Skia in software**. They are not mockups, emulator runs, physical-phone screenshots or proof of GPU optics/performance. GIFs crop the original captured app pixels to show the interaction clearly; replay is slowed for inspection, not real-time phone video. Full uncropped frames, state metadata and hashes are in the verified Actions artifact. [Provenance](PROVENANCE.json).

| Elastic action button — light | Elastic action button — dark |
| --- | --- |
| ![Actual light button press, pull and return](button-light.gif) | ![Actual dark button press, pull and return](button-dark.gif) |

| Quality menu morph — light | Quality menu morph — dark |
| --- | --- |
| ![Actual light origin, liquid neck, panel and reverse closure](menu-light.gif) | ![Actual dark origin, liquid neck, panel and reverse closure](menu-dark.gif) |

## Full application sequences

![Light button sequence](button-light-sequence.png)
![Dark button sequence](button-dark-sequence.png)
![Light menu sequence](menu-light-sequence.png)
![Dark menu sequence](menu-dark-sequence.png)

## Approved baseline versus experimental menu

![Actual baseline and experimental application windows with sample data](baseline-versus-test.png)

The neck silhouette is real custom geometry. In these software captures it uses the compatible tinted material, not physical GPU refraction. Android12 menu-neck captured-screen blur is not an optical lens. See [rendering limits](../README.md) and [Redmi acceptance](../TESTING.md).
