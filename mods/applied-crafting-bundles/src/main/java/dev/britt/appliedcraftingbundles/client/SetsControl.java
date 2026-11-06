package dev.britt.appliedcraftingbundles.client;

import appeng.client.gui.widgets.AE2Button;
import dev.britt.appliedcraftingbundles.ModNetwork;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * Sets count: typed number plus AE2 minus/plus buttons. Click steps by 1, Shift-click by 10, clamped to
 * {@code 1..MAX_SETS}.
 */
final class SetsControl {
    private final AeTextField field;
    private final AE2Button dec;
    private final AE2Button inc;
    private boolean writing;

    SetsControl(Consumer<AbstractWidget> add, Font font, int x, int y) {
        dec = new AE2Button(x, y, 14, 14, Component.literal("-"), b -> step(-1));
        add.accept(dec);
        field = new AeTextField(font, x + 16, y + 1, 18, 12, Component.translatable("screen.appliedcraftingbundles.sets"));
        field.setMaxLength(2);
        field.setValue("1");
        field.setFilter(s -> s.chars().allMatch(Character::isDigit));
        field.setResponder(this::onType);
        add.accept(field);
        inc = new AE2Button(x + 36, y, 14, 14, Component.literal("+"), b -> step(1));
        add.accept(inc);
    }

    int value() {
        return clamp(parse(field.getValue(), 1));
    }

    boolean isFocused() {
        return field.isFocused();
    }

    boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return field.keyPressed(event) || field.isFocused();
    }

    void setVisible(boolean visible) {
        field.visible = visible;
        dec.visible = visible;
        inc.visible = visible;
    }

    /** Empty or zero settles to 1 once the field is no longer being edited. */
    void normalizeIfIdle() {
        if (field.isFocused()) {
            return;
        }
        String want = Integer.toString(value());
        if (!want.equals(field.getValue())) {
            write(want);
        }
    }

    private void step(int direction) {
        int delta = (Minecraft.getInstance().hasShiftDown() ? 10 : 1) * direction;
        write(Integer.toString(clamp(value() + delta)));
    }

    private void onType(String text) {
        if (writing || text.isEmpty()) {
            return;
        }
        int parsed = parse(text, 1);
        int clamped = clamp(parsed);
        if (clamped != parsed) {
            write(Integer.toString(clamped));
        }
    }

    private void write(String text) {
        writing = true;
        field.setValue(text);
        writing = false;
    }

    static int clamp(int value) {
        return Math.clamp(value, 1, ModNetwork.MAX_SETS);
    }

    private static int parse(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
