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

/**
 * 【0.0.19】「限伤 + 限DPS」的**唯一实现**（每个使用者各持一份实例）。
 *
 * <p>0.0.6 时这段逻辑内联在 {@code TunerBoss} 里（环形缓冲 + 三个字段 + 三个方法）。
 * 0.0.19 用户要求「调律师仆从的减伤限伤机制和本体保持一致」，于是把它抽成本类，
 * 由 `TunerBoss` 与 `TunerServant` **共用同一份代码**——而不是抄一份到仆从里
 * （本项目红线：同一职责只允许一处实现）。
 *
 * <p><b>限伤与限DPS 是两件不同的事（互补）</b>：
 * <ul>
 *   <li><b>限伤</b>：约束<b>单次</b>伤害上限 → 只削掉「一击秒杀 / 巨额爆发」，
 *       对高频小伤害毫无作用（100 次 10 点照样打满 1000）；</li>
 *   <li><b>限DPS</b>：约束<b>每秒总吞吐</b>（滑动 1 秒窗口预算）→ 压制多段 / 多来源持续爆发，
 *       但对「一发超大伤害」只能整段吸收或整段放行，粒度粗、手感突兀。</li>
 * </ul>
 * 两者都开启时先限伤再限DPS：先把单次削到上限，再由每秒预算决定这次能兑现多少。
 *
 * <p><b>为什么在 {@code actuallyHurt} 里用</b>（而不是 {@code hurt}）：
 * 参照 Goety 本体 Boss 的成熟做法（{@code Apostle}/{@code Vizier}/{@code EnderKeeper}/
 * {@code RedstoneMonstrosity} 都在 {@code actuallyHurt} 里对最终伤害取 {@code Math.min}）。
 * {@code actuallyHurt} 是伤害真正生效的唯一入口，放在那里**受击动画、击退、无敌帧
 * （{@code invulnerableTime}）全部照常发生**——玩家看到的是「打中了，但只掉这么多」，
 * 而不是「打了完全没反应」。
 *
 * <p>⚠️ 本类**不做** BYPASSES_INVULNERABILITY / generic_kill / goety:death 的豁免判定——
 * 那是调用方（{@code TunerBoss}）特有的后门语义，放在调用处更清楚。
 */
public final class DamageThrottle {

    /** 限DPS 滑动窗口长度：1 秒 = 20 tick，每 tick 一个槽位 */
    private static final int WINDOW_TICKS = 20;

    /** 每秒伤害滑动窗口（环形数组：槽位 = gameTime % 20，槽内只存该 tick 的伤害） */
    private final float[] window = new float[WINDOW_TICKS];
    private float windowSum = 0.0F;
    private long windowLastTick = Long.MIN_VALUE;

    /**
     * 结算一次伤害：先限伤，再走限DPS 预算。
     *
     * @param now       当前 {@code gameTime}（滑动窗口的时钟）
     * @param amount    本次伤害（已过近战易伤等修正）
     * @param maxHealth 实体最大生命（限伤按它的百分比算）
     * @param hitCapPct 单次伤害上限占最大生命的比例，{@code <= 0} 表示关闭限伤
     * @param dpsCap    每秒伤害上限，{@code <= 0} 表示关闭限DPS
     * @return 允许生效的伤害；DPS 预算已耗尽时返回 {@code -1}（调用方应直接放弃本次伤害）
     */
    public float apply(long now, float amount, float maxHealth, double hitCapPct, double dpsCap) {
        float result = applyHitCap(amount, maxHealth, hitCapPct);
        return applyDpsCap(now, result, dpsCap);
    }

    /** 限伤：把单次伤害压到 最大生命 × hitCapPct（{@code <= 0} 时不生效） */
    private float applyHitCap(float amount, float maxHealth, double hitCapPct) {
        if (hitCapPct <= 0.0D) {
            return amount;
        }
        float cap = (float) Math.max(1.0D, maxHealth * hitCapPct);
        if (amount > cap) {
            GoetyTuner.LOGGER.debug("[Tuner] Hit damage capped: {} -> {} ({}% of max health {})",
                    amount, cap, (int) Math.round(hitCapPct * 100.0D), maxHealth);
            return cap;
        }
        return amount;
    }

    /**
     * 限DPS：滑动 1 秒窗口预算。
     *
     * @return 允许生效的伤害；预算已耗尽时返回 -1（调用方直接放弃本次伤害）
     */
    private float applyDpsCap(long now, float amount, double capPerSecond) {
        if (capPerSecond <= 0.0D) {
            return amount;
        }
        advance(now);
        float budget = (float) capPerSecond - windowSum;
        if (budget <= 0.0F) {
            GoetyTuner.LOGGER.debug("[Tuner] DPS budget exhausted ({}/{} per second), hit absorbed",
                    windowSum, capPerSecond);
            return -1.0F;
        }
        float allowed = Math.min(amount, budget);
        record(now, allowed);
        return allowed;
    }

    /** 推进滑动窗口：把 (lastTick, now] 这些 tick 的旧槽位清零（槽位 = tick % 20，20 tick 后会被复用） */
    private void advance(long now) {
        if (windowLastTick == Long.MIN_VALUE) {
            windowLastTick = now;
            return;
        }
        long delta = now - windowLastTick;
        if (delta <= 0L) {
            return;
        }
        if (delta >= WINDOW_TICKS) {
            for (int i = 0; i < WINDOW_TICKS; i++) {
                window[i] = 0.0F;
            }
            windowSum = 0.0F;
        } else {
            for (long t = windowLastTick + 1L; t <= now; t++) {
                int idx = (int) (t % WINDOW_TICKS);
                windowSum -= window[idx];
                window[idx] = 0.0F;
            }
            if (windowSum < 0.0F) {
                windowSum = 0.0F; // 浮点误差兜底
            }
        }
        windowLastTick = now;
    }

    /** 把本次生效的伤害记入当前 tick 的槽位（同一 tick 多次命中会累加） */
    private void record(long now, float amount) {
        int idx = (int) (now % WINDOW_TICKS);
        window[idx] += amount;
        windowSum += amount;
    }

    /** 清空窗口（实体重建 / 调试用） */
    public void reset() {
        for (int i = 0; i < WINDOW_TICKS; i++) {
            window[i] = 0.0F;
        }
        windowSum = 0.0F;
        windowLastTick = Long.MIN_VALUE;
    }
}
