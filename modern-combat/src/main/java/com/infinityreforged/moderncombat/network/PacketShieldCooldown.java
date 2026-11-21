package com.infinityreforged.moderncombat.network;

import com.infinityreforged.moderncombat.ModernCombat;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server -> client: the shield was disabled by an axe for the given number of ticks.
 */
public class PacketShieldCooldown implements IMessage {

    public int ticks;

    public PacketShieldCooldown() {}

    public PacketShieldCooldown(int ticks) {
        this.ticks = ticks;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ticks = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(ticks);
    }

    public static class Handler implements IMessageHandler<PacketShieldCooldown, IMessage> {

        @Override
        public IMessage onMessage(PacketShieldCooldown message, MessageContext ctx) {
            ModernCombat.proxy.handleShieldCooldown(message);
            return null;
        }
    }
}
