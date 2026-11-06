package dev.britt.appliedcraftingbundles;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Wireless terminal, Provider tab: the 9 pattern slots stored on the item + player inventory. */
public class WirelessProviderMenu extends AbstractContainerMenu {
    public final InteractionHand hand;
    public final Container patterns;

    public WirelessProviderMenu(int id, Inventory inv, InteractionHand hand) {
        this(id, inv, hand, new WirelessInventory(inv.player, hand));
    }

    public static WirelessProviderMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new WirelessProviderMenu(id, inv, buf.readEnum(InteractionHand.class), new SimpleContainer(WirelessInventory.SIZE));
    }

    private WirelessProviderMenu(int id, Inventory inv, InteractionHand hand, Container patterns) {
        super(ModMenus.WIRELESS_PROVIDER.get(), id);
        this.hand = hand;
        this.patterns = patterns;
        for (int i = 0; i < WirelessInventory.PATTERNS; i++) {
            addSlot(new Slot(patterns, i, GuiLayout.TERM_PATTERN_X + i * 18, GuiLayout.TERM_PATTERN_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModItems.BUNDLE_PATTERN.get()) && stack.has(ModComponents.BUNDLE_CONTENTS.get());
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addStandardInventorySlots(inv, GuiLayout.TERM_INV_X, GuiLayout.TERM_INV_Y);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int n = WirelessInventory.PATTERNS;
        if (index < n) {
            if (!moveItemStackTo(stack, n, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!slots.get(0).mayPlace(stack) || !moveItemStackTo(stack, 0, n, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ContainerInput input, Player player) {
        // Don't let the terminal itself be moved while its menu is open.
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId).getItem() == player.getItemInHand(hand)) {
            return;
        }
        super.clicked(slotId, button, input, player);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).is(ModItems.WIRELESS_TERMINAL.get());
    }
}
