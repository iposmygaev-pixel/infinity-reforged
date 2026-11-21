package com.infinityreforged.moderncombat;

import java.util.List;

import com.infinityreforged.moderncombat.network.PacketSound;
import com.infinityreforged.moderncombat.network.PacketSweep;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityMultiPart;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.potion.Potion;
import net.minecraft.stats.AchievementList;
import net.minecraft.stats.StatList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;

/**
 * Re-implementation of {@code EntityPlayer.attackTargetEntityWithCurrentItem} from Minecraft 1.9+
 * (attack cooldown, charged critical hits, sprint knockback and sweep attacks).
 */
public final class ModernAttack {

    /** The attack in progress on the current thread, so nested attacks reuse its charge. */
    static final class Context {

        final EntityPlayer player;
        final float strength;
        /** True while an item's own {@code onLeftClickEntity} handles the hit (damage scaled in LivingHurtEvent). */
        boolean selfHandled;

        Context(EntityPlayer player, float strength) {
            this.player = player;
            this.strength = strength;
        }
    }

    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<Context>();

    private ModernAttack() {}

    static Context active() {
        return ACTIVE.get();
    }

    /** Damage multiplier for a given charge: 0.2 + 0.8 * strength^2. */
    public static float chargeMultiplier(float strength) {
        return 0.2F + strength * strength * 0.8F;
    }

    public static void attack(EntityPlayer player, Entity target) {
        Context outer = ACTIVE.get();
        boolean nested = outer != null && outer.player == player;
        CombatData data = CombatData.get(player);
        float strength = nested ? outer.strength : data.attackStrength(player, 0.5F);

        Context ctx = new Context(player, strength);
        ACTIVE.set(ctx);
        try {
            doAttack(player, target, ctx, nested);
        } finally {
            if (!nested) data.resetCooldown();
            ACTIVE.set(outer);
        }
    }

    private static void doAttack(EntityPlayer player, Entity target, Context ctx, boolean nested) {
        ItemStack stack = player.getCurrentEquippedItem();
        if (stack != null) {
            ctx.selfHandled = true;
            boolean handled = stack.getItem()
                .onLeftClickEntity(stack, player, target);
            ctx.selfHandled = false;
            if (handled) return;
        }

        if (!target.canAttackWithItem() || target.hitByEntity(player)) return;

        float strength = ctx.strength;
        float damage = CombatMath.attackDamage(player);
        float enchantDamage = 0.0F;
        if (target instanceof EntityLivingBase) {
            enchantDamage = EnchantmentHelper.getEnchantmentModifierLiving(player, (EntityLivingBase) target);
        }

        damage *= chargeMultiplier(strength);
        enchantDamage *= strength;

        if (damage <= 0.0F && enchantDamage <= 0.0F) return;

        boolean charged = strength > 0.9F;
        boolean server = !player.worldObj.isRemote;

        int knockback = 0;
        if (target instanceof EntityLivingBase) {
            knockback += EnchantmentHelper.getKnockbackModifier(player, (EntityLivingBase) target);
        }

        boolean sprintKnockback = false;
        if (player.isSprinting() && charged) {
            if (server) playSound(player, PacketSound.KNOCKBACK);
            ++knockback;
            sprintKnockback = true;
        }

        boolean airborne = player.fallDistance > 0.0F && !player.onGround
            && !player.isOnLadder()
            && !player.isInWater()
            && !player.isPotionActive(Potion.blindness)
            && player.ridingEntity == null
            && target instanceof EntityLivingBase;
        boolean crit = ModConfig.modernCrits ? airborne && charged && !player.isSprinting() : airborne;

        if (crit) damage *= 1.5F;
        damage += enchantDamage;

        boolean sweep = false;
        if (ModConfig.sweepAttack && !nested && charged && !crit && !sprintKnockback && player.onGround) {
            double walked = player.distanceWalkedModified - player.prevDistanceWalkedModified;
            if (walked < player.getAIMoveSpeed() && stack != null && stack.getItem() instanceof ItemSword) {
                sweep = true;
            }
        }

        boolean ignited = false;
        int fireAspect = EnchantmentHelper.getFireAspectModifier(player);
        if (target instanceof EntityLivingBase && fireAspect > 0 && !target.isBurning()) {
            ignited = true;
            target.setFire(1);
        }

        boolean hurt = target.attackEntityFrom(DamageSource.causePlayerDamage(player), damage);
        if (!hurt) {
            if (server) playSound(player, PacketSound.NODAMAGE);
            if (ignited) target.extinguish();
            return;
        }

        float yawRad = player.rotationYaw * (float) Math.PI / 180.0F;
        if (knockback > 0) {
            target.addVelocity(
                -MathHelper.sin(yawRad) * knockback * 0.5F,
                0.1D,
                MathHelper.cos(yawRad) * knockback * 0.5F);
            player.motionX *= 0.6D;
            player.motionZ *= 0.6D;
            player.setSprinting(false);
        }

        if (sweep) {
            sweepAttack(player, target, yawRad);
        }

        if (crit) {
            if (server) playSound(player, PacketSound.CRIT);
            player.onCriticalHit(target);
        }
        if (!crit && !sweep && server) {
            playSound(player, charged ? PacketSound.STRONG : PacketSound.WEAK);
        }
        if (enchantDamage > 0.0F) {
            player.onEnchantmentCritical(target);
        }
        if (damage >= 18.0F) {
            player.triggerAchievement(AchievementList.overkill);
        }

        player.setLastAttacker(target);
        if (target instanceof EntityLivingBase) {
            EnchantmentHelper.func_151384_a((EntityLivingBase) target, player);
        }
        EnchantmentHelper.func_151385_b(player, target);

        ItemStack held = player.getCurrentEquippedItem();
        Entity hitEntity = target;
        if (target instanceof EntityDragonPart) {
            IEntityMultiPart multipart = ((EntityDragonPart) target).entityDragonObj;
            if (multipart instanceof EntityLivingBase) hitEntity = (EntityLivingBase) multipart;
        }
        if (held != null && hitEntity instanceof EntityLivingBase) {
            held.hitEntity((EntityLivingBase) hitEntity, player);
            if (held.stackSize <= 0) player.destroyCurrentEquippedItem();
        }

        if (target instanceof EntityLivingBase) {
            player.addStat(StatList.damageDealtStat, Math.round(damage * 10.0F));
            if (fireAspect > 0) target.setFire(fireAspect * 4);
        }

        player.addExhaustion(ModConfig.modernExhaustion ? 0.1F : 0.3F);
    }

    private static void sweepAttack(EntityPlayer player, Entity target, float yawRad) {
        double sin = MathHelper.sin(yawRad);
        double cos = MathHelper.cos(yawRad);

        @SuppressWarnings("unchecked")
        List<EntityLivingBase> nearby = player.worldObj
            .getEntitiesWithinAABB(EntityLivingBase.class, target.boundingBox.expand(1.0D, 0.25D, 1.0D));
        for (EntityLivingBase entity : nearby) {
            if (entity == player || entity == target || player.isOnSameTeam(entity)) continue;
            if (player.getDistanceSqToEntity(entity) >= 9.0D) continue;
            entity.knockBack(player, 0.4F, sin, -cos);
            entity.attackEntityFrom(DamageSource.causePlayerDamage(player), 1.0F);
        }

        if (!player.worldObj.isRemote) {
            playSound(player, PacketSound.SWEEP);
            double x = player.posX - sin;
            double y = player.boundingBox.minY + player.height * 0.5D;
            double z = player.posZ + cos;
            ModernCombat.network.sendToAllAround(
                new PacketSweep(x, y, z),
                new TargetPoint(player.dimension, x, y, z, 64.0D));
        }
    }

    private static void playSound(EntityPlayer player, int sound) {
        ModernCombat.network.sendToAllAround(
            new PacketSound(sound, player.posX, player.boundingBox.minY, player.posZ),
            new TargetPoint(player.dimension, player.posX, player.posY, player.posZ, 16.0D));
    }
}
