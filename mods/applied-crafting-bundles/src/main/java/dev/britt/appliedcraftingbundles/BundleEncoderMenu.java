package dev.britt.appliedcraftingbundles;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Player inventory only, so you can pick items onto the cursor. The 9 entries are ghost cells drawn by the screen. */
public class BundleEncoderMenu extends AbstractContainerMenu {
    public static final int INV_Y = 120;
    public final InteractionHand hand;

    public BundleEncoderMenu(int id, Inventory inv, InteractionHand hand) {
        super(ModMenus.BUNDLE_ENCODER.get(), id);
        this.hand = hand;
        addStandardInventorySlots(inv, 8, INV_Y);
    }

    public static BundleEncoderMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new BundleEncoderMenu(id, inv, buf.readEnum(InteractionHand.class));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).is(ModItems.BUNDLE_PATTERN.get());
    }
}
