package dev.britt.appliedcraftingbundles;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CraftingBundles.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BundleContents>> BUNDLE_CONTENTS =
            COMPONENTS.registerComponentType("bundle_contents", b -> b
                    .persistent(BundleContents.CODEC)
                    .networkSynchronized(BundleContents.STREAM_CODEC)
                    .cacheEncoding());

    private ModComponents() {
    }
}
