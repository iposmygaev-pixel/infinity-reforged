package com.infinityreforged.moderncombat;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class ModConfig {

    public static final String CAT_GENERAL = Configuration.CATEGORY_GENERAL;
    public static final String CAT_WEAPONS = "weapons";
    public static final String CAT_CLIENT = "client";

    // general
    public static boolean attackCooldown = true;
    public static boolean sweepAttack = true;
    public static boolean modernCrits = true;
    public static boolean modernPotions = true;
    public static boolean modernExhaustion = true;
    public static boolean disableSwordBlocking = true;
    public static boolean scaleSelfHandledAttacks = true;

    // weapons
    public static boolean modernVanillaDamage = true;
    public static boolean adjustModdedToolDamage = true;
    public static String[] attackSpeedOverrides = new String[0];

    // client
    public static String attackIndicator = "CROSSHAIR";
    public static boolean weaponLowerAnimation = true;
    public static boolean modernTooltips = true;

    private static final String[] DEFAULT_OVERRIDES = {
        // Tinkers' Construct (handles its own damage; only speed is applied, damage is scaled by charge)
        "TConstruct:broadsword=1.6", "TConstruct:longsword=1.4", "TConstruct:rapier=2.0", "TConstruct:cutlass=1.8",
        "TConstruct:dagger=2.2", "TConstruct:battlesign=1.6", "TConstruct:frypan=1.4", "TConstruct:cleaver=0.7",
        "TConstruct:battleaxe=0.8", "TConstruct:hatchet=0.9", "TConstruct:lumberaxe=0.8", "TConstruct:scythe=0.8",
        "TConstruct:pickaxe=1.2", "TConstruct:shovel=1.0", "TConstruct:mattock=0.9", "TConstruct:hammer=0.7",
        "TConstruct:excavator=0.9", "TConstruct:chisel=4.0", };

    private ModConfig() {}

    public static void load(File file) {
        Configuration cfg = new Configuration(file);
        cfg.load();

        attackCooldown = cfg.getBoolean(
            "attackCooldown",
            CAT_GENERAL,
            true,
            "Attack cooldown (1.9+): damage depends on how charged the attack is.");
        sweepAttack = cfg.getBoolean(
            "sweepAttack",
            CAT_GENERAL,
            true,
            "Sweep attack with swords on a fully charged hit while standing still.");
        modernCrits = cfg.getBoolean(
            "modernCrits",
            CAT_GENERAL,
            true,
            "Critical hits only when the attack is charged above 90% and the player is not sprinting.");
        modernPotions = cfg.getBoolean(
            "modernPotions",
            CAT_GENERAL,
            true,
            "Strength gives +3 damage per level and Weakness -4 per level (instead of +130% / -0.5).");
        modernExhaustion = cfg.getBoolean(
            "modernExhaustion",
            CAT_GENERAL,
            true,
            "Attacking costs 0.1 exhaustion (modern) instead of 0.3.");
        disableSwordBlocking = cfg.getBoolean(
            "disableSwordBlocking",
            CAT_GENERAL,
            true,
            "Disable right-click blocking with swords, as in 1.9+.");
        scaleSelfHandledAttacks = cfg.getBoolean(
            "scaleSelfHandledAttacks",
            CAT_GENERAL,
            true,
            "Scale damage of weapons that run their own attack code (e.g. Tinkers' Construct) by the attack charge.");

        modernVanillaDamage = cfg.getBoolean(
            "modernVanillaDamage",
            CAT_WEAPONS,
            true,
            "Use 1.9+ damage values for vanilla swords, axes, pickaxes, shovels and hoes.");
        adjustModdedToolDamage = cfg.getBoolean(
            "adjustModdedToolDamage",
            CAT_WEAPONS,
            true,
            "Apply the 1.9+ damage formula to modded swords (-1), pickaxes (-1) and shovels (+0.5).");
        attackSpeedOverrides = cfg.getStringList(
            "attackSpeedOverrides",
            CAT_WEAPONS,
            DEFAULT_OVERRIDES,
            "Per-item attack speed. Format: modid:item=speed or modid:item=speed,damageBonus\n"
                + "Base player attack speed is 4.0 (fist). Swords 1.6, axes 0.8-1.0, pickaxes 1.2, shovels 1.0.");

        attackIndicator = cfg.getString(
            "attackIndicator",
            CAT_CLIENT,
            "CROSSHAIR",
            "Where to show the attack indicator.",
            new String[] { "CROSSHAIR", "HOTBAR", "OFF" });
        weaponLowerAnimation = cfg.getBoolean(
            "weaponLowerAnimation",
            CAT_CLIENT,
            true,
            "Lower the held item after an attack and raise it as the cooldown recovers.");
        modernTooltips = cfg.getBoolean(
            "modernTooltips",
            CAT_CLIENT,
            true,
            "Show 'When in main hand' attack damage and attack speed in item tooltips.");

        if (cfg.hasChanged()) {
            cfg.save();
        }
    }
}
