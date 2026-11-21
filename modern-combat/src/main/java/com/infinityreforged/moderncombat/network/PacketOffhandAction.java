package com.infinityreforged.moderncombat.network;

import com.infinityreforged.moderncombat.CombatData;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client -> server: an off-hand action. Applied on the next player tick (handlers run on the network thread).
 */
public class PacketOffhandAction implements IMessage {

    public static final int SWAP = 0;
    public static final int RAISE_SHIELD = 1;
    public static final int LOWER_SHIELD = 2;

    public int action;

    public PacketOffhandAction() {}

    public PacketOffhandAction(int action) {
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
    }

    public static class Handler implements IMessageHandler<PacketOffhandAction, IMessage> {

        @Override
        public IMessage onMessage(PacketOffhandAction message, MessageContext ctx) {
            CombatData data = CombatData.get(ctx.getServerHandler().playerEntity);
            switch (message.action) {
                case SWAP:
                    data.swapPending = true;
                    break;
                case RAISE_SHIELD:
                    data.blockRequest = 1;
                    break;
                case LOWER_SHIELD:
                    data.blockRequest = 0;
                    break;
                default:
                    break;
            }
            return null;
        }
    }
}
