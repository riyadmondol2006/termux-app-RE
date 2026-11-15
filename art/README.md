# Termux RE artwork

The Termux RE mark is the Termux `>_` prompt from the app icon, with an `RE`
typed at the prompt above the underscore cursor.

| Colour | Hex | Used for |
| --- | --- | --- |
| Background | `#16191D` | icon and banner background |
| Foreground | `#FFFFFF` | chevron, underscore, wordmark text |
| Accent | `#FFB000` | `RE` |

## Sources

All shapes are drawn on the 108x108 adaptive icon grid. The mark is scaled by 0.9
around the centre (54,54), which keeps it inside the 66dp safe zone. These files
share the same path data, so if you change one, change the others too:

- `logo.svg`: master logo (rounded square). Used for the legacy launcher icon, `logo-512.png` and the store icon.
- `logo-round.svg`: round variant, used for the legacy round launcher icon.
- `../app/src/main/res/drawable/ic_foreground.xml`, `ic_background.xml`, `ic_monochrome.xml`: adaptive icon layers (Android 8.0+). The monochrome layer is used for Android 13+ themed icons.
- `../app/src/main/res/drawable/ic_service_notification.xml`: notification icon.
- `logo-banner.svg` (1280x640, for the GitHub social preview), `tv-banner.svg` and `feature-graphic.svg`: the wordmark lockup. The word "Termux" is [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) ExtraBold (SIL OFL 1.1), converted to outlines.

## Generating PNGs

The scripts need ImageMagick 7 (`magick`) built with librsvg, or `rsvg-convert`. If `zopflipng` is installed, they use it to compress the PNGs. They can be run from any directory, and the output is byte-for-byte reproducible.

| Script | Output |
| --- | --- |
| `generate-launcher-images.sh` | `mipmap-*/ic_launcher.png` and `ic_launcher_round.png` (48 to 192 px) |
| `generate-big-icon.sh` | `logo-512.png`, `logo-banner.png`, the fastlane `icon.png`, and `build/logo-round-512.png` |
| `generate-tv-banner.sh` | `drawable/banner.png` (320x180) and `build/tv-banner.png` (1280x720) |
| `generate-feature-graphic.sh` | `build/feature-graphic.png` (1024x500, no alpha) |

`build/` is ignored by git.
