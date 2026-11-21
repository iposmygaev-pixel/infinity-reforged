package com.infinityreforged.moderncombat.client;

import java.util.Set;

import org.lwjgl.input.Keyboard;

import com.infinityreforged.moderncombat.CombatData;
import com.infinityreforged.moderncombat.ItemShield;
import com.infinityreforged.moderncombat.ModernCombat;
import com.infinityreforged.moderncombat.network.PacketOffhandAction;
import com.infinityreforged.moderncombat.offhand.Offhand;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovingObjectPosition;

/**
 * Client input for the off hand: F swaps hands, holding the use key raises an off-hand shield.
 */
public class OffhandInput {

    public static final KeyBinding SWAP = new KeyBinding(
        "key.moderncombat.swapHands",
        Keyboard.KEY_F,
        "key.categories.inventory");

    private boolean sentBlocking;

    public OffhandInput() {
        ClientRegistry.registerKeyBinding(SWAP);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityClientPlayerMP player = mc.thePlayer;
        if (player == null) {
            sentBlocking = false;
            return;
        }
        CombatData data = CombatData.get(player);

        if (event.phase == TickEvent.Phase.START) {
            // 1.9+: no attacking or mining while the shield is raised.
            if (data.offhandBlocking) {
                while (mc.gameSettings.keyBindAttack.isPressed()) {}
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
            }
            return;
        }

        if (!(player.movementInput instanceof SlowedInput)) {
            player.movementInput = new SlowedInput(player.movementInput);
        }

        while (SWAP.isPressed()) {
            if (mc.currentScreen == null) {
                ModernCombat.network.sendToServer(new PacketOffhandAction(PacketOffhandAction.SWAP));
                data.offhandBlocking = false;
            }
        }

        boolean want = wantsToBlock(mc, data);
        if (want && !data.offhandBlocking) {
            data.offhandBlocking = true;
            data.offhandBlockStart = player.ticksExisted;
        } else if (!want) {
            data.offhandBlocking = false;
        }
        if (want != sentBlocking) {
            sentBlocking = want;
            ModernCombat.network.sendToServer(
                new PacketOffhandAction(want ? PacketOffhandAction.RAISE_SHIELD : PacketOffhandAction.LOWER_SHIELD));
        }
    }

    private static boolean wantsToBlock(Minecraft mc, CombatData data) {
        if (mc.currentScreen != null || !mc.gameSettings.keyBindUseItem.getIsKeyPressed()) return false;
        if (!Offhand.isShield(data.offhand) || data.shieldCooldown > 0 || mc.thePlayer.isUsingItem()) return false;
        return mainHandAllowsOffhand(mc.thePlayer.getCurrentEquippedItem(), mc.objectMouseOver);
    }

    /**
     * 1.9+ uses the off hand when the main hand does nothing on right-click. 1.7.10 can't tell that, so the shield
     * goes up only with an empty hand, a weapon or a tool (when not aimed at a block for modded tools, which may use
     * blocks on right-click, e.g. Tinkers' torch placing).
     */
    private static boolean mainHandAllowsOffhand(ItemStack main, MovingObjectPosition mop) {
        if (main == null) return true;
        Item item = main.getItem();
        if (item instanceof ItemShield) return false;
        if (item instanceof ItemSword || item instanceof ItemTool) return true;
        boolean atBlock = mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
        Set<String> classes = item.getToolClasses(main);
        return !atBlock && classes != null && !classes.isEmpty();
    }

    /** Raising the shield slows walking to 20%, like using an item. */
    static class SlowedInput extends MovementInput {

        private final MovementInput inner;

        SlowedInput(MovementInput inner) {
            this.inner = inner;
        }

        @Override
        public void updatePlayerMoveState() {
            inner.updatePlayerMoveState();
            moveStrafe = inner.moveStrafe;
            moveForward = inner.moveForward;
            jump = inner.jump;
            sneak = inner.sneak;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null && CombatData.get(mc.thePlayer).offhandBlocking && !mc.thePlayer.isRiding()) {
                moveStrafe *= 0.2F;
                moveForward *= 0.2F;
            }
        }
    }
}
