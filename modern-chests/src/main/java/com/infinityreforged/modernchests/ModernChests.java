package com.infinityreforged.modernchests;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import net.minecraft.launchwrapper.Launch;

/**
 * Modern Chests: chests can be placed next to each other as in Minecraft 1.13+. A chest joins a single chest
 * beside it that faces the same way; sneaking while placing keeps it single.
 */
@Mod(
    modid = ModernChests.MODID,
    name = ModernChests.NAME,
    version = ModernChests.VERSION,
    acceptedMinecraftVersions = "[1.7.10]")
public class ModernChests {

    public static final String MODID = "modernchests";
    public static final String NAME = "Modern Chests";
    public static final String VERSION = "0.1.1";

    public static final Logger LOG = LogManager.getLogger(NAME);

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        // Test command only in the development environment.
        if (Boolean.TRUE.equals(Launch.blackboard.get("fml.deobfuscatedEnvironment"))) {
            event.registerServerCommand(new ChestTestCommand());
        }
    }
}
