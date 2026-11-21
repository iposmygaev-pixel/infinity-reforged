package com.infinityreforged.moderncombat;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;

/**
 * Common (both sides) event handlers.
 */
public class CombatEvents {

    @SubscribeEvent
    public void onEntityConstructing(EntityEvent.EntityConstructing event) {
        if (event.entity instanceof EntityPlayer) {
            event.entity.registerExtendedProperties(CombatData.KEY, new CombatData());
        }
    }

    /** Mirrors the 1.9 cooldown bookkeeping in EntityLivingBase/EntityPlayer.onUpdate. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.player instanceof FakePlayer) return;
        EntityPlayer player = event.player;
        CombatData data = CombatData.get(player);

        if (data.swingPending) {
            data.swingPending = false;
            data.resetCooldown();
        }
        ++data.ticksSinceLastSwing;
        if (data.shieldCooldown > 0) {
            data.shieldCooldown--;
            ItemStack using = player.getItemInUse();
            if (using != null && using.getItem() instanceof ItemShield) player.clearItemInUse();
        }

        ItemStack held = player.getCurrentEquippedItem();
        if (!CombatMath.sameItemIgnoreDurability(data.lastHeld, held)) {
            data.resetCooldown();
        }
        data.lastHeld = held == null ? null : held.copy();
    }

    /** Replaces the vanilla 1.7.10 melee attack with the 1.9+ one. Runs last so other mods can cancel first. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onAttackEntity(AttackEntityEvent event) {
        EntityPlayer player = event.entityPlayer;
        if (player instanceof FakePlayer || event.target == null) return;
        event.setCanceled(true);
        ModernAttack.attack(player, event.target);
    }

    /** Weapons with their own attack code (Tinkers' Construct etc.): scale their damage by the charge. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingHurt(LivingHurtEvent event) {
        if (!ModConfig.scaleSelfHandledAttacks) return;
        ModernAttack.Context ctx = ModernAttack.active();
        if (ctx == null || !ctx.selfHandled) return;
        if (event.source.getEntity() == ctx.player && event.source.getSourceOfDamage() == ctx.player) {
            event.ammount *= ModernAttack.chargeMultiplier(ctx.strength);
        }
    }

    /** Runs after other mods changed the damage, right before 1.7.10 applies armor. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingHurtArmor(LivingHurtEvent event) {
        if (ModConfig.modernArmor) ModernArmor.onLivingHurt(event);
    }

    @SubscribeEvent
    public void onJoinWorld(EntityJoinWorldEvent event) {
        if (!event.world.isRemote && event.entity instanceof EntityPlayer && !(event.entity instanceof FakePlayer)) {
            ModernFoodStats.install((EntityPlayer) event.entity);
        }
    }

    /** 1.9 removed blocking with swords. */
    @SubscribeEvent
    public void onUseItemStart(PlayerUseItemEvent.Start event) {
        if (!ModConfig.disableSwordBlocking || event.item == null) return;
        if (event.item.getItem() instanceof ItemSword && event.item.getItemUseAction() == EnumAction.block) {
            event.setCanceled(true);
        }
    }
}
