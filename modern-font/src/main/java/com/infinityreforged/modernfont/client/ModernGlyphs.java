package com.infinityreforged.modernfont.client;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.infinityreforged.modernfont.ModernFont;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

/**
 * Loads modern bitmap font providers (font/default.json format) from the "modernfont" namespace, which the
 * installer fills from the player's own modern Minecraft.
 */
final class ModernGlyphs {

    /** Ascent of the 1.7.10 / modern default font: the baseline is 7 pixels below the top of the line. */
    private static final int DEFAULT_ASCENT = 7;

    static final class Glyph {

        ResourceLocation texture;
        float u0, v0, u1, v1;
        float drawWidth, drawHeight, yOffset;
        int advance;
    }

    private ModernGlyphs() {}

    /** Returns a table indexed by char, or null when no modern font data is installed. */
    static Glyph[] load(IResourceManager manager) {
        JsonObject root;
        try {
            IResource res = manager.getResource(new ResourceLocation(ModernFont.MODID, "font/default.json"));
            Reader reader = new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8);
            try {
                root = new JsonParser().parse(reader)
                    .getAsJsonObject();
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            ModernFont.LOG.info("No modern font data found (run the installer); using the vanilla font");
            return null;
        }

        Glyph[] table = new Glyph[65536];
        int count = 0;
        for (JsonElement el : root.getAsJsonArray("providers")) {
            JsonObject p = el.getAsJsonObject();
            if (!"bitmap".equals(p.get("type").getAsString())) continue;
            String file = p.get("file").getAsString();
            String name = file.substring(file.lastIndexOf('/') + 1);
            if (name.equals("ascii.png")) continue; // 1.7.10 keeps its own Latin font
            try {
                count += loadBitmap(manager, p, name, table);
            } catch (Exception e) {
                ModernFont.LOG.warn("Skipping font provider {}: {}", file, e.toString());
            }
        }
        return count > 0 ? table : null;
    }

    private static int loadBitmap(IResourceManager manager, JsonObject p, String name, Glyph[] table)
        throws Exception {
        ResourceLocation texture = new ResourceLocation(ModernFont.MODID, "textures/font/" + name);
        BufferedImage image;
        InputStream in = manager.getResource(texture)
            .getInputStream();
        try {
            image = ImageIO.read(in);
        } finally {
            in.close();
        }

        JsonArray rows = p.getAsJsonArray("chars");
        int rowCount = rows.size();
        int colCount = rows.get(0)
            .getAsString()
            .codePointCount(
                0,
                rows.get(0)
                    .getAsString()
                    .length());
        int cellW = image.getWidth() / colCount;
        int cellH = image.getHeight() / rowCount;
        int height = p.has("height") ? p.get("height")
            .getAsInt() : 8;
        int ascent = p.get("ascent")
            .getAsInt();
        float scale = (float) height / cellH;
        float texW = image.getWidth();
        float texH = image.getHeight();

        int count = 0;
        for (int row = 0; row < rowCount; row++) {
            String chars = rows.get(row)
                .getAsString();
            int col = 0;
            for (int i = 0; i < chars.length(); col++) {
                int cp = chars.codePointAt(i);
                i += Character.charCount(cp);
                if (cp == 0 || cp > 0xFFFF || table[cp] != null) continue;

                int x0 = col * cellW;
                int y0 = row * cellH;
                int width = glyphWidth(image, x0, y0, cellW, cellH);

                Glyph g = new Glyph();
                g.texture = texture;
                g.u0 = x0 / texW;
                g.v0 = y0 / texH;
                g.u1 = (x0 + width) / texW;
                g.v1 = (y0 + cellH) / texH;
                g.drawWidth = width * scale;
                g.drawHeight = height;
                g.yOffset = DEFAULT_ASCENT - ascent;
                g.advance = (int) (0.5F + width * scale) + 1;
                table[cp] = g;
                count++;
            }
        }
        return count;
    }

    /** Width of the glyph: rightmost non-transparent column + 1 (modern BitmapProvider rule). */
    private static int glyphWidth(BufferedImage image, int x0, int y0, int cellW, int cellH) {
        for (int x = cellW - 1; x >= 0; x--) {
            for (int y = 0; y < cellH; y++) {
                if ((image.getRGB(x0 + x, y0 + y) >>> 24) != 0) return x + 1;
            }
        }
        return 0;
    }
}
