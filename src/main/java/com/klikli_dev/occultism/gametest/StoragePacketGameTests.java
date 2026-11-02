// SPDX-FileCopyrightText: 2026 klikli-dev
//
// SPDX-License-Identifier: MIT

package com.klikli_dev.occultism.gametest;

import com.klikli_dev.occultism.Occultism;
import com.klikli_dev.occultism.api.common.data.GlobalBlockPos;
import com.klikli_dev.occultism.api.common.data.MachineReference;
import com.klikli_dev.occultism.api.common.data.SortDirection;
import com.klikli_dev.occultism.api.common.data.SortType;
import com.klikli_dev.occultism.api.common.data.WorkAreaSize;
import com.klikli_dev.occultism.common.blockentity.StorageControllerBlockEntity;
import com.klikli_dev.occultism.common.container.storage.StorageControllerContainer;
import com.klikli_dev.occultism.common.container.storage.StorageRemoteContainer;
import com.klikli_dev.occultism.network.messages.MessageInsertMouseHeldItem;
import com.klikli_dev.occultism.network.messages.MessageOpenStorageRemote;
import com.klikli_dev.occultism.network.messages.MessageRequestOrder;
import com.klikli_dev.occultism.network.messages.MessageRequestStacks;
import com.klikli_dev.occultism.network.messages.MessageSetRecipe;
import com.klikli_dev.occultism.network.messages.MessageSetRecipeByID;
import com.klikli_dev.occultism.network.messages.MessageSetRecipeByTemplate;
import com.klikli_dev.occultism.network.messages.MessageSetWorkAreaSize;
import com.klikli_dev.occultism.network.messages.MessageSortItems;
import com.klikli_dev.occultism.network.messages.MessageTakeItem;
import com.klikli_dev.occultism.network.messages.MessageUpdateStacks;
import com.klikli_dev.occultism.registry.OccultismDataComponents;
import com.klikli_dev.occultism.registry.OccultismEntities;
import com.klikli_dev.occultism.registry.OccultismItems;
import com.klikli_dev.occultism.util.InputUtil;
import com.klikli_dev.occultism.util.ItemNBTUtil;
import com.klikli_dev.occultism.util.StorageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Regression tests for the server side handling of storage packets. Packets are handled directly on the server
 * thread for a server player whose outgoing packets are recorded.
 */
public class StoragePacketGameTests {

    private static final BlockPos CONTROLLER_POS = new BlockPos(2, 1, 2);
    private static final BlockPos PLAYER_POS = new BlockPos(1, 1, 1);

    private record Setup(StorageControllerBlockEntity controller, ServerPlayer player,
                         StorageControllerContainer container) {
    }

    private static Setup openControllerMenu(GameTestHelper helper) {
        var controller = StorageTestSupport.placeController(helper, CONTROLLER_POS);
        var player = StorageTestSupport.createPlayer(helper, PLAYER_POS);
        var container = StorageTestSupport.openController(player, controller);
        return new Setup(controller, player, container);
    }

    /**
     * Packets must only act on a storage menu that is still valid, otherwise stale menus can be used to dupe items.
     */
    public static void packetsIgnoreInvalidStorageMenu(GameTestHelper helper) {
        var setup = openControllerMenu(helper);
        var server = helper.getLevel().getServer();
        var player = setup.player();
        try {
            StorageTestSupport.store(setup.controller(), new ItemStack(Items.DIRT, 10));

            //out of range of the controller -> menu is no longer valid
            StorageTestSupport.moveTo(helper, player, PLAYER_POS.offset(30, 0, 30));
            helper.assertFalse(setup.container().stillValid(player), "Menu is still valid out of range");

            new MessageTakeItem(new ItemStack(Items.DIRT), InputUtil.MOUSE_BUTTON_LEFT, true, false).onServerReceived(server, player);
            helper.assertTrue(player.getInventory().countItem(Items.DIRT) == 0, "Took items through an invalid storage menu");
            helper.assertTrue(StorageTestSupport.stored(setup.controller(), Items.DIRT) == 10, "Storage changed through an invalid storage menu");

            setup.container().setCarried(new ItemStack(Items.STONE, 5));
            new MessageInsertMouseHeldItem(InputUtil.MOUSE_BUTTON_LEFT).onServerReceived(server, player);
            helper.assertTrue(StorageTestSupport.stored(setup.controller(), Items.STONE) == 0, "Inserted items through an invalid storage menu");
            helper.assertTrue(setup.container().getCarried().getCount() == 5, "The held item changed through an invalid storage menu");
            setup.container().setCarried(ItemStack.EMPTY);

            //back in range -> works again
            StorageTestSupport.moveTo(helper, player, PLAYER_POS);
            new MessageTakeItem(new ItemStack(Items.DIRT), InputUtil.MOUSE_BUTTON_LEFT, true, false).onServerReceived(server, player);
            helper.assertTrue(player.getInventory().countItem(Items.DIRT) == 10, "Could not take items through a valid storage menu");
        } finally {
            player.doCloseContainer();
        }
        helper.succeed();
    }

    /**
     * Taking items must never overwrite an item held on the mouse, and an unknown mouse button must not delete it.
     */
    public static void packetsNeverDeleteMouseHeldItem(GameTestHelper helper) {
        var setup = openControllerMenu(helper);
        var server = helper.getLevel().getServer();
        var player = setup.player();
        var container = setup.container();
        try {
            StorageTestSupport.store(setup.controller(), new ItemStack(Items.DIRT, 10));
            container.setCarried(new ItemStack(Items.STONE, 5));

            new MessageTakeItem(new ItemStack(Items.DIRT), InputUtil.MOUSE_BUTTON_LEFT, false, false).onServerReceived(server, player);
            helper.assertTrue(container.getCarried().is(Items.STONE) && container.getCarried().getCount() == 5,
                    "The held item was replaced by taking an item, now holding " + container.getCarried());
            helper.assertTrue(StorageTestSupport.stored(setup.controller(), Items.DIRT) == 10, "Dirt was extracted although it could not be held");

            new MessageInsertMouseHeldItem(InputUtil.MOUSE_BUTTON_MIDDLE).onServerReceived(server, player);
            helper.assertTrue(container.getCarried().is(Items.STONE) && container.getCarried().getCount() == 5,
                    "An unknown mouse button changed the held item to " + container.getCarried());

            new MessageInsertMouseHeldItem(InputUtil.MOUSE_BUTTON_LEFT).onServerReceived(server, player);
            helper.assertTrue(container.getCarried().isEmpty(), "Left click did not insert the held item");
            helper.assertTrue(StorageTestSupport.stored(setup.controller(), Items.STONE) == 5, "Held item was not stored");
        } finally {
            player.doCloseContainer();
        }
        helper.succeed();
    }

    private static List<Integer> ingredientSlots(GameTestHelper helper, String recipePath) {
        var key = ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace(recipePath));
        var recipe = helper.getLevel().getServer().getRecipeManager().byKey(key).orElseThrow().value();
        List<Optional<Ingredient>> grid = StorageUtil.ensure3by3CraftingMatrix(recipe);
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < grid.size(); slot++) {
            if (grid.get(slot).isPresent())
                slots.add(slot);
        }
        return slots;
    }

    private static void assertOccupiedSlotKept(GameTestHelper helper, Setup setup, List<Integer> slots, String packet) {
        var matrix = setup.container().getCraftMatrix();
        helper.assertTrue(matrix.getItem(slots.get(0)).is(OccultismItems.STORAGE_REMOTE.get()),
                packet + ": the occupied grid slot was changed to " + matrix.getItem(slots.get(0)));
        helper.assertTrue(matrix.getItem(slots.get(1)).is(Items.OAK_PLANKS),
                packet + ": the free grid slot was not filled, holds " + matrix.getItem(slots.get(1)));
        int planks = StorageTestSupport.stored(setup.controller(), Items.OAK_PLANKS);
        helper.assertTrue(planks == 9, packet + ": expected 9 planks left in storage but found " + planks);
    }

    /**
     * Setting a recipe by id must not extract items for grid slots that are still occupied (e.g. because clearing the
     * grid failed), as those items would be voided.
     */
    public static void setRecipeByIdSkipsOccupiedGridSlots(GameTestHelper helper) {
        var setup = openControllerMenu(helper);
        try {
            var slots = ingredientSlots(helper, "stick");
            StorageTestSupport.store(setup.controller(), new ItemStack(Items.OAK_PLANKS, 10));
            //a storage remote is blacklisted, so clearing the grid cannot move it into storage
            setup.container().getCraftMatrix().setItem(slots.get(0), new ItemStack(OccultismItems.STORAGE_REMOTE.get()));

            new MessageSetRecipeByID(Identifier.withDefaultNamespace("stick")).onServerReceived(helper.getLevel().getServer(), setup.player());

            assertOccupiedSlotKept(helper, setup, slots, "MessageSetRecipeByID");
        } finally {
            setup.player().doCloseContainer();
        }
        helper.succeed();
    }

    /**
     * Same as {@link #setRecipeByIdSkipsOccupiedGridSlots} for the item based recipe transfer message.
     */
    public static void setRecipeSkipsOccupiedGridSlots(GameTestHelper helper) {
        var setup = openControllerMenu(helper);
        try {
            var slots = ingredientSlots(helper, "stick");
            StorageTestSupport.store(setup.controller(), new ItemStack(Items.OAK_PLANKS, 10));
            setup.container().getCraftMatrix().setItem(slots.get(0), new ItemStack(OccultismItems.STORAGE_REMOTE.get()));

            var nbt = new CompoundTag();
            for (int slot : slots) {
                var list = new ListTag();
                list.add(ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, new ItemStack(Items.OAK_PLANKS)).getOrThrow());
                nbt.put("s" + slot, list);
            }
            new MessageSetRecipe(nbt).onServerReceived(helper.getLevel().getServer(), setup.player());

            assertOccupiedSlotKept(helper, setup, slots, "MessageSetRecipe");
        } finally {
            setup.player().doCloseContainer();
        }
        helper.succeed();
    }

    /**
     * Setting a shaped recipe by template must fill the grid from the recipe placement info, and malformed client
     * templates must be ignored instead of throwing.
     */
    public static void setRecipeByTemplateFillsShapedRecipe(GameTestHelper helper) {
        var setup = openControllerMenu(helper);
        var server = helper.getLevel().getServer();
        try {
            var matrix = setup.container().getCraftMatrix();
            StorageTestSupport.store(setup.controller(), new ItemStack(Items.OAK_PLANKS, 10));

            new MessageSetRecipeByTemplate(null, NonNullList.withSize(3, ItemStack.EMPTY), 1).onServerReceived(server, setup.player());
            for (int slot = 0; slot < 9; slot++) {
                helper.assertTrue(matrix.getItem(slot).isEmpty(), "A malformed template filled grid slot " + slot);
            }

            new MessageSetRecipeByTemplate(Identifier.withDefaultNamespace("stick"), NonNullList.withSize(9, ItemStack.EMPTY), 1)
                    .onServerReceived(server, setup.player());

            var slots = ingredientSlots(helper, "stick");
            for (int slot = 0; slot < 9; slot++) {
                boolean expectPlanks = slots.contains(slot);
                helper.assertTrue(matrix.getItem(slot).is(Items.OAK_PLANKS) == expectPlanks,
                        "Grid slot " + slot + " holds " + matrix.getItem(slot));
            }
            helper.assertTrue(StorageTestSupport.stored(setup.controller(), Items.OAK_PLANKS) == 8,
                    "Expected 8 planks left in storage");
        } finally {
            setup.player().doCloseContainer();
        }
        helper.succeed();
    }

    /**
     * Orders must only be accepted from a valid storage menu for the requested controller and only for available items.
     */
    public static void requestOrderIsValidatedAgainstOpenMenu(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var controller = StorageTestSupport.placeController(helper, CONTROLLER_POS);
        var player = StorageTestSupport.createPlayer(helper, PLAYER_POS);
        var packets = StorageTestSupport.packets(player);
        var chestPos = new BlockPos(0, 1, 0);
        helper.setBlock(chestPos, Blocks.CHEST);
        var chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
        var machine = MachineReference.from(chest, chest);
        controller.linkMachine(machine);
        StorageTestSupport.store(controller, new ItemStack(Items.DIRT, 10));
        var controllerPos = GlobalBlockPos.from(controller);
        var machinePos = GlobalBlockPos.from(chest);

        //no storage menu open
        new MessageRequestOrder(controllerPos, machinePos, new ItemStack(Items.DIRT, 5)).onServerReceived(server, player);
        helper.assertTrue(packets.systemMessages().isEmpty(), "An order was accepted without an open storage menu");
        helper.assertTrue(controller.getLinkedMachines().containsKey(machine.insertGlobalPos),
                "An order without an open storage menu was processed");

        StorageTestSupport.openController(player, controller);
        try {
            //position of a different controller
            var otherPos = new GlobalBlockPos(helper.absolutePos(new BlockPos(4, 1, 4)), helper.getLevel());
            new MessageRequestOrder(otherPos, machinePos, new ItemStack(Items.DIRT, 5)).onServerReceived(server, player);
            helper.assertTrue(packets.systemMessages().isEmpty(), "An order for a controller other than the open one was accepted");

            //item not available
            new MessageRequestOrder(controllerPos, machinePos, new ItemStack(Items.DIAMOND, 5)).onServerReceived(server, player);
            helper.assertTrue(packets.systemMessages().isEmpty(), "An order for an unavailable item was accepted");
            helper.assertTrue(controller.getLinkedMachines().containsKey(machine.insertGlobalPos),
                    "An order for an unavailable item was processed");

            //valid order
            new MessageRequestOrder(controllerPos, machinePos, new ItemStack(Items.DIRT, 5)).onServerReceived(server, player);
            helper.assertTrue(packets.systemMessages().size() == 1, "A valid order was not accepted");
        } finally {
            player.doCloseContainer();
        }
        helper.succeed();
    }

    /**
     * Sorting must apply to the storage accessor of the open menu, not to a client supplied position.
     */
    public static void sortItemsAppliesToOpenMenu(GameTestHelper helper) {
        var setup = openControllerMenu(helper);
        var otherController = StorageTestSupport.placeController(helper, new BlockPos(0, 1, 0));
        try {
            new MessageSortItems(otherController.getBlockPos(), SortDirection.UP, SortType.NAME)
                    .onServerReceived(helper.getLevel().getServer(), setup.player());

            helper.assertTrue(setup.controller().getSortDirection() == SortDirection.UP && setup.controller().getSortType() == SortType.NAME,
                    "Sorting was not applied to the open storage controller");
            helper.assertTrue(otherController.getSortDirection() == SortDirection.DOWN && otherController.getSortType() == SortType.AMOUNT,
                    "Sorting was applied to a storage controller at a client supplied position");
        } finally {
            setup.player().doCloseContainer();
        }
        helper.succeed();
    }

    /**
     * The first stack request of a freshly opened storage menu must not be rate limited.
     */
    public static void firstStackRequestIsNotRateLimited(GameTestHelper helper) {
        var setup = openControllerMenu(helper);
        var packets = StorageTestSupport.packets(setup.player());
        try {
            packets.clear();
            new MessageRequestStacks().onServerReceived(helper.getLevel().getServer(), setup.player());
            helper.assertTrue(packets.payloads(MessageUpdateStacks.class).size() == 1,
                    "The first stack request was not answered with the storage stacks");
        } finally {
            setup.player().doCloseContainer();
        }
        helper.succeed();
    }

    private static ItemStack remoteLinkedTo(GlobalBlockPos pos) {
        var remote = new ItemStack(OccultismItems.STORAGE_REMOTE.get());
        remote.set(OccultismDataComponents.LINKED_STORAGE_CONTROLLER, pos);
        return remote;
    }

    /**
     * Opening a storage remote linked to a missing dimension or an unloaded position must neither crash nor load chunks.
     */
    public static void storageRemoteOpeningIsGuarded(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var controller = StorageTestSupport.placeController(helper, CONTROLLER_POS);
        var player = StorageTestSupport.createPlayer(helper, PLAYER_POS);
        var packets = StorageTestSupport.packets(player);
        player.getInventory().setSelectedSlot(0);

        //missing dimension
        var missingDimension = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(Occultism.MODID, "missing_dimension"));
        player.getInventory().setItem(0, remoteLinkedTo(new GlobalBlockPos(controller.getBlockPos(), missingDimension)));
        new MessageOpenStorageRemote().onServerReceived(server, player);
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "A menu was opened for a remote linked to a missing dimension");

        player.getMainHandItem().getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "A menu was opened for a remote linked to a missing dimension");
        helper.assertTrue(packets.systemMessages().size() == 1, "The player was not told that the controller is not loaded");

        //unloaded position
        var unloadedPos = controller.getBlockPos().offset(16 * 4000, 0, 16 * 4000);
        helper.assertFalse(helper.getLevel().hasChunkAt(unloadedPos), "Test precondition: the far position is loaded");
        player.getInventory().setItem(0, remoteLinkedTo(new GlobalBlockPos(unloadedPos, helper.getLevel())));
        new MessageOpenStorageRemote().onServerReceived(server, player);
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "A menu was opened for a remote linked to an unloaded position");
        helper.assertFalse(helper.getLevel().hasChunkAt(unloadedPos), "Opening the storage remote loaded the linked chunk");

        //valid link
        player.getInventory().setItem(0, remoteLinkedTo(GlobalBlockPos.from(controller)));
        new MessageOpenStorageRemote().onServerReceived(server, player);
        helper.assertTrue(player.containerMenu instanceof StorageRemoteContainer, "The storage remote menu was not opened for a valid link");
        player.doCloseContainer();
        helper.succeed();
    }

    /**
     * Out of range work area sizes sent by the client must be ignored.
     */
    public static void workAreaSizePacketIgnoresInvalidSizes(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var player = StorageTestSupport.createPlayer(helper, PLAYER_POS);
        var spirit = helper.spawn(OccultismEntities.FOLIOT.get(), new BlockPos(2, 1, 2));
        spirit.setWorkAreaSize(WorkAreaSize.SMALL);
        var book = new ItemStack(OccultismItems.BOOK_OF_CALLING_FOLIOT_LUMBERJACK.get());
        ItemNBTUtil.setSpiritEntityUUID(book, spirit.getUUID());
        player.setItemInHand(InteractionHand.MAIN_HAND, book);

        var invalid = new MessageSetWorkAreaSize(WorkAreaSize.LARGE, InteractionHand.MAIN_HAND);
        invalid.workAreaSize = (byte) WorkAreaSize.values().length;
        invalid.onServerReceived(server, player);
        invalid.workAreaSize = -1;
        invalid.onServerReceived(server, player);
        helper.assertTrue(spirit.getWorkAreaSize() == WorkAreaSize.SMALL, "An invalid work area size changed the spirit");

        new MessageSetWorkAreaSize(WorkAreaSize.LARGE, InteractionHand.MAIN_HAND).onServerReceived(server, player);
        helper.assertTrue(spirit.getWorkAreaSize() == WorkAreaSize.LARGE, "A valid work area size was not applied");
        spirit.discard();
        helper.succeed();
    }
}
