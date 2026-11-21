package com.infinityreforged.modernchests;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;

/** Development-only: what the client-side chest uses for rendering (only loaded in single player dev runs). */
final class ClientChestCheck {

    private ClientChestCheck() {}

    static String describe(int x, int y, int z) {
        if (Minecraft.getMinecraft().theWorld == null) return "client: no world";
        TileEntity te = Minecraft.getMinecraft().theWorld.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityChest)) return "client: no chest";
        TileEntityChest chest = (TileEntityChest) te;
        chest.checkForAdjacentChests();
        boolean dbl = chest.adjacentChestXNeg != null || chest.adjacentChestXPos != null
            || chest.adjacentChestZNeg != null
            || chest.adjacentChestZPos != null;
        return "client renders " + (dbl ? "DOUBLE" : "single");
    }
}
