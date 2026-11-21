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
            || type == ItemRenderType.ENTITY;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return type == ItemRenderType.ENTITY && helper == ItemRendererHelper.ENTITY_BOBBING;
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
        switch (type) {
            case EQUIPPED_FIRST_PERSON:
                ShieldPose.firstPerson(blocking(data.length > 1 ? data[1] : null, item));
                break;
            case EQUIPPED:
                ShieldPose.thirdPerson(blocking(data.length > 1 ? data[1] : null, item));
                break;
            default:
                ShieldPose.ground();
                break;
        }
        model.render();
        GL11.glPopMatrix();
    }
}
