/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.entity;

/**
 * 【0.0.18】「三颗悬浮立方体高亮」的数据来源。
 *
 * <p>0.0.10 引入的 {@code TunerOrbLayer} 原本直接写死成 {@code RenderLayer<TunerBoss, TunerModel>}。
 * 0.0.18 的调律师仆从也要挂同一条渲染层（"仆从与本体同形"），所以把渲染层真正需要的那一个
 * 方法抽成本接口——渲染层因此不再依赖具体的实体类。
 *
 * <p>实现者负责：① 用 {@code SynchedEntityData} 把"当前在施放哪几类聚晶"同步到客户端
 * （位掩码，bit0..3 = ATTACK/DEFENSE/SUMMON/OTHER）；② 在客户端每 tick 把掩码平滑成 0~1 的
 * 高亮强度。渲染层只读结果，不做任何插值或状态推断。
 */
public interface OrbHighlightSource {

    /**
     * @param category    类别序号（{@link com.tiaolvshi.goetytuner.focus.FocusCategory#ordinal()}，0..2 对应三颗立方体）
     * @param partialTick 渲染的部分刻（客户端在锚点之间插值）
     * @return 该类别立方体的高亮强度（0~1）
     */
    float getOrbHighlight(int category, float partialTick);
}
