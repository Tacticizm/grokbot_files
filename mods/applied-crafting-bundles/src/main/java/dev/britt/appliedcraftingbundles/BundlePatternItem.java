package dev.britt.appliedcraftingbundles;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

public class BundlePatternItem extends Item {
    public BundlePatternItem(Properties properties) {
        super(properties);
    }

    /** Right-click in the air: open the Bundle Encoder. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new BundleEncoderMenu(id, inv, hand),
                    Component.translatable("screen.appliedcraftingbundles.encoder")), buf -> buf.writeEnum(hand));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        BundleContents contents = stack.getOrDefault(ModComponents.BUNDLE_CONTENTS.get(), BundleContents.EMPTY);
        if (contents.isEmpty()) {
            tooltip.accept(Component.translatable("tooltip.appliedcraftingbundles.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        String name = contents.name().isBlank()
                ? Component.translatable("tooltip.appliedcraftingbundles.unnamed").getString()
                : contents.name();
        tooltip.accept(Component.translatable("tooltip.appliedcraftingbundles.name", name).withStyle(ChatFormatting.GOLD));
        for (ItemStackTemplate entry : contents.entries()) {
            tooltip.accept(Component.translatable("tooltip.appliedcraftingbundles.entry", entry.count(),
                    entry.create().getHoverName()).withStyle(ChatFormatting.GRAY));
        }
    }
}
