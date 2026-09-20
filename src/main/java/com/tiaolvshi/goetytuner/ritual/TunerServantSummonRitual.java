/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.ritual;

import com.Polarice3.Goety.api.items.magic.IWand;
import com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity;
import com.Polarice3.Goety.common.crafting.RitualRecipe;
import com.Polarice3.Goety.common.ritual.SummonRitual;
import com.tiaolvshi.goetytuner.entity.TunerServant;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 【0.0.20 新增】**调律师仆从的仪式召唤**（用户需求）。
 *
 * <p>用户原话：
 * <blockquote>
 * 调律师仆从的召唤：每秒 10 能量，10 秒，魔法仪式；2 个紫水晶、2 个紫水晶、1 个红石、1 个钻石、
 * 1 个金锭、1 个青金石；中心放上一把「有<b>调律加成</b>」的法杖即可召唤。
 * </blockquote>
 *
 * <p>配方本体在 {@code data/goety/recipes/tuner_servant_ritual.json}，
 * 工厂注册在 {@link ModRituals#TUNER_SERVANT_SUMMON}。
 *
 * <h2>① 为什么"中心放法杖"能生效（与 Boss 仪式同一条链）</h2>
 * Goety 的黑暗祭坛流程：玩家把催化物放在**祭坛中心**，右键祭坛 ⇒
 * {@code DarkAltarBlockEntity.activate()} → 遍历配方 → {@code RitualRecipe.matches()} →
 * {@code Ritual.identify(level, pos, player, stack)}，这里的 {@code stack} 就是**祭坛中心的物品**；
 * 仪式结束时 {@code SummonRitual.finish} 会 {@code shrink(1)} 掉它（**法杖被消耗**）。
 * 本模组 0.0.18 的 Boss 召唤就是这条链，这里完全照搬。
 *
 * <h2>② 激活条件必须是"带调律加成的法杖"</h2>
 * {@code activation_item} 只能写物品 / 标签，**表达不了 NBT 条件**，所以本类完全重写
 * {@link #identify}：要求中心物品是 {@link IWand} **且**它的
 * {@code goetytuner.witchcraft_bonus} 或 {@code goetytuner.magic_damage_bonus} **任一 > 0**
 * （也就是 tooltip 上有「调律:巫法加成」或「调律:魔法伤害加成」那一行）。
 * <p>不满足时返回 false ⇒ Goety 会给出它自己的提示（"无效的仪式" / "物品不是仪式催化剂"），
 * 不需要我们再发明一套文案。
 *
 * <h2>③ tame = true：仪式召唤的仆从是**玩家自己的**</h2>
 * {@code SummonRitual(recipe, tame, noVariant)} 的 {@code tame} 最终走到
 * {@code Ritual.prepareLivingEntityForSpawn(..., tame)} → {@code MobUtil.summonTame(entity, player)}
 * （字节码实证：偏移 0 处即 {@code iload 6; ifeq} 分支跳 {@code MobUtil.summonTame}）
 * ⇒ 仆从**认主**（跟随、可被聚晶指令指挥）。
 * 这与刷怪蛋的"直接放 = 野生"是两条不同的获取路径，互不影响。
 *
 * <h2>④ 召唤用杖要留下来（做两件事）</h2>
 * 杖会被 {@code finish} 消耗掉，所以必须在 {@code super.finish} **之前**把它复制出来
 * （{@link #finish}），然后交给仆从：
 * <ul>
 *   <li>{@code TunerServant#setSummonWand} —— **强度依据**（
 *       {@link com.tiaolvshi.goetytuner.combat.ServantWandBlessing} 读它的
 *       {@code magic_damage_bonus} 决定强健 / 生命恢复 / 抗性提升档位）；</li>
 *   <li>仆从死亡时**原样掉落**（用户："掉落召唤用的法杖。（并不会有加成，原样返回）"）
 *       —— 由 {@link WandUpgradeEvents#onLivingDrops} 处理，
 *       ⚠️ 与 Boss 的掉落**不同**：Boss 是"快照 + 叠加调律加成"，
 *       仆从是"快照，不加不减"。</li>
 * </ul>
 * ⚠️ 仆从**主手**仍然是 AI 施法用的 {@code goetytuner:tuner_wand}
 * （{@code CastChannel} 要求主手是 IWand），召唤用杖只存在 NBT 里、**不装到手上**。
 */
public class TunerServantSummonRitual extends SummonRitual {

    /** 祭坛中心那把杖的快照（在 {@code super.finish} 把它 shrink 掉之前取）。 */
    private ItemStack wandCopy = ItemStack.EMPTY;

    public TunerServantSummonRitual(RitualRecipe recipe) {
        // tame = true（认主）；noVariant = false（本模组的仆从没有变体）
        super(recipe, true, false);
    }

    /**
     * 该物品能否激活本仪式：**必须是一把带"调律加成"的法杖**。
     *
     * <h2>0.0.20 修正：两种调律加成**任一**大于 0 都算数</h2>
     * 用户的规格是"中心放上一把「**有调律加成**」的法杖" —— 是一个**定性**要求（"这杖被调过律"），
     * 而不是"有某一个特定加成"。而 {@code applyWandUpgrade} 每次都是**两个加成一起写**的，
     * 所以正常情况下两者同增同减、判定其中一个就够了。
     * <p>但两种加成各自可以**单独**被配置成 0（{@code wand_upgrade.wandWitchcraftBonus}
     * / {@code wandMagicDamageBonus} 都是 0~10 的可调值）⇒ 只认其中一个会造出
     * "这杖明明被调过律、却召唤不了"的死角。所以这里取**并集**：
     * 只要 {@link WandUpgradeEvents#witchcraftBonus} 或
     * {@link WandUpgradeEvents#magicDamageBonus} **任一 > 0** 就认。
     * <p>⚠️ 与**强度判定**的区别：仆从的增益档位**只看巫法加成**
     * （{@link com.tiaolvshi.goetytuner.combat.ServantWandBlessing}，用户 0.0.20 明确要求）。
     * 所以极端配置下（只给魔法伤害加成、巫法加成为 0）能召唤成功、但仆从拿不到增益 ——
     * 这是"仪式看有没有调过律 / 强度看巫法加成多少"的分工，**不是 bug**。
     *
     * <p>注意这里**不调用 {@code super.identify}** —— 父类只看
     * {@code recipe.getActivationItem().test(stack)}（一个物品标签），
     * 而我们要的是 NBT 条件；基座材料（8 件）仍然照父类的方式校验。
     */
    public static boolean isTunedWand(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof IWand)) {
            return false;
        }
        return WandUpgradeEvents.witchcraftBonus(stack) > 0.0D
                || WandUpgradeEvents.magicDamageBonus(stack) > 0.0D;
    }

    @Override
    public boolean identify(Level level, BlockPos pos, Player player, ItemStack stack) {
        if (!isTunedWand(stack)) {
            return false; // 中心不是"有调律加成的法杖"⇒ 仪式不成立
        }
        return areAdditionalIngredientsFulfilled(level, pos, player, this.recipe.getIngredients());
    }

    @Override
    public void finish(Level level, BlockPos pos, DarkAltarBlockEntity altar,
                       Player player, ItemStack stack) {
        // ⚠️ 时序：父类 finish 会对祭坛 slot0 执行 shrink(1)（法杖被消耗），
        // 所以必须在此之前把完整副本取出来（与 TunerSummonRitual 同一条实证结论）。
        ItemStack slot0 = altar.itemStackHandler
                .orElseThrow(() -> new IllegalStateException("Dark altar item handler missing"))
                .getStackInSlot(0);
        if (!slot0.isEmpty()) {
            this.wandCopy = slot0.copy();
        }
        super.finish(level, pos, altar, player, stack);
    }

    @Override
    public void initSummoned(LivingEntity living, Level level, BlockPos pos,
                             DarkAltarBlockEntity altar, Player player) {
        super.initSummoned(living, level, pos, altar, player);
        if (living instanceof TunerServant servant && !wandCopy.isEmpty()) {
            servant.setSummonWand(wandCopy); // 强度依据 + 死亡掉落，都读这一份
            wandCopy = ItemStack.EMPTY;
        }
    }
}
