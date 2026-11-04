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
import dev.britt.appliedcraftingbundles.BundlePatternItem;
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
    private static final Identifier ENCODER = id("encoder_part_edits_and_copies");
    private static final Identifier WIRELESS = id("wireless_terminal_requests_to_inventory");
    private static final Identifier WIRELESS_NO_CPU = id("wireless_terminal_no_cpu_retry");
    private static final Identifier RECIPES = id("recipes_load");
    private static final Identifier STACKING = id("patterns_stack_until_encoded");
    private static final Identifier IN_STORAGE = id("provider_uses_stored_items");

    @SubscribeEvent
    static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, MISSING, () -> (Consumer<GameTestHelper>) BundleGameTests::reportsMissing);
        event.register(Registries.TEST_FUNCTION, NO_CHANNEL, () -> (Consumer<GameTestHelper>) BundleGameTests::needsChannel);
        event.register(Registries.TEST_FUNCTION, CRAFTS, () -> (Consumer<GameTestHelper>) BundleGameTests::craftsBundle);
        event.register(Registries.TEST_FUNCTION, ENCODER, () -> (Consumer<GameTestHelper>) BundleGameTests::encoderPart);
        event.register(Registries.TEST_FUNCTION, WIRELESS, () -> (Consumer<GameTestHelper>) BundleGameTests::wireless);
        event.register(Registries.TEST_FUNCTION, WIRELESS_NO_CPU, () -> (Consumer<GameTestHelper>) BundleGameTests::wirelessNoCpu);
        event.register(Registries.TEST_FUNCTION, RECIPES, () -> (Consumer<GameTestHelper>) BundleGameTests::recipesLoad);
        event.register(Registries.TEST_FUNCTION, STACKING, () -> (Consumer<GameTestHelper>) BundleGameTests::stacking);
        event.register(Registries.TEST_FUNCTION, IN_STORAGE, () -> (Consumer<GameTestHelper>) BundleGameTests::usesStoredItems);
        event.register(Registries.TEST_FUNCTION, QUEUES, () -> (Consumer<GameTestHelper>) BundleGameTests::queuesWhenOneCpu);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent e) {
        var env = e.registerEnvironment(id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        e.registerTest(MISSING, new FunctionGameTestInstance(fn(MISSING), new TestData<>(env, id("empty"), 200, 0, true)));
        e.registerTest(ENCODER, new FunctionGameTestInstance(fn(ENCODER), new TestData<>(env, id("empty"), 200, 0, true)));
        // Own batch: its mock server player must not be near other tests' molecular assemblers (their
        // animation packets can't be sent to a mock connection).
        var soloEnv = e.registerEnvironment(id("solo"), new TestEnvironmentDefinition.AllOf(List.of()));
        e.registerTest(WIRELESS, new FunctionGameTestInstance(fn(WIRELESS), new TestData<>(soloEnv, id("empty"), 1200, 0, true)));
        var soloEnv2 = e.registerEnvironment(id("solo2"), new TestEnvironmentDefinition.AllOf(List.of()));
        e.registerTest(WIRELESS_NO_CPU, new FunctionGameTestInstance(fn(WIRELESS_NO_CPU), new TestData<>(soloEnv2, id("empty"), 1200, 0, true)));
        e.registerTest(RECIPES, new FunctionGameTestInstance(fn(RECIPES), new TestData<>(env, id("empty"), 20, 0, true)));
        e.registerTest(STACKING, new FunctionGameTestInstance(fn(STACKING), new TestData<>(env, id("empty"), 20, 0, true)));
        e.registerTest(IN_STORAGE, new FunctionGameTestInstance(fn(IN_STORAGE), new TestData<>(env, id("empty"), 400, 0, true)));
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

    /** Our hand-written recipes parse (their AE2 ingredients exist) and craft our items. */
    private static void recipesLoad(GameTestHelper helper) {
        var recipes = helper.getLevel().getServer().getRecipeManager();
        for (var name : List.of("bundle_provider", "bundle_pattern", "bundle_encoder", "wireless_terminal")) {
            var holder = recipes.byKey(ResourceKey.create(Registries.RECIPE, id(name)));
            if (holder.isEmpty()) {
                helper.fail("recipe " + name + " did not load");
            }
        }
        helper.succeed();
    }

    /** Blanks stack to 64, encoded patterns to 1. */
    private static void stacking(GameTestHelper helper) {
        if (new ItemStack(ModItems.BUNDLE_PATTERN.get()).getMaxStackSize() != 64) {
            helper.fail("blank should stack to 64");
        }
        var encoded = BundlePatternItem.encoded(new BundleContents("Set", List.of(new ItemStackTemplate(Items.DIRT, 1))));
        if (encoded.getMaxStackSize() != 1) {
            helper.fail("encoded should not stack");
        }
        helper.succeed();
    }

    private static BundleContents contents(String name, net.minecraft.world.item.Item item) {
        return new BundleContents(name, List.of(new ItemStackTemplate(item, 2)));
    }

    private static String nameOf(ItemStack s) {
        var c = s.get(ModComponents.BUNDLE_CONTENTS.get());
        return c == null ? "blank" : c.name();
    }

    /**
     * Bundle Encoder part on a cable: it needs a channel (gets one on a small ad-hoc network), and its
     * input/output slots follow the edit-in-place / copy-to-blank / encode-a-blank rules.
     */
    private static void encoderPart(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        helper.setBlock(new BlockPos(0, 1, 1), AEBlocks.CREATIVE_ENERGY_CELL.block());
        BlockPos cable = helper.absolutePos(new BlockPos(1, 1, 1));
        appeng.api.parts.PartHelper.setPart(level, cable, null, player,
                appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT));
        var part = appeng.api.parts.PartHelper.setPart(level, cable, net.minecraft.core.Direction.UP, player,
                ModItems.BUNDLE_ENCODER.get());
        if (part == null) {
            helper.fail("could not place the encoder part");
            return;
        }
        var inv = part.getInventory();
        var menu = dev.britt.appliedcraftingbundles.BundleEncoderMenu.forPart(0, player.getInventory(), part);

        // 1. Encoded pattern in the input: re-encoded in place.
        inv.setItem(0, BundlePatternItem.encoded(contents("A", Items.DIRT)));
        menu.encode(player, contents("B", Items.STONE));
        if (!nameOf(inv.getItem(0)).equals("B") || !inv.getItem(1).isEmpty()) {
            helper.fail("edit in place failed: " + nameOf(inv.getItem(0)));
        }
        // 2. Blanks in the output: input untouched, one copy split off to the player.
        inv.setItem(1, new ItemStack(ModItems.BUNDLE_PATTERN.get(), 3));
        menu.encode(player, contents("C", Items.SAND));
        if (!nameOf(inv.getItem(0)).equals("B") || inv.getItem(1).getCount() != 2
                || player.getInventory().getNonEquipmentItems().stream().noneMatch(st -> nameOf(st).equals("C"))) {
            helper.fail("copy to blank failed");
        }
        // 3. A single blank in the output becomes the copy.
        inv.setItem(1, new ItemStack(ModItems.BUNDLE_PATTERN.get()));
        menu.encode(player, contents("D", Items.SAND));
        if (!nameOf(inv.getItem(1)).equals("D") || !nameOf(inv.getItem(0)).equals("B")) {
            helper.fail("single blank copy failed");
        }
        // 4. Blanks in the input, empty output: one blank is encoded into the output.
        inv.setItem(0, new ItemStack(ModItems.BUNDLE_PATTERN.get(), 5));
        inv.setItem(1, ItemStack.EMPTY);
        menu.encode(player, contents("E", Items.GRAVEL));
        if (inv.getItem(0).getCount() != 4 || !nameOf(inv.getItem(1)).equals("E") || inv.getItem(1).getCount() != 1) {
            helper.fail("encode from blank failed");
        }
        // 5. Survives save/load.
        var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        part.writeToNBT(out);
        var copy = new dev.britt.appliedcraftingbundles.BundleEncoderPart(ModItems.BUNDLE_ENCODER.get());
        copy.readFromNBT(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
        if (!nameOf(copy.getInventory().getItem(1)).equals("E") || copy.getInventory().getItem(0).getCount() != 4) {
            helper.fail("encoder slots not saved");
        }
        helper.succeedWhen(() -> {
            if (!part.isActive()) {
                helper.fail("encoder part not active (power + channel)");
            }
        });
    }

    /**
     * Wireless terminal linked to an access point: 64 stored redstone is pulled straight into the player's
     * inventory, and 4 planks are crafted (from logs) and delivered there too.
     */
    /**
     * No crafting CPU: like AE2's craft confirm flow, nothing is queued; the piece waits with AE2's
     * NO_CPU_FOUND error until Retry. After a CPU is added, Retry starts it and the planks arrive.
     */
    private static void wirelessNoCpu(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos cellPos = new BlockPos(0, 1, 2), drivePos = new BlockPos(1, 1, 2), wapPos = new BlockPos(1, 2, 2),
                ppPos = new BlockPos(2, 1, 2), maPos = new BlockPos(3, 1, 2);
        helper.setBlock(cellPos, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(drivePos, AEBlocks.DRIVE.block());
        // Access points only connect on their back: face it up so it sits on the drive.
        var wapState = AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState();
        for (var prop : wapState.getProperties()) {
            if (prop.getName().equals("facing") && prop.getValueClass() == net.minecraft.core.Direction.class) {
                @SuppressWarnings("unchecked")
                var dirProp = (net.minecraft.world.level.block.state.properties.Property<net.minecraft.core.Direction>) prop;
                wapState = wapState.setValue(dirProp, net.minecraft.core.Direction.UP);
            }
        }
        helper.setBlock(wapPos, wapState);
        helper.setBlock(ppPos, AEBlocks.PATTERN_PROVIDER.block());
        helper.setBlock(maPos, AEBlocks.MOLECULAR_ASSEMBLER.block());
        helper.getBlockEntity(drivePos, DriveBlockEntity.class).getInternalInventory().addItems(AEItems.ITEM_CELL_4K.stack());
        helper.getBlockEntity(ppPos, PatternProviderBlockEntity.class).getLogic().getPatternInv().addItems(craftingPattern(level,
                "oak_planks", new ItemStack[] {new ItemStack(Items.OAK_LOG)}, new ItemStack(Items.OAK_PLANKS, 4)));

        var player = helper.makeMockServerPlayerInLevel();
        var hp = helper.absolutePos(new BlockPos(2, 2, 3));
        player.setPos(hp.getX() + 0.5, hp.getY(), hp.getZ() + 0.5);
        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        var terminal = new ItemStack(ModItems.WIRELESS_TERMINAL.get());
        ModItems.WIRELESS_TERMINAL.get().injectAEPower(terminal, 10_000, Actionable.MODULATE);
        appeng.items.tools.powered.WirelessTerminalItem.LINKABLE_HANDLER.link(terminal,
                net.minecraft.core.GlobalPos.of(level.dimension(), helper.absolutePos(wapPos)));
        player.setItemInHand(hand, terminal);
        var winv = new dev.britt.appliedcraftingbundles.WirelessInventory(player, hand);
        winv.setItem(0, bundle("Planks", new ItemStackTemplate(Items.OAK_PLANKS, 4)));
        winv.setChanged();

        var outcome = new AtomicReference<Outcome>();
        var step = new int[1];
        helper.succeedWhen(() -> {
            IGrid grid = helper.getBlockEntity(drivePos, DriveBlockEntity.class).getMainNode().getGrid();
            var wap = helper.getBlockEntity(wapPos, appeng.blockentity.networking.WirelessAccessPointBlockEntity.class);
            if (grid == null || !wap.isActive() || !grid.getCraftingService().isCraftable(AEItemKey.of(Items.OAK_PLANKS))) {
                helper.fail("network not ready");
            }
            if (step[0] == 0) {
                grid.getStorageService().getInventory().insert(AEItemKey.of(Items.OAK_LOG), 8, Actionable.MODULATE,
                        IActionSource.empty());
                step[0] = 1;
                dev.britt.appliedcraftingbundles.WirelessRequests.request(player, hand, 0, 1, outcome::set);
            }
            if (step[0] == 1) {
                if (!(outcome.get() instanceof Outcome.Started s)) {
                    helper.fail("calculating: " + outcome.get());
                    return;
                }
                var blocked = dev.britt.appliedcraftingbundles.WirelessRequests.blockedEntries(player);
                if (s.jobs() != 0 || s.queued() != 1 || blocked.size() != 1 || !s.failures().isEmpty()) {
                    throw new IllegalStateException("expected 1 piece waiting for a CPU: " + s + " blocked=" + blocked.size());
                }
                // Retry with still no CPU: stays blocked (AE2: NO_CPU_FOUND).
                var again = dev.britt.appliedcraftingbundles.WirelessRequests.retryBlocked(player, true);
                if (!(again instanceof Outcome.Started a) || a.queued() != 1) {
                    throw new IllegalStateException("retry without a CPU should stay blocked: " + again);
                }
                helper.setBlock(new BlockPos(1, 1, 3), AEBlocks.CRAFTING_STORAGE_1K.block());
                step[0] = 2;
            }
            if (step[0] == 2) {
                if (grid.getCraftingService().getCpus().isEmpty()) {
                    helper.fail("waiting for the new CPU");
                }
                var retried = dev.britt.appliedcraftingbundles.WirelessRequests.retryBlocked(player, true);
                if (!(retried instanceof Outcome.Started r) || r.jobs() != 1 || r.queued() != 0) {
                    throw new IllegalStateException("retry with a CPU should start the job: " + retried);
                }
                // Mock connection can't take AE2's assembler animation packets: move away while it crafts.
                player.teleportTo(player.getX() + 5000, player.getY() + 100, player.getZ());
                step[0] = 3;
            }
            if (player.getInventory().countItem(Items.OAK_PLANKS) != 4) {
                helper.fail("waiting for planks: " + player.getInventory().countItem(Items.OAK_PLANKS));
            }
            level.getServer().getPlayerList().remove(player);
        });
    }

    private static void wireless(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos cellPos = new BlockPos(0, 1, 2), drivePos = new BlockPos(1, 1, 2), wapPos = new BlockPos(1, 2, 2),
                ppPos = new BlockPos(2, 1, 2), maPos = new BlockPos(3, 1, 2);
        helper.setBlock(cellPos, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(drivePos, AEBlocks.DRIVE.block());
        // Access points only connect on their back: face it up so it sits on the drive.
        var wapState = AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState();
        for (var prop : wapState.getProperties()) {
            if (prop.getName().equals("facing") && prop.getValueClass() == net.minecraft.core.Direction.class) {
                @SuppressWarnings("unchecked")
                var dirProp = (net.minecraft.world.level.block.state.properties.Property<net.minecraft.core.Direction>) prop;
                wapState = wapState.setValue(dirProp, net.minecraft.core.Direction.UP);
            }
        }
        helper.setBlock(wapPos, wapState);
        helper.setBlock(ppPos, AEBlocks.PATTERN_PROVIDER.block());
        helper.setBlock(maPos, AEBlocks.MOLECULAR_ASSEMBLER.block());
        helper.setBlock(new BlockPos(1, 1, 3), AEBlocks.CRAFTING_STORAGE_1K.block());
        helper.getBlockEntity(drivePos, DriveBlockEntity.class).getInternalInventory().addItems(AEItems.ITEM_CELL_4K.stack());
        helper.getBlockEntity(ppPos, PatternProviderBlockEntity.class).getLogic().getPatternInv().addItems(craftingPattern(level,
                "oak_planks", new ItemStack[] {new ItemStack(Items.OAK_LOG)}, new ItemStack(Items.OAK_PLANKS, 4)));

        var player = helper.makeMockServerPlayerInLevel();
        var hp = helper.absolutePos(new BlockPos(2, 2, 3));
        player.setPos(hp.getX() + 0.5, hp.getY(), hp.getZ() + 0.5);
        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        var terminal = new ItemStack(ModItems.WIRELESS_TERMINAL.get());
        ModItems.WIRELESS_TERMINAL.get().injectAEPower(terminal, 10_000, Actionable.MODULATE);
        appeng.items.tools.powered.WirelessTerminalItem.LINKABLE_HANDLER.link(terminal,
                net.minecraft.core.GlobalPos.of(level.dimension(), helper.absolutePos(wapPos)));
        player.setItemInHand(hand, terminal);
        var winv = new dev.britt.appliedcraftingbundles.WirelessInventory(player, hand);
        winv.setItem(0, bundle("Mixed", new ItemStackTemplate(Items.REDSTONE, 64), new ItemStackTemplate(Items.OAK_PLANKS, 4)));
        winv.setChanged();

        var outcome = new AtomicReference<Outcome>();
        var step = new int[1];
        helper.succeedWhen(() -> {
            IGrid grid = helper.getBlockEntity(drivePos, DriveBlockEntity.class).getMainNode().getGrid();
            if (grid == null || !grid.getCraftingService().isCraftable(AEItemKey.of(Items.OAK_PLANKS))
                    || grid.getCraftingService().getCpus().isEmpty()) {
                helper.fail("network not ready: grid=" + (grid != null) + (grid == null ? "" : " craftable="
                        + grid.getCraftingService().isCraftable(AEItemKey.of(Items.OAK_PLANKS))
                        + " cpus=" + grid.getCraftingService().getCpus().size()));
            }
            var storage = grid.getStorageService().getInventory();
            if (step[0] == 0) {
                storage.insert(AEItemKey.of(Items.REDSTONE), 330, Actionable.MODULATE, IActionSource.empty());
                storage.insert(AEItemKey.of(Items.OAK_LOG), 8, Actionable.MODULATE, IActionSource.empty());
                step[0] = 1;
                helper.fail("stocked");
            }
            var wap = helper.getBlockEntity(wapPos, appeng.blockentity.networking.WirelessAccessPointBlockEntity.class);
            if (step[0] == 1 && !wap.isActive()) {
                helper.fail("access point not active: range=" + wap.getRange() + " sameGrid=" + (wap.getGrid() == grid));
            }
            if (step[0] == 1) {
                step[0] = 2;
                dev.britt.appliedcraftingbundles.WirelessRequests.request(player, hand, 0, 1, outcome::set);
                // The mock player's connection can't take AE2's assembler animation packets, so move it far
                // away once the (in-range) request is made. Delivery to the inventory doesn't need range.
                player.teleportTo(player.getX() + 5000, player.getY() + 100, player.getZ());
            }
            if (!(outcome.get() instanceof Outcome.Started s)) {
                if (outcome.get() != null) {
                    throw new IllegalStateException("expected Started, got " + outcome.get() + " "
                            + dev.britt.appliedcraftingbundles.WirelessRequests.pendingEntries(player).stream()
                                    .map(en -> en.entry() + " stock=" + en.fromStock() + " missing=" + en.missing()).toList());
                }
                helper.fail("calculating");
                return;
            }
            if (s.jobs() != 2 || !s.failures().isEmpty()) {
                throw new IllegalStateException("unexpected " + s);
            }
            int red = player.getInventory().countItem(Items.REDSTONE);
            if (red != 64 || storage.getAvailableStacks().get(AEItemKey.of(Items.REDSTONE)) != 266) {
                throw new IllegalStateException("stored redstone should go to the player: " + red);
            }
            if (player.getInventory().countItem(Items.OAK_PLANKS) != 4) {
                helper.fail("waiting for crafted planks: " + player.getInventory().countItem(Items.OAK_PLANKS));
            }
            var requester = dev.britt.appliedcraftingbundles.WirelessRequests.requesterFor(player);
            var link = requester.getRequestedJobs().stream().findFirst().orElse(null);
            // Full inventory: the overflow goes into the network.
            for (int i = 0; i < 36; i++) {
                if (player.getInventory().getItem(i).isEmpty()) {
                    player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
                }
            }
            requester.insertCraftedItems(link, AEItemKey.of(Items.STICK), 3, Actionable.MODULATE);
            if (storage.getAvailableStacks().get(AEItemKey.of(Items.STICK)) != 3) {
                throw new IllegalStateException("overflow should go to the network: stored="
                        + storage.getAvailableStacks().get(AEItemKey.of(Items.STICK)) + " inv=" + player.getInventory().countItem(Items.STICK)
                        + " ground=" + level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(3)).size());
            }
            level.getServer().getPlayerList().remove(player);
        });
    }

    /** Drive with 330 redstone (no pattern) + 40 glowstone. Bundle: 64 redstone, 50 glowstone, 1 dirt. */
    private static void usesStoredItems(GameTestHelper helper) {
        BlockPos drivePos = new BlockPos(1, 1, 1), ourPos = new BlockPos(2, 1, 1);
        helper.setBlock(new BlockPos(0, 1, 1), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(drivePos, AEBlocks.DRIVE.block());
        helper.setBlock(ourPos, ModBlocks.BUNDLE_PROVIDER.get());
        helper.getBlockEntity(drivePos, DriveBlockEntity.class).getInternalInventory().addItems(AEItems.ITEM_CELL_4K.stack());
        var be = helper.getBlockEntity(ourPos, BundleProviderBlockEntity.class);
        be.insertPattern(bundle("Redstone", new ItemStackTemplate(Items.REDSTONE, 64),
                new ItemStackTemplate(Items.GLOWSTONE_DUST, 50), new ItemStackTemplate(Items.DIRT, 1)));
        var outcome = new AtomicReference<Outcome>();
        var step = new int[1];
        helper.succeedWhen(() -> {
            IGrid grid = be.getActionableNode() == null ? null : be.getActionableNode().getGrid();
            if (grid == null || !be.isNetworkActive()) {
                helper.fail("network not ready");
            }
            var storage = grid.getStorageService().getInventory();
            if (step[0] == 0) {
                storage.insert(AEItemKey.of(Items.REDSTONE), 330, Actionable.MODULATE, IActionSource.empty());
                storage.insert(AEItemKey.of(Items.GLOWSTONE_DUST), 40, Actionable.MODULATE, IActionSource.empty());
                step[0] = 1;
                helper.fail("stocked, waiting for storage cache");
            }
            if (step[0] == 1) {
                step[0] = 2;
                be.request(be.getBundles().getFirst(), 1, outcome::set);
            }
            if (!(outcome.get() instanceof Outcome.Missing)) {
                helper.fail("no Missing outcome yet: " + outcome.get());
                return;
            }
            var entries = be.getPendingEntries();
            var red = entries.get(0);
            var glow = entries.get(1);
            var dirt = entries.get(2);
            if (red.isMissing() || !red.fullyInStock() || red.fromStock() != 64) {
                throw new IllegalStateException("redstone should be in storage: " + red);
            }
            if (!glow.isMissing() || glow.fromStock() != 40 || glow.missing().getFirst().amount() != 10) {
                throw new IllegalStateException("glowstone should be 40 stored, 10 short: " + glow + " " + glow.missing());
            }
            if (!dirt.isMissing() || dirt.fromStock() != 0) {
                throw new IllegalStateException("dirt should be short: " + dirt);
            }
            if (!(be.submitSelected(java.util.Set.of(0)) instanceof Outcome.Started s) || s.jobs() != 1 || !s.failures().isEmpty()) {
                throw new IllegalStateException("redstone piece did not start");
            }
            if (be.getPieces().size() != 1 || be.getPieces().getFirst().state != PieceState.DONE) {
                throw new IllegalStateException("redstone piece should be done at once: " + be.getPieces().size());
            }
            var red64 = AEItemKey.of(Items.REDSTONE);
            if (countOutput(be, Items.REDSTONE) != 64 || storage.getAvailableStacks().get(red64) != 266) {
                throw new IllegalStateException("64 redstone should move to the output buffer");
            }
            var cap = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                    helper.absolutePos(ourPos), null);
            if (cap == null || cap.getAmountAsLong(0) != 64) {
                throw new IllegalStateException("output buffer not exposed as an item capability");
            }
            if (be.sendOutputToNetwork() != 64 || countOutput(be, Items.REDSTONE) != 0
                    || storage.getAvailableStacks().get(red64) != 330) {
                throw new IllegalStateException("send to network did not empty the buffer");
            }
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
            // Finished pieces land in the provider's output buffer, not in ME storage.
            long p = countOutput(be, Items.OAK_PLANKS);
            long st = countOutput(be, Items.STICK);
            boolean allDone = be.getPieces().size() == 2 && be.getPieces().stream().allMatch(pc -> pc.state == PieceState.DONE);
            if (p != 4 || st < 4 || !allDone) {
                helper.fail("waiting: planks=" + p + " sticks=" + st + " states="
                        + be.getPieces().stream().map(pc -> pc.state + "/" + pc.note).toList());
            }
        });
    }

    private static long countOutput(BundleProviderBlockEntity be, net.minecraft.world.item.Item item) {
        return be.getOutputContainer().getItems().stream().filter(s -> s.is(item)).mapToLong(ItemStack::getCount).sum();
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
