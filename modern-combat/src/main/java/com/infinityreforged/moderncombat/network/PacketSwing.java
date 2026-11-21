package com.infinityreforged.moderncombat.network;

import com.infinityreforged.moderncombat.CombatData;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client -> server: the player swung without hitting an entity (air or block), which resets the cooldown in 1.9+.
 */
public class PacketSwing implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketSwing, IMessage> {

        @Override
        public IMessage onMessage(PacketSwing message, MessageContext ctx) {
            CombatData.get(ctx.getServerHandler().playerEntity).swingPending = true;
            return null;
        }
    }
}
