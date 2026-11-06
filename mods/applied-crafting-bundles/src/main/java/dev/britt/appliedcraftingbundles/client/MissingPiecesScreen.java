package dev.britt.appliedcraftingbundles.client;

import dev.britt.appliedcraftingbundles.ModNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import appeng.client.gui.widgets.AE2Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** "Some pieces are missing": tick what to craft now, or cancel. Closing the screen counts as Cancel. */
public class MissingPiecesScreen extends Screen {
    private static final int W = 240, ROW_H = 22;

    private final ModNetwork.OpenMissing data;
    private final Map<Integer, Checkbox> boxes = new HashMap<>();
    private boolean answered;
    private boolean rebuild;
    private Set<Integer> ticked;
    private static final int ROWS_Y = 50;
    private int left, top, height0;

    public MissingPiecesScreen(ModNetwork.OpenMissing data) {
        super(Component.translatable(data.entries().stream().anyMatch(e -> !e.missing().isEmpty())
                ? "screen.appliedcraftingbundles.missing.title"
                : "screen.appliedcraftingbundles.confirm.title"));
        this.data = data;
    }

    @Override
    protected void init() {
        boxes.clear();
        height0 = 56 + data.entries().size() * ROW_H + 26;
        left = (width - W) / 2;
        top = (height - height0) / 2;
        List<Integer> craftable = data.entries().stream().filter(e -> e.missing().isEmpty())
                .map(ModNetwork.EntryView::index).toList();
        if (ticked == null) {
            ticked = new HashSet<>(craftable); // craftable rows start ticked
        }
        boolean all = !craftable.isEmpty() && ticked.containsAll(craftable);
        var selectAll = addRenderableWidget(Checkbox.builder(
                        Component.translatable("screen.appliedcraftingbundles.missing.select_all"), font)
                .pos(left + 8, top + 28).maxWidth(W - 16).selected(all)
                .onValueChange((box, on) -> {
                    ticked.clear();
                    if (on) {
                        ticked.addAll(craftable);
                    }
                    rebuild = true;
                }).build());
        selectAll.active = !craftable.isEmpty();
        for (int i = 0; i < data.entries().size(); i++) {
            var e = data.entries().get(i);
            boolean canCraft = e.missing().isEmpty();
            var box = addRenderableWidget(Checkbox.builder(Component.empty(), font)
                    .pos(left + 8, top + ROWS_Y + i * ROW_H).selected(canCraft && ticked.contains(e.index()))
                    .onValueChange((cb, on) -> {
                        if (on) {
                            ticked.add(e.index());
                        } else {
                            ticked.remove(e.index());
                        }
                        rebuild = true;
                    }).build());
            box.active = canCraft; // greyed out and unticked when it can't be crafted
            if (canCraft) {
                boxes.put(e.index(), box);
            }
        }
        int by = top + height0 - 24;
        var craft = addRenderableWidget(new AE2Button(left + 8, by, 110, 18,
                Component.translatable("screen.appliedcraftingbundles.missing.start"), b -> answer(true)));
        craft.active = !ticked.isEmpty();
        addRenderableWidget(new AE2Button(left + W - 118, by, 110, 18,
                Component.translatable("gui.cancel"), b -> answer(false)));
    }

    /** Checkbox has no setter, so state lives in {@link #ticked} and widgets are rebuilt on the next tick. */
    @Override
    public void tick() {
        super.tick();
        if (rebuild) {
            rebuild = false;
            rebuildWidgets();
        }
    }

    private void answer(boolean craft) {
        if (answered) {
            return;
        }
        answered = true;
        List<Integer> chosen = new ArrayList<>();
        chosen.addAll(ticked);
        ClientPacketDistributor.sendToServer(new ModNetwork.MissingChoice(data.pos(), data.wireless(), craft && !chosen.isEmpty(), chosen));
        onClose();
    }

    @Override
    public void onClose() {
        if (!answered) {
            answer(false);
            return;
        }
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.closeContainer();
        }
        super.onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Panels.panel(g, left, top, W, height0);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        g.text(font, title, left + 8, top + 6, Panels.TEXT, false);
        String name = data.bundleName().isBlank() ? "" : data.bundleName();
        g.text(font, font.plainSubstrByWidth(name, W - 16), left + 8, top + 17, 0xFF606060, false);
        for (int i = 0; i < data.entries().size(); i++) {
            var e = data.entries().get(i);
            int y = top + ROWS_Y + i * ROW_H;
            g.item(e.item().create(), left + 36, y);
            String label = e.amount() + "x " + e.item().create().getHoverName().getString();
            g.text(font, font.plainSubstrByWidth(label, W - 66), left + 56, y, Panels.TEXT, false);
            String status;
            if (!e.missing().isEmpty()) {
                status = Component.translatable("screen.appliedcraftingbundles.missing.short", String.join(", ", e.missing())).getString();
            } else if (e.inStorage() >= e.amount()) {
                status = Component.translatable("screen.appliedcraftingbundles.missing.in_storage").getString();
            } else if (e.inStorage() > 0) {
                status = Component.translatable("screen.appliedcraftingbundles.missing.partial", e.inStorage()).getString();
            } else {
                status = Component.translatable("screen.appliedcraftingbundles.missing.ok").getString();
            }
            g.text(font, font.plainSubstrByWidth(status, W - 66), left + 56, y + 10,
                    e.missing().isEmpty() ? 0xFF207020 : 0xFFA02020, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
