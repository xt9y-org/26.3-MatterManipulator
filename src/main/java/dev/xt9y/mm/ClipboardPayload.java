package dev.xt9y.mm;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Small per-player clipboard preview; game data and actual block states
 * always remain owned and validated on the server.
 */
public record ClipboardPayload(String offsets) implements CustomPacketPayload {
    public static final Type<ClipboardPayload> TYPE =
        new Type<>(MatterManipulatorMod.id("clipboard"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardPayload> CODEC =
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ClipboardPayload::offsets, ClipboardPayload::new);
    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
