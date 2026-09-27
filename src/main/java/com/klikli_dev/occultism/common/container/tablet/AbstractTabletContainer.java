/*
 * MIT License
 *
 * Copyright 2020 klikli-dev
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

package com.klikli_dev.occultism.common.container.tablet;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public abstract class AbstractTabletContainer extends AbstractContainerMenu {

    protected Container tabletInventory;
    protected Inventory playerInventory;
    protected int selectedSlot;
    protected ItemStack tabletStack;

    public AbstractTabletContainer(@Nullable MenuType<?> menuType, int id, Inventory playerInventory, Container tabletInventory, int selectedSlot) {
        super(menuType, id);
        this.tabletInventory = tabletInventory;
        this.playerInventory = playerInventory;
        this.selectedSlot = selectedSlot;

        this.tabletStack = this.getSourceStack(playerInventory.player).copy();


        this.setupTabletSlots();
        this.setupPlayerInventorySlots();
        this.setupPlayerHotbar();
    }

    @Override
    public void broadcastChanges() {
        if (this.tabletInventory instanceof TabletInventory) {
            ((TabletInventory) this.tabletInventory).writeItemStack();
        }
        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        //Adapted from Chestcontainer
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            if (index >= this.tabletInventory.getContainerSize()) {
                //if putting into tablet, abort if it's not a compass
                if (!itemstack.is(ItemTags.COMPASSES))
                    return ItemStack.EMPTY;
            }
            //take out of tablet
            if (index < this.tabletInventory.getContainerSize()) {
                if (!this.moveItemStackTo(itemstack1, this.tabletInventory.getContainerSize(),
                        this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            }
            //put into tablet
            else if (!this.moveItemStackTo(itemstack1, 0, this.tabletInventory.getContainerSize(), false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    /**
     * @return the stack currently in the player inventory slot the tablet was opened from (including Inventory.SLOT_OFFHAND).
     */
    protected ItemStack getSourceStack(Player player) {
        if (this.selectedSlot < 0 || this.selectedSlot >= player.getInventory().getContainerSize())
            return ItemStack.EMPTY;
        return player.getInventory().getItem(this.selectedSlot);
    }

    @Override
    public boolean stillValid(Player player) {
        ItemStack sourceStack = this.getSourceStack(player);
        if (this.tabletInventory instanceof TabletInventory inventory) {
            //server side: the tablet we are writing to must still be exactly the stack in the source slot.
            return !sourceStack.isEmpty() && sourceStack == inventory.getItemStack();
        }
        return !sourceStack.isEmpty() && sourceStack.getItem() == this.tabletStack.getItem();
    }

    /**
     * Creates a player inventory slot, locking the slot that holds the open tablet so it cannot be moved.
     */
    protected Slot createPlayerSlot(int slotIndex, int x, int y) {
        if (slotIndex != this.selectedSlot)
            return new Slot(this.playerInventory, slotIndex, x, y);

        return new Slot(this.playerInventory, slotIndex, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player playerIn) {
                return false;
            }
        };
    }

    protected void setupPlayerInventorySlots() {
        int playerInventoryTop = 174;
        int playerInventoryLeft = 44;
        int hotbarSlots = 9;

        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 9; j++)
                this.addSlot(this.createPlayerSlot(j + i * 9 + hotbarSlots, playerInventoryLeft + j * 18,
                        playerInventoryTop + i * 18));
    }

    protected void setupPlayerHotbar() {
        int hotbarTop = 232;
        int hotbarLeft = 44;
        for (int i = 0; i < 9; i++) {
            this.addSlot(this.createPlayerSlot(i, hotbarLeft + i * 18, hotbarTop));
        }
    }

    protected abstract void setupTabletSlots();

}
