package com.infinityreforged.moderncombat;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

/**
 * The 1.9+ shield. Held in the main hand (1.7.10 has no off hand); right-click raises it.
 */
public class ItemShield extends Item {

    public ItemShield() {
        setMaxStackSize(1);
        setMaxDamage(336);
        setCreativeTab(CreativeTabs.tabCombat);
        setUnlocalizedName("moderncombat.shield");
        setTextureName(ModernCombat.MODID + ":shield");
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.block;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (CombatData.get(player).shieldCooldown <= 0) {
            player.setItemInUse(stack, getMaxItemUseDuration(stack));
        }
        return stack;
    }

    /** Repaired with any planks, as in 1.9+. */
    @Override
    public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
        if (repair == null) return false;
        for (ItemStack plank : OreDictionary.getOres("plankWood")) {
            if (OreDictionary.itemMatches(plank, repair, false)) return true;
        }
        return false;
    }
}
