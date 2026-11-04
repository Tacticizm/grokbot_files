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
    public record EntryView(int index, ItemStackTemplate item, long amount, List<String> missing) {
        static final StreamCodec<RegistryFriendlyByteBuf, EntryView> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, EntryView::index, ItemStackTemplate.STREAM_CODEC, EntryView::item,
                ByteBufCodecs.VAR_LONG, EntryView::amount, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                EntryView::missing, EntryView::new);
    }

    public record OpenMissing(BlockPos pos, String bundleName, List<EntryView> entries) implements CustomPacketPayload {
        public static final Type<OpenMissing> TYPE = payloadType("open_missing");
        static final StreamCodec<RegistryFriendlyByteBuf, OpenMissing> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenMissing::pos, ByteBufCodecs.STRING_UTF8, OpenMissing::bundleName,
                EntryView.CODEC.apply(ByteBufCodecs.list()), OpenMissing::entries, OpenMissing::new);

        @Override
        public Type<OpenMissing> type() {
            return TYPE;
        }
    }

    // ---- client -> server ----

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

    /** craft=false means Cancel. */
    public record MissingChoice(BlockPos pos, boolean craft, List<Integer> indices) implements CustomPacketPayload {
        public static final Type<MissingChoice> TYPE = payloadType("missing_choice");
        static final StreamCodec<RegistryFriendlyByteBuf, MissingChoice> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, MissingChoice::pos, ByteBufCodecs.BOOL, MissingChoice::craft,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(9)), MissingChoice::indices, MissingChoice::new);

        @Override
        public Type<MissingChoice> type() {
            return TYPE;
        }
    }

    public record SaveBundle(InteractionHand hand, String name, List<ItemStackTemplate> entries) implements CustomPacketPayload {
        public static final Type<SaveBundle> TYPE = payloadType("save_bundle");
        static final StreamCodec<RegistryFriendlyByteBuf, SaveBundle> CODEC = StreamCodec.composite(
                ByteBufCodecs.idMapper(i -> InteractionHand.values()[i & 1], InteractionHand::ordinal), SaveBundle::hand,
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
        r.playToServer(RequestBundle.TYPE, RequestBundle.CODEC, ModNetwork::onRequest);
        r.playToServer(CancelPiece.TYPE, CancelPiece.CODEC, ModNetwork::onCancelPiece);
        r.playToServer(MissingChoice.TYPE, MissingChoice.CODEC, ModNetwork::onMissingChoice);
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
        be.request(bundle, sets, outcome -> report(sp, be, bundle, outcome));
    }

    private static void report(ServerPlayer sp, BundleProviderBlockEntity be, BundleContents bundle,
            BundleProviderBlockEntity.Outcome outcome) {
        switch (outcome) {
            case BundleProviderBlockEntity.Outcome.Started s -> {
                sp.sendSystemMessage(Component.translatable("message.appliedcraftingbundles.started", s.jobs(), s.queued()));
                s.failures().forEach(f -> sp.sendSystemMessage(Component.literal("  " + f).withStyle(ChatFormatting.RED)));
            }
            case BundleProviderBlockEntity.Outcome.Missing m -> {
                var views = new ArrayList<EntryView>();
                for (var e : be.getPendingEntries()) {
                    var missing = e.missing().stream()
                            .map(g -> g.what().formatAmount(g.amount(), appeng.api.stacks.AmountFormat.SLOT) + "x "
                                    + g.what().getDisplayName().getString())
                            .toList();
                    views.add(new EntryView(e.index(), e.entry(), e.amount(), missing));
                }
                PacketDistributor.sendToPlayer(sp, new OpenMissing(be.getBlockPos(), bundle.name(), views));
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
        report(sp, be, BundleContents.EMPTY, outcome);
    }

    private static void onSave(SaveBundle p, IPayloadContext ctx) {
        var player = ctx.player();
        var stack = player.getItemInHand(p.hand());
        if (!stack.is(ModItems.BUNDLE_PATTERN.get())) {
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
        if (entries.isEmpty()) {
            stack.remove(ModComponents.BUNDLE_CONTENTS.get());
        } else {
            stack.set(ModComponents.BUNDLE_CONTENTS.get(), new BundleContents(name, entries));
        }
        player.closeContainer();
    }

    private ModNetwork() {
    }
}
