/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.combat;

import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.entity.TunerBoss;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 战斗事件路由：把全局伤害事件转发给附近调律师的评分追踪器。
 *
 * boss注册表：实体加入世界时登记，移除时注销（用于事件路由，不做持久化）。
 */
@Mod.EventBusSubscriber(modid = GoetyTuner.MOD_ID)
public class CombatEvents {

    private static final List<ActiveBoss> ACTIVE_BOSSES = new ArrayList<>();

    private record ActiveBoss(UUID id, TunerBoss boss) {
    }

    public static void registerBoss(TunerBoss boss) {
        ACTIVE_BOSSES.add(new ActiveBoss(boss.getUUID(), boss));
    }

    public static void unregisterBoss(TunerBoss boss) {
        ACTIVE_BOSSES.removeIf(a -> a.id.equals(boss.getUUID()));
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (ACTIVE_BOSSES.isEmpty()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        var source = event.getSource();
        for (ActiveBoss ab : ACTIVE_BOSSES) {
            TunerBoss boss = ab.boss;
            if (boss.isRemoved() || !boss.isAlive()) {
                continue;
            }
            // boss本体造成的伤害（直伤归因；DoT暂不识别——已知限制）
            boss.routeDamageForScoring(victim, source, event.getAmount());
            // boss召唤物造成的伤害（召唤输出归因）
            boss.routeMinionDamageForScoring(victim, source, event.getAmount());
        }
    }
}
