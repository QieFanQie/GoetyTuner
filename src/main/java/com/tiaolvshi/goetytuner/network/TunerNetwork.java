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

import java.util.Optional;

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
    }

    public static void sendToPlayer(Object pkt, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), pkt);
    }
}
