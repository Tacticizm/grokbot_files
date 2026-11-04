package dev.britt.appliedcraftingbundles.client;

import dev.britt.appliedcraftingbundles.ModNetwork;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Wireless terminal: some pieces couldn't get a crafting CPU. Mirrors AE2's CraftErrorScreen (its error text,
 * Retry and Cancel buttons and lang keys). Retry submits the same plans again; closing counts as Cancel.
 */
public class CraftErrorScreen extends Screen {
    private static final int W = 260;

    private final ModNetwork.CraftError data;
    private boolean answered;
    private int left, top, h;

    public CraftErrorScreen(ModNetwork.CraftError data) {
        super(Component.translatable("screen.appliedcraftingbundles.craft_error.title"));
        this.data = data;
    }

    private int lineCount() {
        return data.lines().size() + (data.noCpu() ? 1 : 0);
    }

    @Override
    protected void init() {
        h = 22 + lineCount() * 22 + 28;
        left = (width - W) / 2;
        top = (height - h) / 2;
        int by = top + h - 24;
        addRenderableWidget(Button.builder(Component.translatable("gui.ae2.CraftErrorRetry"), b -> answer(true))
                .bounds(left + 8, by, 110, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.ae2.Cancel"), b -> answer(false))
                .bounds(left + W - 118, by, 110, 18).build());
    }

    private void answer(boolean retry) {
        if (!answered) {
            answered = true;
            ClientPacketDistributor.sendToServer(new ModNetwork.CraftErrorChoice(retry));
        }
        super.onClose();
    }

    @Override
    public void onClose() {
        answer(false);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Panels.panel(g, left, top, W, h);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        g.text(font, title, left + 8, top + 6, Panels.TEXT, false);
        int y = top + 22;
        for (Component line : data.lines()) {
            g.textWithWordWrap(font, line, left + 8, y, W - 16, 0xFFA02020, false);
            y += 22;
        }
        if (data.noCpu()) {
            g.textWithWordWrap(font, Component.translatable("gui.ae2.NoCraftingCPUs"), left + 8, y, W - 16, Panels.TEXT, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
