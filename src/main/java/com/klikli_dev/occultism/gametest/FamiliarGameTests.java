// SPDX-FileCopyrightText: 2026 klikli-dev
//
// SPDX-License-Identifier: MIT

package com.klikli_dev.occultism.gametest;

import com.klikli_dev.occultism.Occultism;
import com.klikli_dev.occultism.common.capability.FamiliarSettingsData;
import com.klikli_dev.occultism.common.entity.familiar.BeaverFamiliarEntity;
import com.klikli_dev.occultism.common.entity.familiar.BlacksmithFamiliarEntity;
import com.klikli_dev.occultism.common.entity.familiar.DeerFamiliarEntity;
import com.klikli_dev.occultism.common.entity.familiar.DrikwingEntity;
import com.klikli_dev.occultism.common.entity.familiar.GreedyFamiliarEntity;
import com.klikli_dev.occultism.common.entity.familiar.GuardianFamiliarEntity;
import com.klikli_dev.occultism.common.entity.familiar.HeadlessFamiliarEntity;
import com.klikli_dev.occultism.common.entity.familiar.IFamiliar;
import com.klikli_dev.occultism.common.entity.familiar.IesniumGolemEntity;
import com.klikli_dev.occultism.common.entity.familiar.ShubNiggurathFamiliarEntity;
import com.klikli_dev.occultism.common.entity.familiar.ShubNiggurathSpawnEntity;
import com.klikli_dev.occultism.common.item.familiar.FamiliarCurio;
import com.klikli_dev.occultism.registry.OccultismDataComponents;
import com.klikli_dev.occultism.registry.OccultismEffects;
import com.klikli_dev.occultism.registry.OccultismEntities;
import com.klikli_dev.occultism.registry.OccultismItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Regression tests for familiar ownership, persistence, interactions and abilities.
 * <p>
 * "Offline" owners are mock players that are not part of the level, so the familiar cannot resolve them by UUID,
 * exactly like an owner that logged out. "Online" owners are mock server players placed into the level.
 */
public class FamiliarGameTests {

    /**
     * Test environment definition that disables pvp for the duration of the test batch.
     */
    public static TestEnvironmentDefinition<?> pvpDisabled() {
        return new TestEnvironmentDefinition.SetGameRules(new GameRuleMap.Builder().set(GameRules.PVP, false).build());
    }

    // region helpers

    /**
     * Creates a player that is part of the level, so familiars can resolve it as their owner.
     * <p>
     * Uses a fake player instead of {@link GameTestHelper#makeMockServerPlayerInLevel()}, because logging in a mock
     * player sends mod payloads over a connection that has not negotiated any channels.
     * Unlike a regular fake player it respects the pvp game rule, like a real player.
     */
    private static ServerPlayer onlinePlayer(GameTestHelper helper, BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "occultism-test")) {
            @Override
            public boolean canHarmPlayer(Player target) {
                return level.isPvpAllowed();
            }
        };
        Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(relativePos));
        player.snapTo(pos.x, pos.y, pos.z, 0, 0);
        level.addNewPlayer(player);
        return player;
    }

    private static Player offlinePlayer(GameTestHelper helper, BlockPos relativePos) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(relativePos));
        player.snapTo(pos.x, pos.y, pos.z, 0, 0);
        return player;
    }

    private static void logout(GameTestHelper helper, ServerPlayer player) {
        if (!player.isRemoved())
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
    }

    /**
     * Replaces the familiar data of a familiar item with an equal copy, like a world reload does.
     */
    private static void reloadFamiliarData(ItemStack stack) {
        CustomData data = stack.get(OccultismDataComponents.FAMILIAR_DATA);
        if (data != null)
            stack.set(OccultismDataComponents.FAMILIAR_DATA, CustomData.of(data.copyTag()));
    }

    private static void floor(GameTestHelper helper, int size) {
        for (int x = 0; x < size; x++)
            for (int z = 0; z < size; z++)
                helper.setBlock(x, 0, z, Blocks.STONE);
    }

    private static CompoundTag save(Entity entity) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    private static void load(Entity entity, CompoundTag tag) {
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, entity.registryAccess(), tag));
    }

    /**
     * Edits the saved data of an entity and loads it back, to set state that has no public setter.
     */
    private static void editData(Entity entity, Consumer<CompoundTag> editor) {
        CompoundTag tag = save(entity);
        editor.accept(tag);
        load(entity, tag);
    }

    /**
     * Saves the entity and loads the data into a fresh entity of the same type, like a chunk save/load round trip.
     */
    private static <T extends Entity> T roundTrip(GameTestHelper helper, T entity, EntityType<T> type) {
        CompoundTag tag = save(entity);
        T copy = type.create(helper.getLevel(), EntitySpawnReason.LOAD);
        helper.assertTrue(copy != null, "Could not create " + type);
        load(copy, tag);
        return copy;
    }

    private static Holder<Enchantment> sharpness(GameTestHelper helper) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
    }

    private static ItemStack sharpSword(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(sharpness(helper), 3);
        return sword;
    }

    private static boolean isSharpSword(GameTestHelper helper, ItemStack stack) {
        return stack.is(Items.DIAMOND_SWORD) && EnchantmentHelper.getItemEnchantmentLevel(sharpness(helper), stack) == 3;
    }

    private static boolean isGoalRunning(Mob mob, String goalClassName) {
        for (WrappedGoal goal : mob.goalSelector.getAvailableGoals())
            if (goal.isRunning() && goal.getGoal().getClass().getSimpleName().equals(goalClassName))
                return true;
        return false;
    }

    // endregion helpers

    // region ownership

    /**
     * fix: prevent claiming familiars whose owner is offline
     */
    public static void offlineOwnedFamiliarCannotBeClaimed(GameTestHelper helper) {
        Player owner = offlinePlayer(helper, new BlockPos(1, 1, 1));
        Player stranger = offlinePlayer(helper, new BlockPos(3, 1, 3));

        DeerFamiliarEntity deer = helper.spawn(OccultismEntities.DEER_FAMILIAR.get(), new BlockPos(2, 1, 2));
        deer.setNoAi(true);
        deer.setFamiliarOwner(owner);
        helper.assertTrue(deer.getFamiliarOwner() == null, "Owner should not be resolvable while offline");

        deer.interact(stranger, InteractionHand.MAIN_HAND, deer.position());

        helper.assertTrue(owner.getUUID().equals(deer.getOwnerId()),
                "Familiar of an offline owner was claimed by another player");
        helper.succeed();
    }

    /**
     * fix: prevent claiming familiars whose owner is offline (otherworld birds)
     */
    public static void offlineOwnedBirdCannotBeClaimed(GameTestHelper helper) {
        Player owner = offlinePlayer(helper, new BlockPos(1, 1, 1));
        Player stranger = offlinePlayer(helper, new BlockPos(3, 1, 3));

        DrikwingEntity original = helper.spawn(OccultismEntities.DRIKWING_FAMILIAR.get(), new BlockPos(2, 1, 2));
        original.setFamiliarOwner(owner);
        //reload into a new entity, so the owner reference only knows the UUID like after a restart
        DrikwingEntity bird = roundTrip(helper, original, OccultismEntities.DRIKWING_FAMILIAR.get());
        original.discard();
        bird.setNoAi(true);
        helper.getLevel().addFreshEntity(bird);
        helper.assertTrue(bird.getFamiliarOwner() == null, "Owner should not be resolvable while offline");

        bird.interact(stranger, InteractionHand.MAIN_HAND, bird.position());

        var ownerReference = bird.getOwnerReference();
        helper.assertTrue(ownerReference != null && owner.getUUID().equals(ownerReference.getUUID()),
                "Bird of an offline owner was claimed by another player");
        helper.succeed();
    }

    /**
     * fix: prevent claiming familiars whose owner is offline (capture with a familiar ring)
     */
    public static void offlineOwnedFamiliarCannotBeCaptured(GameTestHelper helper) {
        Player owner = offlinePlayer(helper, new BlockPos(1, 1, 1));
        Player stranger = offlinePlayer(helper, new BlockPos(3, 1, 3));

        DeerFamiliarEntity deer = helper.spawn(OccultismEntities.DEER_FAMILIAR.get(), new BlockPos(2, 1, 2));
        deer.setNoAi(true);
        deer.setFamiliarOwner(owner);

        ItemStack ring = new ItemStack(OccultismItems.FAMILIAR_RING.get());
        stranger.setItemInHand(InteractionHand.MAIN_HAND, ring);
        ring.interactLivingEntity(stranger, deer, InteractionHand.MAIN_HAND);

        helper.assertTrue(!deer.isRemoved(), "Familiar of an offline owner was captured by another player");
        helper.assertTrue(!ring.has(OccultismDataComponents.FAMILIAR_DATA), "Ring should still be empty");
        helper.succeed();
    }

    /**
     * fix: keep familiars sitting while their owner is offline
     */
    public static void familiarKeepsSittingWhileOwnerOffline(GameTestHelper helper) {
        floor(helper, 5);
        Player owner = offlinePlayer(helper, new BlockPos(0, 1, 0));

        DeerFamiliarEntity deer = helper.spawn(OccultismEntities.DEER_FAMILIAR.get(), new BlockPos(2, 1, 2));
        deer.setFamiliarOwner(owner);
        editData(deer, tag -> tag.putBoolean("isSitting", true));
        helper.assertTrue(deer.isSitting(), "Familiar should be sitting");

        helper.succeedWhen(() -> helper.assertTrue(isGoalRunning(deer, "SitGoal"),
                "Sit goal is not running while the owner is offline"));
    }

    /**
     * perf: cache the resolved familiar owner - the cached owner must follow logouts and owner changes.
     */
    public static void familiarOwnerCacheFollowsOwnerChanges(GameTestHelper helper) {
        DeerFamiliarEntity deer = helper.spawn(OccultismEntities.DEER_FAMILIAR.get(), new BlockPos(2, 1, 2));
        deer.setNoAi(true);

        ServerPlayer first = onlinePlayer(helper, new BlockPos(1, 1, 1));
        ServerPlayer second = onlinePlayer(helper, new BlockPos(3, 1, 3));
        try {
            deer.setFamiliarOwner(first);
            helper.assertTrue(deer.getFamiliarOwner() == first, "Online owner was not resolved");
            helper.assertTrue(deer.getFamiliarOwner() == first, "Cached owner was not returned");

            deer.setFamiliarOwner(second);
            helper.assertTrue(deer.getFamiliarOwner() == second, "Owner change was not picked up");

            logout(helper, second);
            helper.assertTrue(deer.getFamiliarOwner() == null, "Logged out owner is still returned");
            helper.assertTrue(deer.getOwnerId() != null, "Familiar lost its owner when the owner logged out");
        } finally {
            logout(helper, first);
            logout(helper, second);
        }
        helper.succeed();
    }

    // endregion ownership

    // region persistence

    /**
     * fix: stop resetting guardian and deer variants on load
     */
    public static void guardianVariantsSurviveSaveLoad(GameTestHelper helper) {
        GuardianFamiliarEntity guardian = helper.spawn(OccultismEntities.GUARDIAN_FAMILIAR.get(), new BlockPos(2, 1, 2));
        guardian.setNoAi(true);
        //tree, bird and tools
        editData(guardian, tag -> {
            tag.putByte("variants", (byte) 0b111);
            tag.putByte("lives", (byte) 3);
        });
        helper.assertTrue(guardian.hasTree() && guardian.hasBird() && guardian.hasTools(), "Variants were not applied");

        GuardianFamiliarEntity loaded = roundTrip(helper, guardian, OccultismEntities.GUARDIAN_FAMILIAR.get());
        helper.assertTrue(loaded.hasTree(), "Guardian lost its tree variant on load");
        helper.assertTrue(loaded.hasBird(), "Guardian lost its bird variant on load");
        helper.assertTrue(loaded.hasTools(), "Guardian lost its tools variant on load");
        helper.assertTrue(loaded.getLives() == 3, "Guardian lost its lives on load");
        helper.succeed();
    }

    /**
     * fix: stop resetting guardian and deer variants on load
     */
    public static void deerRedNoseSurvivesSaveLoad(GameTestHelper helper) {
        DeerFamiliarEntity deer = helper.spawn(OccultismEntities.DEER_FAMILIAR.get(), new BlockPos(2, 1, 2));
        deer.setNoAi(true);
        editData(deer, tag -> tag.putByte("variants", (byte) 1));
        helper.assertTrue(deer.hasRedNose(), "Red nose variant was not applied");

        DeerFamiliarEntity loaded = roundTrip(helper, deer, OccultismEntities.DEER_FAMILIAR.get());
        helper.assertTrue(loaded.hasRedNose(), "Deer lost its red nose variant on load");
        helper.succeed();
    }

    /**
     * fix: stop resetting guardian and deer variants on load - "also avoid NPEs in the guardian when the owner is offline"
     */
    public static void guardianWithOfflineOwnerDoesNotCrash(GameTestHelper helper) {
        Player owner = offlinePlayer(helper, new BlockPos(1, 1, 1));
        GuardianFamiliarEntity guardian = helper.spawn(OccultismEntities.GUARDIAN_FAMILIAR.get(), new BlockPos(2, 1, 2));
        guardian.setNoAi(true);
        guardian.setFamiliarOwner(owner);
        editData(guardian, tag -> tag.putByte("lives", (byte) GuardianFamiliarEntity.DEATHS_DOOR));

        helper.assertTrue(!guardian.getFamiliarEffects().iterator().hasNext(),
                "Guardian with an offline owner should not provide effects");
        helper.assertTrue(guardian.sacrifice(), "Guardian at deaths door should still be able to sacrifice itself");
        helper.assertTrue(guardian.getLives() == 0, "Sacrifice did not consume the last life");
        helper.succeed();
    }

    /**
     * fix: keep familiar settings defaults when deserializing (CompoundTag based)
     */
    public static void familiarSettingsKeepDefaultsWhenDeserializingNbt(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var deer = OccultismEntities.DEER_FAMILIAR.get();

        FamiliarSettingsData data = new FamiliarSettingsData();
        data.deserializeNBT(registries, new CompoundTag());
        helper.assertTrue(data.isFamiliarEnabled(deer), "Familiar missing in the saved settings should be enabled by default");
        helper.assertTrue(data.getEffectAmplifier(deer, MobEffects.SPEED) == 2,
                "Familiar missing in the saved settings should keep its default amplifier");

        //round trip a changed effect amplifier for a modded effect
        FamiliarSettingsData changed = new FamiliarSettingsData();
        changed.setFamiliarEnabled(deer, false);
        changed.setEffectAmplifier(deer, OccultismEffects.STEP_HEIGHT, (byte) 1);
        CompoundTag tag = changed.serializeNBT(registries);

        FamiliarSettingsData loaded = new FamiliarSettingsData();
        loaded.deserializeNBT(registries, tag);
        helper.assertTrue(!loaded.isFamiliarEnabled(deer), "Disabled familiar was enabled after loading");
        helper.assertTrue(loaded.getEffectAmplifier(deer, OccultismEffects.STEP_HEIGHT) == 1,
                "Effect amplifier was not restored after loading");
        helper.succeed();
    }

    /**
     * fix: keep familiar settings defaults when deserializing (ValueInput based, used by the attachment)
     */
    public static void familiarSettingsKeepDefaultsWhenDeserializingValueInput(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var deer = OccultismEntities.DEER_FAMILIAR.get();

        FamiliarSettingsData data = new FamiliarSettingsData();
        data.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, registries, new CompoundTag()));
        helper.assertTrue(data.isFamiliarEnabled(deer), "Familiar missing in the saved settings should be enabled by default");
        helper.assertTrue(data.getEffectAmplifier(deer, MobEffects.SPEED) == 2,
                "Familiar missing in the saved settings should keep its default amplifier");
        helper.assertTrue(data.getEffectAmplifier(OccultismEntities.GOAT_FAMILIAR.get(), MobEffects.SPEED) == -1,
                "Familiar without effects should report no amplifier");
        helper.succeed();
    }

    /**
     * fix: keep familiar settings defaults when deserializing - "match effect holders by registry key"
     */
    public static void familiarSettingsMatchEffectsByRegistryKey(GameTestHelper helper) {
        var deer = OccultismEntities.DEER_FAMILIAR.get();
        Holder<MobEffect> registryHolder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(OccultismEffects.STEP_HEIGHT.get());

        FamiliarSettingsData data = new FamiliarSettingsData();
        data.setEffectAmplifier(deer, registryHolder, (byte) 2);

        helper.assertTrue(data.getEffectAmplifier(deer, OccultismEffects.STEP_HEIGHT) == 2,
                "Amplifier set via the registry holder is not visible via the deferred holder");
        helper.assertTrue(data.getEffectAmplifier(deer, registryHolder) == 2,
                "Amplifier set via the registry holder is not visible via the registry holder");
        //unknown familiar types are ignored
        data.setFamiliarEnabled(EntityType.ZOMBIE, false);
        helper.assertTrue(!data.isFamiliarEnabled(EntityType.ZOMBIE), "Unknown familiar types should not be enabled");
        helper.succeed();
    }

    /**
     * fix: require a blacksmith passive repair delay of at least 1
     */
    public static void blacksmithRepairDelayRejectsZero(GameTestHelper helper) {
        var spec = Occultism.SERVER_CONFIG.familiar.blacksmithFamiliarPassiveRepairDelay.getSpec();
        helper.assertTrue(!spec.test(0), "A passive repair delay of 0 must be rejected (modulo by zero)");
        helper.assertTrue(spec.test(1), "A passive repair delay of 1 must be accepted");
        helper.succeed();
    }

    // endregion persistence

    // region dupes and items

    private static void assertNoEquipmentDupeOnDeath(GameTestHelper helper, Mob familiar, EntityType<? extends Mob> type) {
        familiar.setNoAi(true);
        familiar.setItemSlot(EquipmentSlot.MAINHAND, sharpSword(helper));
        familiar.setGuaranteedDrop(EquipmentSlot.MAINHAND);

        familiar.kill(helper.getLevel());

        helper.runAfterDelay(2, () -> {
            List<ItemEntity> items = helper.getEntities(EntityType.ITEM);
            int swords = 0;
            ItemStack shard = ItemStack.EMPTY;
            for (ItemEntity item : items) {
                if (item.getItem().is(Items.DIAMOND_SWORD))
                    swords += item.getItem().getCount();
                if (item.getItem().is(OccultismItems.SOUL_SHARD_ITEM.get()))
                    shard = item.getItem();
            }
            helper.assertTrue(!shard.isEmpty(), "Dying familiar did not drop a soul shard");
            helper.assertTrue(swords == 0, "Familiar equipment was dropped although it is stored in the soul shard (dupe)");

            var entityData = shard.get(DataComponents.ENTITY_DATA);
            helper.assertTrue(entityData != null, "Soul shard has no entity data");
            Mob restored = type.create(helper.getLevel(), EntitySpawnReason.LOAD);
            helper.assertTrue(restored != null, "Could not create familiar");
            entityData.loadInto(restored);
            helper.assertTrue(isSharpSword(helper, restored.getMainHandItem()),
                    "Soul shard lost the familiar equipment");
            helper.succeed();
        });
    }

    /**
     * fix: prevent familiar equipment dupes on soul shard capture
     */
    public static void familiarEquipmentNotDupedOnDeath(GameTestHelper helper) {
        floor(helper, 5);
        DeerFamiliarEntity deer = helper.spawn(OccultismEntities.DEER_FAMILIAR.get(), new BlockPos(2, 1, 2));
        assertNoEquipmentDupeOnDeath(helper, deer, OccultismEntities.DEER_FAMILIAR.get());
    }

    /**
     * fix: prevent familiar equipment dupes on soul shard capture (otherworld birds)
     */
    public static void birdEquipmentNotDupedOnDeath(GameTestHelper helper) {
        floor(helper, 5);
        DrikwingEntity bird = helper.spawn(OccultismEntities.DRIKWING_FAMILIAR.get(), new BlockPos(2, 1, 2));
        assertNoEquipmentDupeOnDeath(helper, bird, OccultismEntities.DRIKWING_FAMILIAR.get());
    }

    /**
     * fix: restrict giving blocks to the greedy familiar to its owner
     */
    public static void greedyFamiliarOnlyAcceptsBlocksFromOwner(GameTestHelper helper) {
        GreedyFamiliarEntity greedy = helper.spawn(OccultismEntities.GREEDY_FAMILIAR.get(), new BlockPos(2, 1, 2));
        greedy.setNoAi(true);
        ServerPlayer owner = onlinePlayer(helper, new BlockPos(1, 1, 1));
        try {
            greedy.setFamiliarOwner(owner);
            greedy.blacksmithUpgrade();

            Player stranger = offlinePlayer(helper, new BlockPos(3, 1, 3));
            stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 2));
            greedy.interact(stranger, InteractionHand.MAIN_HAND, greedy.position());
            helper.assertTrue(greedy.getOffhandItem().isEmpty(), "Greedy familiar accepted a block from a stranger");
            helper.assertTrue(stranger.getMainHandItem().getCount() == 2, "Stranger's block was consumed");

            ItemStack named = new ItemStack(Items.STONE, 2);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Named Stone"));
            owner.setItemInHand(InteractionHand.MAIN_HAND, named);
            greedy.interact(owner, InteractionHand.MAIN_HAND, greedy.position());
            helper.assertTrue(greedy.getOffhandItem().is(Items.STONE), "Greedy familiar did not accept the owner's block");
            helper.assertTrue(greedy.getOffhandItem().has(DataComponents.CUSTOM_NAME),
                    "Greedy familiar dropped the components of the given block");
            helper.assertTrue(owner.getMainHandItem().getCount() == 1, "Owner's block was not consumed exactly once");

            //strangers may not take the block either
            stranger.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            greedy.interact(stranger, InteractionHand.MAIN_HAND, greedy.position());
            helper.assertTrue(greedy.getOffhandItem().is(Items.STONE), "Stranger took the greedy familiar's block");
        } finally {
            logout(helper, owner);
        }
        helper.succeed();
    }

    private static int ironCount(BlacksmithFamiliarEntity blacksmith) {
        return save(blacksmith).getIntOr("ironCount", -1);
    }

    /**
     * fix: count blacksmith iron/iesnium before shrinking the stack
     */
    public static void blacksmithCountsLastIronCorrectly(GameTestHelper helper) {
        BlacksmithFamiliarEntity blacksmith = helper.spawn(OccultismEntities.BLACKSMITH_FAMILIAR.get(), new BlockPos(2, 1, 2));
        blacksmith.setNoAi(true);
        ServerPlayer owner = onlinePlayer(helper, new BlockPos(1, 1, 1));
        try {
            blacksmith.setFamiliarOwner(owner);
            helper.assertTrue(ironCount(blacksmith) == 0, "Blacksmith should start without iron");

            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 1));
            blacksmith.interact(owner, InteractionHand.MAIN_HAND, blacksmith.position());
            helper.assertTrue(owner.getMainHandItem().isEmpty(), "Ingot was not consumed");
            helper.assertTrue(ironCount(blacksmith) == 1,
                    "Feeding the last iron ingot should add 1 iron but added " + ironCount(blacksmith));

            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_BLOCK, 1));
            blacksmith.interact(owner, InteractionHand.MAIN_HAND, blacksmith.position());
            helper.assertTrue(ironCount(blacksmith) == 10,
                    "Feeding the last iron block should add 9 iron, total is " + ironCount(blacksmith));
        } finally {
            logout(helper, owner);
        }
        helper.succeed();
    }

    /**
     * fix: preserve mob armor and hand items when capturing entities (soul gem)
     */
    public static void soulGemPreservesMobEquipment(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        zombie.setNoAi(true);
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        helmet.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 2);
        zombie.setItemSlot(EquipmentSlot.HEAD, helmet);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, sharpSword(helper));

        Player player = offlinePlayer(helper, new BlockPos(1, 1, 1));
        ItemStack gem = new ItemStack(OccultismItems.SOUL_GEM_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, gem);
        gem.interactLivingEntity(player, zombie, InteractionHand.MAIN_HAND);

        var entityData = player.getMainHandItem().get(DataComponents.ENTITY_DATA);
        helper.assertTrue(entityData != null, "Soul gem did not capture the zombie");
        Zombie restored = EntityType.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.LOAD);
        helper.assertTrue(restored != null, "Could not create zombie");
        entityData.loadInto(restored);
        helper.assertTrue(restored.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),
                "Captured zombie lost its armor");
        helper.assertTrue(isSharpSword(helper, restored.getMainHandItem()), "Captured zombie lost its hand item");
        helper.succeed();
    }

    /**
     * fix: preserve mob armor and hand items when capturing entities (familiar ring capture and release)
     */
    public static void familiarRingPreservesEquipment(GameTestHelper helper) {
        floor(helper, 5);
        DeerFamiliarEntity deer = helper.spawn(OccultismEntities.DEER_FAMILIAR.get(), new BlockPos(2, 1, 2));
        deer.setNoAi(true);
        deer.setItemSlot(EquipmentSlot.MAINHAND, sharpSword(helper));

        Player player = offlinePlayer(helper, new BlockPos(2, 1, 2));
        ItemStack ring = new ItemStack(OccultismItems.FAMILIAR_RING.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, ring);
        ring.interactLivingEntity(player, deer, InteractionHand.MAIN_HAND);
        helper.assertTrue(deer.isRemoved(), "Familiar was not captured");
        //load the familiar from the saved ring data, see familiarRingReleaseRightAfterCaptureKeepsFamiliar for the cached case
        reloadFamiliarData(ring);

        List<IFamiliar> familiars = FamiliarCurio.getFamiliar(ring, helper.getLevel());
        helper.assertTrue(familiars != null && familiars.size() == 1, "Ring does not contain the captured familiar");

        var curio = FamiliarCurio.getCurio(ring);
        helper.assertTrue(curio != null, "Ring has no curio capability");
        helper.assertTrue(curio.releaseFamiliar(player, helper.getLevel(), familiars.getFirst()), "Familiar was not released");

        List<DeerFamiliarEntity> released = helper.getEntities(OccultismEntities.DEER_FAMILIAR.get());
        helper.assertTrue(released.size() == 1, "Expected exactly one released familiar but found " + released.size());
        helper.assertTrue(isSharpSword(helper, released.getFirst().getMainHandItem()),
                "Released familiar lost its enchanted equipment");
        List<IFamiliar> remaining = FamiliarCurio.getFamiliar(ring, helper.getLevel());
        helper.assertTrue(remaining == null || remaining.isEmpty(), "Ring still contains the released familiar");
        released.getFirst().discard();
        helper.succeed();
    }

    /**
     * perf: cache familiars loaded from familiar curios - releasing a familiar right after capturing it must not lose it.
     * <p>
     * Capturing must not leave the captured (and then discarded) entity instance cached for the new ring data, as
     * releasing it would save that discarded instance, which {@link Entity#saveAsPassenger} refuses, so nothing would be
     * spawned and the familiar would be removed from the ring.
     */
    public static void familiarRingReleaseRightAfterCaptureKeepsFamiliar(GameTestHelper helper) {
        floor(helper, 5);
        DeerFamiliarEntity deer = helper.spawn(OccultismEntities.DEER_FAMILIAR.get(), new BlockPos(2, 1, 2));
        deer.setNoAi(true);

        Player player = offlinePlayer(helper, new BlockPos(2, 1, 2));
        ItemStack ring = new ItemStack(OccultismItems.FAMILIAR_RING.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, ring);
        ring.interactLivingEntity(player, deer, InteractionHand.MAIN_HAND);
        helper.assertTrue(deer.isRemoved(), "Familiar was not captured");

        List<IFamiliar> familiars = FamiliarCurio.getFamiliar(ring, helper.getLevel());
        helper.assertTrue(familiars != null && familiars.size() == 1, "Ring does not contain the captured familiar");
        var curio = FamiliarCurio.getCurio(ring);
        helper.assertTrue(curio != null, "Ring has no curio capability");
        curio.releaseFamiliar(player, helper.getLevel(), familiars.getFirst());

        List<DeerFamiliarEntity> released = helper.getEntities(OccultismEntities.DEER_FAMILIAR.get());
        helper.assertTrue(released.size() == 1,
                "Familiar released right after capture was lost, released familiars: " + released.size());
        released.getFirst().discard();
        helper.succeed();
    }

    /**
     * perf: cache familiars loaded from familiar curios - changes to the cached familiars (guardian lives) must be written back
     */
    public static void familiarCurioWritesBackGuardianLives(GameTestHelper helper) {
        GuardianFamiliarEntity guardian = helper.spawn(OccultismEntities.GUARDIAN_FAMILIAR.get(), new BlockPos(2, 1, 2));
        guardian.setNoAi(true);
        editData(guardian, tag -> tag.putByte("lives", (byte) 3));

        Player wearer = offlinePlayer(helper, new BlockPos(2, 1, 2));
        ItemStack ring = new ItemStack(OccultismItems.FAMILIAR_RING.get());
        wearer.setItemInHand(InteractionHand.MAIN_HAND, ring);
        ring.interactLivingEntity(wearer, guardian, InteractionHand.MAIN_HAND);
        helper.assertTrue(guardian.isRemoved(), "Guardian was not captured");
        //load the familiar from the saved ring data, see familiarRingReleaseRightAfterCaptureKeepsFamiliar for the cached case
        reloadFamiliarData(ring);

        List<IFamiliar> familiars = FamiliarCurio.getFamiliar(ring, helper.getLevel());
        helper.assertTrue(familiars != null && familiars.size() == 1, "Ring does not contain the captured guardian");
        GuardianFamiliarEntity stored = (GuardianFamiliarEntity) familiars.getFirst();
        helper.assertTrue(stored.getLives() == 3, "Stored guardian should have 3 lives but has " + stored.getLives());
        stored.sacrifice();

        var curio = FamiliarCurio.getCurio(ring);
        helper.assertTrue(curio != null, "Ring has no curio capability");
        curio.curioTick(new SlotContext("ring", wearer, 0, false, true));

        CompoundTag data = ring.get(OccultismDataComponents.FAMILIAR_DATA).copyTag();
        byte savedLives = data.getListOrEmpty("familiars").getCompoundOrEmpty(0).getByteOr("lives", (byte) -1);
        helper.assertTrue(savedLives == 2, "Lost guardian life was not written back to the ring, saved lives: " + savedLives);

        List<IFamiliar> reloaded = FamiliarCurio.getFamiliar(ring, helper.getLevel());
        helper.assertTrue(reloaded != null && ((GuardianFamiliarEntity) reloaded.getFirst()).getLives() == 2,
                "Guardian in the ring did not keep the lost life");
        helper.succeed();
    }

    // endregion dupes and items

    // region abilities

    /**
     * perf: only re-apply familiar effects when needed - effects must still be applied to a nearby owner.
     */
    public static void familiarAppliesEffectsToOwner(GameTestHelper helper) {
        DeerFamiliarEntity deer = helper.spawn(OccultismEntities.DEER_FAMILIAR.get(), new BlockPos(2, 1, 2));
        deer.setNoAi(true);
        ServerPlayer owner = onlinePlayer(helper, new BlockPos(1, 1, 1));
        deer.setFamiliarOwner(owner);

        helper.succeedWhen(() -> {
            var speed = owner.getEffect(MobEffects.SPEED);
            helper.assertTrue(speed != null && speed.getAmplifier() == 0, "Owner did not receive speed from the deer familiar");
            logout(helper, owner);
        });
    }

    private static HeadlessFamiliarEntity weaknessHeadless(GameTestHelper helper, Player owner) {
        HeadlessFamiliarEntity headless = helper.spawn(OccultismEntities.HEADLESS_FAMILIAR.get(), new BlockPos(2, 1, 2));
        headless.setNoAi(true);
        headless.setFamiliarOwner(owner);
        headless.blacksmithUpgrade();
        headless.setHeadType(EntityType.PLAYER);
        return headless;
    }

    /**
     * fix: respect pvp in the headless familiar weakness aura - control: with pvp enabled other players are weakened.
     */
    public static void headlessWeakensPlayersWithPvp(GameTestHelper helper) {
        ServerPlayer owner = onlinePlayer(helper, new BlockPos(1, 1, 1));
        ServerPlayer target = onlinePlayer(helper, new BlockPos(3, 1, 3));
        weaknessHeadless(helper, owner);

        helper.succeedWhen(() -> {
            helper.assertTrue(target.hasEffect(MobEffects.WEAKNESS), "Other player was not weakened with pvp enabled");
            helper.assertTrue(!owner.hasEffect(MobEffects.WEAKNESS), "Owner was weakened");
            logout(helper, owner);
            logout(helper, target);
        });
    }

    /**
     * fix: respect pvp in the headless familiar weakness aura - runs in an environment with the pvp game rule disabled.
     */
    public static void headlessDoesNotWeakenPlayersWithoutPvp(GameTestHelper helper) {
        ServerPlayer owner = onlinePlayer(helper, new BlockPos(1, 1, 1));
        ServerPlayer target = onlinePlayer(helper, new BlockPos(3, 1, 3));
        weaknessHeadless(helper, owner);

        helper.onEachTick(() -> {
            if (target.hasEffect(MobEffects.WEAKNESS)) {
                logout(helper, owner);
                logout(helper, target);
                helper.fail("Headless familiar weakened a player although pvp is disabled");
            }
        });
        helper.runAfterDelay(40, () -> {
            logout(helper, owner);
            logout(helper, target);
            helper.succeed();
        });
    }

    /**
     * fix: respect pvp in the headless familiar weakness aura - an offline owner cannot harm players.
     */
    public static void headlessWithOfflineOwnerDoesNotWeakenPlayers(GameTestHelper helper) {
        Player owner = offlinePlayer(helper, new BlockPos(1, 1, 1));
        ServerPlayer target = onlinePlayer(helper, new BlockPos(3, 1, 3));
        weaknessHeadless(helper, owner);

        helper.onEachTick(() -> {
            if (target.hasEffect(MobEffects.WEAKNESS)) {
                logout(helper, target);
                helper.fail("Headless familiar of an offline owner weakened a player");
            }
        });
        helper.runAfterDelay(40, () -> {
            logout(helper, target);
            helper.succeed();
        });
    }

    private static ShubNiggurathFamiliarEntity upgradedShub(GameTestHelper helper, BlockPos pos) {
        ShubNiggurathFamiliarEntity shub = helper.spawn(OccultismEntities.SHUB_NIGGURATH_FAMILIAR.get(), pos);
        shub.setNoAi(true);
        //shortest spawn timer
        shub.blacksmithUpgrade();
        shub.iesniumUpgrade();
        return shub;
    }

    /**
     * The first spawn attempt of a new shub-niggurath happens after its initial spawn timer of 10 seconds,
     * later attempts use the (upgraded) timer.
     */
    private static final int SHUB_FIRST_SPAWN_ATTEMPT = 20 * 10 + 2;

    /**
     * perf: cap shub-niggurath spawns and only spawn with enemies around - no enemies, no spawns.
     */
    public static void shubNiggurathDoesNotSpawnWithoutEnemies(GameTestHelper helper) {
        floor(helper, 17);
        ShubNiggurathFamiliarEntity shub = upgradedShub(helper, new BlockPos(8, 1, 8));
        ServerPlayer owner = onlinePlayer(helper, new BlockPos(8, 1, 5));
        shub.setFamiliarOwner(owner);

        helper.onEachTick(() -> {
            if (!helper.getEntities(OccultismEntities.SHUB_NIGGURATH_SPAWN.get()).isEmpty()) {
                logout(helper, owner);
                helper.fail("Shub-niggurath created a spawn without any enemies around");
            }
        });
        helper.runAfterDelay(SHUB_FIRST_SPAWN_ATTEMPT + 30, () -> {
            logout(helper, owner);
            helper.succeed();
        });
    }

    /**
     * Makes a pig the owner's attacker. A pig neither burns in daylight nor despawns, and it is invulnerable so exploding
     * spawns cannot kill it.
     */
    private static Pig ownerEnemy(GameTestHelper helper, ServerPlayer owner, BlockPos pos) {
        Pig pig = helper.spawn(EntityType.PIG, pos);
        pig.setNoAi(true);
        pig.setInvulnerable(true);
        owner.setLastHurtByMob(pig);
        return pig;
    }

    /**
     * perf: cap shub-niggurath spawns and only spawn with enemies around - control: spawns are created with enemies around.
     */
    public static void shubNiggurathSpawnsWithEnemies(GameTestHelper helper) {
        floor(helper, 17);
        ShubNiggurathFamiliarEntity shub = upgradedShub(helper, new BlockPos(8, 1, 8));
        ServerPlayer owner = onlinePlayer(helper, new BlockPos(8, 1, 5));
        shub.setFamiliarOwner(owner);
        ownerEnemy(helper, owner, new BlockPos(12, 1, 8));

        helper.succeedWhen(() -> {
            helper.assertTrue(!helper.getEntities(OccultismEntities.SHUB_NIGGURATH_SPAWN.get()).isEmpty(),
                    "Shub-niggurath did not create a spawn with an enemy around");
            logout(helper, owner);
        });
    }

    /**
     * perf: cap shub-niggurath spawns and only spawn with enemies around - at most 6 live spawns per creator.
     */
    public static void shubNiggurathSpawnsAreCapped(GameTestHelper helper) {
        floor(helper, 17);
        ShubNiggurathFamiliarEntity shub = upgradedShub(helper, new BlockPos(8, 1, 8));
        ServerPlayer owner = onlinePlayer(helper, new BlockPos(8, 1, 5));
        shub.setFamiliarOwner(owner);
        ownerEnemy(helper, owner, new BlockPos(12, 1, 8));

        //six existing spawns of this shub-niggurath, far enough away from the enemy so they do not explode
        ServerLevel level = helper.getLevel();
        for (int i = 0; i < 6; i++) {
            ShubNiggurathSpawnEntity spawn = new ShubNiggurathSpawnEntity(level, shub);
            Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(1, 1, 1 + i)));
            spawn.snapTo(pos.x, pos.y, pos.z, 0, 0);
            spawn.setNoAi(true);
            level.addFreshEntity(spawn);
        }

        helper.onEachTick(() -> {
            int count = helper.getEntities(OccultismEntities.SHUB_NIGGURATH_SPAWN.get()).size();
            if (count > 6) {
                logout(helper, owner);
                helper.fail("Shub-niggurath exceeded the spawn cap, spawns: " + count);
            }
        });
        //first attempt plus a few attempts with the upgraded timer
        helper.runAfterDelay(SHUB_FIRST_SPAWN_ATTEMPT + 50, () -> {
            helper.assertTrue(helper.getEntities(OccultismEntities.SHUB_NIGGURATH_SPAWN.get()).size() == 6,
                    "The existing spawns should still be around");
            logout(helper, owner);
            helper.succeed();
        });
    }

    /**
     * fix: bound the beaver tree search and forget far away trees - a 15 log tree is not "too big".
     */
    public static void beaverChopsFifteenLogTree(GameTestHelper helper) {
        floor(helper, 17);
        //a 3 wide, 5 high wall of logs: 15 logs. The old search queued logs multiple times and gave up on it.
        for (int x = 6; x <= 8; x++)
            for (int y = 1; y <= 5; y++)
                helper.setBlock(x, y, 8, Blocks.OAK_LOG);

        ServerPlayer owner = onlinePlayer(helper, new BlockPos(2, 1, 2));
        BeaverFamiliarEntity beaver = helper.spawn(OccultismEntities.BEAVER_FAMILIAR.get(), new BlockPos(6, 1, 7));
        beaver.setFamiliarOwner(owner);
        beaver.setTreeTarget(helper.absolutePos(new BlockPos(6, 1, 8)));

        helper.succeedWhen(() -> {
            for (int x = 6; x <= 8; x++)
                for (int y = 1; y <= 5; y++) {
                    BlockState state = helper.getBlockState(new BlockPos(x, y, 8));
                    helper.assertTrue(state.isAir(), "Beaver did not chop the log at " + x + "," + y);
                }
            logout(helper, owner);
        });
    }

    /**
     * fix: bound the iesnium golem void rescue - the golem is moved to a free spot above the spawn in one go.
     */
    public static void iesniumGolemIsRescuedFromVoid(GameTestHelper helper) {
        IesniumGolemEntity golem = helper.spawn(OccultismEntities.IESNIUM_GOLEM.get(), new BlockPos(2, 1, 2));
        golem.setNoAi(true);
        ServerLevel level = helper.getLevel();
        BlockPos spawnPos = level.getRespawnData().pos();

        golem.isInvulnerableTo(level, level.damageSources().fellOutOfWorld());

        BlockPos golemPos = golem.blockPosition();
        golem.discard();
        helper.assertTrue(golemPos.getX() == spawnPos.getX() && golemPos.getZ() == spawnPos.getZ(),
                "Golem was not moved above the spawn: " + golemPos + " spawn " + spawnPos);
        helper.assertTrue(golemPos.getY() >= spawnPos.getY() && golemPos.getY() <= spawnPos.getY() + 256,
                "Golem rescue height is out of bounds: " + golemPos + " spawn " + spawnPos);
        for (int i = 1; i <= 3; i++) {
            BlockState state = level.getBlockState(golemPos.below(i));
            helper.assertTrue(state.getBlock().isPossibleToRespawnInThis(state),
                    "Golem was rescued into a non free spot at " + golemPos);
        }
        helper.succeed();
    }

    // endregion abilities
}
