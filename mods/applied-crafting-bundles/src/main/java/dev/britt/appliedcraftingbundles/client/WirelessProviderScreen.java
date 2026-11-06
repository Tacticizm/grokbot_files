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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Wireless terminal, Provider tab. Same window size and tab placement as the Encoder tab.
 * Crafted items go to the player (then the network); this screen has no output buffer.
 */
public class WirelessProviderScreen extends AbstractContainerScreen<WirelessProviderMenu> {
    private AE2Button craftButton;
    private SetsControl sets;
    /** Slot chosen by click or hover. {@code -1} until the player focuses one, then the first encoded pattern shows. */
    private int selected = -1;

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
        var provider = new TabButton(new ItemStack(ModBlocks.BUNDLE_PROVIDER_ITEM.get()),
                Component.translatable("screen.appliedcraftingbundles.tab.provider"), b -> {});
        provider.setStyle(TabButton.Style.BOX);
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
        encoder.setStyle(TabButton.Style.BOX);
        encoder.setX(leftPos + GuiLayout.TAB_X + GuiLayout.TAB + 2);
        encoder.setY(topPos + GuiLayout.TAB_Y);
        addRenderableWidget(encoder);
        sets = new SetsControl(this::addRenderableWidget, font, leftPos + 34, topPos + GuiLayout.TERM_SETS_Y);
        craftButton = addRenderableWidget(new AE2Button(leftPos + GuiLayout.TERM_CRAFT_X, topPos + GuiLayout.TERM_CRAFT_Y,
                GuiLayout.TERM_CRAFT_W, GuiLayout.TERM_CRAFT_H,
                Component.translatable("screen.appliedcraftingbundles.craft"), b -> request()));
        updateWidgets();
    }

    private void request() {
        int slot = shownSlot();
        if (slot < 0 || bundleAt(slot) == null) {
            return;
        }
        ClientPacketDistributor.sendToServer(
                new ModNetwork.WirelessRequest(menu.hand, slot, sets.value(), minecraft.hasShiftDown()));
    }

    private BundleContents bundleAt(int slot) {
        var c = menu.slots.get(slot).getItem().get(ModComponents.BUNDLE_CONTENTS.get());
        return c == null || c.isEmpty() ? null : c;
    }

    private boolean patternSlot(Slot slot) {
        return slot != null && slot.index >= 0 && slot.index < WirelessInventory.PATTERNS;
    }

    private int shownSlot() {
        if (selected >= 0 && selected < WirelessInventory.PATTERNS) {
            return selected;
        }
        for (int i = 0; i < WirelessInventory.PATTERNS; i++) {
            if (bundleAt(i) != null) {
                return i;
            }
        }
        return -1;
    }

    private void focus(int slot) {
        if (slot >= 0 && slot < WirelessInventory.PATTERNS) {
            selected = slot;
        }
    }

    private void updateWidgets() {
        int slot = shownSlot();
        craftButton.visible = slot >= 0 && bundleAt(slot) != null;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        TabHandoff.pinCursor();
        if (patternSlot(hoveredSlot)) {
            focus(hoveredSlot.index);
        }
        sets.normalizeIfIdle();
        updateWidgets();
        craftButton.setMessage(Component.translatable(minecraft.hasShiftDown()
                ? "screen.appliedcraftingbundles.start" : "screen.appliedcraftingbundles.craft"));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int hit = patternAt(event.x(), event.y());
        if (hit >= 0) {
            focus(hit);
            updateWidgets();
        }
        return super.mouseClicked(event, doubleClick);
    }

    private int patternAt(double mx, double my) {
        for (int i = 0; i < WirelessInventory.PATTERNS; i++) {
            var slot = menu.slots.get(i);
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
                return i;
            }
        }
        return -1;
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
        int slot = shownSlot();
        if (slot >= 0) {
            mark(g, GuiLayout.TERM_PATTERN_X + slot * 18, GuiLayout.TERM_PATTERN_Y);
        }
        g.text(font, Component.translatable("screen.appliedcraftingbundles.sets"), 8, GuiLayout.TERM_SETS_Y + 4,
                Panels.TEXT, false);
        var bundle = slot >= 0 ? bundleAt(slot) : null;
        if (bundle == null) {
            return;
        }
        String name = bundle.name().isBlank() ? "#" + (slot + 1) : bundle.name();
        int max = GuiLayout.TERM_CRAFT_X - GuiLayout.TERM_BUNDLE_X - 6;
        String shown = font.plainSubstrByWidth(name, max);
        g.text(font, shown, GuiLayout.TERM_BUNDLE_X, GuiLayout.TERM_BUNDLE_Y, Panels.TEXT, false);
        if (!shown.equals(name) && mouseX - leftPos >= GuiLayout.TERM_BUNDLE_X
                && mouseX - leftPos < GuiLayout.TERM_CRAFT_X
                && mouseY - topPos >= GuiLayout.TERM_BUNDLE_Y - 2
                && mouseY - topPos < GuiLayout.TERM_BUNDLE_Y + 12) {
            g.setTooltipForNextFrame(Component.literal(name), mouseX, mouseY);
        }
    }

    private static void mark(GuiGraphicsExtractor g, int x, int y) {
        int c = 0xFF1F6F8A;
        g.fill(x, y, x + 16, y + 1, c);
        g.fill(x, y + 15, x + 16, y + 16, c);
        g.fill(x, y, x + 1, y + 16, c);
        g.fill(x + 15, y, x + 16, y + 16, c);
    }
}
