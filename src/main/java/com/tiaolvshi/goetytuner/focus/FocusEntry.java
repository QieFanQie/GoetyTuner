/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.focus;

import com.Polarice3.Goety.api.items.magic.IFocus;
import com.Polarice3.Goety.api.magic.ISpell;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 单个聚晶的运行时条目。
 *
 * 静态部分（跨存档、来自 focus_classification.json）：
 *   - category 分类
 *   - attackScore  攻击评分（攻击聚晶：1维；召唤聚晶：输出能力维）
 *   - survivalScore 召唤聚晶生存能力维（仅召唤类使用）
 *
 * 动态部分（每场战斗内、跟随boss实例）：
 *   - dynamicAttackOffset / dynamicSurvivalOffset  在初始配置上加减，不直接改静态评分
 *
 * 使用评分（轮盘赌权重）见 {@link #rouletteWeight}：
 *   攻击：|静态攻击分 + 动态偏移| + 保底基数
 *   召唤：按“场上召唤物不足→偏生存 / 充足→偏输出”的加权公式
 *   防御/其他：仅保底基数（均匀随机）
 */
public class FocusEntry {

    private final ResourceLocation itemId;
    private final IFocus focusItem;
    private final ISpell spell;

    private FocusCategory category;
    private double attackScore;    // 0~10
    private double survivalScore;  // 0~10，仅召唤类

    private double dynamicAttackOffset = 0.0;
    private double dynamicSurvivalOffset = 0.0;

    public FocusEntry(ResourceLocation itemId, IFocus focusItem) {
        this.itemId = itemId;
        this.focusItem = focusItem;
        this.spell = focusItem.getSpell();
    }

    // ---- 基础信息 ----

    public ResourceLocation getItemId() {
        return itemId;
    }

    public IFocus getFocusItem() {
        return focusItem;
    }

    public ISpell getSpell() {
        return spell;
    }

    public ItemStack createFocusStack() {
        return new ItemStack((Item) focusItem);
    }

    // ---- 静态分类与评分 ----

    public FocusCategory getCategory() {
        return category;
    }

    public void setCategory(FocusCategory category) {
        this.category = category;
    }

    public double getAttackScore() {
        return attackScore;
    }

    public void setAttackScore(double attackScore) {
        this.attackScore = attackScore;
    }

    public double getSurvivalScore() {
        return survivalScore;
    }

    public void setSurvivalScore(double survivalScore) {
        this.survivalScore = survivalScore;
    }

    // ---- 动态偏移 ----

    public double getEffectiveAttackScore() {
        return attackScore + dynamicAttackOffset;
    }

    public double getEffectiveSurvivalScore() {
        return survivalScore + dynamicSurvivalOffset;
    }

    public void addDynamicAttackOffset(double delta, double cap) {
        this.dynamicAttackOffset = clamp(this.dynamicAttackOffset + delta, -cap, cap);
    }

    public void addDynamicSurvivalOffset(double delta, double cap) {
        this.dynamicSurvivalOffset = clamp(this.dynamicSurvivalOffset + delta, -cap, cap);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---- 轮盘赌权重 ----

    /**
     * 轮盘赌权重：|静态评分+动态偏移| + 保底基数。
     *
     * @param baseWeight      保底基数（保证最低概率占比，防复读）
     * @param summonContext   召唤上下文（可为null，仅召唤类使用）：
     *                        [0]=fillRatio 场上召唤物/上限 [1]=survivalWeight 参数1 [2]=attackWeight 参数2
     *                        若 summonBlocked=true（召唤物满员），权重直接归0
     */
    public double rouletteWeight(double baseWeight, double[] summonContext, boolean summonBlocked) {
        if (!category.isScored()) {
            return baseWeight; // 防御/其他：均匀随机
        }
        if (category == FocusCategory.ATTACK) {
            return Math.abs(getEffectiveAttackScore()) + baseWeight;
        }
        // 召唤：使用评分 = 生存分*w1 + fillRatio*输出分*w2（公式可在配置调整）
        if (summonBlocked) {
            return 0.0;
        }
        double fillRatio = summonContext != null ? summonContext[0] : 0.0;
        double w1 = summonContext != null ? summonContext[1] : 1.0;
        double w2 = summonContext != null ? summonContext[2] : 1.0;
        double useScore = getEffectiveSurvivalScore() * w1
                + fillRatio * getEffectiveAttackScore() * w2;
        return Math.abs(useScore) + baseWeight;
    }
}
