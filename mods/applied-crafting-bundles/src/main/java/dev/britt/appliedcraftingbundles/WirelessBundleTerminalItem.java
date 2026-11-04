package dev.britt.appliedcraftingbundles;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.items.tools.powered.WirelessTerminalItem;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * One handheld item with a Provider tab and an Encoder tab. Linking (Wireless Access Point slot), AE power
 * and charging come from AE2's wireless terminal; only the menus are ours.
 */
public class WirelessBundleTerminalItem extends WirelessTerminalItem {
    /** AE per bundle request. */
    public static final double REQUEST_POWER = 100;

    public WirelessBundleTerminalItem(Properties props) {
        super(() -> 1_600_000, props);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            openProvider(sp, hand);
        }
        return InteractionResult.SUCCESS;
    }

    public static void openProvider(ServerPlayer sp, InteractionHand hand) {
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new WirelessProviderMenu(id, inv, hand),
                Component.translatable("item.appliedcraftingbundles.wireless_terminal")), buf -> buf.writeEnum(hand));
    }

    public static void openEncoder(ServerPlayer sp, InteractionHand hand) {
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> BundleEncoderMenu.forWireless(id, inv, hand),
                Component.translatable("item.appliedcraftingbundles.wireless_terminal")), buf -> {
                    buf.writeBoolean(true);
                    buf.writeEnum(hand);
                });
    }

    /** An active access point of the linked grid that is in range of the player, or null. */
    public @Nullable IWirelessAccessPoint accessPointInRange(Player player, InteractionHand hand, Consumer<Component> error) {
        var stack = player.getItemInHand(hand);
        IGrid grid = getLinkedGrid(stack, player.level(), error);
        if (grid == null) {
            return null;
        }
        for (Class<?> cls : grid.getMachineClasses()) {
            if (!IWirelessAccessPoint.class.isAssignableFrom(cls)) {
                continue;
            }
            for (Object o : grid.getActiveMachines(cls)) {
                var wap = (IWirelessAccessPoint) o;
                var loc = wap.getLocation();
                if (wap.isActive() && loc.getLevel() == player.level()
                        && loc.getPos().getCenter().distanceTo(player.position()) <= wap.getRange()) {
                    return wap;
                }
            }
        }
        error.accept(Component.translatable("message.appliedcraftingbundles.out_of_range"));
        return null;
    }
}
