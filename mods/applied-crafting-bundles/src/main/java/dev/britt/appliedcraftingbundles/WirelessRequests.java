package dev.britt.appliedcraftingbundles;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import com.google.common.collect.ImmutableSet;
import dev.britt.appliedcraftingbundles.BundleProviderBlockEntity.Calc;
import dev.britt.appliedcraftingbundles.BundleProviderBlockEntity.Outcome;
import dev.britt.appliedcraftingbundles.BundleProviderBlockEntity.PlannedEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Bundle requests from the wireless terminal. Same stock check and planning as the Bundle Provider, but the
 * results go into the player's inventory (overflow into the network).
 * <p>
 * No free crafting CPU works like AE2's own crafting confirm flow ({@code CraftConfirmMenu.startJob} ->
 * {@code CraftErrorScreen}): nothing is queued; the submit error is shown with AE2's message and the player
 * picks Retry or Cancel. Retry stays disabled while the network has no crafting CPUs at all, like AE2's
 * Start button.
 */
public final class WirelessRequests {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, PlayerRequester> REQUESTERS = new HashMap<>();

    private static final class Session {
        final IGrid grid;
        final appeng.api.implementations.blockentities.IWirelessAccessPoint wap;
        @Nullable Calc calc;
        Consumer<Outcome> callback;
        List<PlannedEntry> awaiting = List.of();
        /** Planned pieces whose submit failed for lack of a crafting CPU, waiting for Retry or Cancel. */
        List<PlannedEntry> blocked = List.of();
        long deadline;

        Session(IGrid grid, appeng.api.implementations.blockentities.IWirelessAccessPoint wap, Calc calc,
                Consumer<Outcome> callback) {
            this.grid = grid;
            this.wap = wap;
            this.calc = calc;
            this.callback = callback;
        }
    }

    /** Receives crafted items for one player while the server runs. */
    public static final class PlayerRequester implements ICraftingRequester {
        final UUID player;
        final MinecraftServer server;
        IGrid grid;
        IGridNode node;
        final Set<ICraftingLink> links = new HashSet<>();

        PlayerRequester(UUID player, MinecraftServer server) {
            this.player = player;
            this.server = server;
        }

        @Override
        public ImmutableSet<ICraftingLink> getRequestedJobs() {
            return ImmutableSet.copyOf(links);
        }

        @Override
        public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable mode) {
            if (mode == Actionable.SIMULATE) {
                return amount;
            }
            ServerPlayer sp = server.getPlayerList().getPlayer(player);
            long left = amount;
            if (sp != null && what instanceof AEItemKey item) {
                left = giveToPlayer(sp, item, amount);
            }
            if (left > 0) {
                left -= StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(),
                        what, left, IActionSource.empty(), Actionable.MODULATE);
            }
            if (left > 0 && sp != null && what instanceof AEItemKey item) {
                sp.drop(item.toStack((int) Math.min(left, Integer.MAX_VALUE)), false); // never lose items
            }
            return amount;
        }

        @Override
        public void jobStateChange(ICraftingLink link) {
            links.remove(link);
        }

        @Override
        public @Nullable IGridNode getActionableNode() {
            return node;
        }
    }

    /** Put as much as fits into the player's main inventory; returns what didn't fit. */
    static long giveToPlayer(Player player, AEItemKey item, long amount) {
        var inv = player.getInventory();
        int max = item.toStack().getMaxStackSize();
        for (int i = 0; i < inv.getNonEquipmentItems().size() && amount > 0; i++) {
            ItemStack s = inv.getItem(i);
            // Room is counted by hand: Inventory.add() silently deletes items for creative players.
            if (s.isEmpty()) {
                int n = (int) Math.min(amount, max);
                inv.setItem(i, item.toStack(n));
                amount -= n;
            } else if (item.matches(s) && s.getCount() < max) {
                int n = (int) Math.min(amount, max - s.getCount());
                s.grow(n);
                amount -= n;
            }
        }
        return amount;
    }

    public static @Nullable PlayerRequester requesterFor(Player player) {
        return REQUESTERS.get(player.getUUID());
    }

    public static boolean hasPendingDecision(Player player) {
        var s = SESSIONS.get(player.getUUID());
        return s != null && !s.awaiting.isEmpty();
    }

    public static List<PlannedEntry> pendingEntries(Player player) {
        var s = SESSIONS.get(player.getUUID());
        return s == null ? List.of() : s.awaiting;
    }

    /** Request bundle {@code slot} of the terminal in {@code hand}. The callback runs on the server thread. */
    public static void request(ServerPlayer sp, InteractionHand hand, int slot, int sets, Consumer<Outcome> callback) {
        var stack = sp.getItemInHand(hand);
        if (!(stack.getItem() instanceof WirelessBundleTerminalItem item)) {
            return;
        }
        BundleContents bundle = new WirelessInventory(sp, hand).bundleInSlot(slot);
        if (bundle == null) {
            return;
        }
        if (SESSIONS.containsKey(sp.getUUID())) {
            callback.accept(new Outcome.Failed("A bundle request is already pending"));
            return;
        }
        var error = new Component[1];
        var wap = item.accessPointInRange(sp, hand, c -> error[0] = c);
        IGrid grid = wap == null ? null : wap.getGrid();
        if (grid == null) {
            callback.accept(new Outcome.Failed(error[0] == null ? "Not linked" : error[0].getString()));
            return;
        }
        if (!item.usePower(sp, WirelessBundleTerminalItem.REQUEST_POWER, stack)) {
            callback.accept(new Outcome.Failed(Component.translatable("message.appliedcraftingbundles.no_power").getString()));
            return;
        }
        Calc calc = BundleProviderBlockEntity.beginCalc(grid, sp.level(), () -> IActionSource.ofMachine(wap), bundle, sets);
        SESSIONS.put(sp.getUUID(), new Session(grid, wap, calc, callback));
    }

    static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        var later = new ArrayList<Runnable>(); // start() may add sessions, so not while iterating
        for (var it = SESSIONS.entrySet().iterator(); it.hasNext();) {
            var e = it.next();
            Session s = e.getValue();
            ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
            boolean waiting = !s.awaiting.isEmpty() || !s.blocked.isEmpty();
            if (sp == null || (waiting && server.overworld().getGameTime() > s.deadline)) {
                if (s.calc != null) {
                    s.calc.cancel();
                }
                it.remove();
                continue;
            }
            if (s.calc == null || !s.calc.isDone()) {
                continue;
            }
            List<PlannedEntry> planned;
            try {
                planned = s.calc.collect();
            } catch (Exception ex) {
                it.remove();
                s.callback.accept(new Outcome.Failed("Crafting calculation failed: " + ex.getMessage()));
                continue;
            }
            s.calc = null;
            if (planned.stream().noneMatch(PlannedEntry::isMissing)) {
                it.remove();
                s.callback.accept(start(sp, s, planned));
            } else {
                s.awaiting = List.copyOf(planned);
                s.deadline = server.overworld().getGameTime() + 20 * 120;
                later.add(() -> s.callback.accept(new Outcome.Missing(List.of())));
            }
        }
        later.forEach(Runnable::run);
    }

    public static List<PlannedEntry> blockedEntries(Player player) {
        var s = SESSIONS.get(player.getUUID());
        return s == null ? List.of() : s.blocked;
    }

    /** Retry (or, with {@code retry=false}, cancel) the pieces that couldn't get a crafting CPU. */
    public static Outcome retryBlocked(ServerPlayer sp, boolean retry) {
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null || s.blocked.isEmpty()) {
            return new Outcome.Failed("Nothing waiting for a crafting CPU");
        }
        SESSIONS.remove(sp.getUUID());
        if (!retry) {
            return new Outcome.Started(0, 0, List.of());
        }
        return start(sp, s, s.blocked);
    }

    /** AE2's own text for a failed submit (same lang keys and wording as {@code CraftErrorScreen}). */
    static Component submitErrorText(appeng.api.networking.crafting.ICraftingSubmitResult result) {
        var code = result.errorCode();
        if (code == null) {
            return Component.empty();
        }
        return switch (code) {
            case NO_CPU_FOUND -> Component.translatable("gui.ae2.CraftErrorNoCpuFound");
            case NO_SUITABLE_CPU_FOUND -> {
                var text = Component.translatable("gui.ae2.CraftErrorNoSuitableCpu");
                if (result.errorDetail() instanceof appeng.api.networking.crafting.UnsuitableCpus u) {
                    var parts = new ArrayList<Component>();
                    if (u.offline() > 0) parts.add(Component.translatable("gui.ae2.CraftErrorNoSuitableCpuOffline", u.offline()));
                    if (u.busy() > 0) parts.add(Component.translatable("gui.ae2.CraftErrorNoSuitableCpuBusy", u.busy()));
                    if (u.tooSmall() > 0) parts.add(Component.translatable("gui.ae2.CraftErrorNoSuitableCpuTooSmall", u.tooSmall()));
                    if (u.excluded() > 0) parts.add(Component.translatable("gui.ae2.CraftErrorNoSuitableCpuExcluded", u.excluded()));
                    if (!parts.isEmpty()) {
                        text.append(" (");
                        for (int i = 0; i < parts.size(); i++) {
                            text.append(i == 0 ? Component.empty() : Component.literal(", ")).append(parts.get(i));
                        }
                        text.append(")");
                    }
                }
                yield text;
            }
            case CPU_BUSY -> Component.translatable("gui.ae2.CraftErrorCpuBusy");
            case CPU_OFFLINE -> Component.translatable("gui.ae2.CraftErrorCpuOffline");
            case CPU_TOO_SMALL -> Component.translatable("gui.ae2.CraftErrorCpuTooSmall");
            case MISSING_INGREDIENT -> Component.translatable("gui.ae2.CraftErrorMissingIngredient");
            case INCOMPLETE_PLAN -> Component.translatable("gui.ae2.CraftErrorIncompletePlan");
            default -> Component.literal(code.name());
        };
    }

    public static Outcome submitSelected(ServerPlayer sp, Set<Integer> indices) {
        Session s = SESSIONS.remove(sp.getUUID());
        if (s == null) {
            return new Outcome.Failed("Nothing pending");
        }
        return start(sp, s, s.awaiting.stream().filter(e -> indices.contains(e.index())).toList());
    }

    public static void cancel(Player player) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s != null && s.calc != null) {
            s.calc.cancel();
        }
    }

    private static Outcome start(ServerPlayer sp, Session session, List<PlannedEntry> entries) {
        IGrid grid = session.grid;
        var requester = REQUESTERS.computeIfAbsent(sp.getUUID(), id -> new PlayerRequester(id, sp.level().getServer()));
        requester.grid = grid;
        requester.node = session.wap.getActionableNode();
        var source = IActionSource.ofMachine(session.wap);
        int started = 0;
        var failures = new ArrayList<String>();
        var blocked = new ArrayList<PlannedEntry>();
        var errorLines = new ArrayList<Component>();
        for (PlannedEntry e : entries) {
            String name = e.entry().create().getHoverName().getString();
            if (e.isMissing()) {
                failures.add(name + ": missing ingredients");
                continue;
            }
            AEItemKey key = AEItemKey.of(e.entry());
            if (e.fromStock() > 0) {
                var inv = grid.getStorageService().getInventory();
                long got = StorageHelper.poweredExtraction(grid.getEnergyService(), inv, key, e.fromStock(), source,
                        Actionable.MODULATE);
                long left = giveToPlayer(sp, key, got);
                if (left > 0) { // inventory full: it stays in the network
                    StorageHelper.poweredInsert(grid.getEnergyService(), inv, key, left, source, Actionable.MODULATE);
                }
                if (got < e.fromStock()) {
                    failures.add(name + ": only " + got + " of " + e.fromStock() + " left in storage");
                }
            }
            if (e.fullyInStock()) {
                started++;
                continue;
            }
            var result = grid.getCraftingService().submitJob(e.plan(), requester, null, false, source);
            if (result.successful() && result.link() != null) {
                requester.links.add(result.link());
                started++;
            } else if (result.errorCode() != null && BundleProviderBlockEntity.CPU_ERRORS.contains(result.errorCode())) {
                // Stock was already handed out: a retry only submits the crafted part again.
                blocked.add(new PlannedEntry(e.index(), e.entry(), e.amount(), 0, e.plan()));
                errorLines.add(Component.literal(e.plan().finalOutput().amount() + "x " + name + ": ").append(submitErrorText(result)));
            } else {
                failures.add(name + ": " + submitErrorText(result).getString());
            }
        }
        if (!blocked.isEmpty()) {
            session.blocked = List.copyOf(blocked);
            session.awaiting = List.of();
            session.calc = null;
            session.deadline = sp.level().getServer().overworld().getGameTime() + 20 * 120;
            SESSIONS.put(sp.getUUID(), session);
            boolean noCpu = grid.getCraftingService().getCpus().isEmpty();
            if (sp.connection.hasChannel(ModNetwork.CraftError.TYPE)) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new ModNetwork.CraftError(errorLines, noCpu));
            }
        }
        return new Outcome.Started(started, blocked.size(), failures);
    }

    private WirelessRequests() {
    }
}
