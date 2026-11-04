package dev.britt.appliedcraftingbundles.client;

import dev.britt.appliedcraftingbundles.BundleProviderMenu;
import dev.britt.appliedcraftingbundles.ModNetwork;
import net.minecraft.client.Minecraft;

/** Client-side packet handlers (only ever called on the client). */
public final class ClientPayloads {
    public static void queue(ModNetwork.QueueSync p) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof BundleProviderMenu m && m.pos.equals(p.pos())) {
            m.clientPieces = p.pieces();
        }
    }

    public static void openMissing(ModNetwork.OpenMissing p) {
        Minecraft.getInstance().setScreen(new MissingPiecesScreen(p));
    }

    private ClientPayloads() {
    }
}
