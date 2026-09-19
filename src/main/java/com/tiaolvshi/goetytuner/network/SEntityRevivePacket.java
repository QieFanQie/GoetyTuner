/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.network;

import com.tiaolvshi.goetytuner.client.ClientDeathAnimation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 【0.0.7】死亡动画复位包（服务端 → 客户端）。
 *
 * <p><b>为什么必须自己发包</b>：
 * 原版 {@code LivingEntity.deathTime} 是**普通字段、不是 SynchedEntityData**
 * （只在 NBT 里以 "DeathTime" 持久化）。反编译核实：1.20.1 全游戏**只有
 * {@code LocalPlayer.resetPos()} 会把它归零**（玩家复活专用），
 * 服务端其它任何写入都不会传到客户端。
 * 于是「服务端把 Boss 复活（血量恢复、deathTime=0）」后，**客户端那份 deathTime 仍卡在 >0**，
 * {@code LivingEntityRenderer} 会继续按死亡处理——模型保持倒下旋转 + 红色受伤叠加层，
 * 玩家看到的就是「血条是满的，但 Boss 躺着不动」。
 *
 * <p>原版图腾复活广播的实体事件 35 在 1.20.1 的 {@code LivingEntity.handleEntityEvent}
 * 里**没有对应分支**（该 switch 只有 3/29/30/46~52/54/55/60），所以那条路走不通，
 * 必须由本模组自己把复位指令发给客户端。
 */
public class SEntityRevivePacket {

    public final int entityId;

    public SEntityRevivePacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(SEntityRevivePacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.entityId);
    }

    public static SEntityRevivePacket decode(FriendlyByteBuf buf) {
        return new SEntityRevivePacket(buf.readVarInt());
    }

    public static void consume(SEntityRevivePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientDeathAnimation.reset(pkt.entityId)));
        ctx.get().setPacketHandled(true);
    }
}
