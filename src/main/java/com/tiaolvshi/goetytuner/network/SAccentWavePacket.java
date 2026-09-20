package com.tiaolvshi.goetytuner.network;

import com.tiaolvshi.goetytuner.client.AccentWaveRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Server-triggered visual only; knockback and audio remain server-authoritative. */
public record SAccentWavePacket(int entityId) {
    public static void encode(SAccentWavePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId);
    }
    public static SAccentWavePacket decode(FriendlyByteBuf buffer) {
        return new SAccentWavePacket(buffer.readVarInt());
    }
    public static void consume(SAccentWavePacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AccentWaveRenderer.trigger(packet.entityId)));
        context.get().setPacketHandled(true);
    }
}
