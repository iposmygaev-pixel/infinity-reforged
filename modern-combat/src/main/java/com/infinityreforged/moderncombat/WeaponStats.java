package com.infinityreforged.moderncombat;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

/**
 * Attack speed and damage adjustments per item, matching Minecraft 1.9+ values.
 */
public final class WeaponStats {

    /** Immutable pair of attack speed and a flat damage correction relative to 1.7.10. */
    public static final class Stats {

        public final double speed;
        public final double damageDelta;

        Stats(double speed, double damageDelta) {
            this.speed = speed;
            this.damageDelta = damageDelta;
        }
    }

    public static final double BASE_ATTACK_SPEED = 4.0;

    private static final Stats DEFAULT = new Stats(BASE_ATTACK_SPEED, 0);
    private static final Stats SWORD = new Stats(1.6, 0);
    private static final Stats SWORD_ADJ = new Stats(1.6, -1.0);
    private static final Stats AXE = new Stats(0.9, 0);
    private static final Stats PICKAXE = new Stats(1.2, 0);
    private static final Stats PICKAXE_ADJ = new Stats(1.2, -1.0);
    private static final Stats SHOVEL = new Stats(1.0, 0);
    private static final Stats SHOVEL_ADJ = new Stats(1.0, 0.5);

    private static final Map<Item, Stats> VANILLA = new IdentityHashMap<Item, Stats>();
    private static final Map<String, Stats> OVERRIDES = new HashMap<String, Stats>();
    private static final Map<String, Stats> HOE_CACHE = new HashMap<String, Stats>();

    private WeaponStats() {}

    public static void init() {
        VANILLA.clear();
        // 1.7.10 sword = 4 + material, 1.9 sword = 3 + material: one point less.
        vanilla(Items.wooden_sword, 1.6, -1);
        vanilla(Items.stone_sword, 1.6, -1);
        vanilla(Items.iron_sword, 1.6, -1);
        vanilla(Items.diamond_sword, 1.6, -1);
        vanilla(Items.golden_sword, 1.6, -1);
        // Axes: 1.9 totals 7/9/9/9/7 vs 1.7.10 totals 4/5/6/7/4.
        vanilla(Items.wooden_axe, 0.8, 3);
        vanilla(Items.stone_axe, 0.8, 4);
        vanilla(Items.iron_axe, 0.9, 3);
        vanilla(Items.diamond_axe, 1.0, 2);
        vanilla(Items.golden_axe, 1.0, 3);
        // Pickaxes: 1.9 = 1 + material, 1.7.10 = 2 + material.
        vanilla(Items.wooden_pickaxe, 1.2, -1);
        vanilla(Items.stone_pickaxe, 1.2, -1);
        vanilla(Items.iron_pickaxe, 1.2, -1);
        vanilla(Items.diamond_pickaxe, 1.2, -1);
        vanilla(Items.golden_pickaxe, 1.2, -1);
        // Shovels: 1.9 = 1.5 + material, 1.7.10 = 1 + material.
        vanilla(Items.wooden_shovel, 1.0, 0.5);
        vanilla(Items.stone_shovel, 1.0, 0.5);
        vanilla(Items.iron_shovel, 1.0, 0.5);
        vanilla(Items.diamond_shovel, 1.0, 0.5);
        vanilla(Items.golden_shovel, 1.0, 0.5);
        // Hoes: 1 damage, speed grows with material.
        vanilla(Items.wooden_hoe, 1.0, 0);
        vanilla(Items.stone_hoe, 2.0, 0);
        vanilla(Items.iron_hoe, 3.0, 0);
        vanilla(Items.diamond_hoe, 4.0, 0);
        vanilla(Items.golden_hoe, 1.0, 0);

        OVERRIDES.clear();
        for (String line : ModConfig.attackSpeedOverrides) {
            parseOverride(line);
        }
    }

    private static void vanilla(Item item, double speed, double delta) {
        VANILLA.put(item, new Stats(speed, delta));
    }

    private static void parseOverride(String line) {
        if (line == null) return;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return;
        int eq = trimmed.lastIndexOf('=');
        if (eq <= 0) {
            ModernCombat.LOG.warn("Ignoring attack speed override without '=': {}", line);
            return;
        }
        String name = trimmed.substring(0, eq)
            .trim();
        String[] values = trimmed.substring(eq + 1)
            .split(",");
        try {
            double speed = Double.parseDouble(values[0].trim());
            double delta = values.length > 1 ? Double.parseDouble(values[1].trim()) : 0;
            OVERRIDES.put(name, new Stats(speed, delta));
        } catch (NumberFormatException e) {
            ModernCombat.LOG.warn("Ignoring malformed attack speed override: {}", line);
        }
    }

    public static Stats get(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return DEFAULT;
        Item item = stack.getItem();

        if (!OVERRIDES.isEmpty()) {
            Object name = Item.itemRegistry.getNameForObject(item);
            if (name != null) {
                Stats override = OVERRIDES.get(name.toString());
                if (override != null) return override;
            }
        }

        Stats vanilla = VANILLA.get(item);
        if (vanilla != null) {
            return ModConfig.modernVanillaDamage ? vanilla : new Stats(vanilla.speed, 0);
        }

        boolean adjust = ModConfig.adjustModdedToolDamage;
        if (item instanceof ItemSword) return adjust ? SWORD_ADJ : SWORD;
        if (item instanceof ItemAxe) return AXE;
        if (item instanceof ItemPickaxe) return adjust ? PICKAXE_ADJ : PICKAXE;
        if (item instanceof ItemSpade) return adjust ? SHOVEL_ADJ : SHOVEL;
        if (item instanceof ItemHoe) return hoe((ItemHoe) item);

        Set<String> toolClasses = item.getToolClasses(stack);
        if (toolClasses != null && !toolClasses.isEmpty()) {
            if (toolClasses.contains("axe")) return AXE;
            if (toolClasses.contains("pickaxe")) return PICKAXE;
            if (toolClasses.contains("shovel")) return SHOVEL;
        }
        return DEFAULT;
    }

    /** 1.9 hoe speed = material damage + 1 (wood 1, stone 2, iron 3, diamond 4, gold 1). */
    private static Stats hoe(ItemHoe hoe) {
        String material = hoe.getToolMaterialName();
        Stats cached = HOE_CACHE.get(material);
        if (cached != null) return cached;
        double speed = 1.0;
        try {
            speed = Item.ToolMaterial.valueOf(material)
                .getDamageVsEntity() + 1.0;
        } catch (RuntimeException ignored) {
            // Materials added through EnumHelper may not resolve via valueOf.
        }
        speed = Math.max(1.0, Math.min(4.0, speed));
        Stats stats = new Stats(speed, 0);
        HOE_CACHE.put(material, stats);
        return stats;
    }
}
