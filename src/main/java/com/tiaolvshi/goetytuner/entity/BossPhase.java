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
 * 音乐阶段（服务端权威）。
 * 铺垫 BUILDUP：正常施法（防御→召唤→攻击轮换，前摇/冷却正常）
 * 高潮 CLIMAX：前摇缩短、冷却不变，三系法术并行施放，不可移动
 * 低谷 VALLEY：无法施法，仆从获得增益，boss获得侵蚀+黑暗，不可移动
 */
public enum BossPhase {
    BUILDUP("buildup"),
    CLIMAX("climax"),
    VALLEY("valley");

    private final String id;

    BossPhase(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static BossPhase byId(String id) {
        for (BossPhase p : values()) {
            if (p.id.equalsIgnoreCase(id)) {
                return p;
            }
        }
        return BUILDUP;
    }

    public boolean canCast() {
        return this == BUILDUP || this == CLIMAX;
    }

    public boolean canMove() {
        return this == BUILDUP;
    }
}
