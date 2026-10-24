// SPDX-FileCopyrightText: 2026 klikli-dev
//
// SPDX-License-Identifier: MIT

package com.klikli_dev.occultism.gametest;

import com.klikli_dev.occultism.Occultism;
import com.klikli_dev.occultism.api.common.data.GlobalBlockPos;
import com.klikli_dev.occultism.api.common.data.MachineReference;
import com.klikli_dev.occultism.common.blockentity.StableWormholeBlockEntity;
import com.klikli_dev.occultism.common.blockentity.StorageControllerBlockEntity;
import com.klikli_dev.occultism.common.container.storage.StorageControllerContainer;
import com.klikli_dev.occultism.common.entity.ai.BlockSorter;
import com.klikli_dev.occultism.common.entity.ai.StorageProxySearch;
import com.klikli_dev.occultism.common.entity.job.ManageMachineJob;
import com.klikli_dev.occultism.common.misc.ItemStackComparator;
import com.klikli_dev.occultism.common.misc.MapItemResourceHandler;
import com.klikli_dev.occultism.network.messages.MessageUpdateLinkedMachines;
import com.klikli_dev.occultism.network.messages.MessageUpdateStacks;
import com.klikli_dev.occultism.registry.OccultismBlocks;
import com.klikli_dev.occultism.registry.OccultismEntities;
import com.klikli_dev.occultism.registry.OccultismItems;
import com.klikli_dev.occultism.util.ItemTransferUtil;
import com.klikli_dev.occultism.util.StorageUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Regression tests for the storage controller: its item handler, block entity data, crafting and helpers.
 * Packet handling is covered in {@link StoragePacketGameTests}.
 */
public class StorageGameTests {

    private static final BlockPos CONTROLLER_POS = new BlockPos(2, 1, 2);
    private static final BlockPos PLAYER_POS = new BlockPos(1, 1, 1);

    /**
     * The controller item handler is exposed as capability, so hoppers and pipes must not be able to insert
     * blacklisted items such as storage remotes or storage controllers.
     */
    public static void controllerHandlerRejectsBlacklistedItems(GameTestHelper helper) {
        var controller = StorageTestSupport.placeController(helper, CONTROLLER_POS);
        var handler = helper.requireCapability(Capabilities.Item.BLOCK, CONTROLLER_POS, null);

        try (var tx = Transaction.openRoot()) {
            int remotes = handler.insert(ItemResource.of(new ItemStack(OccultismItems.STORAGE_REMOTE.get())), 1, tx);
            int controllers = handler.insert(ItemResource.of(new ItemStack(OccultismBlocks.STORAGE_CONTROLLER.get())), 1, tx);
            int dirt = handler.insert(ItemResource.of(new ItemStack(Items.DIRT)), 4, tx);
            helper.assertTrue(remotes == 0, "The capability accepted a blacklisted storage remote");
            helper.assertTrue(controllers == 0, "The capability accepted a blacklisted storage controller");
            helper.assertTrue(dirt == 4, "The capability did not accept a regular item, inserted " + dirt);
            tx.commit();
        }

        helper.assertTrue(StorageTestSupport.stored(controller, OccultismItems.STORAGE_REMOTE.get()) == 0,
                "A storage remote ended up in the storage controller");
        helper.assertTrue(StorageTestSupport.stored(controller, Items.DIRT) == 4, "Regular items were not stored");
        helper.succeed();
    }

    private static int visibleResources(MapItemResourceHandler handler) {
        int visible = 0;
        for (int i = 0; i < handler.size(); i++) {
            if (!handler.getResource(i).isEmpty())
                visible++;
        }
        return visible;
    }

    private static void insert(MapItemResourceHandler handler, ItemStack stack) {
        try (var tx = Transaction.openRoot()) {
            handler.insert(ItemResource.of(stack), stack.getCount(), tx);
            tx.commit();
        }
    }

    /**
     * Lowering the item type limit (e.g. by removing a stabilizer) must not hide stored items, the limit is only
     * enforced on insert.
     */
    public static void loweredTypeLimitKeepsItemsVisible(GameTestHelper helper) {
        var handler = new MapItemResourceHandler(4, -1);
        insert(handler, new ItemStack(Items.DIRT, 5));
        insert(handler, new ItemStack(Items.STONE, 5));
        insert(handler, new ItemStack(Items.COBBLESTONE, 5));
        insert(handler, new ItemStack(Items.OAK_LOG, 5));

        handler.maxItemTypes(2);

        helper.assertTrue(visibleResources(handler) == 4,
                "Only " + visibleResources(handler) + " of 4 stored item types are visible after lowering the type limit");
        try (var tx = Transaction.openRoot()) {
            helper.assertTrue(handler.insert(ItemResource.of(new ItemStack(Items.DIAMOND)), 1, tx) == 0,
                    "A new item type was accepted although the type limit is exceeded");
        }
        helper.succeed();
    }

    /**
     * A total item limit above Integer.MAX_VALUE must not overflow when computing the remaining capacity.
     */
    public static void hugeTotalLimitDoesNotOverflow(GameTestHelper helper) {
        var handler = new MapItemResourceHandler(-1, 10_000_000_000L);
        int inserted;
        try (var tx = Transaction.openRoot()) {
            inserted = handler.insert(ItemResource.of(new ItemStack(Items.DIRT)), 64, tx);
            tx.commit();
        }
        helper.assertTrue(inserted == 64, "Expected to insert 64 items but inserted " + inserted);
        helper.assertTrue(handler.totalItemCount() == 64, "Total item count is " + handler.totalItemCount());
        helper.succeed();
    }

    /**
     * Loading must recompute the total item count and keep every loaded slot below the next slot index, even if the
     * saved values are inconsistent.
     */
    public static void loadingRecomputesCountsAndSlots(GameTestHelper helper) {
        var provider = helper.getLevel().registryAccess();
        var handler = new MapItemResourceHandler();
        insert(handler, new ItemStack(Items.DIRT, 7));
        insert(handler, new ItemStack(Items.STONE, 11));
        insert(handler, new ItemStack(Items.COBBLESTONE, 13));

        CompoundTag tag = handler.serializeNBT(provider);
        tag.putLong("totalItemCount", 999L);
        tag.putInt("nextSlot", 0);

        var loaded = new MapItemResourceHandler();
        loaded.deserializeNBT(provider, tag);

        helper.assertTrue(loaded.totalItemCount() == 31,
                "Loaded total item count is " + loaded.totalItemCount() + " instead of 31");
        helper.assertTrue(visibleResources(loaded) == 3,
                "Only " + visibleResources(loaded) + " of 3 loaded item types are visible");
        helper.succeed();
    }

    /**
     * Requests are now matched against the stored resources before extracting. Verifies the observable behavior:
     * one of the most common matching item, and extraction of a single matching resource.
     */
    public static void requestsExtractMatchingStoredResources(GameTestHelper helper) {
        var controller = StorageTestSupport.placeController(helper, CONTROLLER_POS);
        var namedDirt = new ItemStack(Items.DIRT, 15);
        namedDirt.set(DataComponents.CUSTOM_NAME, Component.literal("Named Dirt"));
        StorageTestSupport.store(controller, new ItemStack(Items.DIRT, 10));
        StorageTestSupport.store(controller, namedDirt);
        StorageTestSupport.store(controller, new ItemStack(Items.STONE, 20));

        //dirt (10 + 15 over two variants) is more common than stone (20)
        var mostCommon = controller.getOneOfMostCommonItem(stack -> stack.is(Items.DIRT) || stack.is(Items.STONE), false);
        helper.assertTrue(mostCommon.is(Items.DIRT) && mostCommon.getCount() == 1,
                "Expected one dirt as most common item but got " + mostCommon);
        helper.assertTrue(controller.getOneOfMostCommonItem(stack -> stack.is(Items.DIAMOND), false).isEmpty(),
                "Got an item for a comparator that matches nothing");

        var exact = controller.getItemStack(new ItemStackComparator(namedDirt, true), 64, false);
        helper.assertTrue(exact.getCount() == 15 && ItemStack.isSameItemSameComponents(exact, namedDirt),
                "Exact request returned " + exact);

        var stone = controller.getItemStack(new ItemStackComparator(new ItemStack(Items.STONE), false), 5, false);
        helper.assertTrue(stone.is(Items.STONE) && stone.getCount() == 5, "Stone request returned " + stone);

        helper.assertTrue(controller.itemStackHandler.get(namedDirt) == 0, "Named dirt was not extracted");
        helper.assertTrue(StorageTestSupport.stored(controller, Items.DIRT) == 9, "Expected 9 plain dirt left");
        helper.assertTrue(StorageTestSupport.stored(controller, Items.STONE) == 15, "Expected 15 stone left");
        helper.succeed();
    }

    /**
     * Linked machines must not be part of the block entity network data, but must still be saved and loaded.
     */
    public static void linkedMachinesAreSavedButNotSynced(GameTestHelper helper) {
        var provider = helper.getLevel().registryAccess();
        var controller = StorageTestSupport.placeController(helper, CONTROLLER_POS);
        var chestPos = new BlockPos(0, 1, 0);
        helper.setBlock(chestPos, Blocks.CHEST);
        var chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
        var machine = MachineReference.from(chest, chest);
        controller.linkMachine(machine);

        CompoundTag updateTag = controller.getUpdateTag(provider);
        helper.assertFalse(updateTag.contains("linkedMachines"), "Linked machines are part of the network update tag");

        CompoundTag saved = controller.saveWithFullMetadata(provider);
        helper.assertTrue(saved.contains("linkedMachines"), "Linked machines were not saved");

        var loaded = BlockEntity.loadStatic(controller.getBlockPos(), controller.getBlockState(), saved, provider);
        helper.assertTrue(loaded instanceof StorageControllerBlockEntity loadedController
                        && loadedController.getLinkedMachines().containsKey(machine.insertGlobalPos),
                "Linked machines were not restored on load");
        helper.succeed();
    }

    private static Recipe<?> recipe(GameTestHelper helper, String path) {
        var key = ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace(path));
        var holder = helper.getLevel().getServer().getRecipeManager().byKey(key);
        helper.assertTrue(holder.isPresent(), "Recipe " + key + " not found");
        return holder.get().value();
    }

    /**
     * Shaped recipes must be mapped into the 3x3 grid based on the recipe placement info.
     */
    public static void ingredientGridFollowsRecipePlacement(GameTestHelper helper) {
        List<Optional<Ingredient>> chest = StorageUtil.ensure3by3CraftingMatrix(recipe(helper, "chest"));
        helper.assertTrue(chest.size() == 9, "Grid has " + chest.size() + " entries");
        for (int slot = 0; slot < 9; slot++) {
            boolean expectIngredient = slot != 4;
            helper.assertTrue(chest.get(slot).isPresent() == expectIngredient,
                    "Chest grid slot " + slot + " should " + (expectIngredient ? "" : "not ") + "have an ingredient");
        }
        helper.assertTrue(chest.get(0).get().test(new ItemStack(Items.OAK_PLANKS)), "Chest ingredient does not accept planks");

        List<Optional<Ingredient>> stick = StorageUtil.ensure3by3CraftingMatrix(recipe(helper, "stick"));
        var stickSlots = stick.stream().filter(Optional::isPresent).count();
        helper.assertTrue(stickSlots == 2, "Stick grid has " + stickSlots + " ingredients instead of 2");
        int first = -1;
        for (int slot = 0; slot < 9; slot++) {
            if (stick.get(slot).isPresent()) {
                first = slot;
                break;
            }
        }
        helper.assertTrue(first >= 0 && first + 3 < 9 && stick.get(first + 3).isPresent(),
                "Stick ingredients are not placed vertically");
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> stacksOf(MessageUpdateStacks message) throws ReflectiveOperationException {
        Field field = MessageUpdateStacks.class.getDeclaredField("stacks");
        field.setAccessible(true);
        return (List<ItemStack>) field.get(message);
    }

    private static RegistryFriendlyByteBuf buffer(GameTestHelper helper) {
        return RegistryFriendlyByteBuf.decorator(helper.getLevel().registryAccess(), ConnectionType.NEOFORGE)
                .apply(new FriendlyByteBuf(Unpooled.buffer()));
    }

    private static MessageUpdateStacks testStacksMessage(GameTestHelper helper, ItemStack named) {
        return new MessageUpdateStacks(List.of(new ItemStack(Items.DIRT, 10), named), 10, 2, 1000, 30, helper.getLevel().registryAccess());
    }

    private static ItemStack namedStone() {
        var named = new ItemStack(Items.STONE, 20);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Named Stone"));
        return named;
    }

    /**
     * Stack sync data must survive being encoded to and decoded from a network buffer.
     * <p>
     * uncompress() only reads payload.readableBytes(), so decode() must advance the payload writer index, otherwise
     * every decoded message yields an empty stack list (dedicated server clients see an empty storage).
     */
    public static void stackSyncSurvivesNetworkRoundTrip(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var named = namedStone();
        var buf = buffer(helper);
        testStacksMessage(helper, named).encode(buf);
        var decoded = new MessageUpdateStacks(buf);
        decoded.uncompress(access);
        try {
            var stacks = stacksOf(decoded);
            helper.assertTrue(stacks.size() == 2, "Decoded " + stacks.size() + " stacks instead of 2");
            helper.assertTrue(stacks.get(0).is(Items.DIRT) && stacks.get(0).getCount() == 10, "First stack is " + stacks.get(0));
            helper.assertTrue(ItemStack.isSameItemSameComponents(stacks.get(1), named) && stacks.get(1).getCount() == 20,
                    "Second stack is " + stacks.get(1));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        helper.succeed();
    }

    /**
     * Truncated stack sync data must be rejected instead of looping forever.
     */
    public static void stackSyncRejectsTruncatedData(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var message = testStacksMessage(helper, namedStone());

        //the message as created on the server can be uncompressed
        message.uncompress(access);
        try {
            helper.assertTrue(stacksOf(message).size() == 2, "Uncompressing the original message lost stacks");
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        //build a message with only half of the compressed data
        var full = buffer(helper);
        message.encode(full);
        int usedTypes = full.readVarInt();
        long usedCount = full.readVarLong();
        int maxTypes = full.readVarInt();
        long maxCount = full.readVarLong();
        byte[] compressed = new byte[full.readVarInt()];
        full.readBytes(compressed);

        var truncatedBuf = buffer(helper);
        truncatedBuf.writeVarInt(usedTypes);
        truncatedBuf.writeVarLong(usedCount);
        truncatedBuf.writeVarInt(maxTypes);
        truncatedBuf.writeVarLong(maxCount);
        truncatedBuf.writeVarInt(compressed.length / 2);
        truncatedBuf.writeBytes(compressed, 0, compressed.length / 2);
        var truncated = new MessageUpdateStacks(truncatedBuf);

        //run in a separate thread, so an endless loop fails the test instead of hanging the server
        AtomicReference<Throwable> error = new AtomicReference<>();
        var thread = new Thread(() -> {
            try {
                truncated.uncompress(access);
            } catch (Throwable t) {
                error.set(t);
            }
        }, "occultism-storage-truncated-stacks-test");
        thread.setDaemon(true);
        thread.start();
        try {
            thread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        helper.assertFalse(thread.isAlive(), "Uncompressing truncated stack data did not terminate");
        helper.assertTrue(error.get() == null, "Uncompressing truncated stack data threw " + error.get());
        try {
            helper.assertTrue(stacksOf(truncated).isEmpty(), "Truncated stack data produced stacks");
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        helper.succeed();
    }

    private static RecipeHolder<CraftingRecipe> holder(String path, CraftingRecipe recipe) {
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Occultism.MODID, path)), recipe);
    }

    private static RecipeOverrideContainer openRecipeOverrideContainer(ServerPlayer player, StorageControllerBlockEntity controller) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new RecipeOverrideContainer(id, inventory, controller),
                Component.literal("storage test")));
        return (RecipeOverrideContainer) player.containerMenu;
    }

    /**
     * When a recipe leaves a remainder that differs from the ingredient, shift crafting must consume the ingredient
     * and hand out the remainder, like vanilla ResultSlot#onTake, instead of handing out extra results.
     */
    public static void shiftCraftingWithForeignRemainderConsumesIngredient(GameTestHelper helper) {
        var controller = StorageTestSupport.placeController(helper, CONTROLLER_POS);
        var player = StorageTestSupport.createPlayer(helper, PLAYER_POS);
        var container = openRecipeOverrideContainer(player, controller);
        try {
            container.overrideRecipe = holder("test_foreign_remainder", new ForeignRemainderRecipe());
            container.getCraftMatrix().setItem(0, new ItemStack(Items.DIRT, 2));

            container.shiftCraft(player);

            int diamonds = player.getInventory().countItem(Items.DIAMOND);
            int sticks = player.getInventory().countItem(Items.STICK);
            helper.assertTrue(diamonds == 2, "Shift crafting 2 dirt produced " + diamonds + " results instead of 2");
            helper.assertTrue(sticks == 2, "Shift crafting 2 dirt returned " + sticks + " remainders instead of 2");
            helper.assertTrue(container.getCraftMatrix().getItem(0).isEmpty(),
                    "The ingredient was not consumed, the grid holds " + container.getCraftMatrix().getItem(0));
            helper.assertFalse(container.isRecipeLocked(), "The crafting grid stayed locked");
        } finally {
            player.doCloseContainer();
        }
        helper.succeed();
    }

    /**
     * Shift crafting a recipe that produces no result must not leave the crafting grid locked.
     */
    public static void shiftCraftingWithEmptyResultUnlocksGrid(GameTestHelper helper) {
        var controller = StorageTestSupport.placeController(helper, CONTROLLER_POS);
        var player = StorageTestSupport.createPlayer(helper, PLAYER_POS);
        var container = openRecipeOverrideContainer(player, controller);
        try {
            container.overrideRecipe = holder("test_empty_result", new EmptyResultRecipe());
            container.getCraftMatrix().setItem(0, new ItemStack(Items.DIRT, 1));

            container.shiftCraft(player);

            helper.assertFalse(container.isRecipeLocked(), "The crafting grid stayed locked after shift crafting an empty result");
            helper.assertTrue(container.getCraftMatrix().getItem(0).getCount() == 1, "The ingredient was consumed");
        } finally {
            player.doCloseContainer();
        }
        helper.succeed();
    }

    /**
     * Storage stacks and linked machines are only resent to an open menu once they changed.
     */
    public static void storageUpdatesAreOnlySentWhenChanged(GameTestHelper helper) {
        var controller = StorageTestSupport.placeController(helper, CONTROLLER_POS);
        var player = StorageTestSupport.createPlayer(helper, PLAYER_POS);
        var packets = StorageTestSupport.packets(player);
        StorageControllerContainer container = StorageTestSupport.openController(player, controller);
        try {
            container.sendStorageUpdates(player, false);
            packets.clear();

            container.sendStorageUpdates(player, false);
            helper.assertTrue(packets.payloads(MessageUpdateStacks.class).isEmpty(), "Unchanged stacks were resent");
            helper.assertTrue(packets.payloads(MessageUpdateLinkedMachines.class).isEmpty(), "Unchanged linked machines were resent");

            StorageTestSupport.store(controller, new ItemStack(Items.DIRT, 3));
            container.sendStorageUpdates(player, false);
            helper.assertTrue(packets.payloads(MessageUpdateStacks.class).size() == 1, "Changed stacks were not sent");
            helper.assertTrue(packets.payloads(MessageUpdateLinkedMachines.class).isEmpty(), "Unchanged linked machines were resent");

            packets.clear();
            var chestPos = new BlockPos(0, 1, 0);
            helper.setBlock(chestPos, Blocks.CHEST);
            var chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
            controller.linkMachine(MachineReference.from(chest, chest));
            container.sendStorageUpdates(player, false);
            helper.assertTrue(packets.payloads(MessageUpdateStacks.class).isEmpty(), "Unchanged stacks were resent");
            helper.assertTrue(packets.payloads(MessageUpdateLinkedMachines.class).size() == 1, "Changed linked machines were not sent");

            packets.clear();
            container.sendStorageUpdates(player, true);
            helper.assertTrue(packets.payloads(MessageUpdateStacks.class).size() == 1, "Forced update did not send stacks");
        } finally {
            player.doCloseContainer();
        }
        helper.succeed();
    }

    /**
     * The storage proxy search finds the closest proxy linked to the controller within the range only.
     */
    public static void proxySearchFindsClosestLinkedProxy(GameTestHelper helper) {
        var controllerPos = new BlockPos(0, 1, 0);
        var linkedPos = new BlockPos(2, 1, 2);
        var unlinkedPos = new BlockPos(4, 1, 4);
        var controller = StorageTestSupport.placeController(helper, controllerPos);
        helper.setBlock(linkedPos, OccultismBlocks.STABLE_WORMHOLE.get());
        helper.setBlock(unlinkedPos, OccultismBlocks.STABLE_WORMHOLE.get());
        var linked = helper.getBlockEntity(linkedPos, StableWormholeBlockEntity.class);
        linked.setLinkedStorageControllerPosition(GlobalBlockPos.from(controller));
        helper.getBlockEntity(unlinkedPos, StableWormholeBlockEntity.class).setLinkedStorageControllerPosition(null);

        var player = StorageTestSupport.createPlayer(helper, unlinkedPos);
        var sorter = new BlockSorter(player);
        var center = helper.absolutePos(unlinkedPos);
        var controllerGlobalPos = GlobalBlockPos.from(controller);

        var found = StorageProxySearch.findClosestLinkedProxy(helper.getLevel(), center, 3, 1, controllerGlobalPos, sorter);
        helper.assertTrue(found == linked, "Expected the linked wormhole but found " + found);

        //with a larger range the controller (a proxy for itself) is in range, but further away than the wormhole
        found = StorageProxySearch.findClosestLinkedProxy(helper.getLevel(), center, 5, 1, controllerGlobalPos, sorter);
        helper.assertTrue(found == linked, "Expected the closer linked wormhole but found " + found);

        found = StorageProxySearch.findClosestLinkedProxy(helper.getLevel(), center, 1, 1, controllerGlobalPos, sorter);
        helper.assertTrue(found == null, "Found a proxy outside of the search range: " + found);
        helper.succeed();
    }

    /**
     * A managed machine whose block entity was removed and replaced must be re-resolved instead of using the stale one.
     */
    public static void manageMachineJobReResolvesReplacedMachine(GameTestHelper helper) {
        var chestPos = new BlockPos(0, 1, 0);
        helper.setBlock(chestPos, Blocks.CHEST);
        var chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
        var spirit = helper.spawn(OccultismEntities.FOLIOT.get(), new BlockPos(2, 1, 2));
        var job = new ManageMachineJob(spirit);
        job.setManagedMachine(MachineReference.from(chest, chest));

        helper.assertTrue(job.getManagedMachineBlockEntity() == chest, "Managed machine was not resolved");
        helper.assertTrue(job.getExtractBlockEntity() == chest, "Extract machine was not resolved");

        helper.setBlock(chestPos, Blocks.AIR);
        helper.setBlock(chestPos, Blocks.CHEST);
        var newChest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);

        helper.assertTrue(job.getManagedMachineBlockEntity() == newChest, "The removed managed machine block entity is still used");
        helper.assertTrue(job.getExtractBlockEntity() == newChest, "The removed extract block entity is still used");
        spirit.discard();
        helper.succeed();
    }

    /**
     * insertItemStacked must not commit or close a transaction passed in by the caller.
     */
    public static void insertItemStackedKeepsCallerTransactionOpen(GameTestHelper helper) {
        var handler = new ItemStacksResourceHandler(2);

        try (var tx = Transaction.openRoot()) {
            var remainder = ItemTransferUtil.insertItemStacked(handler, new ItemStack(Items.DIRT, 10), false, tx);
            helper.assertTrue(remainder.isEmpty(), "Not everything was inserted, remainder " + remainder);
            helper.assertTrue(handler.getAmountAsLong(0) == 10, "Insert is not visible within the caller transaction");
            //not committed: must roll back
        }
        helper.assertTrue(handler.getAmountAsLong(0) == 0, "The insert was committed although the caller aborted its transaction");

        try (var tx = Transaction.openRoot()) {
            ItemTransferUtil.insertItemStacked(handler, new ItemStack(Items.DIRT, 10), false, tx);
            tx.commit();
        }
        helper.assertTrue(handler.getAmountAsLong(0) == 10, "The insert was lost although the caller committed");
        helper.succeed();
    }

    /**
     * A storage controller menu that uses a fixed test recipe instead of looking one up.
     */
    private static class RecipeOverrideContainer extends StorageControllerContainer {
        private RecipeHolder<CraftingRecipe> overrideRecipe;

        RecipeOverrideContainer(int id, Inventory playerInventory, StorageControllerBlockEntity storageController) {
            super(id, playerInventory, storageController);
        }

        @Override
        protected void findRecipeForMatrixClient() {
            this.currentRecipe = this.overrideRecipe;
        }

        @Override
        protected void findRecipeForMatrix() {
            this.currentRecipe = this.overrideRecipe;
        }

        void shiftCraft(Player player) {
            this.craftShift(player, this.getStorageController());
        }
    }

    /**
     * Dirt -> diamond, leaving a stick (not a crafting remainder of dirt) in the grid.
     */
    private static class ForeignRemainderRecipe extends ShapelessRecipe {
        ForeignRemainderRecipe() {
            super(new Recipe.CommonInfo(false), new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
                    new ItemStackTemplate(Items.DIAMOND), List.of(Ingredient.of(Items.DIRT)));
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
            NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
            for (int i = 0; i < input.size(); i++) {
                if (input.getItem(i).is(Items.DIRT))
                    remaining.set(i, new ItemStack(Items.STICK));
            }
            return remaining;
        }
    }

    private static class EmptyResultRecipe extends ShapelessRecipe {
        EmptyResultRecipe() {
            super(new Recipe.CommonInfo(false), new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
                    new ItemStackTemplate(Items.DIAMOND), List.of(Ingredient.of(Items.DIRT)));
        }

        @Override
        public ItemStack assemble(CraftingInput input) {
            return ItemStack.EMPTY;
        }
    }
}
