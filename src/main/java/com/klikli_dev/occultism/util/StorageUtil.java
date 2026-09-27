/*
 * MIT License
 *
 * Copyright 2020 klikli-dev, MrRiegel, Sam Bassett
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT
 * OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package com.klikli_dev.occultism.util;

import com.klikli_dev.occultism.api.common.blockentity.IStorageController;
import com.klikli_dev.occultism.common.container.storage.StorageControllerContainerBase;
import com.klikli_dev.occultism.network.Networking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.recipebook.PlaceRecipeHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Clearable;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Based on https://github.com/Lothrazar/Storage-Network
 */
public class StorageUtil {

    /**
     * Clears the crafting matrix of the open container, if that container implements IStorageControllerContainer
     *
     * @param player          the player to clear the crafting matrix for.
     * @param sendStackUpdate true to resend the current stacks to the client.
     */
    public static void clearOpenCraftingMatrix(ServerPlayer player, boolean sendStackUpdate) {
        //only act on a storage menu that is still valid, otherwise stale menus could be used to dupe items
        StorageControllerContainerBase container = StorageControllerContainerBase.getValidOpenContainer(player);
        if (container != null) {
            CraftingContainer craftMatrix = container.getCraftMatrix();
            IStorageController storageController = container.getStorageController();

            for (int i = 0; i < 9; i++) {
                ItemStack stackInSlot = craftMatrix.getItem(i);
                //ignore already cleared slots
                if (stackInSlot.isEmpty()) {
                    continue;
                }

                //move items into storage, and if storage is full, give to player
                int amountBeforeInsert = stackInSlot.getCount();
                int remainingAfterInsert = storageController.insertStack(stackInSlot.copy(), false);
                if (amountBeforeInsert == remainingAfterInsert) {
                    continue;
                }
                if (remainingAfterInsert == 0)
                    craftMatrix.setItem(i, ItemStack.EMPTY);
                else {
                    ItemTransferUtil.giveItemToPlayer(player, stackInSlot.copyWithCount(remainingAfterInsert));
                    craftMatrix.setItem(i, ItemStack.EMPTY);
                }
            }

            //finally if requested, send the updated storage controller contents to the player.
            if (sendStackUpdate) {
                Networking.sendTo(player, storageController.getMessageUpdateStacks());
                container.broadcastChanges();
            }

            //update (now empty) contents on the storage accessor
            container.updateCraftingSlots(true);
        }
    }

    /**
     * Clears the crafting matrix of the open container, if that container implements IStorageControllerContainer
     *
     * @param player          the player to clear the crafting matrix for.
     * @param sendStackUpdate true to resend the current stacks to the client.
     */
    public static void clearOpenOrderSlot(ServerPlayer player, boolean sendStackUpdate) {
        //only act on a storage menu that is still valid, otherwise stale menus could be used to dupe items
        StorageControllerContainerBase container = StorageControllerContainerBase.getValidOpenContainer(player);
        if (container != null) {
            SimpleContainer orderSlot = container.getOrderSlot();
            IStorageController storageController = container.getStorageController();

            ItemStack stackInSlot = orderSlot.getItem(0);
            if (!stackInSlot.isEmpty()) {
                //move items into storage, and if storage is full, keep remainder in crafting matrix
                int amountBeforeInsert = stackInSlot.getCount();
                int remainingAfterInsert = storageController.insertStack(stackInSlot.copy(), false);
                if (amountBeforeInsert != remainingAfterInsert) {
                    if (remainingAfterInsert == 0)
                        orderSlot.setItem(0, ItemStack.EMPTY);
                    else
                        orderSlot.setItem(0,
                                stackInSlot.copyWithCount(remainingAfterInsert));
                }
            }

            //finally if requested, send the updated storage controller contents to the player.
            if (sendStackUpdate) {
                Networking.sendTo(player, storageController.getMessageUpdateStacks());
                container.broadcastChanges();
            }
        }
    }

    /**
     * Extracts the given amount of items matching the given comparator from the given item handler
     *
     * @param itemHandler the handler to extract from.
     * @param comparator  the comparator to match item stacks.
     * @param amount      the amount to extract.
     * @param simulate    true to simulate.
     * @return the extracted stack.
     */
    public static ItemStack extractItem(ResourceHandler<ItemResource> itemHandler, Predicate<ItemStack> comparator, int amount,
                                        boolean simulate) {
        if (itemHandler == null || comparator == null || amount <= 0) {
            return ItemStack.EMPTY;
        }

        try (var tx = Transaction.openRoot()) {
            ItemResource matchedResource = ItemResource.EMPTY;
            int remaining = amount;

            //go through all slots in the handler
            for (int i = 0; i < itemHandler.size() && remaining > 0; i++) {
                var resource = itemHandler.getResource(i);
                if (resource.isEmpty()) {
                    continue;
                }

                var slotAmount = itemHandler.getAmountAsLong(i);
                ItemStack slot = resource.toStack((int) slotAmount);
                //check if current slot matches
                if (!comparator.test(slot)) {
                    continue;
                }

                if (matchedResource.isEmpty()) {
                    matchedResource = resource;
                } else if (!ItemStack.isSameItemSameComponents(matchedResource.toStack(), slot)) {
                    continue;
                }

                int extracted = itemHandler.extract(i, resource, remaining, tx);
                if (extracted > 0) {
                    remaining -= extracted;
                }
            }

            if (remaining > 0 || matchedResource.isEmpty()) {
                return ItemStack.EMPTY;
            }

            if (!simulate) {
                tx.commit();
            }

            return matchedResource.toStack(amount);
        }
    }

    public static ItemStack extractItem(ResourceHandler<ItemResource> itemHandler, Ingredient ingredient, int amount, boolean simulate) {
        return extractItem(itemHandler, ingredient::test, amount, simulate);
    }

    public static int getFirstFilledSlot(ResourceHandler<ItemResource> handler) {
        return getFirstFilledSlotAfter(handler, -1);
    }

    public static int getFirstFilledSlotAfter(ResourceHandler<ItemResource> handler, int slot) {
        for (int i = slot + 1; i < handler.size(); i++) {
            if (!handler.getResource(i).isEmpty())
                return i;
        }
        return -1;
    }

    public static int getFirstMatchingSlot(ResourceHandler<ItemResource> handler, ResourceHandler<ItemResource> filter, String tagFilter, boolean isBlacklist) {
        return getFirstMatchingSlotAfter(handler, -1, filter, tagFilter, isBlacklist);
    }

    public static int getFirstMatchingSlotAfter(ResourceHandler<ItemResource> handler, int slot, ResourceHandler<ItemResource> filter, String tagFilter, boolean isBlacklist) {
        for (int i = slot + 1; i < handler.size(); i++) {
            var resource = handler.getResource(i);
            if (!resource.isEmpty()) {
                ItemStack stack = resource.toStack((int) handler.getAmountAsLong(i));
                boolean matches = matchesFilter(stack, filter) ||
                        matchesFilter(stack, tagFilter);

                //if we're in blacklist mode, if the item matches either item or tag -> we continue into next iteration
                //if we're in blacklist mode and none of the filters match -> we return
                //if we're in whitelist mode, if the item matches either item or tag -> we return
                //if we're in whitelist mode, if the item matches neither item nor tag -> we continue
                if ((!isBlacklist && matches) || (isBlacklist && !matches))
                    return i;
            }
        }
        return -1;
    }


    public static boolean matchesFilter(ItemStack stack, ResourceHandler<ItemResource> filter) {
        for (int i = 0; i < filter.size(); i++) {
            var resource = filter.getResource(i);
            if (resource.isEmpty())
                continue;

            ItemStack filtered = resource.toStack((int) filter.getAmountAsLong(i));

            boolean equals = ItemStack.isSameItem(filtered, stack);

            if (equals) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if stack matches the given tag filter (wildcard match)
     */
    public static boolean matchesFilter(ItemStack stack, String tagFilter) {

        if (tagFilter.isEmpty())
            return false;

        String[] filters = tagFilter.split(";");
        for (String filter : filters) {

            if (filter.startsWith("item:")) {
                filter = filter.substring(5);
                if (FilenameUtils.wildcardMatch(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), filter, IOCase.INSENSITIVE))
                    return true;
            } else {
                //tags should not be prefixed, but we allow it and handle it
                if (filter.startsWith("tag:")) {
                    filter = filter.substring(4);
                }
                final String finalFilter = filter;
                boolean equals = stack.tags().anyMatch(tag -> {
                    return FilenameUtils.wildcardMatch(tag.location().toString(), finalFilter, IOCase.INSENSITIVE);
                });

                if (equals) {
                    return true;
                }
            }

        }
        return false;
    }

    /**
     * Drops all items of the given block entity. Tile entity <bold>must</bold> return a combined item handler for
     * direction null. If the block entity is {@link Clearable}, its contents are cleared afterwards to prevent
     * duplication, e.g. via still open menus.
     *
     * @param blockEntity the block entity to drop contents for.
     */
    public static void dropInventoryItems(BlockEntity blockEntity) {
        var resourceHandler = blockEntity.getLevel().getCapability(Capabilities.Item.BLOCK, blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity,null);
        if (resourceHandler != null) {
            dropInventoryItems(blockEntity.getLevel(), blockEntity.getBlockPos(), resourceHandler);
            if (blockEntity instanceof Clearable clearable)
                clearable.clearContent();
        }
    }

    public static void dropInventoryItems(Level worldIn, BlockPos pos, ResourceHandler<ItemResource> itemHandler) {
        for (int i = 0; i < itemHandler.size(); i++) {
            var resource = itemHandler.getResource(i);
            if (!resource.isEmpty())
                Containers.dropItemStack(worldIn, pos.getX(), pos.getY(), pos.getZ(), resource.toStack((int) itemHandler.getAmountAsLong(i)));
        }
    }

    public static int getFirstMatchingSlot(ResourceHandler<ItemResource> handler, TagKey<Item> tag) {
        for (int i = 0; i < handler.size(); i++) {
            if (handler.getResource(i).toStack().is(tag))
                return i;
        }
        return -1;
    }

    /**
     * Maps a crafting recipe's ingredients to a 3x3 crafting matrix.
     * Shaped recipes smaller than 3x3 are placed at the correct positions, based on the recipe placement info.
     *
     * @return a list of 9 entries, empty optionals mark slots that need no ingredient.
     */
    public static List<Optional<Ingredient>> ensure3by3CraftingMatrix(Recipe<?> recipe) {
        List<Optional<Ingredient>> ingredientsMatrixGrid = new ArrayList<>(Collections.nCopies(9, Optional.empty()));
        if (!(recipe instanceof CraftingRecipe craftingRecipe)) {
            return ingredientsMatrixGrid;
        }

        PlacementInfo placementInfo = craftingRecipe.placementInfo();
        if (placementInfo.isImpossibleToPlace()) {
            return ingredientsMatrixGrid;
        }

        var ingredients = placementInfo.ingredients();
        PlaceRecipeHelper.placeRecipe(3, 3, craftingRecipe, placementInfo.slotsToIngredientIndex(),
                (ingredientIndex, slot, gridXPos, gridYPos) -> {
                    if (slot >= 0 && slot < ingredientsMatrixGrid.size()
                            && ingredientIndex >= 0 && ingredientIndex < ingredients.size()) {
                        ingredientsMatrixGrid.set(slot, Optional.of(ingredients.get(ingredientIndex)));
                    }
                });

        return ingredientsMatrixGrid;
    }
}
