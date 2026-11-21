package com.infinityreforged.modernchests.core;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.Name("ModernChestsCore")
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions("com.infinityreforged.modernchests.core")
// After FML's deobfuscation, so classes have MCP names and methods SRG names (MCP names in dev).
@IFMLLoadingPlugin.SortingIndex(1001)
public class ChestsCorePlugin implements IFMLLoadingPlugin {

    static boolean deobfuscatedEnvironment;

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { ChestTransformer.class.getName() };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        Object deobf = data.get("runtimeDeobfuscationEnabled");
        deobfuscatedEnvironment = !(deobf instanceof Boolean && (Boolean) deobf);
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
