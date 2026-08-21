/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.init;

import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.entity.TunerBoss;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * MOD 总线事件。
 *
 * <p>注意：{@link EntityAttributeCreationEvent} 是 <b>mod bus</b> 事件，不能放在
 * 默认 FORGE bus 的 {@link ModEvents} 中——那会导致实体属性从未注册，服务器日志
 * 反复刷屏 {@code Entity goetytuner:tuner has no attributes}（属性注册缺失时
 * EntityType 工厂创建出的实体没有属性表，客户端/服务端调用 getAttribute 返回 null）。
 */
@Mod.EventBusSubscriber(modid = GoetyTuner.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModBusEvents {

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.TUNER.get(), TunerBoss.createAttributes().build());
        // 注册确认日志：若日志缺失且游戏内反复刷 "has no attributes"，说明本类
        // 被移到了错误的 event bus（必须是 MOD bus）或订阅失效。
        GoetyTuner.LOGGER.info("[GoetyTuner] Registered attribute supplier for entity 'tuner'");
    }
}
