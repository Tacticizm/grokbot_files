package dev.britt.appliedcraftingbundles;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageHelper;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Holds Bundle Patterns and turns "request bundle x N" into one AE2 crafting job per entry.
 * Flow: request() -> calculate every entry -> if nothing is missing, submit all;
 * otherwise wait in {@link #getMissingPieces()} for submitSelected(...) or cancelPending().
 */
public class BundleProviderBlockEntity extends BlockEntity implements IInWorldGridNodeHost, ICraftingRequester {
    public static final int SLOTS = 9, OUTPUT_SLOTS = 27;

    /** One calculated entry of a bundle request. */
    /** {@code fromStock} is taken straight from ME storage; only the rest ({@code plan}, null if none) is crafted. */
    public record PlannedEntry(int index, ItemStackTemplate entry, long amount, long fromStock, @Nullable ICraftingPlan plan) {
        public boolean isMissing() {
            return plan != null && !plan.missingItems().isEmpty();
        }

        public boolean fullyInStock() {
            return plan == null;
        }

        public List<GenericStack> missing() {
            var list = new ArrayList<GenericStack>();
            if (plan == null) {
                return list;
            }
            for (var e : plan.missingItems()) {
                list.add(new GenericStack(e.getKey(), e.getLongValue()));
            }
            return list;
        }
    }

    /** What the stage-4 menu shows: the entry and what AE2 says is short for it. */
    public record MissingPiece(int index, ItemStackTemplate entry, long amount, List<GenericStack> missing) {
    }

    /** Result handed back to whoever asked (chat for now, the menu later). */
    public sealed interface Outcome {
        record Started(int jobs, int queued, List<String> failures) implements Outcome {}
        record Missing(List<MissingPiece> pieces) implements Outcome {}
        record Failed(String reason) implements Outcome {}
    }

    private record Pending(Calc calc, Consumer<Outcome> callback) {}

    private final SimpleContainer patterns = newPatternContainer(this::setChanged);
    /** Finished and in-stock pieces land here. Players, hoppers and pipes can only take items out. */
    private final SimpleContainer output = newOutputContainer(this::setChanged);
    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(BundleProviderBlockEntity owner, IGridNode node) {
            owner.setChanged();
        }
    })
            .setInWorldNode(true)
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setIdlePowerUsage(1.0)
            .setTagName("proxy")
            .addService(ICraftingRequester.class, this);

    @Nullable
    private Pending calculating;
    /** Calculated plans waiting for a decision because something was missing. */
    private List<PlannedEntry> awaitingDecision = List.of();
    /** A missing-pieces decision nobody answers (closed client, disconnect) is dropped after 2 minutes. */
    private long decisionDeadline;

    public BundleProviderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.BUNDLE_PROVIDER_BE.get(), pos, state);
        mainNode.setVisualRepresentation(ModBlocks.BUNDLE_PROVIDER_ITEM.get());
    }

    // ---- grid node lifecycle ----

    @Override
    public void onLoad() {
        super.onLoad();
        GridHelper.onFirstTick(this, be -> be.mainNode.create(be.getLevel(), be.getBlockPos()));
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        mainNode.destroy();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        mainNode.destroy();
    }

    @Override
    public @Nullable IGridNode getGridNode(Direction dir) {
        return mainNode.getNode();
    }

    @Override
    public @Nullable IGridNode getActionableNode() {
        return mainNode.getNode();
    }

    /** 9 single-item slots that only accept Bundle Patterns. Also used client-side by the menu. */
    public static SimpleContainer newPatternContainer(Runnable onChange) {
        return new SimpleContainer(SLOTS) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return stack.is(ModItems.BUNDLE_PATTERN.get());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public void setChanged() {
                super.setChanged();
                onChange.run();
            }
        };
    }

    public static SimpleContainer newOutputContainer(Runnable onChange) {
        return new SimpleContainer(OUTPUT_SLOTS) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return false;
            }

            @Override
            public void setChanged() {
                super.setChanged();
                onChange.run();
            }
        };
    }

    public SimpleContainer getOutputContainer() {
        return output;
    }

    /** Put up to {@code amount} of {@code key} into the output buffer; returns how much fits/fitted. */
    public long insertOutput(AEItemKey key, long amount, boolean simulate) {
        long left = amount;
        int max = key.toStack().getMaxStackSize();
        for (int i = 0; i < OUTPUT_SLOTS && left > 0; i++) {
            ItemStack s = output.getItem(i);
            int n;
            if (s.isEmpty()) {
                n = (int) Math.min(left, max);
                if (!simulate) {
                    output.setItem(i, key.toStack(n));
                }
            } else if (key.matches(s)) {
                n = (int) Math.min(left, Math.max(0, max - s.getCount()));
                if (!simulate) {
                    s.grow(n);
                }
            } else {
                continue;
            }
            left -= n;
        }
        if (!simulate && left < amount) {
            output.setChanged();
        }
        return amount - left;
    }

    /** "Send to network": move everything in the output buffer into ME storage. Returns items moved. */
    public long sendOutputToNetwork() {
        IGrid grid = mainNode.getGrid();
        if (grid == null || !mainNode.isActive()) {
            return 0;
        }
        long moved = 0;
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            ItemStack s = output.getItem(i);
            AEItemKey key = AEItemKey.of(s);
            if (key == null) {
                continue;
            }
            long in = StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(),
                    key, s.getCount(), source(), Actionable.MODULATE);
            s.shrink((int) in);
            moved += in;
        }
        if (moved > 0) {
            output.setChanged();
        }
        return moved;
    }

    public SimpleContainer getPatternContainer() {
        return patterns;
    }

    /** Bundle in pattern slot {@code slot}, or null. */
    public @Nullable BundleContents getBundleInSlot(int slot) {
        if (slot < 0 || slot >= SLOTS) {
            return null;
        }
        BundleContents c = patterns.getItem(slot).get(ModComponents.BUNDLE_CONTENTS.get());
        return c == null || c.isEmpty() ? null : c;
    }

    public boolean isNetworkActive() {
        return mainNode.isActive();
    }

    // ---- pattern inventory ----

    public boolean insertPattern(ItemStack stack) {
        for (int i = 0; i < SLOTS; i++) {
            if (patterns.getItem(i).isEmpty()) {
                patterns.setItem(i, stack);
                setChanged();
                return true;
            }
        }
        return false;
    }

    public ItemStack extractLastPattern() {
        for (int i = SLOTS - 1; i >= 0; i--) {
            if (!patterns.getItem(i).isEmpty()) {
                ItemStack out = patterns.getItem(i);
                patterns.setItem(i, ItemStack.EMPTY);
                setChanged();
                return out;
            }
        }
        return ItemStack.EMPTY;
    }

    public List<BundleContents> getBundles() {
        var list = new ArrayList<BundleContents>();
        for (ItemStack s : patterns.getItems()) {
            BundleContents c = s.get(ModComponents.BUNDLE_CONTENTS.get());
            if (c != null && !c.isEmpty()) {
                list.add(c);
            }
        }
        return list;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, patterns);
            Containers.dropContents(level, pos, output);
        }
    }

    // ---- requesting ----

    public enum PieceState { QUEUED, PLANNING, RUNNING, DONE, CANCELLED }

    /** One entry of a started bundle, tracked until done. Queued pieces wait for a free crafting CPU. */
    public static final class Piece {
        public final UUID id;
        public final ItemStackTemplate item;
        public final long amount;
        public PieceState state;
        public String note = "";
        @Nullable ICraftingLink link;
        @Nullable Future<ICraftingPlan> planning;

        Piece(UUID id, ItemStackTemplate item, long amount, PieceState state) {
            this.id = id;
            this.item = item.withCount(1);
            this.amount = amount;
            this.state = state;
        }

        public boolean isFinished() {
            return state == PieceState.DONE || state == PieceState.CANCELLED;
        }
    }

    static final Set<CraftingSubmitErrorCode> CPU_ERRORS = EnumSet.of(CraftingSubmitErrorCode.NO_CPU_FOUND,
            CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND, CraftingSubmitErrorCode.CPU_BUSY,
            CraftingSubmitErrorCode.CPU_OFFLINE, CraftingSubmitErrorCode.CPU_TOO_SMALL);
    private static final int MAX_FINISHED_SHOWN = 20;

    private final List<Piece> pieces = new ArrayList<>();
    /** Bumped whenever {@link #pieces} changes, so open screens know to resync. */
    private int piecesVersion;
    private int tickCounter;

    private IActionSource source() {
        return IActionSource.ofMachine(this);
    }

    public List<Piece> getPieces() {
        return pieces;
    }

    public int getPiecesVersion() {
        return piecesVersion;
    }

    private void piecesChanged() {
        piecesVersion++;
        int finished = (int) pieces.stream().filter(Piece::isFinished).count();
        for (var it = pieces.iterator(); finished > MAX_FINISHED_SHOWN && it.hasNext();) {
            if (it.next().isFinished()) {
                it.remove();
                finished--;
            }
        }
        setChanged();
    }

    /** Start calculating every entry of {@code bundle} x {@code sets}. The callback runs on the server thread. */
    public void request(BundleContents bundle, int sets, Consumer<Outcome> callback) {
        IGrid grid = mainNode.getGrid();
        if (grid == null || !mainNode.isActive()) {
            callback.accept(new Outcome.Failed("Bundle Provider is offline: it needs a powered ME network with a free channel"));
            return;
        }
        if (calculating != null || !awaitingDecision.isEmpty()) {
            callback.accept(new Outcome.Failed("A bundle request is already pending"));
            return;
        }
        calculating = new Pending(beginCalc(grid, level, this::source, bundle, sets), callback);
    }

    /** Stock check + one AE2 calculation per entry. {@code futures} entries are null for pieces fully in storage. */
    public record Calc(BundleContents bundle, int sets, List<Long> fromStock, List<Future<ICraftingPlan>> futures) {
        public boolean isDone() {
            return futures.stream().allMatch(f -> f == null || f.isDone());
        }

        public List<PlannedEntry> collect() throws InterruptedException, ExecutionException {
            var planned = new ArrayList<PlannedEntry>();
            for (int i = 0; i < futures.size(); i++) {
                ItemStackTemplate entry = bundle.entries().get(i);
                var f = futures.get(i);
                planned.add(new PlannedEntry(i, entry, (long) entry.count() * sets, fromStock.get(i), f == null ? null : f.get()));
            }
            return planned;
        }

        public void cancel() {
            futures.forEach(f -> {
                if (f != null) {
                    f.cancel(true);
                }
            });
        }
    }

    public static Calc beginCalc(IGrid grid, Level level, ICraftingSimulationRequester src, BundleContents bundle, int sets) {
        var crafting = grid.getCraftingService();
        // Check storage first: AE2's planner reports stored-but-unpatterned items (e.g. redstone) as missing.
        var stock = new java.util.HashMap<AEKey, Long>();
        var fromStock = new ArrayList<Long>();
        var futures = new ArrayList<Future<ICraftingPlan>>();
        for (ItemStackTemplate entry : bundle.entries()) {
            AEItemKey key = AEItemKey.of(entry);
            long want = (long) entry.count() * sets;
            long left = stock.computeIfAbsent(key, k -> grid.getStorageService().getCachedInventory().get(k));
            long take = Math.min(want, left);
            stock.put(key, left - take);
            fromStock.add(take);
            futures.add(take >= want ? null : crafting.beginCraftingCalculation(level, src, key,
                    want - take, CalculationStrategy.REPORT_MISSING_ITEMS));
        }
        return new Calc(bundle, sets, fromStock, futures);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BundleProviderBlockEntity be) {
        if (be.hasPendingDecision() && level.getGameTime() > be.decisionDeadline) {
            be.cancelPending();
        }
        be.pollCalculations();
        be.tickQueue();
    }

    private void pollCalculations() {
        Pending p = calculating;
        if (p == null || !p.calc().isDone()) {
            return;
        }
        calculating = null;
        List<PlannedEntry> planned;
        try {
            planned = p.calc().collect();
        } catch (InterruptedException | ExecutionException | java.util.concurrent.CancellationException e) {
            p.callback().accept(new Outcome.Failed("Crafting calculation failed: " + e.getMessage()));
            return;
        }
        if (planned.stream().noneMatch(PlannedEntry::isMissing)) {
            p.callback().accept(startOrQueue(planned));
        } else {
            awaitingDecision = List.copyOf(planned);
            decisionDeadline = level.getGameTime() + 20 * 120;
            p.callback().accept(new Outcome.Missing(getMissingPieces()));
        }
    }

    /** Entries of the pending request that can't be crafted right now (empty when nothing is pending). */
    public List<MissingPiece> getMissingPieces() {
        return awaitingDecision.stream().filter(PlannedEntry::isMissing)
                .map(e -> new MissingPiece(e.index(), e.entry(), e.amount(), e.missing())).toList();
    }

    /** All entries of the pending request, so a menu can show what *can* be crafted too. */
    public List<PlannedEntry> getPendingEntries() {
        return awaitingDecision;
    }

    public boolean hasPendingDecision() {
        return !awaitingDecision.isEmpty();
    }

    /** "Craft the ticked pieces": start/queue only the chosen entry indices. Entries still missing items are skipped. */
    public Outcome submitSelected(Set<Integer> indices) {
        var chosen = awaitingDecision.stream().filter(e -> indices.contains(e.index())).toList();
        awaitingDecision = List.of();
        return startOrQueue(chosen);
    }

    /** Drop the pending request without crafting anything. */
    public void cancelPending() {
        if (calculating != null) {
            calculating.calc().cancel();
        }
        calculating = null;
        awaitingDecision = List.of();
    }

    /** Remove a queued piece. Running pieces are left to AE2's own crafting status screen. */
    public boolean cancelQueued(UUID id) {
        boolean removed = pieces.removeIf(pc -> pc.id.equals(id) && pc.state == PieceState.QUEUED);
        if (removed) {
            piecesChanged();
        }
        return removed;
    }

    /** Submit what a free CPU will take; everything else that only lacks a CPU is queued. */
    private Outcome startOrQueue(List<PlannedEntry> entries) {
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            return new Outcome.Failed("Bundle Provider lost its network");
        }
        int started = 0, queued = 0;
        var failures = new ArrayList<String>();
        for (PlannedEntry e : entries) {
            String name = e.entry().create().getHoverName().getString();
            if (e.isMissing()) {
                failures.add(name + ": missing ingredients");
                continue;
            }
            if (e.fromStock() > 0) {
                long got = takeFromStorage(grid, AEItemKey.of(e.entry()), e.fromStock());
                if (got < e.fromStock()) {
                    failures.add(name + ": only " + got + " of " + e.fromStock() + " left in storage");
                }
                if (e.fullyInStock()) {
                    var done = new Piece(UUID.randomUUID(), e.entry(), got, PieceState.DONE);
                    done.note = "taken from storage";
                    pieces.add(done);
                    started++;
                    continue;
                }
            }
            var piece = new Piece(UUID.randomUUID(), e.entry(), e.amount() - e.fromStock(), PieceState.QUEUED);
            ICraftingSubmitResult result = grid.getCraftingService().submitJob(e.plan(), this, null, false, source());
            if (result.successful() && result.link() != null) {
                piece.state = PieceState.RUNNING;
                piece.link = result.link();
                started++;
            } else if (result.errorCode() != null && CPU_ERRORS.contains(result.errorCode())) {
                piece.note = "waiting for a free crafting CPU";
                queued++;
            } else {
                failures.add(name + ": " + result.errorCode());
                continue;
            }
            pieces.add(piece);
        }
        piecesChanged();
        return new Outcome.Started(started, queued, failures);
    }

    /**
     * Pull an in-stock piece out of ME storage into the output buffer. Whatever doesn't fit simply stays in
     * the network. Returns how much was really available.
     */
    private long takeFromStorage(IGrid grid, AEItemKey key, long amount) {
        var energy = grid.getEnergyService();
        var inv = grid.getStorageService().getInventory();
        long available = StorageHelper.poweredExtraction(energy, inv, key, amount, source(), Actionable.SIMULATE);
        long fits = insertOutput(key, available, true);
        long taken = StorageHelper.poweredExtraction(energy, inv, key, fits, source(), Actionable.MODULATE);
        insertOutput(key, taken, false);
        return available;
    }

    /** Once a second: if a CPU is free, re-plan and start the oldest queued piece. */
    private void tickQueue() {
        IGrid grid = mainNode.getGrid();
        if (grid == null || !mainNode.isActive()) {
            return;
        }
        Piece planning = pieces.stream().filter(pc -> pc.state == PieceState.PLANNING).findFirst().orElse(null);
        if (planning != null) {
            if (planning.planning == null) {
                planning.state = PieceState.QUEUED;
            } else if (planning.planning.isDone()) {
                finishQueuedPlan(grid, planning);
            }
            return;
        }
        if (++tickCounter % 20 != 0) {
            return;
        }
        var crafting = grid.getCraftingService();
        if (crafting.getCpus().stream().allMatch(cpu -> cpu.isBusy())) {
            return;
        }
        pieces.stream().filter(pc -> pc.state == PieceState.QUEUED).findFirst().ifPresent(pc -> {
            pc.state = PieceState.PLANNING;
            pc.planning = crafting.beginCraftingCalculation(level, this::source, AEItemKey.of(pc.item), pc.amount,
                    CalculationStrategy.REPORT_MISSING_ITEMS);
            piecesChanged();
        });
    }

    private void finishQueuedPlan(IGrid grid, Piece pc) {
        ICraftingPlan plan;
        try {
            plan = pc.planning.get();
        } catch (Exception e) {
            plan = null;
        }
        pc.planning = null;
        pc.state = PieceState.QUEUED;
        if (plan == null || !plan.missingItems().isEmpty()) {
            pc.note = "missing ingredients, will retry";
        } else {
            var result = grid.getCraftingService().submitJob(plan, this, null, false, source());
            if (result.successful() && result.link() != null) {
                pc.state = PieceState.RUNNING;
                pc.link = result.link();
                pc.note = "";
            } else {
                pc.note = "waiting (" + result.errorCode() + ")";
            }
        }
        piecesChanged();
    }

    // ---- ICraftingRequester ----

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        var set = ImmutableSet.<ICraftingLink>builder();
        for (Piece pc : pieces) {
            if (pc.state == PieceState.RUNNING && pc.link != null && !pc.link.isDone() && !pc.link.isCanceled()) {
                set.add(pc.link);
            }
        }
        return set.build();
    }

    /** Finished items go into the output buffer; overflow (and non-items) go into ME storage. */
    @Override
    public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable mode) {
        long buffered = what instanceof AEItemKey item ? insertOutput(item, amount, mode == Actionable.SIMULATE) : 0;
        IGrid grid = mainNode.getGrid();
        if (grid == null || buffered == amount) {
            return buffered;
        }
        return buffered + StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(),
                what, amount - buffered, source(), mode);
    }

    @Override
    public void jobStateChange(ICraftingLink link) {
        // Match by crafting ID: after a reload our link objects are new instances.
        for (Piece pc : pieces) {
            if (pc.link != null && (pc.link == link || pc.link.getCraftingID().equals(link.getCraftingID()))) {
                pc.state = link.isCanceled() ? PieceState.CANCELLED : PieceState.DONE;
                pc.link = null;
            }
        }
        piecesChanged();
    }

    // ---- save / load ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, patterns.getItems());
        ContainerHelper.saveAllItems(output.child("output"), this.output.getItems());
        mainNode.serialize(output.child("gridnode"));
        var list = output.childrenList("pieces");
        for (Piece pc : pieces) {
            var c = list.addChild();
            c.putString("id", pc.id.toString());
            c.store("item", ItemStackTemplate.CODEC, pc.item);
            c.putLong("amount", pc.amount);
            // A piece mid-planning restarts as queued after a reload.
            c.putString("state", (pc.state == PieceState.PLANNING ? PieceState.QUEUED : pc.state).name());
            c.putString("note", pc.note);
            if (pc.link != null) {
                pc.link.writeToNBT(c.child("link"));
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, patterns.getItems());
        output.clearContent();
        input.child("output").ifPresent(o -> ContainerHelper.loadAllItems(o, output.getItems()));
        input.child("gridnode").ifPresent(mainNode::deserialize);
        pieces.clear();
        for (ValueInput c : input.childrenListOrEmpty("pieces")) {
            var item = c.read("item", ItemStackTemplate.CODEC);
            if (item.isEmpty()) {
                continue;
            }
            PieceState state;
            try {
                state = PieceState.valueOf(c.getStringOr("state", "QUEUED"));
            } catch (IllegalArgumentException e) {
                state = PieceState.QUEUED;
            }
            var pc = new Piece(UUID.fromString(c.getStringOr("id", UUID.randomUUID().toString())), item.get(),
                    c.getLongOr("amount", 1), state);
            pc.note = c.getStringOr("note", "");
            var linkTag = c.child("link");
            if (state == PieceState.RUNNING && linkTag.isPresent()) {
                pc.link = StorageHelper.loadCraftingLink(linkTag.get(), this);
            } else if (state == PieceState.RUNNING) {
                pc.state = PieceState.QUEUED;
            }
            pieces.add(pc);
        }
        piecesVersion++;
    }
}
