package dev.britt.appliedcraftingbundles.client;

import appeng.client.gui.widgets.IconButton;
import appeng.util.Icon;
import net.minecraft.network.chat.Component;

/** AE2 toolbar icon button. The message is the tooltip, matching {@link IconButton}. */
final class AeIconButton extends IconButton {
    private final Icon icon;

    AeIconButton(Icon icon, Component tooltip, OnPress onPress) {
        super(onPress);
        this.icon = icon;
        setMessage(tooltip);
    }

    @Override
    protected Icon getIcon() {
        return icon;
    }
}
