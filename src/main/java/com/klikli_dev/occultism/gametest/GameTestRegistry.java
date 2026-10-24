// SPDX-FileCopyrightText: 2026 klikli-dev
//
// SPDX-License-Identifier: MIT

package com.klikli_dev.occultism.gametest;

import com.klikli_dev.occultism.Occultism;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Consumer;

public class GameTestRegistry {

    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
            DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, Occultism.MODID);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MAP_ITEM_RESOURCE_HANDLER_ABORTED_PROBES =
            TEST_FUNCTIONS.register("map_item_resource_handler_aborted_probes", () -> MapItemResourceHandlerGameTests::abortedProbesDoNotChangeState);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MAP_ITEM_RESOURCE_HANDLER_COMMITTED_EXTRACTION =
            TEST_FUNCTIONS.register("map_item_resource_handler_committed_extraction", () -> MapItemResourceHandlerGameTests::committedExtractionPersists);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MAP_ITEM_RESOURCE_HANDLER_NESTED_ABORT =
            TEST_FUNCTIONS.register("map_item_resource_handler_nested_abort", () -> MapItemResourceHandlerGameTests::nestedCommitThenRootAbortRollsBack);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MAP_ITEM_RESOURCE_HANDLER_NESTED_COMMIT =
            TEST_FUNCTIONS.register("map_item_resource_handler_nested_commit", () -> MapItemResourceHandlerGameTests::nestedCommitThenRootCommitPersists);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MAP_ITEM_RESOURCE_HANDLER_SLOT_REUSE_ROLLBACK =
            TEST_FUNCTIONS.register("map_item_resource_handler_slot_reuse_rollback", () -> MapItemResourceHandlerGameTests::abortedSlotReuseRestoresSlotAssignments);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MAP_ITEM_RESOURCE_HANDLER_UNDO_JOURNAL_DRAINS =
            TEST_FUNCTIONS.register("map_item_resource_handler_undo_journal_drains", () -> MapItemResourceHandlerGameTests::undoJournalDrainsAfterClosedTransactions);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MAP_ITEM_RESOURCE_HANDLER_RECURSIVE_COMMIT_NOTIFICATION =
            TEST_FUNCTIONS.register("map_item_resource_handler_recursive_commit_notification", () -> MapItemResourceHandlerGameTests::commitNotificationsCoverChangesTriggeredByNotifications);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CURIOS_UTIL_STORAGE_REMOTE_FIRST_SLOT =
            TEST_FUNCTIONS.register("curios_util_storage_remote_first_slot", () -> CuriosUtilGameTests::storageRemoteInFirstInventorySlotIsFound);

    // region Storage controller

    // endregion Storage controller

    // region Satchel and wormhole tablet

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SATCHEL_WRITES_BACK_ONLY_WHILE_VALID =
            TEST_FUNCTIONS.register("satchel_writes_back_only_while_valid", () -> SatchelTabletGameTests::satchelWritesBackOnlyWhileValid);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TABLET_WRITES_BACK_ONLY_WHILE_VALID =
            TEST_FUNCTIONS.register("tablet_writes_back_only_while_valid", () -> SatchelTabletGameTests::tabletWritesBackOnlyWhileValid);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SATCHEL_REPLACED_BY_COPY_INVALIDATES_MENU =
            TEST_FUNCTIONS.register("satchel_replaced_by_copy_invalidates_menu", () -> SatchelTabletGameTests::satchelReplacedByCopyInvalidatesMenu);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TABLET_REPLACED_BY_COPY_INVALIDATES_MENU =
            TEST_FUNCTIONS.register("tablet_replaced_by_copy_invalidates_menu", () -> SatchelTabletGameTests::tabletReplacedByCopyInvalidatesMenu);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SATCHEL_SOURCE_SLOT_IS_LOCKED =
            TEST_FUNCTIONS.register("satchel_source_slot_is_locked", () -> SatchelTabletGameTests::satchelSourceSlotIsLocked);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TABLET_SOURCE_SLOT_IS_LOCKED =
            TEST_FUNCTIONS.register("tablet_source_slot_is_locked", () -> SatchelTabletGameTests::tabletSourceSlotIsLocked);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SATCHEL_HOTBAR_KEY_MOVE_INVALIDATES_WITHOUT_DUPE =
            TEST_FUNCTIONS.register("satchel_hotbar_key_move_invalidates_without_dupe", () -> SatchelTabletGameTests::satchelHotbarKeyMoveInvalidatesWithoutDupe);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SATCHEL_OPENED_FROM_OFFHAND_VALIDATES_OFFHAND =
            TEST_FUNCTIONS.register("satchel_opened_from_offhand_validates_offhand", () -> SatchelTabletGameTests::satchelOpenedFromOffhandValidatesOffhand);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TABLET_OPENED_FROM_OFFHAND_VALIDATES_OFFHAND =
            TEST_FUNCTIONS.register("tablet_opened_from_offhand_validates_offhand", () -> SatchelTabletGameTests::tabletOpenedFromOffhandValidatesOffhand);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ROTATE_TABLET_CONTENTS_ON_SERVER =
            TEST_FUNCTIONS.register("rotate_tablet_contents_on_server", () -> SatchelTabletGameTests::rotateTabletContentsOnServer);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ROTATE_TABLET_CONTENTS_IGNORED_WHEN_NOT_APPLICABLE =
            TEST_FUNCTIONS.register("rotate_tablet_contents_ignored_when_not_applicable", () -> SatchelTabletGameTests::rotateTabletContentsIgnoredWhenNotApplicable);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TELEPORT_SETS_BACK_COMPASS =
            TEST_FUNCTIONS.register("teleport_sets_back_compass", () -> SatchelTabletGameTests::teleportSetsBackCompass);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TELEPORT_WITH_BACK_COMPASS_KEEPS_ITS_TARGET =
            TEST_FUNCTIONS.register("teleport_with_back_compass_keeps_its_target", () -> SatchelTabletGameTests::teleportWithBackCompassKeepsItsTarget);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RANDOM_TELEPORT_AT_WORLD_BORDER_DOES_NOT_THROW =
            TEST_FUNCTIONS.register("random_teleport_at_world_border_does_not_throw", () -> SatchelTabletGameTests::randomTeleportAtWorldBorderDoesNotThrow);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RANDOM_TELEPORT_FINDS_TARGET_IN_UNLOADED_AREA =
            TEST_FUNCTIONS.register("random_teleport_finds_target_in_unloaded_area", () -> SatchelTabletGameTests::randomTeleportFindsTargetInUnloadedArea);

    // endregion Satchel and wormhole tablet

    // region Rituals
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RITUAL_FULL_RUN_COMPLETES =
            TEST_FUNCTIONS.register("ritual_full_run_completes", () -> RitualGameTests::fullRitualCompletes);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RITUAL_ACTIVATION_ITEM_NOT_CONSUMED_WHEN_INSERT_FAILS =
            TEST_FUNCTIONS.register("ritual_activation_item_not_consumed_when_insert_fails", () -> RitualGameTests::activationItemNotConsumedWhenInsertFails);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RITUAL_HANDLER_INSERT_STARTS_ON_COMMIT =
            TEST_FUNCTIONS.register("ritual_handler_insert_starts_on_commit", () -> RitualGameTests::handlerInsertStartsRitualOnlyOnCommit);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RITUAL_BROKEN_PENTACLE_INTERRUPTS =
            TEST_FUNCTIONS.register("ritual_broken_pentacle_interrupts", () -> RitualGameTests::brokenPentacleInterruptsRitual);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RITUAL_ONE_FLAME_OF_AUTOMATION =
            TEST_FUNCTIONS.register("ritual_one_flame_of_automation", () -> RitualGameTests::oneFlameOfAutomationPerRitual);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RITUAL_ADVANCEMENT_RITUAL_ID =
            TEST_FUNCTIONS.register("ritual_advancement_ritual_id", () -> RitualGameTests::advancementRitualIdMatchesRecipe);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RITUAL_INSTANCE_CACHED =
            TEST_FUNCTIONS.register("ritual_instance_cached", () -> RitualGameTests::ritualInstanceIsCachedPerRecipe);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RITUAL_LOAD_NETWORK_CLEARS_STALE_STATE =
            TEST_FUNCTIONS.register("ritual_load_network_clears_stale_state", () -> RitualGameTests::loadNetworkClearsStaleRitualState);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RITUAL_LOAD_MISSING_RECIPE =
            TEST_FUNCTIONS.register("ritual_load_missing_recipe", () -> RitualGameTests::loadWithMissingRitualRecipe);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RITUAL_LISTENERS_UNREGISTERED_ON_CHUNK_UNLOAD =
            TEST_FUNCTIONS.register("ritual_listeners_unregistered_on_chunk_unload", () -> RitualGameTests::listenersUnregisteredOnChunkUnload);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RITUAL_EMPTY_ENTITY_TAG_PREVIEW =
            TEST_FUNCTIONS.register("ritual_empty_entity_tag_preview", () -> RitualGameTests::emptyEntityTagPreviewReturnsNull);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RITUAL_PREVIEWED_PENTACLE_VALIDATED =
            TEST_FUNCTIONS.register("ritual_previewed_pentacle_validated", () -> RitualGameTests::previewedPentacleIsValidated);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RITUAL_BOWL_NETWORK_SYNC_KEEPS_INVENTORY =
            TEST_FUNCTIONS.register("ritual_bowl_network_sync_keeps_inventory", () -> RitualGameTests::bowlNetworkSyncKeepsInventory);
    // endregion Rituals

    // region Dimensional mineshaft and battlefield

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MINESHAFT_MINES_WITH_AUTOMATION_MINER =
            TEST_FUNCTIONS.register("mineshaft_mines_with_automation_miner", () -> MineshaftBattlefieldGameTests::mineshaftMinesWithMinerInsertedByAutomation);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MINESHAFT_KEEPS_MINER_WHEN_OUTPUT_FULL =
            TEST_FUNCTIONS.register("mineshaft_keeps_miner_when_output_full", () -> MineshaftBattlefieldGameTests::mineshaftKeepsMinerWhenOutputIsFull);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MINESHAFT_MOVES_MINER_TO_OUTPUT_BEFORE_BREAKING =
            TEST_FUNCTIONS.register("mineshaft_moves_miner_to_output_before_breaking", () -> MineshaftBattlefieldGameTests::mineshaftMovesMinerToOutputBeforeBreaking);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MINESHAFT_BREAK_DROPS_INVENTORY_ONCE =
            TEST_FUNCTIONS.register("mineshaft_break_drops_inventory_once", () -> MineshaftBattlefieldGameTests::mineshaftBreakDropsInventoryOnceAndClearsIt);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MINESHAFT_MENU_CLOSES_WHEN_BLOCK_REMOVED =
            TEST_FUNCTIONS.register("mineshaft_menu_closes_when_block_removed", () -> MineshaftBattlefieldGameTests::mineshaftMenuClosesWhenBlockRemoved);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MINESHAFT_SHIFT_CLICK_DOES_NOT_MERGE_INTO_OUTPUT =
            TEST_FUNCTIONS.register("mineshaft_shift_click_does_not_merge_into_output", () -> MineshaftBattlefieldGameTests::mineshaftShiftClickDoesNotMergeIntoOutput);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MINESHAFT_UPDATE_TAG_ONLY_NETWORK_DATA =
            TEST_FUNCTIONS.register("mineshaft_update_tag_only_network_data", () -> MineshaftBattlefieldGameTests::mineshaftUpdateTagOnlyContainsNetworkData);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MINER_SPIRIT_MAX_STACK_SIZE_DOES_NOT_MUTATE =
            TEST_FUNCTIONS.register("miner_spirit_max_stack_size_does_not_mutate", () -> MineshaftBattlefieldGameTests::minerSpiritMaxStackSizeDoesNotMutateComponents);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BATTLEFIELD_INPUT_CHANGES_RESTART_FIGHT =
            TEST_FUNCTIONS.register("battlefield_input_changes_restart_fight", () -> MineshaftBattlefieldGameTests::battlefieldInputChangesThroughHandlerRestartFight);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BATTLEFIELD_FIGHT_PRODUCES_LOOT =
            TEST_FUNCTIONS.register("battlefield_fight_produces_loot", () -> MineshaftBattlefieldGameTests::battlefieldFightProducesLoot);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BATTLEFIELD_DEFEAT_COLLECTS_DROPS =
            TEST_FUNCTIONS.register("battlefield_defeat_collects_drops", () -> MineshaftBattlefieldGameTests::battlefieldDefeatCollectsDropsAndPersistsExperience);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BATTLEFIELD_BREAK_DROPS_ALL_SLOTS_ONCE =
            TEST_FUNCTIONS.register("battlefield_break_drops_all_slots_once", () -> MineshaftBattlefieldGameTests::battlefieldBreakDropsAllSlotsOnceAndClearsThem);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BATTLEFIELD_MENU_CLOSES_WHEN_BLOCK_REMOVED =
            TEST_FUNCTIONS.register("battlefield_menu_closes_when_block_removed", () -> MineshaftBattlefieldGameTests::battlefieldMenuClosesWhenBlockRemoved);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BATTLEFIELD_SHIFT_CLICK_OUTPUT_DOES_NOT_FILL_INPUTS =
            TEST_FUNCTIONS.register("battlefield_shift_click_output_does_not_fill_inputs", () -> MineshaftBattlefieldGameTests::battlefieldShiftClickOutputDoesNotFillInputSlots);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BATTLEFIELD_UPDATE_TAG_ONLY_NETWORK_DATA =
            TEST_FUNCTIONS.register("battlefield_update_tag_only_network_data", () -> MineshaftBattlefieldGameTests::battlefieldUpdateTagOnlyContainsNetworkData);

    // endregion Dimensional mineshaft and battlefield

    // region Familiars

    // endregion Familiars

    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(Identifier.fromNamespaceAndPath(Occultism.MODID, "map_item_resource_handler"));
        var curiosEnvironment = event.registerEnvironment(Identifier.fromNamespaceAndPath(Occultism.MODID, "curios_util"));
        var structure = Identifier.fromNamespaceAndPath(Occultism.MODID, "map_item_resource_handler_test");
        // Empty 17x8x17 area for tests that need to place multiblocks such as ritual pentacles.
        var largeStructure = Identifier.fromNamespaceAndPath(Occultism.MODID, "empty_17x8x17");

        registerTest(event, MAP_ITEM_RESOURCE_HANDLER_ABORTED_PROBES, environment, structure, 40, 0);
        registerTest(event, MAP_ITEM_RESOURCE_HANDLER_COMMITTED_EXTRACTION, environment, structure, 40, 0);
        registerTest(event, MAP_ITEM_RESOURCE_HANDLER_NESTED_ABORT, environment, structure, 40, 0);
        registerTest(event, MAP_ITEM_RESOURCE_HANDLER_NESTED_COMMIT, environment, structure, 40, 0);
        registerTest(event, MAP_ITEM_RESOURCE_HANDLER_SLOT_REUSE_ROLLBACK, environment, structure, 40, 0);
        registerTest(event, MAP_ITEM_RESOURCE_HANDLER_UNDO_JOURNAL_DRAINS, environment, structure, 40, 0);
        registerTest(event, MAP_ITEM_RESOURCE_HANDLER_RECURSIVE_COMMIT_NOTIFICATION, environment, structure, 40, 0);
        registerTest(event, CURIOS_UTIL_STORAGE_REMOTE_FIRST_SLOT, curiosEnvironment, structure, 40, 0);

        // region Storage controller

        // endregion Storage controller

        // region Satchel and wormhole tablet
        var satchelTabletEnvironment = event.registerEnvironment(Identifier.fromNamespaceAndPath(Occultism.MODID, "satchel_tablet"));
        registerTest(event, SATCHEL_WRITES_BACK_ONLY_WHILE_VALID, satchelTabletEnvironment, structure, 40, 0);
        registerTest(event, TABLET_WRITES_BACK_ONLY_WHILE_VALID, satchelTabletEnvironment, structure, 40, 0);
        registerTest(event, SATCHEL_REPLACED_BY_COPY_INVALIDATES_MENU, satchelTabletEnvironment, structure, 40, 0);
        registerTest(event, TABLET_REPLACED_BY_COPY_INVALIDATES_MENU, satchelTabletEnvironment, structure, 40, 0);
        registerTest(event, SATCHEL_SOURCE_SLOT_IS_LOCKED, satchelTabletEnvironment, structure, 40, 0);
        registerTest(event, TABLET_SOURCE_SLOT_IS_LOCKED, satchelTabletEnvironment, structure, 40, 0);
        registerTest(event, SATCHEL_HOTBAR_KEY_MOVE_INVALIDATES_WITHOUT_DUPE, satchelTabletEnvironment, structure, 40, 0);
        registerTest(event, SATCHEL_OPENED_FROM_OFFHAND_VALIDATES_OFFHAND, satchelTabletEnvironment, structure, 40, 0);
        registerTest(event, TABLET_OPENED_FROM_OFFHAND_VALIDATES_OFFHAND, satchelTabletEnvironment, structure, 40, 0);
        registerTest(event, ROTATE_TABLET_CONTENTS_ON_SERVER, satchelTabletEnvironment, structure, 40, 0);
        registerTest(event, ROTATE_TABLET_CONTENTS_IGNORED_WHEN_NOT_APPLICABLE, satchelTabletEnvironment, structure, 40, 0);
        registerTest(event, TELEPORT_SETS_BACK_COMPASS, satchelTabletEnvironment, structure, 40, 0);
        registerTest(event, TELEPORT_WITH_BACK_COMPASS_KEEPS_ITS_TARGET, satchelTabletEnvironment, structure, 40, 0);
        registerTest(event, RANDOM_TELEPORT_AT_WORLD_BORDER_DOES_NOT_THROW, satchelTabletEnvironment, structure, 40, 0);
        registerTest(event, RANDOM_TELEPORT_FINDS_TARGET_IN_UNLOADED_AREA, satchelTabletEnvironment, structure, 40, 0);
        // endregion Satchel and wormhole tablet

        // region Rituals
        var ritualEnvironment = event.registerEnvironment(Identifier.fromNamespaceAndPath(Occultism.MODID, "rituals"));
        // the repair chalks ritual takes 10 seconds (200 ticks)
        registerTest(event, RITUAL_FULL_RUN_COMPLETES, ritualEnvironment, largeStructure, 600, 0);
        registerTest(event, RITUAL_ACTIVATION_ITEM_NOT_CONSUMED_WHEN_INSERT_FAILS, ritualEnvironment, largeStructure, 40, 0);
        registerTest(event, RITUAL_HANDLER_INSERT_STARTS_ON_COMMIT, ritualEnvironment, largeStructure, 40, 0);
        registerTest(event, RITUAL_BROKEN_PENTACLE_INTERRUPTS, ritualEnvironment, largeStructure, 200, 0);
        registerTest(event, RITUAL_ONE_FLAME_OF_AUTOMATION, ritualEnvironment, structure, 40, 0);
        registerTest(event, RITUAL_ADVANCEMENT_RITUAL_ID, ritualEnvironment, structure, 40, 0);
        registerTest(event, RITUAL_INSTANCE_CACHED, ritualEnvironment, structure, 40, 0);
        registerTest(event, RITUAL_LOAD_NETWORK_CLEARS_STALE_STATE, ritualEnvironment, structure, 40, 0);
        registerTest(event, RITUAL_LOAD_MISSING_RECIPE, ritualEnvironment, structure, 40, 0);
        registerTest(event, RITUAL_LISTENERS_UNREGISTERED_ON_CHUNK_UNLOAD, ritualEnvironment, structure, 40, 0);
        registerTest(event, RITUAL_EMPTY_ENTITY_TAG_PREVIEW, ritualEnvironment, structure, 40, 0);
        registerTest(event, RITUAL_PREVIEWED_PENTACLE_VALIDATED, ritualEnvironment, structure, 40, 0);
        registerTest(event, RITUAL_BOWL_NETWORK_SYNC_KEEPS_INVENTORY, ritualEnvironment, structure, 40, 0);
        // endregion Rituals

        // region Dimensional mineshaft and battlefield

        var mineshaftBattlefieldEnvironment = event.registerEnvironment(Identifier.fromNamespaceAndPath(Occultism.MODID, "mineshaft_battlefield"));
        registerTest(event, MINESHAFT_MINES_WITH_AUTOMATION_MINER, mineshaftBattlefieldEnvironment, structure, 100, 0);
        registerTest(event, MINESHAFT_KEEPS_MINER_WHEN_OUTPUT_FULL, mineshaftBattlefieldEnvironment, structure, 40, 0);
        registerTest(event, MINESHAFT_MOVES_MINER_TO_OUTPUT_BEFORE_BREAKING, mineshaftBattlefieldEnvironment, structure, 100, 0);
        registerTest(event, MINESHAFT_BREAK_DROPS_INVENTORY_ONCE, mineshaftBattlefieldEnvironment, structure, 40, 0);
        registerTest(event, MINESHAFT_MENU_CLOSES_WHEN_BLOCK_REMOVED, mineshaftBattlefieldEnvironment, structure, 40, 0);
        registerTest(event, MINESHAFT_SHIFT_CLICK_DOES_NOT_MERGE_INTO_OUTPUT, mineshaftBattlefieldEnvironment, structure, 40, 0);
        registerTest(event, MINESHAFT_UPDATE_TAG_ONLY_NETWORK_DATA, mineshaftBattlefieldEnvironment, structure, 40, 0);
        registerTest(event, MINER_SPIRIT_MAX_STACK_SIZE_DOES_NOT_MUTATE, mineshaftBattlefieldEnvironment, structure, 40, 0);
        registerTest(event, BATTLEFIELD_INPUT_CHANGES_RESTART_FIGHT, mineshaftBattlefieldEnvironment, structure, 40, 0);
        registerTest(event, BATTLEFIELD_FIGHT_PRODUCES_LOOT, mineshaftBattlefieldEnvironment, structure, 100, 0);
        registerTest(event, BATTLEFIELD_DEFEAT_COLLECTS_DROPS, mineshaftBattlefieldEnvironment, structure, 40, 0);
        registerTest(event, BATTLEFIELD_BREAK_DROPS_ALL_SLOTS_ONCE, mineshaftBattlefieldEnvironment, structure, 40, 0);
        registerTest(event, BATTLEFIELD_MENU_CLOSES_WHEN_BLOCK_REMOVED, mineshaftBattlefieldEnvironment, structure, 40, 0);
        registerTest(event, BATTLEFIELD_SHIFT_CLICK_OUTPUT_DOES_NOT_FILL_INPUTS, mineshaftBattlefieldEnvironment, structure, 40, 0);
        registerTest(event, BATTLEFIELD_UPDATE_TAG_ONLY_NETWORK_DATA, mineshaftBattlefieldEnvironment, structure, 40, 0);

        // endregion Dimensional mineshaft and battlefield

        // region Familiars

        // endregion Familiars
    }

    private static void registerTest(
            RegisterGameTestsEvent event,
            DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> test,
            Holder<TestEnvironmentDefinition<?>> environment,
            Identifier structure,
            int maxTicks,
            int setupTicks
    ) {
        var testData = new TestData<>(environment, structure, maxTicks, setupTicks, true);
        event.registerTest(test.getId(), new FunctionGameTestInstance(test.getKey(), testData));
    }
}
