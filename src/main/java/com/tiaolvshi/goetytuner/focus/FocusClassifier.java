/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.focus;

import java.util.Locale;

/**
 * 聚晶启发式分类器：根据 lang 描述（item.&lt;modid&gt;.&lt;focus&gt;.info）做关键词匹配，
 * 作为 focus_classification.json 未配置条目时的默认分类（LLM 自动分类/手写配置优先覆盖）。
 *
 * <p>判定优先级：SUMMON（召唤仆从）→ OTHER（工具/机动/仆从管理）→ DEFENSE（护盾/增益）
 * → ATTACK（直接伤害）→ OTHER。先判定召唤是因为 "Summons Ice Golem" 这类描述同时
 * 含攻击词（ice）；OTHER 先于 DEFENSE 是为了让 "detect creatures behind walls"
 * （sensing，探测工具）不被 wall 词误判为防御。
 *
 * <p>词表基于 Goety 2.5.56.5 全部 124 个聚晶的英文描述整理；对未知 mod 的附属聚晶
 * 同样适用（描述均为英文 en_us，服务端固定加载）。
 */
public final class FocusClassifier {

    /** 召唤仆从特征词（含具体生物名词，避免与 "Summons Fangs" 等攻击混淆）
     *  【第二十九轮】增补诡厄暮色仆从生物名词（instanceof ISummonSpell 已是权威判定，此处仅兜底） */
    private static final String[] SUMMON_KEYWORDS = {
            "servant", "servants", "golem", "mini-ghast", "mini-ghasts",
            "wolf", "wolves", "flies", "bear", "skeleton", "reaper", "phantom",
            "magma cube", "magma cubes", "doppelganger", "doppelgangers",
            "leapleave", "leapleaves", "ministrosit", "hoglin", "haunted skull", "haunted skulls",
            "minion", "carrion", "vex",
            "troll", "goblin", "kobold", "minotaur", "mosquito", "druid",
            "knight", "yeti", "beetle", "lich"
    };

    /** 工具/机动/仆从管理特征词（在防御/攻击判定之前拦截，避免 "shoots"/"fire" 误判） */
    private static final String[] OTHER_KEYWORDS = {
            "crafting table", "ender chest", "pulverize", "rotat", "recall",
            "command", "order", "glow", "illuminate", "sense", "sensing",
            "mining", "mine blocks", "invisible", "teleports the caster",
            "fly", "flying", "launches", "grapple", "hook", "pull",
            "give them charged", "ignite", "sets a target or block on fire", "ball of light"
    };

    /** 防御/增益特征词（"armor points" 而非 "armor"：sonic_boom 的 "goes through armor" 是攻击特性） */
    private static final String[] DEFENSE_KEYWORDS = {
            "shield", "shields", "armor points", "hide", "fall damage",
            "wall", "walls", "cushion", "protect", "resistance"
    };

    /** 攻击/直接伤害特征词 */
    private static final String[] ATTACK_KEYWORDS = {
            "damag", "shoot", "bolt", "beam", "laser", "fang", "fangs", "spike",
            "explosion", "explode", "knock", "freez", "poison", "fire", "frost",
            "ice", "lightning", "electr", "tremor", "volcano", "eruption",
            "meteor", "lava", "bomb", "slash", "siphon", "dart", "torrent",
            "fling", "current", "burn", "rift", "thorn", "mine", "trap",
            "arrow", "arrows", "tremors", "fist", "pierce", "sonic"
    };

    private FocusClassifier() {
    }

    /** 根据描述文本猜测功能分类 */
    public static FocusCategory guess(String description) {
        String d = description == null ? "" : description.toLowerCase(Locale.ROOT);
        if (containsAny(d, SUMMON_KEYWORDS)) {
            return FocusCategory.SUMMON;
        }
        if (containsAny(d, OTHER_KEYWORDS)) {
            return FocusCategory.OTHER;
        }
        if (containsAny(d, DEFENSE_KEYWORDS)) {
            return FocusCategory.DEFENSE;
        }
        if (containsAny(d, ATTACK_KEYWORDS)) {
            return FocusCategory.ATTACK;
        }
        return FocusCategory.OTHER;
    }

    /** 启发式默认评分（attack, survival 两维） */
    public static double[] guessScores(FocusCategory category) {
        return switch (category) {
            case ATTACK -> new double[]{6.5, 4.5};
            case SUMMON -> new double[]{4.5, 6.5};
            case DEFENSE -> new double[]{4.0, 7.0};
            default -> new double[]{4.0, 5.0};
        };
    }

    private static boolean containsAny(String text, String[] keywords) {
        for (String k : keywords) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }
}
