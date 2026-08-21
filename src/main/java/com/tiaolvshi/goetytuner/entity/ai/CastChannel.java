/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.entity.ai;

import com.Polarice3.Goety.utils.WandUtil;
import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.config.TunerCommonConfig;
import com.tiaolvshi.goetytuner.focus.BossWandHelper;
import com.tiaolvshi.goetytuner.focus.FocusCategory;
import com.tiaolvshi.goetytuner.focus.FocusEntry;
import com.tiaolvshi.goetytuner.focus.FocusPoolManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * 施法通道：忠实模拟玩家「长按右键施法」的完整生命周期。
 *
 * 状态机：IDLE → WARMUP(前摇，tick useSpell) → CAST_DONE(SpellResult) → 进入冷却池 → IDLE
 *
 * - 前摇时长 = min(spell.castDuration(boss, wand), maxCastWindowTicks) × 倍率，保底10tick。
 *   【2026-08-18 第十三轮】Goety 法术前摇 castDuration 默认 5-10 秒甚至 300 秒（玩家按住右键
 *   的完整蓄力时长），boss 照搬会导致站桩蓄力过久、几乎不放技能。已确认玩家「提前松手」
 *   （releaseUsing → MagicResults → SpellResult）是合法释放路径，故将每次蓄力截断到配置窗口
 *   （默认40tick=2秒），显著提升释放频率。
 * - 完成后：聚晶移入对应冷却池（spell.spellCooldown(boss) + 防复读额外冷却）
 * - 【2026-08-18 第十三轮】beginCast 成功后立即将聚晶从功能池移除（锁池），防止高潮多通道
 *   争抢同一聚晶重复抽中；interrupt/失败时归还功能池（无冷却）。
 * - 施法开始前通过 BossWandHelper 把聚晶装到boss主手法杖上（附魔加成随之生效）
 * - 主线程tick驱动（不做异步线程，避免与MC世界状态竞态）
 *
 * 高潮期：三个通道（防御/召唤/攻击）并行、互不干扰；
 * 铺垫期：单一通道按 防御→召唤→攻击 轮换。
 */
public class CastChannel {

    public enum State { IDLE, WARMUP, FINISHED }

    private final FocusCategory category; // 本通道负责的功能分块
    private final TunerCastCallback callback;

    private State state = State.IDLE;
    private FocusEntry current;
    private int warmupTicksRemaining;
    private int castTicksElapsed;
    private ItemStack wandSnapshot = ItemStack.EMPTY;

    /** 施法事件的宿主回调（由TunerBoss实现） */
    public interface TunerCastCallback {
        LivingEntity boss();

        FocusPoolManager pools();

        /** 施法开始（前摇起手） */
        default void onCastStart(FocusEntry entry) {
        }

        /** 施法完成（法术已释放，进入冷却） */
        default void onCastFinish(FocusEntry entry) {
        }

        /** 通道因阶段切换被打断 */
        default void onCastInterrupted(FocusEntry entry) {
        }

        /** 【第二十六轮】施法抛异常（如聚晶需要玩家来源）：自动拉黑该聚晶 */
        default void onCastFailed(FocusEntry entry) {
        }
    }

    public CastChannel(FocusCategory category, TunerCastCallback callback) {
        this.category = category;
        this.callback = callback;
    }

    // ================= 状态机驱动 =================

    /** 每tick调用；idle时按需抽取新聚晶开始施法 */
    public void tick(ServerLevel level, double minionFill, boolean summonBlocked, double warmupMultiplier) {
        LivingEntity boss = callback.boss();
        if (state == State.IDLE) {
            return;
        }
        if (!(level.getEntity(boss.getId()) instanceof LivingEntity)) {
            return;
        }
        if (current == null) {
            state = State.IDLE;
            return;
        }

        if (state == State.WARMUP) {
            // 【2026-08-18 第十三轮】攻击类蓄力期间目标丢失/死亡：打断并归还聚晶，避免站桩空蓄
            if (category == FocusCategory.ATTACK && boss instanceof Mob mob) {
                LivingEntity target = mob.getTarget();
                if (target == null || !target.isAlive() || target.isRemoved()) {
                    interrupt(level);
                    return;
                }
            }
            castTicksElapsed++;
            var spell = current.getSpell();
            var stats = WandUtil.getStats(boss, spell);
            // 【第三十一轮】施法朝向修正：瞬移走位（弧形/后撤/追击跳）与嘲讽逃跑会瞬间
            // 改变位置/朝向，而 LookControl 每 tick 最多转 50°，法术沿视线发射就会朝旧
            // 方向打偏。前摇期间每 tick 把朝向（含俯仰）钉在仇恨目标上，
            // 保证法术方向 = boss 面朝方向。
            snapTowardTarget(boss);
            try {
                // 模拟 DarkWand.onUseTick：每tick useSpell
                spell.useSpell(level, boss, boss.getMainHandItem(), castTicksElapsed, stats);

                if (--warmupTicksRemaining <= 0) {
                    // 前摇结束：结算法术效果（直接调 SpellResult，等价于 Spell.mobSpellResult 的服务端路径）
                    spell.SpellResult(level, boss, boss.getMainHandItem(), stats);
                    finishCast();
                }
            } catch (Throwable t) {
                // 【第二十六轮】自愈：聚晶需要玩家来源等情况会抛异常，捕获后拉黑该聚晶并归还池
                GoetyTuner.LOGGER.error("[Tuner] Spell {} threw exception during cast (likely needs player source). "
                        + "Auto-blacklisting focus {}.", current.getItemId(), current.getItemId(), t);
                FocusPoolManager.runtimeBlacklist(current.getItemId().toString());
                callback.onCastFailed(current);
                callback.pools().returnEntry(current);
                current = null;
                state = State.IDLE;
            }
        }
    }

    /** 抽取并开始一次施法（含前摇）；返回是否成功开始 */
    public boolean beginCast(ServerLevel level, double minionFill, boolean summonBlocked, double warmupMultiplier) {
        if (state != State.IDLE) {
            return false;
        }
        LivingEntity boss = callback.boss();
        FocusPoolManager pools = callback.pools();
        FocusEntry entry = category.isScored()
                ? pools.draw(category, minionFill, summonBlocked)
                : pools.drawUniform(category);
        if (entry == null) {
            return false;
        }
        var spell = entry.getSpell();
        if (spell == null) {
            return false;
        }
        // 条件不满足（如地形/天气限制）则换下一个：直接放弃本次，聚晶留在池中
        if (!spell.conditionsMet(level, boss)) {
            return false;
        }

        // 主手必须是法杖（IWand 自带 SoulUsing capability），否则本次施法无法进行
        // （SoulUsingItemHandler.get 会对非法杖抛异常）。正常由 TunerBoss 保证常驻暗法杖；
        // 此检查为最终防线：异常时放弃本次施法而非崩溃。
        if (!(boss.getMainHandItem().getItem() instanceof com.Polarice3.Goety.api.items.magic.IWand)) {
            return false;
        }
        // 装配法杖：boss主手杖 + 当前聚晶（附魔注入示例： potency 2，后续由配置驱动）
        // TODO: 附魔表由 focus_enchants.json 配置注入
        BossWandHelper.installFocus(boss.getMainHandItem(), entry, null);
        boss.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, boss.getMainHandItem());

        current = entry;
        castTicksElapsed = 0;
        // 【2026-08-18 第十三轮】蓄力时长封顶：Goety castDuration 默认 5-10 秒甚至 300 秒，
        // boss 照搬会站桩蓄力过久。截断到 maxCastWindowTicks（默认40=2秒），保底10tick(0.5秒)。
        // 玩家提前松手（releaseUsing→MagicResults）是合法释放路径，提前结算安全。
        int raw = (int) Math.max(1, spell.castDuration(boss, boss.getMainHandItem()) * warmupMultiplier);
        int window = TunerCommonConfig.MAX_CAST_WINDOW_TICKS.get();
        int warmup = Math.min(raw, window);
        if (warmup < 10) {
            warmup = Math.min(10, Math.max(1, raw)); // 不强制拉长原本更短的前摇
        }
        warmupTicksRemaining = warmup;
        state = State.WARMUP;

        // 【2026-08-18 第十三轮】锁池：立即从功能池移除，防止高潮多通道/重音瞬发争抢同一聚晶。
        // finishCast 时 moveToCooldown 会将其放入冷却池；interrupt 时 returnEntry 归还功能池。
        pools.removeEntry(entry);

        // 起手：startSpell + 施法音效
        // 【第三十二轮】startSpell 结算路径同样钉朝向：VoidRift/FlameStrike（Goety）、
        // AbyssalBeam（灾变）、ChipRain（觉醒）等法术在 startSpell 内就沿视线 rayTrace
        // 生成实体——boss 若在转身未完成时起手（LookControl 每 tick 限 50°），
        // 这些法术会沿旧视线放出，无任何瞬移干扰也能复现"朝另一个方向放"。
        snapTowardTarget(boss);
        try {
            spell.startSpell(level, boss, boss.getMainHandItem(), WandUtil.getStats(boss, spell));
        } catch (Throwable t) {
            // 【第三十一轮】此路径施法尚未开始（onCastStart 未调用），不触发 onCastFailed
            // 回调——TunerBoss 的施法状态计数器在 onCastStart 才递增，此处调用会造成计数失衡。
            GoetyTuner.LOGGER.error("[Tuner] Spell {} threw on startSpell. Auto-blacklisting focus {}.",
                    current.getItemId(), current.getItemId(), t);
            FocusPoolManager.runtimeBlacklist(current.getItemId().toString());
            callback.onCastFailed(current);
            callback.pools().returnEntry(current);
            current = null;
            state = State.IDLE;
            return false;
        }
        var sound = spell.CastingSound(boss);
        if (sound != null) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), sound,
                    net.minecraft.sounds.SoundSource.HOSTILE, spell.castingVolume(), spell.castingPitch());
        }
        callback.onCastStart(entry);
        BossWandHelper.logCast(level, boss, entry);
        return true;
    }

    private void finishCast() {
        LivingEntity boss = callback.boss();
        int cooldown = current.getSpell().spellCooldown(boss)
                + TunerCommonConfig.EXTRA_CAST_COOLDOWN.get();
        callback.pools().moveToCooldown(current, cooldown);
        callback.onCastFinish(current);
        current = null;
        state = State.IDLE;
    }

    /** 强制打断（进入高潮/低谷时由阶段切换调用 / 蓄力中目标丢失） */
    public void interrupt(ServerLevel level) {
        if (state == State.WARMUP && current != null) {
            LivingEntity boss = callback.boss();
            var spell = current.getSpell();
            spell.stopSpell(level, boss, boss.getMainHandItem(),
                    com.Polarice3.Goety.api.items.magic.IWand.getFocus(boss.getMainHandItem()),
                    castTicksElapsed, WandUtil.getStats(boss, spell));
            callback.onCastInterrupted(current);
            // 【2026-08-18 第十三轮】归还功能池（无冷却）：打断不应惩罚聚晶
            callback.pools().returnEntry(current);
            GoetyTuner.LOGGER.debug("[Tuner] Cast interrupted: {}", current.getItemId());
        }
        current = null;
        state = State.IDLE;
    }

    /** 无前摇瞬发（重音触发时使用：其他类聚晶） */
    public boolean instantCast(ServerLevel level, FocusCategory category) {
        if (state != State.IDLE) {
            return false;
        }
        LivingEntity boss = callback.boss();
        FocusEntry entry = callback.pools().drawUniform(category);
        if (entry == null) {
            return false;
        }
        var spell = entry.getSpell();
        if (spell == null || !spell.conditionsMet(level, boss)) {
            return false;
        }
        BossWandHelper.installFocus(boss.getMainHandItem(), entry, null);
        // 【第三十一轮】瞬发同样钉朝向：重音瞬发常紧跟走位瞬移，直接按当前视线发射会打偏
        snapTowardTarget(boss);
        try {
            spell.SpellResult(level, boss, boss.getMainHandItem(), WandUtil.getStats(boss, spell));
        } catch (Throwable t) {
            GoetyTuner.LOGGER.error("[Tuner] Spell {} threw on instantCast. Auto-blacklisting focus {}.",
                    entry.getItemId(), entry.getItemId(), t);
            FocusPoolManager.runtimeBlacklist(entry.getItemId().toString());
            callback.pools().returnEntry(entry);
            return false;
        }
        callback.pools().moveToCooldown(entry, spell.spellCooldown(boss));
        return true;
    }

    // ================= 查询 =================

    /**
     * 【第三十一轮】施法朝向修正：把 boss 朝向（含俯仰）直接钉在仇恨目标上。
     *
     * <p>根因：瞬移走位（二阶段弧形瞬移/一阶段后撤步/追击跳）与嘲讽逃跑会瞬间改变
     * boss 的位置与朝向，而 LookControl 每 tick 最多转 50°，跟不上瞬移造成的角度突变；
     * Goety 法术沿施法者视线/旋转角度发射，就会朝旧方向打偏（"施法方向 bug"）。
     *
     * <p>处理：前摇期间每 tick、以及瞬发结算前，把 yRot/yHeadRot/yBodyRot/xRot 直接
     * 设为指向目标（头部俯仰瞄准目标眼睛，空中目标也能朝上打）。
     * 该调用发生在阶段行为步骤（早于嘲讽状态机），即使嘲讽逃跑中施法，发射方向也正确。
     */
    private void snapTowardTarget(LivingEntity boss) {
        if (!(boss instanceof Mob mob)) {
            return;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        double dx = target.getX() - boss.getX();
        double dy = target.getEyeY() - boss.getEyeY();
        double dz = target.getZ() - boss.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Math.atan2(dy, Math.max(horiz, 1.0E-4D)) * (180.0D / Math.PI)));
        boss.setYRot(yaw);
        boss.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        boss.setXRot(pitch);
        // 【第三十二轮】同步上一 tick 旋转值：个别法术/粒子用 partialTicks<1 在
        // yRot/yRotO 之间插值取方向，若 O 值仍是转身前的旧角度会打偏。
        // O 字段为纯服务端逻辑量，服务端直写不影响客户端渲染插值。
        boss.yRotO = yaw;
        boss.xRotO = pitch;
    }

    public State getState() {
        return state;
    }

    public FocusCategory getCategory() {
        return category;
    }

    public boolean isBusy() {
        return state != State.IDLE;
    }
}
