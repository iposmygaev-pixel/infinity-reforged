package com.infinityreforged.modernfont.client;

import com.infinityreforged.modernfont.CommonProxy;
import com.infinityreforged.modernfont.ModernFont;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;

public class ClientProxy extends CommonProxy {

    private boolean disableUnicodeMode = true;
    private ModernFontRenderer renderer;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        Configuration cfg = new Configuration(event.getSuggestedConfigurationFile());
        cfg.load();
        disableUnicodeMode = cfg.getBoolean(
            "disableUnicodeMode",
            Configuration.CATEGORY_GENERAL,
            true,
            "Languages like Russian switch 1.7.10 to the small unicode font for ALL text. When modern glyphs are\n"
                + "installed, keep the normal font instead (as modern versions do). 'Force Unicode Font' still works.");
        if (cfg.hasChanged()) cfg.save();
    }

    @Override
    public void init() {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer old = mc.fontRenderer;
        renderer = new ModernFontRenderer(
            mc.gameSettings,
            new ResourceLocation("textures/font/ascii.png"),
            mc.renderEngine,
            old.getUnicodeFlag());
        renderer.setBidiFlag(old.getBidiFlag());
        // Registering loads the glyphs right away and again on every resource reload.
        ((IReloadableResourceManager) mc.getResourceManager()).registerReloadListener(renderer);
        mc.fontRenderer = renderer;
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        ModernFont.LOG.info("Modern font renderer installed ({} glyphs)", renderer.glyphCount());
    }

    /** The language switches the unicode flag back on load/change; undo it while modern glyphs are present. */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !disableUnicodeMode) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.fontRenderer == renderer && renderer.hasGlyphs()
            && renderer.getUnicodeFlag()
            && !mc.gameSettings.forceUnicodeFont) {
            renderer.setUnicodeFlag(false);
        }
    }
}
