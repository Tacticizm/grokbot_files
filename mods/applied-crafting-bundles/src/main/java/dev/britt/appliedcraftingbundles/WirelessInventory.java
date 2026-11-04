package dev.britt.appliedcraftingbundles;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The wireless terminal's own slots, stored on the item (vanilla container component):
 * 0-8 Bundle Patterns for the Provider tab, 9-10 the Encoder tab's input and output.
 */
public class WirelessInventory extends SimpleContainer {
    public static final int PATTERNS = 9, ENCODER_IN = 9, SIZE = 11;

    private final Player player;
    private final InteractionHand hand;

    public WirelessInventory(Player player, InteractionHand hand) {
        super(SIZE);
        this.player = player;
        this.hand = hand;
        ItemStack stack = player.getItemInHand(hand);
        stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(getItems());
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.is(ModItems.BUNDLE_PATTERN.get());
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(ModItems.WIRELESS_TERMINAL.get())) {
            stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(getItems()));
        }
    }

    public BundleContents bundleInSlot(int slot) {
        if (slot < 0 || slot >= PATTERNS) {
            return null;
        }
        BundleContents c = getItem(slot).get(ModComponents.BUNDLE_CONTENTS.get());
        return c == null || c.isEmpty() ? null : c;
    }
}
