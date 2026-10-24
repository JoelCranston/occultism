// SPDX-FileCopyrightText: 2026 klikli-dev
//
// SPDX-License-Identifier: MIT

package com.klikli_dev.occultism.gametest;

import com.klikli_dev.modonomicon.api.multiblock.Multiblock.SimulateResult;
import com.klikli_dev.occultism.Occultism;
import com.klikli_dev.occultism.common.advancement.RitualTrigger.TriggerInstance;
import com.klikli_dev.occultism.common.blockentity.GoldenSacrificialBowlBlockEntity;
import com.klikli_dev.occultism.common.blockentity.RitualCatcherBlockEntity;
import com.klikli_dev.occultism.common.blockentity.SacrificialBowlBlockEntity;
import com.klikli_dev.occultism.crafting.recipe.OccultismRecipeManager;
import com.klikli_dev.occultism.crafting.recipe.RitualRecipe;
import com.klikli_dev.occultism.network.messages.MessageSendPreviewedPentacle;
import com.klikli_dev.occultism.registry.OccultismBlocks;
import com.klikli_dev.occultism.registry.OccultismDataComponents;
import com.klikli_dev.occultism.registry.OccultismItems;
import com.klikli_dev.occultism.registry.OccultismRecipes;
import com.klikli_dev.occultism.util.EntityUtil;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Game tests for rituals: pentacle validation, golden bowl activation, the full ritual lifecycle and regressions.
 * <p>
 * The full ritual tests use the "repair chalks" ritual: it is one of the shortest real rituals (10 seconds), needs no
 * sacrifice or item use, and its result (the repaired chalk) is easy to identify.
 */
public class RitualGameTests {

    private static final Identifier REPAIR_CHALKS = Identifier.fromNamespaceAndPath(Occultism.MODID, "ritual/repair_chalks");
    private static final Identifier POSSESS_SKELETON = Identifier.fromNamespaceAndPath(Occultism.MODID, "ritual/possess_skeleton");
    private static final Identifier CRAFT_DJINNI_PENTACLE = Identifier.fromNamespaceAndPath(Occultism.MODID, "craft_djinni");

    /**
     * Golden bowl position in the 17x8x17 structure. The craft_djinni pentacle is 13x2x13, with the golden bowl on the
     * top layer, so the bottom (floor) layer is at y = 1.
     */
    private static final BlockPos GOLDEN_BOWL = new BlockPos(8, 2, 8);

    /**
     * Free ("any") positions in the craft_djinni pentacle relative to the golden bowl, used for the sacrificial bowls.
     */
    private static final List<BlockPos> SACRIFICIAL_BOWL_OFFSETS = List.of(
            new BlockPos(2, 0, 0), new BlockPos(-2, 0, 0), new BlockPos(0, 0, 2), new BlockPos(0, 0, -2));

    private static final int CHALK_DAMAGE = 10;

    // region Helpers

    private static RecipeHolder<RitualRecipe> ritualRecipe(GameTestHelper helper, Identifier id) {
        var key = ResourceKey.create(Registries.RECIPE, id);
        var recipe = OccultismRecipeManager.get().getRecipeByKey(OccultismRecipes.RITUAL_TYPE.get(), key, helper.getLevel());
        if (recipe.isEmpty())
            throw helper.assertionException(Component.literal("Ritual recipe " + id + " not found"));
        return recipe.get();
    }

    private static FakePlayer fakePlayer(GameTestHelper helper) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "occultism_ritual_test"));
    }

    private static ItemStack damagedChalk() {
        var chalk = new ItemStack(OccultismItems.CHALK_WHITE.get());
        chalk.setDamageValue(CHALK_DAMAGE);
        return chalk;
    }

    /**
     * Builds the craft_djinni pentacle around {@link #GOLDEN_BOWL} by placing the displayed state of every matcher.
     *
     * @return the simulation results, e.g. to find glyph positions.
     */
    private static List<SimulateResult> buildCraftDjinniPentacle(GameTestHelper helper) {
        var level = helper.getLevel();
        var pentacle = ritualRecipe(helper, REPAIR_CHALKS).value().getPentacle();
        helper.assertTrue(pentacle != null && CRAFT_DJINNI_PENTACLE.equals(pentacle.getId()), "Expected the repair chalks ritual to use the craft_djinni pentacle");

        var anchor = helper.absolutePos(GOLDEN_BOWL);
        var results = new ArrayList<>(pentacle.simulate(level, anchor, Rotation.NONE, false, false).getSecond());
        //place the floor first so glyphs and candles have support
        results.sort(Comparator.comparingInt(r -> r.worldPosition().getY()));
        for (var result : results) {
            var state = result.stateMatcher().getDisplayedState(0);
            if (!state.isAir())
                level.setBlock(result.worldPosition(), state, 3);
        }

        helper.assertTrue(pentacle.validate(level, anchor) != null, "Placed craft_djinni pentacle does not validate");
        helper.assertBlockPresent(OccultismBlocks.GOLDEN_SACRIFICIAL_BOWL.get(), GOLDEN_BOWL);
        return results;
    }

    /**
     * Places sacrificial bowls with the ingredients of the repair chalks ritual.
     */
    private static List<SacrificialBowlBlockEntity> placeRepairChalksIngredients(GameTestHelper helper) {
        var items = List.of(
                new ItemStack(OccultismItems.OTHERWORLD_ESSENCE.get()),
                new ItemStack(OccultismItems.OTHERWORLD_ESSENCE.get()),
                new ItemStack(OccultismItems.OTHERWORLD_ESSENCE.get()),
                new ItemStack(OccultismItems.SPIRIT_ATTUNED_GEM.get()));

        var bowls = new ArrayList<SacrificialBowlBlockEntity>();
        for (int i = 0; i < SACRIFICIAL_BOWL_OFFSETS.size(); i++) {
            var pos = GOLDEN_BOWL.offset(SACRIFICIAL_BOWL_OFFSETS.get(i));
            helper.setBlock(pos, OccultismBlocks.SACRIFICIAL_BOWL.get());
            SacrificialBowlBlockEntity bowl = helper.getBlockEntity(pos, SacrificialBowlBlockEntity.class);
            bowl.itemStackHandler.set(0, ItemResource.of(items.get(i)), 1);
            bowls.add(bowl);
        }
        return bowls;
    }

    private static GoldenSacrificialBowlBlockEntity goldenBowl(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, GoldenSacrificialBowlBlockEntity.class);
    }

    private static List<ItemEntity> chalkEntities(GameTestHelper helper, int damage) {
        return helper.getEntities(EntityType.ITEM, GOLDEN_BOWL, 8).stream()
                .filter(e -> e.getItem().is(OccultismItems.CHALK_WHITE.get()) && e.getItem().getDamageValue() == damage)
                .toList();
    }

    private static boolean isEmpty(ItemStacksResourceHandler handler) {
        return handler.getResource(0).isEmpty() || handler.getAmountAsInt(0) == 0;
    }

    // endregion Helpers

    /**
     * End-to-end: build a real pentacle, fill the sacrificial bowls, activate the golden bowl with a player holding the
     * activation item and wait for the ritual to finish.
     */
    public static void fullRitualCompletes(GameTestHelper helper) {
        buildCraftDjinniPentacle(helper);
        var sacrificialBowls = placeRepairChalksIngredients(helper);
        var bowl = goldenBowl(helper, GOLDEN_BOWL);

        var player = fakePlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, damagedChalk());
        helper.useBlock(GOLDEN_BOWL, player);

        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                "Activation item was not taken from the player, remaining: " + player.getItemInHand(InteractionHand.MAIN_HAND));
        helper.assertTrue(bowl.itemStackHandler.getResource(0).is(OccultismItems.CHALK_WHITE.get()) && bowl.itemStackHandler.getAmountAsInt(0) == 1,
                "Golden bowl should hold exactly one activation item");
        helper.assertTrue(bowl.ritualActive, "Ritual did not start");
        var recipe = bowl.getCurrentRitualRecipe();
        helper.assertTrue(recipe != null && REPAIR_CHALKS.equals(recipe.id().identifier()), "Expected the repair chalks ritual to be active");
        helper.assertTrue(bowl.castingPlayer == player, "Casting player was not set");

        helper.succeedWhen(() -> {
            helper.assertFalse(bowl.ritualActive, "Ritual still active");
            helper.assertTrue(bowl.getCurrentRitualRecipe() == null, "Ritual recipe not cleared after finishing");
            helper.assertTrue(isEmpty(bowl.itemStackHandler), "Activation item still in golden bowl");
            for (var sacrificialBowl : sacrificialBowls)
                helper.assertTrue(isEmpty(sacrificialBowl.itemStackHandler), "Ingredient was not consumed at " + sacrificialBowl.getBlockPos());
            helper.assertValueEqual(chalkEntities(helper, 0).size(), 1, "repaired chalk item entities");
            helper.assertValueEqual(chalkEntities(helper, CHALK_DAMAGE).size(), 0, "unrepaired chalk item entities");
        });
    }

    /**
     * Regression: the activation item must only be consumed if it was actually inserted into the golden bowl.
     */
    public static void activationItemNotConsumedWhenInsertFails(GameTestHelper helper) {
        buildCraftDjinniPentacle(helper);
        placeRepairChalksIngredients(helper);
        var bowl = goldenBowl(helper, GOLDEN_BOWL);
        //the bowl is occupied (but no ritual is running), so the activation item cannot be inserted
        bowl.itemStackHandler.set(0, ItemResource.of(Items.STICK), 1);

        var player = fakePlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, damagedChalk());
        helper.useBlock(GOLDEN_BOWL, player);

        var held = player.getItemInHand(InteractionHand.MAIN_HAND);
        helper.assertTrue(held.is(OccultismItems.CHALK_WHITE.get()) && held.getCount() == 1,
                "Activation item was consumed although it was not inserted, player now holds: " + held);
        helper.assertTrue(bowl.itemStackHandler.getResource(0).is(Items.STICK), "Golden bowl content changed");
        helper.assertFalse(bowl.ritualActive, "Ritual should not have started");
        helper.succeed();
    }

    /**
     * Automation path: inserting the activation item through the item handler (e.g. a hopper) starts the ritual only
     * when the transaction is committed, and the bowl rejects other items.
     */
    public static void handlerInsertStartsRitualOnlyOnCommit(GameTestHelper helper) {
        buildCraftDjinniPentacle(helper);
        placeRepairChalksIngredients(helper);
        var bowl = goldenBowl(helper, GOLDEN_BOWL);
        var chalk = ItemResource.of(damagedChalk());

        try (var tx = Transaction.openRoot()) {
            helper.assertValueEqual(bowl.itemStackHandler.insert(0, ItemResource.of(Items.STICK), 1, tx), 0, "inserted non activation items");
        }

        try (var tx = Transaction.openRoot()) {
            helper.assertValueEqual(bowl.itemStackHandler.insert(0, chalk, 1, tx), 1, "simulated activation item insert");
            //aborted
        }
        helper.assertTrue(isEmpty(bowl.itemStackHandler), "Aborted insert left an item in the golden bowl");
        helper.assertFalse(bowl.ritualActive, "Aborted insert started a ritual");

        try (var tx = Transaction.openRoot()) {
            helper.assertValueEqual(bowl.itemStackHandler.insert(0, chalk, 1, tx), 1, "inserted activation items");
            tx.commit();
        }
        helper.assertTrue(bowl.ritualActive, "Committed insert did not start the ritual");
        var recipe = bowl.getCurrentRitualRecipe();
        helper.assertTrue(recipe != null && REPAIR_CHALKS.equals(recipe.id().identifier()), "Expected the repair chalks ritual to be active");

        try (var tx = Transaction.openRoot()) {
            helper.assertValueEqual(bowl.itemStackHandler.insert(0, chalk, 1, tx), 0, "inserted items into an occupied golden bowl");
        }

        bowl.stopRitual(false);
        helper.assertFalse(bowl.ritualActive, "Ritual did not stop");
        helper.succeed();
    }

    /**
     * Breaking the pentacle interrupts a running ritual (validation is throttled, but must still happen) and returns
     * the activation item.
     */
    public static void brokenPentacleInterruptsRitual(GameTestHelper helper) {
        var pentacle = buildCraftDjinniPentacle(helper);
        placeRepairChalksIngredients(helper);
        var bowl = goldenBowl(helper, GOLDEN_BOWL);

        var player = fakePlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, damagedChalk());
        helper.useBlock(GOLDEN_BOWL, player);
        helper.assertTrue(bowl.ritualActive, "Ritual did not start");

        var glyph = pentacle.stream().filter(r -> r.character() == 'X').findFirst()
                .orElseThrow(() -> helper.assertionException(Component.literal("No purple glyph in the craft_djinni pentacle")))
                .worldPosition();

        helper.runAfterDelay(25, () -> {
            helper.assertTrue(bowl.ritualActive, "Ritual stopped before the pentacle was broken");
            helper.getLevel().setBlock(glyph, Blocks.AIR.defaultBlockState(), 3);

            helper.succeedWhen(() -> {
                helper.assertFalse(bowl.ritualActive, "Ritual still active after breaking the pentacle");
                helper.assertTrue(isEmpty(bowl.itemStackHandler), "Activation item still in golden bowl");
                helper.assertValueEqual(chalkEntities(helper, CHALK_DAMAGE).size(), 1, "returned activation item entities");
                helper.assertValueEqual(chalkEntities(helper, 0).size(), 0, "repaired chalk item entities");
            });
        });
    }

    /**
     * Regression: only one flame of automation is put into ritual catchers per ritual.
     */
    public static void oneFlameOfAutomationPerRitual(GameTestHelper helper) {
        var center = new BlockPos(2, 1, 2);
        var catcherPositions = List.of(new BlockPos(1, 2, 2), new BlockPos(3, 2, 2), new BlockPos(2, 2, 1));
        for (var pos : catcherPositions)
            helper.setBlock(pos, OccultismBlocks.RITUAL_CATCHER.get());

        var ritual = ritualRecipe(helper, REPAIR_CHALKS).value().getRitual();
        ritual.dropResultAndFlame(helper.getLevel(), helper.absolutePos(center), null, null, ItemStack.EMPTY);

        int flames = 0;
        for (var pos : catcherPositions) {
            var catcher = helper.getBlockEntity(pos, RitualCatcherBlockEntity.class);
            if (catcher.itemStackHandler.getResource(0).is(OccultismItems.FLAME_AUTOMATION.get()))
                flames += catcher.itemStackHandler.getAmountAsInt(0);
        }
        helper.assertValueEqual(flames, 1, "flames of automation in ritual catchers");
        helper.succeed();
    }

    /**
     * Regression: ritual advancement criteria with a ritual_id compare against the recipe identifier.
     */
    public static void advancementRitualIdMatchesRecipe(GameTestHelper helper) {
        var holder = ritualRecipe(helper, REPAIR_CHALKS);
        var ritual = holder.value().getRitual();
        var player = fakePlayer(helper);

        var matching = new TriggerInstance(Optional.empty(), Optional.of(REPAIR_CHALKS), Optional.empty());
        helper.assertTrue(matching.matches(player, ritual), "Advancement with the ritual's recipe id did not match");

        var other = new TriggerInstance(Optional.empty(), Optional.of(POSSESS_SKELETON), Optional.empty());
        helper.assertFalse(other.matches(player, ritual), "Advancement with another ritual id matched");

        var factory = new TriggerInstance(Optional.empty(), Optional.empty(), Optional.of(ritual.getFactoryID()));
        helper.assertTrue(factory.matches(player, ritual), "Advancement with the ritual factory id did not match");
        helper.succeed();
    }

    /**
     * Regression: rituals are cached per recipe and still resolve their recipe holder.
     */
    public static void ritualInstanceIsCachedPerRecipe(GameTestHelper helper) {
        var holder = ritualRecipe(helper, REPAIR_CHALKS);
        var ritual = holder.value().getRitual();
        helper.assertTrue(ritual == holder.value().getRitual(), "getRitual() created a new ritual instance");

        var resolved = ritual.getRecipeHolder(helper.getLevel());
        helper.assertTrue(resolved != null && REPAIR_CHALKS.equals(resolved.id().identifier()), "Cached ritual resolved the wrong recipe holder");
        helper.assertTrue(ritual.getRecipeHolder(helper.getLevel()) != null, "Cached ritual failed to resolve its recipe holder a second time");
        helper.succeed();
    }

    /**
     * Regression: a network update without ritual data clears the previously synced ritual state.
     */
    public static void loadNetworkClearsStaleRitualState(GameTestHelper helper) {
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, OccultismBlocks.GOLDEN_SACRIFICIAL_BOWL.get());
        var bowl = goldenBowl(helper, pos);

        bowl.currentRitualRecipe = ritualRecipe(helper, REPAIR_CHALKS);
        bowl.castingPlayerId = UUID.randomUUID();
        bowl.ritualActive = true;
        bowl.currentTime = 5;

        bowl.loadNetwork(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), new CompoundTag()));

        helper.assertTrue(bowl.getCurrentRitualRecipe() == null, "Stale ritual recipe kept after sync");
        helper.assertTrue(bowl.castingPlayerId == null, "Stale casting player kept after sync");
        helper.assertFalse(bowl.ritualActive, "Stale ritualActive kept after sync");
        helper.assertValueEqual(bowl.currentTime, 0, "current time");
        helper.succeed();
    }

    /**
     * Regression: loading a golden bowl whose ritual recipe no longer exists must not crash.
     */
    public static void loadWithMissingRitualRecipe(GameTestHelper helper) {
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, OccultismBlocks.GOLDEN_SACRIFICIAL_BOWL.get());
        var bowl = goldenBowl(helper, pos);

        var tag = new CompoundTag();
        tag.putString("currentRitual", Identifier.fromNamespaceAndPath(Occultism.MODID, "ritual/gametest_missing_ritual").toString());
        tag.putBoolean("ritualActive", true);
        bowl.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));

        helper.assertTrue(bowl.getCurrentRitualRecipe() == null, "Missing ritual recipe resolved to something");
        helper.assertTrue(bowl.remainingAdditionalIngredients != null && bowl.remainingAdditionalIngredients.isEmpty(),
                "Remaining ingredients should be empty for a missing recipe");

        //ticking must not crash either
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(bowl.getCurrentRitualRecipe() == null, "Missing ritual recipe resolved to something");
            helper.succeed();
        });
    }

    /**
     * Regression: the sacrifice/item use listeners of a golden bowl are unregistered when its chunk unloads.
     */
    public static void listenersUnregisteredOnChunkUnload(GameTestHelper helper) {
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, OccultismBlocks.GOLDEN_SACRIFICIAL_BOWL.get());
        var bowl = goldenBowl(helper, pos);
        var level = helper.getLevel();
        var player = fakePlayer(helper);

        var holder = ritualRecipe(helper, POSSESS_SKELETON);
        helper.assertTrue(holder.value().requiresSacrifice(), "Expected possess skeleton to require a sacrifice");
        helper.assertTrue(bowl.startRitual(null, ItemStack.EMPTY, holder), "Ritual did not start");

        var chicken = EntityType.CHICKEN.create(level, EntitySpawnReason.TRIGGERED);
        helper.assertTrue(chicken != null, "Could not create chicken");
        var chickenPos = helper.absolutePos(pos).above();
        chicken.setPos(chickenPos.getX() + 0.5, chickenPos.getY(), chickenPos.getZ() + 0.5);

        //sanity check: while the ritual runs the sacrifice is detected
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(chicken, level.damageSources().playerAttack(player)));
        helper.assertTrue(bowl.sacrificeProvided, "Sacrifice was not detected while the ritual was running");

        bowl.sacrificeProvided = false;
        bowl.onChunkUnloaded();
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(chicken, level.damageSources().playerAttack(player)));
        helper.assertFalse(bowl.sacrificeProvided, "Unloaded golden bowl still received sacrifice events");

        bowl.stopRitual(false);
        helper.succeed();
    }

    /**
     * Regression: previewing a sacrifice from an entity tag without (non-player) entities must not crash.
     */
    public static void emptyEntityTagPreviewReturnsNull(GameTestHelper helper) {
        var level = helper.getLevel();
        var lookup = level.registryAccess().lookupOrThrow(Registries.ENTITY_TYPE);

        TagKey<EntityType<?>> emptyTag = lookup.listTags()
                .filter(set -> set.stream().map(Holder::value).allMatch(type -> type == EntityType.PLAYER))
                .map(HolderSet.Named::key)
                .findFirst()
                .orElseThrow(() -> helper.assertionException(Component.literal("No entity type tag without non-player entities found")));

        helper.assertTrue(EntityUtil.getEntityInTag(level, emptyTag) == null, "Expected no entity for empty tag " + emptyTag.location());
        helper.assertTrue(EntityUtil.getEntityInTag(level, TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("c", "chickens"))) != null,
                "Expected an entity for the chickens tag");
        helper.succeed();
    }

    /**
     * Regression: pentacle preview data sent by the client is validated before it is stored.
     */
    public static void previewedPentacleIsValidated(GameTestHelper helper) {
        var player = fakePlayer(helper);
        var satchel = OccultismItems.RITUAL_SATCHEL_T1.get();
        var target = helper.absolutePos(new BlockPos(2, 1, 2));
        var server = helper.getLevel().getServer();

        new MessageSendPreviewedPentacle(CRAFT_DJINNI_PENTACLE, target.offset(1000, 0, 0), Rotation.NONE, target).onServerReceived(server, player);
        helper.assertFalse(satchel.targetPentacles().containsKey(player.getUUID()), "Far away pentacle anchor was accepted");

        new MessageSendPreviewedPentacle(Identifier.fromNamespaceAndPath(Occultism.MODID, "gametest_missing_pentacle"), target, Rotation.NONE, target).onServerReceived(server, player);
        helper.assertFalse(satchel.targetPentacles().containsKey(player.getUUID()), "Unknown pentacle was accepted");

        new MessageSendPreviewedPentacle(CRAFT_DJINNI_PENTACLE, target.offset(2, 0, 2), Rotation.NONE, target).onServerReceived(server, player);
        helper.assertTrue(satchel.targetPentacles().containsKey(player.getUUID()), "Valid pentacle preview was rejected");
        helper.assertTrue(OccultismItems.RITUAL_SATCHEL_T2.get().targetPentacles().containsKey(player.getUUID()), "Valid pentacle preview was not stored for the T2 satchel");

        satchel.targetPentacles().remove(player.getUUID());
        OccultismItems.RITUAL_SATCHEL_T2.get().targetPentacles().remove(player.getUUID());
        helper.succeed();
    }

    /**
     * Regression: network serialization of bowls strips storage controller contents from a copy only and keeps all
     * other items.
     */
    public static void bowlNetworkSyncKeepsInventory(GameTestHelper helper) {
        var level = helper.getLevel();
        var contentsTag = new CompoundTag();
        contentsTag.putString("gametest", "keep_me");
        var contents = CustomData.of(contentsTag);

        var controller = new ItemStack(OccultismBlocks.STORAGE_CONTROLLER.get());
        controller.set(OccultismDataComponents.STORAGE_CONTROLLER_CONTENTS.get(), contents);

        var bowlPos = new BlockPos(1, 1, 1);
        helper.setBlock(bowlPos, OccultismBlocks.SACRIFICIAL_BOWL.get());
        var bowl = helper.getBlockEntity(bowlPos, SacrificialBowlBlockEntity.class);
        bowl.itemStackHandler.set(0, ItemResource.of(controller), 1);
        assertNetworkCopy(helper, level, bowl.itemStackHandler, out -> bowl.saveNetwork(out), contents, "sacrificial bowl");

        var otherPos = new BlockPos(3, 1, 1);
        helper.setBlock(otherPos, OccultismBlocks.SACRIFICIAL_BOWL.get());
        var otherBowl = helper.getBlockEntity(otherPos, SacrificialBowlBlockEntity.class);
        otherBowl.itemStackHandler.set(0, ItemResource.of(Items.DIAMOND), 1);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        otherBowl.saveNetwork(output);
        var synced = new ItemStacksResourceHandler(1);
        synced.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()).childOrEmpty("inventory"));
        helper.assertTrue(synced.getResource(0).is(Items.DIAMOND), "Regular item missing from the network sync data");

        var catcherPos = new BlockPos(1, 1, 3);
        helper.setBlock(catcherPos, OccultismBlocks.RITUAL_CATCHER.get());
        var catcher = helper.getBlockEntity(catcherPos, RitualCatcherBlockEntity.class);
        catcher.itemStackHandler.set(0, ItemResource.of(controller), 1);
        assertNetworkCopy(helper, level, catcher.itemStackHandler, out -> catcher.saveNetwork(out), contents, "ritual catcher");

        //clear the controllers so nothing is dropped when the test area is cleaned up
        bowl.itemStackHandler.set(0, ItemResource.of(ItemStack.EMPTY), 0);
        catcher.itemStackHandler.set(0, ItemResource.of(ItemStack.EMPTY), 0);
        helper.succeed();
    }

    private static void assertNetworkCopy(GameTestHelper helper, ServerLevel level, ItemStacksResourceHandler handler,
                                          Consumer<TagValueOutput> save, CustomData contents, String name) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        save.accept(output);

        var actual = handler.getResource(0).toStack();
        helper.assertTrue(actual.is(OccultismBlocks.STORAGE_CONTROLLER.get().asItem()), "Storage controller removed from the " + name);
        helper.assertTrue(contents.equals(actual.get(OccultismDataComponents.STORAGE_CONTROLLER_CONTENTS.get())),
                "Network sync stripped the storage controller contents of the actual " + name + " inventory");

        var synced = new ItemStacksResourceHandler(1);
        synced.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()).childOrEmpty("inventory"));
        var syncedStack = synced.getResource(0).toStack();
        helper.assertTrue(syncedStack.is(OccultismBlocks.STORAGE_CONTROLLER.get().asItem()), "Storage controller missing from the " + name + " network sync data");
        var syncedContents = syncedStack.get(OccultismDataComponents.STORAGE_CONTROLLER_CONTENTS.get());
        helper.assertTrue(syncedContents == null || syncedContents.isEmpty(), "Storage controller contents were not stripped from the " + name + " network sync data");
    }
}
