package com.infinityreforged.moderncombat.client;

import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.google.common.collect.Multimap;
import com.infinityreforged.moderncombat.CombatData;
import com.infinityreforged.moderncombat.ModConfig;
import com.infinityreforged.moderncombat.ModernCombat;
import com.infinityreforged.moderncombat.WeaponStats;
import com.infinityreforged.moderncombat.network.PacketSwing;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

public class ClientEvents {

    private static final String HUD = "moderncombat:textures/gui/sprites/hud/";
    private static final ResourceLocation CROSSHAIR_BG = new ResourceLocation(
        HUD + "crosshair_attack_indicator_background.png");
    private static final ResourceLocation CROSSHAIR_PROGRESS = new ResourceLocation(
        HUD + "crosshair_attack_indicator_progress.png");
    private static final ResourceLocation CROSSHAIR_FULL = new ResourceLocation(
        HUD + "crosshair_attack_indicator_full.png");
    private static final ResourceLocation HOTBAR_BG = new ResourceLocation(
        HUD + "hotbar_attack_indicator_background.png");
    private static final ResourceLocation HOTBAR_PROGRESS = new ResourceLocation(
        HUD + "hotbar_attack_indicator_progress.png");

    private static final DecimalFormat FORMAT = new DecimalFormat("#.##");

    private final Field leftClickCounter;
    private final Field equippedProgress;
    private final Field prevEquippedProgress;

    public ClientEvents() {
        leftClickCounter = findField(Minecraft.class, "leftClickCounter", "field_71429_W");
        equippedProgress = findField(ItemRenderer.class, "equippedProgress", "field_78454_c");
        prevEquippedProgress = findField(ItemRenderer.class, "prevEquippedProgress", "field_78451_d");
    }

    private static Field findField(Class<?> owner, String... names) {
        try {
            return ReflectionHelper.findField(owner, names);
        } catch (RuntimeException e) {
            ModernCombat.LOG.warn("Could not find field {} in {}; the related feature is disabled", names[0], owner);
            return null;
        }
    }

    // ---------------------------------------------------------------- input

    private boolean canSwing(Minecraft mc) {
        if (mc.thePlayer == null || mc.currentScreen != null || !mc.inGameHasFocus) return false;
        if (mc.thePlayer.isUsingItem()) return false;
        if (leftClickCounter != null) {
            try {
                if (leftClickCounter.getInt(mc) > 0) return false;
            } catch (IllegalAccessException ignored) {}
        }
        return true;
    }

    /** Swinging at air or a block resets the cooldown (hits on entities reset it in the attack itself). */
    private static void swing(Minecraft mc) {
        CombatData.get(mc.thePlayer)
            .resetCooldown();
        ModernCombat.network.sendToServer(new PacketSwing());
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.button != 0 || !event.buttonstate) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!canSwing(mc)) return;
        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) {
            swing(mc);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase == TickEvent.Phase.START) {
            Runnable task;
            while ((task = ClientProxy.TASKS.poll()) != null) {
                task.run();
            }
            return;
        }

        if (mc.thePlayer == null || mc.isGamePaused()) return;

        // Holding attack on a block swings every tick while mining.
        if (mc.gameSettings.keyBindAttack.getIsKeyPressed() && canSwing(mc)
            && mc.objectMouseOver != null
            && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            swing(mc);
        }

        lowerHeldItem(mc);
    }

    /** 1.9 ItemRenderer: the held item follows strength^3, so it dips after an attack. */
    private void lowerHeldItem(Minecraft mc) {
        if (!ModConfig.weaponLowerAnimation || !ModConfig.attackCooldown
            || equippedProgress == null
            || prevEquippedProgress == null) return;
        ItemRenderer renderer = mc.entityRenderer.itemRenderer;
        try {
            float prev = prevEquippedProgress.getFloat(renderer);
            float current = equippedProgress.getFloat(renderer);
            float strength = CombatData.get(mc.thePlayer)
                .attackStrength(mc.thePlayer, 1.0F);
            float target = strength * strength * strength;
            float delta = target - prev;
            if (delta < -0.4F) delta = -0.4F;
            if (delta > 0.4F) delta = 0.4F;
            float lowered = prev + delta;
            if (lowered < current) {
                equippedProgress.setFloat(renderer, lowered);
            }
        } catch (IllegalAccessException ignored) {}
    }

    // ---------------------------------------------------------------- HUD

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (!ModConfig.attackCooldown) return;
        String mode = ModConfig.attackIndicator;
        if (event.type == RenderGameOverlayEvent.ElementType.CROSSHAIRS && "CROSSHAIR".equals(mode)) {
            renderCrosshairIndicator(event.resolution);
        } else if (event.type == RenderGameOverlayEvent.ElementType.HOTBAR && "HOTBAR".equals(mode)) {
            renderHotbarIndicator(event.resolution);
        }
    }

    private void renderCrosshairIndicator(ScaledResolution res) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null || mc.gameSettings.thirdPersonView != 0) return;

        float strength = CombatData.get(player)
            .attackStrength(player, 0.0F);
        boolean ready = false;
        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop != null && mop.entityHit instanceof EntityLivingBase && strength >= 1.0F) {
            ready = CombatData.cooldownPeriod(player) > 5.0F && mop.entityHit.isEntityAlive();
        }
        if (!ready && strength >= 1.0F) return;

        int x = res.getScaledWidth() / 2 - 8;
        int y = res.getScaledHeight() / 2 - 7 + 16;

        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        if (ready) {
            drawSprite(mc, CROSSHAIR_FULL, x, y, 16, 16, 0, 0, 1, 1);
        } else {
            int filled = (int) (strength * 17.0F);
            drawSprite(mc, CROSSHAIR_BG, x, y, 16, 4, 0, 0, 1, 1);
            if (filled > 0) {
                drawSprite(mc, CROSSHAIR_PROGRESS, x, y, Math.min(filled, 16), 4, 0, 0, Math.min(filled, 16) / 16.0, 1);
            }
        }
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glDisable(GL11.GL_BLEND);
        mc.getTextureManager()
            .bindTexture(Gui.icons);
    }

    private void renderHotbarIndicator(ScaledResolution res) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;
        float strength = CombatData.get(player)
            .attackStrength(player, 0.0F);
        if (strength >= 1.0F) return;

        int x = res.getScaledWidth() / 2 + 91 + 6;
        int y = res.getScaledHeight() - 20;
        int filled = (int) (strength * 19.0F);
        if (filled > 18) filled = 18;

        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        drawSprite(mc, HOTBAR_BG, x, y, 18, 18, 0, 0, 1, 1);
        if (filled > 0) {
            drawSprite(mc, HOTBAR_PROGRESS, x, y + 18 - filled, 18, filled, 0, (18 - filled) / 18.0, 1, 1);
        }
        GL11.glDisable(GL11.GL_BLEND);
        mc.getTextureManager()
            .bindTexture(Gui.icons);
    }

    /** Draws part of a standalone sprite; UVs are normalized so any texture resolution works. */
    private static void drawSprite(Minecraft mc, ResourceLocation texture, int x, int y, int w, int h, double u0,
        double v0, double u1, double v1) {
        mc.getTextureManager()
            .bindTexture(texture);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x, y + h, 0, u0, v1);
        tess.addVertexWithUV(x + w, y + h, 0, u1, v1);
        tess.addVertexWithUV(x + w, y, 0, u1, v0);
        tess.addVertexWithUV(x, y, 0, u0, v0);
        tess.draw();
    }

    // ---------------------------------------------------------------- tooltips

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onTooltip(ItemTooltipEvent event) {
        if (!ModConfig.modernTooltips || !ModConfig.attackCooldown || event.itemStack == null) return;
        ItemStack stack = event.itemStack;
        List<String> tip = event.toolTip;
        WeaponStats.Stats stats = WeaponStats.get(stack);

        String damageName = StatCollector.translateToLocal("attribute.name.generic.attackDamage");
        String header = EnumChatFormatting.GRAY + StatCollector.translateToLocal("moderncombat.tooltip.mainhand");
        String speedLine = EnumChatFormatting.DARK_GREEN + " "
            + FORMAT.format(stats.speed)
            + " "
            + StatCollector.translateToLocal("moderncombat.tooltip.attackSpeed");

        @SuppressWarnings("unchecked")
        Multimap<String, AttributeModifier> modifiers = stack.getAttributeModifiers();
        Collection<AttributeModifier> damageMods = modifiers
            .get(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName());

        for (AttributeModifier mod : damageMods) {
            if (mod.getOperation() != 0) continue;
            String vanillaLine = EnumChatFormatting.BLUE + StatCollector.translateToLocalFormatted(
                "attribute.modifier.plus.0",
                ItemStack.field_111284_a.format(mod.getAmount()),
                damageName);
            int index = tip.indexOf(vanillaLine);
            if (index < 0) continue;
            double total = 1.0 + mod.getAmount() + stats.damageDelta;
            tip.set(index, EnumChatFormatting.DARK_GREEN + " " + FORMAT.format(total) + " " + damageName);
            tip.add(index + 1, speedLine);
            tip.add(index, header);
            return;
        }

        // No damage modifier (e.g. Tinkers' tools): show attack speed only if it differs from the fist.
        if (stats.speed != WeaponStats.BASE_ATTACK_SPEED) {
            tip.add("");
            tip.add(header);
            tip.add(speedLine);
        }
    }
}
