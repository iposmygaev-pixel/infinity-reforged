package com.infinityreforged.modernfont.client;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;

/**
 * Draws characters that 1.7.10 sends to its unicode font with modern bitmap glyphs instead: same pixel style
 * and size as Latin text, correct widths (1.7.10 miscounts wide unicode glyphs).
 */
public class ModernFontRenderer extends FontRenderer {

    private ModernGlyphs.Glyph[] glyphs;

    public ModernFontRenderer(GameSettings settings, ResourceLocation font, TextureManager textures,
        boolean unicode) {
        super(settings, font, textures, unicode);
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        super.onResourceManagerReload(manager);
        glyphs = ModernGlyphs.load(manager);
    }

    public boolean hasGlyphs() {
        return glyphs != null;
    }

    public int glyphCount() {
        if (glyphs == null) return 0;
        int n = 0;
        for (ModernGlyphs.Glyph g : glyphs) if (g != null) n++;
        return n;
    }

    private ModernGlyphs.Glyph glyph(char c) {
        if (glyphs == null || getUnicodeFlag() || DefaultFontChars.contains(c)) return null;
        return glyphs[c];
    }

    @Override
    protected float renderUnicodeChar(char c, boolean italic) {
        ModernGlyphs.Glyph g = glyph(c);
        if (g == null) return super.renderUnicodeChar(c, italic);

        bindTexture(g.texture);
        float slant = italic ? 1.0F : 0.0F;
        float top = this.posY + g.yOffset;
        float bottom = top + g.drawHeight - 0.01F;
        float right = this.posX + g.drawWidth - 0.01F;

        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        GL11.glTexCoord2f(g.u0, g.v0);
        GL11.glVertex3f(this.posX + slant, top, 0.0F);
        GL11.glTexCoord2f(g.u0, g.v1);
        GL11.glVertex3f(this.posX - slant, bottom, 0.0F);
        GL11.glTexCoord2f(g.u1, g.v0);
        GL11.glVertex3f(right + slant, top, 0.0F);
        GL11.glTexCoord2f(g.u1, g.v1);
        GL11.glVertex3f(right - slant, bottom, 0.0F);
        GL11.glEnd();
        return g.advance;
    }

    @Override
    public int getCharWidth(char c) {
        ModernGlyphs.Glyph g = glyph(c);
        return g != null ? g.advance : super.getCharWidth(c);
    }
}
