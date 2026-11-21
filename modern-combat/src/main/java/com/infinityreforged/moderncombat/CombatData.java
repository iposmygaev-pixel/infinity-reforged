package com.infinityreforged.moderncombat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;

/**
 * Per-player attack cooldown state (the 1.9 {@code ticksSinceLastSwing} field).
 */
public class CombatData implements IExtendedEntityProperties {

    public static final String KEY = "ModernCombat";

    public int ticksSinceLastSwing;
    /** Ticks left before the shield can be raised again (after an axe hit). */
    public int shieldCooldown;
    public ItemStack lastHeld;
    /** Set from the network thread when the client reports a swing; consumed on the next player tick. */
    public volatile boolean swingPending;

    public static CombatData get(EntityPlayer player) {
        IExtendedEntityProperties props = player.getExtendedProperties(KEY);
        if (props instanceof CombatData) return (CombatData) props;
        CombatData data = new CombatData();
        player.registerExtendedProperties(KEY, data);
        return data;
    }

    public void resetCooldown() {
        ticksSinceLastSwing = 0;
    }

    /** Ticks needed for a full charge: 20 / attackSpeed. */
    public static float cooldownPeriod(EntityPlayer player) {
        return (float) (20.0 / CombatMath.attackSpeed(player));
    }

    /** 1.9 {@code getCooledAttackStrength}. */
    public float attackStrength(EntityPlayer player, float adjustTicks) {
        if (!ModConfig.attackCooldown) return 1.0F;
        float strength = (ticksSinceLastSwing + adjustTicks) / cooldownPeriod(player);
        return strength < 0 ? 0 : (strength > 1 ? 1 : strength);
    }

    @Override
    public void saveNBTData(NBTTagCompound compound) {}

    @Override
    public void loadNBTData(NBTTagCompound compound) {}

    @Override
    public void init(Entity entity, World world) {}
}
