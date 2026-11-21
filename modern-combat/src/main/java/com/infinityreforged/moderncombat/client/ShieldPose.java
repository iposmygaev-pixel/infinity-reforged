package com.infinityreforged.moderncombat.client;

import org.lwjgl.opengl.GL11;

/**
 * Transforms for the shield model in each view, applied on top of the matrices 1.7.10 sets up for held items.
 */
final class ShieldPose {

    private ShieldPose() {}

    static void firstPerson(boolean blocking) {
        if (blocking) {
            GL11.glTranslatef(0.2F, 0.9F, 0.4F);
            GL11.glRotatef(-110.0F, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(10.0F, 1.0F, 0.0F, 0.0F);
        } else {
            GL11.glTranslatef(0.6F, 0.7F, 0.3F);
            GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(5.0F, 0.0F, 0.0F, 1.0F);
        }
        GL11.glScalef(1.25F, -1.25F, -1.25F);
    }

    static void thirdPerson(boolean blocking) {
        if (blocking) {
            GL11.glTranslatef(0.3F, 0.6F, 0.2F);
            GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);
        } else {
            GL11.glTranslatef(0.5F, 0.4F, 0.5F);
            GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);
        }
        GL11.glScalef(1.0F, -1.0F, -1.0F);
    }

    static void ground() {
        GL11.glScalef(0.5F, -0.5F, -0.5F);
    }
}
