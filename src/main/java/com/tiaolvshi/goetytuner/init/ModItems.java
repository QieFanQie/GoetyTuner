/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.init;

import com.tiaolvshi.goetytuner.GoetyTuner;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 物品注册：调律师刷怪蛋 + 本模组创造模式标签页。
 *
 * <p>刷怪蛋颜色取 Boss 形象：深色西装（无头指挥家）+ 灵魂紫（诡厄巫法主题色）。
 *
 * <p>【2026-08-18 第七轮修复】必须用 {@link ForgeSpawnEggItem} 而非原版 SpawnEggItem：
 * 本项目环境中 minecraft:item 的 RegisterEvent 先于 minecraft:entity_type fire，
 * 原版 SpawnEggItem 在 item 注册 supplier 里立即 {@code ModEntities.TUNER.get()}
 * 会抛 "Registry Object not present: goetytuner:tuner"（实证日志 runclient-7th.log）。
 * ForgeSpawnEggItem 以 {@code Supplier}（RegistryObject 即 Supplier）延迟解析 EntityType，
 * 颜色注册由 Forge 的 ColorRegisterHandler 在注册完成后自动处理——与 Goety 本体
 * ModSpawnEggItem（Lazy.of(RegistryObject)）同机制。
 */
public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, GoetyTuner.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GoetyTuner.MOD_ID);

    /** 调律师刷怪蛋（dev/测试与创造模式便捷生成；正式召唤方式见开发计划"结构/仪式"） */
    public static final RegistryObject<ForgeSpawnEggItem> TUNER_SPAWN_EGG =
            ITEMS.register("tuner_spawn_egg", () ->
                    new ForgeSpawnEggItem(ModEntities.TUNER, 0x1E1E28, 0x7B5CD6, new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> TUNER_TAB =
            CREATIVE_TABS.register("tuner_tab", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.goetytuner"))
                            .icon(() -> new ItemStack(TUNER_SPAWN_EGG.get()))
                            .displayItems((params, output) -> output.accept(TUNER_SPAWN_EGG.get()))
                            .build());
}
