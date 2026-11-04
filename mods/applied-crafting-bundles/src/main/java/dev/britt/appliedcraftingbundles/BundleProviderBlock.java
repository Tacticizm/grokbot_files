package dev.britt.appliedcraftingbundles;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class BundleProviderBlock extends BaseEntityBlock {
    public static final MapCodec<BundleProviderBlock> CODEC = simpleCodec(BundleProviderBlock::new);

    public BundleProviderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BundleProviderBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlocks.BUNDLE_PROVIDER_BE.get(), BundleProviderBlockEntity::serverTick);
    }

    /** Right-click with a Bundle Pattern: insert it. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.BUNDLE_PATTERN.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BundleProviderBlockEntity be) {
            if (be.insertPattern(stack.copyWithCount(1))) {
                stack.shrink(1);
            } else {
                player.sendSystemMessage(Component.translatable("message.appliedcraftingbundles.full"));
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Sneak + empty hand: pop the last pattern out. Plain empty hand: open the provider screen. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BundleProviderBlockEntity be) {
            if (player.isShiftKeyDown()) {
                ItemStack out = be.extractLastPattern();
                if (!out.isEmpty() && !player.getInventory().add(out)) {
                    player.drop(out, false);
                }
            } else if (player instanceof ServerPlayer sp) {
                sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new BundleProviderMenu(id, inv, be),
                        Component.translatable("block.appliedcraftingbundles.bundle_provider")), buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
