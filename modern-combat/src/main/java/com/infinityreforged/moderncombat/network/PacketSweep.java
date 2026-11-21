package com.infinityreforged.moderncombat.network;

import com.infinityreforged.moderncombat.ModernCombat;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server -> client: spawn a sweep attack particle.
 */
public class PacketSweep implements IMessage {

    public double x;
    public double y;
    public double z;

    public PacketSweep() {}

    public PacketSweep(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    public static class Handler implements IMessageHandler<PacketSweep, IMessage> {

        @Override
        public IMessage onMessage(PacketSweep message, MessageContext ctx) {
            ModernCombat.proxy.handleSweep(message);
            return null;
        }
    }
}
