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
import com.Polarice3.Goety.init.ModAttributes;
import com.tiaolvshi.goetytuner.config.TunerCommonConfig;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 【0.0.20 新增】**「强健」等级 → 法术强度**的唯一实现。
 *
 * <p>用户需求："将强健等级换算成法术强度，**且只对调律师生效**
 * （即调律师本人根据自身的强健等级增加法术伤害）。"
 * 已与用户确认作用域 = **本模组「调律师一族」**（{@link com.tiaolvshi.goetytuner.entity.TunerBoss}
 * 与 {@link com.tiaolvshi.goetytuner.entity.TunerServant}），
 * 各自按**自身**的「强健」等级加成；**不是**一条全局规则
 * （其它模组的生物、玩家即便有强健也不会因此变强）。
 *
 * <h2>⚠️ 为什么单位是"每级 +1 点"而不是"每级 +10%"</h2>
 * 这一条是**反编译实证**出来的，也是本轮最容易做错的地方：
 * <ol>
 *   <li>{@code goety:spell_potency} 注册时是
 *       {@code new RangedAttribute("attribute.name.goety.spell_potency", 0.0D, 0.0D, 2048.0D)}
 *       ⇒ **基础值是 0.0**（不是 1.0）；</li>
 *   <li>Goety 读它的唯一入口是
 *       {@code ModAttributes.getPotency(LivingEntity)}，实现是
 *       {@code (int) livingEntity.getAttributeValue(SPELL_POTENCY)} ⇒ **返回 int**；</li>
 *   <li>法术拿的是 {@code SpellStat.getPotency()}（int），当作**平铺伤害点数**用
 *       （Goety 的伤害公式普遍是「配置基础伤害 + potency」，不是乘算）。</li>
 * </ol>
 * 两条结论：
 * <ul>
 *   <li>**百分比 modifier 在这个属性上恒等于 0** —— Minecraft 的 {@code MULTIPLY_TOTAL} 是
 *       {@code total *= 1 + amount}，基础值 0.0 乘任何数还是 0.0
 *       ⇒ 本模组里**已存在**的两处"百分比法术强度"（玩家的巫法加成、
 *       Boss 二阶段的 力量等级×0.1）**实际上都没生效**，见类尾的"已知问题"；</li>
 *   <li>因此本类用 **{@link AttributeModifier.Operation#ADDITION}**，且数值必须是
 *       **整数级别**的（{@code 0.1} 会被 {@code (int)} 截成 0 ＝ 白挂）。
 *       比例配置 {@code casting.buffSpellPowerPerLevel} 因此是 **Int**，默认 **1**
 *       （= 强健 Ⅰ 时每发法术 +1 点伤害，Ⅴ 时 +5 点）。</li>
 * </ul>
 *
 * <h2>与 Goety 原版「强健」的关系</h2>
 * Goety 的 {@code goety:buff}（中文名"强健"）本身是
 * {@code ATTACK_DAMAGE + 1/级}（语言文件："每级增加 1 点的近战攻击伤害"）。
 * ⚠️ **本模组的两个实体都没有近战手段**（调律师是"指挥家"、仆从也不装 {@code MeleeAttackGoal}）
 * ⇒ 原版那部分加成对它们**等于没有**。本类就是这个缺口在**法术侧**的补位，
 * 而且刻意采用与 Goety 相同的"**每级 +1**"节奏，读起来一致。
 *
 * <h2>实现方式（低频自愈式，与 ServantWandBlessing 同款）</h2>
 * 由两个实体每 tick 调用 {@link #tick}，内部按 {@value #REFRESH_TICKS} tick 降频；
 * **数值没变就一个字节都不写**（避免每帧碰 attribute 造成同步流量）。
 * 强健一旦消失（被净化 / 自然到期）⇒ 目标值回到 0 ⇒ **主动移除** modifier。
 *
 * <h2>✅ 相关修复（0.0.20 同一轮）</h2>
 * 上面这条"百分比恒为 0"的实证同时暴露了两处**早已存在**的死代码，已在同一轮修掉：
 * <ul>
 *   <li>玩家的「调律:巫法加成」（原 {@code WandUpgradeEvents#refreshWitchcraftModifier}）
 *       —— 那个 attribute modifier 与两个刷新监听器**已整体删除**；</li>
 *   <li>Boss 二阶段的「力量等级 × 0.1」（原 {@code TunerBoss#tickPhase2Buffs} 里的 modifier）
 *       —— 同样**已删除**，改为读取力量效果本身（{@code TunerBoss#phase2SpellDamageBonus}）。</li>
 * </ul>
 * 两者都改走 **{@link SpellDamageBonus}**（伤害事件 ×(1+加成)），
 * **文字写多少就真的生效多少**。⇒ 分工最终定为：
 * <b>百分比 → 伤害事件；点数 → SPELL_POTENCY</b>（也就是本类）。
 */
public final class BuffSpellPower {

    /** 本 modifier 的固定 UUID（`getModifier` / `removeModifier` 都靠它，防重复挂） */
    private static final UUID MODIFIER_UUID =
            UUID.nameUUIDFromBytes("goetytuner:buff_spell_power".getBytes(StandardCharsets.UTF_8));
    private static final String MODIFIER_NAME = "TunerBuffSpellPower";

    /** 刷新周期（tick）：1 秒一次。强健本身由 ServantWandBlessing 每 20 tick 续 60 tick，节奏一致。 */
    public static final int REFRESH_TICKS = 20;

    private BuffSpellPower() {
    }

    /**
     * 该实体的「强健」等级（{@code goety:buff} 的 amplifier + 1；没有该效果 ⇒ 0）。
     *
     * <p>用 {@code getEffect} 而不是 {@code getActiveEffectsMap}：
     * 只看这一个效果，O(1)，且不构造集合。
     */
    public static int buffLevelOf(LivingEntity entity) {
        MobEffectInstance instance = entity.getEffect(GoetyEffects.BUFF.get());
        return instance == null ? 0 : instance.getAmplifier() + 1;
    }

    /** 换算后的法术强度数值（= 等级 × 每级点数；等级 0 或关闭时 0）。 */
    public static double potencyFor(int buffLevel) {
        if (buffLevel <= 0) {
            return 0.0D;
        }
        int perLevel = TunerCommonConfig.BUFF_SPELL_POWER_PER_LEVEL.get();
        return perLevel <= 0 ? 0.0D : (double) buffLevel * perLevel;
    }

    /**
     * 每 tick 由实体调用；本方法自带降频与幂等短路。
     *
     * <p>**只允许**被本模组的调律师一族调用（用户要求"只对调律师生效"）。
     */
    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide || !entity.isAlive()) {
            return;
        }
        if (entity.tickCount % REFRESH_TICKS != 0) {
            return;
        }
        refresh(entity);
    }

    /** 立即重算（不降频；供 tick 与将来的指令/调试入口共用）。 */
    public static void refresh(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(ModAttributes.SPELL_POTENCY.get());
        if (attr == null) {
            // Goety 的 EntityAttributeModificationEvent 会给**所有**实体类型挂上本属性，
            // 正常不会走到这里；留着是为了将来 Goety 改动时不至于 NPE。
            return;
        }
        double want = potencyFor(buffLevelOf(entity));
        AttributeModifier existing = attr.getModifier(MODIFIER_UUID);
        if (existing != null && Math.abs(existing.getAmount() - want) < 1.0E-6D) {
            return; // 数值没变：不碰 attribute
        }
        attr.removeModifier(MODIFIER_UUID);
        if (want > 0.0D) {
            // ⚠️ 必须是 ADDITION：本属性基础值 0.0，任何 MULTIPLY_* 都会得到 0（见类注释）
            attr.addTransientModifier(new AttributeModifier(
                    MODIFIER_UUID, MODIFIER_NAME, want, AttributeModifier.Operation.ADDITION));
        }
    }
}
