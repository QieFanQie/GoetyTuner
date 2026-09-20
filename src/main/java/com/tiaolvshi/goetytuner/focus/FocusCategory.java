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

    /**
     * 【0.0.18】配置数字串 → 功能分块序列（<b>Boss 与仆从共用的唯一实现</b>）。
     *
     * <p>映射：{@code 1=防御 2=攻击 3=召唤 4=其他}。非法字符忽略；
     * 全非法/为空时回退默认的"防御→攻击→召唤"。
     *
     * <p>原先这段逻辑是 {@code TunerBoss#parseRoleSpec} 的私有实现。0.0.18 的调律师仆从
     * 也要按配置的序列轮换施法，故上提到枚举里——两处各自抄一份是本项目明令避免的做法
     * （同一职责只允许一处实现）。
     */
    public static FocusCategory[] parseRoleSpec(String spec) {
        java.util.List<FocusCategory> out = new java.util.ArrayList<>();
        if (spec != null) {
            for (char c : spec.toCharArray()) {
                switch (c) {
                    case '1' -> out.add(DEFENSE);
                    case '2' -> out.add(ATTACK);
                    case '3' -> out.add(SUMMON);
                    case '4' -> out.add(OTHER);
                    default -> {
                        // 非法字符忽略
                    }
                }
            }
        }
        if (out.isEmpty()) {
            out.add(DEFENSE);
            out.add(ATTACK);
            out.add(SUMMON);
        }
        return out.toArray(new FocusCategory[0]);
    }
}
