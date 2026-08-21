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
import com.Polarice3.Goety.init.ModAttributes;
import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.config.TunerCommonConfig;
import com.tiaolvshi.goetytuner.entity.TunerBoss;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 升级法杖全套机制（任务 #118）：
 *
 * <ul>
 *   <li><b>掉落</b>：仪式召唤的 Boss 死亡时掉落「原始法杖快照 + 加成 NBT」，
 *       并从掉落列表移除战斗中已被改写的法杖（快照制，见设计文档 5.1 修订）；</li>
 *   <li><b>10% 巫法加成</b>：玩家手持升级法杖时给实体挂 SPELL_POTENCY 的
 *       MULTIPLY_TOTAL 百分比 modifier（Goety 原生施法通道，天然乘法叠加语义）；</li>
 *   <li><b>40% 魔法伤害加成</b>：LivingDamageEvent 拦截魔法伤害 ×(1+加成)；</li>
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

    /** 巫法加成 modifier 的固定 UUID（装备/卸下时成对增删，防重复） */
    private static final UUID POTENCY_MODIFIER_UUID =
            UUID.nameUUIDFromBytes("goetytuner:wand_witchcraft_potency".getBytes(StandardCharsets.UTF_8));
    private static final String POTENCY_MODIFIER_NAME = "TunerWandWitchcraft";

    /**
     * 1.20.1 魔法伤害判定（无 {@code DamageSource.isMagic()}——那是 1.20.2+ API）：
     * <ol>
     *   <li>{@code forge:is_magic} damage_type tag（Goety 经 ModDamageTypeTagsProvider 把
     *       phobia/ice_bouquet/acid/spike/magic_bolt/wind_blast/soul_leech/life_leech 8 种
     *       法术伤害标入此 tag，数据驱动可被其它整合包扩展）；</li>
     *   <li>原版 {@code magic} / {@code indirectMagic} msgId（瞬间伤害/喷溅药水等；
     *       Goety magic_bolt 的 message_id 也是 {@code indirectMagic}，双重命中）。</li>
     * </ol>
     */
    private static final TagKey<DamageType> FORGE_IS_MAGIC =
            TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("forge", "is_magic"));

    private static boolean isMagicDamage(DamageSource src) {
        if (src.is(FORGE_IS_MAGIC)) {
            return true;
        }
        String id = src.getMsgId();
        return id.equals("magic") || id.equals("indirectMagic");
    }

    // ================= 掉落（快照制） =================

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
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

    // ================= 10% 巫法加成（SPELL_POTENCY 百分比 modifier） =================

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            refreshWitchcraftModifier(player);
        }
    }

    /**
     * 【第三十五轮】登录兜底：LivingEquipmentChangeEvent 只在装备变化时触发，
     * 玩家登录/重进存档时若主手已是升级法杖，modifier 不会自动挂上。
     * 每 20 tick（1秒）低频检查一次，无变化时零写入（幂等跳过）。
     */
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player == null || player.level().isClientSide) {
            return;
        }
        if ((player.tickCount & 0x1F) == 0x1F) { // 约每 32 tick 一次（避免与音乐HUD等节奏冲突）
            refreshWitchcraftModifier(player);
        }
    }

    /** 根据主/副手升级法杖重算 SPELL_POTENCY 百分比 modifier（先清后加，幂等） */
    private static void refreshWitchcraftModifier(Player player) {
        AttributeInstance attr = player.getAttribute(ModAttributes.SPELL_POTENCY.get());
        if (attr == null) {
            return;
        }
        double total = 0.0D;
        total += witchcraftOf(player.getMainHandItem());
        total += witchcraftOf(player.getOffhandItem());
        AttributeModifier existing = attr.getModifier(POTENCY_MODIFIER_UUID);
        if (existing != null && Math.abs(existing.getAmount() - total) < 1.0E-4D) {
            return; // 数值未变：不触碰 attribute（零开销）
        }
        attr.removeModifier(POTENCY_MODIFIER_UUID);
        if (total > 0.0D) {
            attr.addTransientModifier(new AttributeModifier(
                    POTENCY_MODIFIER_UUID, POTENCY_MODIFIER_NAME, total,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** 只读读取（不 getOrCreate，避免污染无 tag 物品的 NBT） */
    private static double witchcraftOf(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof IWand)) {
            return 0.0D;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_KEY)) {
            return 0.0D;
        }
        return tag.getCompound(NBT_KEY).getDouble(WITCHCRAFT_KEY);
    }

    // ================= 40% 魔法伤害加成（LivingDamageEvent） =================

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        if (!isMagicDamage(src)) {
            return; // 仅魔法/法术类伤害（forge:is_magic tag 或原版 magic/indirectMagic）
        }
        // 实际加成取法杖 NBT 中叠加后的值（多次击败叠加生效），而非配置单次值
        double magicBonus;
        if (attacker instanceof Player player) {
            magicBonus = magicBonusOf(player.getMainHandItem())
                    + magicBonusOf(player.getOffhandItem()); // 双持两把升级杖叠加
        } else if (attacker instanceof TunerBoss) {
            // Boss 手持升级法杖时是否同样加成（默认 false，防自伤放大）
            magicBonus = TunerCommonConfig.WAND_BONUS_APPLIES_TO_BOSS.get()
                    ? magicBonusOf(attacker.getMainHandItem()) : 0.0D;
        } else {
            magicBonus = 0.0D;
        }
        if (magicBonus > 0.0D) {
            event.setAmount((float) (event.getAmount() * (1.0D + magicBonus)));
        }
    }

    /** 只读读取（不 getOrCreate，避免污染无 tag 物品的 NBT） */
    private static double magicBonusOf(ItemStack stack) {
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
