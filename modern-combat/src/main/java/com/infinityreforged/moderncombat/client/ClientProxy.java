package com.infinityreforged.moderncombat.client;

import java.util.concurrent.ConcurrentLinkedQueue;

import com.infinityreforged.moderncombat.CommonProxy;
import com.infinityreforged.moderncombat.CombatData;
import com.infinityreforged.moderncombat.ModConfig;
import com.infinityreforged.moderncombat.ModernCombat;
import com.infinityreforged.moderncombat.network.PacketOffhandSync;
import com.infinityreforged.moderncombat.network.PacketShieldCooldown;
import com.infinityreforged.moderncombat.network.PacketSound;
import com.infinityreforged.moderncombat.network.PacketSweep;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {

    /** Network handlers run on the Netty thread in 1.7.10; work is handed to the client thread here. */
    static final ConcurrentLinkedQueue<Runnable> TASKS = new ConcurrentLinkedQueue<Runnable>();

    @Override
    public void init() {
        MinecraftForgeClient.registerItemRenderer(ModernCombat.shield, new ShieldRenderer());
        ClientEvents events = new ClientEvents();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance()
            .bus()
            .register(events);
        if (ModConfig.offhand) {
            OffhandInput input = new OffhandInput();
            FMLCommonHandler.instance()
                .bus()
                .register(input);
            MinecraftForge.EVENT_BUS.register(new OffhandRender());
        }
    }

    @Override
    public void handleOffhandSync(final PacketOffhandSync message) {
        TASKS.add(new Runnable() {

            @Override
            public void run() {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.theWorld == null) return;
                Entity entity = mc.theWorld.getEntityByID(message.entityId);
                // Our own off hand comes from the inventory container and local input.
                if (!(entity instanceof EntityPlayer) || entity == mc.thePlayer) return;
                CombatData data = CombatData.get((EntityPlayer) entity);
                data.offhand = message.stack;
                data.offhandBlocking = message.blocking;
            }
        });
    }

    @Override
    public void handleSweep(final PacketSweep message) {
        TASKS.add(new Runnable() {

            @Override
            public void run() {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.theWorld == null) return;
                mc.effectRenderer.addEffect(new EntitySweepFX(mc.theWorld, message.x, message.y, message.z));
            }
        });
    }

    @Override
    public void handleSound(final PacketSound message) {
        TASKS.add(new Runnable() {

            @Override
            public void run() {
                if (message.sound < 0 || message.sound >= PacketSound.NAMES.length) return;
                Minecraft mc = Minecraft.getMinecraft();
                ResourceLocation id = new ResourceLocation("moderncombat", PacketSound.NAMES[message.sound]);
                // Sounds come from the optional asset pack; stay silent instead of logging warnings if absent.
                if (mc.getSoundHandler()
                    .getSound(id) == null) return;
                mc.getSoundHandler()
                    .playSound(
                        new PositionedSoundRecord(
                            id,
                            1.0F,
                            1.0F,
                            (float) message.x,
                            (float) message.y,
                            (float) message.z));
            }
        });
    }

    @Override
    public void handleShieldCooldown(final PacketShieldCooldown message) {
        TASKS.add(new Runnable() {

            @Override
            public void run() {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.thePlayer == null) return;
                CombatData.get(mc.thePlayer).shieldCooldown = message.ticks;
                mc.thePlayer.clearItemInUse();
            }
        });
    }
}
