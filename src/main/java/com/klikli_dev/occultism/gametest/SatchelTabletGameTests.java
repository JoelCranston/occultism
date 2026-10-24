// SPDX-FileCopyrightText: 2026 klikli-dev
//
// SPDX-License-Identifier: MIT

package com.klikli_dev.occultism.gametest;

import com.klikli_dev.occultism.common.container.satchel.SatchelInventory;
import com.klikli_dev.occultism.common.container.satchel.StorageSatchelContainer;
import com.klikli_dev.occultism.common.container.tablet.TabletInventory;
import com.klikli_dev.occultism.common.container.tablet.TeleportTabletContainer;
import com.klikli_dev.occultism.network.messages.MessageRotateTabletContents;
import com.klikli_dev.occultism.registry.OccultismItems;
import com.klikli_dev.occultism.util.TeleportUtil;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.neoforge.common.util.FakePlayer;

import javax.annotation.Nullable;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.Optional;
import java.util.function.Consumer;

public class SatchelTabletGameTests {

    // Menu slot layout: container slots first, then 27 player inventory slots, then the 9 hotbar slots.
    private static final int SATCHEL_HOTBAR_START = StorageSatchelContainer.SATCHEL_SIZE + 27;
    private static final int TABLET_HOTBAR_START = TeleportTabletContainer.TABLET_SIZE + 27;

    // region menu write back

    /**
     * Regression for "only write satchel and tablet contents back while the menu is valid":
     * contents are written into the satchel while it is still in its source slot, but not after it was moved away.
     */
    public static void satchelWritesBackOnlyWhileValid(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var satchel = new ItemStack(OccultismItems.SATCHEL.get());
        player.getInventory().setSelectedSlot(0);
        player.getInventory().setItem(0, satchel);

        var inventory = new SatchelInventory(satchel, StorageSatchelContainer.SATCHEL_SIZE);
        var menu = new StorageSatchelContainer(1, player.getInventory(), inventory, 0);

        inventory.setItem(0, new ItemStack(Items.DIAMOND, 5));
        menu.broadcastChanges();
        helper.assertTrue(menu.stillValid(player), "Satchel menu should be valid while the satchel is in its source slot");
        helper.assertValueEqual(count(satchel, Items.DIAMOND), 5, "diamonds written into the satchel while valid");

        //move the very same satchel stack out of its source slot
        player.getInventory().setItem(0, ItemStack.EMPTY);
        player.getInventory().setItem(5, satchel);
        helper.assertFalse(menu.stillValid(player), "Satchel menu must be invalid once the satchel left its source slot");

        inventory.setItem(1, new ItemStack(Items.EMERALD, 3));
        menu.broadcastChanges();
        helper.assertValueEqual(count(satchel, Items.EMERALD), 0, "emeralds written into a moved satchel");
        helper.assertValueEqual(count(satchel, Items.DIAMOND), 5, "diamonds kept in the moved satchel");
        helper.succeed();
    }

    public static void tabletWritesBackOnlyWhileValid(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var tablet = new ItemStack(OccultismItems.WORMHOLE_TABLET.get());
        player.getInventory().setSelectedSlot(0);
        player.getInventory().setItem(0, tablet);

        var inventory = new TabletInventory(tablet, TeleportTabletContainer.TABLET_SIZE);
        var menu = new TeleportTabletContainer(1, player.getInventory(), inventory, 0);

        inventory.setItem(0, new ItemStack(Items.COMPASS));
        menu.broadcastChanges();
        helper.assertTrue(menu.stillValid(player), "Tablet menu should be valid while the tablet is in its source slot");
        helper.assertValueEqual(count(tablet, Items.COMPASS), 1, "compasses written into the tablet while valid");

        player.getInventory().setItem(0, ItemStack.EMPTY);
        player.getInventory().setItem(5, tablet);
        helper.assertFalse(menu.stillValid(player), "Tablet menu must be invalid once the tablet left its source slot");

        inventory.setItem(1, new ItemStack(Items.RECOVERY_COMPASS));
        menu.broadcastChanges();
        helper.assertValueEqual(count(tablet, Items.RECOVERY_COMPASS), 0, "recovery compasses written into a moved tablet");
        helper.assertValueEqual(count(tablet, Items.COMPASS), 1, "compasses kept in the moved tablet");
        helper.succeed();
    }

    // endregion menu write back

    // region source slot validation

    /**
     * Regression for "lock and validate the source slot of open satchels and tablets":
     * replacing the source stack with a copy of the same item must invalidate the menu (stack identity, not item type).
     */
    public static void satchelReplacedByCopyInvalidatesMenu(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var satchel = new ItemStack(OccultismItems.SATCHEL.get());
        player.getInventory().setItem(0, satchel);

        var inventory = new SatchelInventory(satchel, StorageSatchelContainer.SATCHEL_SIZE);
        var menu = new StorageSatchelContainer(1, player.getInventory(), inventory, 0);
        inventory.setItem(0, new ItemStack(Items.DIAMOND, 5));
        menu.broadcastChanges();

        var copy = satchel.copy();
        player.getInventory().setItem(0, copy);
        helper.assertFalse(menu.stillValid(player), "Satchel menu must be invalid when its stack was replaced by a copy");

        inventory.setItem(0, ItemStack.EMPTY);
        menu.broadcastChanges();
        helper.assertValueEqual(count(copy, Items.DIAMOND), 5, "diamonds in the replacing copy");
        helper.assertValueEqual(count(satchel, Items.DIAMOND), 5, "diamonds in the replaced original");
        helper.succeed();
    }

    public static void tabletReplacedByCopyInvalidatesMenu(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var tablet = new ItemStack(OccultismItems.WORMHOLE_TABLET.get());
        player.getInventory().setItem(0, tablet);

        var inventory = new TabletInventory(tablet, TeleportTabletContainer.TABLET_SIZE);
        var menu = new TeleportTabletContainer(1, player.getInventory(), inventory, 0);

        player.getInventory().setItem(0, tablet.copy());
        helper.assertFalse(menu.stillValid(player), "Tablet menu must be invalid when its stack was replaced by a copy");
        helper.succeed();
    }

    /**
     * The source slot of an open satchel cannot be picked up, placed into, shift-clicked or swapped via hotbar key.
     */
    public static void satchelSourceSlotIsLocked(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var satchel = new ItemStack(OccultismItems.SATCHEL.get());
        player.getInventory().setItem(0, satchel);
        player.getInventory().setItem(1, new ItemStack(Items.DIRT, 10));

        var inventory = new SatchelInventory(satchel, StorageSatchelContainer.SATCHEL_SIZE);
        var menu = new StorageSatchelContainer(1, player.getInventory(), inventory, 0);
        assertSourceSlotLocked(helper, menu, player, SATCHEL_HOTBAR_START, satchel);
        helper.succeed();
    }

    public static void tabletSourceSlotIsLocked(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var tablet = new ItemStack(OccultismItems.WORMHOLE_TABLET.get());
        player.getInventory().setItem(0, tablet);
        player.getInventory().setItem(1, new ItemStack(Items.DIRT, 10));

        var inventory = new TabletInventory(tablet, TeleportTabletContainer.TABLET_SIZE);
        var menu = new TeleportTabletContainer(1, player.getInventory(), inventory, 0);
        assertSourceSlotLocked(helper, menu, player, TABLET_HOTBAR_START, tablet);
        helper.succeed();
    }

    /**
     * Pressing the source slot's hotbar key over another slot still moves the satchel (vanilla swap does not consult the
     * source slot), but the menu becomes invalid and the moved satchel keeps exactly its last written contents, so the
     * server ignores further clicks and closes the menu without duping.
     */
    public static void satchelHotbarKeyMoveInvalidatesWithoutDupe(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var satchel = new ItemStack(OccultismItems.SATCHEL.get());
        player.getInventory().setItem(0, satchel);

        var inventory = new SatchelInventory(satchel, StorageSatchelContainer.SATCHEL_SIZE);
        var menu = new StorageSatchelContainer(1, player.getInventory(), inventory, 0);
        inventory.setItem(0, new ItemStack(Items.DIAMOND, 5));
        menu.broadcastChanges();

        //hotbar key 1 (button 0) over the first main inventory slot (player inventory slot 9)
        menu.clicked(StorageSatchelContainer.SATCHEL_SIZE, 0, ContainerInput.SWAP, player);
        menu.broadcastChanges();

        if (player.getInventory().getItem(0) == satchel) {
            //swap was blocked entirely, which is also fine
            helper.assertTrue(menu.stillValid(player), "Menu should stay valid when the swap was blocked");
        } else {
            helper.assertFalse(menu.stillValid(player), "Menu must be invalid after its satchel was moved by hotbar key");
            //simulate taking the diamonds out of the (now invalid) menu, the moved satchel must not be updated
            inventory.setItem(0, ItemStack.EMPTY);
            menu.broadcastChanges();
        }
        helper.assertValueEqual(count(satchel, Items.DIAMOND), 5, "diamonds in the satchel");
        helper.succeed();
    }

    /**
     * Satchels opened from the offhand are tracked as Inventory.SLOT_OFFHAND and validated against the offhand stack.
     */
    public static void satchelOpenedFromOffhandValidatesOffhand(GameTestHelper helper) {
        withServerPlayer(helper, player -> {
            var satchel = new ItemStack(OccultismItems.SATCHEL.get());
            player.setItemInHand(InteractionHand.OFF_HAND, satchel);
            satchel.use(helper.getLevel(), player, InteractionHand.OFF_HAND);

            helper.assertTrue(player.containerMenu instanceof StorageSatchelContainer, "Expected an open satchel menu, got " + player.containerMenu);
            var menu = player.containerMenu;
            helper.assertTrue(menu.stillValid(player), "Satchel menu opened from the offhand should be valid");

            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.getInventory().setItem(5, satchel);
            helper.assertFalse(menu.stillValid(player), "Satchel menu must be invalid once the satchel left the offhand");
            player.closeContainer();
        });
        helper.succeed();
    }

    public static void tabletOpenedFromOffhandValidatesOffhand(GameTestHelper helper) {
        withServerPlayer(helper, player -> {
            var tablet = new ItemStack(OccultismItems.WORMHOLE_TABLET.get());
            player.setItemInHand(InteractionHand.OFF_HAND, tablet);
            player.setShiftKeyDown(true);
            tablet.use(helper.getLevel(), player, InteractionHand.OFF_HAND);
            player.setShiftKeyDown(false);

            helper.assertTrue(player.containerMenu instanceof TeleportTabletContainer, "Expected an open tablet menu, got " + player.containerMenu);
            var menu = player.containerMenu;
            helper.assertTrue(menu.stillValid(player), "Tablet menu opened from the offhand should be valid");

            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.getInventory().setItem(5, tablet);
            helper.assertFalse(menu.stillValid(player), "Tablet menu must be invalid once the tablet left the offhand");
            player.closeContainer();
        });
        helper.succeed();
    }

    // endregion source slot validation

    // region rotate

    /**
     * Regression for "rotate wormhole tablet contents on the server": the server rotates the contents it knows.
     */
    public static void rotateTabletContentsOnServer(GameTestHelper helper) {
        withServerPlayer(helper, player -> {
            var tablet = new ItemStack(OccultismItems.WORMHOLE_TABLET.get());
            tablet.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(
                    named("A"), named("B"), named("C"))));
            player.setItemInHand(InteractionHand.MAIN_HAND, tablet);

            new MessageRotateTabletContents(InteractionHand.MAIN_HAND, true).onServerReceived(helper.getLevel().getServer(), player);
            assertNames(helper, player.getItemInHand(InteractionHand.MAIN_HAND), "C", "A", "B");

            new MessageRotateTabletContents(InteractionHand.MAIN_HAND, false).onServerReceived(helper.getLevel().getServer(), player);
            new MessageRotateTabletContents(InteractionHand.MAIN_HAND, false).onServerReceived(helper.getLevel().getServer(), player);
            assertNames(helper, player.getItemInHand(InteractionHand.MAIN_HAND), "B", "C", "A");
        });
        helper.succeed();
    }

    /**
     * The rotate packet must not touch a tablet while a menu is open, nor any non-tablet item.
     */
    public static void rotateTabletContentsIgnoredWhenNotApplicable(GameTestHelper helper) {
        withServerPlayer(helper, player -> {
            var satchel = new ItemStack(OccultismItems.SATCHEL.get());
            satchel.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(named("A"), named("B"))));
            player.setItemInHand(InteractionHand.MAIN_HAND, satchel);
            new MessageRotateTabletContents(InteractionHand.MAIN_HAND, true).onServerReceived(helper.getLevel().getServer(), player);
            assertNames(helper, satchel, "A", "B");

            var tablet = new ItemStack(OccultismItems.WORMHOLE_TABLET.get());
            tablet.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(named("A"), named("B"))));
            player.setItemInHand(InteractionHand.MAIN_HAND, tablet);
            player.setShiftKeyDown(true);
            tablet.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            player.setShiftKeyDown(false);
            helper.assertTrue(player.containerMenu instanceof TeleportTabletContainer, "Expected an open tablet menu");

            new MessageRotateTabletContents(InteractionHand.MAIN_HAND, true).onServerReceived(helper.getLevel().getServer(), player);
            assertNames(helper, tablet, "A", "B");
            player.closeContainer();
        });
        helper.succeed();
    }

    // endregion rotate

    // region back compass

    /**
     * Regression for "set wormhole tablet back-compass on the server directly": teleporting writes the departure point
     * into the BACK compass on the server (previously sent a to-server packet from the server).
     */
    public static void teleportSetsBackCompass(GameTestHelper helper) {
        var stonePos = new BlockPos(3, 1, 3);
        helper.setBlock(stonePos, Blocks.STONE);
        var startPos = helper.absolutePos(new BlockPos(1, 2, 1));

        withServerPlayer(helper, player -> {
            player.snapTo(startPos.getX() + 0.5, startPos.getY(), startPos.getZ() + 0.5);
            var tablet = new ItemStack(OccultismItems.WORMHOLE_TABLET.get());
            tablet.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(
                    lodestoneCompass(helper, helper.absolutePos(stonePos)), named("BACK"))));
            player.setItemInHand(InteractionHand.MAIN_HAND, tablet);

            tablet.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

            var contents = tablet.get(DataComponents.CONTAINER);
            helper.assertTrue(contents != null && contents.getSlots() >= 2, "Tablet lost its contents");
            var back = contents.getStackInSlot(1);
            helper.assertValueEqual(back.getHoverName().getString(), "BACK", "second compass name");
            var tracker = back.get(DataComponents.LODESTONE_TRACKER);
            helper.assertTrue(tracker != null && tracker.target().isPresent(), "BACK compass was not set to the departure point");
            helper.assertValueEqual(tracker.target().get().pos(), startPos, "BACK compass target");
            //the player position itself is not checked: FakePlayer's net handler ignores teleports
        });
        helper.succeed();
    }

    /**
     * Teleporting with the BACK compass in the first slot must not overwrite it (name compared by value).
     */
    public static void teleportWithBackCompassKeepsItsTarget(GameTestHelper helper) {
        var stonePos = new BlockPos(3, 1, 3);
        helper.setBlock(stonePos, Blocks.STONE);
        var startPos = helper.absolutePos(new BlockPos(1, 2, 1));

        withServerPlayer(helper, player -> {
            player.snapTo(startPos.getX() + 0.5, startPos.getY(), startPos.getZ() + 0.5);
            var back = lodestoneCompass(helper, helper.absolutePos(stonePos));
            back.set(DataComponents.CUSTOM_NAME, Component.literal("BACK"));
            var tablet = new ItemStack(OccultismItems.WORMHOLE_TABLET.get());
            tablet.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(back)));
            player.setItemInHand(InteractionHand.MAIN_HAND, tablet);

            tablet.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

            var tracker = tablet.get(DataComponents.CONTAINER).getStackInSlot(0).get(DataComponents.LODESTONE_TRACKER);
            helper.assertTrue(tracker != null && tracker.target().isPresent(), "BACK compass lost its target");
            helper.assertValueEqual(tracker.target().get().pos(), helper.absolutePos(stonePos), "BACK compass target");
        });
        helper.succeed();
    }

    // endregion back compass

    // region random teleport

    /**
     * Regression for "make random teleport search safe and cheaper": an entity at (less than one block from) the world
     * border used to make nextInt(-0, 0) throw.
     */
    public static void randomTeleportAtWorldBorderDoesNotThrow(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        WorldBorder border = helper.getLevel().getWorldBorder();
        player.setPos(border.getMaxX() - 0.5, 100, border.getCenterZ());
        helper.assertTrue(border.getDistanceToBorder(player) < 1, "Test entity is not at the world border");

        try {
            TeleportUtil.findSafeRTP(helper.getLevel(), player, 4);
        } catch (RuntimeException e) {
            helper.fail("findSafeRTP threw at the world border: " + e);
        }
        helper.succeed();
    }

    /**
     * Random teleport should find a target around an entity whose surroundings are not loaded yet.
     * NOT REGISTERED: currently fails. findSafeRTP uses Level#getHeight, which returns getMinY() for unloaded chunks
     * instead of loading them, so every candidate outside already loaded chunks is rejected and RTP returns null.
     */
    public static void randomTeleportFindsTargetInUnloadedArea(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(1_000_000.5, 100, 1_000_000.5);

        var target = TeleportUtil.findSafeRTP(helper.getLevel(), player, 16);
        helper.assertTrue(target != null, "findSafeRTP found no target around an entity in an unloaded area");
        helper.succeed();
    }

    // endregion random teleport

    // region helpers

    private static void assertSourceSlotLocked(GameTestHelper helper, AbstractContainerMenu menu, Player player, int sourceMenuSlot, ItemStack source) {
        //pick up
        menu.clicked(sourceMenuSlot, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty(), "Source slot could be picked up");
        helper.assertTrue(player.getInventory().getItem(0) == source, "Source slot changed after pickup click");

        //shift click
        menu.clicked(sourceMenuSlot, 0, ContainerInput.QUICK_MOVE, player);
        helper.assertTrue(player.getInventory().getItem(0) == source, "Source slot changed after shift click");

        //hotbar key 2 (holding dirt) while hovering the source slot
        menu.clicked(sourceMenuSlot, 1, ContainerInput.SWAP, player);
        helper.assertTrue(player.getInventory().getItem(0) == source, "Source slot changed after hotbar swap");
        helper.assertTrue(player.getInventory().getItem(1).is(Items.DIRT), "Hotbar slot 2 changed after hotbar swap");

        //place onto the source slot
        menu.setCarried(new ItemStack(Items.DIRT, 1));
        menu.clicked(sourceMenuSlot, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(player.getInventory().getItem(0) == source, "Source slot changed after placing onto it");
        helper.assertTrue(menu.getCarried().is(Items.DIRT), "Carried stack changed after placing onto the source slot");
        menu.setCarried(ItemStack.EMPTY);

        helper.assertTrue(menu.stillValid(player), "Menu should still be valid");
    }

    private static void withServerPlayer(GameTestHelper helper, Consumer<ServerPlayer> test) {
        test.accept(makeServerPlayer(helper));
    }

    /**
     * A fake server player that is not added to the level or player list. Opening a menu creates the server side menu
     * and sets it as containerMenu without sending anything to a client.
     */
    private static ServerPlayer makeServerPlayer(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "occultism-test-player")) {
            @Override
            public OptionalInt openMenu(@Nullable MenuProvider menuProvider, @Nullable Consumer<RegistryFriendlyByteBuf> extraDataWriter) {
                if (menuProvider == null)
                    return OptionalInt.empty();
                var menu = menuProvider.createMenu(1, this.getInventory(), this);
                if (menu == null)
                    return OptionalInt.empty();
                this.containerMenu = menu;
                return OptionalInt.of(menu.containerId);
            }
        };
    }

    private static ItemStack named(String name) {
        var stack = new ItemStack(Items.COMPASS);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static ItemStack lodestoneCompass(GameTestHelper helper, BlockPos target) {
        var stack = new ItemStack(Items.COMPASS);
        stack.set(DataComponents.LODESTONE_TRACKER,
                new LodestoneTracker(Optional.of(GlobalPos.of(helper.getLevel().dimension(), target)), false));
        return stack;
    }

    private static void assertNames(GameTestHelper helper, ItemStack stack, String... names) {
        var contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        var actual = new StringBuilder();
        for (int i = 0; i < contents.getSlots(); i++) {
            var item = contents.getStackInSlot(i);
            if (!item.isEmpty())
                actual.append(item.getHoverName().getString());
        }
        helper.assertValueEqual(actual.toString(), String.join("", names), "contents order");
    }

    private static int count(ItemStack container, Item item) {
        var contents = container.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        int count = 0;
        for (int i = 0; i < contents.getSlots(); i++) {
            var stack = contents.getStackInSlot(i);
            if (stack.is(item))
                count += stack.getCount();
        }
        return count;
    }

    // endregion helpers
}
