/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.combat;

import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.config.TunerCommonConfig;
import com.tiaolvshi.goetytuner.entity.TunerBoss;
import com.tiaolvshi.goetytuner.ritual.WandUpgradeEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 【0.0.20 新增】**「法术伤害百分比加成」的唯一实现**。
 *
 * <h2>它解决的问题</h2>
 * 用户 0.0.20 要求"保证实际生效和文字描述相同" —— 本轮之前本模组有**两处**
 * 在文档 / tooltip 里承诺了百分比法术加成、实际上却**从未生效**的东西：
 * <table border="1">
 *   <tr><th>位置</th><th>文字描述</th><th>修复前</th></tr>
 *   <tr><td>玩家的「调律:巫法加成」（{@code wand_upgrade.wandWitchcraftBonus}，默认 0.10）</td>
 *       <td>tooltip：<code>调律:巫法加成 +10%</code></td>
 *       <td>挂 {@code SPELL_POTENCY} 的 {@code MULTIPLY_TOTAL} ⇒ <b>恒等于 0</b></td></tr>
 *   <tr><td>Boss 二阶段的「力量等级 × 0.1」（等级 2/5）</td>
 *       <td>配置注释："等级2=20%、等级5=50% 法术伤害"</td>
 *       <td>同上 ⇒ <b>恒等于 0</b></td></tr>
 * </table>
 * **根因**（反编译实证，见 {@link BuffSpellPower} 的类注释）：
 * {@code goety:spell_potency} 的注册基础值是 <b>0.0</b>、读取入口是 {@code (int)} 截断、
 * 法术把它当**平铺伤害点数**用 ⇒ 任何 {@code MULTIPLY_TOTAL} 都是 {@code 0.0 × (1+x) = 0.0}。
 * <p>⇒ **百分比必须走伤害事件**（本类），**点数走 {@code SPELL_POTENCY}**（{@link BuffSpellPower}）。
 * 两条通道职责分明、互不重叠。
 *
 * <h2>「法术伤害」怎么判定（本轮同时修宽的第二个问题）</h2>
 * 修复前用的是 {@code forge:is_magic} 标签 + 原版 {@code magic}/{@code indirectMagic}，
 * 而该标签**只标了 Goety 39 个伤害类型里的 8 个**
 * （phobia / ice_bouquet / acid / spike / magic_bolt / wind_blast / soul_leech / life_leech）
 * ⇒ Boss 的主力法术（腐化光束、火球、魔法火、雷霆、冰冻、霜呼…）**全都不在里面**
 * ⇒ 就算通道修对了，加成的覆盖面依然是错的。
 * <p>现在的判定（三者取并集）：
 * <ol>
 *   <li>{@code forge:is_magic} 标签（数据驱动，可被整合包扩展）；</li>
 *   <li>原版 {@code magic} / {@code indirectMagic}（瞬间伤害、喷溅药水等）；</li>
 *   <li><b>{@code goety} 命名空间下的任意伤害类型</b> —— Goety 的 39 个
 *       {@code data/goety/damage_type/*.json} 全是法术产物（含腐化光束用的
 *       {@code magic_fire}、{@code magic_fireball}、{@code hellfire}、{@code shock} 等），
 *       用命名空间判定既**数据驱动**又**不写死法术清单**。</li>
 * </ol>
 * ⚠️ 反过来说：**其他模组**的法术若既不在 {@code forge:is_magic} 里、也不是原版
 * {@code magic}/{@code indirectMagic}，就不会被本加成覆盖（宁可漏、不可错伤近战）。
 *
 * <h2>加成从哪来（{@link #bonusOf}）</h2>
 * <ul>
 *   <li><b>玩家</b>：主手 + 副手两把杖的「巫法加成」与「魔法伤害加成」**相加**
 *       ⇒ 总倍率 {@code 1 + 巫法 + 魔法}（一把满配杖 = ×1.50）。
 *       两者相加而不是相乘，是为了不让同一把杖的两条加成互相放大。</li>
 *   <li><b>调律师 Boss</b>：自身二阶段的「力量」等级 × 0.1
 *       （见 {@code TunerBoss#phase2SpellDamageBonus}，等级 2/5 ⇒ +20%/+50%），
 *       外加原有的 {@code wand_upgrade.wandBonusAppliesToBoss}（默认 false）
 *       所控制的"手持升级杖也吃魔法伤害加成"。</li>
 * </ul>
 *
 * <h2>⚠️ 不放大自伤</h2>
 * {@code src.getEntity() == event.getEntity()}（攻击者就是受害者）时**直接放行**：
 * 例如「索命聚晶」会对施法者反噬"目标当前生命值的 125%"，若被自己的加成再放大，
 * 就变成"加成越高、自杀越快" —— 那不是任何文字描述承诺过的行为。
 * （修复前的 {@code magic_damage_bonus} 没有这条保护，属于顺带修掉的隐患。）
 */
@Mod.EventBusSubscriber(modid = GoetyTuner.MOD_ID)
public final class SpellDamageBonus {

    /** 原版与 Goety 都往这里标"法术伤害"（数据驱动，整合包可扩展）。 */
    private static final TagKey<DamageType> FORGE_IS_MAGIC =
            TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("forge", "is_magic"));

    /** Goety 自己的伤害类型命名空间：它的 39 个 damage_type 全部由法术产生。 */
    private static final String GOETY_NAMESPACE = "goety";

    private SpellDamageBonus() {
    }

    // ================= 判定 =================

    /** 这条伤害算不算"法术伤害"（见类注释的三条并集）。 */
    public static boolean isSpellDamage(DamageSource src) {
        if (src.is(FORGE_IS_MAGIC)) {
            return true;
        }
        String msgId = src.getMsgId();
        if (msgId.equals("magic") || msgId.equals("indirectMagic")) {
            return true;
        }
        ResourceLocation typeId = src.typeHolder().unwrapKey()
                .map(ResourceKey::location).orElse(null);
        return typeId != null && GOETY_NAMESPACE.equals(typeId.getNamespace());
    }

    // ================= 加成总额 =================

    /** 攻击者当前的法术伤害加成（**小数**：{@code 0.10} = +10%；没有加成 ⇒ 0）。 */
    public static double bonusOf(LivingEntity attacker) {
        double bonus = 0.0D;
        if (attacker instanceof Player player) {
            // 双持两把杖叠加（与 0.0.18 起的原行为一致：主手 + 副手）
            bonus += WandUpgradeEvents.witchcraftBonus(player.getMainHandItem());
            bonus += WandUpgradeEvents.witchcraftBonus(player.getOffhandItem());
            bonus += WandUpgradeEvents.magicDamageBonus(player.getMainHandItem());
            bonus += WandUpgradeEvents.magicDamageBonus(player.getOffhandItem());
        } else if (attacker instanceof TunerBoss boss) {
            // 手持升级杖是否也吃魔法伤害加成（默认 false，防自伤/友伤放大）
            if (TunerCommonConfig.WAND_BONUS_APPLIES_TO_BOSS.get()) {
                bonus += WandUpgradeEvents.magicDamageBonus(boss.getMainHandItem());
            }
            // 二阶段「力量等级 × 0.1」——文字描述承诺的那一条，现在真的生效
            bonus += boss.phase2SpellDamageBonus();
        }
        return bonus;
    }

    // ================= 应用 =================

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof LivingEntity attacker)) {
            return; // 无主伤害（环境 / 未指定来源）
        }
        if (attacker == event.getEntity()) {
            return; // 不放大自伤（见类注释）
        }
        if (!isSpellDamage(src)) {
            return;
        }
        double bonus = bonusOf(attacker);
        if (bonus > 0.0D) {
            event.setAmount((float) (event.getAmount() * (1.0D + bonus)));
        }
    }
}
