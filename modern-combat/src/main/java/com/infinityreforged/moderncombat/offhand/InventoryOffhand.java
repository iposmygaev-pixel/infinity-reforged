package com.infinityreforged.moderncombat.offhand;

import com.infinityreforged.moderncombat.CombatData;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/**
 * One-slot inventory backed by the player's off-hand stack in {@link CombatData}.
 */
public class InventoryOffhand implements IInventory {

    private final EntityPlayer player;

    public InventoryOffhand(EntityPlayer player) {
        this.player = player;
    }

    private CombatData data() {
        return CombatData.get(player);
    }

    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == 0 ? data().offhand : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        ItemStack stack = getStackInSlot(slot);
        if (stack == null) return null;
        if (stack.stackSize <= amount) {
            setInventorySlotContents(slot, null);
            return stack;
        }
        ItemStack split = stack.splitStack(amount);
        markDirty();
        return split;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot != 0) return;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) stack.stackSize = getInventoryStackLimit();
        data().offhand = stack;
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "container.moderncombat.offhand";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void markDirty() {}

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return player == this.player;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return true;
    }
}
