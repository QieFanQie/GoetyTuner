/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.focus;

import com.Polarice3.Goety.common.items.handler.SoulUsingItemHandler;
import com.tiaolvshi.goetytuner.init.ModItems;
import net.minecraft.nbt.CompoundTag;
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
 * boss持杖：主手常驻一把**本模组自备**的 {@code goetytuner:tuner_wand}（{@link TunerWand}，
 * {@code SpellType.NONE} 通用系别、接受所有聚晶），施法前通过 SoulUsingItemHandler.insertItem
 * 换装当前聚晶（附魔由配置注入）。
 *
 * <p>【0.0.21】主手那把**必须**是 {@link TunerWand} —— 因为通道型施法会 {@code startUsingItem}，
 * 而 {@code goety:dark_wand}（及其全部子类）对非玩家施法者会冒白烟 + 响灭火音且不放法术。
 * 详见 {@link #inertCarrier(ItemStack)} 与 {@link TunerWand} 的类注释。
 */
public final class BossWandHelper {

    /**
     * 【0.0.21】载体杖 tag 里存"该画成哪把杖"的键：值是**原杖的完整 ItemStack 快照**
     * （`ItemStack#save` 出来的复合标签，含 id / Count / tag）。
     *
     * <p>为什么放在**物品 tag** 里而不是自建网络包：Mob 的手持物本来就同步给客户端
     * （`FriendlyByteBuf#writeItem` + `Item#shouldOverrideMultiplayerNbt()` 默认 `true` ⇒
     * 连 `IWand#getShareTag` 一起发），所以客户端**天然**能读到这个键、也能跟着存档走。
     * 于是"实际持惰性杖、画面画原杖"只需要一个渲染层替换（见 `client/render/TunerHandLayer`），
     * **不需要任何新的封包 / 同步字段**。
     */
    public static final String DISPLAY_KEY = "goetytuner_display";

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

    /**
     * 【0.0.21】把**任意**法杖栈换成「本模组自备的惰性施法载体」{@code goetytuner:tuner_wand}，
     * 并**原样保留旧杖的 NBT**（附魔 / 无法破坏 / {@code goetytuner} 调律加成 / 聚晶之外的记账键…）。
     *
     * <h2>为什么必须有这一步（反编译 + 字节码实证的完整链条）</h2>
     * <p>调律师的通道型施法要 {@code startUsingItem(MAIN_HAND)}（见 {@code CastChannel#tickChannel}），
     * 于是施法者手里的**那把杖的 Item 层代码会被真正跑起来**。而 Goety 的
     * {@code DarkWand}（含子类 {@code DarkStaff} / {@code NamelessStaff}）对**非玩家**施法者是致命的：
     *
     * <ol>
     *   <li>{@code DarkWand#getUseDuration}（jar 内 SRG 名 {@code m_8105_}）读的是
     *       {@code stack.getTag().getInt("Cast Time")}。**Mob 的持有物从来不会跑
     *       {@code ItemStack#inventoryTick}**（1.20.1 里只有 {@code Inventory#tick} 调它 ⇒ 仅玩家
     *       物品栏），所以这个键要么不存在（= 0）、要么是玩家留下的陈旧值；</li>
     *   <li>{@code LivingEntity#startUsingItem} 用 {@code getUseDuration()} 当 {@code useItemRemaining}；
     *       为 0 时 {@code LivingEntity#updateUsingItem} **连 {@code onUseTick} 都不调**，
     *       直接 {@code --useItemRemaining <= 0} ⇒ {@code completeUsingItem()}
     *       ⇒ {@code DarkWand#finishUsingItem}（{@code m_5922_}）⇒ {@code MagicResults(...)}；</li>
     *   <li>{@code DarkWand#MagicResults} 的整段逻辑包在 {@code caster instanceof Player} 里，
     *       **非玩家直接掉进 else 分支**：{@code failParticles(...)}（= {@code IWand} 的 default 实现，
     *       {@code for (i < random.nextInt(35) + 10)} 反复 {@code ParticleTypes.CLOUD} ⇒
     *       **10~44 个白烟粒子**）+ {@code FIRE_EXTINGUISH} 音效，**且什么都不放**。</li>
     * </ol>
     *
     * <p>更糟的是 {@code CastChannel#tickChannel} 每 tick 自愈式重设「正在使用」⇒ 这条链
     * **每 tick 触发一次**（不是"每冷却 tick"）⇒ Boss 会变成一台冒烟机器。
     *
     * <h2>谁能坐进主手（本方法覆盖的三个来源）</h2>
     * <ul>
     *   <li>0.0.18 及以前的旧档：主手存的就是 {@code goety:dark_wand}；</li>
     *   <li>仪式召唤（{@code TunerSummonRitual}）：0.0.20 及以前会把**玩家那把杖**
     *       （任意 {@code goety:wands} 物品，例如 {@code dark_staff} / {@code nameless_staff} /
     *       附属法杖）塞进 Boss 主手；</li>
     *   <li>指令 / 数据包 / 其它附属给 Boss 换上的任意 {@code IWand}。</li>
     * </ul>
     * ⇒ 只认「是不是我们自己的 {@code TunerWand}」，而不是只认 {@code goety:dark_wand} 一个注册名
     * （此前 {@code TunerBoss#isLegacyDarkWand} 就是这么写的、漏掉了全部 staff）。
     *
     * @param source 旧杖栈（可以为空 / 非法杖：那样得到的就是一把干净的空载体）
     * @return {@code source} 本身（若它已经是 {@code TunerWand}）或一把带着 {@code source} NBT 的新载体
     */
    public static ItemStack inertCarrier(ItemStack source) {
        if (source.getItem() instanceof TunerWand) {
            // 已经是载体：**原样返回**，连里面可能存着的显示快照一起留着
            // （重进存档时主手就是它，显示快照必须存活，否则外观会退回暗法杖）。
            return source;
        }
        ItemStack carrier = new ItemStack(ModItems.TUNER_WAND.get());
        CompoundTag tag = source.hasTag() ? source.getTag().copy() : new CompoundTag();
        // 只搬 tag（附魔/名字/调律加成/无法破坏…）；capability（聚晶槽）**不能**这样搬过来，
        // 施法前由 installFocus 重新装配当前聚晶。
        // 另外存一份"原杖完整快照"用于**渲染**：实际手持的仍是惰性载体杖（机制上安全），
        // 画面上画的是原杖（外观/模型/BEWLR 全部忠实地来自原物品本身）。
        tag.put(DISPLAY_KEY, source.copy().save(new CompoundTag()));
        carrier.setTag(tag);
        return carrier;
    }

    /**
     * 【0.0.21】取出载体杖里那份"原杖完整快照"（客户端渲染用；服务端读它没有意义但也不会出错）。
     *
     * <p>返回的栈是一个**新建的** ItemStack（`ItemStack#of` 解析出来的），可以直接交给
     * `ItemInHandRenderer` 渲染：模型、贴图、`display` 变换、附魔光效、自定义名、
     * 乃至物品自己的 {@code getCustomRenderer()}`（BEWLR，例如 {@code goety:nameless_staff}）
     * 全部来自原物品，**逐项忠实**。
     *
     * <p>物品在客户端不存在（附属模组缺失）或没存快照时返回 {@link ItemStack#EMPTY}，
     * 调用方应回退到渲染载体杖本身。
     */
    public static ItemStack displayWand(ItemStack carrier) {
        if (!(carrier.getItem() instanceof TunerWand)) {
            return ItemStack.EMPTY;
        }
        CompoundTag tag = carrier.getTag();
        if (tag == null || !tag.contains(DISPLAY_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }
        ItemStack display = ItemStack.of(tag.getCompound(DISPLAY_KEY));
        // 防递归：快照本身绝不可能是载体杖（inertCarrier 对 TunerWand 会提前返回），
        // 这里再挡一道，保证"显示杖"的渲染永远不需要二次替换。
        return display.getItem() instanceof TunerWand ? ItemStack.EMPTY : display;
    }

    public static void logCast(ServerLevel level, LivingEntity boss, FocusEntry entry) {
        com.tiaolvshi.goetytuner.GoetyTuner.LOGGER.debug("[Tuner] {} casts {} ({})", boss.getName().getString(),
                entry.getItemId(), entry.getCategory());
    }
}
