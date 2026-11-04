package dev.britt.appliedcraftingbundles;

import appeng.api.parts.IPartItem;
import appeng.parts.reporting.AbstractDisplayPart;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** Flat terminal on a cable (needs a channel). Holds the encoder's input and output pattern slots. */
public class BundleEncoderPart extends AbstractDisplayPart {
    private final SimpleContainer inv = BundleEncoding.newContainer(this::onInvChanged);

    public BundleEncoderPart(IPartItem<?> partItem) {
        super(partItem, true);
    }

    private void onInvChanged() {
        if (getHost() != null) {
            getHost().markForSave();
        }
    }

    public SimpleContainer getInventory() {
        return inv;
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (player instanceof ServerPlayer sp && getBlockEntity() != null) {
            var bePos = getBlockEntity().getBlockPos();
            var side = getSide();
            sp.openMenu(new SimpleMenuProvider((id, pinv, p) -> BundleEncoderMenu.forPart(id, pinv, this),
                    Component.translatable("item.appliedcraftingbundles.bundle_encoder")), buf -> {
                        buf.writeBoolean(false);
                        buf.writeBlockPos(bePos);
                        buf.writeEnum(side);
                    });
        }
        return true;
    }

    @Override
    public void writeToNBT(ValueOutput data) {
        super.writeToNBT(data);
        ContainerHelper.saveAllItems(data.child("encoder"), inv.getItems());
    }

    @Override
    public void readFromNBT(ValueInput data) {
        super.readFromNBT(data);
        inv.clearContent();
        data.child("encoder").ifPresent(c -> ContainerHelper.loadAllItems(c, inv.getItems()));
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        for (ItemStack s : inv.getItems()) {
            if (!s.isEmpty()) {
                drops.add(s.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        inv.clearContent();
    }
}
