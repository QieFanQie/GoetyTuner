/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.client;

import com.tiaolvshi.goetytuner.GoetyTuner;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端视角震颤：二阶段低谷重音的附加表现。
 *
 * 服务端 SShakePacket 触发 handle()，此后 shakeTicks 时间内
 * 每帧对摄像机 roll 施加随机抖动（幅度 = ACCENT_SHAKE_STRENGTH 度）。
 * 事件挂 FORGE 总线（ViewportEvent/TickEvent 均在主总线触发）。
 */
@Mod.EventBusSubscriber(modid = GoetyTuner.MOD_ID, value = Dist.CLIENT)
public class ClientCameraShake {

    private static int shakeTicks = 0;
    private static float strength = 0.0F;

    /** 由 SShakePacket 调用（客户端主线程） */
    public static void handle(int ticks, float str) {
        if (ticks <= 0) {
            return;
        }
        // 叠加取最大值，避免多个boss重音互相覆盖闪断
        shakeTicks = Math.max(shakeTicks, ticks);
        strength = Math.max(strength, str);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (shakeTicks > 0) {
            shakeTicks--;
            if (shakeTicks == 0) {
                strength = 0.0F;
            }
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shakeTicks <= 0 || strength <= 0.0F) {
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        // roll 抖动随剩余时间衰减，末尾平滑收束
        float decay = Math.min(1.0F, shakeTicks / 5.0F);
        float jitter = (player.getRandom().nextFloat() - 0.5F) * strength * decay;
        event.setRoll(event.getRoll() + jitter);
    }
}
