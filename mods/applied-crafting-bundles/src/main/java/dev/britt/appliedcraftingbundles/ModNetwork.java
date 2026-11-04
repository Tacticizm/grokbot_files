package dev.britt.appliedcraftingbundles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/** All packets. Client handlers live in {@code client.ClientPayloads}; everything here is validated server side. */
public final class ModNetwork {
    public static final int MAX_NAME = 40, MAX_COUNT = 999, MAX_SETS = 64;

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CraftingBundles.MODID, path));
    }

    static final StreamCodec<io.netty.buffer.ByteBuf, InteractionHand> HAND_CODEC =
            ByteBufCodecs.idMapper(i -> InteractionHand.values()[i & 1], InteractionHand::ordinal);

    // ---- server -> client ----

    public record PieceView(UUID id, ItemStackTemplate item, long amount, String state, String note) {
        static final StreamCodec<RegistryFriendlyByteBuf, PieceView> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, PieceView::id, ItemStackTemplate.STREAM_CODEC, PieceView::item,
                ByteBufCodecs.VAR_LONG, PieceView::amount, ByteBufCodecs.STRING_UTF8, PieceView::state,
                ByteBufCodecs.STRING_UTF8, PieceView::note, PieceView::new);
    }

    public record QueueSync(BlockPos pos, List<PieceView> pieces) implements CustomPacketPayload {
        public static final Type<QueueSync> TYPE = payloadType("queue_sync");
        static final StreamCodec<RegistryFriendlyByteBuf, QueueSync> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, QueueSync::pos, PieceView.CODEC.apply(ByteBufCodecs.list()), QueueSync::pieces,
                QueueSync::new);

        @Override
        public Type<QueueSync> type() {
            return TYPE;
        }
    }

    /** One bundle entry in the missing-pieces menu. {@code missing} is empty when the entry can be crafted. */
    public record EntryView(int index, ItemStackTemplate item, long amount, long inStorage, List<String> missing) {
        static final StreamCodec<RegistryFriendlyByteBuf, EntryView> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, EntryView::index, ItemStackTemplate.STREAM_CODEC, EntryView::item,
                ByteBufCodecs.VAR_LONG, EntryView::amount, ByteBufCodecs.VAR_LONG, EntryView::inStorage,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                EntryView::missing, EntryView::new);
    }

    /** {@code wireless}: the request came from the wireless terminal, not the provider at {@code pos}. */
    public record OpenMissing(BlockPos pos, boolean wireless, String bundleName, List<EntryView> entries) implements CustomPacketPayload {
        public static final Type<OpenMissing> TYPE = payloadType("open_missing");
        static final StreamCodec<RegistryFriendlyByteBuf, OpenMissing> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenMissing::pos, ByteBufCodecs.BOOL, OpenMissing::wireless,
                ByteBufCodecs.STRING_UTF8, OpenMissing::bundleName,
                EntryView.CODEC.apply(ByteBufCodecs.list()), OpenMissing::entries, OpenMissing::new);

        @Override
        public Type<OpenMissing> type() {
            return TYPE;
        }
    }

    /** Wireless terminal: pieces that couldn't get a crafting CPU, with AE2's error text (like AE2's CraftErrorScreen). */
    public record CraftError(List<Component> lines, boolean noCpu) implements CustomPacketPayload {
        public static final Type<CraftError> TYPE = payloadType("craft_error");
        static final StreamCodec<RegistryFriendlyByteBuf, CraftError> CODEC = StreamCodec.composite(
                net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(64)), CraftError::lines,
                ByteBufCodecs.BOOL, CraftError::noCpu, CraftError::new);

        @Override
        public Type<CraftError> type() {
            return TYPE;
        }
    }

    // ---- client -> server ----

    /** Answer to {@link CraftError}: Retry or Cancel. */
    public record CraftErrorChoice(boolean retry) implements CustomPacketPayload {
        public static final Type<CraftErrorChoice> TYPE = payloadType("craft_error_choice");
        static final StreamCodec<RegistryFriendlyByteBuf, CraftErrorChoice> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, CraftErrorChoice::retry, CraftErrorChoice::new);

        @Override
        public Type<CraftErrorChoice> type() {
            return TYPE;
        }
    }

    public record RequestBundle(BlockPos pos, int slot, int sets) implements CustomPacketPayload {
        public static final Type<RequestBundle> TYPE = payloadType("request_bundle");
        static final StreamCodec<RegistryFriendlyByteBuf, RequestBundle> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RequestBundle::pos, ByteBufCodecs.VAR_INT, RequestBundle::slot,
                ByteBufCodecs.VAR_INT, RequestBundle::sets, RequestBundle::new);

        @Override
        public Type<RequestBundle> type() {
            return TYPE;
        }
    }

    public record CancelPiece(BlockPos pos, UUID id) implements CustomPacketPayload {
        public static final Type<CancelPiece> TYPE = payloadType("cancel_piece");
        static final StreamCodec<RegistryFriendlyByteBuf, CancelPiece> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, CancelPiece::pos, UUIDUtil.STREAM_CODEC, CancelPiece::id, CancelPiece::new);

        @Override
        public Type<CancelPiece> type() {
            return TYPE;
        }
    }

    public record WirelessRequest(InteractionHand hand, int slot, int sets) implements CustomPacketPayload {
        public static final Type<WirelessRequest> TYPE = payloadType("wireless_request");
        static final StreamCodec<RegistryFriendlyByteBuf, WirelessRequest> CODEC = StreamCodec.composite(
                HAND_CODEC, WirelessRequest::hand, ByteBufCodecs.VAR_INT, WirelessRequest::slot,
                ByteBufCodecs.VAR_INT, WirelessRequest::sets, WirelessRequest::new);

        @Override
        public Type<WirelessRequest> type() {
            return TYPE;
        }
    }

    /** Switch the wireless terminal between its Provider and Encoder tabs. */
    public record WirelessTab(InteractionHand hand, boolean encoder) implements CustomPacketPayload {
        public static final Type<WirelessTab> TYPE = payloadType("wireless_tab");
        static final StreamCodec<RegistryFriendlyByteBuf, WirelessTab> CODEC = StreamCodec.composite(
                HAND_CODEC, WirelessTab::hand, ByteBufCodecs.BOOL, WirelessTab::encoder, WirelessTab::new);

        @Override
        public Type<WirelessTab> type() {
            return TYPE;
        }
    }

    public record SendToNetwork(BlockPos pos) implements CustomPacketPayload {
        public static final Type<SendToNetwork> TYPE = payloadType("send_to_network");
        static final StreamCodec<RegistryFriendlyByteBuf, SendToNetwork> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SendToNetwork::pos, SendToNetwork::new);

        @Override
        public Type<SendToNetwork> type() {
            return TYPE;
        }
    }

    /** craft=false means Cancel. */
    public record MissingChoice(BlockPos pos, boolean wireless, boolean craft, List<Integer> indices) implements CustomPacketPayload {
        public static final Type<MissingChoice> TYPE = payloadType("missing_choice");
        static final StreamCodec<RegistryFriendlyByteBuf, MissingChoice> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, MissingChoice::pos, ByteBufCodecs.BOOL, MissingChoice::wireless,
                ByteBufCodecs.BOOL, MissingChoice::craft,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(9)), MissingChoice::indices, MissingChoice::new);

        @Override
        public Type<MissingChoice> type() {
            return TYPE;
        }
    }

    /** Encode into the open Bundle Encoder menu's pattern slots. */
    public record SaveBundle(String name, List<ItemStackTemplate> entries) implements CustomPacketPayload {
        public static final Type<SaveBundle> TYPE = payloadType("save_bundle");
        static final StreamCodec<RegistryFriendlyByteBuf, SaveBundle> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_NAME * 4), SaveBundle::name,
                ItemStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list(BundleProviderBlockEntity.SLOTS)), SaveBundle::entries,
                SaveBundle::new);

        @Override
        public Type<SaveBundle> type() {
            return TYPE;
        }
    }

    // ---- registration ----

    static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToClient(QueueSync.TYPE, QueueSync.CODEC, (p, ctx) -> dev.britt.appliedcraftingbundles.client.ClientPayloads.queue(p));
        r.playToClient(OpenMissing.TYPE, OpenMissing.CODEC, (p, ctx) -> dev.britt.appliedcraftingbundles.client.ClientPayloads.openMissing(p));
        r.playToClient(CraftError.TYPE, CraftError.CODEC, (p, ctx) -> dev.britt.appliedcraftingbundles.client.ClientPayloads.craftError(p));
        r.playToServer(CraftErrorChoice.TYPE, CraftErrorChoice.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                var outcome = WirelessRequests.retryBlocked(sp, p.retry());
                if (!p.retry()) {
                    sp.sendSystemMessage(Component.translatable("message.appliedcraftingbundles.cancelled"));
                } else {
                    report(sp, sp.blockPosition(), true, "", List::of, outcome);
                }
            }
        });
        r.playToServer(RequestBundle.TYPE, RequestBundle.CODEC, ModNetwork::onRequest);
        r.playToServer(CancelPiece.TYPE, CancelPiece.CODEC, ModNetwork::onCancelPiece);
        r.playToServer(MissingChoice.TYPE, MissingChoice.CODEC, ModNetwork::onMissingChoice);
        r.playToServer(SendToNetwork.TYPE, SendToNetwork.CODEC, (p, ctx) -> {
            var be = provider(ctx, p.pos());
            if (be != null) {
                be.sendOutputToNetwork();
            }
        });
        r.playToServer(WirelessRequest.TYPE, WirelessRequest.CODEC, ModNetwork::onWirelessRequest);
        r.playToServer(WirelessTab.TYPE, WirelessTab.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp && sp.getItemInHand(p.hand()).is(ModItems.WIRELESS_TERMINAL.get())) {
                if (p.encoder()) {
                    WirelessBundleTerminalItem.openEncoder(sp, p.hand());
                } else {
                    WirelessBundleTerminalItem.openProvider(sp, p.hand());
                }
            }
        });
        r.playToServer(SaveBundle.TYPE, SaveBundle.CODEC, ModNetwork::onSave);
    }

    public static void sendQueue(ServerPlayer player, BundleProviderBlockEntity be) {
        var views = new ArrayList<PieceView>();
        for (var pc : be.getPieces()) {
            views.add(new PieceView(pc.id, pc.item, pc.amount, pc.state.name(), pc.note));
        }
        PacketDistributor.sendToPlayer(player, new QueueSync(be.getBlockPos(), views));
    }

    /** The provider at {@code pos}, only if the player is close enough to be using it. */
    private static @Nullable BundleProviderBlockEntity provider(IPayloadContext ctx, BlockPos pos) {
        var player = ctx.player();
        if (player.distanceToSqr(pos.getCenter()) > 64 || !player.level().isLoaded(pos)) {
            return null;
        }
        return player.level().getBlockEntity(pos) instanceof BundleProviderBlockEntity be ? be : null;
    }

    private static void onRequest(RequestBundle p, IPayloadContext ctx) {
        var be = provider(ctx, p.pos());
        var bundle = be == null ? null : be.getBundleInSlot(p.slot());
        if (bundle == null || !(ctx.player() instanceof ServerPlayer sp)) {
            return;
        }
        int sets = Math.clamp(p.sets(), 1, MAX_SETS);
        be.request(bundle, sets, outcome -> report(sp, be.getBlockPos(), false, bundle.name(), be::getPendingEntries, outcome));
    }

    private static void onWirelessRequest(WirelessRequest p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) {
            return;
        }
        var bundle = new WirelessInventory(sp, p.hand()).bundleInSlot(p.slot());
        String name = bundle == null ? "" : bundle.name();
        WirelessRequests.request(sp, p.hand(), p.slot(), Math.clamp(p.sets(), 1, MAX_SETS),
                outcome -> report(sp, sp.blockPosition(), true, name, () -> WirelessRequests.pendingEntries(sp), outcome));
    }

    private static void report(ServerPlayer sp, BlockPos pos, boolean wireless, String bundleName,
            java.util.function.Supplier<List<BundleProviderBlockEntity.PlannedEntry>> pending,
            BundleProviderBlockEntity.Outcome outcome) {
        switch (outcome) {
            case BundleProviderBlockEntity.Outcome.Started s -> {
                sp.sendSystemMessage(Component.translatable(wireless ? "message.appliedcraftingbundles.started_wireless"
                        : "message.appliedcraftingbundles.started", s.jobs(), s.queued()));
                s.failures().forEach(f -> sp.sendSystemMessage(Component.literal("  " + f).withStyle(ChatFormatting.RED)));
            }
            case BundleProviderBlockEntity.Outcome.Missing m -> {
                var views = new ArrayList<EntryView>();
                for (var e : pending.get()) {
                    var missing = e.missing().stream()
                            .map(g -> g.what().formatAmount(g.amount(), appeng.api.stacks.AmountFormat.SLOT) + "x "
                                    + g.what().getDisplayName().getString())
                            .toList();
                    views.add(new EntryView(e.index(), e.entry(), e.amount(), e.fromStock(), missing));
                }
                PacketDistributor.sendToPlayer(sp, new OpenMissing(pos, wireless, bundleName, views));
            }
            case BundleProviderBlockEntity.Outcome.Failed f ->
                    sp.sendSystemMessage(Component.literal(f.reason()).withStyle(ChatFormatting.RED));
        }
    }

    private static void onCancelPiece(CancelPiece p, IPayloadContext ctx) {
        var be = provider(ctx, p.pos());
        if (be != null) {
            be.cancelQueued(p.id());
        }
    }

    private static void onMissingChoice(MissingChoice p, IPayloadContext ctx) {
        if (p.wireless()) {
            if (!(ctx.player() instanceof ServerPlayer sp) || !WirelessRequests.hasPendingDecision(sp)) {
                return;
            }
            if (!p.craft()) {
                WirelessRequests.cancel(sp);
                sp.sendSystemMessage(Component.translatable("message.appliedcraftingbundles.cancelled"));
                return;
            }
            report(sp, p.pos(), true, "", List::of, WirelessRequests.submitSelected(sp, new HashSet<>(p.indices())));
            return;
        }
        var be = provider(ctx, p.pos());
        if (be == null || !be.hasPendingDecision() || !(ctx.player() instanceof ServerPlayer sp)) {
            return;
        }
        if (!p.craft()) {
            be.cancelPending();
            sp.sendSystemMessage(Component.translatable("message.appliedcraftingbundles.cancelled"));
            return;
        }
        var outcome = be.submitSelected(new HashSet<>(p.indices()));
        report(sp, be.getBlockPos(), false, "", List::of, outcome);
    }

    private static void onSave(SaveBundle p, IPayloadContext ctx) {
        var player = ctx.player();
        if (!(player.containerMenu instanceof BundleEncoderMenu menu) || !menu.stillValid(player)) {
            return;
        }
        String name = p.name().strip();
        if (name.length() > MAX_NAME) {
            name = name.substring(0, MAX_NAME);
        }
        var entries = new ArrayList<ItemStackTemplate>();
        for (var e : p.entries()) {
            if (e.item().value() == Items.AIR || entries.size() >= BundleProviderBlockEntity.SLOTS) {
                continue;
            }
            // Only item + count matter for crafting requests; drop any extra components.
            entries.add(new ItemStackTemplate(e.item(), Math.clamp(e.count(), 1, MAX_COUNT)));
        }
        menu.encode(player, new BundleContents(name, entries));
    }

    private ModNetwork() {
    }
}
