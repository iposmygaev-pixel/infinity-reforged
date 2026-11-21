package com.infinityreforged.moderncombat;

import java.util.Set;

import com.infinityreforged.moderncombat.network.PacketShieldCooldown;
import com.infinityreforged.moderncombat.network.PacketSound;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

/**
 * Shield blocking, following EntityLivingBase/EntityPlayer of Minecraft 1.9+.
 */
public class ShieldHandler {

    /** Ticks an axe hit disables the shield for. */
    public static final int AXE_COOLDOWN = 100;

    /** 1.9 {@code isActiveItemStackBlocking}: the shield protects after being raised for 5 ticks. */
    public static boolean isBlocking(EntityPlayer player) {
        ItemStack using = player.getItemInUse();
        return player.isUsingItem() && using != null
            && using.getItem() instanceof ItemShield
            && player.getItemInUseDuration() >= 5;
    }

    /** 1.9 {@code canBlockDamageSource}: unblockable sources pass; the source must be in front of the player. */
    private static boolean canBlock(EntityPlayer player, DamageSource source) {
        if (source.isUnblockable() || !isBlocking(player)) return false;
        Entity from = source.getSourceOfDamage();
        if (from == null) return false;
        Vec3 look = player.getLook(1.0F);
        double dx = player.posX - from.posX;
        double dz = player.posZ - from.posZ;
        double len = MathHelper.sqrt_double(dx * dx + dz * dz);
        if (len < 1.0E-4) return true;
        return (dx / len) * look.xCoord + (dz / len) * look.zCoord < 0.0D;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingAttack(LivingAttackEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer) || event.entityLiving.worldObj.isRemote) return;
        EntityPlayer player = (EntityPlayer) event.entityLiving;
        if (event.ammount <= 0.0F || !canBlock(player, event.source)) return;

        event.setCanceled(true);
        damageShield(player, event.ammount);
        playSound(player, PacketSound.SHIELD_BLOCK);

        Entity immediate = event.source.getSourceOfDamage();
        if (!event.source.isProjectile() && immediate instanceof EntityLivingBase) {
            blockUsingShield(player, (EntityLivingBase) immediate);
        }
    }

    /** 1.9 {@code damageShield}: hits of 3+ cost 1 + floor(damage) durability. */
    private static void damageShield(EntityPlayer player, float damage) {
        ItemStack shield = player.getItemInUse();
        if (damage < 3.0F || shield == null) return;
        shield.damageItem(1 + MathHelper.floor_float(damage), player);
        if (shield.stackSize <= 0) {
            player.clearItemInUse();
            if (player.getCurrentEquippedItem() == shield) {
                player.destroyCurrentEquippedItem();
            }
        }
    }

    /** 1.9 {@code blockUsingShield}: push the attacker back; an axe may disable the shield. */
    private static void blockUsingShield(EntityPlayer player, EntityLivingBase attacker) {
        attacker.knockBack(player, 0.5F, player.posX - attacker.posX, player.posZ - attacker.posZ);
        if (attacker instanceof EntityPlayer && isAxe(((EntityPlayer) attacker).getCurrentEquippedItem())) {
            ItemStack axe = ((EntityPlayer) attacker).getCurrentEquippedItem();
            float chance = 0.25F + EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, axe) * 0.05F;
            if (attacker.isSprinting()) chance += 0.75F;
            if (player.getRNG()
                .nextFloat() < chance) {
                disableShield(player);
            }
        }
    }

    private static boolean isAxe(ItemStack stack) {
        if (stack == null) return false;
        if (stack.getItem() instanceof ItemAxe) return true;
        Set<String> classes = stack.getItem()
            .getToolClasses(stack);
        return classes != null && classes.contains("axe");
    }

    private static void disableShield(EntityPlayer player) {
        player.clearItemInUse();
        CombatData.get(player).shieldCooldown = AXE_COOLDOWN;
        player.worldObj
            .playSoundAtEntity(player, "random.break", 0.8F, 0.8F + player.worldObj.rand.nextFloat() * 0.4F);
        if (player instanceof EntityPlayerMP) {
            ModernCombat.network.sendTo(new PacketShieldCooldown(AXE_COOLDOWN), (EntityPlayerMP) player);
        }
    }

    private static void playSound(EntityPlayer player, int sound) {
        ModernCombat.network.sendToAllAround(
            new PacketSound(sound, player.posX, player.boundingBox.minY, player.posZ),
            new TargetPoint(player.dimension, player.posX, player.posY, player.posZ, 16.0D));
    }
}
