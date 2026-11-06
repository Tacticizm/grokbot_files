# Art needed (placeholders in use)

All textures are 16x16 PNG, vanilla style, to sit next to AE2's art. Placeholders are listed so they can be swapped without code changes.

## Bundle Encoder (flat terminal part)

AE2 terminal faces are three greyscale layers that AE2 tints with the cable colour (dark/medium/bright), drawn on top of AE2's shared `display_base` frame. Right now the model points straight at AE2's Pattern Encoding Terminal layers.

| Texture | Target path | Depicts | Placeholder now |
|---|---|---|---|
| bundle_encoder_bright | `assets/appliedcraftingbundles/textures/part/bundle_encoder_bright.png` | Brightest lit pixels of the screen: a bundle/pouch glyph with a small "+" or pencil to read as "encode bundle" | `ae2:part/pattern_encoding_terminal_bright` |
| bundle_encoder_medium | `assets/appliedcraftingbundles/textures/part/bundle_encoder_medium.png` | Mid-tone layer of the same screen glyph | `ae2:part/pattern_encoding_terminal_medium` |
| bundle_encoder_dark | `assets/appliedcraftingbundles/textures/part/bundle_encoder_dark.png` | Dark layer and screen background of the glyph | `ae2:part/pattern_encoding_terminal_dark` |

When these arrive, change `textures` in `models/item/bundle_encoder.json` and point `ae2/parts/bundle_encoder.json` at two new part models (`models/part/bundle_encoder_on.json` / `_off.json`, copied from AE2's pattern_encoding_terminal_on/off with our texture ids).

## Wireless Bundle Terminal (handheld item)

| Texture | Target path | Depicts | Placeholder now |
|---|---|---|---|
| wireless_terminal | `assets/appliedcraftingbundles/textures/item/wireless_terminal.png` | Handheld AE2-style wireless terminal (antenna, small screen) showing the bundle glyph | copy of our `bundle_pattern_encoded.png` |

Optional later: a `wireless_terminal_off.png` for when it has no charge or isn't linked.

## GUI (optional)

Screens are drawn in code (grey panels). If wanted later: a 256x256 sheet per screen (`textures/gui/bundle_encoder.png`, `textures/gui/wireless_terminal.png`).
