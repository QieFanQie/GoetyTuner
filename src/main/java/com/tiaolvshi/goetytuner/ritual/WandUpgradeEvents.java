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
import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.config.TunerCommonConfig;
import com.tiaolvshi.goetytuner.entity.TunerBoss;
import com.tiaolvshi.goetytuner.entity.TunerServant;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 升级法杖全套机制（任务 #118）：
 *
 * <ul>
 *   <li><b>掉落</b>：仪式召唤的 Boss 死亡时掉落「原始法杖快照 + 加成 NBT」，
 *       并从掉落列表移除战斗中已被改写的法杖（快照制，见设计文档 5.1 修订）；
 *       【0.0.20】仪式召唤的**仆从**死亡时掉落「召唤用杖快照，不加不减」（见
 *       {@link #dropServantSummonWand}）；</li>
 *   <li><b>巫法加成 / 魔法伤害加成</b>：**都在
 *       {@link com.tiaolvshi.goetytuner.combat.SpellDamageBonus} 里生效**
 *       —— 玩家施法造成的法术伤害 ×(1 + 两者之和)。
 *       ⚠️ 0.0.20 修正：这两条原先一条挂 {@code SPELL_POTENCY} 的 {@code MULTIPLY_TOTAL}、
 *       一条走伤害事件，而前者在**基础值 0.0** 的属性上**恒等于 0**（从未生效）；
 *       现在两条统一走伤害事件，**tooltip 写多少就真的加多少**；</li>
 *   <li><b>紫色描述文本</b>：「调律:巫法加成 / 调律:魔法伤害加成」两条 LIGHT_PURPLE
 *       提示（避嫌前缀「调律」，数值随 NBT 实时显示）。</li>
 * </ul>
 *
 * 全部加成均为常驻型（非冷却/非施法前摇），数值与叠加开关可配置。
 */
@Mod.EventBusSubscriber(modid = GoetyTuner.MOD_ID)
public class WandUpgradeEvents {

    /** 升级法杖 NBT 根键（挂在物品 tag 下，不影响其它原版数据） */
    public static final String NBT_KEY = "goetytuner";
    public static final String WITCHCRAFT_KEY = "witchcraft_bonus";
    public static final String MAGIC_DAMAGE_KEY = "magic_damage_bonus";

    // ================= 掉落（快照制） =================

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof TunerServant servant) {
            dropServantSummonWand(event, servant);
            return;
        }
        if (!(event.getEntity() instanceof TunerBoss boss)) {
            return;
        }
        ItemStack original = boss.getOriginalWand();
        if (original.isEmpty()) {
            return; // 非仪式召唤（刷怪蛋/指令）：无快照，保持原行为
        }
        // 移除战斗中已被 Boss 改写聚晶槽的法杖（主手），避免掉出玩家原聚晶丢失版
        event.getDrops().removeIf(e -> e.getItem().getItem() instanceof IWand);

        ItemStack drop = original.copy();
        if (TunerCommonConfig.WAND_UPGRADE_ENABLED.get()) {
            applyWandUpgrade(drop);
        }
        ItemEntity item = new ItemEntity(boss.level(), boss.getX(), boss.getY() + 0.5D, boss.getZ(), drop);
        item.setDefaultPickUpDelay();
        event.getDrops().add(item);
        GoetyTuner.LOGGER.info("[Tuner] Ritual boss dropped upgraded wand: {} ({})",
                boss.getName().getString(), drop.getDisplayName().getString());
    }

    /**
     * 【0.0.20 新增】**仆从死亡时把召唤用杖原样还给玩家**（用户需求）。
     *
     * <p>用户原话："当调律师仆从死亡时，掉落召唤用的法杖。（并不会有加成，原样返回）"
     * ⇒ 与 Boss 的掉落**刻意不同**：
     * <ul>
     *   <li><b>Boss</b>：掉"原始快照 + {@link #applyWandUpgrade} 叠加调律加成"（击杀奖励）；</li>
     *   <li><b>仆从</b>：掉**召唤时那把杖的完整副本**（附魔 / 聚晶 / 已有的调律加成全部保留），
     *       **不额外加任何东西**。用户括号里的"并不会有加成"指的是"不会再被加成一次"，
     *       而不是"把原有的加成剥掉"（已与用户确认）。</li>
     * </ul>
     * 仪式结束时法杖已被祭坛 {@code shrink(1)} 消耗，所以这里掉的是
     * {@link TunerServant#getSummonWand()} 存下来的那份快照 —— 刷怪蛋 / {@code /summon}
     * 出来的仆从没有快照，**不掉任何东西**（保持原行为）。
     *
     * <p>同样先移除主手那把 {@code tuner_wand}（它是配发给 AI 的施法载体，
     * {@code setDropChance(MAINHAND, 0)} 本来就让它掉不出来；这里再兜一道，
     * 防止将来有人改掉掉落概率时白送一把杖）。
     */
    private static void dropServantSummonWand(LivingDropsEvent event, TunerServant servant) {
        ItemStack summonWand = servant.getSummonWand();
        if (summonWand.isEmpty()) {
            return;
        }
        event.getDrops().removeIf(e -> e.getItem().getItem() instanceof IWand);
        ItemEntity item = new ItemEntity(servant.level(),
                servant.getX(), servant.getY() + 0.5D, servant.getZ(), summonWand.copy());
        item.setDefaultPickUpDelay();
        event.getDrops().add(item);
        GoetyTuner.LOGGER.info("[Tuner] Tuner servant died, returned its summoning wand: {} ({})",
                servant.getUUID(), summonWand.getDisplayName().getString());
    }

    // ================= 两条加成怎么生效 =================
    //
    // 【0.0.20 修正】原先这里维护一个挂在 SPELL_POTENCY 上的 MULTIPLY_TOTAL modifier
    // （装备变化 + 每 32 tick 兜底刷新）。但那个属性的**注册基础值是 0.0**、
    // 读取入口 ModAttributes.getPotency 又是 (int) 截断 ⇒ 0.0 × (1+0.10) = 0.0，
    // **从未生效过**（tooltip 上那行「调律:巫法加成 +10%」是空头承诺）。
    // 现在两条加成统一由 combat/SpellDamageBonus 走伤害事件按百分比放大，
    // 因此本类**不再需要**任何 attribute modifier、也**不再需要**那两个每 tick / 每换装的监听器
    // （顺带省掉一个常驻 PlayerTickEvent 订阅）。
    // 本类保留的职责：法杖 NBT 的读写（witchcraftBonus / magicDamageBonus）、
    // 死亡掉落、tooltip 文本 —— 见下方。

    /**
     * 读取法杖的「调律·巫法加成」（**小数**：{@code 0.10} = +10%）。
     *
     * <p>只读读取（不 {@code getOrCreate}，避免污染无 tag 物品的 NBT）。
     * 非 {@link IWand} / 无 tag / 无该键一律返回 {@code 0.0D}。
     *
     * <p>【0.0.20】由 {@code private witchcraftOf} 提升为 {@code public}：
     * 与 {@link #magicDamageBonus} 同理，它是"法杖的巫法加成是多少"的**唯一实现**，
     * 现在有两个消费方 —— 本类的 SPELL_POTENCY modifier、
     * 以及 {@link com.tiaolvshi.goetytuner.combat.ServantWandBlessing}
     * （换算成百分数决定仆从增益档位；用户要求**以巫法加成为准**，因为它的默认值 +10%
     * 比魔法伤害加成的 +40% 陡峭得多，档位爬升才是"打了很多次"才到得的高度）。
     */
    public static double witchcraftBonus(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof IWand)) {
            return 0.0D;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_KEY)) {
            return 0.0D;
        }
        return tag.getCompound(NBT_KEY).getDouble(WITCHCRAFT_KEY);
    }

    /**
     * 读取法杖的「调律·魔法伤害加成」（**小数**：{@code 0.40} = +40%）。
     *
     * <p>只读读取（不 {@code getOrCreate}，避免污染无 tag 物品的 NBT）。
     * 非 {@link IWand} / 无 tag / 无该键一律返回 {@code 0.0D}。
     *
     * <p>【0.0.20】由 {@code private magicBonusOf} 提升为 {@code public}：
     * 它是"法杖加成是多少"的**唯一实现**，现在有三个消费方 ——
     * 本类的伤害加成、{@link com.tiaolvshi.goetytuner.ritual.TunerServantSummonRitual#isTunedWand}
     * （判定仪式中心那把杖算不算"有调律加成"）、
     * {@link com.tiaolvshi.goetytuner.combat.ServantWandBlessing}（换算成百分数决定仆从增益档位）。
     */
    public static double magicDamageBonus(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof IWand)) {
            return 0.0D;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_KEY)) {
            return 0.0D;
        }
        return tag.getCompound(NBT_KEY).getDouble(MAGIC_DAMAGE_KEY);
    }

    // ================= 紫色 tooltip 文本 =================

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !stack.hasTag() || !stack.getTag().contains(NBT_KEY)) {
            return;
        }
        CompoundTag t = stack.getTagElement(NBT_KEY);
        if (t == null) {
            return;
        }
        List<Component> tip = event.getToolTip();
        if (t.getDouble(WITCHCRAFT_KEY) > 0.0D) {
            tip.add(Component.translatable("tooltip.goetytuner.wand_witchcraft_bonus",
                    String.format("%.0f", t.getDouble(WITCHCRAFT_KEY) * 100.0D))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (t.getDouble(MAGIC_DAMAGE_KEY) > 0.0D) {
            tip.add(Component.translatable("tooltip.goetytuner.wand_magic_damage_bonus",
                    String.format("%.0f", t.getDouble(MAGIC_DAMAGE_KEY) * 100.0D))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    // ================= NBT 写入（保留原数据 + 叠加） =================

    /**
     * 给法杖追加升级加成。原 NBT（附魔/无法破坏/聚晶等）不动，只新增
     * {@code goetytuner} 复合键；若已存在同键加成，按 {@code wandBonusStack}
     * 配置叠加（true=加法叠加，false=覆盖）。
     */
    public static void applyWandUpgrade(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag t = stack.getOrCreateTagElement(NBT_KEY);
        double witch = t.getDouble(WITCHCRAFT_KEY);
        double dmg = t.getDouble(MAGIC_DAMAGE_KEY);
        double witchAdd = TunerCommonConfig.WAND_WITCHCRAFT_BONUS.get();
        double dmgAdd = TunerCommonConfig.WAND_MAGIC_DAMAGE_BONUS.get();
        if (TunerCommonConfig.WAND_BONUS_STACK.get()) {
            witch += witchAdd;
            dmg += dmgAdd;
        } else {
            witch = witchAdd;
            dmg = dmgAdd;
        }
        t.putDouble(WITCHCRAFT_KEY, witch);
        t.putDouble(MAGIC_DAMAGE_KEY, dmg);
    }
}
