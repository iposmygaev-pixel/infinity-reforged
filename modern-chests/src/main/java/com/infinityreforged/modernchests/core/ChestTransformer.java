package com.infinityreforged.modernchests.core;

import java.util.ListIterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.minecraft.launchwrapper.IClassTransformer;

/**
 * Redirects the vanilla chest methods that enforce 1.7.10 chest placement and pairing to {@link ChestHooks}.
 * Method bodies are replaced with a call to a static hook taking {@code this} plus the original arguments.
 */
public class ChestTransformer implements IClassTransformer, Opcodes {

    private static final Logger LOG = LogManager.getLogger("ModernChestsCore");
    private static final String HOOKS = "com/infinityreforged/modernchests/ChestHooks";

    public static final String PAIR_FIELD = "infinityreforged$pair";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        if ("net.minecraft.block.BlockChest".equals(transformedName)) return patchBlock(bytes);
        if ("net.minecraft.tileentity.TileEntityChest".equals(transformedName)) return patchTile(bytes);
        return bytes;
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] write(ClassNode node) {
        // Frames are not recomputed (that would need class loading); hook bodies are straight-line code.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode find(ClassNode node, String mcp, String srg, String desc) {
        for (MethodNode m : node.methods) {
            if ((m.name.equals(mcp) || m.name.equals(srg)) && m.desc.equals(desc)) return m;
        }
        return null;
    }

    /** Replaces the body with: return ChestHooks.hook(this, args...). */
    private static boolean redirect(ClassNode node, String mcp, String srg, String desc, String hook, String owner) {
        MethodNode m = find(node, mcp, srg, desc);
        if (m == null) {
            LOG.error("Could not find {}.{}{}", node.name, mcp, desc);
            return false;
        }
        InsnList code = new InsnList();
        code.add(new VarInsnNode(ALOAD, 0));
        int slot = 1;
        for (Type arg : Type.getArgumentTypes(desc)) {
            code.add(new VarInsnNode(arg.getOpcode(ILOAD), slot));
            slot += arg.getSize();
        }
        String hookDesc = "(L" + owner + ";" + desc.substring(1);
        code.add(new MethodInsnNode(INVOKESTATIC, HOOKS, hook, hookDesc, false));
        code.add(new InsnNode(Type.getReturnType(desc).getOpcode(IRETURN)));
        m.instructions.clear();
        m.instructions.add(code);
        m.tryCatchBlocks.clear();
        if (m.localVariables != null) m.localVariables.clear();
        return true;
    }

    private byte[] patchBlock(byte[] bytes) {
        ClassNode node = read(bytes);
        String owner = "net/minecraft/block/Block";
        int ok = 0;
        ok += redirect(node, "canPlaceBlockAt", "func_149742_c", "(Lnet/minecraft/world/World;III)Z", "canPlaceBlockAt", owner) ? 1 : 0;
        ok += redirect(node, "onBlockAdded", "func_149726_b", "(Lnet/minecraft/world/World;III)V", "onBlockAdded", owner) ? 1 : 0;
        ok += redirect(
            node,
            "onBlockPlacedBy",
            "func_149689_a",
            "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;)V",
            "onBlockPlacedBy",
            owner) ? 1 : 0;
        ok += redirect(
            node,
            "func_149951_m",
            "func_149951_m",
            "(Lnet/minecraft/world/World;III)Lnet/minecraft/inventory/IInventory;",
            "getInventory",
            owner) ? 1 : 0;
        ok += redirect(
            node,
            "setBlockBoundsBasedOnState",
            "func_149719_a",
            "(Lnet/minecraft/world/IBlockAccess;III)V",
            "setBlockBoundsBasedOnState",
            owner) ? 1 : 0;
        LOG.info("Patched BlockChest ({}/5 methods)", ok);
        return write(node);
    }

    private byte[] patchTile(byte[] bytes) {
        ClassNode node = read(bytes);
        String owner = "net/minecraft/tileentity/TileEntityChest";
        boolean dev = ChestsCorePlugin.deobfuscatedEnvironment;

        // Pairing state: 0 = unknown (chests from before the mod: vanilla rules), 1 = single, 2..5 = partner side.
        node.fields.add(new FieldNode(ACC_PUBLIC, PAIR_FIELD, "B", null, null));

        int ok = redirect(node, "checkForAdjacentChests", "func_145979_i", "()V", "checkForAdjacentChests", owner) ? 1 : 0;

        // Persist the pairing state: call the hook before every return of readFromNBT / writeToNBT.
        ok += appendCall(node, "readFromNBT", "func_145839_a", "readNBT", owner) ? 1 : 0;
        ok += appendCall(node, "writeToNBT", "func_145841_b", "writeNBT", owner) ? 1 : 0;

        // Sync the pairing state to clients (TileEntityChest has no description packet in 1.7.10).
        MethodNode describe = new MethodNode(
            ACC_PUBLIC,
            dev ? "getDescriptionPacket" : "func_145844_m",
            "()Lnet/minecraft/network/Packet;",
            null,
            null);
        describe.instructions.add(new VarInsnNode(ALOAD, 0));
        describe.instructions.add(
            new MethodInsnNode(
                INVOKESTATIC,
                HOOKS,
                "getDescriptionPacket",
                "(L" + owner + ";)Lnet/minecraft/network/Packet;",
                false));
        describe.instructions.add(new InsnNode(ARETURN));
        node.methods.add(describe);

        // onDataPacket is added by Forge and keeps its name in every environment.
        MethodNode receive = new MethodNode(
            ACC_PUBLIC,
            "onDataPacket",
            "(Lnet/minecraft/network/NetworkManager;Lnet/minecraft/network/play/server/S35PacketUpdateTileEntity;)V",
            null,
            null);
        receive.instructions.add(new VarInsnNode(ALOAD, 0));
        receive.instructions.add(new VarInsnNode(ALOAD, 2));
        receive.instructions.add(
            new MethodInsnNode(
                INVOKESTATIC,
                HOOKS,
                "onDataPacket",
                "(L" + owner + ";Lnet/minecraft/network/play/server/S35PacketUpdateTileEntity;)V",
                false));
        receive.instructions.add(new InsnNode(RETURN));
        node.methods.add(receive);

        LOG.info("Patched TileEntityChest ({}/3 methods, pairing state and sync added)", ok);
        return write(node);
    }

    private static boolean appendCall(ClassNode node, String mcp, String srg, String hook, String owner) {
        MethodNode m = find(node, mcp, srg, "(Lnet/minecraft/nbt/NBTTagCompound;)V");
        if (m == null) {
            LOG.error("Could not find {}.{}", node.name, mcp);
            return false;
        }
        ListIterator<AbstractInsnNode> it = m.instructions.iterator();
        while (it.hasNext()) {
            AbstractInsnNode insn = it.next();
            if (insn.getOpcode() == RETURN) {
                InsnList call = new InsnList();
                call.add(new VarInsnNode(ALOAD, 0));
                call.add(new VarInsnNode(ALOAD, 1));
                call.add(
                    new MethodInsnNode(
                        INVOKESTATIC,
                        HOOKS,
                        hook,
                        "(L" + owner + ";Lnet/minecraft/nbt/NBTTagCompound;)V",
                        false));
                m.instructions.insertBefore(insn, call);
            }
        }
        return true;
    }
}
