package dev.britt.appliedcraftingbundles.client;

import appeng.client.gui.style.BackgroundGenerator;
import appeng.client.gui.style.Blitter;
import appeng.util.Icon;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** AE2's own GUI chrome (background tile and slot sprite). No new textures. */
final class Panels {
    /** AE2 palette DEFAULT_TEXT_COLOR. */
    static final int TEXT = 0xFF413F54;
    /** AE2 palette MUTED_TEXT_COLOR. */
    static final int MUTED = 0xFF878FA5;

    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        BackgroundGenerator.draw(w, h, g, x, y);
    }

    /** {@code x,y} is the item's top-left, same as a menu slot. */
    static void slot(GuiGraphicsExtractor g, int x, int y) {
        Blitter.icon(Icon.SLOT_BACKGROUND).dest(x - 1, y - 1).blit(g);
    }

    private Panels() {
    }
}
