package dev.britt.appliedcraftingbundles.client;

import dev.britt.appliedcraftingbundles.BundleContents;
import dev.britt.appliedcraftingbundles.BundleProviderBlockEntity;
import dev.britt.appliedcraftingbundles.BundleProviderMenu;
import dev.britt.appliedcraftingbundles.ModComponents;
import dev.britt.appliedcraftingbundles.ModNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Patterns tab: 9 slots + bundle list with request buttons. Queue tab: queued/running/done pieces. */
public class BundleProviderScreen extends AbstractContainerScreen<BundleProviderMenu> {
    private static final int LIST_X = 70, ROW_H = 11, QUEUE_ROWS = 5, QUEUE_ROW_H = 18;

    private final List<Button> requestButtons = new ArrayList<>();
    private final List<Button> cancelButtons = new ArrayList<>();
    private EditBox setsBox;
    private Button patternsTab, queueTab;
    private int scroll;

    public BundleProviderScreen(BundleProviderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 176, 214);
        this.inventoryLabelY = BundleProviderMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        requestButtons.clear();
        cancelButtons.clear();
        patternsTab = addRenderableWidget(Button.builder(Component.translatable("screen.appliedcraftingbundles.tab.patterns"),
                b -> menu.queueView = false).bounds(leftPos + 92, topPos + 3, 44, 12).build());
        queueTab = addRenderableWidget(Button.builder(Component.translatable("screen.appliedcraftingbundles.tab.queue"),
                b -> menu.queueView = true).bounds(leftPos + 138, topPos + 3, 32, 12).build());
        setsBox = addRenderableWidget(new EditBox(font, leftPos + 30, topPos + 76, 30, 12,
                Component.translatable("screen.appliedcraftingbundles.sets")));
        setsBox.setValue("1");
        setsBox.setMaxLength(2);
        setsBox.setFilter(s -> s.chars().allMatch(Character::isDigit));
        for (int i = 0; i < BundleProviderBlockEntity.SLOTS; i++) {
            int slot = i;
            requestButtons.add(addRenderableWidget(Button.builder(Component.literal(">"), b -> request(slot))
                    .bounds(leftPos + 154, topPos + 18 + i * ROW_H, 14, 10).build()));
        }
        for (int i = 0; i < QUEUE_ROWS; i++) {
            int row = i;
            cancelButtons.add(addRenderableWidget(Button.builder(Component.literal("x"), b -> cancelRow(row))
                    .bounds(leftPos + 154, topPos + 20 + i * QUEUE_ROW_H, 14, 12).build()));
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
        ClientPacketDistributor.sendToServer(new ModNetwork.RequestBundle(menu.pos, slot, sets));
    }

    private void cancelRow(int row) {
        int i = scroll + row;
        if (i < menu.clientPieces.size()) {
            ClientPacketDistributor.sendToServer(new ModNetwork.CancelPiece(menu.pos, menu.clientPieces.get(i).id()));
        }
    }

    private BundleContents bundleAt(int slot) {
        var c = menu.slots.get(slot).getItem().get(ModComponents.BUNDLE_CONTENTS.get());
        return c == null || c.isEmpty() ? null : c;
    }

    private void updateWidgets() {
        boolean q = menu.queueView;
        patternsTab.active = q;
        queueTab.active = !q;
        setsBox.visible = !q;
        for (int i = 0; i < requestButtons.size(); i++) {
            requestButtons.get(i).visible = !q && bundleAt(i) != null;
        }
        scroll = Math.max(0, Math.min(scroll, menu.clientPieces.size() - QUEUE_ROWS));
        for (int i = 0; i < cancelButtons.size(); i++) {
            int idx = scroll + i;
            cancelButtons.get(i).visible = q && idx < menu.clientPieces.size()
                    && menu.clientPieces.get(idx).state().equals("QUEUED");
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateWidgets();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (menu.queueView) {
            scroll -= (int) Math.signum(sy);
            updateWidgets();
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
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
            if (slot.isActive()) {
                Panels.slot(g, leftPos + slot.x, topPos + slot.y);
            }
        }
        if (menu.queueView) {
            g.fill(leftPos + 6, topPos + 16, leftPos + 170, topPos + 18 + QUEUE_ROWS * QUEUE_ROW_H, 0xFF8B8B8B);
        } else {
            g.fill(leftPos + LIST_X - 2, topPos + 16, leftPos + 170, topPos + 18 + 9 * ROW_H, 0xFF8B8B8B);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        if (!menu.queueView) {
            g.text(font, Component.translatable("screen.appliedcraftingbundles.sets"), 8, 78, Panels.TEXT, false);
            for (int i = 0; i < BundleProviderBlockEntity.SLOTS; i++) {
                var b = bundleAt(i);
                if (b != null) {
                    String name = b.name().isBlank() ? "#" + (i + 1) : b.name();
                    g.text(font, font.plainSubstrByWidth(name, 80), LIST_X, 19 + i * ROW_H, 0xFFFFFFFF, false);
                }
            }
            return;
        }
        var pieces = menu.clientPieces;
        if (pieces.isEmpty()) {
            g.text(font, Component.translatable("screen.appliedcraftingbundles.queue.empty"), 10, 22, 0xFFFFFFFF, false);
        }
        for (int row = 0; row < QUEUE_ROWS && scroll + row < pieces.size(); row++) {
            var pc = pieces.get(scroll + row);
            int y = 18 + row * QUEUE_ROW_H;
            g.item(pc.item().create(), 8, y);
            String label = pc.amount() + "x " + pc.item().create().getHoverName().getString();
            g.text(font, font.plainSubstrByWidth(label, 80), 27, y + 1, 0xFFFFFFFF, false);
            g.text(font, Component.translatable("screen.appliedcraftingbundles.state." + pc.state().toLowerCase()),
                    27, y + 9, stateColor(pc.state()), false);
            if (!pc.note().isEmpty() && mouseX - leftPos >= 6 && mouseX - leftPos < 150
                    && mouseY - topPos >= y && mouseY - topPos < y + QUEUE_ROW_H) {
                g.setTooltipForNextFrame(Component.literal(pc.note()), mouseX, mouseY);
            }
        }
    }

    private static int stateColor(String state) {
        return switch (state) {
            case "QUEUED" -> 0xFFFFE066;
            case "PLANNING" -> 0xFF66E0FF;
            case "RUNNING" -> 0xFF66FF66;
            case "CANCELLED" -> 0xFFFF6666;
            default -> 0xFFDDDDDD;
        };
    }
}
