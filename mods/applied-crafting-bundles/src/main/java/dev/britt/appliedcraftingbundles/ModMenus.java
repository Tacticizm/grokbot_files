package dev.britt.appliedcraftingbundles;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CraftingBundles.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<BundleProviderMenu>> BUNDLE_PROVIDER =
            MENUS.register("bundle_provider", () -> IMenuTypeExtension.create(BundleProviderMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<BundleEncoderMenu>> BUNDLE_ENCODER =
            MENUS.register("bundle_encoder", () -> IMenuTypeExtension.create(BundleEncoderMenu::fromNetwork));

    private ModMenus() {
    }
}
