# Tact's Default Realism: LaserIO

An add-on resource pack for **LaserIO**, made to sit alongside **Tact's Default Realism** (128x).
It redraws LaserIO's blocks (laser node, laser connector, advanced laser connector), items (cards, card layers,
filters, overclockers, logic chips, laser wrench) and laser beam textures at 128x (8x vanilla) in the main pack's
style: crisp pixel grid, top-left light, shiny light-steel, gunmetal and gold casings, while keeping LaserIO's original
colours, shapes and UV layouts. The laser node keeps its animation timing, and colour-tinted parts (the laser-coloured
block faces and the card channel stripe) stay greyscale so in-game tinting works.

27 textures.

## Requirements

- Minecraft 26.1.2 with NeoForge 26.1.2
- LaserIO **1.10.2**
- **Tact's Default Realism** (the main pack), enabled alongside this add-on

## Install

1. Put `Tacts-Default-Realism-LaserIO.zip` in your `.minecraft/resourcepacks` folder (do not unzip it).
2. In Options > Resource Packs, enable both **Tact's Default Realism** and **Tact's Default Realism: LaserIO**.
3. Either order works (the packs share no files).
4. Click Done. If textures look scrambled after changing packs (blocks showing the wrong texture), restart the game; at 128x a live reload can mis-stitch the texture atlas.

## Notes

- GUI and guide-book textures stay as LaserIO ships them (their fixed UV layouts don't allow a redraw at a different size).
- A few textures that LaserIO ships but never uses (`laser_node_orig`, `card_chemical`, `dire`) are not included.
- Different LaserIO versions may add or rename textures; anything not covered falls back to LaserIO's own texture.
