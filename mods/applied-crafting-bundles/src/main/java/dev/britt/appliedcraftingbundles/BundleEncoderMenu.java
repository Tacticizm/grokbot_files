package dev.britt.appliedcraftingbundles;

import java.util.function.Predicate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Input + output pattern slots and the player inventory (so items can be picked onto the cursor).
 * The 9 entries are ghost cells drawn by the screen. Hosted by the Bundle Encoder part or the wireless terminal.
 */
public class BundleEncoderMenu extends AbstractContainerMenu {
    public static final int INV_Y = 120, IN_X = 124, OUT_X = 152, IO_Y = 66;

    public final Container container;
    public final int inSlot, outSlot;
    /** Set for the wireless terminal, which shows a Provider tab. */
    @Nullable
    public final InteractionHand wirelessHand;
    private final Predicate<Player> valid;

    private BundleEncoderMenu(int id, Inventory inv, Container container, int inSlot, @Nullable InteractionHand wirelessHand,
            Predicate<Player> valid) {
        super(ModMenus.BUNDLE_ENCODER.get(), id);
        this.container = container;
        this.inSlot = inSlot;
        this.outSlot = inSlot + 1;
        this.wirelessHand = wirelessHand;
        this.valid = valid;
        addSlot(new PatternSlot(container, inSlot, IN_X, IO_Y));
        addSlot(new PatternSlot(container, outSlot, OUT_X, IO_Y));
        addStandardInventorySlots(inv, 8, INV_Y);
    }

    public static BundleEncoderMenu forPart(int id, Inventory inv, BundleEncoderPart part) {
        var side = part.getSide();
        return new BundleEncoderMenu(id, inv, part.getInventory(), 0, null, p -> part.getHost() != null
                && part.getHost().getPart(side) == part && !part.getBlockEntity().isRemoved()
                && p.distanceToSqr(part.getBlockEntity().getBlockPos().getCenter()) <= 64);
    }

    public static BundleEncoderMenu forWireless(int id, Inventory inv, InteractionHand hand) {
        return new BundleEncoderMenu(id, inv, new WirelessInventory(inv.player, hand), WirelessInventory.ENCODER_IN, hand,
                p -> p.getItemInHand(hand).is(ModItems.WIRELESS_TERMINAL.get()));
    }

    public static BundleEncoderMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            var hand = buf.readEnum(InteractionHand.class);
            return new BundleEncoderMenu(id, inv, new net.minecraft.world.SimpleContainer(WirelessInventory.SIZE),
                    WirelessInventory.ENCODER_IN, hand, p -> true);
        }
        buf.readBlockPos();
        buf.readEnum(net.minecraft.core.Direction.class);
        return new BundleEncoderMenu(id, inv, BundleEncoding.newContainer(() -> {}), 0, null, p -> true);
    }

    /** Called from the Save packet. */
    public boolean encode(Player player, BundleContents contents) {
        boolean ok = BundleEncoding.encode(container, inSlot, outSlot, player, contents);
        broadcastChanges();
        return ok;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!stack.is(ModItems.BUNDLE_PATTERN.get()) || !moveItemStackTo(stack, 0, 1, false)) {
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
    public boolean stillValid(Player player) {
        return valid.test(player);
    }

    private static class PatternSlot extends Slot {
        PatternSlot(Container c, int i, int x, int y) {
            super(c, i, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ModItems.BUNDLE_PATTERN.get());
        }
    }
}
