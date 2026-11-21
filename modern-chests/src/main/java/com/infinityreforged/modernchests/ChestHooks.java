package com.infinityreforged.modernchests;

import java.lang.reflect.Field;
import java.util.List;

import com.infinityreforged.modernchests.core.ChestTransformer;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Chest placement and pairing rules of Minecraft 1.13+, called from the patched BlockChest / TileEntityChest.
 * <p>
 * Each chest remembers its partner ({@link #UNKNOWN} for chests placed before the mod: they keep vanilla pairing).
 * A new chest joins a single chest directly to its left or right that faces the same way, unless placed while
 * sneaking; any number of chests can stand next to each other.
 */
public final class ChestHooks {

    public static final byte UNKNOWN = 0;
    public static final byte SINGLE = 1;
    // 2..5: partner on that ForgeDirection side (NORTH, SOUTH, WEST, EAST).

    private static final String NBT_KEY = "InfinityReforgedPair";
    private static final ForgeDirection[] HORIZONTAL = { ForgeDirection.NORTH, ForgeDirection.SOUTH,
        ForgeDirection.WEST, ForgeDirection.EAST };

    private static Field pairField;

    private ChestHooks() {}

    // ------------------------------------------------------------------ state

    private static Field field() {
        if (pairField == null) {
            try {
                pairField = TileEntityChest.class.getField(ChestTransformer.PAIR_FIELD);
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("Modern Chests coremod did not patch TileEntityChest", e);
            }
        }
        return pairField;
    }

    public static byte getPair(TileEntityChest chest) {
        try {
            return field().getByte(chest);
        } catch (IllegalAccessException e) {
            return UNKNOWN;
        }
    }

    private static void setPair(TileEntityChest chest, byte pair) {
        try {
            field().setByte(chest, pair);
        } catch (IllegalAccessException ignored) {}
        chest.updateContainingBlockInfo();
        chest.markDirty();
        World world = chest.getWorldObj();
        if (world != null) world.markBlockForUpdate(chest.xCoord, chest.yCoord, chest.zCoord);
    }

    private static TileEntityChest chestAt(IBlockAccess world, int x, int y, int z, Block block) {
        if (world.getBlock(x, y, z) != block) return null;
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileEntityChest ? (TileEntityChest) te : null;
    }

    private static TileEntityChest neighbour(IBlockAccess world, TileEntityChest chest, ForgeDirection side,
        Block block) {
        return chestAt(
            world,
            chest.xCoord + side.offsetX,
            chest.yCoord + side.offsetY,
            chest.zCoord + side.offsetZ,
            block);
    }

    /** The chest this one forms a double chest with, or null. */
    public static TileEntityChest partner(IBlockAccess world, TileEntityChest chest, Block block) {
        byte pair = getPair(chest);
        if (pair == SINGLE) return null;
        for (ForgeDirection side : HORIZONTAL) {
            if (pair != UNKNOWN && pair != side.ordinal()) continue;
            TileEntityChest other = neighbour(world, chest, side, block);
            if (other == null || other.isInvalid()) continue;
            byte otherPair = getPair(other);
            if (otherPair == side.getOpposite()
                .ordinal() || otherPair == UNKNOWN) {
                return other;
            }
        }
        return null;
    }

    /** True if the chest is free to join a new neighbour: single, or its recorded partner is gone. */
    private static boolean available(World world, TileEntityChest chest, Block block) {
        return partner(world, chest, block) == null;
    }

    // ------------------------------------------------------------------ BlockChest

    /** 1.13+: a chest can go anywhere a block can (no limit on neighbouring chests). */
    public static boolean canPlaceBlockAt(Block block, World world, int x, int y, int z) {
        return world.getBlock(x, y, z)
            .isReplaceable(world, x, y, z);
    }

    /** Only sets this chest's own facing when it has none (vanilla also re-oriented neighbouring chests). */
    public static void onBlockAdded(Block block, World world, int x, int y, int z) {
        if (world.isRemote || world.getBlockMetadata(x, y, z) >= 2) return;
        Block north = world.getBlock(x, y, z - 1);
        Block south = world.getBlock(x, y, z + 1);
        Block west = world.getBlock(x - 1, y, z);
        Block east = world.getBlock(x + 1, y, z);
        int facing = 3;
        if (north.func_149730_j() && !south.func_149730_j()) facing = 3;
        if (south.func_149730_j() && !north.func_149730_j()) facing = 2;
        if (west.func_149730_j() && !east.func_149730_j()) facing = 5;
        if (east.func_149730_j() && !west.func_149730_j()) facing = 4;
        world.setBlockMetadataWithNotify(x, y, z, facing, 3);
    }

    public static void onBlockPlacedBy(Block block, World world, int x, int y, int z, EntityLivingBase placer,
        ItemStack stack) {
        int rotation = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        int facing = rotation == 0 ? 2 : rotation == 1 ? 5 : rotation == 2 ? 3 : 4;
        world.setBlockMetadataWithNotify(x, y, z, facing, 3);

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityChest)) return;
        TileEntityChest chest = (TileEntityChest) te;
        if (stack.hasDisplayName()) chest.func_145976_a(stack.getDisplayName());

        // Mark the new chest single first, so older neighbours don't count it as their partner while checking.
        try {
            field().setByte(chest, SINGLE);
        } catch (IllegalAccessException ignored) {}

        byte pair = SINGLE;
        if (!placer.isSneaking()) {
            // Candidates: directly left / right of the chest's front.
            ForgeDirection[] sides = facing == 2 || facing == 3
                ? new ForgeDirection[] { ForgeDirection.WEST, ForgeDirection.EAST }
                : new ForgeDirection[] { ForgeDirection.NORTH, ForgeDirection.SOUTH };
            for (ForgeDirection side : sides) {
                TileEntityChest other = neighbour(world, chest, side, block);
                if (other == null) continue;
                if (world.getBlockMetadata(other.xCoord, other.yCoord, other.zCoord) != facing) continue;
                if (!available(world, other, block)) continue;
                pair = (byte) side.ordinal();
                setPair(
                    other,
                    (byte) side.getOpposite()
                        .ordinal());
                break;
            }
        }
        setPair(chest, pair);
    }

    /** BlockChest.func_149951_m: the inventory opened / used by hoppers, honouring the recorded pairing. */
    public static IInventory getInventory(Block block, World world, int x, int y, int z) {
        TileEntityChest chest = chestAt(world, x, y, z, block);
        if (chest == null || blocked(world, x, y, z)) return null;
        TileEntityChest other = partner(world, chest, block);
        if (other == null) return chest;
        if (blocked(world, other.xCoord, other.yCoord, other.zCoord)) return null;
        // Vanilla order: the chest at lower x / lower z is the top half of the double inventory.
        boolean otherFirst = other.xCoord < x || other.zCoord < z;
        return otherFirst ? new InventoryLargeChest("container.chestDouble", other, chest)
            : new InventoryLargeChest("container.chestDouble", chest, other);
    }

    /** Lid blocked by a solid block or a sitting ocelot. */
    private static boolean blocked(World world, int x, int y, int z) {
        if (world.isSideSolid(x, y + 1, z, ForgeDirection.DOWN)) return true;
        @SuppressWarnings("unchecked")
        List<EntityOcelot> cats = world.getEntitiesWithinAABB(
            EntityOcelot.class,
            AxisAlignedBB.getBoundingBox(x, y + 1, z, x + 1, y + 2, z + 1));
        for (EntityOcelot cat : cats) {
            if (cat.isSitting()) return true;
        }
        return false;
    }

    public static void setBlockBoundsBasedOnState(Block block, IBlockAccess world, int x, int y, int z) {
        TileEntityChest chest = chestAt(world, x, y, z, block);
        TileEntityChest other = chest == null ? null : partner(world, chest, block);
        if (other == null) {
            block.setBlockBounds(0.0625F, 0.0F, 0.0625F, 0.9375F, 0.875F, 0.9375F);
        } else if (other.zCoord < z) {
            block.setBlockBounds(0.0625F, 0.0F, 0.0F, 0.9375F, 0.875F, 0.9375F);
        } else if (other.zCoord > z) {
            block.setBlockBounds(0.0625F, 0.0F, 0.0625F, 0.9375F, 0.875F, 1.0F);
        } else if (other.xCoord < x) {
            block.setBlockBounds(0.0F, 0.0F, 0.0625F, 0.9375F, 0.875F, 0.9375F);
        } else {
            block.setBlockBounds(0.0625F, 0.0F, 0.0625F, 1.0F, 0.875F, 0.9375F);
        }
    }

    // ------------------------------------------------------------------ TileEntityChest

    /** TileEntityChest.checkForAdjacentChests: fills the adjacentChest* fields from the recorded pairing. */
    public static void checkForAdjacentChests(TileEntityChest chest) {
        if (chest.isInvalid()) {
            // Called from invalidate() when the chest is broken: like vanilla, make the former partner re-check,
            // otherwise the remaining half keeps rendering as a double chest.
            TileEntityChest[] old = { chest.adjacentChestZNeg, chest.adjacentChestXPos, chest.adjacentChestXNeg,
                chest.adjacentChestZPos };
            for (TileEntityChest other : old) {
                if (other != null) other.adjacentChestChecked = false;
            }
        }
        if (chest.adjacentChestChecked) return;
        chest.adjacentChestChecked = true;
        chest.adjacentChestZNeg = null;
        chest.adjacentChestXPos = null;
        chest.adjacentChestXNeg = null;
        chest.adjacentChestZPos = null;
        World world = chest.getWorldObj();
        if (world == null) return;
        Block block = world.getBlock(chest.xCoord, chest.yCoord, chest.zCoord);
        TileEntityChest other = partner(world, chest, block);
        if (other == null) return;
        if (other.zCoord < chest.zCoord) chest.adjacentChestZNeg = other;
        else if (other.zCoord > chest.zCoord) chest.adjacentChestZPos = other;
        else if (other.xCoord < chest.xCoord) chest.adjacentChestXNeg = other;
        else chest.adjacentChestXPos = other;
    }

    public static void readNBT(TileEntityChest chest, NBTTagCompound tag) {
        try {
            field().setByte(chest, tag.getByte(NBT_KEY));
        } catch (IllegalAccessException ignored) {}
    }

    public static void writeNBT(TileEntityChest chest, NBTTagCompound tag) {
        byte pair = getPair(chest);
        if (pair != UNKNOWN) tag.setByte(NBT_KEY, pair);
    }

    public static Packet getDescriptionPacket(TileEntityChest chest) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte(NBT_KEY, getPair(chest));
        return new S35PacketUpdateTileEntity(chest.xCoord, chest.yCoord, chest.zCoord, 0, tag);
    }

    public static void onDataPacket(TileEntityChest chest, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();
        if (tag == null) return;
        try {
            field().setByte(chest, tag.getByte(NBT_KEY));
        } catch (IllegalAccessException ignored) {}
        chest.updateContainingBlockInfo();
        // Re-pair the neighbours too, so a double chest renders as one.
        World world = chest.getWorldObj();
        if (world == null) return;
        for (ForgeDirection side : HORIZONTAL) {
            TileEntity te = world.getTileEntity(
                chest.xCoord + side.offsetX,
                chest.yCoord,
                chest.zCoord + side.offsetZ);
            if (te instanceof TileEntityChest) te.updateContainingBlockInfo();
        }
    }
}
