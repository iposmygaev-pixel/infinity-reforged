package com.infinityreforged.moderncombat.client;

import org.lwjgl.opengl.GL11;

import com.infinityreforged.moderncombat.ModernCombat;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

/**
 * Renders the shield as a 3D model in hand and on the ground. The inventory uses the flat icon.
 */
public class ShieldRenderer implements IItemRenderer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        ModernCombat.MODID,
        "textures/entity/shield.png");
    private final ModelShield model = new ModelShield();

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON
            || type == ItemRenderType.ENTITY
            || type == ItemRenderType.INVENTORY;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        if (type == ItemRenderType.ENTITY) return helper == ItemRendererHelper.ENTITY_BOBBING;
        // Plain frame in hand (only a -0.5 offset) instead of Forge's flat-item transform.
        return helper == ItemRendererHelper.EQUIPPED_BLOCK;
    }

    private static boolean blocking(Object entity, ItemStack stack) {
        if (!(entity instanceof EntityPlayer)) return false;
        EntityPlayer player = (EntityPlayer) entity;
        return player.isUsingItem() && player.getItemInUse() == stack;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TEXTURE);
        GL11.glPushMatrix();
        Object holder = data.length > 1 ? data[1] : null;
        switch (type) {
            case EQUIPPED_FIRST_PERSON:
                if (blocking(holder, item)) {
                    // Raised shield: placed directly in camera space (x right, y up, -z forward).
                    GL11.glLoadIdentity();
                    ShieldPose.apply("fpBlock");
                } else {
                    ShieldPose.apply("fpIdle");
                }
                break;
            case EQUIPPED:
                ShieldPose.apply(blocking(holder, item) ? "tpBlock" : "tpIdle");
                break;
            case INVENTORY:
                // Same 3D model as the modern inventory icon.
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                ShieldPose.apply("inventory");
                break;
            default:
                ShieldPose.apply("ground");
                break;
        }
        model.render();
        GL11.glPopMatrix();
    }
}
