package com.infinityreforged.moderncombat.client;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;

/**
 * Transforms for the shield model in each view, on top of the matrices 1.7.10 sets up for held items.
 * Each pose: translate x y z, rotate around Y, X, Z (degrees), uniform scale.
 * <p>
 * For tuning, poses can be overridden from {@code shieldpose.txt} in the game folder (lines like
 * {@code fpBlock=0 0 0 0 0 0 1}); the file is re-read when it changes. Normal installs don't have it.
 */
final class ShieldPose {

    private static final Map<String, float[]> POSES = new HashMap<String, float[]>();

    static {
        POSES.put("fpIdle", new float[] { 0.6F, 0.7F, 0.3F, -90F, 0F, 5F, 1.25F });
        POSES.put("fpBlock", new float[] { -0.2F, -0.38F, -0.85F, 15F, 0F, -5F, 0.55F });
        POSES.put("tpIdle", new float[] { 0.3F, 0.3F, -0.1F, 90F, -50F, 0F, 2.0F });
        POSES.put("tpBlock", new float[] { 0.3F, 0.45F, -0.1F, 0F, 0F, 10F, 2.0F });
        POSES.put("ground", new float[] { 0F, 0F, 0F, 0F, 0F, 0F, 0.5F });
        POSES.put("inventory", new float[] { 8F, 8F, 0F, -25F, 15F, -5F, 11F });
    }

    private static long lastCheck;
    private static long lastModified;

    private ShieldPose() {}

    static void apply(String name) {
        reloadOverrides();
        float[] p = POSES.get(name);
        GL11.glTranslatef(p[0], p[1], p[2]);
        GL11.glRotatef(p[3], 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(p[4], 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(p[5], 0.0F, 0.0F, 1.0F);
        // Model space has Y up and the plate facing -Z; flip into item space (Y down).
        GL11.glScalef(p[6], -p[6], -p[6]);
    }

    private static void reloadOverrides() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 500) return;
        lastCheck = now;
        File file = new File(Minecraft.getMinecraft().mcDataDir, "shieldpose.txt");
        if (!file.isFile() || file.lastModified() == lastModified) return;
        lastModified = file.lastModified();
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                int eq = line.indexOf('=');
                if (eq <= 0 || !POSES.containsKey(line.substring(0, eq).trim())) continue;
                String[] parts = line.substring(eq + 1).trim().split("\\s+");
                if (parts.length != 7) continue;
                float[] values = new float[7];
                for (int i = 0; i < 7; i++) values[i] = Float.parseFloat(parts[i]);
                POSES.put(line.substring(0, eq).trim(), values);
            }
        } catch (Exception ignored) {}
    }
}
