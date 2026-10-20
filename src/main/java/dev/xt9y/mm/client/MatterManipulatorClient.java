package dev.xt9y.mm.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import dev.xt9y.mm.MatterManipulatorMod;
import dev.xt9y.mm.StatePayload;
import dev.xt9y.mm.ClipboardPayload;
import dev.xt9y.mm.HoldPayload;

/**
 * Debounced GTNH-style inputs: RMB marks adjacent position; short Shift+RMB
 * marks the targeted block; Shift+RMB >=1 s starts the progressive edit.
 */
public final class MatterManipulatorClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath(MatterManipulatorMod.MOD_ID, "controls"));
    private static final long HOLD_NANOS = 250_000_000L;

    private final KeyMapping cycle = key("key.mattermanipulator.menu", InputConstants.KEY_M);
    private final KeyMapping control = key("key.mm-ctrl", InputConstants.KEY_LCONTROL);
    private final KeyMapping copy = key("key.mm-copy", InputConstants.KEY_C);
    private final KeyMapping cut = key("key.mm-cut", InputConstants.KEY_X);
    private final KeyMapping paste = key("key.mm-paste", InputConstants.KEY_V);
    private final KeyMapping reset = key("key.mm-reset", InputConstants.KEY_Z);
    private final KeyMapping flipX = key("key.mm-flip-x", InputConstants.KEY_X);
    private final KeyMapping flipY = key("key.mm-flip-y", InputConstants.KEY_Y);
    private final KeyMapping flipZ = key("key.mm-flip-z", InputConstants.KEY_Z);

    private boolean lastMouseDown;
    private long shiftedStartNanos;
    private BlockPos shiftedTarget;
    private boolean building;
    private int heartbeatTicks;

    private static KeyMapping key(String name, int code) {
        return KeyMappingHelper.registerKeyMapping(
            new KeyMapping(name, InputConstants.Type.KEYBOARD, code, CATEGORY));
    }

    private static void command(net.minecraft.client.Minecraft mc, String argument) {
        if (mc.getConnection() != null) mc.getConnection().sendCommand("mm " + argument);
    }

    private static boolean hold(String data) {
        if (!ClientPlayNetworking.canSend(HoldPayload.TYPE)) return false;
        ClientPlayNetworking.send(new HoldPayload(data));
        return true;
    }

    private static void select(BlockPos position) {
        if (position != null)
            hold("select:" + position.getX() + ":" + position.getY() + ":" + position.getZ());
    }

    private static BlockPos aimedBlock(net.minecraft.client.Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK)
            return hit.getBlockPos();
        return null;
    }

    private static BlockPos adjacent(net.minecraft.client.Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK)
            return hit.getBlockPos().relative(hit.getDirection());
        return BlockPos.containing(mc.player.getEyePosition()
            .add(mc.player.getLookAngle().scale(5.0)));
    }

    // Vanilla use callbacks fire even on a click shorter than one client tick.
    // Remember the actual targeted block now and commit selection on release,
    // unless the 250 ms hold has started editing.
    private void armShiftUse(BlockPos target) {
        if (shiftedStartNanos != 0 || building) return;
        shiftedStartNanos = System.nanoTime();
        shiftedTarget = target;
    }

    private void cancelHold() {
        if (building) hold("stop");
        shiftedStartNanos = 0;
        shiftedTarget = null;
        heartbeatTicks = 0;
        building = false;
    }

    private void tickInputs(net.minecraft.client.Minecraft mc) {
        boolean ready = mc.player != null && mc.gui.screen() == null
            && mc.player.getMainHandItem().is(MatterManipulatorMod.TOOL);
        boolean ctrl = ready && control.isDown();
        boolean changedMode = false;

        while (cycle.consumeClick()) if (ready) {
            cancelHold();
            command(mc, "mode next");
            changedMode = true;
        }
        while (copy.consumeClick()) if (ctrl) {
            cancelHold();
            command(mc, "shortcut copy");
            changedMode = true;
        }
        while (cut.consumeClick()) if (ctrl) {
            cancelHold();
            command(mc, "shortcut cut");
            changedMode = true;
        }
        while (paste.consumeClick()) if (ctrl) {
            cancelHold();
            command(mc, "shortcut paste");
            changedMode = true;
        }
        while (reset.consumeClick()) if (ctrl) {
            cancelHold();
            command(mc, "shortcut reset");
            changedMode = true;
        }

        boolean flipping = ready && !ctrl && "paste".equals(Preview.mode);
        while (flipX.consumeClick()) if (flipping) { cancelHold(); command(mc, "flip x"); changedMode = true; }
        while (flipY.consumeClick()) if (flipping) { cancelHold(); command(mc, "flip y"); changedMode = true; }
        while (flipZ.consumeClick()) if (flipping) { cancelHold(); command(mc, "flip z"); changedMode = true; }

        // Minecraft's configured Use Item/Place Block binding (mouse or keyboard).
        boolean down = ready && mc.options.keyUse.isDown();
        boolean shift = ready && (mc.hasShiftDown() || mc.options.keyShift.isDown());
        long now = System.nanoTime();

        if (changedMode || !ready) {
            cancelHold();
            lastMouseDown = down;
            return;
        }

        if (down && !lastMouseDown && shift) {
            armShiftUse(aimedBlock(mc));
        }

        if (shiftedStartNanos != 0) {
            if (!down || !shift) {
                // Short sneak-use selects the actual block, but only AFTER
                // release. A long sneak-use never selects a new corner.
                if (!building && !down)
                    select(shiftedTarget);
                cancelHold();
            } else if (!building && now - shiftedStartNanos >= HOLD_NANOS) {
                BlockPos target = adjacent(mc);
                // Don't claim the operation started when the server doesn't
                // advertise our packet channel (e.g. mod missing server-side).
                if (hold("start:" + target.getX() + ":" + target.getY() + ":" + target.getZ())) {
                    building = true;
                    heartbeatTicks = 0;
                }
            } else if (building && ++heartbeatTicks >= 4) {
                hold("pulse");
                heartbeatTicks = 0;
            }
        }
        lastMouseDown = down;
    }

    @Override
    public void onInitializeClient() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() && hand == InteractionHand.MAIN_HAND
                    && player.getMainHandItem().is(MatterManipulatorMod.TOOL)
                    && player.isShiftKeyDown()) {
                armShiftUse(hit.getBlockPos());
            }
            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide() && hand == InteractionHand.MAIN_HAND
                    && player.getMainHandItem().is(MatterManipulatorMod.TOOL)
                    && player.isShiftKeyDown()) {
                armShiftUse(null);
            }
            return InteractionResult.PASS;
        });
        ClientPlayNetworking.registerGlobalReceiver(StatePayload.TYPE,
            (payload, context) -> context.client().execute(
                () -> Preview.receiveState(payload.data())));
        ClientPlayNetworking.registerGlobalReceiver(ClipboardPayload.TYPE,
            (payload, context) -> context.client().execute(
                () -> Preview.receiveClipboard(payload.offsets())));
        new SelectionRenderer().register();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Preview.tick(mc);
            tickInputs(mc);
        });
    }
}
