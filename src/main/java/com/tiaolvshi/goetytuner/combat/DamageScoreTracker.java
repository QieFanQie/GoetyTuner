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
import com.tiaolvshi.goetytuner.config.TunerCommonConfig;
import com.tiaolvshi.goetytuner.entity.TunerBoss;
import com.tiaolvshi.goetytuner.focus.FocusCategory;
import com.tiaolvshi.goetytuner.focus.FocusEntry;
import com.tiaolvshi.goetytuner.focus.FocusPoolManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 攻击类聚晶动态评分追踪。
 *
 * 机制：通过「伤害来源为该boss（含其弹射物）的伤害事件」追踪伤害并求和平均，
 * 得到该法术的实际DPS；与期望DPS（过去释放的所有攻击聚晶的平均DPS）比较：
 *   偏移 += (实际dps / 期望dps - 1) * rate，不直接修改静态评分。
 *
 * 已知限制（按需求标注）：仅识别直伤；DoT（持续伤害）无法归因，暂不处理。
 *
 * 归因方式：法术释放后一个时间窗（默认200tick）内，直接伤害来源实体==boss
 * 或弹射物owner==boss的伤害计入该聚晶。
 */
public class DamageScoreTracker {

    private static class Attribution {
        final FocusEntry entry;
        final long startedTick;
        double damageSum;
        int hits;

        Attribution(FocusEntry entry, long startedTick) {
            this.entry = entry;
            this.startedTick = startedTick;
        }
    }

    /** 期望DPS：全部攻击聚晶滚动平均 */
    private double expectedDps = 5.0;
    private int sampledSpells = 0;

    /** 活跃归因窗口：可能同时有多个（高潮三通道） */
    private final Map<FocusEntry, Attribution> active = new HashMap<>();
    private static final long WINDOW_TICKS = 200;

    /**
     * boss受击时由 TunerBoss#hurt 转发。
     * 注意：这里记录的是 boss 造成的伤害 —— 通过 LivingHurtEvent 事件总线收集，
     * 事件处理器见 {@link CombatEvents#onLivingHurt}。
     */
    public void recordDamage(LivingEntity victim, DamageSource source, float amount, TunerBoss boss) {
        if (active.isEmpty() || amount <= 0) {
            return;
        }
        // 伤害来源归因：直接来源是boss，或弹射物的owner是boss
        Entity direct = source.getEntity();
        Entity causual = source.getDirectEntity();
        boolean fromBoss = (direct == boss) || (causual == boss);
        if (!fromBoss && causual instanceof Projectile proj) {
            fromBoss = proj.getOwner() == boss;
        }
        if (!fromBoss) {
            return;
        }
        // 窗口内最后一个攻击聚晶承担该伤害（单通道时精确；多通道时近似均摊）
        Attribution target = latestActive();
        if (target != null) {
            target.damageSum += amount;
            target.hits++;
        }
    }

    private Attribution latestActive() {
        Attribution best = null;
        for (Attribution a : active.values()) {
            if (best == null || a.startedTick > best.startedTick) {
                best = a;
            }
        }
        return best;
    }

    /** 法术释放完成时开启归因窗口 */
    public void beginAttribution(ServerLevel level, TunerBoss boss, FocusEntry entry) {
        if (entry.getCategory() != FocusCategory.ATTACK) {
            return;
        }
        active.put(entry, new Attribution(entry, level.getGameTime()));
    }

    /** 每tick：关闭过期窗口并结算评分偏移 */
    public void tick(ServerLevel level, TunerBoss boss, FocusPoolManager pools) {
        if (active.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<FocusEntry, Attribution>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Attribution a = it.next().getValue();
            if (now - a.startedTick >= WINDOW_TICKS) {
                settle(a);
                it.remove();
            }
        }
    }

    /** 结算：实际DPS vs 期望DPS → 修正动态偏移 */
    private void settle(Attribution a) {
        double dps = a.damageSum / (WINDOW_TICKS / 20.0D); // 每秒伤害
        // 更新期望（滚动平均）
        sampledSpells++;
        expectedDps = expectedDps + (dps - expectedDps) / sampledSpells;

        double rate = TunerCommonConfig.DPS_ADJUST_RATE.get();
        double cap = TunerCommonConfig.DYNAMIC_SCORE_CAP.get();
        double delta = (dps / Math.max(0.1D, expectedDps) - 1.0D) * rate;
        a.entry.addDynamicAttackOffset(delta, cap);
        GoetyTuner.LOGGER.debug("[Tuner] DPS settle {}={} dmg ({} dps) → offset {+}{}, expected {}",
                a.entry.getItemId(), a.damageSum, String.format("%.1f", dps),
                String.format("%.2f", delta), String.format("%.1f", expectedDps));
    }
}
