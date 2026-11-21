package com.infinityreforged.moderncombat.network;

import com.infinityreforged.moderncombat.ModernCombat;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server -> client: play a 1.9+ attack sound. Clients play it only if a resource pack provides it.
 */
public class PacketSound implements IMessage {

    public static final int KNOCKBACK = 0;
    public static final int CRIT = 1;
    public static final int SWEEP = 2;
    public static final int STRONG = 3;
    public static final int WEAK = 4;
    public static final int NODAMAGE = 5;
    public static final int SHIELD_BLOCK = 6;

    public static final String[] NAMES = { "attack.knockback", "attack.crit", "attack.sweep", "attack.strong",
        "attack.weak", "attack.nodamage", "shield.block" };

    public int sound;
    public double x;
    public double y;
    public double z;

    public PacketSound() {}

    public PacketSound(int sound, double x, double y, double z) {
        this.sound = sound;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        sound = buf.readByte();
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(sound);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    public static class Handler implements IMessageHandler<PacketSound, IMessage> {

        @Override
        public IMessage onMessage(PacketSound message, MessageContext ctx) {
            ModernCombat.proxy.handleSound(message);
            return null;
        }
    }
}
