/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.init;

import com.Polarice3.Goety.common.items.magic.MagicFocus;
import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.focus.RippleSpell;
import com.tiaolvshi.goetytuner.focus.TunerWand;
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
 * 物品注册：调律师刷怪蛋、调律师仆从刷怪蛋、调律波纹聚晶 + 本模组创造模式标签页。
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
 * ⇒ <b>0.0.18 新增的 {@code tuner_servant_spawn_egg} 同样必须走 ForgeSpawnEggItem。</b>
 *
 * <h2>【0.0.18】「调律波纹聚晶」的注册次序（勿随意搬动）</h2>
 *
 * <p>本模组有一个「初始化最后读取 MC 内全部聚晶」的环节：{@code FocusPoolManager#initIfNeeded()}
 * 在<b>服务器启动时</b>（{@code ModEvents#onServerStarting} → {@code ServerStartingEvent}）
 * 遍历 {@code ForgeRegistries.ITEMS}，把所有 {@code instanceof IFocus && getSpell() != null}
 * 的物品收进聚晶池。要让它"读得到、且不出 bug"，这里有三条**必须同时满足**的约束：
 *
 * <ol>
 *   <li><b>物品必须注册在同一个 {@link #ITEMS} DeferredRegister 上。</b>
 *       该 DeferredRegister 由 {@code GoetyTuner} 构造函数第一时间
 *       {@code ModItems.ITEMS.register(modBus)}。DeferredRegister 在 {@code RegisterEvent}
 *       （mod 加载早期、远早于 ServerStartingEvent）统一落地，所以放在本类里的物品
 *       **一定**会被后续的聚晶扫描看到。反之，若为本聚晶另起一个 DeferredRegister 却忘了
 *       {@code .register(modBus)}（或注册得晚），物品压根不会进入 {@code ForgeRegistries.ITEMS}，
 *       扫描自然看不到——这正是本项目在刷怪蛋上踩过的"注册时序坑"。</li>
 *   <li><b>{@code getSpell()} 必须立刻返回非 null 的单例。</b>
 *       扫描的准入条件就是 {@code focus.getSpell() != null}；Goety 的 {@link MagicFocus}
 *       在构造函数里就把 {@code spell} 存成字段，天然满足。若写成延迟创建，
 *       扫描时可能拿到 null 而被静默跳过。</li>
 *   <li><b>本聚晶必须同时写进黑名单。</b>见下。</li>
 * </ol>
 *
 * <p><b>为什么必须拉黑</b>：调律师 Boss 的卖点是"程序化演奏全部聚晶"。若不加限制，
 * 它会在战斗里抽到「调律波纹聚晶」并对着玩家放——等于 Boss 获得了一个 0.5 秒冷却、
 * 5 灵魂的免费重音，而且它自己也会被这发涟漪击退（{@code AccentRipple} 的排除表里没有它）。
 * 用户明确要求把它写进黑名单，故 {@code focus.blacklist} 的默认值已加入
 * {@code goetytuner:tuner_ripple_focus}。
 *
 * <p>⚠️ <b>黑名单是"改已有键的值"，不是"加键"</b>：Forge <b>不会</b>用新默认值覆盖玩家已有的
 * {@code goetytuner-common.toml}（本项目红线 8 / 0.0.10 的配置迁移陷阱）。
 * 因此老存档需要手工迁移——仓库内提供了 {@code scripts/add_ripple_focus_blacklist.py}，
 * 或者直接在游戏内 Mods → Config → 「聚晶黑名单」输入框里补上（0.0.14 起有入口）。
 */
public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, GoetyTuner.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GoetyTuner.MOD_ID);

    /** 调律师刷怪蛋（dev/测试与创造模式便捷生成；正式召唤方式见开发计划"结构/仪式"） */
    public static final RegistryObject<ForgeSpawnEggItem> TUNER_SPAWN_EGG =
            ITEMS.register("tuner_spawn_egg", () ->
                    new ForgeSpawnEggItem(ModEntities.TUNER, 0x8A2BE2, 0x2FA8FF, new Item.Properties()));

    /**
     * 【0.0.18 / 0.0.19】调律师仆从刷怪蛋。
     *
     * <p>0.0.19 起改用 {@link TunerServantSpawnEggItem}（继承 Goety 本体的仆从蛋范式）：
     * **直接放置 = 野生仆从；潜行放置 = 认主**，并附文字提示。详见该类 javadoc。
     */
    public static final RegistryObject<TunerServantSpawnEggItem> TUNER_SERVANT_SPAWN_EGG =
            ITEMS.register("tuner_servant_spawn_egg", () ->
                    new TunerServantSpawnEggItem(ModEntities.TUNER_SERVANT, 0x2B1A47, 0xBFA8E8,
                            new Item.Properties()));

    /**
     * 【0.0.19】调律师法杖 —— Boss 与调律师仆从的配发法杖。
     *
     * <p>为什么不再用 {@code goety:dark_wand}：让 Mob「真的在使用法杖」是本轮修复
     * "长按类聚晶只放一瞬间"的关键一步，而 {@code DarkWand.onUseTick} 对非玩家施法者会走
     * {@code failParticles + FIRE_EXTINGUISH} 分支（冒白烟、响灭火音）。
     * 本模组自备的这把杖行为干净、外观完全一致（模型继承 {@code goety:item/dark_wand}），
     * 详见 {@link com.tiaolvshi.goetytuner.focus.TunerWand} 的类注释。
     */
    public static final RegistryObject<TunerWand> TUNER_WAND =
            ITEMS.register("tuner_wand", TunerWand::new);

    /**
     * 【0.0.18】调律波纹聚晶：释放一次铺垫期的重音涟漪。
     *
     * <p>直接复用 Goety 的 {@link MagicFocus}（而不是自己写一个 IFocus 实现）：
     * 它自带标准的"灵魂消耗 / 系别 / 说明"提示文本、附魔规则与 {@code IFocus} 契约，
     * 与本模组 {@code BossWandHelper.installFocus} 的装配路径也完全兼容。
     */
    public static final RegistryObject<MagicFocus> TUNER_RIPPLE_FOCUS =
            ITEMS.register("tuner_ripple_focus", () -> new MagicFocus(new RippleSpell()));

    public static final RegistryObject<CreativeModeTab> TUNER_TAB =
            CREATIVE_TABS.register("tuner_tab", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.goetytuner"))
                            .icon(() -> new ItemStack(TUNER_SPAWN_EGG.get()))
                            .displayItems((params, output) -> {
                                output.accept(TUNER_SPAWN_EGG.get());
                                output.accept(TUNER_SERVANT_SPAWN_EGG.get());
                                output.accept(TUNER_RIPPLE_FOCUS.get());
                            })
                            .build());
}
