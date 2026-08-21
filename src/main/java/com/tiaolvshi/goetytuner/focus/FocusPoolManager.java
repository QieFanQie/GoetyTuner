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
import java.util.HashSet;
import java.util.List;
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
        for (Item item : ForgeRegistries.ITEMS) {
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
        }

        // 应用静态分类与评分（配置文件缺失则生成默认示例）
        classification = FocusClassificationConfig.load();
        classification.applyTo(ALL_ENTRIES);
        for (FocusEntry e : ALL_ENTRIES) {
            STATIC_POOLS.get(e.getCategory()).add(e);
        }

        initialized = true;
        GoetyTuner.LOGGER.info("[Tuner] Focus scan complete: {} foci -> attack:{} defense:{} summon:{} other:{}",
                ALL_ENTRIES.size(),
                STATIC_POOLS.get(FocusCategory.ATTACK).size(),
                STATIC_POOLS.get(FocusCategory.DEFENSE).size(),
                STATIC_POOLS.get(FocusCategory.SUMMON).size(),
                STATIC_POOLS.get(FocusCategory.OTHER).size());
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static List<FocusEntry> allEntries() {
        initIfNeeded();
        return ALL_ENTRIES;
    }

    public static FocusClassificationConfig classification() {
        initIfNeeded();
        return classification;
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

    /** 【2026-08-18 第十三轮】归还功能池（无冷却）：施法被打断时调用 */
    public void returnEntry(FocusEntry entry) {
        pools.get(entry.getCategory()).add(entry);
    }

    // ---- 抽取（轮盘赌） ----

    /**
     * 从指定功能池轮盘赌抽取一个聚晶。
     * 权重 = |静态评分+动态偏移| + 保底基数（详见 FocusEntry#rouletteWeight）
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
        List<FocusEntry> usable = new ArrayList<>(pool.size());
        for (FocusEntry e : pool) {
            if (!isBlacklisted(e.getItemId().toString())) {
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

        double total = 0.0;
        double[] weights = new double[usable.size()];
        for (int i = 0; i < usable.size(); i++) {
            weights[i] = usable.get(i).rouletteWeight(base, ctx, summonBlocked);
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
        List<FocusEntry> usable = new ArrayList<>(pool.size());
        for (FocusEntry e : pool) {
            if (!isBlacklisted(e.getItemId().toString())) {
                usable.add(e);
            }
        }
        if (usable.isEmpty()) {
            return null;
        }
        return usable.get(random.nextInt(usable.size()));
    }
}
