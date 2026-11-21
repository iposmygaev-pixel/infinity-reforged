package com.infinityreforged.moderncombat.offhand;

import com.infinityreforged.moderncombat.CombatData;
import com.infinityreforged.moderncombat.ItemShield;
import com.infinityreforged.moderncombat.ModernCombat;
import com.infinityreforged.moderncombat.network.PacketOffhandSync;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * The 1.9+ off hand: an extra slot in the player's inventory (shown next to the player in the inventory screen),
 * F swaps it with the held item, and a shield in it can be raised with the use key.
 * <p>
 * 1.7.10 has no second hand, so the slot is added to the player's inventory container on both sides
 * (same index on client and server) and the stack is saved in {@link CombatData}.
 */
public class Offhand {

    /** Position of the slot in the survival inventory screen, as in 1.9+. */
    public static final int SLOT_X = 77;
    public static final int SLOT_Y = 62;

    public static class SlotOffhand extends Slot {

        public SlotOffhand(EntityPlayer player) {
            super(new InventoryOffhand(player), 0, SLOT_X, SLOT_Y);
        }
    }

    public static ItemStack get(EntityPlayer player) {
        return CombatData.get(player).offhand;
    }

    public static boolean isShield(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemShield;
    }

    /** The off-hand shield is raised (it protects only after 5 ticks, see ShieldHandler). */
    public static boolean isRaised(EntityPlayer player) {
        CombatData data = CombatData.get(player);
        return data.offhandBlocking && isShield(data.offhand) && data.shieldCooldown <= 0;
    }

    /** The off-hand slot of the player's inventory container, adding it if needed. */
    public static Slot slot(EntityPlayer player) {
        Container container = player.inventoryContainer;
        if (container == null) return null;
        for (Object o : container.inventorySlots) {
            if (o instanceof SlotOffhand) return (Slot) o;
        }
        SlotOffhand slot = new SlotOffhand(player);
        slot.slotNumber = container.inventorySlots.size();
        container.inventorySlots.add(slot);
        container.inventoryItemStacks.add(null);
        return slot;
    }

    // ---------------------------------------------------------------- events (both sides)

    @SubscribeEvent
    public void onJoinWorld(EntityJoinWorldEvent event) {
        if (event.entity instanceof EntityPlayer && !(event.entity instanceof FakePlayer)) {
            slot((EntityPlayer) event.entity);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.worldObj.isRemote
            || !(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        CombatData data = CombatData.get(player);

        if (data.swapPending) {
            data.swapPending = false;
            swapHands(player, data);
        }
        int request = data.blockRequest;
        if (request >= 0) {
            data.blockRequest = -1;
            if (request == 1) {
                if (!data.offhandBlocking && isShield(data.offhand) && data.shieldCooldown <= 0 && !player.isUsingItem()) {
                    data.offhandBlocking = true;
                    data.offhandBlockStart = player.ticksExisted;
                }
            } else {
                data.offhandBlocking = false;
            }
        }
        if (data.offhandBlocking && (!isShield(data.offhand) || data.shieldCooldown > 0 || player.isUsingItem())) {
            data.offhandBlocking = false;
        }

        if (!ItemStack.areItemStacksEqual(data.offhand, data.syncedOffhand) || data.offhandBlocking != data.syncedBlocking) {
            data.syncedOffhand = data.offhand == null ? null : data.offhand.copy();
            data.syncedBlocking = data.offhandBlocking;
            Packet packet = ModernCombat.network.getPacketFrom(syncMessage(player));
            // To everyone who sees the player (the player's own slot is synced by the inventory container).
            ((WorldServer) player.worldObj).getEntityTracker()
                .func_151247_a(player, packet);
        }
    }

    private static PacketOffhandSync syncMessage(EntityPlayer player) {
        CombatData data = CombatData.get(player);
        return new PacketOffhandSync(player.getEntityId(), data.offhand, data.offhandBlocking);
    }

    /** 1.9+ "Swap Item With Offhand" (F). */
    private static void swapHands(EntityPlayerMP player, CombatData data) {
        int current = player.inventory.currentItem;
        if (current < 0 || current >= 9) return;
        ItemStack main = player.inventory.mainInventory[current];
        player.inventory.mainInventory[current] = data.offhand;
        data.offhand = main;
        data.offhandBlocking = false;
        player.clearItemInUse();
        player.inventoryContainer.detectAndSendChanges();
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.target instanceof EntityPlayer && event.entityPlayer instanceof EntityPlayerMP) {
            ModernCombat.network.sendTo(syncMessage((EntityPlayer) event.target), (EntityPlayerMP) event.entityPlayer);
        }
    }

    /** Respawn / return from the End: keep the off hand like the rest of the inventory. */
    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        boolean keep = !event.wasDeath || event.original.worldObj.getGameRules()
            .getGameRuleBooleanValue("keepInventory");
        if (keep) {
            CombatData.get(event.entityPlayer).offhand = CombatData.get(event.original).offhand;
        }
    }

    @SubscribeEvent
    public void onDrops(PlayerDropsEvent event) {
        EntityPlayer player = event.entityPlayer;
        if (player.worldObj.getGameRules()
            .getGameRuleBooleanValue("keepInventory")) return;
        CombatData data = CombatData.get(player);
        if (data.offhand == null) return;
        EntityItem item = new EntityItem(
            player.worldObj,
            player.posX,
            player.posY - 0.3D + player.getEyeHeight(),
            player.posZ,
            data.offhand);
        item.delayBeforeCanPickup = 40;
        float speed = player.getRNG()
            .nextFloat() * 0.5F;
        float angle = player.getRNG()
            .nextFloat() * (float) Math.PI * 2.0F;
        item.motionX = -Math.sin(angle) * speed;
        item.motionZ = Math.cos(angle) * speed;
        item.motionY = 0.2D;
        event.drops.add(item);
        data.offhand = null;
        data.offhandBlocking = false;
    }
}
