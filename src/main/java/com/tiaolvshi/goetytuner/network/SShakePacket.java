/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.network;

import com.tiaolvshi.goetytuner.client.ClientCameraShake;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 视角震颤包（服务端 → 客户端）。
 *
 * 二阶段低谷重音触发：服务端向附近玩家发送，客户端收到后
 * 在 ACCENT_SHAKE_TICKS 时间内对摄像机施加 roll 抖动（见 ClientCameraShake）。
 */
public class SShakePacket {

    public final int ticks;
    public final float strength;

    public SShakePacket(int ticks, float strength) {
        this.ticks = ticks;
        this.strength = strength;
    }

    public static void encode(SShakePacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.ticks);
        buf.writeFloat(pkt.strength);
    }

    public static SShakePacket decode(FriendlyByteBuf buf) {
        return new SShakePacket(buf.readVarInt(), buf.readFloat());
    }

    public static void consume(SShakePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientCameraShake.handle(pkt.ticks, pkt.strength)));
        ctx.get().setPacketHandled(true);
    }
}
