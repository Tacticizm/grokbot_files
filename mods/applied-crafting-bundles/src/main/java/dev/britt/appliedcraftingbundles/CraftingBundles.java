package dev.britt.appliedcraftingbundles;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(CraftingBundles.MODID)
public class CraftingBundles {
    public static final String MODID = "appliedcraftingbundles";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CraftingBundles(IEventBus modBus) {
        ModComponents.COMPONENTS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ModBlocks::registerCapabilities);
        modBus.addListener(ModItems::addToCreativeTab);
        ModMenus.MENUS.register(modBus);
        modBus.addListener(ModNetwork::register);
        LOGGER.info("Applied: Crafting Bundles loading");
    }
}
