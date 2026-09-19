/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.network;

import com.tiaolvshi.goetytuner.GoetyTuner;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.server.level.ServerPlayer;

public class TunerNetwork {
    private static SimpleChannel INSTANCE;
    private static int id = 0;

    private static int nextID() {
        return id++;
    }

    public static void init() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(GoetyTuner.MOD_ID, "channel"),
                () -> "1.0", s -> true, s -> true);

        // 音乐同步包：服务端 → 客户端（进度tick、阶段枚举、二阶段标记）
        INSTANCE.registerMessage(nextID(), SMusicSyncPacket.class,
                SMusicSyncPacket::encode, SMusicSyncPacket::decode, SMusicSyncPacket::consume);

        // 视角震颤包：服务端 → 客户端（二阶段低谷重音）
        INSTANCE.registerMessage(nextID(), SShakePacket.class,
                SShakePacket::encode, SShakePacket::decode, SShakePacket::consume);

        // 【0.0.7】死亡动画复位包：服务端 → 客户端
        // （deathTime 非同步数据，服务端复活后必须显式通知客户端清动画）
        INSTANCE.registerMessage(nextID(), SEntityRevivePacket.class,
                SEntityRevivePacket::encode, SEntityRevivePacket::decode, SEntityRevivePacket::consume);
    }

    public static void sendToPlayer(Object pkt, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), pkt);
    }

    /**
     * 【0.0.7】发送给所有正在追踪该实体的玩家（死亡动画复位用）。
     * 直接复用 Forge 的 TRACKING_ENTITY 分发器，无需自己遍历玩家列表。
     */
    public static void sendToTracking(Object pkt, net.minecraft.world.entity.Entity entity) {
        if (INSTANCE != null && entity != null) {
            INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), pkt);
        }
    }
}
