package dev.britt.appliedcraftingbundles;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CraftingBundles.MODID);

    public static final DeferredItem<BundlePatternItem> BUNDLE_PATTERN = ITEMS.registerItem("bundle_pattern",
            BundlePatternItem::new, p -> p.stacksTo(64));

    public static final DeferredItem<appeng.items.parts.PartItem<BundleEncoderPart>> BUNDLE_ENCODER = ITEMS.registerItem(
            "bundle_encoder", p -> new appeng.items.parts.PartItem<>(p, BundleEncoderPart.class, BundleEncoderPart::new));

    public static final DeferredItem<WirelessBundleTerminalItem> WIRELESS_TERMINAL = ITEMS.registerItem(
            "wireless_terminal", WirelessBundleTerminalItem::new, p -> p.stacksTo(1));

    /** AE2's main creative tab ("ae2:main"), referenced by id so we don't touch AE2 internals. */
    private static final ResourceKey<CreativeModeTab> AE2_MAIN_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("ae2", "main"));

    static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(AE2_MAIN_TAB)) {
            event.accept(BUNDLE_PATTERN.get());
            event.accept(exampleBundle());
            event.accept(ModBlocks.BUNDLE_PROVIDER_ITEM.get());
            event.accept(BUNDLE_ENCODER.get());
            event.accept(WIRELESS_TERMINAL.get());
        }
    }

    /** Pre-filled sample so the tooltip and data can be checked in game before the encoder exists. */
    private static ItemStack exampleBundle() {
        return BundlePatternItem.encoded(new BundleContents("Example Farm Set", List.of(
                new ItemStackTemplate(Items.FLOWER_POT, 1),
                new ItemStackTemplate(Items.DIRT, 1),
                new ItemStackTemplate(Items.CHEST, 1))));
    }

    private ModItems() {
    }
}
