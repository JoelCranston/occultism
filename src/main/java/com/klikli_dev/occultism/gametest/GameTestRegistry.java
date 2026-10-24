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

    // endregion Satchel and wormhole tablet

    // region Rituals

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

        // endregion Satchel and wormhole tablet

        // region Rituals

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
