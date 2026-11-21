package com.infinityreforged.moderncombat.network;

import com.infinityreforged.moderncombat.ModernCombat;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;

/**
 * Server -> client: what a player holds in the off hand and whether the off-hand shield is raised (for rendering).
 */
public class PacketOffhandSync implements IMessage {

    public int entityId;
    public ItemStack stack;
    public boolean blocking;

    public PacketOffhandSync() {}

    public PacketOffhandSync(int entityId, ItemStack stack, boolean blocking) {
        this.entityId = entityId;
        this.stack = stack;
        this.blocking = blocking;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        blocking = buf.readBoolean();
        stack = ByteBufUtils.readItemStack(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(blocking);
        ByteBufUtils.writeItemStack(buf, stack);
    }

    public static class Handler implements IMessageHandler<PacketOffhandSync, IMessage> {

        @Override
        public IMessage onMessage(PacketOffhandSync message, MessageContext ctx) {
            ModernCombat.proxy.handleOffhandSync(message);
            return null;
        }
    }
}
