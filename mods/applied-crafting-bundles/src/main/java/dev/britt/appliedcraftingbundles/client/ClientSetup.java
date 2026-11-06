package dev.britt.appliedcraftingbundles.client;

import dev.britt.appliedcraftingbundles.CraftingBundles;
import dev.britt.appliedcraftingbundles.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = CraftingBundles.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    static void screens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.BUNDLE_PROVIDER.get(), BundleProviderScreen::new);
        event.register(ModMenus.BUNDLE_ENCODER.get(), BundleEncoderScreen::new);
        event.register(ModMenus.WIRELESS_PROVIDER.get(), WirelessProviderScreen::new);
    }

    private ClientSetup() {
    }
}
