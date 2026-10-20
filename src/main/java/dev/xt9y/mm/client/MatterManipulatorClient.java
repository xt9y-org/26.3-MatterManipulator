package dev.xt9y.mm.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import dev.xt9y.mm.MatterManipulatorMod;
import dev.xt9y.mm.StatePayload;
import dev.xt9y.mm.ClipboardPayload;
import dev.xt9y.mm.HoldPayload;

/**
 * M cycles four modes. Ctrl+C/X/V select modes. A one-second Shift+RMB
 * hold begins an operation, with periodic heartbeats until release.
 */
public final class MatterManipulatorClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath(MatterManipulatorMod.MOD_ID, "controls"));
    private final KeyMapping cycle = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.mattermanipulator.menu", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_M, CATEGORY));
    private final KeyMapping control = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.mm-ctrl", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_LCONTROL, CATEGORY));
    private final KeyMapping copy = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.mm-copy", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_C, CATEGORY));
    private final KeyMapping cut = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.mm-cut", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_X, CATEGORY));
    private final KeyMapping paste = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.mm-paste", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_V, CATEGORY));
    private final KeyMapping reset = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.mm-reset", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_Z, CATEGORY));

    private long holdStartedNanos;
    private boolean applying;
    private int heartbeatTicks;

    private static void send(net.minecraft.client.Minecraft mc, String command) {
        if (mc.getConnection() != null) mc.getConnection().sendCommand("mm " + command);
    }

    private void stop(net.minecraft.client.Minecraft mc) {
        if (applying && ClientPlayNetworking.canSend(HoldPayload.TYPE))
            ClientPlayNetworking.send(new HoldPayload("stop"));
        holdStartedNanos = 0;
        heartbeatTicks = 0;
        applying = false;
    }

    private static BlockPos aim(net.minecraft.client.Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult hit) {
            return hit.getBlockPos().relative(hit.getDirection());
        }
        return BlockPos.containing(mc.player.getEyePosition()
            .add(mc.player.getLookAngle().scale(5.0)));
    }

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(StatePayload.TYPE,
            (payload, context) -> context.client().execute(
                () -> Preview.receiveState(payload.data())));
        ClientPlayNetworking.registerGlobalReceiver(ClipboardPayload.TYPE,
            (payload, context) -> context.client().execute(
                () -> Preview.receiveClipboard(payload.offsets())));
        new SelectionRenderer().register();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean ready = mc.player != null && mc.gui.screen() == null
                && mc.player.getMainHandItem().is(MatterManipulatorMod.TOOL);
            while (cycle.consumeClick()) if (ready) {
                stop(mc);
                send(mc, "mode next");
            }
            boolean modifier = ready && control.isDown();
            while (copy.consumeClick()) if (modifier) {
                stop(mc);
                send(mc, "shortcut copy");
            }
            while (cut.consumeClick()) if (modifier) {
                stop(mc);
                send(mc, "shortcut cut");
            }
            while (paste.consumeClick()) if (modifier) {
                stop(mc);
                send(mc, "shortcut paste");
            }
            while (reset.consumeClick()) if (modifier) {
                stop(mc);
                send(mc, "shortcut reset");
            }

            boolean held = ready && mc.player.isShiftKeyDown() && mc.options.keyUse.isDown();
            if (!held) {
                stop(mc);
            } else if (holdStartedNanos == 0) {
                holdStartedNanos = System.nanoTime();
            } else if (!applying && System.nanoTime() - holdStartedNanos >= 1_000_000_000L) {
                BlockPos target = aim(mc);
                if (ClientPlayNetworking.canSend(HoldPayload.TYPE)) {
                    ClientPlayNetworking.send(new HoldPayload("start:"
                        + target.getX() + ":" + target.getY() + ":" + target.getZ()));
                }
                applying = true;
                heartbeatTicks = 0;
            } else if (applying && ++heartbeatTicks >= 4) {
                if (ClientPlayNetworking.canSend(HoldPayload.TYPE))
                    ClientPlayNetworking.send(new HoldPayload("pulse"));
                heartbeatTicks = 0;
            }
            Preview.tick(mc);
        });
    }
}
