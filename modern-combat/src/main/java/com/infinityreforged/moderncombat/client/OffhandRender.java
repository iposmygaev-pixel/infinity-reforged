package com.infinityreforged.moderncombat.client;

import java.lang.reflect.Method;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.util.glu.Project;

import com.infinityreforged.moderncombat.CombatData;
import com.infinityreforged.moderncombat.ModernCombat;
import com.infinityreforged.moderncombat.ShieldHandler;
import com.infinityreforged.moderncombat.offhand.InventoryOffhand;
import com.infinityreforged.moderncombat.offhand.Offhand;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;

/**
 * Draws the off hand: in the left hand (first and third person), next to the hotbar, and the slot frame in the
 * inventory screen (1.7.10's inventory texture has no off-hand slot).
 */
public class OffhandRender {

    /** Empty off-hand slot sprite from the modern-assets pack (optional). */
    private static final ResourceLocation SLOT_SHIELD = new ResourceLocation(
        ModernCombat.MODID,
        "textures/gui/slot_shield.png");
    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");

    private static final RenderItem ITEM_RENDER = new RenderItem();

    /** Set while an off-hand item is drawn, so the shield renderer can tell which hand it is in. */
    static boolean renderingOffhand;

    private final Method fovModifier;
    private final Method hurtCamera;
    private final Method viewBobbing;
    private final java.lang.reflect.Field guiLeft;
    private final java.lang.reflect.Field guiTop;

    private final Method renderToolTip;
    private Boolean hasSlotSprite;

    public OffhandRender() {
        fovModifier = method("getFOVModifier", "func_78481_a", float.class, boolean.class);
        hurtCamera = method("hurtCameraEffect", "func_78482_e", float.class);
        viewBobbing = method("setupViewBobbing", "func_78475_f", float.class);
        guiLeft = field(GuiContainer.class, "guiLeft", "field_147003_i");
        guiTop = field(GuiContainer.class, "guiTop", "field_147009_r");
        Method tip = null;
        try {
            tip = ReflectionHelper.findMethod(
                GuiScreen.class,
                null,
                new String[] { "renderToolTip", "func_146285_a" },
                ItemStack.class,
                int.class,
                int.class);
            tip.setAccessible(true);
        } catch (RuntimeException e) {
            ModernCombat.LOG.warn("Could not find GuiScreen.renderToolTip");
        }
        renderToolTip = tip;
    }

    private static Method method(String name, String srg, Class<?>... args) {
        try {
            Method m = ReflectionHelper.findMethod(EntityRenderer.class, null, new String[] { name, srg }, args);
            m.setAccessible(true);
            return m;
        } catch (RuntimeException e) {
            ModernCombat.LOG.warn("Could not find EntityRenderer.{}; off-hand rendering is simplified", name);
            return null;
        }
    }

    private static java.lang.reflect.Field field(Class<?> owner, String... names) {
        try {
            return ReflectionHelper.findField(owner, names);
        } catch (RuntimeException e) {
            ModernCombat.LOG.warn("Could not find {}.{}", owner.getSimpleName(), names[0]);
            return null;
        }
    }

    static boolean isOffhandStack(Object holder, ItemStack stack) {
        return holder instanceof EntityPlayer && stack != null && Offhand.get((EntityPlayer) holder) == stack;
    }

    // ---------------------------------------------------------------- first person

    /**
     * Fired right before the main hand is drawn. Sets up the same camera the hand uses, draws the off-hand item
     * mirrored into the left hand, and restores everything; the game then clears the depth buffer and draws the
     * main hand on top.
     */
    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityClientPlayerMP player = mc.thePlayer;
        if (player == null || mc.gameSettings.thirdPersonView != 0
            || mc.gameSettings.hideGUI
            || mc.gameSettings.anaglyph
            || mc.renderViewEntity != player
            || player.isPlayerSleeping()
            || mc.playerController.enableEverythingIsScrewedUpMode()) return;
        ItemStack stack = Offhand.get(player);
        if (stack == null) return;
        float pt = event.partialTicks;
        EntityRenderer er = mc.entityRenderer;

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        float fov = 70.0F;
        try {
            if (fovModifier != null) fov = (Float) fovModifier.invoke(er, pt, false);
        } catch (Exception ignored) {}
        Project.gluPerspective(fov, (float) mc.displayWidth / (float) mc.displayHeight, 0.05F, mc.gameSettings.renderDistanceChunks * 32.0F);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            if (hurtCamera != null) hurtCamera.invoke(er, pt);
            if (viewBobbing != null && mc.gameSettings.viewBobbing) viewBobbing.invoke(er, pt);
        } catch (Exception ignored) {}
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        er.enableLightmap(pt);
        renderFirstPerson(mc, player, stack, pt);
        er.disableLightmap(pt);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }

    /** ItemRenderer.renderItemInFirstPerson for a held item, mirrored to the left. */
    private void renderFirstPerson(Minecraft mc, EntityClientPlayerMP player, ItemStack stack, float pt) {
        float pitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * pt;
        GL11.glPushMatrix();
        GL11.glRotatef(pitch, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * pt, 0.0F, 1.0F, 0.0F);
        RenderHelper.enableStandardItemLighting();
        GL11.glPopMatrix();

        EntityPlayerSP sp = player;
        float armPitch = sp.prevRenderArmPitch + (sp.renderArmPitch - sp.prevRenderArmPitch) * pt;
        float armYaw = sp.prevRenderArmYaw + (sp.renderArmYaw - sp.prevRenderArmYaw) * pt;
        GL11.glRotatef((player.rotationPitch - armPitch) * 0.1F, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef((player.rotationYaw - armYaw) * 0.1F, 0.0F, 1.0F, 0.0F);

        int light = mc.theWorld.getLightBrightnessForSkyBlocks(
            MathHelper.floor_double(player.posX),
            MathHelper.floor_double(player.posY),
            MathHelper.floor_double(player.posZ),
            0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        GL11.glPushMatrix();
        // Mirror into the left hand; mirrored faces wind clockwise.
        GL11.glScalef(-1.0F, 1.0F, 1.0F);
        GL11.glFrontFace(GL11.GL_CW);
        float f = 0.8F;
        GL11.glTranslatef(0.7F * f, -0.65F * f, -0.9F * f);
        GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glScalef(0.4F, 0.4F, 0.4F);
        if (stack.getItem()
            .shouldRotateAroundWhenRendering()) {
            GL11.glRotatef(180.0F, 0.0F, 1.0F, 0.0F);
        }
        renderPasses(mc.entityRenderer.itemRenderer, player, stack, ItemRenderType.EQUIPPED_FIRST_PERSON);
        GL11.glFrontFace(GL11.GL_CCW);
        GL11.glPopMatrix();

        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
    }

    private static void renderPasses(ItemRenderer renderer, EntityPlayer player, ItemStack stack, ItemRenderType type) {
        renderingOffhand = true;
        try {
            int passes = stack.getItem()
                .requiresMultipleRenderPasses()
                    ? stack.getItem()
                        .getRenderPasses(stack.getItemDamage())
                    : 1;
            for (int pass = 0; pass < passes; pass++) {
                int color = stack.getItem()
                    .getColorFromItemStack(stack, pass);
                GL11.glColor4f((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F, 1.0F);
                renderer.renderItem(player, stack, pass, type);
            }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            renderingOffhand = false;
        }
    }

    // ---------------------------------------------------------------- third person

    @SubscribeEvent
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        ItemStack stack = Offhand.get(event.entityPlayer);
        int pose = stack == null ? 0 : (Offhand.isRaised(event.entityPlayer) ? 3 : 1);
        setLeftArm(event.renderer, pose);
    }

    @SubscribeEvent
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        setLeftArm(event.renderer, 0);
    }

    private static void setLeftArm(RenderPlayer renderer, int pose) {
        renderer.modelBipedMain.heldItemLeft = pose;
        renderer.modelArmor.heldItemLeft = pose;
        renderer.modelArmorChestplate.heldItemLeft = pose;
    }

    /** RenderPlayer.renderEquippedItems for the held item, mirrored to the left arm. */
    @SubscribeEvent
    public void onRenderSpecials(RenderPlayerEvent.Specials.Post event) {
        EntityPlayer player = event.entityPlayer;
        ItemStack stack = Offhand.get(player);
        if (stack == null) return;
        GL11.glPushMatrix();
        event.renderer.modelBipedMain.bipedLeftArm.postRender(0.0625F);
        GL11.glScalef(-1.0F, 1.0F, 1.0F);
        GL11.glFrontFace(GL11.GL_CW);
        GL11.glTranslatef(-0.0625F, 0.4375F, 0.0625F);

        IItemRenderer custom = MinecraftForgeClient.getItemRenderer(stack, ItemRenderType.EQUIPPED);
        boolean is3D = custom != null
            && custom.shouldUseRenderHelper(ItemRenderType.EQUIPPED, stack, IItemRenderer.ItemRendererHelper.BLOCK_3D);
        if (is3D || stack.getItem() instanceof ItemBlock
            && RenderBlocks.renderItemIn3d(Block.getBlockFromItem(stack.getItem()).getRenderType())) {
            float s = 0.5F * 0.75F;
            GL11.glTranslatef(0.0F, 0.1875F, -0.3125F);
            GL11.glRotatef(20.0F, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
            GL11.glScalef(-s, -s, s);
        } else if (stack.getItem()
            .isFull3D()) {
                float s = 0.625F;
                if (stack.getItem()
                    .shouldRotateAroundWhenRendering()) {
                    GL11.glRotatef(180.0F, 0.0F, 0.0F, 1.0F);
                    GL11.glTranslatef(0.0F, -0.125F, 0.0F);
                }
                GL11.glTranslatef(0.0F, 0.1875F, 0.0F);
                GL11.glScalef(s, -s, s);
                GL11.glRotatef(-100.0F, 1.0F, 0.0F, 0.0F);
                GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
            } else {
                float s = 0.375F;
                GL11.glTranslatef(0.25F, 0.1875F, -0.1875F);
                GL11.glScalef(s, s, s);
                GL11.glRotatef(60.0F, 0.0F, 0.0F, 1.0F);
                GL11.glRotatef(-90.0F, 1.0F, 0.0F, 0.0F);
                GL11.glRotatef(20.0F, 0.0F, 0.0F, 1.0F);
            }
        renderPasses(Minecraft.getMinecraft().entityRenderer.itemRenderer, player, stack, ItemRenderType.EQUIPPED);
        GL11.glFrontFace(GL11.GL_CCW);
        GL11.glPopMatrix();
    }

    // ---------------------------------------------------------------- HUD

    /** 1.9+ off-hand slot left of the hotbar. */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        ItemStack stack = Offhand.get(mc.thePlayer);
        if (stack == null) return;
        ScaledResolution res = event.resolution;
        int x = res.getScaledWidth() / 2 - 91 - 26;
        int y = res.getScaledHeight() - 22;

        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager()
            .bindTexture(WIDGETS);
        // A one-slot frame from the two ends of the hotbar texture.
        drawRect(x, y, 0, 0, 11, 22);
        drawRect(x + 11, y, 171, 0, 11, 22);
        GL11.glDisable(GL11.GL_BLEND);

        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        ITEM_RENDER.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x + 3, y + 3);
        ITEM_RENDER.renderItemOverlayIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x + 3, y + 3);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);

        int cooldown = CombatData.get(mc.thePlayer).shieldCooldown;
        if (cooldown > 0 && Offhand.isShield(stack)) {
            int height = Math.round(16.0F * cooldown / ShieldHandler.AXE_COOLDOWN);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            Gui.drawRect(x + 3, y + 3 + 16 - height, x + 19, y + 19, 0x7FFFFFFF);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager()
            .bindTexture(Gui.icons);
    }

    private static void drawRect(int x, int y, int u, int v, int w, int h) {
        float t = 1.0F / 256.0F;
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x, y + h, 0, u * t, (v + h) * t);
        tess.addVertexWithUV(x + w, y + h, 0, (u + w) * t, (v + h) * t);
        tess.addVertexWithUV(x + w, y, 0, (u + w) * t, v * t);
        tess.addVertexWithUV(x, y, 0, u * t, v * t);
        tess.draw();
    }

    // ---------------------------------------------------------------- inventory screens

    private static Slot findSlot(GuiContainer gui) {
        for (Object o : gui.inventorySlots.inventorySlots) {
            if (o instanceof Slot && ((Slot) o).inventory instanceof InventoryOffhand) return (Slot) o;
        }
        return null;
    }

    @SubscribeEvent
    public void onDrawScreenPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        // The creative inventory tab would put the slot over the hotbar, and creative clicks on extra slots are
        // not sent to the server; keep it hidden there (F still swaps hands).
        if (event.gui instanceof GuiContainerCreative) {
            Slot slot = findSlot((GuiContainer) event.gui);
            if (slot != null) {
                slot.xDisplayPosition = -2000;
                slot.yDisplayPosition = -2000;
            }
        }
    }

    /** Draws the slot frame (the 1.7.10 texture has none) and redraws the item on top of it. */
    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        GuiScreen screen = event.gui;
        if (!(screen instanceof GuiContainer) || screen instanceof GuiContainerCreative
            || guiLeft == null
            || guiTop == null) return;
        GuiContainer gui = (GuiContainer) screen;
        Slot slot = findSlot(gui);
        if (slot == null || slot.xDisplayPosition < -1000) return;
        int left;
        int top;
        try {
            left = guiLeft.getInt(gui);
            top = guiTop.getInt(gui);
        } catch (IllegalAccessException e) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int x = left + slot.xDisplayPosition;
        int y = top + slot.yDisplayPosition;

        GL11.glPushMatrix();
        GL11.glTranslatef(0.0F, 0.0F, 100.0F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        Gui.drawRect(x - 1, y - 1, x + 17, y, 0xFF373737);
        Gui.drawRect(x - 1, y, x, y + 17, 0xFF373737);
        Gui.drawRect(x, y + 16, x + 17, y + 17, 0xFFFFFFFF);
        Gui.drawRect(x + 16, y, x + 17, y + 16, 0xFFFFFFFF);
        Gui.drawRect(x, y, x + 16, y + 16, 0xFF8B8B8B);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        ItemStack stack = slot.getStack();
        if (stack == null) {
            if (hasSlotSprite()) {
                GL11.glEnable(GL11.GL_BLEND);
                OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
                mc.getTextureManager()
                    .bindTexture(SLOT_SHIELD);
                Tessellator tess = Tessellator.instance;
                tess.startDrawingQuads();
                tess.addVertexWithUV(x, y + 16, 0, 0, 1);
                tess.addVertexWithUV(x + 16, y + 16, 0, 1, 1);
                tess.addVertexWithUV(x + 16, y, 0, 1, 0);
                tess.addVertexWithUV(x, y, 0, 0, 0);
                tess.draw();
                GL11.glDisable(GL11.GL_BLEND);
            }
        } else {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableGUIStandardItemLighting();
            ITEM_RENDER.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
            ITEM_RENDER.renderItemOverlayIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        boolean hovered = event.mouseX >= x - 1 && event.mouseX < x + 17 && event.mouseY >= y - 1 && event.mouseY < y + 17;
        if (hovered) {
            GL11.glColorMask(true, true, true, false);
            Gui.drawRect(x, y, x + 16, y + 16, 0x80FFFFFF);
            GL11.glColorMask(true, true, true, true);
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        // The tooltip was drawn before the frame; draw it again on top.
        if (hovered && stack != null && mc.thePlayer.inventory.getItemStack() == null && renderToolTip != null) {
            try {
                renderToolTip.invoke(gui, stack, event.mouseX, event.mouseY);
            } catch (Exception ignored) {}
        }
    }

    private boolean hasSlotSprite() {
        if (hasSlotSprite == null) {
            try {
                Minecraft.getMinecraft()
                    .getResourceManager()
                    .getResource(SLOT_SHIELD);
                hasSlotSprite = true;
            } catch (Exception e) {
                hasSlotSprite = false;
            }
        }
        return hasSlotSprite;
    }
}
