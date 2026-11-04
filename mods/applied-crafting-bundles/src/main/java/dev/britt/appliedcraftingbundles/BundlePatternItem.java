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

    /** A single encoded pattern. Encoded patterns don't stack (blanks stack to 64), like AE2's. */
    public static ItemStack encoded(BundleContents contents) {
        ItemStack stack = new ItemStack(ModItems.BUNDLE_PATTERN.get());
        stack.set(ModComponents.BUNDLE_CONTENTS.get(), contents);
        stack.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 1);
        return stack;
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
