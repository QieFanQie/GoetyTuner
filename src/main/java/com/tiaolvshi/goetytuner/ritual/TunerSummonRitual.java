/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.ritual;

import com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity;
import com.Polarice3.Goety.common.crafting.RitualRecipe;
import com.Polarice3.Goety.common.ritual.SummonRitual;
import com.tiaolvshi.goetytuner.config.TunerCommonConfig;
import com.tiaolvshi.goetytuner.entity.TunerBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Set;

/**
 * 调律师仪式召唤：玩家手持任意法杖右键祭坛激活，仪式完成后生成手持该法杖的 Boss。
 *
 * <p>激活条件（{@link #identify}）：手持物品属于 goety:wands 标签 ∨ 在配置白名单中，
 * 且基座材料满足。白名单用于兼容未加入 goety:wands 标签的附属 mod 法杖。
 *
 * <p>关键时序（由 Goety 字节码确认）：
 * {@link SummonRitual#finish} 先 {@code super.finish} → 再对祭坛传入的 stack 执行
 * {@code shrink(1)} → 生成实体 → 调用 {@link #initSummoned}。因此必须在
 * {@code super.finish} 之前从祭坛 slot0 取出法杖副本，否则 shrink 后副本数量归零。
 *
 * <p>战斗期间 Boss 的 {@link com.tiaolvshi.goetytuner.focus.BossWandHelper#installFocus}
 * 会不断改写主手法杖的聚晶槽；为避免玩家原聚晶丢失，{@link #initSummoned} 同时把
 * 原始法杖快照存入 Boss NBT，死亡掉落以此快照为准（见 {@link com.tiaolvshi.goetytuner.ritual.WandUpgradeEvents}）。
 */
public class TunerSummonRitual extends SummonRitual {

    private ItemStack wandCopy = ItemStack.EMPTY;

    public TunerSummonRitual(RitualRecipe recipe) {
        super(recipe, false, false);
    }

    /**
     * 【v0.0.0 正式版】重写仪式激活判定：在 Goety 原生的 goety:wands 标签匹配之外，
     * 额外检查配置文件中的法杖白名单。白名单中的物品ID也可作为法杖激活仪式。
     *
     * <p>原始判定链：{@code DarkAltarBlockEntity.activate()} → 遍历所有仪式配方 →
     * {@code RitualRecipe.matches()} → {@code Ritual.identify()} →
     * {@code recipe.getActivationItem().test(stack) && areAdditionalIngredientsFulfilled(...)}
     *
     * <p>本重写在 super.identify() 返回 false 时，检查手持物品是否在白名单中；
     * 若在白名单中，仍需基座材料满足（areAdditionalIngredientsFulfilled）才返回 true。
     */
    @Override
    public boolean identify(Level level, BlockPos pos, Player player, ItemStack stack) {
        // 原生判定：goety:wands 标签 + 基座材料
        if (super.identify(level, pos, player, stack)) {
            return true;
        }
        // 白名单判定：配置中的法杖白名单
        if (!stack.isEmpty()) {
            Set<String> whitelist = TunerCommonConfig.getWandWhitelist();
            if (!whitelist.isEmpty()) {
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (itemId != null && whitelist.contains(itemId.toString())) {
                    // 白名单命中：仍需基座材料满足
                    return areAdditionalIngredientsFulfilled(level, pos, player, this.recipe.getIngredients());
                }
            }
        }
        return false;
    }

    @Override
    public void finish(Level level, BlockPos pos, DarkAltarBlockEntity altar,
                       Player player, ItemStack stack) {
        // 在父类 shrink(1) 之前保留法杖完整副本（含玩家原聚晶、附魔、无法破坏等全部 NBT）
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
        if (living instanceof TunerBoss boss && !wandCopy.isEmpty()) {
            // Boss 战斗用：会被持续改写聚晶槽，但死亡不掉落它
            boss.setItemSlot(EquipmentSlot.MAINHAND, wandCopy.copy());
            // 原始快照持久化：死亡掉落 + 升级逻辑读取此快照
            boss.setOriginalWand(wandCopy.copy());
            wandCopy = ItemStack.EMPTY;
        }
    }
}
