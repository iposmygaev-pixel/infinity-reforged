package com.infinityreforged.moderncombat;

import java.util.Collection;
import java.util.UUID;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

public final class CombatMath {

    /** UUIDs of the 1.7.10 Strength / Weakness attack damage modifiers. */
    private static final UUID STRENGTH_UUID = UUID.fromString("648D7064-6A60-4F59-8ABE-C2C23A6DD7A9");
    private static final UUID WEAKNESS_UUID = UUID.fromString("22653B89-116E-49DC-9B6B-9971489B5BE5");

    private CombatMath() {}

    /** Attack speed attribute value: item speed modified by Haste / Mining Fatigue (+/-10% per level). */
    public static double attackSpeed(EntityPlayer player) {
        double speed = WeaponStats.get(player.getCurrentEquippedItem()).speed;
        PotionEffect haste = player.getActivePotionEffect(Potion.digSpeed);
        if (haste != null) speed *= 1.0 + 0.1 * (haste.getAmplifier() + 1);
        PotionEffect fatigue = player.getActivePotionEffect(Potion.digSlowdown);
        if (fatigue != null) speed *= 1.0 - 0.1 * (fatigue.getAmplifier() + 1);
        return Math.max(speed, 1.0E-4);
    }

    /** Attack damage attribute with 1.9+ weapon values and potion effects. */
    public static float attackDamage(EntityPlayer player) {
        ItemStack stack = player.getCurrentEquippedItem();
        double delta = WeaponStats.get(stack).damageDelta;
        IAttributeInstance attr = player.getEntityAttribute(SharedMonsterAttributes.attackDamage);

        if (!ModConfig.modernPotions) {
            return (float) Math.max(0, attr.getAttributeValue() + delta);
        }

        @SuppressWarnings("unchecked")
        Collection<AttributeModifier> modifiers = attr.func_111122_c();

        double sum = attr.getBaseValue() + delta;
        for (AttributeModifier m : modifiers) {
            if (isPotionModifier(m)) continue;
            if (m.getOperation() == 0) sum += m.getAmount();
        }

        PotionEffect strength = player.getActivePotionEffect(Potion.damageBoost);
        if (strength != null) sum += 3.0 * (strength.getAmplifier() + 1);
        PotionEffect weakness = player.getActivePotionEffect(Potion.weakness);
        if (weakness != null) sum -= 4.0 * (weakness.getAmplifier() + 1);

        double value = sum;
        for (AttributeModifier m : modifiers) {
            if (isPotionModifier(m)) continue;
            if (m.getOperation() == 1) value += sum * m.getAmount();
        }
        for (AttributeModifier m : modifiers) {
            if (isPotionModifier(m)) continue;
            if (m.getOperation() == 2) value *= 1.0 + m.getAmount();
        }
        return (float) Math.max(0, Math.min(2048, value));
    }

    private static boolean isPotionModifier(AttributeModifier m) {
        UUID id = m.getID();
        return STRENGTH_UUID.equals(id) || WEAKNESS_UUID.equals(id);
    }

    /** 1.9 {@code ItemStack.areItemsEqualIgnoreDurability}. */
    public static boolean sameItemIgnoreDurability(ItemStack a, ItemStack b) {
        if (a == null || b == null) return a == b;
        if (a.getItem() != b.getItem()) return false;
        return a.isItemStackDamageable() || a.getItemDamage() == b.getItemDamage();
    }
}
