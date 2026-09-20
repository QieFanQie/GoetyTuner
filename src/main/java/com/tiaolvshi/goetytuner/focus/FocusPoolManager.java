/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.focus;

import com.Polarice3.Goety.api.items.magic.IFocus;
import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.config.TunerCommonConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 聚晶池管理器。
 *
 * 全局单例负责“扫描 + 静态分类”；每个Boss实例调用 {@link #createFightPools()}
 * 生成一份战斗用池拷贝（含冷却状态、动态偏移），保证多只boss互不干扰。
 *
 * 池结构（有序列表，便于遍历和轮盘赌）：
 *   functionalPools:  ATTACK/DEFENSE/SUMMON/OTHER 四个功能聚晶池
 *   cooldownPools:    对应四个冷却聚晶池；施法完成后进入冷却池，计时结束回归功能池
 *
 * 初始化时机：所有模组注册完成后（ServerStartingEvent / 首次实体生成时懒加载）。
 */
public class FocusPoolManager {

    // ================= 全局静态部分 =================

    private static volatile boolean initialized = false;
    private static final List<FocusEntry> ALL_ENTRIES = new ArrayList<>();
    private static final EnumMap<FocusCategory, List<FocusEntry>> STATIC_POOLS = new EnumMap<>(FocusCategory.class);
    private static FocusClassificationConfig classification;
    /** 【第二十六轮】运行期自愈拉黑集合：施法抛异常的聚晶 id 临时加入，避免反复崩溃 */
    private static final Set<String> RUNTIME_BLACKLIST = new HashSet<>();

    /** 判断聚晶 id 是否被拉黑（配置黑名单 ∪ 运行期自愈黑名单） */
    public static boolean isBlacklisted(String itemId) {
        if (itemId == null) {
            return false;
        }
        if (RUNTIME_BLACKLIST.contains(itemId)) {
            return true;
        }
        // 【第二十八轮】配置黑名单改为 String + 读取时自动规范化（单id/逗号分隔/数组格式）
        return TunerCommonConfig.getBlacklist().contains(itemId);
    }

    /** 运行期自愈：把抛异常的聚晶临时拉黑（仅本会话，重启按配置黑名单走） */
    public static void runtimeBlacklist(String itemId) {
        if (itemId != null && RUNTIME_BLACKLIST.add(itemId)) {
            GoetyTuner.LOGGER.error("[Tuner] Focus {} auto-blacklisted (cast threw exception). "
                    + "Add it to config 'focus.blacklist' to suppress permanently.", itemId);
            // 从静态池移除（已生成的战斗池实例不退，但本聚晶不会再被新战斗抽取）
            for (FocusCategory c : FocusCategory.values()) {
                STATIC_POOLS.get(c).removeIf(e -> itemId.equals(e.getItemId().toString()));
            }
        }
    }

    /** 懒加载：服务器启动后或首次需要时扫描全部注册聚晶 */
    public static synchronized void initIfNeeded() {
        if (initialized) {
            return;
        }
        ALL_ENTRIES.clear();
        STATIC_POOLS.clear();
        for (FocusCategory c : FocusCategory.values()) {
            STATIC_POOLS.put(c, new ArrayList<>());
        }

        // 扫描所有注册物品中的 IFocus（含诡厄巫法本体与任何附属）
        // 【0.0.8 健壮性】单个物品的扫描整体兜底：附属模组的 IFocus 实现可能在
        // getSpell()/构造 FocusEntry 时抛异常（版本不匹配、依赖缺失等）。
        // 原先这种异常会让整个 initIfNeeded 失败 → 聚晶池为空 + 服务器启动报错；
        // 现在跳过该物品并记日志，其余聚晶照常可用。
        int skipped = 0;
        for (Item item : ForgeRegistries.ITEMS) {
            try {
                if (item instanceof IFocus focus && focus.getSpell() != null) {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                    if (id != null) {
                        // 【第二十六轮】跳过配置黑名单中的聚晶（需要玩家来源/会崩溃的）
                        if (isBlacklisted(id.toString())) {
                            GoetyTuner.LOGGER.info("[Tuner] Skipping blacklisted focus: {}", id);
                            continue;
                        }
                        ALL_ENTRIES.add(new FocusEntry(id, focus));
                    }
                }
            } catch (Throwable t) {
                skipped++;
                GoetyTuner.LOGGER.error("[Tuner] Skipping focus item {} — scan threw {}",
                        ForgeRegistries.ITEMS.getKey(item), t.toString());
            }
        }
        if (skipped > 0) {
            GoetyTuner.LOGGER.warn("[Tuner] {} focus item(s) skipped during scan (broken addon implementations)", skipped);
        }

        // 应用静态分类与评分（配置文件缺失则生成默认示例）
        classification = FocusClassificationConfig.load();
        try {
            classification.applyTo(ALL_ENTRIES);
        } catch (Throwable t) {
            // 分类阶段异常（第三方法术类在 instanceof ISummonSpell / 描述解析时抛错）
            // 不应让整个池子建不起来：保留默认分类继续
            GoetyTuner.LOGGER.error("[Tuner] Focus classification failed; keeping default categories", t);
        }
        for (FocusEntry e : ALL_ENTRIES) {
            List<FocusEntry> bucket = STATIC_POOLS.get(e.getCategory());
            if (bucket != null) {
                bucket.add(e);
            }
        }

        initialized = true;
        GoetyTuner.LOGGER.info("[Tuner] Focus scan complete: {} foci -> attack:{} defense:{} summon:{} other:{}",
                ALL_ENTRIES.size(),
                STATIC_POOLS.get(FocusCategory.ATTACK).size(),
                STATIC_POOLS.get(FocusCategory.DEFENSE).size(),
                STATIC_POOLS.get(FocusCategory.SUMMON).size(),
                STATIC_POOLS.get(FocusCategory.OTHER).size());

        // 【0.0.19】把「长按持续释放」类聚晶的**自动检测结果**打出来（可观测性）。
        //
        // 背景：腐化 / 震撼 / 炼狱这类法术前一阵只放一瞬间就停，修法见 CastChannel#tickChannel。
        // 该修复的判定只有一条 `spell instanceof IChargingSpell` —— 它靠的是 Goety 自己的接口
        // **继承闭包**，因此本体与任意附属的同类法术都会被自动覆盖，**不写死任何聚晶 id**。
        // 这条日志就是这个"自动检测"的现场证据：启动时数一遍、并给几个样例。
        // （实测本机整合包：本体 19 个具体法术 + 觉醒 4 + 阶梯 2 + 灾变 1 = 26 个具体法术落在闭包内。）
        logChanneledFoci();
    }

    /** 【0.0.19】统计并记录自动检测到的「长按持续释放」类聚晶（仅启动/重扫时跑一次）。 */
    private static void logChanneledFoci() {
        int count = 0;
        StringBuilder sample = new StringBuilder();
        for (FocusEntry e : ALL_ENTRIES) {
            if (e.getSpell() instanceof com.Polarice3.Goety.api.magic.IChargingSpell) {
                ++count;
                if (sample.length() < 160) {
                    if (sample.length() > 0) {
                        sample.append(", ");
                    }
                    sample.append(e.getItemId());
                }
            }
        }
        GoetyTuner.LOGGER.info("[Tuner] Channelled (IChargingSpell) foci auto-detected: {} / {} — e.g. {}",
                count, ALL_ENTRIES.size(), count == 0 ? "(none)" : sample);
    }

    public static List<FocusEntry> allEntries() {
        initIfNeeded();
        return ALL_ENTRIES;
    }

    public static FocusClassificationConfig classification() {
        initIfNeeded();
        return classification;
    }

    /**
     * 【0.0.14】黑名单热刷新：配置界面改完 `focus.blacklist` 后立即让改动生效（两个方向都生效）。
     *
     * <p>为什么需要它：{@link #initIfNeeded()} 在扫描时**直接 `continue` 跳过**黑名单里的聚晶，
     * 它们根本不进 {@code ALL_ENTRIES}。于是"**新增**拉黑"能靠 `draw()/drawUniform()` 的实时过滤
     * 立刻生效，但"**取消**拉黑"必须重新扫描才会回来——而上次的实现在重扫时会**new 出全新的
     * `FocusEntry` 对象**。
     *
     * <p>重扫本身是安全的：本方法**复用已存在的 `FocusEntry` 对象**（按 `namespace:path` 建索引），
     * 只为"这次才被解禁"的聚晶新建对象。这样实体侧那些**按对象身份**记录的状态
     * （`TunerBoss.activeVisualCasts` 身份集合、正在施法的 `CastChannel.current`）不会失效——
     * 否则会出现"重新分类后立方体高亮卡住 / 施法收尾回调对不上"这类隐蔽问题。
     * 被解禁的聚晶此前不可能在施法中，所以它们新建对象是安全的。
     */
    public static synchronized void refreshBlacklist() {
        Map<String, FocusEntry> existing = new HashMap<>();
        for (FocusEntry e : ALL_ENTRIES) {
            existing.put(e.getItemId().toString(), e);
        }
        List<FocusEntry> rebuilt = new ArrayList<>();
        int skipped = 0;
        int reused = 0;
        for (Item item : ForgeRegistries.ITEMS) {
            try {
                if (item instanceof IFocus focus && focus.getSpell() != null) {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                    if (id == null || isBlacklisted(id.toString())) {
                        continue;
                    }
                    FocusEntry old = existing.get(id.toString());
                    if (old != null) {
                        rebuilt.add(old); // 复用：保持对象身份
                        reused++;
                    } else {
                        rebuilt.add(new FocusEntry(id, focus)); // 刚被解禁
                    }
                }
            } catch (Throwable t) {
                skipped++;
                GoetyTuner.LOGGER.error("[Tuner] refreshBlacklist: skipping focus item {} — {}",
                        ForgeRegistries.ITEMS.getKey(item), t.toString());
            }
        }
        ALL_ENTRIES.clear();
        ALL_ENTRIES.addAll(rebuilt);
        try {
            classification.applyTo(ALL_ENTRIES);
        } catch (Throwable t) {
            GoetyTuner.LOGGER.error("[Tuner] refreshBlacklist: classification failed, keeping defaults", t);
        }
        STATIC_POOLS.values().forEach(List::clear);
        for (FocusEntry e : ALL_ENTRIES) {
            STATIC_POOLS.get(e.getCategory()).add(e);
        }
        GoetyTuner.LOGGER.info("[Tuner] Blacklist refreshed: {} foci active (reused {}, skipped {})",
                ALL_ENTRIES.size(), reused, skipped);
        logChanneledFoci(); // 【0.0.19】重扫后同样刷新一次"长按类"检测结果
    }

    /** 重新加载配置文件并重新分类（LLM自动配置完成后调用） */
    public static synchronized void reclassify() {
        classification = FocusClassificationConfig.load();
        classification.applyTo(ALL_ENTRIES);
        STATIC_POOLS.values().forEach(List::clear);
        for (FocusEntry e : ALL_ENTRIES) {
            STATIC_POOLS.get(e.getCategory()).add(e);
        }
    }

    // ================= 每场战斗实例部分 =================

    /** 冷却票据：到点后聚晶回归功能池 */
    private static class CooldownTicket {
        final FocusEntry entry;
        int ticksRemaining;

        CooldownTicket(FocusEntry entry, int ticks) {
            this.entry = entry;
            this.ticksRemaining = ticks;
        }
    }

    private final EnumMap<FocusCategory, List<FocusEntry>> pools = new EnumMap<>(FocusCategory.class);
    private final EnumMap<FocusCategory, List<CooldownTicket>> cooldownPools = new EnumMap<>(FocusCategory.class);
    private final RandomSource random = RandomSource.create();

    // ================= 【0.0.18】仆从个体指令：禁放 / 优先 =================
    //
    // 用户需求：「调律师仆从」在被玩家手持某聚晶左键/右键时，分别被设为
    // **不释放** / **优先释放** 那个聚晶。
    //
    // 这两张表**故意放在实例上**（不是 static），理由与 0.0.16 的 castCount 完全一致：
    // pools 由 {@link #createFightPools()} 每只仆从各建一份，FocusEntry 也是实例级复制，
    // 所以"玩家对这只仆从下达的指令"天然只作用于这一只——
    // 若做成 static，对一只仆从的指令会连带影响场上所有调律师与仆从（严重错误）。

    /** 该个体**禁放**的聚晶 id（玩家左键设置）。 */
    private final Set<String> disabledFoci = new HashSet<>();

    /**
     * 该个体**优先释放**的聚晶 id（玩家右键设置）。
     * 用 LinkedHashSet 保留玩家设定的先后顺序，多只被优先时按设定顺序依次尝试（先设的先放）。
     */
    private final Set<String> priorityFoci = new LinkedHashSet<>();

    /**
     * 设置/取消「禁放」。返回 true 表示状态**发生了变化**（供调用方决定要不要提示玩家）。
     *
     * <p>同一个聚晶同时被"优先"和"禁放"时，**禁放优先**——禁放是明确的否决，
     * 且 {@link #draw}/{@link #drawUniform}/{@link #takeSpecific} 全部先过 {@link #isUsable}。
     */
    public boolean setFocusDisabled(String itemId, boolean disabled) {
        if (itemId == null) {
            return false;
        }
        boolean changed = disabled ? disabledFoci.add(itemId) : disabledFoci.remove(itemId);
        if (changed) {
            GoetyTuner.LOGGER.info("[Tuner] Servant focus {} -> {}",
                    itemId, disabled ? "DISABLED" : "not disabled");
        }
        return changed;
    }

    /** 该聚晶是否被本个体禁放。 */
    public boolean isFocusDisabled(String itemId) {
        return itemId != null && disabledFoci.contains(itemId);
    }

    /** 设置/取消「优先释放」。返回 true 表示状态发生了变化。 */
    public boolean setFocusPriority(String itemId, boolean priority) {
        if (itemId == null) {
            return false;
        }
        boolean changed = priority ? priorityFoci.add(itemId) : priorityFoci.remove(itemId);
        if (changed) {
            GoetyTuner.LOGGER.info("[Tuner] Servant focus {} -> {}",
                    itemId, priority ? "PRIORITY" : "not priority");
        }
        return changed;
    }

    /** 该聚晶是否被本个体设为优先释放。 */
    public boolean isFocusPriority(String itemId) {
        return itemId != null && priorityFoci.contains(itemId);
    }

    /** 优先释放表（只读视图，按玩家设定的顺序）。 */
    public Set<String> priorityFoci() {
        return java.util.Collections.unmodifiableSet(priorityFoci);
    }

    /** 禁放表（只读视图）。 */
    public Set<String> disabledFoci() {
        return java.util.Collections.unmodifiableSet(disabledFoci);
    }

    /** 从存档恢复（{@code TunerServant#readAdditionalSaveData} 调用）。 */
    public void restoreFocusCommands(java.util.Collection<String> disabled, java.util.Collection<String> priority) {
        disabledFoci.clear();
        priorityFoci.clear();
        if (disabled != null) {
            for (String s : disabled) {
                if (s != null && !s.isEmpty()) {
                    disabledFoci.add(s);
                }
            }
        }
        if (priority != null) {
            for (String s : priority) {
                if (s != null && !s.isEmpty()) {
                    priorityFoci.add(s);
                }
            }
        }
    }

    /**
     * 该聚晶在本个体上是否**可用**：未被（配置 / 运行期 / 本个体指令）拉黑。
     *
     * <p>注意"禁放"是**个体指令**，而 {@link #isBlacklisted} 是全局的——
     * 玩家把某聚晶从禁放里取消，也不会让一个全局拉黑的聚晶复活。
     */
    public boolean isUsable(String itemId) {
        return itemId != null && !isBlacklisted(itemId) && !disabledFoci.contains(itemId);
    }

    /**
     * 【0.0.18】按 id 取一个**当前可用**的聚晶条目（不锁定、不移除）。
     *
     * <p>用途：仆从的「优先释放」指令需要在开始施法前把指定聚晶挑出来交给
     * {@code CastChannel#beginCast(level, entry, mult)}；若它此时正在冷却池里、或已被禁放，
     * 就返回 null（调用方退回常规轮换抽签）。
     */
    @Nullable
    public FocusEntry takeSpecific(String itemId) {
        if (!isUsable(itemId)) {
            return null;
        }
        for (FocusCategory c : FocusCategory.values()) {
            for (FocusEntry e : pools.get(c)) {
                if (itemId.equals(e.getItemId().toString())) {
                    return e;
                }
            }
        }
        return null;
    }

    private FocusEntry copyEntry(FocusEntry src) {
        FocusEntry copy = new FocusEntry(src.getItemId(), src.getFocusItem());
        copy.setCategory(src.getCategory());
        copy.setAttackScore(src.getAttackScore());
        copy.setSurvivalScore(src.getSurvivalScore());
        return copy;
    }

    public FocusPoolManager() {
        for (FocusCategory c : FocusCategory.values()) {
            pools.put(c, new ArrayList<>());
            cooldownPools.put(c, new ArrayList<>());
        }
    }

    /**
     * 为一场战斗创建池实例。
     * 说明：FocusEntry 的动态偏移字段属于条目对象，此处对条目做实例级复制，
     * 避免多只boss共享动态偏移；静态评分仍从全局配置读取。
     */
    public static FocusPoolManager createFightPools() {
        initIfNeeded();
        FocusPoolManager m = new FocusPoolManager();
        for (FocusCategory c : FocusCategory.values()) {
            for (FocusEntry e : STATIC_POOLS.get(c)) {
                m.pools.get(c).add(m.copyEntry(e));
            }
        }
        return m;
    }

    // ---- 冷却驱动 ----

    /** 每tick调用：冷却归零的聚晶回归功能池 */
    public void tickCooldowns() {
        for (FocusCategory c : FocusCategory.values()) {
            List<CooldownTicket> tickets = cooldownPools.get(c);
            if (tickets.isEmpty()) {
                continue;
            }
            tickets.removeIf(t -> {
                if (--t.ticksRemaining <= 0) {
                    pools.get(c).add(t.entry);
                    return true;
                }
                return false;
            });
        }
    }

    /** 施法完成：聚晶进入对应冷却池 */
    public void moveToCooldown(FocusEntry entry, int cooldownTicks) {
        FocusCategory c = entry.getCategory();
        pools.get(c).remove(entry);
        if (cooldownTicks > 0) {
            cooldownPools.get(c).add(new CooldownTicket(entry, cooldownTicks));
        } else {
            pools.get(c).add(entry);
        }
    }

    /** 【2026-08-18 第十三轮】锁池：施法开始（beginCast）时从功能池移除，防止多通道争抢同一聚晶 */
    public void removeEntry(FocusEntry entry) {
        pools.get(entry.getCategory()).remove(entry);
    }

    /**
     * 【2026-08-18 第十三轮】归还功能池（无冷却）：施法被打断时调用。
     * 【0.0.8】改为**幂等**：只在池中确实不存在该条目时才放回，
     * 避免"施法在多个阶段失败"时被重复归还、导致同一聚晶在池里出现多份（抽取权重被人为放大）。
     */
    public void returnEntry(FocusEntry entry) {
        if (entry == null) {
            return;
        }
        List<FocusEntry> pool = pools.get(entry.getCategory());
        if (pool != null && !pool.contains(entry)) {
            pool.add(entry);
        }
    }

    // ---- 抽取（轮盘赌） ----

    // ================= 【0.0.16】「逐渐学习」：动态评分的权重系数 =================

    /**
     * 该调律师个体已完成的**施法次数**（由 {@code TunerBoss.onCastStart} 递增）。
     *
     * <p>存在这里而不是静态字段：{@link #createFightPools()} 每个 Boss 实例各建一份
     * （`FocusEntry` 也是实例级复制），所以"学习进度"天然是**该个体私有**的，
     * 与动态偏移的生命周期完全一致（两者一起随实体重建而重置）。
     */
    private int castCount = 0;

    /** 当前动态评分系数（0~max），随 castCount 从 learningWeightStart 缓升到 learningWeightMax。 */
    private double learningWeight = -1.0; // -1 = 尚未初始化（首次访问时按当前配置算）

    /** 已记录日志的里程碑百分比（每跨 10% 打一条 INFO，便于观察"逐渐学习"）。 */
    private int lastLoggedMilestone = -1;

    /**
     * 【0.0.16】记一次成功施法，并推进「学习系数」。
     *
     * <p>背景（用户反馈）：调律师的**动态评分**（实战学到的偏移）会把**初始评分**（配置/LLM 分类）
     * 的影响大幅冲淡——开局没打几下，初始分类就基本失效了，"这是个会学习的指挥家"的表现力很弱。
     *
     * <p>因此：**动态评分整体乘以一个系数**，开局取一个很低的值（初始分类主导），
     * 随该个体施法次数**线性**爬升到上限（实战反馈逐步接管）。
     * 静态评分（`attackScore`/`survivalScore`）**完全不受影响**，所以"初始分类被冲淡"这件事被压住了。
     */
    public void noteCast() {
        castCount++;
        refreshLearningWeight();
    }

    /** 按当前 castCount 与配置重算系数；跨 10% 里程碑时打一条 INFO。 */
    private void refreshLearningWeight() {
        double start = TunerCommonConfig.LEARNING_WEIGHT_START.get();
        double max = TunerCommonConfig.LEARNING_WEIGHT_MAX.get();
        int ramp = Math.max(1, TunerCommonConfig.LEARNING_WEIGHT_RAMP_CASTS.get());
        double t = Math.min(1.0, castCount / (double) ramp);
        learningWeight = start + (max - start) * t;
        int milestone = (int) (t * 100.0) / 10 * 10; // 0/10/.../100
        if (milestone != lastLoggedMilestone) {
            lastLoggedMilestone = milestone;
            GoetyTuner.LOGGER.info("[Tuner] Learning weight {}/{} = {} (casts {}/{})",
                    String.format("%.0f", start), String.format("%.0f", max),
                    String.format("%.3f", learningWeight), castCount, ramp);
        }
    }

    /** 当前动态评分系数（1.0 = 旧行为）。首次访问时按配置初始化。 */
    public double learningWeight() {
        if (learningWeight < 0.0) {
            refreshLearningWeight(); // 首次：castCount=0 ⇒ 取起始值（并打 0% 里程碑）
        }
        return learningWeight;
    }

    /** 该个体的施法次数（供调试/展示）。 */
    public int castCount() {
        return castCount;
    }

    /**
     * 从指定功能池轮盘赌抽取一个聚晶。
     * 权重 = |静态评分 + 动态偏移×学习系数| + 保底基数（详见 FocusEntry#rouletteWeight）
     *
     * @param category      功能分块
     * @param minionFill    当前召唤物数/上限（0~1+，召唤池用）
     * @param summonBlocked 召唤物是否满员（满员时召唤权重归0）
     */
    @Nullable
    public FocusEntry draw(FocusCategory category, double minionFill, boolean summonBlocked) {
        List<FocusEntry> pool = pools.get(category);
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        // 【第二十六轮】过滤掉被运行期拉黑的聚晶（配置黑名单在 init 时已排除，这里兜底）
        // 【0.0.18】同时过滤"该个体被玩家设为禁放"的聚晶（仆从指令）
        List<FocusEntry> usable = new ArrayList<>(pool.size());
        for (FocusEntry e : pool) {
            if (isUsable(e.getItemId().toString())) {
                usable.add(e);
            }
        }
        if (usable.isEmpty()) {
            return null;
        }
        double base = TunerCommonConfig.BASE_ROULETTE_WEIGHT.get();
        double w1 = TunerCommonConfig.SUMMON_SURVIVAL_WEIGHT.get();
        double w2 = TunerCommonConfig.SUMMON_ATTACK_WEIGHT.get();
        double[] ctx = new double[]{minionFill, w1, w2};
        double dynamicScale = learningWeight(); // 【0.0.16】动态评分缩权系数

        double total = 0.0;
        double[] weights = new double[usable.size()];
        for (int i = 0; i < usable.size(); i++) {
            weights[i] = usable.get(i).rouletteWeight(base, ctx, summonBlocked, dynamicScale);
            if (weights[i] < 0) weights[i] = 0;
            total += weights[i];
        }
        if (total <= 0) {
            return null; // 全部归0（如召唤满员）
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < usable.size(); i++) {
            roll -= weights[i];
            if (roll <= 0) {
                return usable.get(i);
            }
        }
        return usable.get(usable.size() - 1);
    }

    public List<FocusEntry> pool(FocusCategory c) {
        return pools.get(c);
    }

    public int poolSize(FocusCategory c) {
        List<FocusEntry> p = pools.get(c);
        return p == null ? 0 : p.size();
    }

    /** 防御/其他池均匀抽取（不评分） */
    @Nullable
    public FocusEntry drawUniform(FocusCategory category) {
        List<FocusEntry> pool = pools.get(category);
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        // 【第二十六轮】过滤运行期拉黑
        // 【0.0.18】同时过滤"该个体被玩家设为禁放"的聚晶（仆从指令）
        List<FocusEntry> usable = new ArrayList<>(pool.size());
        for (FocusEntry e : pool) {
            if (isUsable(e.getItemId().toString())) {
                usable.add(e);
            }
        }
        if (usable.isEmpty()) {
            return null;
        }
        return usable.get(random.nextInt(usable.size()));
    }
}
