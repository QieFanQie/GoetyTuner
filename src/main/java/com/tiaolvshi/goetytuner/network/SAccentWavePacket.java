/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.network;

import com.tiaolvshi.goetytuner.client.AccentWaveRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：在**世界坐标** {@code (x, y, z)} 触发一条声波涟漪（纯视觉）。
 *
 * <p>击退与音效仍由服务端权威执行，本包只负责让客户端"看见"。
 *
 * <p>【0.0.18】**载荷由实体 id 改为绝对坐标**。原先只带 {@code entityId}，客户端收到后用
 * {@code level.getEntity(id)} 反查位置——这只有"能反查到某个实体"时才成立（0.0.10 的实现
 * 甚至要求它恰好是一只 {@code TunerBoss}）。0.0.18 的「调律波纹聚晶」由**玩家**释放，
 * 涟漪锚点是一个裸坐标、没有实体可查，故统一改成直接下发坐标：
 * 触发点由服务端算好，客户端不再做任何反查或插值推断（顺带也更准——原先客户端读到的是
 * 该刻的实体位置，而服务端触发点本就是权威值）。
 */
public record SAccentWavePacket(double x, double y, double z) {

    public static void encode(SAccentWavePacket packet, FriendlyByteBuf buffer) {
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
    }

    public static SAccentWavePacket decode(FriendlyByteBuf buffer) {
        return new SAccentWavePacket(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    public static void consume(SAccentWavePacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AccentWaveRenderer.trigger(packet.x, packet.y, packet.z)));
        context.get().setPacketHandled(true);
    }
}
