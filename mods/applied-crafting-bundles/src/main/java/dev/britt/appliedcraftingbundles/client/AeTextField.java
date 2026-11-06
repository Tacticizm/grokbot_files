package dev.britt.appliedcraftingbundles.client;

import appeng.client.gui.style.Blitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Edit box drawn with AE2's text-field widget texture. No new art. */
final class AeTextField extends EditBox {
    private static final Blitter FIELD = Blitter.texture("guis/text_field.png", 128, 128);
    /** AE2 palette TEXTFIELD_TEXT. */
    static final int TEXT = 0xFFF2F2F2;
    private final int outerX;
    private final int outerY;
    private final int outerW;

    AeTextField(Font font, int x, int y, int width, int height, Component message) {
        super(font, x + 2, y + 2, Math.max(8, width - 4), Math.max(8, height - 4), message);
        this.outerX = x;
        this.outerY = y;
        this.outerW = width;
        setBordered(false);
        setTextColor(TEXT);
        setTextColorUneditable(0xFF878FA5);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        if (isVisible()) {
            int yOffset = isFocused() ? 24 : 0;
            int right = outerX + outerW;
            FIELD.copy().src(0, yOffset, 1, 12).dest(outerX, outerY).blit(graphics);
            int mid = Math.min(126, outerW - 2);
            FIELD.copy().src(1, yOffset, mid, 12).dest(outerX + 1, outerY).blit(graphics);
            FIELD.copy().src(127, yOffset, 1, 12).dest(right - 1, outerY).blit(graphics);
            super.extractWidgetRenderState(graphics, mouseX, mouseY, partial);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) {
            return true;
        }
        // Typing must not close the screen (the inventory key is a letter).
        return isFocused() && event.key() != GLFW.GLFW_KEY_ESCAPE && event.key() != GLFW.GLFW_KEY_TAB;
    }
}
