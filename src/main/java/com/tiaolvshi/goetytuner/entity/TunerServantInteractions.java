/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.entity;

import com.Polarice3.Goety.api.items.magic.IFocus;
import com.Polarice3.Goety.common.items.magic.FocusBag;
import com.tiaolvshi.goetytuner.GoetyTuner;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 【0.0.18 新增 / 0.0.20 补齐提示与批量】调律师仆从的**聚晶指令**：
 * 玩家手持聚晶（或**聚晶包 / 多晶大袋**）对仆从左键 / 右键。
 *
 * <table border="1">
 *   <tr><th>操作</th><th>手持单个聚晶</th><th>手持聚晶包 / 多晶大袋</th><th>再操作一次</th></tr>
 *   <tr><td>左键（{@link AttackEntityEvent}）</td><td>设为「<b>不释放</b>」该聚晶</td>
 *       <td>袋内**全部**聚晶一起设为「不释放」</td><td>回到中立 / 整体清除</td></tr>
 *   <tr><td>右键（{@link PlayerInteractEvent.EntityInteract}）</td><td>设为「<b>优先释放</b>」该聚晶</td>
 *       <td>袋内**全部**聚晶一起设为「优先释放」</td><td>回到中立 / 整体清除</td></tr>
 * </table>
 *
 * <h2>0.0.19 收尾 · 三处修正（都来自用户实测反馈）</h2>
 *
 * <p><b>① 去掉"潜行 + 左键 = 清空全部指令"</b>（这是 0.0.18 我自己加的逃生通道）。
 * 它有个副作用：**玩家只要处于潜行，左键就变成"清空"而不是"设为不释放"** —— 表现为
 * 「不释放的调整功能不起效」。语义本身（两种状态互斥 + 各自可撤销）已经足够，这个额外
 * 手势属于不必要的复杂度，已删除 ⇒ **左键永远是"切换不释放"**。
 *
 * <p><b>② 野生仆从也接受指令</b>。0.0.19 我把归属改成"刷怪蛋放置时定死"
 * （直接放 = 野生、潜行放 = 认主），并顺手把**非主人一律拒绝**。但玩家的默认操作是
 * **直接放置**（不潜行）⇒ 得到的是野生仆从 ⇒ 任何聚晶指令都被拒 ⇒ 看起来"功能完全坏了"。
 * <p>修正后的规则：**只要不是"别人家的仆从"就接受指令** ——
 * {@code owner == null}（野生）**或** {@code owner == player}。
 *
 * <h2>0.0.20 · 提示回补（用户反馈）</h2>
 * <p>0.0.19 我把这里**所有**动作栏提示都删掉了（当时用户的要求是"不要冗余提示"），
 * 结果把**左右键的指令结果提示**也一起删了 —— 用户明确要求恢复：
 * "左键和右键功能都没有提示了；……这个提示是需要的"。
 * <p>所以现在的取舍是**分开的**（这才是用户真正要的）：
 * <ul>
 *   <li><b>要提示</b>：聚晶指令的**结果**（设成了什么 / 取消了没有 / 被拒绝的原因）——
 *       这是操作反馈，没有它玩家只能靠猜；</li>
 *   <li><b>不要提示</b>：物品介绍（只留刷怪蛋那一行 tooltip）、刷怪蛋**放置**时的提示
 *       （见 {@code TunerServantSpawnEggItem}）。</li>
 * </ul>
 * 无论有没有提示，**状态变化一律同时打 INFO 日志**（{@code FocusPoolManager} 里），
 * 这样"看起来没反应"永远可以查得到（见 HANDOVER 红线 17）。
 *
 * <h2>为什么用 Forge 事件而不是覆写 Item 的方法</h2>
 * <p>"手持任意一个聚晶"都要生效，而聚晶来自 Goety 本体与任意附属模组——我们不可能去改它们的
 * {@code Item} 子类，也不可能给每一个都加一个 mixin。Forge 的这两个事件在**交互发生之前**触发，
 * 既能读到手上的物品、又能取消掉默认行为：
 * <ul>
 *   <li>左键：取消 {@code AttackEntityEvent} ⇒ <b>不会真的打到仆从</b>；</li>
 *   <li>右键：取消 {@code EntityInteract} 并置结果为 SUCCESS ⇒ 不会再走
 *       {@code entity.interact} / {@code item.interactLivingEntity} 那条链。</li>
 * </ul>
 *
 * <h2>为什么只在服务端执行</h2>
 * <p>两个事件在**客户端也会触发一次**（本地预测）。若在客户端也 {@code setCanceled(true)}，
 * 左键那条路径会让 {@code LocalPlayer} 提前返回、**连攻击包都不发**，服务端永远收不到指令
 * ⇒ 机制直接失效。所以：客户端一律放行，真正的判定、取消与状态修改全部在服务端。
 * （钩子位置已用 {@code javap} 反编译映射版 MC 实证：{@code Player.attack} 偏移 0 即
 * {@code ForgeHooks.onPlayerAttackTarget}；{@code Player.interactOn} 偏移 30 即
 * {@code ForgeHooks.onInteractEntity}，早于 {@code Entity.interact} 与
 * {@code stack.interactLivingEntity}。）
 */
@Mod.EventBusSubscriber(modid = GoetyTuner.MOD_ID)
public class TunerServantInteractions {

    /** 左键：切换「不释放」（手持袋子时 = 批量）。 */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof TunerServant servant)) {
            return;
        }
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return; // 见类注释：客户端必须放行，否则攻击包不会发出去
        }
        ItemStack held = player.getMainHandItem();

        // ---- 批量（聚晶包 / 多晶大袋） ----
        if (held.getItem() instanceof FocusBag) {
            List<String> ids = focusIdsInBag(held);
            if (ids.isEmpty()) {
                return; // 袋里没有聚晶：不吞掉这次攻击 / 交互
            }
            if (!canCommand(servant, player)) {
                return; // 别人家的仆从：只提示，不吞掉
            }
            event.setCanceled(true);
            if (!servant.acceptCommandClick(player.level().getGameTime(), true)) {
                return; // 按住左键时的重复包：去抖（见 TunerServant#acceptCommandClick）
            }
            boolean on = servant.applyFocusDisabledBatch(ids);
            message(player, on ? "info.goetytuner.servant.batch.disabled"
                    : "info.goetytuner.servant.batch.disabled.cleared", ids.size());
            return;
        }

        // ---- 单个聚晶 ----
        String focusId = focusIdOrNull(held);
        if (focusId == null || !canCommand(servant, player)) {
            return;
        }
        event.setCanceled(true); // 指令而非攻击：不吃伤害、不掉血
        if (!servant.acceptCommandClick(player.level().getGameTime(), true)) {
            return; // 按住左键时的重复包：去抖
        }
        boolean on = servant.toggleFocusDisabled(focusId);
        message(player, on ? "info.goetytuner.servant.focus.disabled"
                : "info.goetytuner.servant.focus.disabled.cleared", held.getHoverName());
    }

    /** 右键：切换「优先释放」（手持袋子时 = 批量）。 */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof TunerServant servant)) {
            return;
        }
        Player player = event.getEntity();
        if (event.getLevel().isClientSide) {
            return; // 同上
        }
        ItemStack held = player.getItemInHand(event.getHand());

        // ---- 【0.0.20】下界之星：切换「只释放优先聚晶」 ----
        if (held.is(Items.NETHER_STAR)) {
            if (!canCommand(servant, player)) {
                return;
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (!servant.acceptCommandClick(player.level().getGameTime(), false)) {
                return; // 按住右键每 4 tick 会重发一次交互包：去抖
            }
            boolean on = servant.togglePriorityOnly();
            // 用户要求"没有优先聚晶时也可以这么设置，只是无效、照常释放" ⇒ 提示里点明这一点
            String key;
            if (!on) {
                key = "info.goetytuner.servant.priority_only.cleared";
            } else if (servant.hasPriorityFoci()) {
                key = "info.goetytuner.servant.priority_only";
            } else {
                key = "info.goetytuner.servant.priority_only.empty";
            }
            player.displayClientMessage(Component.translatable(key).withStyle(
                    on ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
            return;
        }

        // ---- 批量（聚晶包 / 多晶大袋） ----
        if (held.getItem() instanceof FocusBag) {
            List<String> ids = focusIdsInBag(held);
            if (ids.isEmpty()) {
                return;
            }
            if (!canCommand(servant, player)) {
                return;
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (!servant.acceptCommandClick(player.level().getGameTime(), false)) {
                return; // 按住右键时的重复包：去抖
            }
            boolean on = servant.applyFocusPriorityBatch(ids);
            message(player, on ? "info.goetytuner.servant.batch.priority"
                    : "info.goetytuner.servant.batch.priority.cleared", ids.size());
            return;
        }

        // ---- 单个聚晶 ----
        String focusId = focusIdOrNull(held);
        if (focusId == null || !canCommand(servant, player)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!servant.acceptCommandClick(player.level().getGameTime(), false)) {
            return; // 按住右键时的重复包：去抖
        }
        boolean on = servant.toggleFocusPriority(focusId);
        message(player, on ? "info.goetytuner.servant.focus.priority"
                : "info.goetytuner.servant.focus.priority.cleared", held.getHoverName());
    }

    // ================= 工具 =================

    /** 动作栏提示（第二条参数是"聚晶名"或"数量"，随文案而定）。 */
    private static void message(Player player, String key, Object arg) {
        player.displayClientMessage(
                Component.translatable(key, arg).withStyle(ChatFormatting.AQUA), true);
    }

    /** 手上的物品是聚晶时返回它的注册 id（{@code namespace:path}），否则 null。 */
    private static String focusIdOrNull(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof IFocus)) {
            return null;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? null : id.toString();
    }

    /**
     * 【0.0.20】读出聚晶包 / 多晶大袋里的**全部聚晶 id**（去重，保持槽位顺序）。
     *
     * <p>为什么用通用的物品栏 capability 而不是 Goety 的内部类：
     * {@code FocusPack extends FocusBag}，两者都在 {@code initCapabilities} 里挂了
     * {@code FocusBagItemCapability}（字节码实证：袋子 11 格、大袋 21 格），
     * 所以 {@code ForgeCapabilities.ITEM_HANDLER} 对两者都成立，
     * 而本类只对 {@link FocusBag} 的实例调用（见两个事件里的 {@code instanceof} 判定）。
     *
     * <p>袋子里的**非聚晶物品直接跳过**（药剂、杂物等），不影响批量指令。
     */
    private static List<String> focusIdsInBag(ItemStack bagStack) {
        List<String> ids = new ArrayList<>();
        LazyOptional<IItemHandler> optional = bagStack.getCapability(ForgeCapabilities.ITEM_HANDLER);
        IItemHandler handler = optional.orElse(null);
        if (handler == null) {
            GoetyTuner.LOGGER.warn("[Tuner] Focus bag without ITEM_HANDLER capability: {}",
                    bagStack.getItem());
            return ids;
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            String id = focusIdOrNull(handler.getStackInSlot(i));
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * 该玩家能否对这只仆从下指令。
     *
     * <p>【0.0.19 修正】只拒绝"**别人家的**仆从"：
     * <ul>
     *   <li>{@code owner == null}（野生，刷怪蛋**直接**放置的产物）⇒ **接受**。
     *       野生仆从依然是野生的（不跟随、不被认主），只是可以被配置 —— 归属不会因为交互而改变，
     *       仍符合"要么一直是野生、要么一直是某人的"。</li>
     *   <li>{@code owner == player}（自己的，含仪式召唤的）⇒ 接受。</li>
     *   <li>其它（已被他人认主）⇒ 拒绝。**给玩家一句提示 + 打一条 INFO**：
     *       静默拒绝正是"看起来没反应"的来源（见 HANDOVER 红线 17）。</li>
     * </ul>
     */
    private static boolean canCommand(TunerServant servant, Player player) {
        LivingEntity owner = servant.getTrueOwner();
        if (owner == null || owner == player) {
            return true;
        }
        GoetyTuner.LOGGER.info("[Tuner] Servant focus command refused: {} belongs to {}, not {}",
                servant.getUUID(), owner.getName().getString(), player.getName().getString());
        player.displayClientMessage(
                Component.translatable("info.goetytuner.servant.not_owner").withStyle(ChatFormatting.RED),
                true);
        return false;
    }
}
