package dev.britt.appliedcraftingbundles.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Placeholder, code-drawn GUI pieces until Pack Forge supplies real textures. */
final class Panels {
    static final int TEXT = 0xFF404040;

    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF555555);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
    }

    static void slot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
        g.fill(x, y, x + 17, y + 17, 0xFFFFFFFF);
        g.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
    }

    private Panels() {
    }
}
