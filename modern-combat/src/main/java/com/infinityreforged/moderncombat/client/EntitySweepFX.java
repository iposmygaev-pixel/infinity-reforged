package com.infinityreforged.moderncombat.client;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/**
 * The 1.9+ sweep attack particle: an 8-frame, 2-block-wide crescent that lives for 4 ticks.
 */
public class EntitySweepFX extends EntityFX {

    private static final ResourceLocation[] FRAMES = new ResourceLocation[8];

    static {
        for (int i = 0; i < FRAMES.length; i++) {
            FRAMES[i] = new ResourceLocation("moderncombat", "textures/particle/sweep_" + i + ".png");
        }
    }

    private final float size;

    public EntitySweepFX(World world, double x, double y, double z) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);
        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;
        this.particleMaxAge = 4;
        float shade = this.rand.nextFloat() * 0.6F + 0.4F;
        this.particleRed = shade;
        this.particleGreen = shade;
        this.particleBlue = shade;
        this.size = 1.0F;
        this.noClip = true;
    }

    @Override
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        if (++this.particleAge >= this.particleMaxAge) {
            this.setDead();
        }
    }

    /** Layer 3 is rendered without a bound sheet, so the particle binds and draws its own texture. */
    @Override
    public int getFXLayer() {
        return 3;
    }

    @Override
    public void renderParticle(Tessellator tess, float partialTicks, float rotX, float rotXZ, float rotZ, float rotYZ,
        float rotXY) {
        int frame = (int) ((this.particleAge + partialTicks) * 7.0F / this.particleMaxAge);
        if (frame < 0) frame = 0;
        if (frame > 7) frame = 7;

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(FRAMES[frame]);

        float x = (float) (this.prevPosX + (this.posX - this.prevPosX) * partialTicks - interpPosX);
        float y = (float) (this.prevPosY + (this.posY - this.prevPosY) * partialTicks - interpPosY);
        float z = (float) (this.prevPosZ + (this.posZ - this.prevPosZ) * partialTicks - interpPosZ);
        float s = this.size;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.003921569F);
        GL11.glDepthMask(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);

        tess.startDrawingQuads();
        tess.setColorRGBA_F(this.particleRed, this.particleGreen, this.particleBlue, 1.0F);
        tess.addVertexWithUV(x - rotX * s - rotYZ * s, y - rotXZ * s, z - rotZ * s - rotXY * s, 1.0D, 1.0D);
        tess.addVertexWithUV(x - rotX * s + rotYZ * s, y + rotXZ * s, z - rotZ * s + rotXY * s, 1.0D, 0.0D);
        tess.addVertexWithUV(x + rotX * s + rotYZ * s, y + rotXZ * s, z + rotZ * s + rotXY * s, 0.0D, 0.0D);
        tess.addVertexWithUV(x + rotX * s - rotYZ * s, y - rotXZ * s, z + rotZ * s - rotXY * s, 0.0D, 1.0D);
        tess.draw();

        GL11.glDepthMask(true);
        GL11.glPopAttrib();
    }
}
