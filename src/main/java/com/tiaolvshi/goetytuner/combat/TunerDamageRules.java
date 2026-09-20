/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.combat;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * 【0.0.19】「调律师系」实体共用的**减伤规则**（Boss 与调律师仆从各持一份实例）。
 *
 * <p>起因（用户要求）：调律师仆从的「血量 / 护甲 / 减伤限伤机制要和本体保持一致」。
 * 「一致」的正确做法是**共用同一份实现**，而不是把 Boss 的那几段抄到仆从里
 * （本项目红线：同一职责只允许一处实现）——所以 0.0.19 把 Boss 原先内联的
 * 三项判定抽到本类，`TunerBoss` 与 `TunerServant` **都**调这里。
 *
 * <p><b>刻意不共享的东西</b>（仆从**没有**、也不该有）：
 * <ul>
 *   <li><b>锁血阶梯</b>（`applyLockHealth` / `lockMark` / 宽限期免疫 / 致死截断）——
 *       那是 Boss 的招牌机制，仆从若也锁血就成了打不死的怪；</li>
 *   <li>`/kill` 后门与索命后门——两者都是"给锁血体系开后门"的补丁，没有锁血就没有它们的意义。</li>
 * </ul>
 *
 * <p>因此本类只覆盖**与锁血无关**的通用规则：身份免疫、近战易伤、限伤 / 限DPS。
 */
public final class TunerDamageRules {

    private TunerDamageRules() {
    }

    /**
     * 【第二十三轮】「调律师身份」伤害免疫：摔落 / 原版火焰（含岩浆/燃烧，即 {@code IS_FIRE} 全系）/
     * 窒息（卡墙） / 溺水。
     *
     * <p>不免疫魔法、爆炸、普通攻击等玩家可造成、可操作的伤害类型——
     * 这三类豁免只是不让 Boss（以及它的仆从）被环境莫名其妙地磨死。
     */
    public static boolean isIdentityImmune(DamageSource source) {
        return source.is(DamageTypes.FALL)
                || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.DROWN);
    }

    /**
     * 【第二十九轮】判定伤害是否为「直接近战物理攻击」：
     * 原版类型 {@code player_attack} / {@code mob_attack}（手持武器挥击与横扫；1.20.1 无
     * {@code mob_attack_no_cooldown}，通用兜底已覆盖该情况）；
     * 投射物与爆炸不算；其余情况要求直接伤害实体就是来源实体（贴身接触）。
     */
    public static boolean isDirectMelee(DamageSource source) {
        if (source.is(DamageTypes.PLAYER_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK)) {
            return true;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        return source.getDirectEntity() instanceof LivingEntity
                && source.getDirectEntity() == source.getEntity();
    }

    /**
     * 近战易伤：直接近身物理攻击的伤害 ×(1 + {@code boss.meleeVulnerability})。
     *
     * <p>默认 +25%，让贴身近战成为有效输出手段；投射物/爆炸/魔法等远程手段不受加成。
     * ⚠️ 这是**增伤**而不是减伤，但它属于"本体承受伤害的规则"的一部分，
     * 仆从按"与本体一致"的要求同样适用（见类注释）。
     */
    public static float applyMeleeVulnerability(DamageSource source, float amount,
                                               double vulnerability) {
        if (vulnerability <= 0.0D || !isDirectMelee(source)) {
            return amount;
        }
        return amount * (float) (1.0D + vulnerability);
    }
}
