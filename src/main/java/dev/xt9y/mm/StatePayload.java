package dev.xt9y.mm;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server-authoritative manipulator configuration and coordinates. */
public record StatePayload(String data) implements CustomPacketPayload {
    public static final Type<StatePayload> TYPE =
        new Type<>(MatterManipulatorMod.id("state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC =
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8, StatePayload::data, StatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
