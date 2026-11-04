package dev.britt.appliedcraftingbundles.test;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import dev.britt.appliedcraftingbundles.BundleContents;
import dev.britt.appliedcraftingbundles.BundleProviderBlockEntity;
import dev.britt.appliedcraftingbundles.BundleProviderBlockEntity.Outcome;
import dev.britt.appliedcraftingbundles.BundleProviderBlockEntity.PieceState;
import dev.britt.appliedcraftingbundles.CraftingBundles;
import dev.britt.appliedcraftingbundles.ModBlocks;
import dev.britt.appliedcraftingbundles.ModComponents;
import dev.britt.appliedcraftingbundles.ModItems;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Dev-only GameTests. Uses AE2 internals to build networks; the mod itself only uses AE2's API. */
@EventBusSubscriber(modid = CraftingBundles.MODID)
public final class BundleGameTests {
    private static final Identifier MISSING = id("provider_reports_missing");
    private static final Identifier CRAFTS = id("provider_crafts_bundle");
    private static final Identifier NO_CHANNEL = id("provider_needs_channel");
    private static final Identifier QUEUES = id("provider_queues_one_cpu");

    @SubscribeEvent
    static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, MISSING, () -> (Consumer<GameTestHelper>) BundleGameTests::reportsMissing);
        event.register(Registries.TEST_FUNCTION, NO_CHANNEL, () -> (Consumer<GameTestHelper>) BundleGameTests::needsChannel);
        event.register(Registries.TEST_FUNCTION, CRAFTS, () -> (Consumer<GameTestHelper>) BundleGameTests::craftsBundle);
        event.register(Registries.TEST_FUNCTION, QUEUES, () -> (Consumer<GameTestHelper>) BundleGameTests::queuesWhenOneCpu);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent e) {
        var env = e.registerEnvironment(id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        e.registerTest(MISSING, new FunctionGameTestInstance(fn(MISSING), new TestData<>(env, id("empty"), 200, 0, true)));
        e.registerTest(QUEUES, new FunctionGameTestInstance(fn(QUEUES), new TestData<>(env, id("empty"), 2400, 0, true)));
        e.registerTest(NO_CHANNEL, new FunctionGameTestInstance(fn(NO_CHANNEL), new TestData<>(env, id("wide"), 200, 0, true)));
        e.registerTest(CRAFTS, new FunctionGameTestInstance(fn(CRAFTS), new TestData<>(env, id("empty"), 1200, 0, true)));
    }

    private static ResourceKey<Consumer<GameTestHelper>> fn(Identifier id) {
        return ResourceKey.create(Registries.TEST_FUNCTION, id);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(CraftingBundles.MODID, path);
    }

    private static ItemStack bundle(String name, ItemStackTemplate... entries) {
        ItemStack stack = new ItemStack(ModItems.BUNDLE_PATTERN.get());
        stack.set(ModComponents.BUNDLE_CONTENTS.get(), new BundleContents(name, List.of(entries)));
        return stack;
    }

    /** Controller-less (ad-hoc) networks have 8 channels: 9 drives + our provider leaves it without one. */
    private static void needsChannel(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1), AEBlocks.CREATIVE_ENERGY_CELL.block());
        for (int x = 1; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), AEBlocks.DRIVE.block());
        }
        BlockPos ourPos = new BlockPos(10, 1, 1);
        helper.setBlock(ourPos, ModBlocks.BUNDLE_PROVIDER.get());
        var be = helper.getBlockEntity(ourPos, BundleProviderBlockEntity.class);
        be.insertPattern(bundle("Test", new ItemStackTemplate(Items.DIRT, 1)));
        helper.runAfterDelay(60, () -> {
            var node = be.getActionableNode();
            if (node == null || node.getGrid() == null || !node.isPowered()) {
                helper.fail("provider never joined a powered grid");
            }
            if (be.isNetworkActive()) {
                helper.fail("provider is active without a channel");
            }
            var outcome = new AtomicReference<Outcome>();
            be.request(be.getBundles().getFirst(), 1, outcome::set);
            if (!(outcome.get() instanceof Outcome.Failed f) || !f.reason().contains("channel")) {
                helper.fail("expected a channel failure, got " + outcome.get());
            }
            helper.succeed();
        });
    }

    /** Creative energy cell + provider only: an uncraftable entry comes back as Missing. */
    private static void reportsMissing(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(new BlockPos(2, 1, 1), ModBlocks.BUNDLE_PROVIDER.get());
        var be = helper.getBlockEntity(new BlockPos(2, 1, 1), BundleProviderBlockEntity.class);
        be.insertPattern(bundle("Test", new ItemStackTemplate(Items.DIRT, 2)));

        var outcome = new AtomicReference<Outcome>();
        var requested = new boolean[1];
        helper.succeedWhen(() -> {
            if (!requested[0]) {
                if (!be.isNetworkActive()) {
                    helper.fail("grid not active yet");
                }
                requested[0] = true;
                be.request(be.getBundles().getFirst(), 3, outcome::set);
            }
            if (!(outcome.get() instanceof Outcome.Missing m)) {
                helper.fail("no Missing outcome yet: " + outcome.get());
                return;
            }
            var piece = m.pieces().getFirst();
            if (piece.amount() != 6 || piece.missing().getFirst().amount() != 6 || be.getMissingPieces().size() != 1) {
                throw new IllegalStateException("unexpected missing data: " + piece);
            }
            be.cancelPending();
        });
    }

    /**
     * Full network: energy cell, drive (4k cell, 8 oak logs), two 1k crafting CPUs, pattern provider with
     * planks + sticks patterns, molecular assembler, our provider. Bundle = 4 planks + 4 sticks, x1.
     */
    private static void craftsBundle(GameTestHelper helper) {
        woodSetTest(helper, 2);
    }

    /** One CPU: one piece starts, the other queues and starts after the first finishes. */
    private static void queuesWhenOneCpu(GameTestHelper helper) {
        woodSetTest(helper, 1);
    }

    private static void woodSetTest(GameTestHelper helper, int cpus) {
        var level = helper.getLevel();
        BlockPos cellPos = new BlockPos(0, 1, 2), drivePos = new BlockPos(1, 1, 2), ourPos = new BlockPos(2, 1, 2),
                ppPos = new BlockPos(3, 1, 2), maPos = new BlockPos(4, 1, 2);
        helper.setBlock(cellPos, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(drivePos, AEBlocks.DRIVE.block());
        helper.setBlock(ourPos, ModBlocks.BUNDLE_PROVIDER.get());
        helper.setBlock(ppPos, AEBlocks.PATTERN_PROVIDER.block());
        helper.setBlock(maPos, AEBlocks.MOLECULAR_ASSEMBLER.block());
        // Separate (non-touching) 1-block CPUs: each running job needs its own CPU.
        helper.setBlock(new BlockPos(1, 2, 2), AEBlocks.CRAFTING_STORAGE_1K.block());
        if (cpus > 1) {
            helper.setBlock(new BlockPos(2, 1, 3), AEBlocks.CRAFTING_STORAGE_1K.block());
        }

        helper.getBlockEntity(drivePos, DriveBlockEntity.class).getInternalInventory().addItems(AEItems.ITEM_CELL_4K.stack());

        var pp = helper.getBlockEntity(ppPos, PatternProviderBlockEntity.class);
        pp.getLogic().getPatternInv().addItems(craftingPattern(level, "oak_planks",
                new ItemStack[] {new ItemStack(Items.OAK_LOG)}, new ItemStack(Items.OAK_PLANKS, 4)));
        var planks = new ItemStack(Items.OAK_PLANKS);
        pp.getLogic().getPatternInv().addItems(craftingPattern(level, "stick",
                new ItemStack[] {planks, ItemStack.EMPTY, ItemStack.EMPTY, planks.copy()}, new ItemStack(Items.STICK, 4)));

        var be = helper.getBlockEntity(ourPos, BundleProviderBlockEntity.class);
        be.insertPattern(bundle("Wood Set", new ItemStackTemplate(Items.OAK_PLANKS, 4), new ItemStackTemplate(Items.STICK, 4)));

        var stocked = new boolean[1];
        var outcome = new AtomicReference<Outcome>();
        var requested = new boolean[1];
        var savedOk = new boolean[1];
        helper.succeedWhen(() -> {
            IGrid grid = be.getActionableNode() == null ? null : be.getActionableNode().getGrid();
            if (grid == null || !be.isNetworkActive() || grid.getCraftingService().getCpus().size() < cpus
                    || !grid.getCraftingService().isCraftable(AEItemKey.of(Items.STICK))) {
                helper.fail("network not ready");
            }
            var storage = grid.getStorageService().getInventory();
            if (!stocked[0]) {
                long in = storage.insert(AEItemKey.of(Items.OAK_LOG), 8, Actionable.MODULATE, IActionSource.empty());
                if (in != 8) {
                    throw new IllegalStateException("could not stock logs: " + in);
                }
                stocked[0] = true;
            }
            if (!requested[0]) {
                requested[0] = true;
                be.request(be.getBundles().getFirst(), 1, outcome::set);
            }
            if (!(outcome.get() instanceof Outcome.Started s)) {
                if (outcome.get() != null) {
                    throw new IllegalStateException("expected Started, got " + outcome.get());
                }
                helper.fail("calculating");
                return;
            }
            if (s.jobs() != cpus || s.queued() != 2 - cpus || !s.failures().isEmpty()) {
                throw new IllegalStateException("expected " + cpus + " started / " + (2 - cpus) + " queued, got " + s);
            }
            if (!savedOk[0]) {
                // Running links and queued pieces must survive a save/load round trip (loaded into a fresh copy).
                var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                be.saveWithoutMetadata(out);
                CompoundTag tag = out.buildResult();
                var copy = new BundleProviderBlockEntity(ourPos, be.getBlockState());
                copy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
                long queuedCopy = copy.getPieces().stream().filter(p -> p.state == PieceState.QUEUED).count();
                if (copy.getRequestedJobs().size() != cpus || queuedCopy != 2 - cpus || copy.getBundles().size() != 1) {
                    throw new IllegalStateException("reloaded copy: links=" + copy.getRequestedJobs().size()
                            + " queued=" + queuedCopy);
                }
                savedOk[0] = true;
            }
            long p = storage.getAvailableStacks().get(AEItemKey.of(Items.OAK_PLANKS));
            long st = storage.getAvailableStacks().get(AEItemKey.of(Items.STICK));
            boolean allDone = be.getPieces().size() == 2 && be.getPieces().stream().allMatch(pc -> pc.state == PieceState.DONE);
            // With one CPU the queued sticks job is re-planned after the planks job finished, so AE2 takes
            // 2 of those fresh planks from storage instead of crafting more: only 2 planks remain.
            long wantPlanks = cpus > 1 ? 4 : 2;
            if (p < wantPlanks || st < 4 || !allDone) {
                helper.fail("waiting: planks=" + p + " sticks=" + st + " states="
                        + be.getPieces().stream().map(pc -> pc.state + "/" + pc.note).toList());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static ItemStack craftingPattern(net.minecraft.server.level.ServerLevel level, String recipe, ItemStack[] in,
            ItemStack out) {
        var holder = level.getServer().getRecipeManager()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace(recipe))).orElseThrow();
        var grid = new ItemStack[9];
        java.util.Arrays.fill(grid, ItemStack.EMPTY);
        System.arraycopy(in, 0, grid, 0, in.length);
        return PatternDetailsHelper.encodeCraftingPattern((RecipeHolder<CraftingRecipe>) (Object) holder, grid, out, false, false);
    }

    private BundleGameTests() {
    }
}
