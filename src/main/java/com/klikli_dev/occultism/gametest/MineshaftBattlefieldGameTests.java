// SPDX-FileCopyrightText: 2026 klikli-dev
//
// SPDX-License-Identifier: MIT

package com.klikli_dev.occultism.gametest;

import com.klikli_dev.occultism.Occultism;
import com.klikli_dev.occultism.common.blockentity.DimensionalBattlefieldBlockEntity;
import com.klikli_dev.occultism.common.blockentity.DimensionalMineshaftBlockEntity;
import com.klikli_dev.occultism.registry.OccultismBlocks;
import com.klikli_dev.occultism.registry.OccultismDataComponents;
import com.klikli_dev.occultism.registry.OccultismItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Regression tests for the dimensional mineshaft and the dimensional battlefield.
 * <p>
 * Machine cycles take hundreds of ticks with the default config, so the cycle tests let the machine start on its own
 * and then fast-forward the public progress fields to the last tick of the cycle.
 */
public class MineshaftBattlefieldGameTests {

    private static final BlockPos MACHINE_POS = new BlockPos(2, 2, 2);

    // region Helpers

    private static DimensionalMineshaftBlockEntity placeMineshaft(GameTestHelper helper) {
        helper.setBlock(MACHINE_POS, OccultismBlocks.DIMENSIONAL_MINESHAFT.get());
        return helper.getBlockEntity(MACHINE_POS, DimensionalMineshaftBlockEntity.class);
    }

    private static DimensionalBattlefieldBlockEntity placeBattlefield(GameTestHelper helper) {
        helper.setBlock(MACHINE_POS, OccultismBlocks.DIMENSIONAL_BATTLEFIELD.get());
        return helper.getBlockEntity(MACHINE_POS, DimensionalBattlefieldBlockEntity.class);
    }

    /**
     * Places a mineshaft with minerOutputBeforeBreak enabled. The block entity reads the config once on construction,
     * so the config is only changed while the block entity is created and restored right after.
     */
    private static DimensionalMineshaftBlockEntity placeMineshaftWithSaveMiner(GameTestHelper helper) {
        var config = Occultism.SERVER_CONFIG.itemSettings.minerOutputBeforeBreak;
        boolean previous = config.get();
        config.set(true);
        try {
            return placeMineshaft(helper);
        } finally {
            config.set(previous);
        }
    }

    /**
     * The item handler automation (e.g. hoppers) sees on the given side.
     */
    private static ResourceHandler<ItemResource> getHandler(GameTestHelper helper, Direction side) {
        var handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(MACHINE_POS), side);
        helper.assertTrue(handler != null, "Machine does not expose an item handler on side " + side);
        return handler;
    }

    private static int insert(ResourceHandler<ItemResource> handler, ItemStack stack) {
        try (var tx = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(stack), stack.getCount(), tx);
            tx.commit();
            return inserted;
        }
    }

    private static int extract(ResourceHandler<ItemResource> handler, ItemStack stack) {
        try (var tx = Transaction.openRoot()) {
            int extracted = handler.extract(ItemResource.of(stack), stack.getCount(), tx);
            tx.commit();
            return extracted;
        }
    }

    private static ItemStack getStack(ResourceHandler<ItemResource> handler, int slot) {
        return handler.getResource(slot).toStack(handler.getAmountAsInt(slot));
    }

    private static boolean isEmpty(ResourceHandler<ItemResource> handler) {
        for (int i = 0; i < handler.size(); i++) {
            if (!handler.getResource(i).isEmpty())
                return false;
        }
        return true;
    }

    private static int countInHandler(ResourceHandler<ItemResource> handler, Item item) {
        int count = 0;
        for (int i = 0; i < handler.size(); i++) {
            if (handler.getResource(i).is(item))
                count += handler.getAmountAsInt(i);
        }
        return count;
    }

    private static int countDroppedItems(GameTestHelper helper, Item item) {
        int count = 0;
        for (ItemEntity entity : helper.getEntities(EntityType.ITEM, MACHINE_POS, 3)) {
            if (entity.getItem().is(item))
                count += entity.getItem().getCount();
        }
        return count;
    }

    private static Player makePlayerNextToMachine(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var pos = Vec3.atCenterOf(helper.absolutePos(MACHINE_POS.north()));
        player.setPos(pos.x, pos.y, pos.z);
        return player;
    }

    /**
     * A soul gem containing a cow, captured the same way the soul gem item does it.
     */
    private static ItemStack makeCowSoul(GameTestHelper helper) {
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, MACHINE_POS.east());
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, cow.registryAccess());
        cow.saveWithoutId(output);
        cow.discard();
        var soul = new ItemStack(OccultismItems.SOUL_GEM_ITEM.get());
        soul.set(DataComponents.ENTITY_DATA, TypedEntityData.of(EntityType.COW, output.buildResult()));
        return soul;
    }

    private static ItemStack makeAlmostBrokenMiner(int remainingUses) {
        var miner = new ItemStack(OccultismItems.MINER_FOLIOT_UNSPECIALIZED.get());
        miner.setDamageValue(miner.getMaxDamage() - remainingUses);
        return miner;
    }

    // endregion Helpers

    // region Mineshaft

    /**
     * A miner inserted through the item handler (as a hopper would) starts the mining cycle, and a completed cycle
     * produces output and damages the miner.
     */
    public static void mineshaftMinesWithMinerInsertedByAutomation(GameTestHelper helper) {
        var mineshaft = placeMineshaft(helper);
        var miner = new ItemStack(OccultismItems.MINER_FOLIOT_UNSPECIALIZED.get());
        helper.assertValueEqual(insert(getHandler(helper, Direction.UP), miner), 1, "inserted miners");

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(mineshaft.miningTime > 0, "Mining did not start after inserting a miner through the item handler");
            helper.assertTrue(mineshaft.maxMiningTime > 0, "Max mining time was not initialized from the miner");
            //skip the remaining mining time of the cycle
            mineshaft.miningTime = 1;
        });
        helper.succeedWhen(() -> {
            helper.assertFalse(isEmpty(mineshaft.outputHandler), "Mining cycle produced no output");
            helper.assertValueEqual(getStack(mineshaft.inputHandler, 0).getDamageValue(), 1, "miner damage after one operation");
        });
    }

    /**
     * Fix 9194331db: with minerOutputBeforeBreak enabled a miner that is about to break must stay in the input if the
     * output is full, instead of being deleted.
     */
    public static void mineshaftKeepsMinerWhenOutputIsFull(GameTestHelper helper) {
        var mineshaft = placeMineshaftWithSaveMiner(helper);
        for (int i = 0; i < mineshaft.outputHandler.size(); i++)
            mineshaft.outputHandler.set(i, ItemResource.of(Items.STONE), 64);
        mineshaft.inputHandler.set(0, ItemResource.of(makeAlmostBrokenMiner(1)), 1);

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(mineshaft.miningTime > 0, "Mining did not start");
            mineshaft.miningTime = 1;
        });
        helper.runAfterDelay(6, () -> {
            var input = getStack(mineshaft.inputHandler, 0);
            helper.assertTrue(input.is(OccultismItems.MINER_FOLIOT_UNSPECIALIZED.get()),
                    "Miner was removed from the input although it did not fit into the full output, got " + input);
            helper.assertValueEqual(countInHandler(mineshaft.outputHandler, Items.STONE), 9 * 64, "stone in output");
            helper.succeed();
        });
    }

    /**
     * Fix 9194331db: with minerOutputBeforeBreak enabled and free output space, the miner is moved to the output once
     * the next operation would break it.
     */
    public static void mineshaftMovesMinerToOutputBeforeBreaking(GameTestHelper helper) {
        var mineshaft = placeMineshaftWithSaveMiner(helper);
        mineshaft.inputHandler.set(0, ItemResource.of(makeAlmostBrokenMiner(2)), 1);

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(mineshaft.miningTime > 0, "Mining did not start");
            mineshaft.miningTime = 1;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(mineshaft.inputHandler.getResource(0).isEmpty(), "Miner is still in the input");
            helper.assertValueEqual(countInHandler(mineshaft.outputHandler, OccultismItems.MINER_FOLIOT_UNSPECIALIZED.get()), 1, "miners in output");
        });
    }

    /**
     * Fix cc295df08: breaking the mineshaft drops its inventory exactly once and clears it, so nothing can be taken
     * out of the removed block entity afterwards.
     */
    public static void mineshaftBreakDropsInventoryOnceAndClearsIt(GameTestHelper helper) {
        var mineshaft = placeMineshaft(helper);
        mineshaft.inputHandler.set(0, ItemResource.of(OccultismItems.MINER_FOLIOT_UNSPECIALIZED.get()), 1);
        mineshaft.outputHandler.set(0, ItemResource.of(Items.IRON_ORE), 5);
        mineshaft.outputHandler.set(8, ItemResource.of(Items.GOLD_ORE), 3);

        helper.destroyBlock(MACHINE_POS);

        helper.assertValueEqual(countDroppedItems(helper, OccultismItems.MINER_FOLIOT_UNSPECIALIZED.get()), 1, "dropped miners");
        helper.assertValueEqual(countDroppedItems(helper, Items.IRON_ORE), 5, "dropped iron ore");
        helper.assertValueEqual(countDroppedItems(helper, Items.GOLD_ORE), 3, "dropped gold ore");
        helper.assertTrue(isEmpty(mineshaft.inputHandler), "Input of the removed mineshaft was not cleared");
        helper.assertTrue(isEmpty(mineshaft.outputHandler), "Output of the removed mineshaft was not cleared");
        helper.succeed();
    }

    /**
     * Fix b5fc46192: an open mineshaft menu becomes invalid (and is closed by the server) once the block is removed.
     */
    public static void mineshaftMenuClosesWhenBlockRemoved(GameTestHelper helper) {
        var mineshaft = placeMineshaft(helper);
        var player = makePlayerNextToMachine(helper);
        AbstractContainerMenu menu = mineshaft.createMenu(1, player.getInventory(), player);

        helper.assertTrue(menu.stillValid(player), "Mineshaft menu is not valid while the block exists");
        helper.destroyBlock(MACHINE_POS);
        helper.assertFalse(menu.stillValid(player), "Mineshaft menu is still valid after the block was removed");
        helper.succeed();
    }

    /**
     * Fix b5fc46192: shift-clicking a player stack must only target the miner input slot, never merge into a
     * matching stack in the output slots.
     */
    public static void mineshaftShiftClickDoesNotMergeIntoOutput(GameTestHelper helper) {
        var mineshaft = placeMineshaft(helper);
        mineshaft.outputHandler.set(0, ItemResource.of(Items.COBBLESTONE), 10);
        var player = makePlayerNextToMachine(helper);
        player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        AbstractContainerMenu menu = mineshaft.createMenu(1, player.getInventory(), player);

        //9 output slots, 1 input slot, 27 main inventory slots, then the hotbar
        int hotbarSlot0 = mineshaft.outputHandler.size() + 1 + 27;
        helper.assertTrue(menu.getSlot(hotbarSlot0).getItem().is(Items.COBBLESTONE), "Unexpected menu slot layout");
        menu.quickMoveStack(player, hotbarSlot0);

        helper.assertValueEqual(mineshaft.outputHandler.getAmountAsInt(0), 10, "cobblestone in output slot");
        helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 5, "cobblestone in player hotbar");
        helper.succeed();
    }

    /**
     * Fix aac6bf860: the block entity update tag only contains the network data, not the inventories.
     */
    public static void mineshaftUpdateTagOnlyContainsNetworkData(GameTestHelper helper) {
        var mineshaft = placeMineshaft(helper);
        mineshaft.inputHandler.set(0, ItemResource.of(OccultismItems.MINER_FOLIOT_UNSPECIALIZED.get()), 1);
        mineshaft.outputHandler.set(0, ItemResource.of(Items.IRON_ORE), 5);

        var tag = mineshaft.getUpdateTag(helper.getLevel().registryAccess());
        helper.assertTrue(tag.contains("miningTime"), "Update tag is missing the network data: " + tag);
        helper.assertFalse(tag.contains("inputHandler"), "Update tag contains the input inventory: " + tag);
        helper.assertFalse(tag.contains("outputHandler"), "Update tag contains the output inventory: " + tag);
        helper.succeed();
    }

    /**
     * Fix 966e105bb: querying the max stack size of a miner spirit must not write the miner stat components.
     */
    public static void minerSpiritMaxStackSizeDoesNotMutateComponents(GameTestHelper helper) {
        var miner = new ItemStack(OccultismItems.MINER_FOLIOT_UNSPECIALIZED.get());
        miner.getMaxStackSize();
        helper.assertFalse(miner.has(OccultismDataComponents.MAX_MINING_TIME), "getMaxStackSize wrote MAX_MINING_TIME");
        helper.assertFalse(miner.has(OccultismDataComponents.ROLLS_PER_OPERATION), "getMaxStackSize wrote ROLLS_PER_OPERATION");
        helper.assertFalse(miner.has(OccultismDataComponents.MINER_OUTPUT_MULTIPLIER), "getMaxStackSize wrote MINER_OUTPUT_MULTIPLIER");
        helper.succeed();
    }

    // endregion Mineshaft

    // region Battlefield

    /**
     * Fix 7081787fd: changing the weapon or soul through the item handler (as automation does) restarts the fight,
     * while the weapon only taking damage does not. Slot validity checks no longer have side effects (b5fc46192).
     */
    public static void battlefieldInputChangesThroughHandlerRestartFight(GameTestHelper helper) {
        var battlefield = placeBattlefield(helper);
        var input = getHandler(helper, Direction.UP);
        battlefield.maxMobLife = 50;

        battlefield.mobHealth = 7;
        helper.assertValueEqual(insert(input, new ItemStack(Items.IRON_SWORD)), 1, "inserted swords");
        helper.assertTrue(battlefield.inputWeaponHandler.getResource(0).is(Items.IRON_SWORD), "Sword was not inserted into the weapon slot");
        helper.assertValueEqual(battlefield.mobHealth, 50, "mob health after inserting a weapon");

        //weapon damage during the fight must not restart it
        battlefield.mobHealth = 7;
        var damaged = new ItemStack(Items.IRON_SWORD);
        damaged.setDamageValue(10);
        battlefield.inputWeaponHandler.set(0, ItemResource.of(damaged), 1);
        helper.assertValueEqual(battlefield.mobHealth, 7, "mob health after the weapon took damage");

        battlefield.mobHealth = 7;
        helper.assertValueEqual(extract(input, damaged), 1, "extracted swords");
        helper.assertValueEqual(battlefield.mobHealth, 50, "mob health after removing the weapon");

        battlefield.mobHealth = 7;
        var soul = makeCowSoul(helper);
        helper.assertValueEqual(insert(input, soul), 1, "inserted souls");
        helper.assertTrue(!battlefield.inputSoulHandler.getResource(0).isEmpty(), "Soul was not inserted into the soul slot");
        helper.assertValueEqual(battlefield.mobHealth, 50, "mob health after inserting a soul");

        //checking whether an item may be placed must not restart the fight
        battlefield.mobHealth = 7;
        var player = makePlayerNextToMachine(helper);
        AbstractContainerMenu menu = battlefield.createMenu(1, player.getInventory(), player);
        int outputSize = battlefield.outputHandler.size();
        menu.getSlot(outputSize).mayPlace(soul);
        menu.getSlot(outputSize + 2).mayPlace(new ItemStack(Items.DIAMOND_SWORD));
        helper.assertValueEqual(battlefield.mobHealth, 7, "mob health after mayPlace checks");
        helper.succeed();
    }

    /**
     * A full fight driven by the block entity ticker consumes fuel and collects the loot into the output
     * (939db4e89 collects drops through a single static listener).
     */
    public static void battlefieldFightProducesLoot(GameTestHelper helper) {
        var battlefield = placeBattlefield(helper);
        var input = getHandler(helper, Direction.UP);
        helper.assertValueEqual(insert(input, makeCowSoul(helper)), 1, "inserted souls");
        helper.assertValueEqual(insert(input, new ItemStack(Items.IRON_SWORD)), 1, "inserted swords");
        helper.assertValueEqual(insert(input, new ItemStack(OccultismItems.DEMONS_DREAM_ESSENCE.get(), 4)), 4, "inserted fuel");

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(battlefield.maxMobLife > 0, "Fight did not start");
            helper.assertTrue(battlefield.mobHealth > 0, "Fight is not running");
            //skip to the last hit of the fight
            battlefield.mobHealth = 1;
            battlefield.hitTimer = 1;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(countInHandler(battlefield.outputHandler, Items.BEEF) > 0, "No beef was collected into the output");
            helper.assertValueEqual(battlefield.inputFuelHandler.getAmountAsInt(0), 3, "fuel after one defeat");
        });
    }

    /**
     * Fix 939db4e89: a defeat collects the drops into the output and the stored experience is persisted. Item
     * entities spawned next to the battlefield outside a defeat are not absorbed.
     */
    public static void battlefieldDefeatCollectsDropsAndPersistsExperience(GameTestHelper helper) {
        var battlefield = placeBattlefield(helper);
        var soul = makeCowSoul(helper);
        battlefield.inputSoulHandler.set(0, ItemResource.of(soul), 1);
        battlefield.inputWeaponHandler.set(0, ItemResource.of(Items.IRON_SWORD), 1);

        battlefield.setStoredLivingEntity(soul, helper.getLevel());
        battlefield.defeat(soul, 1);

        helper.assertTrue(countInHandler(battlefield.outputHandler, Items.BEEF) > 0, "Defeat did not collect beef into the output");
        var saved = battlefield.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.assertTrue(saved.getIntOr("xpStored", 0) > 0, "Stored experience was not saved: " + saved);

        int beefBefore = countInHandler(battlefield.outputHandler, Items.BEEF);
        var item = helper.spawnItem(Items.BEEF, Vec3.atCenterOf(MACHINE_POS));
        helper.assertTrue(item.isAlive() && !item.getItem().isEmpty(), "Item spawned outside a defeat was absorbed");
        helper.assertValueEqual(countInHandler(battlefield.outputHandler, Items.BEEF), beefBefore, "beef in output after spawning an item");
        helper.succeed();
    }

    /**
     * Fix cc295df08: breaking the battlefield drops all 25 output slots (not only the 8 slots exposed for Jade) and
     * the inputs exactly once, and clears the inventories.
     */
    public static void battlefieldBreakDropsAllSlotsOnceAndClearsThem(GameTestHelper helper) {
        var battlefield = placeBattlefield(helper);
        battlefield.inputWeaponHandler.set(0, ItemResource.of(Items.IRON_SWORD), 1);
        battlefield.inputFuelHandler.set(0, ItemResource.of(OccultismItems.DEMONS_DREAM_ESSENCE.get()), 2);
        battlefield.outputHandler.set(0, ItemResource.of(Items.EMERALD), 2);
        battlefield.outputHandler.set(20, ItemResource.of(Items.DIAMOND), 3);
        battlefield.outputHandler.set(24, ItemResource.of(Items.BEEF), 7);

        helper.destroyBlock(MACHINE_POS);

        helper.assertValueEqual(countDroppedItems(helper, Items.IRON_SWORD), 1, "dropped swords");
        helper.assertValueEqual(countDroppedItems(helper, OccultismItems.DEMONS_DREAM_ESSENCE.get()), 2, "dropped fuel");
        helper.assertValueEqual(countDroppedItems(helper, Items.EMERALD), 2, "dropped emeralds");
        helper.assertValueEqual(countDroppedItems(helper, Items.DIAMOND), 3, "dropped diamonds");
        helper.assertValueEqual(countDroppedItems(helper, Items.BEEF), 7, "dropped beef");
        helper.assertTrue(isEmpty(battlefield.inputWeaponHandler), "Weapon of the removed battlefield was not cleared");
        helper.assertTrue(isEmpty(battlefield.inputFuelHandler), "Fuel of the removed battlefield was not cleared");
        helper.assertTrue(isEmpty(battlefield.outputHandler), "Output of the removed battlefield was not cleared");
        helper.succeed();
    }

    /**
     * Fix b5fc46192: an open battlefield menu becomes invalid (and is closed by the server) once the block is removed.
     */
    public static void battlefieldMenuClosesWhenBlockRemoved(GameTestHelper helper) {
        var battlefield = placeBattlefield(helper);
        var player = makePlayerNextToMachine(helper);
        AbstractContainerMenu menu = battlefield.createMenu(1, player.getInventory(), player);

        helper.assertTrue(menu.stillValid(player), "Battlefield menu is not valid while the block exists");
        helper.destroyBlock(MACHINE_POS);
        helper.assertFalse(menu.stillValid(player), "Battlefield menu is still valid after the block was removed");
        helper.succeed();
    }

    /**
     * Fix b5fc46192: shift-clicking an output stack only moves it into the player inventory, never into the fuel or
     * weapon slots, even if the player inventory is full.
     */
    public static void battlefieldShiftClickOutputDoesNotFillInputSlots(GameTestHelper helper) {
        var battlefield = placeBattlefield(helper);
        battlefield.outputHandler.set(0, ItemResource.of(Items.IRON_SWORD), 1);
        var player = makePlayerNextToMachine(helper);
        for (int i = 0; i < 36; i++)
            player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        AbstractContainerMenu menu = battlefield.createMenu(1, player.getInventory(), player);

        menu.quickMoveStack(player, 0);

        helper.assertTrue(battlefield.inputWeaponHandler.getResource(0).isEmpty(), "Output was shift-clicked into the weapon slot");
        helper.assertTrue(battlefield.inputFuelHandler.getResource(0).isEmpty(), "Output was shift-clicked into the fuel slot");
        helper.assertTrue(battlefield.outputHandler.getResource(0).is(Items.IRON_SWORD), "Output stack was lost");
        helper.succeed();
    }

    /**
     * Fix aac6bf860: the block entity update tag only contains the network data, not the inventories.
     */
    public static void battlefieldUpdateTagOnlyContainsNetworkData(GameTestHelper helper) {
        var battlefield = placeBattlefield(helper);
        battlefield.inputWeaponHandler.set(0, ItemResource.of(Items.IRON_SWORD), 1);
        battlefield.outputHandler.set(0, ItemResource.of(Items.BEEF), 5);

        var tag = battlefield.getUpdateTag(helper.getLevel().registryAccess());
        helper.assertTrue(tag.contains("mobHealth"), "Update tag is missing the network data: " + tag);
        helper.assertFalse(tag.contains("inputWeaponHandler"), "Update tag contains the weapon inventory: " + tag);
        helper.assertFalse(tag.contains("outputHandler"), "Update tag contains the output inventory: " + tag);
        helper.succeed();
    }

    // endregion Battlefield
}
