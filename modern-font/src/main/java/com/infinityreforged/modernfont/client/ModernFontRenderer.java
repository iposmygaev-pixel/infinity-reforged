package com.infinityreforged.modernfont.client;

import java.util.Arrays;
import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;

/**
 * Draws characters that 1.7.10 sends to its unicode font with modern bitmap glyphs instead: same pixel style
 * and size as Latin text, correct widths (1.7.10 miscounts wide unicode glyphs).
 * <p>
 * All public measuring and wrapping methods are overridden as well: OptiFine replaces the vanilla width code
 * with its own and would otherwise measure modern glyphs with the old unicode widths, shifting centered text.
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

    private boolean hasModernGlyphs(String text) {
        if (text == null || glyphs == null || getUnicodeFlag()) return false;
        for (int i = 0; i < text.length(); i++) {
            if (glyph(text.charAt(i)) != null) return true;
        }
        return false;
    }

    /**
     * Measures everything except modern glyphs with the base implementation (vanilla or OptiFine, whatever it
     * does with normal characters) and adds the real width of each modern glyph.
     */
    @Override
    public int getStringWidth(String text) {
        if (!hasModernGlyphs(text)) return super.getStringWidth(text);
        StringBuilder rest = new StringBuilder(text.length());
        int extra = 0;
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i < text.length() - 1) {
                char code = text.charAt(++i);
                rest.append(c)
                    .append(code);
                if (code == 'l' || code == 'L') bold = true;
                else if (code == 'r' || code == 'R') bold = false;
                continue;
            }
            ModernGlyphs.Glyph g = glyph(c);
            if (g != null) {
                extra += g.advance + (bold ? 1 : 0);
            } else {
                rest.append(c);
            }
        }
        return super.getStringWidth(rest.toString()) + extra;
    }

    @Override
    public String trimStringToWidth(String text, int maxWidth, boolean reverse) {
        if (!hasModernGlyphs(text)) return super.trimStringToWidth(text, maxWidth, reverse);
        StringBuilder sb = new StringBuilder();
        int width = 0;
        int start = reverse ? text.length() - 1 : 0;
        int step = reverse ? -1 : 1;
        boolean format = false;
        boolean bold = false;
        for (int i = start; i >= 0 && i < text.length() && width < maxWidth; i += step) {
            char c = text.charAt(i);
            int w = getCharWidth(c);
            if (format) {
                format = false;
                if (c == 'l' || c == 'L') bold = true;
                else if (c == 'r' || c == 'R') bold = false;
            } else if (w < 0) {
                format = true;
            } else {
                width += w;
                if (bold) width++;
            }
            if (width > maxWidth) break;
            if (reverse) sb.insert(0, c);
            else sb.append(c);
        }
        return sb.toString();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List listFormattedStringToWidth(String text, int wrapWidth) {
        if (!hasModernGlyphs(text)) return super.listFormattedStringToWidth(text, wrapWidth);
        return Arrays.asList(
            wrap(text, wrapWidth).split("\n"));
    }

    private String wrap(String text, int wrapWidth) {
        int cut = sizeStringToWidth(text, wrapWidth);
        if (text.length() <= cut) return text;
        String head = text.substring(0, cut);
        char c = text.charAt(cut);
        boolean skip = c == ' ' || c == '\n';
        String tail = formatFromString(head) + text.substring(cut + (skip ? 1 : 0));
        return head + "\n" + wrap(tail, wrapWidth);
    }

    private int sizeStringToWidth(String text, int wrapWidth) {
        int length = text.length();
        int width = 0;
        int i = 0;
        int lastSpace = -1;
        for (boolean bold = false; i < length; ++i) {
            char c = text.charAt(i);
            switch (c) {
                case '\n':
                    --i;
                    break;
                case '\u00a7':
                    if (i < length - 1) {
                        ++i;
                        char code = text.charAt(i);
                        if (code == 'l' || code == 'L') bold = true;
                        else if (code == 'r' || code == 'R' || isFormatColor(code)) bold = false;
                    }
                    break;
                case ' ':
                    lastSpace = i;
                    // fall through
                default:
                    width += getCharWidth(c);
                    if (bold) ++width;
            }
            if (c == '\n') {
                ++i;
                lastSpace = i;
                break;
            }
            if (width > wrapWidth) break;
        }
        return i != length && lastSpace != -1 && lastSpace < i ? lastSpace : i;
    }

    private static boolean isFormatColor(char c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
    }

    private static boolean isFormatSpecial(char c) {
        return c >= 'k' && c <= 'o' || c >= 'K' && c <= 'O' || c == 'r' || c == 'R';
    }

    private static String formatFromString(String text) {
        String format = "";
        int i = -1;
        int length = text.length();
        while ((i = text.indexOf('\u00a7', i + 1)) != -1) {
            if (i < length - 1) {
                char c = text.charAt(i + 1);
                if (isFormatColor(c)) format = "\u00a7" + c;
                else if (isFormatSpecial(c)) format = format + "\u00a7" + c;
            }
        }
        return format;
    }
}
