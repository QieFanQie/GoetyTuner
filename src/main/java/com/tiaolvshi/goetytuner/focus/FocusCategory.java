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

    /**
     * 【v0.0.3】宽松分类：LLM 可能输出非规范字符串（大小写/别名/中英文/拼写误差）。
     * 优先精确匹配，否则按关键词包含匹配，最后兜底 OTHER。
     */
    public static FocusCategory byIdLenient(String id) {
        if (id == null) return OTHER;
        FocusCategory exact = byId(id);
        if (exact != OTHER) return exact;
        String s = id.toLowerCase();
        if (s.contains("summon") || s.contains("召唤") || s.contains("minion")
                || s.contains("spawn") || s.contains("仆从") || s.contains("随从")) {
            return SUMMON;
        }
        if (s.contains("defen") || s.contains("防御") || s.contains("guard")
                || s.contains("armor") || s.contains("shield")) {
            return DEFENSE;
        }
        if (s.contains("attack") || s.contains("攻击") || s.contains("atk")
                || s.contains("offense") || s.contains("damage") || s.contains("输出")) {
            return ATTACK;
        }
        return OTHER;
    }

    /** 是否参与评分（攻击/召唤） */
    public boolean isScored() {
        return this == ATTACK || this == SUMMON;
    }
}
