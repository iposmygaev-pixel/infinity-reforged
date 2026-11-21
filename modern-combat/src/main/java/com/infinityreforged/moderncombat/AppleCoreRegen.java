package com.infinityreforged.moderncombat;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.FoodStats;
import squeek.applecore.api.hunger.HealthRegenEvent;

/**
 * 1.9+ regeneration through AppleCore's events. AppleCore rewrites FoodStats itself, so with AppleCore installed
 * the FoodStats replacement is skipped and the same rules are applied here instead.
 * Only loaded when AppleCore is present.
 */
public class AppleCoreRegen {

    /** Full food bar with saturation left: fast healing from saturation. */
    private static boolean saturated(EntityPlayer player) {
        FoodStats food = player.getFoodStats();
        return food.getSaturationLevel() > 0.0F && food.getFoodLevel() >= 20;
    }

    @SubscribeEvent
    public void onAllowRegen(HealthRegenEvent.AllowRegen event) {
        if (!ModConfig.modernRegeneration) return;
        EntityPlayer player = event.player;
        if (saturated(player) && player.shouldHeal()
            && player.worldObj.getGameRules()
                .getGameRuleBooleanValue("naturalRegeneration")) {
            event.setResult(Event.Result.ALLOW);
        }
    }

    @SubscribeEvent
    public void onRegenTickPeriod(HealthRegenEvent.GetRegenTickPeriod event) {
        if (ModConfig.modernRegeneration && saturated(event.player)) {
            event.regenTickPeriod = 10;
        }
    }

    @SubscribeEvent
    public void onRegen(HealthRegenEvent.Regen event) {
        if (!ModConfig.modernRegeneration) return;
        if (saturated(event.player)) {
            float amount = Math.min(
                event.player.getFoodStats()
                    .getSaturationLevel(),
                6.0F);
            event.deltaHealth = amount / 6.0F;
            event.deltaExhaustion = amount;
        } else {
            event.deltaHealth = 1.0F;
            event.deltaExhaustion = 6.0F;
        }
    }
}
