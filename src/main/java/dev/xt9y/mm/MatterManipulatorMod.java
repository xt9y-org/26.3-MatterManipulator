package dev.xt9y.mm;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
                case "pulse" -> SimpleEditor.pulse(context.player());
                case "stop" -> SimpleEditor.stop(context.player());
                default -> {}
            }
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
                .then(Commands.literal("shortcut")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> SimpleEditor.shortcut(ctx.getSource().getPlayerOrException(),
                            StringArgumentType.getString(ctx, "name")))))
            )
        );
    }
}
