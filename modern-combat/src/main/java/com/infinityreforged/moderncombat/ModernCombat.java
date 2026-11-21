package com.infinityreforged.moderncombat;

import com.infinityreforged.moderncombat.network.PacketSound;
import com.infinityreforged.moderncombat.network.PacketSweep;
import com.infinityreforged.moderncombat.network.PacketSwing;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import net.minecraftforge.common.MinecraftForge;

/**
 * Modern Combat: brings the Minecraft 1.9+ combat system to 1.7.10.
 */
@Mod(
    modid = ModernCombat.MODID,
    name = ModernCombat.NAME,
    version = ModernCombat.VERSION,
    acceptedMinecraftVersions = "[1.7.10]")
public class ModernCombat {

    public static final String MODID = "moderncombat";
    public static final String NAME = "Modern Combat";
    public static final String VERSION = "0.1.0";

    public static final Logger LOG = LogManager.getLogger(NAME);

    @SidedProxy(
        clientSide = "com.infinityreforged.moderncombat.client.ClientProxy",
        serverSide = "com.infinityreforged.moderncombat.CommonProxy")
    public static CommonProxy proxy;

    public static SimpleNetworkWrapper network;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.load(event.getSuggestedConfigurationFile());

        network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        network.registerMessage(PacketSwing.Handler.class, PacketSwing.class, 0, Side.SERVER);
        network.registerMessage(PacketSweep.Handler.class, PacketSweep.class, 1, Side.CLIENT);
        network.registerMessage(PacketSound.Handler.class, PacketSound.class, 2, Side.CLIENT);

        proxy.preInit();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        CombatEvents events = new CombatEvents();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance()
            .bus()
            .register(events);
        proxy.init();
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // All items are registered by now, so overrides by registry name can be resolved.
        WeaponStats.init();
    }
}
