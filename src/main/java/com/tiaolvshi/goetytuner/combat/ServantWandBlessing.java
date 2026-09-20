/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.combat;

import com.Polarice3.Goety.common.effects.GoetyEffects;
import com.tiaolvshi.goetytuner.config.TunerCommonConfig;
import com.tiaolvshi.goetytuner.ritual.WandUpgradeEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 【0.0.20 新增】「召唤用**法杖的调律·巫法加成**越高，调律师仆从越强」的**唯一实现**。
 *
 * <p>用户需求原话：
 * <blockquote>
 * 法杖的「调律·魔法伤害加成」越高，调律师仆从的强度就越高，体现在：<br>
 * ① 根据加成的多少，持续性地给予自身 n 级**强健**效果（10 时为强健 1，每 20 加一级）；<br>
 * ② 加成高于 20 时持续性给予自身**生命恢复 1**，高于 60 时改为**生命恢复 2**，
 * 高于 80 时改为**生命恢复 2 + 抗性提升 1**，高于 100 时改为**生命恢复 2 + 抗性提升 2**（到达上限）。
 * </blockquote>
 *
 * <h2>⚠️ 0.0.20 修正：判定来源由「魔法伤害加成」改为「巫法加成」</h2>
 * 用户实测后的反馈："我发现按照调律·魔法伤害加成的计数规则**有点太容易触发**了，
 * 改为按照调律·巫法加成来判定，**数值不变**。"
 * <p>原因一目了然 —— 两个加成的**单次默认值差 4 倍**（{@code [wand_upgrade]} 段）：
 * <table border="1">
 *   <tr><th>加成</th><th>每次击败 Boss 的默认增量</th><th>打一次就到的档位</th></tr>
 *   <tr><td>调律:魔法伤害加成</td><td><b>+40%</b></td><td>强健 Ⅱ + 生命恢复 Ⅰ（几乎立刻满配）</td></tr>
 *   <tr><td>调律:巫法加成（<b>现在用它</b>）</td><td><b>+10%</b></td><td>强健 Ⅰ（真正需要反复击杀才爬上去）</td></tr>
 * </table>
 * ⇒ 阈值（10 / 20 / 60 / 80 / 100）**一个都没改**，只换了读哪个 NBT 键。
 * 按巫法加成 +10%/次 的爬升曲线：
 *
 * <table border="1">
 *   <tr><th>击杀次数</th><th>巫法加成</th><th>强健</th><th>生命恢复</th><th>抗性提升</th></tr>
 *   <tr><td>0</td><td>+0%</td><td>—</td><td>—</td><td>—</td></tr>
 *   <tr><td>1</td><td>+10%</td><td>Ⅰ</td><td>—</td><td>—</td></tr>
 *   <tr><td>3</td><td>+30%</td><td>Ⅱ</td><td>Ⅰ</td><td>—</td></tr>
 *   <tr><td>7</td><td>+70%</td><td>Ⅳ</td><td>Ⅱ</td><td>—</td></tr>
 *   <tr><td>9</td><td>+90%</td><td>Ⅴ</td><td>Ⅱ</td><td>Ⅰ</td></tr>
 *   <tr><td>11</td><td>+110%</td><td>Ⅵ+（继续升）</td><td>Ⅱ</td><td>Ⅱ（封顶）</td></tr>
 * </table>
 * （多次击败的叠加由 {@code wand_upgrade.wandBonusStack} 决定，默认 true = 加法叠加。）
 *
 * <h2>「加成」的计量单位</h2>
 * 法杖 NBT 里存的是**小数**（{@code 0.10} = +10%，见
 * {@link WandUpgradeEvents#WITCHCRAFT_KEY}），而用户说的是**百分数**（"10 时为强健 1" = +10%）。
 * 所以本类统一按 {@code pct = bonus × 100} 判定，阈值常量也就写成百分数，
 * **与 tooltip 上显示的数字完全一致**（tooltip 那行是 `调律:巫法加成 +%s%%`）。
 *
 * <h2>为什么"持续性地给予"而不是一次给完</h2>
 * 药水效果会自然倒计时、也会被牛奶/净化类效果清掉。所以本类是**低频自愈式**刷新：
 * {@value #REFRESH_TICKS} tick（1 秒）重挂一次、每次给 {@value #DURATION_TICKS} tick（3 秒）的时长
 * ⇒ 玩家中途清掉效果，最多 1 秒后就自动回来，而**平时每秒最多 3 次 {@code addEffect}
 * 且数值未变时会被 {@link #apply} 提前跳过**（不产生无谓的网络同步）。
 *
 * <h2>药水机制与本体独立（用户要求）</h2>
 * 用户明确："尽管调律师有药水效果相关的免疫和给予机制，但调律师仆从的药水机制和调律师的独立。"
 * ⇒ 本类**只服务仆从**，不碰 {@code TunerBoss} 的任何效果逻辑：
 * <ul>
 *   <li>不继承 Boss 的二阶段增益免疫（{@code phase2_buffs.phase2EffectImmunity}）、低谷的
 *       SAPPED / DARKNESS、锁血期的任何效果；</li>
 *   <li>不受 {@code [boss]} 段任何键影响（唯一的开关是本模组自己的
 *       {@code servant.wandBlessingEnabled}）；</li>
 *   <li>仆从自己的免疫规则仍然只有它自己的那一条（拒绝 Goety 的召唤冷却
 *       {@code SUMMON_DOWN}，见 {@code TunerServant#canBeAffected}）——
 *       本类的增益走的是 {@code LivingEntity#addEffect}，天然也要过那道判定，
 *       而它只拒绝 SUMMON_DOWN，所以不被自己挡住。</li>
 * </ul>
 *
 * <h2>谁能吃到</h2>
 * 只有**仪式召唤**出来的仆从才有"召唤用杖"（{@code TunerServant#getSummonWand()}）。
 * 刷怪蛋 / {@code /summon} 出来的仆从没有杖 ⇒ 得不到任何增益（这是有意的：
 * 用户的设计是"用带调律加成的法杖召唤出的仆从才强"）。
 */public final class ServantWandBlessing {

    // ---- 强健阈值（百分数） ----
    /** 强健的起始门槛：加成 ≥ 10% 才给 1 级。 */
    public static final double BUFF_START_PCT = 10.0D;
    /** 强健每多少个百分点加一级。 */
    public static final double BUFF_STEP_PCT = 20.0D;

    // ---- 生命恢复 / 抗性提升阈值（百分数） ----
    /** 高于此值给生命恢复 1。 */
    public static final double REGEN_1_ABOVE_PCT = 20.0D;
    /** 高于此值改为生命恢复 2。 */
    public static final double REGEN_2_ABOVE_PCT = 60.0D;
    /** 高于此值额外给抗性提升 1。 */
    public static final double RESISTANCE_1_ABOVE_PCT = 80.0D;
    /** 高于此值改为抗性提升 2（到顶）。 */
    public static final double RESISTANCE_2_ABOVE_PCT = 100.0D;

    /** 刷新周期（tick）：1 秒一次。 */
    public static final int REFRESH_TICKS = 20;
    /** 每次挂的时长（tick）：3 秒，足以跨过 3 个刷新周期。 */
    public static final int DURATION_TICKS = 60;

    private ServantWandBlessing() {
    }

    // ================= 数值映射（纯函数，便于单测 / 复核） =================

    /**
     * 强健等级（0 = 不给）。
     *
     * <p>{@code pct >= 10 ⇒ 1 + floor((pct - 10) / 20)}：10→1、30→2、50→3、70→4、90→5、110→6…
     * **不封顶**（用户："越高越高"）。
     */
    public static int buffLevel(double pct) {
        if (!(pct >= BUFF_START_PCT)) { // 写法兼顾 NaN
            return 0;
        }
        return 1 + (int) Math.floor((pct - BUFF_START_PCT) / BUFF_STEP_PCT);
    }

    /** 生命恢复等级（0 = 不给）：&gt;20 → 1、&gt;60 → 2、&gt;100 仍为 2（上限）。 */
    public static int regenerationLevel(double pct) {
        if (pct > REGEN_2_ABOVE_PCT) {
            return 2;
        }
        return pct > REGEN_1_ABOVE_PCT ? 1 : 0;
    }

    /** 抗性提升等级（0 = 不给）：&gt;80 → 1、&gt;100 → 2（上限）。 */
    public static int resistanceLevel(double pct) {
        if (pct > RESISTANCE_2_ABOVE_PCT) {
            return 2;
        }
        return pct > RESISTANCE_1_ABOVE_PCT ? 1 : 0;
    }

    /** 从召唤用杖读出**巫法加成**的**百分数**（没有杖 / 没有加成 ⇒ 0）。 */
    public static double bonusPctOf(ItemStack summonWand) {
        return WandUpgradeEvents.witchcraftBonus(summonWand) * 100.0D;
    }

    // ================= 刷新 =================

    /**
     * 每 tick 由 {@code TunerServant#aiStep} 调用；本方法自己按 {@link #REFRESH_TICKS} 降频。
     *
     * @param servant    要加持的仆从
     * @param summonWand 它的召唤用杖（可能为空 ⇒ 什么都不做）
     */
    public static void tick(LivingEntity servant, ItemStack summonWand) {
        if (!TunerCommonConfig.SERVANT_WAND_BLESSING_ENABLED.get()) {
            return;
        }
        if (servant.tickCount % REFRESH_TICKS != 0) {
            return;
        }
        double pct = bonusPctOf(summonWand);
        apply(servant, GoetyEffects.BUFF.get(), buffLevel(pct));
        apply(servant, MobEffects.REGENERATION, regenerationLevel(pct));
        apply(servant, MobEffects.DAMAGE_RESISTANCE, resistanceLevel(pct));
    }

    /**
     * 挂/续一个效果。**数值未变且剩余时长还够时直接跳过**（避免每秒重复同步）。
     *
     * <p>{@code level <= 0} 表示按表不该给 —— 这里**不去主动移除**已有的效果：
     * 效果本来就会自然到期，而且"杖被换掉"在本模组里不可能发生
     * （召唤用杖存在仆从自己的 NBT 里、不随装备变化）。
     */
    private static void apply(LivingEntity servant, MobEffect effect, int level) {
        if (level <= 0) {
            return;
        }
        int amplifier = level - 1;
        MobEffectInstance current = servant.getEffect(effect);
        if (current != null && current.getAmplifier() == amplifier
                && current.getDuration() > DURATION_TICKS / 2) {
            return; // 已经是目标档位且不会很快过期
        }
        // ambient=false（正常粒子）、visible=true、showIcon=true：让玩家一眼看出"这只是被强化过的"
        servant.addEffect(new MobEffectInstance(effect, DURATION_TICKS, amplifier, false, true, true));
    }
}
