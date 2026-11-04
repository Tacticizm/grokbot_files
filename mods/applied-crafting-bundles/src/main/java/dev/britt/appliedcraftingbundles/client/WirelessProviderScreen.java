package dev.britt.appliedcraftingbundles.client;

import dev.britt.appliedcraftingbundles.BundleContents;
import dev.britt.appliedcraftingbundles.BundleProviderMenu;
import dev.britt.appliedcraftingbundles.ModComponents;
import dev.britt.appliedcraftingbundles.ModNetwork;
import dev.britt.appliedcraftingbundles.WirelessInventory;
import dev.britt.appliedcraftingbundles.WirelessProviderMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Wireless terminal, Provider tab: the item's 9 patterns with request buttons, plus an Encoder tab. */
public class WirelessProviderScreen extends AbstractContainerScreen<WirelessProviderMenu> {
    private static final int LIST_X = 70, ROW_H = 11;

    private final List<Button> requestButtons = new ArrayList<>();
    private EditBox setsBox;

    public WirelessProviderScreen(WirelessProviderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 176, 214);
        this.inventoryLabelY = BundleProviderMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        requestButtons.clear();
        addRenderableWidget(Button.builder(Component.translatable("screen.appliedcraftingbundles.tab.provider"), b -> {})
                .bounds(leftPos + 80, topPos + 3, 44, 12).build()).active = false;
        addRenderableWidget(Button.builder(Component.translatable("screen.appliedcraftingbundles.tab.encoder"),
                b -> ClientPacketDistributor.sendToServer(new ModNetwork.WirelessTab(menu.hand, true)))
                .bounds(leftPos + 126, topPos + 3, 44, 12).build());
        setsBox = addRenderableWidget(new EditBox(font, leftPos + 30, topPos + 76, 30, 12,
                Component.translatable("screen.appliedcraftingbundles.sets")));
        setsBox.setValue("1");
        setsBox.setMaxLength(2);
        setsBox.setFilter(s -> s.chars().allMatch(Character::isDigit));
        for (int i = 0; i < WirelessInventory.PATTERNS; i++) {
            int slot = i;
            requestButtons.add(addRenderableWidget(Button.builder(Component.literal(">"), b -> request(slot))
                    .bounds(leftPos + 154, topPos + 18 + i * ROW_H, 14, 10).build()));
        }
        updateWidgets();
    }

    private void request(int slot) {
        int sets;
        try {
            sets = Math.max(1, Integer.parseInt(setsBox.getValue()));
        } catch (NumberFormatException e) {
            sets = 1;
        }
        ClientPacketDistributor.sendToServer(new ModNetwork.WirelessRequest(menu.hand, slot, sets));
    }

    private BundleContents bundleAt(int slot) {
        var c = menu.slots.get(slot).getItem().get(ModComponents.BUNDLE_CONTENTS.get());
        return c == null || c.isEmpty() ? null : c;
    }

    private void updateWidgets() {
        for (int i = 0; i < requestButtons.size(); i++) {
            requestButtons.get(i).visible = bundleAt(i) != null;
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateWidgets();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (setsBox.isFocused() && event.key() != GLFW.GLFW_KEY_ESCAPE) {
            return setsBox.keyPressed(event) || true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Panels.panel(g, leftPos, topPos, imageWidth, imageHeight);
        for (var slot : menu.slots) {
            Panels.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        g.fill(leftPos + LIST_X - 2, topPos + 16, leftPos + 170, topPos + 18 + 9 * ROW_H, 0xFF8B8B8B);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        g.text(font, Component.translatable("screen.appliedcraftingbundles.sets"), 8, 78, Panels.TEXT, false);
        for (int i = 0; i < WirelessInventory.PATTERNS; i++) {
            var b = bundleAt(i);
            if (b != null) {
                String name = b.name().isBlank() ? "#" + (i + 1) : b.name();
                g.text(font, font.plainSubstrByWidth(name, 80), LIST_X, 19 + i * ROW_H, 0xFFFFFFFF, false);
            }
        }
    }
}
