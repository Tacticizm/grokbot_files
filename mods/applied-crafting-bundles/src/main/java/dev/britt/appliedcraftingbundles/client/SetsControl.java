package dev.britt.appliedcraftingbundles.client;

import appeng.util.Icon;
import dev.britt.appliedcraftingbundles.ModNetwork;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * Sets count: typed number plus AE2 arrow buttons. Click steps by 1, Shift-click by 10, clamped to
 * {@code 1..MAX_SETS}.
 */
final class SetsControl {
    private final AeTextField field;
    private final AeIconButton dec;
    private final AeIconButton inc;
    private boolean writing;

    SetsControl(Consumer<AbstractWidget> add, Font font, int x, int y) {
        dec = new AeIconButton(Icon.ARROW_LEFT, Component.translatable("screen.appliedcraftingbundles.sets.dec"),
                b -> step(-1));
        dec.setX(x);
        dec.setY(y);
        add.accept(dec);
        field = new AeTextField(font, x + 18, y + 2, 24, 12, Component.translatable("screen.appliedcraftingbundles.sets"));
        field.setMaxLength(2);
        field.setValue("1");
        field.setFilter(s -> s.chars().allMatch(Character::isDigit));
        field.setResponder(this::onType);
        add.accept(field);
        inc = new AeIconButton(Icon.ARROW_RIGHT, Component.translatable("screen.appliedcraftingbundles.sets.inc"),
                b -> step(1));
        inc.setX(x + 44);
        inc.setY(y);
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
