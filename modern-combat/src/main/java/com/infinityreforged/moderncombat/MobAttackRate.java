package com.infinityreforged.moderncombat;

import java.util.Map;
import java.util.WeakHashMap;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

/**
 * Fixes the 1.7.10 melee attack rate of mobs.
 * <p>
 * In 1.7.10 {@code EntityAIAttackOnCollide} checks {@code attackTick <= 20} right after setting it to 20, so a mob
 * in reach attacks every tick and only the target's 10-tick invulnerability limits the damage: 2 hits per second.
 * 1.8 fixed the check to {@code attackTick <= 0} (one attack per 20 ticks). Here a mob's direct melee hit is
 * allowed at most once per {@link #COOLDOWN} ticks.
 */
public class MobAttackRate {

    public static final int COOLDOWN = 20;

    /** Mob -> its {@code ticksExisted} at the last allowed melee hit. */
    private final Map<Entity, Integer> lastAttack = new WeakHashMap<Entity, Integer>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(LivingAttackEvent event) {
        if (event.entityLiving.worldObj.isRemote || !"mob".equals(event.source.getDamageType())) return;
        Entity attacker = event.source.getEntity();
        // Direct melee only (projectiles have another immediate source); slimes deal contact damage every tick
        // in modern versions too.
        if (!(attacker instanceof EntityLiving) || attacker instanceof EntitySlime
            || event.source.getSourceOfDamage() != attacker) return;

        Integer last = lastAttack.get(attacker);
        int now = attacker.ticksExisted;
        if (last != null && now >= last && now - last < COOLDOWN) {
            event.setCanceled(true);
            return;
        }
        lastAttack.put(attacker, now);
    }
}
