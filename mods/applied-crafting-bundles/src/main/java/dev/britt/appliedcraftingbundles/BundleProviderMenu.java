package dev.britt.appliedcraftingbundles;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 9 pattern slots, the 27-slot output buffer, and the player inventory. The queue list is pushed to the
 * client with {@link ModNetwork.QueueSync}. Output slots stay on the main view; the queue is only a list.
 */
public class BundleProviderMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    @Nullable
    private final BundleProviderBlockEntity be; // server side only
    private final Player player;
    private int sentVersion = -1;

    public static final int TAB_PATTERNS = 0, TAB_QUEUE = 1;

    /** Client view state. */
    public int tab = TAB_PATTERNS;
    public List<ModNetwork.PieceView> clientPieces = List.of();

    public BundleProviderMenu(int id, Inventory inv, BundleProviderBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.getPatternContainer(), be.getOutputContainer(), be);
    }

    public static BundleProviderMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new BundleProviderMenu(id, inv, buf.readBlockPos(), BundleProviderBlockEntity.newPatternContainer(() -> {}),
                BundleProviderBlockEntity.newOutputContainer(() -> {}), null);
    }

    private BundleProviderMenu(int id, Inventory inv, BlockPos pos, Container patterns, Container output, @Nullable BundleProviderBlockEntity be) {
        super(ModMenus.BUNDLE_PROVIDER.get(), id);
        this.pos = pos;
        this.be = be;
        this.player = inv.player;
        for (int i = 0; i < BundleProviderBlockEntity.SLOTS; i++) {
            addSlot(new Slot(patterns, i, GuiLayout.PROV_PATTERN_X + i * 18, GuiLayout.PROV_PATTERN_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModItems.BUNDLE_PATTERN.get());
                }

                @Override
                public boolean isActive() {
                    // Tab is client view state. The server copy stays on the patterns tab, so clicks still land.
                    return tab == TAB_PATTERNS;
                }
            });
        }
        for (int i = 0; i < BundleProviderBlockEntity.OUTPUT_SLOTS; i++) {
            addSlot(new Slot(output, i, GuiLayout.PROV_OUT_X + (i % 9) * 18, GuiLayout.PROV_OUT_Y + (i / 9) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        addStandardInventorySlots(inv, GuiLayout.PROV_INV_X, GuiLayout.PROV_INV_Y);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (be != null && player instanceof ServerPlayer sp && be.getPiecesVersion() != sentVersion) {
            sentVersion = be.getPiecesVersion();
            ModNetwork.sendQueue(sp, be);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int n = BundleProviderBlockEntity.SLOTS, inv = n + BundleProviderBlockEntity.OUTPUT_SLOTS;
        if (index < inv) {
            if (!moveItemStackTo(stack, inv, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!stack.is(ModItems.BUNDLE_PATTERN.get()) || !moveItemStackTo(stack, 0, n, false)) {
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
        return be == null || (!be.isRemoved() && player.distanceToSqr(pos.getCenter()) <= 64);
    }
}
