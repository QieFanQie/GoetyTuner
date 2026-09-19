/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

/**
 * 【0.0.8】客户端死亡动画复位（由 {@link com.tiaolvshi.goetytuner.network.SEntityRevivePacket} 触发）。
 *
 * <p>背景：{@code LivingEntity.deathTime} 不是同步数据，服务端把它清零客户端并不知道
 * （详见 {@code SEntityRevivePacket} 的类注释）。本类把客户端那份状态一并清零，
 * 让「服务端已复活」与「客户端还在演死亡动画」不再脱节。
 *
 * <p>为什么必须三样都清：
 * <ul>
 *   <li>{@code deathTime}：{@code LivingEntityRenderer.setupRotations} 用它做**倒下旋转**
 *       （旋转量 = sqrt((deathTime + partialTick) / 20) * 90°），不清就一直是躺倒姿势；</li>
 *   <li>{@code hurtTime}/{@code hurtDuration}：{@code getOverlayCoords} 是
 *       {@code hurtTime > 0 || deathTime > 0} 就叠**红色受伤层**；</li>
 *   <li>{@code Pose.DYING}：若不慎被置为垂死姿态，一起恢复成站立。</li>
 * </ul>
 *
 * <p>实体在客户端已被移除时静默跳过（此时该客户端看不到这个 Boss，无需处理）。
 */
public final class ClientDeathAnimation {

    private ClientDeathAnimation() {
    }

    public static void reset(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity e = mc.level.getEntity(entityId);
        if (!(e instanceof LivingEntity living)) {
            return;
        }
        living.deathTime = 0;
        living.hurtTime = 0;
        living.hurtDuration = 0;
        if (living.getPose() == Pose.DYING) {
            living.setPose(Pose.STANDING);
        }
    }
}
