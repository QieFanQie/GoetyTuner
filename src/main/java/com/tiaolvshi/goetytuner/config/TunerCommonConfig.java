/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 通用配置（同步前后端通用数值）。
 * 静态聚晶分类与评分表在 config/goetytuner/focus_classification.json（JSON，非此处），
 * 便于 LLM 自动配置功能直接读写。
 */
public class TunerCommonConfig {

    public static final ForgeConfigSpec SPEC;

    // ---- Boss 基础 ----
    public static final ForgeConfigSpec.IntValue BOSS_MAX_HEALTH;
    public static final ForgeConfigSpec.IntValue LOCK_HEALTH_INTERVAL;   // 类锁血间隔（默认128）
    public static final ForgeConfigSpec.IntValue LOCK_GRACE_TICKS;       // 锁血宽限期（触发后持续回弹的tick数）
    /** 【第三十三轮】死亡自愈开关：锁血未耗尽时 boss 进入死亡状态（血量归零/死亡动画）则回弹 */
    public static final ForgeConfigSpec.BooleanValue LOCK_DEATH_REVIVE;
    public static final ForgeConfigSpec.DoubleValue EQUIVALENT_ARMOR;    // 等效护甲（默认16，不显示）
    public static final ForgeConfigSpec.DoubleValue MELEE_VULNERABILITY; // 【第二十九轮】近战易伤加成（默认0.25=+25%）
    public static final ForgeConfigSpec.IntValue TELEPORT_INTERVAL;      // 主动瞬移（追击）最小间隔tick
    public static final ForgeConfigSpec.IntValue TELEPORT_RESCUE_INTERVAL; // 异常位置修正间隔tick
    public static final ForgeConfigSpec.DoubleValue TELEPORT_MIN_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue TELEPORT_CHASE_MAX_DISTANCE; // 追击瞬移触发的距离上限
    /** 【第三十一轮】逐跳追击：每次瞬移向目标方向跳跃的距离 */
    public static final ForgeConfigSpec.DoubleValue TELEPORT_HOP_DISTANCE;
    /** 【第三十一轮】逐跳追击：相邻两跳的间隔 */
    public static final ForgeConfigSpec.IntValue TELEPORT_HOP_INTERVAL;
    public static final ForgeConfigSpec.IntValue TELEPORT_SEARCH_RADIUS; // 玩家身边可站立方块搜索半径(16)
    public static final ForgeConfigSpec.IntValue BUILDUP_BACKSTEP_DISTANCE; // 一阶段铺垫攻击完毕后撤步瞬移距离
    public static final ForgeConfigSpec.IntValue BOSS_TARGET_RANGE;      // 索敌半径（写入FOLLOW_RANGE属性）
    public static final ForgeConfigSpec.IntValue PHASE2_LOCK_MARK;       // 二阶段触发的lockMark阈值
    /** 【第三十一轮】二阶段进场重音击退连发次数 */
    public static final ForgeConfigSpec.IntValue PHASE2_ENTRY_BURST_COUNT;

    // ---- 二阶段自施药水（第三十三轮）----
    /** 二阶段周期性自施药水总开关（原版力量+重振；Goety 的 BUFF/强健 属性加成对boss法术输出无感知，改原版力量保证肉眼可见） */
    public static final ForgeConfigSpec.BooleanValue PHASE2_BUFFS_ENABLED;
    /** 二阶段自施原版力量等级：锁血档位<10（正常档） */
    public static final ForgeConfigSpec.IntValue PHASE2_STRENGTH_LEVEL_LOW;
    /** 二阶段自施原版力量等级：锁血档位≥10（狂暴档） */
    public static final ForgeConfigSpec.IntValue PHASE2_STRENGTH_LEVEL_HIGH;

    // ---- 召唤逻辑 ----
    public static final ForgeConfigSpec.IntValue MAX_MINIONS;            // 召唤物上限
    public static final ForgeConfigSpec.IntValue MINION_REFILL_HYSTERESIS; // 归零解除阈值（默认4）
    public static final ForgeConfigSpec.DoubleValue SUMMON_SURVIVAL_WEIGHT; // 参数1：生存评分权重
    public static final ForgeConfigSpec.DoubleValue SUMMON_ATTACK_WEIGHT;   // 参数2：输出评分权重

    // ---- 评分 ----
    public static final ForgeConfigSpec.DoubleValue BASE_ROULETTE_WEIGHT;  // 保底基数
    public static final ForgeConfigSpec.DoubleValue DYNAMIC_SCORE_CAP;     // 动态偏移上下限
    public static final ForgeConfigSpec.DoubleValue DPS_ADJUST_RATE;       // dps对比期望的修正速率

    // ---- 施法 ----
    public static final ForgeConfigSpec.IntValue EXTRA_CAST_COOLDOWN;    // boss额外施法冷却（防复读，tick）
    public static final ForgeConfigSpec.IntValue MAX_CAST_WINDOW_TICKS; // 施法窗口封顶（boss每次蓄力最大tick数）
    public static final ForgeConfigSpec.DoubleValue CLIMAX_WARMUP_MULTIPLIER; // 高潮期前摇倍率
    // 角色序列（数字串，每字符一个通道位）：1=防御 2=攻击 3=召唤 4=其他
    public static final ForgeConfigSpec.ConfigValue<String> PHASE1_BUILDUP_ROTATION;  // 一阶段铺垫轮换
    public static final ForgeConfigSpec.ConfigValue<String> PHASE2_BUILDUP_ROTATION;  // 二阶段铺垫轮换
    public static final ForgeConfigSpec.ConfigValue<String> PHASE1_CLIMAX_CHANNELS;   // 一阶段高潮并行通道
    public static final ForgeConfigSpec.ConfigValue<String> PHASE2_CLIMAX_CHANNELS;   // 二阶段高潮并行通道
    public static final ForgeConfigSpec.DoubleValue PHASE2_TELEPORT_INTERVAL_FACTOR;
    /** 【第二十二轮】二阶段铺垫期攻击法术开始前的弧形瞬移半径（boss身前半圆弧，0=关闭） */
    public static final ForgeConfigSpec.DoubleValue PHASE2_BUILDUP_ARC_RADIUS;  // 二阶段主动瞬移间隔倍率
    /** 【第二十二轮】二阶段弧形走位瞬移触发频率：每N次攻击施法触发一次 */
    public static final ForgeConfigSpec.IntValue PHASE2_BUILDUP_ARC_EVERY_N;

    // ---- 聚晶黑名单（第二十六轮；第二十八轮改为 String + 读取时自动规范化）----
    /** 黑名单原始输入（String）。支持 单个id / 逗号分隔多个 / 数组格式，读取时自动归一化，见 {@link #getBlacklist()} */
    public static final ForgeConfigSpec.ConfigValue<String> FOCUS_BLACKLIST;

    // ---- 法杖白名单（v0.0.0 正式版）----
    /** 法杖白名单原始输入（String）。写入物品ID后可视为法杖用于仪式召唤Boss（补充 goety:wands 标签） */
    public static final ForgeConfigSpec.ConfigValue<String> WAND_WHITELIST;

    // ---- 音乐 ----
    public static final ForgeConfigSpec.IntValue MUSIC_SYNC_INTERVAL;    // 同步包间隔（默认20tick）
    public static final ForgeConfigSpec.DoubleValue ACCENT_KNOCKBACK_BASE;
    public static final ForgeConfigSpec.DoubleValue ACCENT_KNOCKBACK_VALLEY;
    public static final ForgeConfigSpec.DoubleValue ACCENT_KNOCKBACK_CLIMAX;
    public static final ForgeConfigSpec.DoubleValue PHASE2_VALLEY_KNOCKBACK_MULTIPLIER; // 二阶段低谷重音击退倍率
    public static final ForgeConfigSpec.IntValue ACCENT_SHAKE_TICKS;    // 重音视角震颤持续tick
    public static final ForgeConfigSpec.DoubleValue ACCENT_SHAKE_STRENGTH; // 重音视角震颤强度（roll角度幅度）

    // ---- 重音特效（第十九轮）----
    public static final ForgeConfigSpec.BooleanValue ACCENT_PARTICLES;  // 粒子冲击环+音符爆发
    public static final ForgeConfigSpec.BooleanValue ACCENT_SOUND;      // 阶段差异化提示音

    // ---- 音乐播放（第十九轮）----
    public static final ForgeConfigSpec.DoubleValue MUSIC_VOLUME;        // 播放音量
    public static final ForgeConfigSpec.DoubleValue MUSIC_PITCH_PHASE1;  // 播放速度(pitch)

    // ---- LLM 自动分类 ----
    public static final ForgeConfigSpec.ConfigValue<String> LLM_API_URL;
    public static final ForgeConfigSpec.ConfigValue<String> LLM_MODEL;

    // ---- 仪式法杖升级（任务#118 第三十五轮）----
    /** 总开关：关闭后 Boss 死亡掉原始法杖（无加成） */
    public static final ForgeConfigSpec.BooleanValue WAND_UPGRADE_ENABLED;
    /** 调律:巫法加成（SPELL_POTENCY 百分比加成，MULTIPLY_TOTAL modifier） */
    public static final ForgeConfigSpec.DoubleValue WAND_WITCHCRAFT_BONUS;
    /** 调律:魔法伤害加成（魔法伤害 ×(1+此值)） */
    public static final ForgeConfigSpec.DoubleValue WAND_MAGIC_DAMAGE_BONUS;
    /** 法杖已有同键加成时：true=加法叠加，false=覆盖 */
    public static final ForgeConfigSpec.BooleanValue WAND_BONUS_STACK;
    /** Boss 手持升级法杖时是否同样享受魔法伤害加成（默认 false，防自伤/友伤放大） */
    public static final ForgeConfigSpec.BooleanValue WAND_BONUS_APPLIES_TO_BOSS;
    /** Boss 掉落经验倍率（任务#118 要求翻4倍） */
    public static final ForgeConfigSpec.DoubleValue XP_MULTIPLIER;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("boss");
        BOSS_MAX_HEALTH = b.comment("Boss最大生命值。【第二十六轮】默认216=12档×18血")
                .defineInRange("maxHealth", 216, 20, 10240);
        LOCK_HEALTH_INTERVAL = b.comment("类锁血间隔：血量每降低此值触发一次锁血判定。【第二十六轮】默认18=每档18血")
                .defineInRange("lockHealthInterval", 18, 8, 1024);
        LOCK_GRACE_TICKS = b.comment("锁血宽限期（tick，20=1秒）：每次触发锁血后，该时长内血量持续钉在锁血地板值（回弹机制保持），0=关闭")
                .defineInRange("lockGraceTicks", 10, 0, 100);
        LOCK_DEATH_REVIVE = b.comment("【第三十三轮】死亡自愈开关：锁血未耗尽时，boss 一旦进入死亡状态"
                        + "（血量归零进入死亡动画，含 /kill、绕过 hurt 拦截的异常伤害等）就立即回弹到下一档锁血地板，"
                        + "平时每 tick 检测（覆写 tick 在死亡动画期也运行，旧实现放在 aiStep 内——死亡后 aiStep 不再执行，"
                        + "回弹实际从未触发）。false=关闭（boss 可被异常途径杀死）。默认 true")
                .define("lockDeathRevive", true);
        EQUIVALENT_ARMOR = b.comment("等效护甲值（不显示于客户端）")
                .defineInRange("equivalentArmor", 16.0, 0.0, 30.0);
        MELEE_VULNERABILITY = b.comment("【第二十九轮】近战易伤加成：boss受到的直接近战物理伤害乘以 (1+此值)。"
                        + "默认0.25=近战伤害+25%（鼓励贴身近战；远程/投射物/爆炸/魔法不受影响）")
                .defineInRange("meleeVulnerability", 0.25, 0.0, 3.0);
        TELEPORT_INTERVAL = b.comment("主动瞬移（追击）最小间隔（tick，20=1秒）。默认200=10秒。"
                        + "【第三十一轮】追击已改为逐跳逼近（teleportHopDistance/teleportHopInterval），"
                        + "本值现作为「追击链结束后的冷却」：连续小瞬移贴近目标（或目标脱离追击区间）后，"
                        + "需等待本时长才会开始下一轮追击链")
                .defineInRange("teleportInterval", 200, 20, 1200);
        TELEPORT_RESCUE_INTERVAL = b.comment("异常位置（水里/岩浆/悬空/卡墙）修正瞬移间隔（tick）。默认20=1秒")
                .defineInRange("teleportRescueInterval", 20, 5, 200);
        TELEPORT_MIN_DISTANCE = b.comment("与最近可索敌玩家距离超过该值则尝试主动追击瞬移")
                .defineInRange("teleportMinDistance", 12.0, 4.0, 64.0);
        TELEPORT_CHASE_MAX_DISTANCE = b.comment("追击瞬移触发的距离上限（格）：与最近可索敌玩家距离超过该值时不再瞬移"
                        + "（过远视为脱战距离，避免瞬移跨越地图追人）。"
                        + "【第二十二轮】默认96=与 targetRange 对齐：仇恨保留范围内始终能瞬移追击")
                .defineInRange("teleportChaseMaxDistance", 96.0, 16.0, 256.0);
        TELEPORT_HOP_DISTANCE = b.comment("【第三十一轮】逐跳追击：每次瞬移仅向目标方向跳跃该距离（格）。"
                        + "跳完仍在追击区间时经 teleportHopInterval 后继续跳——远距离追击表现为一串连续小瞬移。"
                        + "落点优先找可站立方块（±3格高度扫描，可逐跳爬坡），目标悬空无地面时兜底空中位"
                        + "（修「索敌空中单位时瞬移失效/卡死」bug）")
                .defineInRange("teleportHopDistance", 8.0, 1.0, 32.0);
        TELEPORT_HOP_INTERVAL = b.comment("【第三十一轮】逐跳追击的相邻两跳间隔（tick，20=1秒）。默认10=0.5秒"
                        + "（96格追击约10跳、耗时约5秒，肉眼可见连续瞬移追击画面）")
                .defineInRange("teleportHopInterval", 10, 1, 200);
        TELEPORT_SEARCH_RADIUS = b.comment("玩家身边可站立方块搜索半径")
                .defineInRange("teleportSearchRadius", 16, 4, 32);
        BUILDUP_BACKSTEP_DISTANCE = b.comment("一阶段铺垫期：攻击法术施展完毕后向身后短距离瞬移的距离（格，0=关闭）")
                .defineInRange("buildupBackstepDistance", 4, 0, 16);
        BOSS_TARGET_RANGE = b.comment("索敌半径（格，写入FOLLOW_RANGE属性；半径内玩家即可锁定，无需视线；"
                        + "距离超出该值boss会丢失仇恨=脱战）。【第二十二轮】默认96=大幅提高仇恨保留范围，"
                        + "配合瞬移追击（teleportChaseMaxDistance 默认同为96）避免实战中甩开太远脱离战斗")
                .defineInRange("targetRange", 96, 16, 128);
        PHASE2_LOCK_MARK = b.comment("二阶段触发的lockMark阈值：当锁血回弹使lockMark达到此值时进入二阶段。"
                        + "【第二十六轮】默认6=半血档位（216血/18间隔时，第6次锁血=108血=半血）。"
                        + "基于lockMark触发可确保锁血回弹先于阶段切换，避免高伤跳过锁血直接进二阶段。")
                .defineInRange("phase2LockMark", 6, 1, 20);
        PHASE2_ENTRY_BURST_COUNT = b.comment("【第三十一轮】进入二阶段时连续触发的重音击退次数"
                + "（每5tick一次、音调逐次递升，0=关闭）。默认6")
                .defineInRange("phase2EntryBurstCount", 6, 0, 20);
        b.pop();

        b.push("phase2_buffs");
        PHASE2_BUFFS_ENABLED = b.comment("【第三十三轮】二阶段周期性自施药水总开关：每2秒给自己施加原版力量"
                + "（等级 phase2StrengthLevelLow/High）+ 重振（RALLYING）。"
                + "原实现自施 Goety 的 BUFF/强健——经反编译确认 BUFF 的属性加成（攻击/移速）与重振一样"
                + "只作用于近战属性，对 boss 的法术输出毫无加成，玩家观察即「不奏效」；改原版力量后近战伤害"
                + "肉眼可见提升。【第三十七轮】额外挂 SPELL_POTENCY 属性 modifier（法术伤害×(1+等级×0.1)），"
                + "通过 Goety 原生施法通道真正提升法术输出。false=完全关闭自施药水+属性加成。默认 true")
                .define("phase2BuffsEnabled", true);
        PHASE2_STRENGTH_LEVEL_LOW = b.comment("二阶段自施原版力量等级：锁血档位<10 时（半血至狂暴前）。"
                + "原版力量每级 +3 近战伤害。默认2")
                .defineInRange("phase2StrengthLevelLow", 2, 1, 10);
        PHASE2_STRENGTH_LEVEL_HIGH = b.comment("二阶段自施原版力量等级：锁血档位≥10 时（狂暴档）。默认5")
                .defineInRange("phase2StrengthLevelHigh", 5, 1, 10);
        b.pop();

        b.push("summon");
        MAX_MINIONS = b.comment("以boss为主人的召唤物数量上限")
                .defineInRange("maxMinions", 32, 1, 256);
        MINION_REFILL_HYSTERESIS = b.comment("召唤评分归零解除阈值：召唤物数 < 上限-该值 才恢复召唤")
                .defineInRange("refillHysteresis", 4, 1, 64);
        SUMMON_SURVIVAL_WEIGHT = b.comment("召唤使用评分公式-参数1（生存评分权重）")
                .defineInRange("survivalWeight", 1.0, 0.0, 10.0);
        SUMMON_ATTACK_WEIGHT = b.comment("召唤使用评分公式-参数2（输出评分权重）")
                .defineInRange("attackWeight", 1.0, 0.0, 10.0);
        b.pop();

        b.push("scoring");
        BASE_ROULETTE_WEIGHT = b.comment("轮盘赌保底基数：每个聚晶至少拥有该权重")
                .defineInRange("baseRouletteWeight", 2.0, 0.1, 100.0);
        DYNAMIC_SCORE_CAP = b.comment("动态偏移绝对值上限")
                .defineInRange("dynamicScoreCap", 5.0, 0.5, 50.0);
        DPS_ADJUST_RATE = b.comment("攻击聚晶动态修正速率：偏移 += (实际dps/期望dps - 1) * rate")
                .defineInRange("dpsAdjustRate", 0.5, 0.05, 10.0);
        b.pop();

        b.push("casting");
        EXTRA_CAST_COOLDOWN = b.comment("boss完成施法后的额外冷却（tick，防复读）")
                .defineInRange("extraCastCooldown", 20, 0, 1200);
        MAX_CAST_WINDOW_TICKS = b.comment("施法窗口封顶（tick）：Goety法术前摇castDuration默认5-10秒甚至更长，boss若照搬会站桩蓄力过久几乎不放技能。此值截断每次蓄力时长（玩家提前松手也是合法释放路径）。【第二十六轮】默认50=2.5秒")
                .defineInRange("maxCastWindowTicks", 50, 10, 200);
        CLIMAX_WARMUP_MULTIPLIER = b.comment("高潮期施法前摇倍率（冷却不变）")
                .defineInRange("climaxWarmupMultiplier", 0.5, 0.1, 1.0);
        PHASE1_BUILDUP_ROTATION = b.comment("一阶段铺垫期轮换序列。数字串：1=防御 2=攻击 3=召唤 4=其他，按序循环")
                .define("phase1BuildupRotation", "123");
        PHASE2_BUILDUP_ROTATION = b.comment("二阶段铺垫期轮换序列（进入二阶段后生效）。如\"222\"=全攻击复读")
                .define("phase2BuildupRotation", "222");
        PHASE1_CLIMAX_CHANNELS = b.comment("一阶段高潮期并行通道序列。每位一个独立施法通道，数字含义同上")
                .define("phase1ClimaxChannels", "123");
        PHASE2_CLIMAX_CHANNELS = b.comment("二阶段高潮期并行通道序列。如\"222\"=三端攻击")
                .define("phase2ClimaxChannels", "222");
        PHASE2_TELEPORT_INTERVAL_FACTOR = b.comment("二阶段主动瞬移间隔倍率（作用于teleportInterval，1.0=与一阶段相同。二阶段铺垫期的走位主要由 phase2BuildupArcRadius 的攻击前弧形瞬移承担）")
                .defineInRange("phase2TeleportIntervalFactor", 1.0, 0.1, 1.0);
        PHASE2_BUILDUP_ARC_RADIUS = b.comment("二阶段铺垫期：攻击法术开始前的弧形瞬移半径（格，boss身前半圆弧随机点，0=关闭）。默认6")
                .defineInRange("phase2BuildupArcRadius", 6.0, 0.0, 16.0);
        PHASE2_BUILDUP_ARC_EVERY_N = b.comment("【第二十二轮】二阶段铺垫期弧形走位瞬移触发频率：每N次攻击施法触发一次"
                        + "（默认3=每轮\"222\"轮换施法一次；1=每次攻击施法都瞬移=旧行为）")
                .defineInRange("phase2BuildupArcEveryN", 3, 1, 20);
        b.pop();

        b.comment("【聚晶设置】\n"
                        + "这里控制 BOSS 抽取聚晶（法杖法术）时的黑名单：被列入的聚晶不会在战斗中抽取、施放。\n"
                        + "主要用于屏蔽「需要玩家来源、由 BOSS 施放会崩溃」的聚晶（例如 诡厄：暮色 的毁坏链锤 destruction_focus）。")
                .push("focus");
        FOCUS_BLACKLIST = b.comment("聚晶黑名单（可留空 = 不屏蔽任何聚晶）。\n"
                        + "填写后自动识别格式，无需手动加引号或方括号：\n"
                        + "  · 单个聚晶：goetytwilight:destruction_focus\n"
                        + "  · 多个聚晶，用英文逗号分隔：goetytwilight:destruction_focus, goety:another_focus\n"
                        + "  · 也兼容数组写法：[\"goetytwilight:destruction_focus\", \"goety:another_focus\"]\n"
                        + "每条格式为 模组id:物品id（modid:itemid）。\n"
                        + "运行期若某聚晶施法抛异常，会被自动临时拉黑并记日志，把日志里的 id 填到这里即可永久屏蔽。\n"
                        + "【默认值】goetytwilight:destruction_focus（诡厄：暮色 的毁坏链锤对非玩家 owner 必崩，内置屏蔽）")
                .define("blacklist", "goetytwilight:destruction_focus");
        b.pop();

        b.comment("【法杖白名单】\n"
                        + "仪式召唤Boss时，除了 goety:wands 标签中的法杖外，此处填写的物品ID也可作为法杖激活仪式。\n"
                        + "用途：某些诡厄巫法附属mod的法杖未加入 goety:wands 标签时，在此添加其物品ID即可使用。\n"
                        + "格式同聚晶黑名单：单个id / 英文逗号分隔多个 / 数组写法，自动识别。")
                .push("wand_whitelist");
        WAND_WHITELIST = b.comment("法杖白名单（可留空 = 仅使用 goety:wands 标签中的法杖）。\n"
                        + "示例：goetytwilight:twilight_wand, someaddon:special_wand\n"
                        + "每条格式为 模组id:物品id（modid:itemid）")
                .define("whitelist", "");
        b.pop();

        b.push("music");
        MUSIC_SYNC_INTERVAL = b.comment("音乐同步包间隔（tick）")
                .defineInRange("syncInterval", 20, 5, 100);
        ACCENT_KNOCKBACK_BASE = b.defineInRange("accentKnockbackBase", 0.4, 0.0, 5.0);
        ACCENT_KNOCKBACK_VALLEY = b.defineInRange("accentKnockbackValley", 0.8, 0.0, 5.0);
        ACCENT_KNOCKBACK_CLIMAX = b.defineInRange("accentKnockbackClimax", 1.2, 0.0, 5.0);
        PHASE2_VALLEY_KNOCKBACK_MULTIPLIER = b.comment("二阶段低谷期重音击退倍率（乘在accentKnockbackValley上）")
                .defineInRange("phase2ValleyKnockbackMultiplier", 2.0, 1.0, 5.0);
        ACCENT_SHAKE_TICKS = b.comment("二阶段低谷重音视角震颤持续tick（0=关闭）")
                .defineInRange("accentShakeTicks", 10, 0, 100);
        ACCENT_SHAKE_STRENGTH = b.comment("视角震颤强度（摄像机roll抖动幅度，度）")
                .defineInRange("accentShakeStrength", 2.0, 0.0, 10.0);
        ACCENT_PARTICLES = b.comment("重音特效-粒子：三波END_ROD同心冲击环 + NOTE音符爆发（boss头顶）")
                .define("accentParticles", true);
        ACCENT_SOUND = b.comment("重音特效-提示音：阶段差异化音调的紫水晶音（铺垫0.9/高潮1.4/低谷0.6，使用原版音效无需音频资源）")
                .define("accentSound", true);
        MUSIC_VOLUME = b.comment("boss战音乐播放音量（0-8，0=静音；客户端循环实例的增益，1=原音量）")
                .defineInRange("volume", 4.0, 0.0, 8.0);
        MUSIC_PITCH_PHASE1 = b.comment("音乐播放速度pitch（1.0=原速，1.2=快20%同时音调升高，0.8=慢20%同时音调降低。"
                        + "全程恒定（一/二阶段共用），乐谱时间轴同步按此速度推进。"
                        + "注意：原版音频引擎速度与音调绑定，无法只变速不变调；若改速度请按变速后的实际时长标注乐谱分段")
                .defineInRange("pitchPhase1", 1.0, 0.5, 2.0);
        b.pop();

        b.push("llm");
        LLM_API_URL = b.comment("OpenAI兼容Chat Completions端点")
                .define("apiUrl", "https://api.openai.com/v1/chat/completions");
        LLM_MODEL = b.comment("模型名").define("model", "gpt-4o-mini");
        // API Key 存于本地 focus_classification.json 而非此处，避免写入toml共享区
        b.pop();

        b.push("wand_upgrade");
        WAND_UPGRADE_ENABLED = b.comment("【任务#118】仪式召唤法杖升级总开关：true=仪式召唤的Boss死亡时"
                + "掉落「原始法杖 + 调律加成NBT」；false=掉落原始法杖（无加成）。"
                + "非仪式召唤（刷怪蛋/指令）的Boss不受影响（无快照即原行为）。默认 true")
                .define("wandUpgradeEnabled", true);
        WAND_WITCHCRAFT_BONUS = b.comment("【任务#118】调律:巫法加成：玩家手持升级法杖时，"
                + "实体属性 SPELL_POTENCY（施法强度）获得该值的 MULTIPLY_TOTAL 百分比加成。"
                + "Goety 施法数值全部走施法者实体属性，此为原生通道（非冷却/非施法前摇）。"
                + "默认0.10=+10%")
                .defineInRange("wandWitchcraftBonus", 0.10, 0.0, 10.0);
        WAND_MAGIC_DAMAGE_BONUS = b.comment("【任务#118】调律:魔法伤害加成：手持升级法杖造成的"
                + "魔法/法术类伤害 ×(1+此值)。默认0.40=+40%")
                .defineInRange("wandMagicDamageBonus", 0.40, 0.0, 10.0);
        WAND_BONUS_STACK = b.comment("【任务#118】多次击败Boss获得的升级法杖再次被击败时："
                + "true=加成数值加法叠加（10%+10%=20%），false=覆盖为单次值。默认 true（叠加）")
                .define("wandBonusStack", true);
        WAND_BONUS_APPLIES_TO_BOSS = b.comment("【任务#118】Boss 手持升级法杖时是否同样享受"
                + "魔法伤害加成。默认 false：Boss 施法数值走自身实体属性（SPELL_POTENCY），"
                + "不受此加成影响，且防止手持高叠法杖时自伤/误伤被放大")
                .define("wandBonusAppliesToBoss", false);
        XP_MULTIPLIER = b.comment("【任务#118】Boss 掉落经验倍率（任务要求翻4倍）。"
                + "基础经验500，返回 (int)round(500×此值)。默认4.0=2000经验")
                .defineInRange("xpMultiplier", 4.0, 0.0, 100.0);
        b.pop();

        SPEC = b.build();
    }

    /**
     * 【第二十八轮】读取并规范化聚晶黑名单。
     * 自动识别三种输入格式，无需用户手动补引号/方括号：
     * ① 单个 id：goetytwilight:destruction_focus
     * ② 逗号分隔多个：a, b, c
     * ③ 数组写法（含完整/残缺括号）：["a", "b"]、["a","b"]
     * 结果始终为去空白、去引号后的 id 集合。
     *
     * 【第三十五轮】性能优化：draw() 每次施法都会对整个功能池逐条调用本方法，
     * 旧实现每调用都重新 split 配置字符串。现增加缓存：raw 字符串未变则直接返回
     * 不可变集合（Forge 配置热重载时 raw 变化，自动触发重新解析）。
     */
    private static volatile String cachedBlacklistRaw = null;
    private static volatile Set<String> cachedBlacklist = null;

    public static Set<String> getBlacklist() {
        String raw = FOCUS_BLACKLIST.get();
        Set<String> cached = cachedBlacklist;
        if (cached != null && raw.equals(cachedBlacklistRaw)) {
            return cached;
        }
        Set<String> parsed = parseBlacklist(raw);
        cachedBlacklist = parsed;
        cachedBlacklistRaw = raw;
        return parsed;
    }

    // ---- 法杖白名单读取（v0.0.0）----

    private static volatile String cachedWhitelistRaw = null;
    private static volatile Set<String> cachedWhitelist = null;

    public static Set<String> getWandWhitelist() {
        String raw = WAND_WHITELIST.get();
        Set<String> cached = cachedWhitelist;
        if (cached != null && raw.equals(cachedWhitelistRaw)) {
            return cached;
        }
        Set<String> parsed = parseBlacklist(raw); // 复用同一解析逻辑
        cachedWhitelist = parsed;
        cachedWhitelistRaw = raw;
        return parsed;
    }

    private static Set<String> parseBlacklist(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null) return out;
        String s = raw.trim();
        if (s.isEmpty()) return out;
        // 数组写法：剥掉首尾括号后按逗号拆分（与逗号分隔走同一逻辑）
        if (s.startsWith("[")) {
            if (s.endsWith("]")) s = s.substring(1, s.length() - 1);
            else s = s.substring(1);
        }
        for (String part : s.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            // 去掉两端成对引号（双引号或单引号）
            if (p.length() >= 2
                    && ((p.startsWith("\"") && p.endsWith("\""))
                    || (p.startsWith("'") && p.endsWith("'")))) {
                p = p.substring(1, p.length() - 1);
            }
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }
}
