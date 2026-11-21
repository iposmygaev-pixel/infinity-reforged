package com.infinityreforged.moderncombat;

import java.lang.reflect.Field;

import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.FoodStats;
import net.minecraft.world.EnumDifficulty;

/**
 * Minecraft 1.9+ hunger: fast regeneration from saturation when the food bar is full.
 */
public class ModernFoodStats extends FoodStats {

    private static Field foodLevel;
    private static Field saturation;
    private static Field exhaustion;
    private static Field timer;
    private static Field prevFoodLevel;
    private static Field playerFoodStats;
    private static boolean available;

    public static void init() {
        try {
            foodLevel = ReflectionHelper.findField(FoodStats.class, "foodLevel", "field_75127_a");
            saturation = ReflectionHelper.findField(FoodStats.class, "foodSaturationLevel", "field_75125_b");
            exhaustion = ReflectionHelper.findField(FoodStats.class, "foodExhaustionLevel", "field_75126_c");
            timer = ReflectionHelper.findField(FoodStats.class, "foodTimer", "field_75123_d");
            prevFoodLevel = ReflectionHelper.findField(FoodStats.class, "prevFoodLevel", "field_75124_e");
            playerFoodStats = ReflectionHelper.findField(EntityPlayer.class, "foodStats", "field_71100_bB");
            available = true;
        } catch (RuntimeException e) {
            ModernCombat.LOG.warn("Could not access FoodStats fields; modern regeneration is disabled", e);
        }
    }

    /** Replaces a player's vanilla FoodStats, keeping its state. Other mods' replacements are left alone. */
    public static void install(EntityPlayer player) {
        if (!available || !ModConfig.modernRegeneration) return;
        FoodStats current = player.getFoodStats();
        if (current == null || current.getClass() != FoodStats.class) return;
        NBTTagCompound tag = new NBTTagCompound();
        current.writeNBT(tag);
        ModernFoodStats replacement = new ModernFoodStats();
        replacement.readNBT(tag);
        try {
            replacement.copyTimers(current);
            playerFoodStats.set(player, replacement);
        } catch (IllegalAccessException e) {
            ModernCombat.LOG.warn("Could not replace FoodStats", e);
        }
    }

    private void copyTimers(FoodStats from) throws IllegalAccessException {
        prevFoodLevel.setInt(this, prevFoodLevel.getInt(from));
    }

    @Override
    public void onUpdate(EntityPlayer player) {
        if (!ModConfig.modernRegeneration) {
            super.onUpdate(player);
            return;
        }
        try {
            update(player);
        } catch (IllegalAccessException e) {
            super.onUpdate(player);
        }
    }

    /** 1.9+ {@code FoodStats.onUpdate}. */
    private void update(EntityPlayer player) throws IllegalAccessException {
        EnumDifficulty difficulty = player.worldObj.difficultySetting;
        int food = foodLevel.getInt(this);
        float sat = saturation.getFloat(this);
        float exh = exhaustion.getFloat(this);
        prevFoodLevel.setInt(this, food);

        if (exh > 4.0F) {
            exhaustion.setFloat(this, exh - 4.0F);
            if (sat > 0.0F) {
                sat = Math.max(sat - 1.0F, 0.0F);
                saturation.setFloat(this, sat);
            } else if (difficulty != EnumDifficulty.PEACEFUL) {
                food = Math.max(food - 1, 0);
                foodLevel.setInt(this, food);
            }
        }

        boolean regen = player.worldObj.getGameRules()
            .getGameRuleBooleanValue("naturalRegeneration");
        int ticks = timer.getInt(this);

        if (regen && sat > 0.0F && player.shouldHeal() && food >= 20) {
            if (++ticks >= 10) {
                float amount = Math.min(sat, 6.0F);
                player.heal(amount / 6.0F);
                addExhaustion(amount);
                ticks = 0;
            }
        } else if (regen && food >= 18 && player.shouldHeal()) {
            if (++ticks >= 80) {
                player.heal(1.0F);
                addExhaustion(6.0F);
                ticks = 0;
            }
        } else if (food <= 0) {
            if (++ticks >= 80) {
                float health = player.getHealth();
                if (health > 10.0F || difficulty == EnumDifficulty.HARD
                    || (health > 1.0F && difficulty == EnumDifficulty.NORMAL)) {
                    player.attackEntityFrom(DamageSource.starve, 1.0F);
                }
                ticks = 0;
            }
        } else {
            ticks = 0;
        }
        timer.setInt(this, ticks);
    }
}
