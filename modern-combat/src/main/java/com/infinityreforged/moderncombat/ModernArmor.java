package com.infinityreforged.moderncombat;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraftforge.common.ISpecialArmor;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Minecraft 1.9+ armor: big hits pierce armor, armor toughness resists that, durability loss is damage / 4.
 */
public final class ModernArmor {

    private static final Map<Item, Float> VANILLA_TOUGHNESS = new IdentityHashMap<Item, Float>();
    private static final Map<String, Float> OVERRIDES = new HashMap<String, Float>();
    private static Field unblockableField;

    private ModernArmor() {}

    public static void init() {
        VANILLA_TOUGHNESS.clear();
        VANILLA_TOUGHNESS.put(Items.diamond_helmet, 2.0F);
        VANILLA_TOUGHNESS.put(Items.diamond_chestplate, 2.0F);
        VANILLA_TOUGHNESS.put(Items.diamond_leggings, 2.0F);
        VANILLA_TOUGHNESS.put(Items.diamond_boots, 2.0F);

        OVERRIDES.clear();
        for (String line : ModConfig.armorToughness) {
            if (line == null || line.trim()
                .isEmpty()) continue;
            int eq = line.lastIndexOf('=');
            if (eq <= 0) continue;
            try {
                OVERRIDES.put(
                    line.substring(0, eq)
                        .trim(),
                    Float.parseFloat(
                        line.substring(eq + 1)
                            .trim()));
            } catch (NumberFormatException e) {
                ModernCombat.LOG.warn("Ignoring malformed armor toughness entry: {}", line);
            }
        }

        try {
            unblockableField = ReflectionHelper.findField(DamageSource.class, "isUnblockable", "field_76374_o");
        } catch (RuntimeException e) {
            ModernCombat.LOG.warn("Could not access DamageSource.isUnblockable; using the fallback armor path");
        }
    }

    /** 1.9+ {@code CombatRules.getDamageAfterAbsorb}. */
    public static float damageAfterArmor(float damage, float armor, float toughness) {
        float f = 2.0F + toughness / 4.0F;
        float effective = armor - damage / f;
        if (effective < armor * 0.2F) effective = armor * 0.2F;
        if (effective > 20.0F) effective = 20.0F;
        return damage * (1.0F - effective / 25.0F);
    }

    public static float toughness(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemArmor)) return 0.0F;
        if (!OVERRIDES.isEmpty()) {
            Object name = Item.itemRegistry.getNameForObject(stack.getItem());
            if (name != null) {
                Float value = OVERRIDES.get(name.toString());
                if (value != null) return value;
            }
        }
        Float value = VANILLA_TOUGHNESS.get(stack.getItem());
        return value == null ? 0.0F : value;
    }

    /**
     * Called from LivingHurtEvent, right before 1.7.10 applies its armor. Rewrites the damage so the end result
     * follows the 1.9+ formula.
     */
    public static void onLivingHurt(LivingHurtEvent event) {
        EntityLivingBase entity = event.entityLiving;
        DamageSource source = event.source;
        float damage = event.ammount;
        if (damage <= 0.0F || source.isUnblockable() || entity.worldObj.isRemote) return;

        int armor = entity.getTotalArmorValue();
        // 25+ armor already blocks everything in 1.7.10; nothing to convert.
        if (armor <= 0 || armor >= 25) return;

        float toughness = 0.0F;
        boolean player = entity instanceof EntityPlayer;
        if (player) {
            for (ItemStack stack : ((EntityPlayer) entity).inventory.armorInventory) {
                // Special modded armor has its own absorption logic; leave it alone.
                if (stack != null && stack.getItem() instanceof ISpecialArmor) return;
                toughness += toughness(stack);
            }
        } else {
            for (int slot = 1; slot <= 4; slot++) {
                toughness += toughness(entity.getEquipmentInSlot(slot));
            }
        }

        float reduced = damageAfterArmor(damage, armor, toughness);

        if (player && source instanceof EntityDamageSource && unblockableField != null) {
            // Entity damage sources are created per hit, so the armor step can be skipped for this hit only:
            // apply 1.9 durability loss and the 1.9 reduction here.
            try {
                unblockableField.setBoolean(source, true);
                ((EntityPlayer) entity).inventory.damageArmor(damage);
                event.ammount = reduced;
                return;
            } catch (IllegalAccessException ignored) {}
        }

        // Shared damage sources (lava, cactus...) and mobs: pre-scale so 1.7.10's linear armor yields the 1.9 value.
        event.ammount = reduced * 25.0F / (25.0F - armor);
    }
}
