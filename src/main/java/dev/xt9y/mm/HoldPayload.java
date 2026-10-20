package dev.xt9y.mm;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Lightweight client hold events, separate from chat commands and spam limits. */
public record HoldPayload(String data) implements CustomPacketPayload {
    public static final Type<HoldPayload> TYPE = new Type<>(MatterManipulatorMod.id("hold"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HoldPayload> CODEC =
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8, HoldPayload::data, HoldPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
