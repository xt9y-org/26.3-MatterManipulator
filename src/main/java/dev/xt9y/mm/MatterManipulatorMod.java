package dev.xt9y.mm;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class MatterManipulatorMod implements ModInitializer {
    public static final String MOD_ID = "mattermanipulator";
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static final Item TOOL;
    static {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id("matter_manipulator"));
        TOOL = Registry.register(BuiltInRegistries.ITEM, key,
            new MatterManipulatorItem(new Item.Properties().setId(key).stacksTo(1)));
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClipboardPayload.TYPE, ClipboardPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HoldPayload.TYPE, HoldPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(HoldPayload.TYPE, (payload, context) -> {
            String[] parts = payload.data().split(":", -1);
            switch (parts[0]) {
                case "start" -> {
                    if (parts.length != 4) return;
                    try {
                        SimpleEditor.begin(context.player(),
                            new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                                Integer.parseInt(parts[3])));
                    } catch (NumberFormatException ignored) {}
                }
                case "select" -> {
                    if (parts.length != 4) return;
                    try {
                        BlockPos selected = new BlockPos(Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                        // Selection packets must describe nearby positions and be made
                        // while holding the tool; the server owns all world edits.
                        if (context.player().getMainHandItem().is(TOOL)
                                && selected.distSqr(context.player().blockPosition()) <= 100) {
                            SimpleEditor.select(context.player(), selected);
                        }
                    } catch (NumberFormatException ignored) {}
                }
                case "pulse" -> SimpleEditor.pulse(context.player());
                case "stop" -> SimpleEditor.stop(context.player());
                default -> {}
            }
        });
        // Intercept right-click BEFORE vanilla chest/furnace/container handling.
        // Without this, the block GUI can swallow a selection click before
        // MatterManipulatorItem.useOn is ever invoked.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().is(TOOL)) {
                return InteractionResult.PASS;
            }
            // Client Shift+Use must reach the client listener registered by
            // MatterManipulatorClient so it can distinguish short selection
            // from a >=250 ms edit hold. Returning SUCCESS here prevents
            // later UseBlockCallback listeners from running.
            if (level.isClientSide() && player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide() && !player.isShiftKeyDown()
                    && player instanceof ServerPlayer serverPlayer) {
                SimpleEditor.select(serverPlayer,
                    hit.getBlockPos().relative(hit.getDirection()));
            }
            return InteractionResult.SUCCESS;
        });

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
            .register(tab -> tab.accept(TOOL));
        ServerTickEvents.END_SERVER_TICK.register(SimpleEditor::tick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            dispatcher.register(Commands.literal("mm")
                .executes(ctx -> SimpleEditor.status(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("status")
                    .executes(ctx -> SimpleEditor.status(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("clear")
                    .executes(ctx -> SimpleEditor.clear(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("mode")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> SimpleEditor.mode(ctx.getSource().getPlayerOrException(),
                            StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("fill")
                    .executes(ctx -> SimpleEditor.mode(ctx.getSource().getPlayerOrException(), "fill")))
                .then(Commands.literal("geometry")
                    .then(Commands.literal("fill")
                        .then(Commands.literal("cube")
                            .executes(ctx -> SimpleEditor.mode(ctx.getSource().getPlayerOrException(), "fill")))))
                .then(Commands.literal("copy")
                    .executes(ctx -> SimpleEditor.mode(ctx.getSource().getPlayerOrException(), "copy")))
                .then(Commands.literal("cut")
                    .executes(ctx -> SimpleEditor.mode(ctx.getSource().getPlayerOrException(), "cut")))
                .then(Commands.literal("paste")
                    .executes(ctx -> SimpleEditor.mode(ctx.getSource().getPlayerOrException(), "paste")))
                .then(Commands.literal("flip")
                    .then(Commands.argument("axis", StringArgumentType.word())
                        .executes(ctx -> SimpleEditor.flip(ctx.getSource().getPlayerOrException(),
                            StringArgumentType.getString(ctx, "axis")))))
                .then(Commands.literal("shortcut")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> SimpleEditor.shortcut(ctx.getSource().getPlayerOrException(),
                            StringArgumentType.getString(ctx, "name")))))
            )
        );
    }
}
