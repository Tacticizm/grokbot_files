package dev.britt.appliedcraftingbundles;

import appeng.api.AECapabilities;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CraftingBundles.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CraftingBundles.MODID);

    public static final DeferredBlock<BundleProviderBlock> BUNDLE_PROVIDER = BLOCKS.registerBlock("bundle_provider",
            BundleProviderBlock::new, p -> p.mapColor(MapColor.METAL).strength(2.2f, 11f).sound(SoundType.METAL));

    public static final DeferredItem<BlockItem> BUNDLE_PROVIDER_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("bundle_provider", BUNDLE_PROVIDER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BundleProviderBlockEntity>> BUNDLE_PROVIDER_BE =
            BLOCK_ENTITIES.register("bundle_provider",
                    () -> new BlockEntityType<>(BundleProviderBlockEntity::new, BUNDLE_PROVIDER.get()));

    /** Lets AE2 cables find our grid node. */
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, BUNDLE_PROVIDER_BE.get(), (be, ctx) -> be);
        // Lets AE2's charger and other energy sources charge the wireless terminal.
        event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.Energy.ITEM,
                (stack, access) -> new appeng.items.tools.powered.powersink.PoweredItemCapabilities(access,
                        ModItems.WIRELESS_TERMINAL.get(), ModItems.WIRELESS_TERMINAL.get()),
                ModItems.WIRELESS_TERMINAL.get());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, BUNDLE_PROVIDER_BE.get(),
                (be, side) -> net.neoforged.neoforge.transfer.item.VanillaContainerWrapper.of(be.getOutputContainer()));
    }

    private ModBlocks() {
    }
}
