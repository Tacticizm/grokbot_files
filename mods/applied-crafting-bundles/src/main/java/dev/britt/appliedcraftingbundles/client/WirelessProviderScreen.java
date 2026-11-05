package dev.britt.appliedcraftingbundles.client;

import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.TabButton;
import dev.britt.appliedcraftingbundles.BundleContents;
import dev.britt.appliedcraftingbundles.WirelessProviderMenu;
import dev.britt.appliedcraftingbundles.GuiLayout;
import dev.britt.appliedcraftingbundles.ModBlocks;
import dev.britt.appliedcraftingbundles.ModComponents;
import dev.britt.appliedcraftingbundles.ModItems;
import dev.britt.appliedcraftingbundles.ModNetwork;
import dev.britt.appliedcraftingbundles.WirelessInventory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Wireless terminal, Provider tab. Same window size and tab placement as the Encoder tab.
 * Crafted items go to the player (then the network); this screen has no output buffer.
 */
public class WirelessProviderScreen extends AbstractContainerScreen<WirelessProviderMenu> {
    private final List<AE2Button> requestButtons = new ArrayList<>();
    private SetsControl sets;

    public WirelessProviderScreen(WirelessProviderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, GuiLayout.TERM_W, GuiLayout.TERM_H);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = GuiLayout.TERM_INV_X;
        this.inventoryLabelY = GuiLayout.TERM_INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        TabHandoff.applyOrigin(false, new TabHandoff.ScreenOrigin() {
            @Override
            public int width() {
                return WirelessProviderScreen.this.width;
            }

            @Override
            public int height() {
                return WirelessProviderScreen.this.height;
            }

            @Override
            public void move(int left, int top) {
                leftPos = left;
                topPos = top;
            }
        });
        requestButtons.clear();
        var provider = new TabButton(new ItemStack(ModBlocks.BUNDLE_PROVIDER_ITEM.get()),
                Component.translatable("screen.appliedcraftingbundles.tab.provider"), b -> {});
        provider.setStyle(TabButton.Style.HORIZONTAL);
        provider.setSelected(true);
        provider.setX(leftPos + GuiLayout.TAB_X);
        provider.setY(topPos + GuiLayout.TAB_Y);
        addRenderableWidget(provider);
        var encoder = new TabButton(new ItemStack(ModItems.BUNDLE_ENCODER.get()),
                Component.translatable("screen.appliedcraftingbundles.tab.encoder"),
                b -> {
                    TabHandoff.arm(true, leftPos, topPos, width, height);
                    ClientPacketDistributor.sendToServer(new ModNetwork.WirelessTab(menu.hand, true));
                });
        encoder.setStyle(TabButton.Style.HORIZONTAL);
        encoder.setX(leftPos + GuiLayout.TAB_X + GuiLayout.TAB + 2);
        encoder.setY(topPos + GuiLayout.TAB_Y);
        addRenderableWidget(encoder);
        sets = new SetsControl(this::addRenderableWidget, font, leftPos + 34, topPos + GuiLayout.TERM_SETS_Y);
        for (int i = 0; i < WirelessInventory.PATTERNS; i++) {
            int slot = i;
            requestButtons.add(addRenderableWidget(new AE2Button(
                    leftPos + GuiLayout.TERM_REQ_X, topPos + GuiLayout.TERM_LIST_Y + i * GuiLayout.TERM_ROW,
                    14, 10, Component.literal(">"), b -> request(slot))));
        }
        updateWidgets();
    }

    private void request(int slot) {
        ClientPacketDistributor.sendToServer(new ModNetwork.WirelessRequest(menu.hand, slot, sets.value()));
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
        TabHandoff.pinCursor();
        sets.normalizeIfIdle();
        updateWidgets();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (sets.isFocused() && event.key() != GLFW.GLFW_KEY_ESCAPE) {
            return sets.keyPressed(event);
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
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        g.text(font, Component.translatable("screen.appliedcraftingbundles.sets"), 8, GuiLayout.TERM_SETS_Y + 4,
                Panels.TEXT, false);
        for (int i = 0; i < WirelessInventory.PATTERNS; i++) {
            var b = bundleAt(i);
            if (b != null) {
                String name = b.name().isBlank() ? "#" + (i + 1) : b.name();
                g.text(font, font.plainSubstrByWidth(name, GuiLayout.TERM_REQ_X - GuiLayout.TERM_LIST_X - 2),
                        GuiLayout.TERM_LIST_X, GuiLayout.TERM_LIST_Y + i * GuiLayout.TERM_ROW + 1, Panels.TEXT, false);
            }
        }
    }
}
