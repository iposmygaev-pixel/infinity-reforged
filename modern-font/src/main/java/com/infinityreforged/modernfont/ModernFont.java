package com.infinityreforged.modernfont;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * Modern Font: draws Cyrillic and other non-Latin text with the pixel font of modern Minecraft. Client only.
 */
@Mod(
    modid = ModernFont.MODID,
    name = ModernFont.NAME,
    version = ModernFont.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public class ModernFont {

    public static final String MODID = "modernfont";
    public static final String NAME = "Modern Font";
    public static final String VERSION = "0.2.0";

    public static final Logger LOG = LogManager.getLogger(NAME);

    @SidedProxy(
        clientSide = "com.infinityreforged.modernfont.client.ClientProxy",
        serverSide = "com.infinityreforged.modernfont.CommonProxy")
    public static CommonProxy proxy;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }
}
