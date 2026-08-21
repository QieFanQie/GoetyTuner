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
import com.Polarice3.Goety.common.items.handler.SoulUsingItemHandler;
import com.Polarice3.Goety.utils.WandUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import javax.annotation.Nullable;

/**
 * Boss「模拟玩家右键施法」的核心：为boss动态构建 一把持有指定聚晶的法杖。
 *
 * 关键事实（源码调研结论）：
 * - Goety的IFocus/SoulUsingItemHandler均为public，无需AT/Mixin。
 * - 法术内部的附魔加成通过 WandUtil.findWand(caster) → IWand.getFocus(wand)
 *   → WandUtil.getLevels(enchant, caster) 读取，即：法杖装上带附魔的聚晶即获得加成。
 * - 施法生命周期：startSpell → useSpell(每tick) → SpellResult；冷却/耗蓝对非玩家不生效。
 *
 * boss持杖：主手常驻一把 Goety 黑暗魔杖（DARK_WAND，SpellType.NONE 通用系别，接受所有聚晶），
 * 施法前通过 SoulUsingItemHandler.insertItem 换装当前聚晶（附魔由配置注入）。
 */
public final class BossWandHelper {

    private BossWandHelper() {
    }

    /**
     * 构建一把装配了指定聚晶（含附魔）的法杖。
     *
     * @param wandStack    法杖物品（如 DARK_WAND，需具备 ITEM_HANDLER capability）
     * @param entry        目标聚晶条目
     * @param enchantments 附魔注入表（可null=不加附魔；enchant->level）
     */
    public static ItemStack installFocus(ItemStack wandStack, FocusEntry entry,
                                         @Nullable java.util.Map<Enchantment, Integer> enchantments) {
        ItemStack focus = entry.createFocusStack();
        if (enchantments != null) {
            for (var e : enchantments.entrySet()) {
                if (entry.getSpell() != null && entry.getSpell().acceptedEnchantments().contains(e.getKey())) {
                    focus.enchant(e.getKey(), e.getValue());
                }
            }
        }
        // 【2026-08-18 第八轮修复】防御：主手非法杖（无 ITEM_HANDLER capability）时跳过装配，
        // 不抛异常崩服务器（SoulUsingItemHandler.get 内部 orElseThrow）。
        // 正常情况下主手由 TunerBoss 构造函数/readAdditionalSaveData 保证常驻暗法杖（IWand）。
        var capOpt = wandStack.getCapability(
                net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).resolve();
        if (capOpt.isEmpty() || !(capOpt.get() instanceof SoulUsingItemHandler handler)) {
            return wandStack;
        }
        if (!handler.getSlot().isEmpty()) {
            handler.extractItem(); // 先取出旧聚晶
        }
        handler.insertItem(focus);
        return wandStack;
    }

    /** boss当前主手是否已装该聚晶 */
    public static boolean isHoldingFocus(LivingEntity boss, FocusEntry entry) {
        ItemStack wand = boss.getMainHandItem();
        ItemStack focus = com.Polarice3.Goety.api.items.magic.IWand.getFocus(wand);
        return !focus.isEmpty() && focus.getItem() == entry.getFocusItem();
    }

    /** 读取当前聚晶法术（供调试/断言） */
    @Nullable
    public static ISpell currentSpell(LivingEntity boss) {
        return WandUtil.getSpell(boss);
    }

    /** 构建附魔名→等级映射的简易文本描述（调试命令用） */
    public static String describe(ItemStack wand) {
        ItemStack focus = com.Polarice3.Goety.api.items.magic.IWand.getFocus(wand);
        return focus.isEmpty() ? "(empty)" : focus.toString();
    }

    public static void logCast(ServerLevel level, LivingEntity boss, FocusEntry entry) {
        com.tiaolvshi.goetytuner.GoetyTuner.LOGGER.debug("[Tuner] {} casts {} ({})", boss.getName().getString(),
                entry.getItemId(), entry.getCategory());
    }
}
