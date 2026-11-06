package dev.britt.appliedcraftingbundles.client;

import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.TabButton;
import appeng.util.Icon;
import dev.britt.appliedcraftingbundles.BundleContents;
import dev.britt.appliedcraftingbundles.BundleProviderBlockEntity;
import dev.britt.appliedcraftingbundles.BundleProviderMenu;
import dev.britt.appliedcraftingbundles.GuiLayout;
import dev.britt.appliedcraftingbundles.ModComponents;
import dev.britt.appliedcraftingbundles.ModItems;
import dev.britt.appliedcraftingbundles.ModNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Block provider: the selected pattern and the 27-slot output buffer on one AE2-style window. */
public class BundleProviderScreen extends AbstractContainerScreen<BundleProviderMenu> {
    private final List<AE2Button> cancelButtons = new ArrayList<>();
    private AE2Button craftButton;
    private SetsControl sets;
    private TabButton patternsTab, queueTab;
    /** Slot chosen by click or hover. {@code -1} until the player focuses one, then the first encoded pattern shows. */
    private int selected = -1;

    public BundleProviderScreen(BundleProviderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, GuiLayout.PROV_W, GuiLayout.PROV_H);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = GuiLayout.PROV_INV_X;
        this.inventoryLabelY = GuiLayout.PROV_INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        cancelButtons.clear();
        patternsTab = tab(new ItemStack(ModItems.BUNDLE_PATTERN.get()), "screen.appliedcraftingbundles.tab.patterns",
                GuiLayout.TAB_X, () -> menu.tab = BundleProviderMenu.TAB_PATTERNS);
        queueTab = tab(Icon.SCHEDULING_ROUND_ROBIN, "screen.appliedcraftingbundles.tab.queue",
                GuiLayout.TAB_X + GuiLayout.TAB + 2, () -> menu.tab = BundleProviderMenu.TAB_QUEUE);
        addRenderableWidget(new AE2Button(leftPos + GuiLayout.PROV_SEND_X, topPos + GuiLayout.PROV_SEND_Y,
                GuiLayout.PROV_SEND_W, GuiLayout.PROV_SEND_H,
                Component.translatable("screen.appliedcraftingbundles.send_to_network"),
                b -> ClientPacketDistributor.sendToServer(new ModNetwork.SendToNetwork(menu.pos))));
        sets = new SetsControl(this::addRenderableWidget, font, leftPos + 34, topPos + GuiLayout.PROV_SETS_Y);
        craftButton = addRenderableWidget(new AE2Button(leftPos + GuiLayout.PROV_CRAFT_X, topPos + GuiLayout.PROV_CRAFT_Y,
                GuiLayout.PROV_CRAFT_W, GuiLayout.PROV_CRAFT_H,
                Component.translatable("screen.appliedcraftingbundles.craft"), b -> request()));
        for (int i = 0; i < GuiLayout.PROV_QUEUE_ROWS; i++) {
            int row = i;
            var button = new AE2Button(leftPos + GuiLayout.PROV_CANCEL_X,
                    topPos + GuiLayout.PROV_QUEUE_Y + i * GuiLayout.PROV_QUEUE_ROW, 14, 12,
                    Component.literal("x"), b -> cancelRow(row));
            cancelButtons.add(addRenderableWidget(button));
        }
        updateWidgets();
    }

    private TabButton tab(ItemStack icon, String key, int x, Runnable select) {
        var button = new TabButton(icon, Component.translatable(key), b -> select.run());
        // BOX is AE2's 20×20 square with a border on all four sides. HORIZONTAL's selected
        // sprite has no left edge, so the patterns tab looked open on the left.
        button.setStyle(TabButton.Style.BOX);
        button.setX(leftPos + x);
        button.setY(topPos + GuiLayout.TAB_Y);
        return addRenderableWidget(button);
    }

    private TabButton tab(Icon icon, String key, int x, Runnable select) {
        var button = new TabButton(icon, Component.translatable(key), b -> select.run());
        // BOX is AE2's 20×20 square with a border on all four sides. HORIZONTAL's selected
        // sprite has no left edge, so the patterns tab looked open on the left.
        button.setStyle(TabButton.Style.BOX);
        button.setX(leftPos + x);
        button.setY(topPos + GuiLayout.TAB_Y);
        return addRenderableWidget(button);
    }

    private void request() {
        int slot = shownSlot();
        if (slot < 0 || bundleAt(slot) == null) {
            return;
        }
        ClientPacketDistributor.sendToServer(
                new ModNetwork.RequestBundle(menu.pos, slot, sets.value(), minecraft.hasShiftDown()));
    }

    private void cancelRow(int row) {
        int i = scroll + row;
        if (i < menu.clientPieces.size()) {
            ClientPacketDistributor.sendToServer(new ModNetwork.CancelPiece(menu.pos, menu.clientPieces.get(i).id()));
        }
    }

    private int scroll;

    private BundleContents bundleAt(int slot) {
        var c = menu.slots.get(slot).getItem().get(ModComponents.BUNDLE_CONTENTS.get());
        return c == null || c.isEmpty() ? null : c;
    }

    private boolean patternSlot(Slot slot) {
        return slot != null && slot.isActive() && slot.index >= 0 && slot.index < BundleProviderBlockEntity.SLOTS;
    }

    /** Focused pattern, or the first encoded one when the player has not chosen yet. */
    private int shownSlot() {
        if (menu.tab != BundleProviderMenu.TAB_PATTERNS) {
            return -1;
        }
        if (selected >= 0 && selected < BundleProviderBlockEntity.SLOTS) {
            return selected;
        }
        for (int i = 0; i < BundleProviderBlockEntity.SLOTS; i++) {
            if (bundleAt(i) != null) {
                return i;
            }
        }
        return -1;
    }

    private void focus(int slot) {
        if (slot >= 0 && slot < BundleProviderBlockEntity.SLOTS) {
            selected = slot;
        }
    }

    private void updateWidgets() {
        boolean patterns = menu.tab == BundleProviderMenu.TAB_PATTERNS;
        patternsTab.setSelected(patterns);
        queueTab.setSelected(!patterns);
        sets.setVisible(patterns);
        int slot = shownSlot();
        craftButton.visible = patterns && slot >= 0 && bundleAt(slot) != null;
        scroll = Math.max(0, Math.min(scroll, menu.clientPieces.size() - GuiLayout.PROV_QUEUE_ROWS));
        for (int i = 0; i < cancelButtons.size(); i++) {
            int idx = scroll + i;
            cancelButtons.get(i).visible = !patterns && idx < menu.clientPieces.size()
                    && menu.clientPieces.get(idx).state().equals("QUEUED");
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (menu.tab == BundleProviderMenu.TAB_PATTERNS && patternSlot(hoveredSlot)) {
            focus(hoveredSlot.index);
        }
        sets.normalizeIfIdle();
        updateWidgets();
        craftButton.setMessage(Component.translatable(minecraft.hasShiftDown()
                ? "screen.appliedcraftingbundles.start" : "screen.appliedcraftingbundles.craft"));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menu.tab == BundleProviderMenu.TAB_PATTERNS) {
            int hit = patternAt(event.x(), event.y());
            if (hit >= 0) {
                focus(hit);
                updateWidgets();
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private int patternAt(double mx, double my) {
        for (int i = 0; i < BundleProviderBlockEntity.SLOTS; i++) {
            var slot = menu.slots.get(i);
            if (!slot.isActive()) {
                continue;
            }
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (menu.tab == BundleProviderMenu.TAB_QUEUE) {
            scroll -= (int) Math.signum(sy);
            updateWidgets();
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
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
            if (slot.isActive()) {
                Panels.slot(g, leftPos + slot.x, topPos + slot.y);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        boolean patterns = menu.tab == BundleProviderMenu.TAB_PATTERNS;
        if (!patterns) {
            g.text(font, Component.translatable("screen.appliedcraftingbundles.tab.queue"),
                    8, GuiLayout.PROV_LABEL_Y, Panels.TEXT, false);
        }
        g.text(font, Component.translatable("screen.appliedcraftingbundles.output"),
                GuiLayout.PROV_OUT_X, GuiLayout.PROV_LABEL_Y, Panels.TEXT, false);
        if (patterns) {
            int slot = shownSlot();
            if (slot >= 0) {
                mark(g, GuiLayout.PROV_PATTERN_X + slot * 18, GuiLayout.PROV_PATTERN_Y);
            }
            g.text(font, Component.translatable("screen.appliedcraftingbundles.sets"), 8, GuiLayout.PROV_SETS_Y + 4,
                    Panels.TEXT, false);
            var bundle = slot >= 0 ? bundleAt(slot) : null;
            if (bundle != null) {
                String name = bundle.name().isBlank() ? "#" + (slot + 1) : bundle.name();
                int max = GuiLayout.PROV_CRAFT_X - GuiLayout.PROV_NAME_X - 6;
                String shown = font.plainSubstrByWidth(name, max);
                g.text(font, shown, GuiLayout.PROV_NAME_X, GuiLayout.PROV_NAME_Y, Panels.TEXT, false);
                if (!shown.equals(name) && mouseX - leftPos >= GuiLayout.PROV_NAME_X
                        && mouseX - leftPos < GuiLayout.PROV_CRAFT_X
                        && mouseY - topPos >= GuiLayout.PROV_NAME_Y - 2
                        && mouseY - topPos < GuiLayout.PROV_NAME_Y + 12) {
                    g.setTooltipForNextFrame(Component.literal(name), mouseX, mouseY);
                }
            }
            return;
        }
        var pieces = menu.clientPieces;
        if (pieces.isEmpty()) {
            g.text(font, Component.translatable("screen.appliedcraftingbundles.queue.empty"), 8,
                    GuiLayout.PROV_QUEUE_Y + 4, Panels.MUTED, false);
        }
        for (int row = 0; row < GuiLayout.PROV_QUEUE_ROWS && scroll + row < pieces.size(); row++) {
            var pc = pieces.get(scroll + row);
            int y = GuiLayout.PROV_QUEUE_Y + row * GuiLayout.PROV_QUEUE_ROW;
            g.item(pc.item().create(), 8, y);
            String label = pc.amount() + "x " + pc.item().create().getHoverName().getString();
            g.text(font, font.plainSubstrByWidth(label, GuiLayout.PROV_CANCEL_X - 36), 28, y + 1, Panels.TEXT, false);
            g.text(font, Component.translatable("screen.appliedcraftingbundles.state." + pc.state().toLowerCase()),
                    28, y + 9, stateColor(pc.state()), false);
            if (!pc.note().isEmpty() && mouseX - leftPos >= 6 && mouseX - leftPos < GuiLayout.PROV_OUT_X - 8
                    && mouseY - topPos >= y && mouseY - topPos < y + GuiLayout.PROV_QUEUE_ROW) {
                g.setTooltipForNextFrame(Component.literal(pc.note()), mouseX, mouseY);
            }
        }
    }

    /** Frame drawn inside the slot. An outside frame sat on the panel border and ate the left edge. */
    private static void mark(GuiGraphicsExtractor g, int x, int y) {
        int c = 0xFF1F6F8A;
        g.fill(x, y, x + 16, y + 1, c);
        g.fill(x, y + 15, x + 16, y + 16, c);
        g.fill(x, y, x + 1, y + 16, c);
        g.fill(x + 15, y, x + 16, y + 16, c);
    }

    private static int stateColor(String state) {
        return switch (state) {
            case "QUEUED" -> 0xFF8A6A00;
            case "PLANNING" -> 0xFF1F6F8A;
            case "RUNNING" -> 0xFF1F7A32;
            case "CANCELLED" -> 0xFFCE2401;
            default -> Panels.MUTED;
        };
    }
}
