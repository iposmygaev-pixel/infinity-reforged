package com.infinityreforged.modernfont.client;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.infinityreforged.modernfont.ModernFont;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.GuiScreenEvent;

/**
 * Button labels that don't fit scroll back and forth inside the button, as in Minecraft 1.19.4+, instead of
 * spilling over its edges.
 * <p>
 * Before a screen draws, overflowing labels are blanked; after it draws, they are rendered clipped to the button
 * and the original text is restored, so the screen's own state never changes.
 */
public class ButtonTextScroller {

    private static final int MARGIN = 2;

    private final Field buttonListField;
    private final Map<GuiButton, String> hidden = new IdentityHashMap<GuiButton, String>();

    public ButtonTextScroller() {
        Field field = null;
        try {
            field = ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n");
        } catch (RuntimeException e) {
            ModernFont.LOG.warn("Could not access GuiScreen.buttonList; scrolling button text is disabled");
        }
        buttonListField = field;
    }

    @SuppressWarnings("unchecked")
    private List<GuiButton> buttons(GuiScreen gui) {
        if (buttonListField == null) return null;
        try {
            return (List<GuiButton>) buttonListField.get(gui);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrawPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        restore();
        if (event.isCanceled()) return;
        List<GuiButton> list = buttons(event.gui);
        if (list == null) return;
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        for (GuiButton button : list) {
            String text = button.displayString;
            if (!button.visible || text == null || text.isEmpty()) continue;
            if (font.getStringWidth(text) > button.width - 2 * MARGIN) {
                hidden.put(button, text);
                button.displayString = "";
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDrawPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (hidden.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int scale = res.getScaleFactor();

        for (Map.Entry<GuiButton, String> entry : hidden.entrySet()) {
            GuiButton button = entry.getKey();
            String text = entry.getValue();
            button.displayString = text;

            int color = 14737632;
            if (button.packedFGColour != 0) color = button.packedFGColour;
            else if (!button.enabled) color = 10526880;
            else if (button.func_146115_a()) color = 16777120;

            int minX = button.xPosition + MARGIN;
            int maxX = button.xPosition + button.width - MARGIN;
            int textY = button.yPosition + (button.height - 8) / 2;
            int overflow = font.getStringWidth(text) - (maxX - minX);

            // Same motion as modern AbstractWidget.renderScrollingString.
            double seconds = Minecraft.getSystemTime() / 1000.0;
            double period = Math.max(overflow * 0.5, 3.0);
            double progress = Math.sin(Math.PI / 2 * Math.cos(2 * Math.PI * seconds / period)) / 2.0 + 0.5;
            int offset = (int) Math.round(progress * overflow);

            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(
                minX * scale,
                mc.displayHeight - (button.yPosition + button.height) * scale,
                (maxX - minX) * scale,
                button.height * scale);
            font.drawStringWithShadow(text, minX - offset, textY, color);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        hidden.clear();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Safety net: never leave a label blanked if a draw was interrupted. */
    private void restore() {
        for (Map.Entry<GuiButton, String> entry : hidden.entrySet()) {
            entry.getKey().displayString = entry.getValue();
        }
        hidden.clear();
    }
}
