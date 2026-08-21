/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.focus;

/**
 * 聚晶功能分块。
 * 防御/其他类不参与评分（均匀随机），攻击/召唤类参与动态评分。
 */
public enum FocusCategory {
    ATTACK("attack"),
    DEFENSE("defense"),
    SUMMON("summon"),
    OTHER("other");

    private final String id;

    FocusCategory(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static FocusCategory byId(String id) {
        for (FocusCategory c : values()) {
            if (c.id.equalsIgnoreCase(id)) {
                return c;
            }
        }
        return OTHER;
    }

    /** 是否参与评分（攻击/召唤） */
    public boolean isScored() {
        return this == ATTACK || this == SUMMON;
    }
}
