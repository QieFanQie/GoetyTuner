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
 * 状态机：IDLE → WARMUP(前摇，每tick useSpell) → 结算(SpellResult) → 进入冷却池 → IDLE
 *
 * - 前摇时长 = min(spell.castDuration(boss, wand), maxCastWindowTicks) × 倍率，保底10tick。
 *   【2026-08-18 第十三轮】Goety 法术前摇 castDuration 默认 5-10 秒甚至 300 秒（玩家按住右键
 *   的完整蓄力时长），boss 照搬会导致站桩蓄力过久、几乎不放技能。已确认玩家「提前松手」
 *   （releaseUsing → MagicResults → SpellResult）是合法释放路径，故将每次蓄力截断到配置窗口
 *   （【第二十六轮起】默认50tick=2.5秒），显著提升释放频率。
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

    /** 通道状态：IDLE=空闲可接单；WARMUP=前摇中（结算完成或被打断后立即回 IDLE）。 */
    public enum State { IDLE, WARMUP }

    private final FocusCategory category; // 本通道负责的功能分块
    private final TunerCastCallback callback;

    private State state = State.IDLE;
    private FocusEntry current;
    private int warmupTicksRemaining;
    private int castTicksElapsed;
    private ItemStack wandSnapshot = ItemStack.EMPTY;

    // ================= 【0.0.19】「长按持续释放」类法术（IChargingSpell）的通道状态 =================

    /**
     * 本次施法是否为「长按持续释放」型（{@link com.Polarice3.Goety.api.magic.IChargingSpell}）。
     *
     * <p>用户报的 bug：腐化 / 震撼 / 炼狱这类聚晶在调律师身上**只放一瞬间就停了**（被当成瞬发用了）。
     * 根因有两层，见 {@link #tickChannel} 与 {@code TunerWand} 的类注释。
     */
    private boolean channeled;
    /** 蓄力到第几 tick 才开始真正放（来自 {@code IChargingSpell#castUp}）。 */
    private int channelChargeTicks;
    /** 总时长上限对应的 {@code castTicksElapsed} 值（到点即结束——用户要求的"用时上限"）。 */
    private int channelEndTick;
    /** 距下一次开火还有几 tick（来自 {@code IChargingSpell#Cooldown}）。 */
    private int channelFireTimer;
    /** 本次通道已经放了几发。 */
    private int channelShots;

    /**
     * 单次通道最多开火次数（防御性硬上限；正常由时长/时长上限/法术自己的 {@code shotsNumber} 收口）。
     */
    private static final int MAX_CHANNEL_SHOTS = 400;

    /**
     * 【0.0.12】本次施法是否已经发出过 {@code onCastStart}。
     *
     * <p>回调必须**严格成对**：{@code TunerBoss} 的施法状态计数（`DATA_CAST_STATE` 蹲姿）与
     * 0.0.10 新增的立方体类别位掩码都靠 start/end 配对增减。若发出 start 后异常逃逸且无人补发 end，
     * 计数会**永久 > 0** ⇒ 对应立方体一直高亮、蹲姿卡住；而 {@code TunerBoss} 那边用
     * `FocusEntry` 身份集合做幂等去重，会让这个 `entry` 之后**再也无法计入**，状态无法自愈。
     *
     * <p>{@code startCast} 已把 {@code onCastStart} 挪到 `return true` 前的最后一步（结构上无法再被
     * 后续代码打断），本标志是第二道保险：万一将来又有人在它后面加代码抛异常，
     * {@code beginCast} 的兜底 catch 会据此补发一次 {@code onCastFailed} 让计数平衡。
     */
    private boolean startEmitted;

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

                if (channeled) {
                    // 【0.0.19】「长按持续释放」类法术：不是"蓄力一次然后放一发"，
                    // 而是"蓄力到点后按法术自己的节奏反复释放，直到用时上限"。
                    tickChannel(level, boss, spell, stats);
                } else if (--warmupTicksRemaining <= 0) {
                    // 前摇结束：结算法术效果（直接调 SpellResult，等价于 Spell.mobSpellResult 的服务端路径）
                    spell.SpellResult(level, boss, boss.getMainHandItem(), stats);
                    finishCast();
                }
            } catch (Throwable t) {
                // 【第二十六轮】自愈：聚晶需要玩家来源等情况会抛异常，捕获后拉黑该聚晶并归还池
                GoetyTuner.LOGGER.error("[Tuner] Spell {} threw exception during cast (likely needs player source). "
                        + "Auto-blacklisting focus {}.", current.getItemId(), current.getItemId(), t);
                FocusPoolManager.runtimeBlacklist(current.getItemId().toString());
                // 【0.0.19】通道型法术在中途抛异常时也必须松手 + 复位通道标志，
                // 否则施法者会永久保持"使用中"、且腐化光束这类实体会永远不消失。
                stopChannelUse(boss);
                callback.onCastFailed(current);
                callback.pools().returnEntry(current);
                current = null;
                state = State.IDLE;
                channeled = false;
            }
        }
    }

    /** 抽取并开始一次施法（含前摇）；返回是否成功开始 */
    public boolean beginCast(ServerLevel level, double minionFill, boolean summonBlocked, double warmupMultiplier) {
        if (state != State.IDLE) {
            return false;
        }
        startEmitted = false; // 【0.0.12】新一轮施法：清掉上一轮的成对性标记
        FocusEntry entry = category.isScored()
                ? callback.pools().draw(category, minionFill, summonBlocked)
                : callback.pools().drawUniform(category);
        if (entry == null) {
            return false;
        }
        return tryStartCast(level, entry, warmupMultiplier);
    }

    /**
     * 【0.0.18】用**指定**聚晶起手（仆从的「优先释放」指令）。
     *
     * <p>与 {@link #beginCast} 的唯一区别是"聚晶不是抽出来的，而是调用方点名的"：
     * 抽签路径（轮盘赌权重 / 均匀随机）整个被跳过，其余流程（conditionsMet → 装配法杖 →
     * 锁池 → startSpell → 锁朝向 → 音效 → onCastStart）**完全共用** {@link #startCast}。
     *
     * <p>本方法不会校验 {@code entry} 是否属于本通道的 {@code category}——
     * 调用方应当把条目路由到**它自己类别**的通道上（{@code entry.getCategory()} 决定），
     * 否则 {@code tick()} 里"攻击类蓄力中目标丢失则打断"的判定会张冠李戴。
     *
     * @param entry 必须来自 {@code callback.pools()}（否则锁池/冷却记账会对不上）
     */
    public boolean beginCast(ServerLevel level, FocusEntry entry, double warmupMultiplier) {
        if (state != State.IDLE || entry == null) {
            return false;
        }
        startEmitted = false;
        return tryStartCast(level, entry, warmupMultiplier);
    }

    /**
     * 【0.0.8 健壮性】把整条施法流程统一兜底。
     *
     * <p>附属模组的法术实现（ISpell）可能在 conditionsMet / castDuration / startSpell /
     * CastingSound / castingVolume 等**任一处**抛异常；原实现只 try 了 startSpell，
     * 其余几步抛出的异常会沿 tickBuildup/tickClimax → aiStep → serverAiStep 一路冒泡，
     * **直接崩服**（整合包里换一批附属模组就可能触发）。
     * 现在任一步抛异常都按"该聚晶不可用"处理：运行期拉黑 + 归还功能池（幂等）+ 复位通道。
     */
    private boolean tryStartCast(ServerLevel level, FocusEntry entry, double warmupMultiplier) {
        try {
            return startCast(level, entry, warmupMultiplier);
        } catch (Throwable t) {
            GoetyTuner.LOGGER.error("[Tuner] beginCast failed for focus {} ({}); auto-blacklisting",
                    entry.getItemId(), t.toString(), t);
            FocusPoolManager.runtimeBlacklist(entry.getItemId().toString());
            // 【0.0.12】回调成对性：若 startCast 已经发出 onCastStart（异常发生在它之后），
            // 这里必须补发一次结束回调，否则宿主侧的施法状态计数与立方体类别掩码会永久 > 0。
            // （startCast 已把 onCastStart 放到 return 前最后一步，所以正常不会走到这里；
            //  这是防将来有人在它后面加代码的第二道保险。）
            if (startEmitted) {
                startEmitted = false;
                try {
                    callback.onCastFailed(entry);
                } catch (Throwable cb) {
                    GoetyTuner.LOGGER.error("[Tuner] onCastFailed (balancing) threw for {}",
                            entry.getItemId(), cb);
                }
            }
            callback.pools().returnEntry(entry); // 幂等：已归还/未锁池时不会重复添加
            // 【0.0.19】异常逃逸时若已经是通道型施法，必须松手（否则施法者永远"使用中"、
            // 腐化光束这类实体也永远不会消失）
            stopChannelUse(callback.boss());
            current = null;
            state = State.IDLE;
            channeled = false;
            return false;
        }
    }

    /**
     * 【0.0.19】「长按持续释放」类法术（{@link IChargingSpell}）的每 tick 推进。
     *
     * <h2>用户报的 bug 与它的两层根因</h2>
     * <p>现象：**腐化 / 震撼 / 炼狱**这类"长按持续释放"的聚晶，在调律师（以及仆从）身上
     * 只放一瞬间就停了 —— 被当成瞬发法术用了。反编译 Goety 2.5.56.5 后定位到两层原因：
     *
     * <ol>
     *   <li><b>只结算一次</b>：玩家的施法路径（{@code DarkWand.onUseTick}）对
     *       {@code IChargingSpell} 是"蓄力到 {@code castUp} 之后，每 {@code Cooldown} tick 调一次
     *       {@code MagicResults}（→ {@code SpellResult}）"，一直持续到松手；
     *       而本通道原先只在前摇结束时调**一次** {@code SpellResult}。于是轰炸/雷电/暴雪这类
     *       "每发生成一个实体"的法术只出了一发。</li>
     *   <li><b>实体下一 tick 就自毁</b>：腐化光束（{@code CorruptedBeam}）继承自
     *       {@code AbstractBeam}，它的 tick 里有
     *       {@code if (itemBase && !MobUtil.isSpellCasting(owner)) discard();}，
     *       而 {@code isSpellCasting} = {@code isUsingItem() && 使用物 instanceof IWand && 法杖里有聚晶}。
     *       **Mob 从不 startUsingItem** ⇒ 光束刚生成就被丢弃 ⇒ 玩家看到的就是"闪一下"。</li>
     * </ol>
     *
     * <p>因此本方法做两件事：① 用 {@code startUsingItem(MAIN_HAND)} 让施法者**真的在"使用法杖"**
     * （为此本模组自备 {@code TunerWand}，它没有任何 Item 层副作用，见其类注释）；
     * ② 按法术自己的 {@code Cooldown} / {@code shotsNumber} 节奏反复释放。
     *
     * <h2>用时上限（用户明确要求"给一个用时上限，防止它停不下来"）</h2>
     * <p>上限 = {@code casting.channelMaxTicks}（默认 **20 tick = 1 秒**），到点必然
     * {@code finishCast()}（会 {@code stopUsingItem}）。三条独立的收口条件，谁先到算谁：
     * ① 总时长到上限；② 法术自己的 {@code shotsNumber}（&gt;0 时）放完；
     * ③ {@link #MAX_CHANNEL_SHOTS} 防御性硬上限。
     *
     * <p>⚠️ 顺带回答另一个常见疑问：老配置项 {@code casting.maxCastWindowTicks}（默认 50）
     * 此前**只**用于"把前摇截断到 2.5 秒"（这也是为什么长按类法术以前会站桩 2.5 秒才放一发），
     * 它并不是"持续释放的时长上限"——持续释放的时长上限是本轮新增的
     * {@code casting.channelMaxTicks}。
     */
    private void tickChannel(ServerLevel level, LivingEntity caster, com.Polarice3.Goety.api.magic.ISpell spell,
                             com.Polarice3.Goety.common.magic.SpellStat stats) {
        if (!(spell instanceof com.Polarice3.Goety.api.magic.IChargingSpell charging)) {
            // 理论上不可达（channeled 就是这么判定的）；兜底走普通路径，绝不把通道卡死
            if (--warmupTicksRemaining <= 0) {
                spell.SpellResult(level, caster, caster.getMainHandItem(), stats);
                finishCast();
            }
            return;
        }
        // ① 维持"正在使用法杖"：被打断（受伤、其它模组 stopUsingItem 等）时自愈。
        //    这是 AbstractBeam 那类"以法杖为基准"的实体存活的前提。
        if (!caster.isUsingItem()) {
            caster.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        // ② 蓄力满之后按法术自己的节奏反复释放
        if (castTicksElapsed >= channelChargeTicks && --channelFireTimer <= 0) {
            int interval;
            try {
                interval = charging.Cooldown(caster, caster.getMainHandItem(), channelShots);
            } catch (Throwable t) {
                interval = 1;
            }
            channelFireTimer = Math.max(1, interval);
            spell.SpellResult(level, caster, caster.getMainHandItem(), stats);
            ++channelShots;
            // ③ 防御性硬上限（正常永远先被"持续时长上限"收口）
            //
            // ⚠️【0.0.19 修正】这里**故意不再**用 `IChargingSpell#shotsNumber` 提前结束通道。
            // 起因（用户反馈）：箭雨聚晶（`ArrowRainSpell extends EverChargeSpell`，`shotsNumber` =
            // `SpellConfig.ArrowRainDuration` 默认 **100**）实测"几乎没有持续"。
            // 反编译核对玩家侧 `DarkWand#onUseTick` 后发现：**它从不因 shotsNumber 停止开火** ——
            // shotsNumber 只被用来 (a) 给 `ShotsFired` 计数、(b) 松开右键时按比例算冷却
            //（`releaseUsing` 里 `coolPercent = ShotsFired / shotsNumber`）。
            // 也就是说玩家按住就一直放、放多少发只影响之后的冷却 ⇒ 本模组若按 shotsNumber 截断，
            // 就会比玩家**更早停手**，与"忠实模拟玩家长按"的目标相悖。真正的收口只有下面那条时长上限。
            if (channelShots >= MAX_CHANNEL_SHOTS) {
                finishCast();
                return;
            }
        }
        // ④ 持续释放时长上限（`casting.channelMaxTicks`；**不含前面的蓄力**，见 startCast 的说明）
        if (castTicksElapsed >= channelEndTick) {
            finishCast();
        }
    }

    /** beginCast 的实际流程（由 beginCast 统一兜底异常） */
    private boolean startCast(ServerLevel level, FocusEntry entry, double warmupMultiplier) {
        LivingEntity boss = callback.boss();
        FocusPoolManager pools = callback.pools();
        var spell = entry.getSpell();
        if (spell == null) {
            return false;
        }
        channeled = false; // 【0.0.19】每次起手先归零，避免上一轮的通道状态泄漏到本轮
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

        // 【0.0.19】分两条路：普通法术 = 前摇一次然后放一发；「长按持续释放」法术 = 蓄力后连续放。
        // 判定依据是 Goety 自己的 IChargingSpell（腐化/震撼/暴雪/轰炸/旋风…全是它的子类）。
        channeled = spell instanceof com.Polarice3.Goety.api.magic.IChargingSpell;
        if (channeled) {
            var charging = (com.Polarice3.Goety.api.magic.IChargingSpell) spell;
            // ⚠️【0.0.19 修正 · 关键】蓄力与"持续释放"是**两段互不重叠**的时间，必须分别给预算：
            //     总时长 = 蓄力(channelChargeTicks) + 持续(channelMaxTicks) = channelEndTick
            //
            // 起因（用户反馈）：箭雨聚晶实测"几乎没有持续"。
            // 根因是我第一版把它写成"总时长 = channelMaxTicks、蓄力从里面扣"，于是
            // `ArrowRainSpell`（`castUp` = `SpellConfig.ArrowRainChargeUp` 默认 **20**）在
            // `channelMaxTicks` 默认 **20** 的情况下 ⇒ 蓄力就吃掉 19 tick、只剩 1 tick 开火
            // ⇒ **整整一发**，看起来就是"闪一下"。
            // 玩家侧的真实语义是：`castUp` 决定"按住多久才开始放"，之后**一直放**直到松手
            // （`DarkWand#onUseTick` 每 tick 走一遍 `COOL >= Cooldown` 判定）⇒ 两段相加才对。
            int sustain = Math.max(5, TunerCommonConfig.CHANNEL_MAX_TICKS.get());
            // 蓄力取法术自己的 castUp，并吃高潮/二阶段的前摇倍率；**单独**用老的
            // maxCastWindowTicks 封顶（避免某个法术 castUp 极大时站桩过久 —— 那是老键的本职）。
            int up = 0;
            try {
                up = charging.castUp(boss, boss.getMainHandItem());
            } catch (Throwable t) {
                GoetyTuner.LOGGER.error("[Tuner] castUp threw for {} (assuming 0).", entry.getItemId(), t);
            }
            channelChargeTicks = (int) Math.max(0L, Math.min(
                    (long) TunerCommonConfig.MAX_CAST_WINDOW_TICKS.get(),
                    Math.round(up * warmupMultiplier)));
            channelEndTick = channelChargeTicks + sustain;
            channelFireTimer = 0;
            channelShots = 0;
            warmupTicksRemaining = sustain; // 仅作兜底路径使用
            // 让施法者**真的开始"使用法杖"**：AbstractBeam 那类实体靠
            // MobUtil.isSpellCasting(caster) 判定存活（详细理由见 tickChannel 的 javadoc）。
            boss.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
            GoetyTuner.LOGGER.debug("[Tuner] Channelled cast {} (charge={}, sustain={}, total={})",
                    entry.getItemId(), channelChargeTicks, sustain, channelEndTick);
        } else {
            // 【2026-08-18 第十三轮】蓄力时长封顶：Goety castDuration 默认 5-10 秒甚至 300 秒，
            // boss 照搬会站桩蓄力过久。截断到 maxCastWindowTicks（【第二十六轮起】默认50=2.5秒），
            // 保底10tick(0.5秒)。
            // 玩家提前松手（releaseUsing→MagicResults）是合法释放路径，提前结算安全。
            int raw = (int) Math.max(1, spell.castDuration(boss, boss.getMainHandItem()) * warmupMultiplier);
            int window = TunerCommonConfig.MAX_CAST_WINDOW_TICKS.get();
            int warmup = Math.min(raw, window);
            if (warmup < 10) {
                warmup = Math.min(10, Math.max(1, raw)); // 不强制拉长原本更短的前摇
            }
            warmupTicksRemaining = warmup;
        }
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
            // 【第三十一轮】此路径施法尚未开始（onCastStart 尚未调用），**不得**触发 onCastFailed 回调——
            // TunerBoss 的施法状态计数器 activeWarmups 只在 onCastStart 里递增，而 onCastFailed 会走
            // castEnded() 无条件自减。并行高潮通道下若 A 通道正在前摇、B 通道在此抛异常，
            // B 的自减会把计数提前打到 0 → DATA_CAST_STATE 误清 0 → A 的蹲姿/瞄准提前收势。
            // 【0.0.4 修复】此前代码与注释相反，确实调用了 onCastFailed（历史缺陷），现已移除；
            // 施法未开始故也无需 castEnded 平衡：此处只做拉黑 + 归还聚晶 + 复位。
            GoetyTuner.LOGGER.error("[Tuner] Spell {} threw on startSpell. Auto-blacklisting focus {}.",
                    spell.getClass().getSimpleName(), current.getItemId(), t);
            FocusPoolManager.runtimeBlacklist(current.getItemId().toString());
            callback.pools().returnEntry(current);
            // 【0.0.19】通道型法术可能已经 startUsingItem：这里必须松手，否则施法者会一直"使用中"
            stopChannelUse(boss);
            current = null;
            state = State.IDLE;
            channeled = false;
            return false;
        }
        var sound = spell.CastingSound(boss);
        if (sound != null) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), sound,
                    net.minecraft.sounds.SoundSource.HOSTILE, spell.castingVolume(), spell.castingPitch());
        }
        // 【0.0.12】先记日志，再发 onCastStart —— 顺序很关键：
        // logCast 也是会调第三方/IO 的一步，原先它排在 onCastStart **之后**，一旦它抛异常，
        // 异常会逃逸到 beginCast 的兜底 catch，而那里不补发结束回调 ⇒ onCastStart 成了"孤儿回调"，
        // TunerBoss 的施法状态计数与立方体类别掩码永久 > 0（立方体一直亮、蹲姿卡住），
        // 且身份集合幂等会让该聚晶之后再也无法计入。调换顺序后 onCastStart 是 return 前最后一步，
        // **结构上不可能**再被后续代码打断。（beginCast 的 startEmitted 兜底是第二道保险。）
        BossWandHelper.logCast(level, boss, entry);
        callback.onCastStart(entry);
        startEmitted = true;
        return true;
    }

    private void finishCast() {
        LivingEntity boss = callback.boss();
        // 【0.0.19】「长按持续释放」类法术结束：必须让施法者松手（stopUsingItem）。
        // 否则一来 isSpellCasting 会一直为真（腐化光束这类"以法杖为基准"的实体会永远不消失），
        // 二来施法者会一直保持"使用中"的同步状态。
        stopChannelUse(boss);
        // 【0.0.8 健壮性】spellCooldown 也是第三方法术实现；它抛异常时不能让结算中断——
        // 否则聚晶留在锁池状态（既不在功能池也没进冷却池）永久丢失。失败则退化为纯额外冷却。
        int cooldown;
        try {
            cooldown = current.getSpell().spellCooldown(boss)
                    + TunerCommonConfig.EXTRA_CAST_COOLDOWN.get();
        } catch (Throwable t) {
            GoetyTuner.LOGGER.error("[Tuner] spellCooldown threw for {} (using extra cooldown only).",
                    current.getItemId(), t);
            cooldown = TunerCommonConfig.EXTRA_CAST_COOLDOWN.get();
        }
        callback.pools().moveToCooldown(current, cooldown);
        callback.onCastFinish(current);
        current = null;
        state = State.IDLE;
        channeled = false;
    }

    /**
     * 【0.0.19】结束"长按"状态：让施法者松手。
     *
     * <p>只在本次施法确实是通道型时才动手 —— 普通法术从不 {@code startUsingItem}，
     * 无条件 {@code stopUsingItem} 可能误伤别的模组让 Boss 正在使用的物品
     * （例如某个附属给 Boss 挂的引导型道具）。
     */
    private void stopChannelUse(LivingEntity caster) {
        if (!channeled) {
            return;
        }
        if (caster.isUsingItem()) {
            caster.stopUsingItem();
        }
    }

    /** 强制打断（进入高潮/低谷时由阶段切换调用 / 蓄力中目标丢失） */
    public void interrupt(ServerLevel level) {
        if (state == State.WARMUP && current != null) {
            LivingEntity boss = callback.boss();
            var spell = current.getSpell();
            // 【0.0.8 健壮性】stopSpell 也是第三方法术实现，同样可能抛异常；
            // 打断路径必须无论如何都把聚晶归还、把通道复位（否则聚晶永远锁在池外）。
            try {
                spell.stopSpell(level, boss, boss.getMainHandItem(),
                        com.Polarice3.Goety.api.items.magic.IWand.getFocus(boss.getMainHandItem()),
                        castTicksElapsed, WandUtil.getStats(boss, spell));
            } catch (Throwable t) {
                GoetyTuner.LOGGER.error("[Tuner] Spell {} threw on stopSpell (ignored, cast still released).",
                        spell == null ? "?" : spell.getClass().getSimpleName(), t);
            }
            // 【0.0.19】通道型法术被打断同样要松手（否则腐化光束会一直挂在那儿）
            stopChannelUse(boss);
            callback.onCastInterrupted(current);
            // 【2026-08-18 第十三轮】归还功能池（无冷却）：打断不应惩罚聚晶
            callback.pools().returnEntry(current);
            GoetyTuner.LOGGER.debug("[Tuner] Cast interrupted: {}", current.getItemId());
        }
        current = null;
        state = State.IDLE;
        channeled = false;
    }

    /** 无前摇瞬发（重音触发时使用：其他类聚晶） */
    public boolean instantCast(ServerLevel level, FocusCategory category) {
        if (state != State.IDLE) {
            return false;
        }
        FocusEntry entry = callback.pools().drawUniform(category);
        if (entry == null) {
            return false;
        }
        // 【0.0.8 健壮性】同 beginCast：conditionsMet / installFocus / spellCooldown 都可能由
        // 附属模组的法术实现抛异常，统一兜底而不是让异常冒到 AI tick 上崩服。
        try {
            return instantCastInternal(level, entry);
        } catch (Throwable t) {
            GoetyTuner.LOGGER.error("[Tuner] instantCast failed for focus {} ({}); auto-blacklisting",
                    entry.getItemId(), t.toString(), t);
            FocusPoolManager.runtimeBlacklist(entry.getItemId().toString());
            callback.pools().returnEntry(entry);
            return false;
        }
    }

    /** instantCast 的实际流程（由 instantCast 统一兜底异常） */
    private boolean instantCastInternal(ServerLevel level, FocusEntry entry) {
        LivingEntity boss = callback.boss();
        var spell = entry.getSpell();
        if (spell == null || !spell.conditionsMet(level, boss)) {
            return false;
        }
        BossWandHelper.installFocus(boss.getMainHandItem(), entry, null);
        // 【第三十一轮】瞬发同样钉朝向：重音瞬发常紧跟走位瞬移，直接按当前视线发射会打偏
        snapTowardTarget(boss);
        spell.SpellResult(level, boss, boss.getMainHandItem(), WandUtil.getStats(boss, spell));
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
     * <p>处理：共三个调用点——① 前摇期间每 tick；② <b>beginCast 内调用 spell.startSpell 之前</b>
     * （VoidRift/FlameStrike/AbyssalBeam/ChipRain 等法术在 startSpell 内就沿视线 rayTrace 生成实体，
     * 此时若不钉朝向，无任何瞬移干扰也会朝旧视线放出）；③ 瞬发（instantCast）结算前。
     * 三处都把 yRot/yHeadRot/yBodyRot/xRot 直接设为指向目标（头部俯仰瞄准目标眼睛，空中目标也能朝上打）。
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

    public FocusCategory getCategory() {
        return category;
    }

    public boolean isBusy() {
        return state != State.IDLE;
    }
}
