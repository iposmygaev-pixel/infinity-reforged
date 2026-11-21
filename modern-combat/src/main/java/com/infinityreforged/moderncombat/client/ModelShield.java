package com.infinityreforged.moderncombat.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;

/**
 * Shield model with the same boxes and texture layout as modern Minecraft (64x64 texture):
 * a 12x22x1 plate and a 2x6x6 handle behind it.
 */
public class ModelShield extends ModelBase {

    private final ModelRenderer plate;
    private final ModelRenderer handle;

    public ModelShield() {
        textureWidth = 64;
        textureHeight = 64;
        plate = new ModelRenderer(this, 0, 0);
        plate.addBox(-6.0F, -11.0F, -2.0F, 12, 22, 1, 0.0F);
        handle = new ModelRenderer(this, 26, 0);
        handle.addBox(-1.0F, -3.0F, -1.0F, 2, 6, 6, 0.0F);
    }

    public void render() {
        float scale = 0.0625F;
        plate.render(scale);
        handle.render(scale);
    }
}
