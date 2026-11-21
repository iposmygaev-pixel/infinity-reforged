package com.infinityreforged.modernchests;

import net.minecraft.block.Block;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

/**
 * Development-only command for testing chest placement through the normal player placement path.
 * /chesttest place x y z yaw [sneak] - places a chest as the player would (looking down, with that yaw)
 * /chesttest report x y z - logs the chest's pairing and inventory size
 */
public class ChestTestCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "chesttest";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/chesttest place <x> <y> <z> <yaw> [sneak] | report <x> <y> <z>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        World world = player.worldObj;
        int x = parseInt(sender, args[1]);
        int y = parseInt(sender, args[2]);
        int z = parseInt(sender, args[3]);
        if ("break".equals(args[0])) {
            world.setBlockToAir(x, y, z);
            log(sender, "break " + x + " " + y + " " + z);
            return;
        }
        if ("client".equals(args[0])) {
            log(sender, "client " + x + " " + y + " " + z + ": " + ClientChestCheck.describe(x, y, z));
            return;
        }
        if ("place".equals(args[0])) {
            float yaw = (float) parseDouble(sender, args[4]);
            boolean sneak = args.length > 5 && "sneak".equals(args[5]);
            float oldYaw = player.rotationYaw;
            player.rotationYaw = yaw;
            player.setSneaking(sneak);
            boolean placed = new ItemStack(Blocks.chest)
                .tryPlaceItemIntoWorld(player, world, x, y - 1, z, 1, 0.5F, 1.0F, 0.5F);
            player.setSneaking(false);
            player.rotationYaw = oldYaw;
            log(sender, "place " + x + " " + y + " " + z + " yaw=" + yaw + (sneak ? " sneak" : "") + " -> " + placed);
        } else {
            TileEntity te = world.getTileEntity(x, y, z);
            if (!(te instanceof TileEntityChest)) {
                log(sender, "report " + x + " " + y + " " + z + ": no chest");
                return;
            }
            TileEntityChest chest = (TileEntityChest) te;
            Block block = world.getBlock(x, y, z);
            TileEntityChest other = ChestHooks.partner(world, chest, block);
            IInventory inv = ChestHooks.getInventory(block, world, x, y, z);
            log(
                sender,
                "report " + x + " " + y + " " + z
                    + ": meta="
                    + world.getBlockMetadata(x, y, z)
                    + " pair="
                    + ChestHooks.getPair(chest)
                    + " partner="
                    + (other == null ? "none" : other.xCoord + "," + other.yCoord + "," + other.zCoord)
                    + " slots="
                    + (inv == null ? "blocked" : inv.getSizeInventory()));
        }
    }

    private static void log(ICommandSender sender, String message) {
        ModernChests.LOG.info("[chesttest] {}", message);
        sender.addChatMessage(new ChatComponentText(message));
    }
}
