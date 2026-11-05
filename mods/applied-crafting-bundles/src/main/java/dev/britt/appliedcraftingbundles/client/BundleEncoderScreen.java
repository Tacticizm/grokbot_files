package dev.britt.appliedcraftingbundles.client;

import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.TabButton;
import appeng.util.Icon;
import dev.britt.appliedcraftingbundles.BundleContents;
import dev.britt.appliedcraftingbundles.BundleEncoderMenu;
import dev.britt.appliedcraftingbundles.BundleProviderBlockEntity;
import dev.britt.appliedcraftingbundles.GuiLayout;
import dev.britt.appliedcraftingbundles.ModBlocks;
import dev.britt.appliedcraftingbundles.ModComponents;
import dev.britt.appliedcraftingbundles.ModItems;
import dev.britt.appliedcraftingbundles.ModNetwork;
import java.util.ArrayList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Name field + 9 ghost cells. Pick an item up from your inventory and left-click a cell to set it (nothing is
 * consumed); right-click a cell to clear it; scroll over a cell to change its count (hold Shift for x10).
 * The wireless terminal uses the same window bounds as its Provider tab.
 */
public class BundleEncoderScreen extends AbstractContainerScreen<BundleEncoderMenu> {
    private final ItemStackTemplate[] cells = new ItemStackTemplate[BundleProviderBlockEntity.SLOTS];
    private AeTextField nameBox;

    public BundleEncoderScreen(BundleEncoderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, GuiLayout.TERM_W, GuiLayout.TERM_H);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = GuiLayout.TERM_INV_X;
        this.inventoryLabelY = GuiLayout.TERM_INV_Y - 11;
    }

    /** Putting an encoded pattern in the input slot loads it for viewing/editing; a blank starts fresh. */
    private ItemStack lastInput = ItemStack.EMPTY;

    @Override
    protected void init() {
        super.init();
        if (menu.wirelessHand != null) {
            TabHandoff.applyOrigin(true, new TabHandoff.ScreenOrigin() {
                @Override
                public int width() {
                    return BundleEncoderScreen.this.width;
                }

                @Override
                public int height() {
                    return BundleEncoderScreen.this.height;
                }

                @Override
                public void move(int left, int top) {
                    leftPos = left;
                    topPos = top;
                }
            });
            addWirelessTabs();
        }
        String old = nameBox == null ? null : nameBox.getValue();
        nameBox = addRenderableWidget(new AeTextField(font, leftPos + GuiLayout.TERM_NAME_X, topPos + GuiLayout.TERM_NAME_Y,
                GuiLayout.TERM_NAME_W, 12, Component.translatable("screen.appliedcraftingbundles.encoder.name")));
        nameBox.setMaxLength(ModNetwork.MAX_NAME);
        if (old != null) {
            nameBox.setValue(old);
        }
        addRenderableWidget(new AE2Button(leftPos + GuiLayout.TERM_ENCODE_X, topPos + GuiLayout.TERM_ENCODE_Y, 70, 16,
                Component.translatable("screen.appliedcraftingbundles.encoder.save"), b -> save()));
    }

    private void addWirelessTabs() {
        var provider = new TabButton(new ItemStack(ModBlocks.BUNDLE_PROVIDER_ITEM.get()),
                Component.translatable("screen.appliedcraftingbundles.tab.provider"),
                b -> {
                    TabHandoff.arm(false, leftPos, topPos, width, height);
                    ClientPacketDistributor.sendToServer(new ModNetwork.WirelessTab(menu.wirelessHand, false));
                });
        provider.setStyle(TabButton.Style.HORIZONTAL);
        provider.setX(leftPos + GuiLayout.TAB_X);
        provider.setY(topPos + GuiLayout.TAB_Y);
        addRenderableWidget(provider);
        var encoder = new TabButton(new ItemStack(ModItems.BUNDLE_ENCODER.get()),
                Component.translatable("screen.appliedcraftingbundles.tab.encoder"), b -> {});
        encoder.setStyle(TabButton.Style.HORIZONTAL);
        encoder.setSelected(true);
        encoder.setX(leftPos + GuiLayout.TAB_X + GuiLayout.TAB + 2);
        encoder.setY(topPos + GuiLayout.TAB_Y);
        addRenderableWidget(encoder);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (menu.wirelessHand != null) {
            TabHandoff.pinCursor();
        }
        ItemStack in = menu.slots.get(0).getItem();
        if (!ItemStack.matches(in, lastInput)) {
            lastInput = in.copy();
            BundleContents c = in.get(ModComponents.BUNDLE_CONTENTS.get());
            if (c != null || in.is(ModItems.BUNDLE_PATTERN.get())) {
                java.util.Arrays.fill(cells, null);
                if (c != null) {
                    for (int i = 0; i < c.entries().size() && i < cells.length; i++) {
                        cells[i] = c.entries().get(i);
                    }
                }
                nameBox.setValue(c == null ? "" : c.name());
            }
        }
    }

    private void save() {
        var entries = new ArrayList<ItemStackTemplate>();
        for (var c : cells) {
            if (c != null) {
                entries.add(c);
            }
        }
        ClientPacketDistributor.sendToServer(new ModNetwork.SaveBundle(nameBox.getValue(), entries));
    }

    private int cellAt(double mx, double my) {
        for (int i = 0; i < cells.length; i++) {
            int x = leftPos + GuiLayout.TERM_CELL_X + i * 18, y = topPos + GuiLayout.TERM_CELL_Y;
            if (mx >= x && mx < x + 18 && my >= y && my < y + 18) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int i = cellAt(event.x(), event.y());
        if (i >= 0) {
            var carried = menu.getCarried();
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                cells[i] = null;
            } else if (!carried.isEmpty()) {
                cells[i] = new ItemStackTemplate(carried.getItem(), Math.min(carried.getCount(), ModNetwork.MAX_COUNT));
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        int i = cellAt(x, y);
        if (i >= 0 && cells[i] != null) {
            int step = (minecraft.hasShiftDown() ? 10 : 1) * (int) Math.signum(sy);
            cells[i] = cells[i].withCount(Math.clamp(cells[i].count() + step, 1, ModNetwork.MAX_COUNT));
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (nameBox.isFocused() && event.key() != GLFW.GLFW_KEY_ESCAPE) {
            return nameBox.keyPressed(event) || true;
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
        for (int i = 0; i < cells.length; i++) {
            int x = leftPos + GuiLayout.TERM_CELL_X + i * 18, y = topPos + GuiLayout.TERM_CELL_Y;
            Panels.slot(g, x, y);
            @Nullable ItemStackTemplate c = cells[i];
            if (c != null) {
                var stack = c.create();
                g.item(stack, x, y);
                g.itemDecorations(font, stack, x, y, String.valueOf(c.count()));
            }
        }
        Blitter.icon(Icon.ARROW_RIGHT).dest(leftPos + GuiLayout.TERM_IN_X + 18, topPos + GuiLayout.TERM_IO_Y).blit(g);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        g.text(font, Component.translatable("screen.appliedcraftingbundles.encoder.items"),
                GuiLayout.TERM_CELL_X, GuiLayout.TERM_CELL_Y - 10, Panels.TEXT, false);
        g.textWithWordWrap(font, Component.translatable("screen.appliedcraftingbundles.encoder.help"),
                8, GuiLayout.TERM_HELP_Y, imageWidth - 16, Panels.MUTED, false);
        int i = cellAt(mouseX, mouseY);
        if (i >= 0 && cells[i] != null) {
            g.setTooltipForNextFrame(cells[i].create().getHoverName(), mouseX, mouseY);
        }
    }
}
