/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.focus;

import com.Polarice3.Goety.api.magic.ISpell;
import com.Polarice3.Goety.api.magic.SpellType;
import com.Polarice3.Goety.common.magic.SpellStat;
import com.Polarice3.Goety.utils.ColorUtil;
import com.tiaolvshi.goetytuner.combat.AccentRipple;
import com.tiaolvshi.goetytuner.config.TunerCommonConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;

/**
 * 【0.0.18】「调律波纹聚晶」的法术：释放一次**铺垫期的重音涟漪**。
 *
 * <p>数值（用户指定）：
 * <table border="1">
 *   <tr><th>项</th><th>值</th><th>Goety 对应</th></tr>
 *   <tr><td>灵魂能量消耗</td><td><b>5</b></td><td>{@link #defaultSoulCost()}</td></tr>
 *   <tr><td>蓄力时长</td><td><b>0.2 秒 = 4 tick</b></td><td>{@link #defaultCastDuration()}</td></tr>
 *   <tr><td>冷却</td><td><b>0.5 秒 = 10 tick</b></td><td>{@link #defaultSpellCooldown()}</td></tr>
 * </table>
 *
 * <p>⚠️ 这三个值是**基础值**，Goety 会在其上叠施法者自己的修正：
 * <ul>
 *   <li>耗蓝：{@code SoulCalculation} 会乘 SoulDiscount（巫法长袍等）与
 *       环境加成（生物群系/维度），装备齐全时实际低于 5；</li>
 *   <li>蓄力：{@code castDuration} 会乘 {@code ModAttributes.getCastingSpeed}，
 *       并受"减半施法时间"的饰品影响；</li>
 *   <li>冷却：{@code spellCooldown} 会乘 {@code ModAttributes.getCooldownDiscount}。</li>
 * </ul>
 * 这是 Goety 所有聚晶的统一规则（不是本聚晶特有），故不去覆写绕过。
 *
 * <p>效果 = {@link AccentRipple} 的四件事（击退 / 粒子 / 声波涟漪 / 提示音），
 * 参数取**铺垫期**的值：力量 {@code music.accentKnockbackBase}（默认 0.4）、音调 0.9，
 * 与 {@code TunerBoss#onAccent} 的 BUILDUP 分支完全一致（同一处实现）。
 *
 * <p><b>唯一的"改良"</b>：击退会额外豁免**施法者自己人**（自己 / 自己的宠物 / 自己的仆从，见
 * {@link AccentRipple#isFriendlyTo}）。Boss 放重音时无此必要（它没有友军），
 * 而玩家在仆从堆里放这一发时，把自己的召唤物轰飞纯属负体验。
 *
 * <p>与聚晶池的关系：本聚晶**默认写进 {@code focus.blacklist}**（见 {@code ModItems} 的说明），
 * 所以调律师 Boss 不会在战斗里抽到它、更不会自己给自己来一发击退。
 */
public class RippleSpell implements ISpell {

    /** 灵魂能量消耗（用户指定 5）。 */
    public static final int SOUL_COST = 5;
    /** 蓄力时长（tick）：0.2 秒。 */
    public static final int CAST_DURATION_TICKS = 4;
    /** 冷却（tick）：0.5 秒。 */
    public static final int COOLDOWN_TICKS = 10;
    /** 提示音调：与铺垫期重音一致（{@code TunerBoss#onAccent} 的 default 分支）。 */
    public static final float ACCENT_PITCH = 0.9F;

    @Override
    public int defaultSoulCost() {
        return SOUL_COST;
    }

    @Override
    public int defaultCastDuration() {
        return CAST_DURATION_TICKS;
    }

    @Override
    public int defaultSpellCooldown() {
        return COOLDOWN_TICKS;
    }

    /**
     * {@link SpellType#NONE}：通用系别。
     *
     * <p>Goety 的 {@code dark_wand} 默认就是 NONE（接受所有聚晶），且 {@code DarkWand} 的施法路径
     * 并不校验聚晶系别，所以任何法杖都能装它——与其它"通用"聚晶（如 utility 类）行为一致。
     */
    @Override
    public SpellType getSpellType() {
        return SpellType.NONE;
    }

    /** 不接受任何附魔（与其他工具类聚晶一致，避免附魔槽里出现无意义的组合）。 */
    @Override
    public List<Enchantment> acceptedEnchantments() {
        return List.of();
    }

    /** 蓄力期间的声音：紫水晶轻响（比默认的 EVOKER_CAST_SPELL 更贴"调律"）。 */
    @Override
    public SoundEvent CastingSound(LivingEntity caster) {
        return SoundEvents.AMETHYST_BLOCK_CHIME;
    }

    @Override
    public float castingVolume() {
        return 0.6F;
    }

    @Override
    public float castingPitch() {
        return 1.35F; // 蓄力时音调偏高，释放时落到 0.9，形成"下行"听感
    }

    /** 施法粒子颜色（法杖周围的 ENTITY_EFFECT 粒子）——调律师的紫。 */
    @Override
    public ColorUtil particleColors(LivingEntity caster) {
        return new ColorUtil(0.62F, 0.44F, 0.94F);
    }

    /**
     * 结算：释放一次铺垫期的重音涟漪。
     *
     * <p>作用点 = 施法者脚下（与 Boss 的重音一致，涟漪锚定在该世界坐标上播完）。
     */
    @Override
    public void SpellResult(ServerLevel level, LivingEntity caster, ItemStack staff, SpellStat spellStat) {
        double strength = TunerCommonConfig.ACCENT_KNOCKBACK_BASE.get();
        AccentRipple.knockback(level, caster, strength, e -> AccentRipple.isFriendlyTo(caster, e));
        AccentRipple.particles(level, caster);
        AccentRipple.wave(level, caster);
        AccentRipple.chime(level, caster, ACCENT_PITCH);
    }
}
