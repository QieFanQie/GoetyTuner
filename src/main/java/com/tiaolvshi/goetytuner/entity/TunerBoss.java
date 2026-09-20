/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.entity;

import com.Polarice3.Goety.common.effects.GoetyEffects;
import com.Polarice3.Goety.init.ModAttributes;
import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.combat.BuffSpellPower;
import com.tiaolvshi.goetytuner.combat.CombatEvents;
import com.tiaolvshi.goetytuner.combat.DamageScoreTracker;
import com.tiaolvshi.goetytuner.combat.DamageThrottle;
import com.tiaolvshi.goetytuner.combat.SummonScoreTracker;
import com.tiaolvshi.goetytuner.combat.TunerDamageRules;
import com.tiaolvshi.goetytuner.config.TunerCommonConfig;
import com.tiaolvshi.goetytuner.entity.ai.CastChannel;
import com.tiaolvshi.goetytuner.focus.FocusCategory;
import com.tiaolvshi.goetytuner.focus.FocusPoolManager;
import com.tiaolvshi.goetytuner.network.SMusicSyncPacket;
import com.tiaolvshi.goetytuner.network.SEntityRevivePacket;
import com.tiaolvshi.goetytuner.network.TunerNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.List;

/**
 * 「调律师」—— 诡厄巫法附属Boss。
 *
 * 章程：
 * - 音乐驱动阶段：服务端 MusicController 权威推进，铺垫/高潮/低谷三段式
 * - 铺垫：单通道按配置角色序列轮换（默认123=防御/攻击/召唤）；主动瞬移按间隔配置
 * - 高潮：配置序列数量的通道并行；前摇缩短、冷却不变；打断正在施放的法术；不移动
 * - 低谷：无法施法；仆从获得 生命恢复1/伤害吸收1/缓慢3/虚弱2；自身获得侵蚀12+黑暗；不移动
 * - 重音：微小斥力推离周围实体；铺垫期无前摇释放1个其他类法术；高潮期3个+三连击
 * - 锁血：每18点为一档（12档×18=216血）；跌破地板则回设地板值且lockMark++；第6档(108血=半血)进入二阶段
 * - 二阶段：角色序列切换（默认全攻击复读）、杀死全部仆从、瞬移追击全程生效、
 *   弧形走位瞬移每轮施法一次（每3次攻击施法）、施法前摇×0.8（高潮/铺垫）、
 *   低谷段按铺垫期处理（音乐条显示与行为，第二十二轮）、音乐不变（全程同一版本）、
 *   节奏条改为右→左滚动（客户端HUD行为）
 * - 【第二十二轮】仇恨保留：targetRange 默认96（FOLLOW_RANGE），配合全程瞬移追击
 * - 【第二十三轮】Boss身份免疫：摔落/原版火焰/窒息/溺水；非玩家瞬移追击
 *   （96格内无可参战玩家且仇恨目标非玩家时，独立计时器驱动，不干扰玩家追击）
 * - 【第二十四轮】嘲讽彩蛋（一阶段）：被激怒且目标6-12格时随机触发——面向目标蹲起4轮
 *   后向反向跳跃逃跑约5格，或仅蹲望片刻；原版 Pose.CROUCHING 同步+TunerModel 蹲姿动画
 *   （teleportChaseMaxDistance 默认96）防止实战中被甩开脱战
 *
 * 无限灵魂能量：不接SoulEnergy capability，耗蓝对非玩家施法本就不生效。
 */
public class TunerBoss extends Monster implements CastChannel.TunerCastCallback, OrbHighlightSource {

    private static final EntityDataAccessor<Integer> DATA_CAST_CATEGORIES =
            SynchedEntityData.defineId(TunerBoss.class, EntityDataSerializers.INT);
    private final int[] activeByCategory = new int[FocusCategory.values().length];
    private final java.util.Set<com.tiaolvshi.goetytuner.focus.FocusEntry> activeVisualCasts =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final float[] orbHighlight = new float[3];
    private final float[] previousOrbHighlight = new float[3];

    public int getActiveCastCategories() { return entityData.get(DATA_CAST_CATEGORIES); }

    @Override
    public float getOrbHighlight(int category, float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, previousOrbHighlight[category], orbHighlight[category]);
    }

    private void changeCastCategory(com.tiaolvshi.goetytuner.focus.FocusEntry entry, int delta) {
        // A finish callback may itself fail and be followed by onCastFailed. Count it only once.
        if (delta > 0 ? !activeVisualCasts.add(entry) : !activeVisualCasts.remove(entry)) return;
        int index = entry.getCategory().ordinal();
        activeByCategory[index] = Math.max(0, activeByCategory[index] + delta);
        int mask = 0;
        for (int i = 0; i < activeByCategory.length; i++) {
            if (activeByCategory[i] > 0) mask |= 1 << i;
        }
        if (getActiveCastCategories() != mask) entityData.set(DATA_CAST_CATEGORIES, mask);
    }

    // ---- 同步数据（客户端动画/HUD用） ----
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(TunerBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PHASE2 = SynchedEntityData.defineId(TunerBoss.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_CAST_STATE = SynchedEntityData.defineId(TunerBoss.class, EntityDataSerializers.INT); // 0闲 1施法中

    // ---- 战斗子系统 ----
    private final FocusPoolManager pools = FocusPoolManager.createFightPools();
    private final MusicController music = new MusicController();
    private final DamageScoreTracker damageTracker = new DamageScoreTracker();
    private final SummonScoreTracker summonTracker = new SummonScoreTracker();

    /** 铺垫期轮换顺序：由配置角色序列驱动（一/二阶段各一条，见 TunerCommonConfig） */
    private int rotationIndex = 0;
    /** 【2026-08-18 第十三轮】铺垫施法失败退避tick（防止每tick空转换角色） */
    private int buildupRetryTimer = 0;

    private final CastChannel buildupChannel = new CastChannel(FocusCategory.ATTACK, this); // 通用工具通道（重音瞬发用）
    /** 高潮期并行通道（独立施法）：由配置角色序列构建，进入二阶段时重建 */
    private CastChannel[] climaxChannels = new CastChannel[0];
    /** 铺垫期每类轮换通道 + 当前活跃引用 */
    private final java.util.EnumMap<FocusCategory, CastChannel> buildupChannels = new java.util.EnumMap<>(FocusCategory.class);
    @Nullable
    private CastChannel activeBuildupChannel;

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.goetytuner.tuner"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    // ---- 锁血 ----
    // lockMark 语义（V2）：已触发的锁血次数。
    // 0 = 满血未锁过；每次跌破下一档地板时 +1；maxMark = maxHealth/interval。
    // 地板序列：maxHealth-interval*1, maxHealth-interval*2, ..., maxHealth-interval*(maxMark-1)。
    // 当 lockMark >= maxMark-1 时地板<=0，boss 可被正常击杀。
    // 例（当前默认）：maxHealth=216, interval=18 → maxMark=12，地板 198/180/162/…/18，
    // 共 11 次锁血回弹；第 12 档（lockMark=12）后锁血耗尽，可被正常击杀。
    // 二阶段入口 = 第 6 档（108 血 = 半血），见 phase2LockMark。
    private int lockMark = 0;

    /**
     * 【0.0.9】/kill 后门标记：管理员用 {@code /kill} 击杀时置位，令锁血体系的一切保护让路。
     *
     * <p>字节码实证的调用链：{@code KillCommand} → {@code Entity.kill()} →（虚分派）
     * {@code LivingEntity.kill()} → {@code hurt(damageSources().genericKill(), Float.MAX_VALUE)}。
     * 因此只要在 {@code hurt} 里识别 {@code DamageTypes.GENERIC_KILL} 即可精准区分"管理员指令"
     * 与"玩家伤害"，无需给命令加特殊权限判断。
     *
     * <p>置位后：宽限期免疫跳过、致死截断跳过、{@link #maintainDeathState()} 不做回弹、
     * {@link #canStillRevive()} 返回 false（于是 {@code remove(KILLED)} 也不再拦截）。
     * 击杀完成（实体被移除）后标记自然失效；若该次伤害被其它模组取消，则下一个正常 tick 会清除标记。
     */
    private boolean adminKillPending = false;

    /**
     * 【0.0.17】索命聚晶（{@code goety:death} 伤害）后门标记。
     *
     * <p>起因（用户反馈）：玩家用**索命聚晶**打中调律师时，Boss 会进入
     * "动画已经死了、血量却回弹"的破状态——因为索命造成的是"等同于目标当前生命值"的致死伤害，
     * 被锁血体系拦住后又复活，动画却留在了客户端。
     *
     * <p>Goety 侧的实现已反编译核实（{@code KillingSpell.SpellResult}）：
     * {@code ModDamageSource.deathCurse(target)} → {@code MobUtil.hurtCalculation(...)} →
     * {@code target.hurt(source, amount)}。而 {@code ModDamageSource.DEATH} 对应的伤害类型是
     * **{@code goety:death}**（jar 内 {@code data/goety/damage_type/death.json}，
     * {@code message_id = "goety.death"}；静态初始化里 {@code create("death")} → {@code putstatic DEATH}）。
     * 所以它能被 {@code source.is(ResourceKey)} 精确识别，**且不需要编译期依赖 Goety 的类**
     * （只用一个资源位置字符串；未装 Goety 时该键永不匹配）。
     *
     * <p>置位语义与 {@link #adminKillPending} 一致：宽限期免疫跳过、致死截断跳过、
     * 限伤/限DPS 也跳过、{@link #maintainDeathState()} 不回弹、{@link #canStillRevive()} 返回 false。
     * 也就是**"索命成功命中 = 真的能杀"**，这正是这个聚晶该有的样子（代价是施法者要承受目标当前生命值 125% 的反噬）。
     */
    private boolean deathCursePending = false;

    /** Goety 索命聚晶的伤害类型：{@code goety:death}（见 {@link #deathCursePending} 的实证说明）。 */
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType>
            GOETY_DEATH_CURSE = net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.DAMAGE_TYPE,
            new net.minecraft.resources.ResourceLocation("goety", "death"));
    // 【2026-08-18 第十三轮】锁血宽限期：触发锁血后 graceTicks 内血量持续钉在 lockGraceFloor，
    // 让"锁血"有存在感（防高频/多段伤害穿透），窗口结束才继续掉血。0=关闭。
    private int lockGraceTicks = 0;
    private float lockGraceFloor = 0.0F;

    // 【任务#118 第三十五轮】仪式召唤快照：召唤瞬间祭坛 slot0 法杖的完整副本（含玩家原聚晶、
    // 附魔、无法破坏等全部 NBT）。战斗期间 Boss 主手法杖会被聚晶通道持续改写（installFocus
    // extract+insert），死亡掉落以此快照为准（见 WandUpgradeEvents），避免玩家原聚晶凭空消失。
    private ItemStack originalWand = ItemStack.EMPTY;

    // 【2026-08-18 第十五轮】仇恨门控：有仇恨才演奏（音乐推进/阶段行为/施法的总开关），
    // 同步到客户端供 HUD 在非演奏状态隐藏音乐条。
    private boolean musicPlaying = false;
    // 【2026-08-20 第二十一轮】上tick演奏状态（检测脱战→演奏边沿：乐谱归零+立即同步）
    private boolean wasPlayingForMusic = false;

    // ---- 计时 ----
    private int teleportTimer = 0;
    // 【第二十三轮】非玩家瞬移追击独立计时（与玩家追击 teleportTimer 互不干扰）
    private int nonPlayerTeleportTimer = 0;
    // 【第三十一轮】逐跳追击链状态：true=正处于连续小瞬移追击中。
    // 链内相邻两跳间隔 = teleportHopInterval（默认10tick）；链结束（贴近/脱离区间）后
    // 恢复正常冷却 teleportInterval，避免无限高频跳。
    private boolean chaseChainActive = false;

    // 【第三十一轮】施法状态计数：并行高潮通道下"任一通道前摇中"即 DATA_CAST_STATE=1。
    // 此前 onCastFinish 无条件置 0，通道 A 完成而 B 仍在施法时状态被误清；
    // 且 CastChannel 异常自愈路径不经过 finish/interrupt，状态会永久卡在 1。
    private int activeWarmups = 0;

    // 【第三十一轮】二阶段进场重音连发：进入二阶段时连续 N 次重音击退脉冲
    //（每 PHASE2_ENTRY_BURST_INTERVAL tick 一次，音调逐次递升，"调音上行"起手）
    private int phase2EntryBurstRemaining = 0;
    private int phase2EntryBurstTimer = 0;
    private static final int PHASE2_ENTRY_BURST_INTERVAL = 5;

    // ---- 嘲讽彩蛋（【2026-08-20 第二十四轮】一阶段专属，运动层表现，不打断施法/阶段行为）----
    // 状态机：0=待机 1=蹲起循环 2=反向跳跃逃跑 3=蹲望（下蹲看目标一会再站起）
    private int tauntState = 0;
    private int tauntTimer = 0;          // 当前子阶段剩余tick
    private int tauntCycle = 0;          // 已完成的蹲起轮数
    private boolean tauntCrouched = false;
    private int tauntCooldown = 0;       // 结束后冷却（防连发）
    private double tauntFleeX = 0.0D, tauntFleeZ = 0.0D; // 逃跑目标点
    // 数值常量（彩蛋，刻意不进配置文件；要调频率改 TAUNT_TRIGGER_CHANCE 即可）
    private static final float TAUNT_TRIGGER_CHANCE = 0.002F; // 条件满足时每tick触发概率（平均~25秒一次）
    private static final int TAUNT_CROUCH_TICKS = 4;          // 每次下蹲持续
    private static final int TAUNT_STAND_TICKS = 4;           // 每次站起持续
    private static final int TAUNT_ROUNDS = 4;                // 蹲起轮数
    private static final int TAUNT_STARE_MIN = 40;            // 蹲望最短时长
    private static final int TAUNT_STARE_EXTRA = 30;          // 蹲望随机加长上限
    private static final int TAUNT_FLEE_TICKS = 26;           // 反向逃跑时长（0.25格/tick≈5格+跳跃损耗）
    private static final int TAUNT_FLEE_DISTANCE = 5;         // 逃跑距离（格）
    private static final int TAUNT_COOLDOWN_TICKS = 200;      // 冷却10秒
    // 【第二十二轮】二阶段铺垫期攻击施法计数：每 N 次（phase2BuildupArcEveryN，默认3=一轮"222"轮换）
    // 攻击施法才触发一次半圆弧走位瞬移（原先每次攻击施法前都瞬移，过于频繁）
    private int phase2ArcCastCounter = 0;
    private int syncTimer = 0;
    // 【2026-08-18 第十三轮】二阶段仆从连续清理：史莱姆类召唤物死亡会分裂出更小的个体，
    // 一次性清理杀不干净，改为进入二阶段立即清1轮 + 再排2轮（每10tick）。
    private int phase2MinionCleansRemaining = 0;
    private int phase2CleanTimer = 0;

    public TunerBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        this.setPersistenceRequired();
        // 【2026-08-18 第八轮修复】主手常驻一把法杖（需具备 ITEM_HANDLER capability）。
        // 崩溃根因：SoulUsingItemHandler.get(stack) 要求物品具备 ITEM_HANDLER capability，
        // 而 IWand.initCapabilities 正是提供 SoulUsingItemCapability 的入口；此前主手为空，
        // 空 ItemStack 无 capability → orElseThrow 抛 "ItemStack is missing item capability"。
        // 施法前由 BossWandHelper.installFocus 换装当前聚晶（见 CastChannel.beginCast）。
        //
        // 【0.0.19】由 goety:dark_wand 换成**本模组自备的 goetytuner:tuner_wand**：
        // 长按类法术（腐化光束等）要求施法者"真的在使用一把装着聚晶的 IWand"
        // （MobUtil.isSpellCasting），而 DarkWand.onUseTick 对非玩家施法者会走
        // failParticles + FIRE_EXTINGUISH 分支（冒白烟、响灭火音），所以必须换成行为干净的
        // TunerWand。外观完全一致（模型继承 goety:item/dark_wand）。详见 TunerWand 的类注释。
        this.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(com.tiaolvshi.goetytuner.init.ModItems.TUNER_WAND.get()));
        // 【0.0.19】把这把杖标成"永不掉落"。
        //
        // 为什么要显式写：**Boss 的法杖与玩家的法杖是两条完全不同的链路**，而"击败 Boss 获得
        // 强化法杖"这个设计针对的是**玩家自己那把**（仪式激活时把玩家手持的法杖 `copy()` 成
        // originalWand 快照，死亡时由 WandUpgradeEvents 掉这份快照 + 调律加成 NBT）。
        // Boss 主手这把只是**施法载体**，玩家拿到它毫无用处（`TunerWand.onUseTick` 是空实现、
        // `use()` 返回 PASS ⇒ 不能施法）。若它因掉落概率漏出去，玩家会得到一把"看起来像暗法杖
        // 却不能用"的废品，还会连带掉出当时装在里面的聚晶。
        //
        // 实测：`Mob.getEquipmentDropChance(MAINHAND)` 读的是 `handDropChances[0]`，其默认值本来就是
        // 0.0F（`javap` 反编译确认：HAND 分支取 handDropChances，ARMOR 分支取 armorDropChances，
        // 其余分支 `fconst_0`），所以**本来就不会掉**。这里显式设 0 是为了把意图写进代码，
        // 防止将来有人给 Boss 加装备/改掉落概率时无意中把这条设计破坏掉。
        this.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
        // 应用 config 数值：实体在游戏内创建时 config 必已加载。
        // 注意 createAttributes() 在 EntityAttributeCreationEvent（注册阶段）执行，
        // 彼时 config 尚未加载，只能使用默认常量，故此处按 config 覆盖（保持配置可调）。
        var maxHealthAttr = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(TunerCommonConfig.BOSS_MAX_HEALTH.get());
        }
        var armorAttr = this.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            armorAttr.setBaseValue(TunerCommonConfig.EQUIVALENT_ARMOR.get());
        }
        var followAttr = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (followAttr != null) {
            followAttr.setBaseValue(TunerCommonConfig.BOSS_TARGET_RANGE.get());
        }
        rebuildClimaxChannels();
        this.setHealth(this.getMaxHealth());
    }

    // ================= 角色序列（配置驱动） =================

    /**
     * 配置数字串 → 功能分块序列。
     * 映射：1=防御 2=攻击 3=召唤 4=其他。非法字符忽略；全非法时回退默认"123"。
     *
     * <p>【0.0.18】实现已上提到 {@link FocusCategory#parseRoleSpec(String)}：调律师仆从
     * （{@code TunerServant}）也要按同一种序列轮换施法，两处各自抄一份是本项目明令避免的做法。
     * 这里保留一个薄委托，避免改动 Boss 侧既有的 4 个调用点。
     */
    private static FocusCategory[] parseRoleSpec(String spec) {
        return FocusCategory.parseRoleSpec(spec);
    }

    /** 当前（按一/二阶段）铺垫轮换序列 */
    private FocusCategory[] currentRotation() {
        return parseRoleSpec(music.isPhase2()
                ? TunerCommonConfig.PHASE2_BUILDUP_ROTATION.get()
                : TunerCommonConfig.PHASE1_BUILDUP_ROTATION.get());
    }

    /** 按当前阶段重建高潮并行通道（进入二阶段时必须调用） */
    private void rebuildClimaxChannels() {
        FocusCategory[] roles = parseRoleSpec(music.isPhase2()
                ? TunerCommonConfig.PHASE2_CLIMAX_CHANNELS.get()
                : TunerCommonConfig.PHASE1_CLIMAX_CHANNELS.get());
        climaxChannels = new CastChannel[roles.length];
        for (int i = 0; i < roles.length; i++) {
            climaxChannels[i] = new CastChannel(roles[i], this);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        // 【2026-08-18 第七轮修复】此方法在 EntityAttributeCreationEvent（mod 注册阶段）
        // 被调用，config 尚未加载（Forge: Cannot get config value before config is loaded），
        // 故用与 TunerCommonConfig 默认值一致的常量；实际数值在 TunerBoss 构造函数按 config 覆盖。
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 216.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.FOLLOW_RANGE, 96.0D)
                .add(Attributes.ARMOR, 16.0D) // 等效护甲16（客户端不显示：渲染器无armor层）
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PHASE, BossPhase.BUILDUP.ordinal());
        this.entityData.define(DATA_PHASE2, false);
        this.entityData.define(DATA_CAST_STATE, 0);
        this.entityData.define(DATA_CAST_CATEGORIES, 0);
    }

    @Override
    protected void registerGoals() {
        // 【第二十九轮】移除 LookAtPlayerGoal / RandomLookAroundGoal：这两个 Goal 每tick与
        // aiStep 的手动 setLookAt 竞争覆盖（Goal 在 serverAiStep 内 tick，晚于/早于我们的调用
        // 交替生效），导致 boss 目光漂移、随机张望。改为 aiStep 有仇恨时每tick锁定目标（全神贯注）。
        // 施法行为在 customServerAiStep 中由音乐阶段驱动，不用Goal系统（阶段强相关+可并行）
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearerAttackGoal());
    }

    private class NearerAttackGoal extends NearestAttackableTargetGoal<Player> {
        NearerAttackGoal() {
            // 【2026-08-18 第十三轮】mustSee=false：半径内即可锁定，不要求视线可见
            super(TunerBoss.this, Player.class, false);
        }
    }

    // ================= 主循环 =================

    /**
     * 【第三十三轮】死亡回弹主检测。**0.0.11 更正了本注释原先的错误前提**。
     *
     * <p><b>原注释（错误）</b>：曾写"反编译确认 LivingEntity.tick 的 AI 区块为
     * {@code if (isDeadOrDying()) {...} else if (isEffectiveAi()) { serverAiStep() }}——
     * 死亡后 aiStep() 根本不再执行，原 applyLockHealth 的死亡自愈分支是死代码"。
     *
     * <p><b>0.0.11 反编译实证（事实）</b>：
     * <ul>
     *   <li>{@code LivingEntity.tick()} 共 366 行字节码，其中 {@code aiStep()} 只有 **1 处调用、
     *       偏移 179、完全无条件**；`isDeadOrDying` / `serverAiStep` / `tickDeath` 在该方法内
     *       **一次都没有出现** ⇒ 原文把 {@code aiStep()} 与 {@code serverAiStep()} 混为一谈，
     *       **死亡期间 {@code aiStep()} 照常执行**（原假设不成立）。</li>
     *   <li>真正被死亡把关的是 {@code baseTick()}：偏移 357 判 {@code isDeadOrDying()} →
     *       偏移 375 调 {@code tickDeath()}；{@code tickDeath()} 里 `deathTime++`，
     *       {@code >= 20} 且非客户端且未移除时播实体事件 60 并 {@code remove(KILLED)}。</li>
     * </ul>
     *
     * <p><b>所以本覆写是必需的</b>：{@code tickDeath()} 在 {@code baseTick()}（即 {@code super.tick()} 内部）
     * 才递增 {@code deathTime}，而 {@code applyLockHealth()} 又在其后无条件执行——两者都不是回弹该待的地方。
     * 这里在 {@code super.tick()} **之前**统一维护死亡状态，是唯一能同时满足
     * "尊重 /kill 后门" 与 "同步复位客户端死亡动画" 的时机。每 tick 调用，
     * 但热路径只有两次读取（{@code getHealth() <= 0} 判定 + {@code deathTime} 字段），
     * 真正做事的复位/回弹分支只在确实处于死亡或脏状态时才进入（见 {@link #maintainDeathState()}）。
     */
    @Override
    public void tick() {
        if (this.level().isClientSide) {
            // 【0.0.17】客户端自愈：血量已经回正、死亡动画却还挂着 → 本地复位。
            //
            // 起因（用户反馈）：被索命聚晶打中后会出现"动画死了但血量回弹"的破状态。根因是
            // **deathTime 不是同步数据**（见 SEntityRevivePacket 的实证），客户端的 `deathTime`
            // 由客户端自己的 `LivingEntity.tickDeath()` 递增，而它只看**客户端本地的血量**。
            // 服务端复活时只发**一次**复位包；若那一刻客户端的血量同步还没落地，
            // 客户端会在复位后**又自己把 deathTime 加上去**，此后血量虽然变正、动画却再没人清 ⇒ 永久躺着。
            //
            // 服务端侧的"情形 A"（{@link #maintainDeathState()}）治不了这一侧，所以在客户端补一条对称的自愈：
            // 客户端同样知道血量（DATA_HEALTH_ID 是同步数据），血量 > 0 而 deathTime > 0 就是脏状态，直接清。
            // resetDeathAnimation() 只在服务端发包，这里调用不会产生任何网络流量。
            if (this.deathTime > 0 && this.getHealth() > 0.0F) {
                this.resetDeathAnimation("client stale death animation (health=" + this.getHealth() + " > 0)");
            }
            int mask = isAlive() ? getActiveCastCategories() : 0;
            for (int i = 0; i < 3; i++) {
                previousOrbHighlight[i] = orbHighlight[i];
                orbHighlight[i] = net.minecraft.util.Mth.clamp(orbHighlight[i]
                        + ((mask & (1 << i)) != 0 ? 1F / 3 : -1F / 3), 0, 1);
            }
        }
        if (!this.level().isClientSide) {
            maintainDeathState();
            // 【0.0.20】把「强健」(goety:buff) 等级换算成法术强度（SPELL_POTENCY，ADDITION）。
            // 用户要求"只对调律师生效"⇒ 只有本模组的两个实体调用这个方法；
            // 本体今天并不自施强健（二阶段给的是原版力量，见 tickPhase2Buffs），
            // 所以这里平时是"确保没有该 modifier"的幂等短路 —— 将来若给本体加强健，它自动生效。
            BuffSpellPower.tick(this);
        }
        super.tick();
    }

    /**
     * 【0.0.8】死亡状态维护（每 tick，死亡动画期同样运行）。分两种情形：
     *
     * <p><b>⚠️ 0.0.11：这是全类**唯一**的死亡回弹实现。</b>原先 {@code applyLockHealth()} 里另有一段
     * 重复的"死亡自愈"分支（注释误称为死代码），它既绕过 `/kill` 后门、又不复位客户端动画，
     * 已在 0.0.11 删除，改为在 {@code applyLockHealth()} 开头直接对死亡状态早退。**任何新的回弹需求
     * 都应加在这里，不要再在锁血路径里另起一套。**
     *
     * <p><b>情形 A —— 脏状态清理（本轮新增）</b>：血量已经 &gt; 0，但死亡动画还挂着
     * （{@code deathTime > 0}）。实测由多种"特殊手段"造成，例如：
     * 其它模组直接 {@code setHealth(0)}/{@code kill()}、被 {@code BYPASSES_INVULNERABILITY}
     * 类伤害打空血量、外部复活/回血手段只补血未清动画，甚至本模组自己的锁血宽限期地板与
     * 二阶段回血在死亡动画期间把血量拉回正数。
     * 这种状态**不消耗锁血档位**（否则一次外部救活会白吃一档），只把动画状态重置干净，
     * 并通知客户端（客户端那份 {@code deathTime} 不是同步数据，见 {@link SEntityRevivePacket}）。
     *
     * <p><b>情形 B —— 真的死了（血量 ≤ 0）</b>：锁血未耗尽则回弹到下一档地板（原第三十三轮逻辑）。
     * 锁血耗尽（{@code lockMark >= maxMark-1}）时放行正常击杀。
     */
    private void maintainDeathState() {
        boolean dying = this.isDeadOrDying();
        boolean inDeathAnimation = this.deathTime > 0;
        // 【0.0.9】/kill 后门：管理员指令造成的死亡不做任何回弹或清理，让死亡流程正常走完
        // （remove(KILLED) 也会因 canStillRevive() 返回 false 而被放行）。
        // 【0.0.17】索命聚晶后门同理：goety:death 打死的就让它死透，不做回弹。
        if (this.adminKillPending || this.deathCursePending) {
            return;
        }
        if (!dying && !inDeathAnimation) {
            // 状态正常 → 清掉可能残留的后门标记（例如那次伤害被其它模组取消，Boss 并没死）
            this.adminKillPending = false;
            this.deathCursePending = false;
            return; // 状态正常
        }

        // ---- 情形 A：血量 > 0 却仍在死亡动画 → 脏状态，只清动画、不吃档位 ----
        if (!dying && this.getHealth() > 0.0F) {
            this.resetDeathAnimation("stale death animation (health=" + this.getHealth() + " > 0)");
            return;
        }

        // ---- 情形 B：真的死了，按锁血档位回弹 ----
        if (!TunerCommonConfig.LOCK_DEATH_REVIVE.get()) {
            return;
        }
        int interval = TunerCommonConfig.LOCK_HEALTH_INTERVAL.get();
        float maxHealth = TunerCommonConfig.BOSS_MAX_HEALTH.get();
        int maxMark = (int) (maxHealth / interval);
        if (lockMark >= maxMark - 1) {
            return; // 锁血耗尽，可被正常击杀
        }
        float reviveHp = Math.max(maxHealth - interval * (float) (lockMark + 1), 1.0F);
        this.setHealth(reviveHp);
        lockMark++;
        this.resetDeathAnimation("death-revive");
        GoetyTuner.LOGGER.info("[Tuner] Death-revive → {} HP (mark={}/{})", reviveHp, lockMark, maxMark);
        if (this.level() instanceof ServerLevel serverLevel) {
            onLockTriggered(serverLevel, reviveHp, maxMark, "Revived from death");
        }
    }

    /**
     * 【0.0.8】把服务端与客户端的死亡/受伤动画状态一起清零。
     *
     * <p>必须三样都清（原因见 {@code ClientDeathAnimation}）：
     * {@code deathTime}（倒下旋转）、{@code hurtTime}/{@code hurtDuration}（红色受伤叠加层）、
     * 以及万一被置成 {@code Pose.DYING} 的姿态；随后把复位指令发给所有追踪者——
     * 1.20.1 的 {@code deathTime} **不是同步数据**（全游戏只有 {@code LocalPlayer.resetPos} 会归零），
     * 不通知客户端的话，服务端活了、客户端模型还躺着。
     */
    private void resetDeathAnimation(String reason) {
        this.deathTime = 0;
        this.hurtTime = 0;
        this.hurtDuration = 0;
        if (this.getPose() == Pose.DYING) {
            this.setPose(Pose.STANDING);
        }
        if (this.level() instanceof ServerLevel) {
            TunerNetwork.sendToTracking(new SEntityRevivePacket(this.getId()), this);
        }
        GoetyTuner.LOGGER.debug("[Tuner] Death animation reset ({})", reason);
    }

    /**
     * 【0.0.8】拦住在"锁血未耗尽"时发生的 {@code remove(KILLED)}。
     *
     * <p>某些特殊手段会绕过 {@code hurt()} 直接把血量打到 0（{@code /kill} 的 genericKill、
     * 其它模组的 {@code setHealth(0)}/{@code kill()}）。死亡动画跑满 20 tick 后原版会执行
     * {@code remove(KILLED)}——**一旦移除就无法回弹**（实体已不在世界里，血量再高也没用）。
     * 这里把这一次移除挡下，交给下一 tick 的 {@link #maintainDeathState()} 回弹。
     *
     * <p>此判断被合并在原有 {@code remove} 覆写里（那里还要注销 CombatEvents 注册），
     * 只拦 {@code KILLED}：区块卸载（UNLOADED_*）、和平模式消失（DISCARDED）等一律放行；
     * 锁血已耗尽或总开关关闭时也不再拦（正常击杀路径保持原样）。
     */
    private boolean canStillRevive() {
        if (this.adminKillPending) {
            return false; // 【0.0.9】/kill 后门：不拦 remove(KILLED)，让管理员能真正击杀
        }
        if (this.deathCursePending) {
            return false; // 【0.0.17】索命后门：goety:death 打死的也不拦 remove(KILLED)
        }
        if (!TunerCommonConfig.LOCK_DEATH_REVIVE.get()) {
            return false;
        }
        int interval = TunerCommonConfig.LOCK_HEALTH_INTERVAL.get();
        float maxHealth = TunerCommonConfig.BOSS_MAX_HEALTH.get();
        int maxMark = (int) (maxHealth / interval);
        return lockMark < maxMark - 1;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();

        // 0. 【2026-08-18 第十五轮】仇恨门控：有仇恨才演奏——无有效目标时打断全部施法、
        //    暂停音乐推进与一切阶段行为（创造/旁观玩家不会被索为目标，故不会演奏）。
        //    链路：有仇恨 → 演奏（music.tick）→ 音乐状态 → 触发施法；缺一不可。
        LivingEntity aggroTarget = this.getTarget();
        boolean hasAggro = aggroTarget != null && aggroTarget.isAlive() && !aggroTarget.isRemoved();
        this.musicPlaying = hasAggro;

        // 【2026-08-20 第二十一轮】仇恨边沿：脱战→演奏时乐谱归零（客户端音频从头起播，两端0点对齐），
        // 并立即广播同步包降低客户端起播延迟；演奏→脱战/boss死亡由客户端 BossMusicManager 停止音频。
        if (this.musicPlaying && !this.wasPlayingForMusic) {
            music.restart();
            syncTimer = 0;
        }
        this.wasPlayingForMusic = this.musicPlaying;

        if (hasAggro) {
            // 0.5 【第二十九轮】全神贯注：有仇恨时目光始终锁定仇恨目标（每tick刷新，覆盖全部阶段，
            //     含此前不注视的低谷期/瞬移冷却期）。配合已移除的 Look/RandomLook Goal，
            //     头部朝向不再有任何随机漂移；转身速度上限 50°/tick 保证跟手不僵硬。
            this.getLookControl().setLookAt(aggroTarget, 50F, 50F);

            // 1. 音乐推进（服务端权威）
            // 【第二十二轮】先记录上一tick阶段，供分段切换判定"从什么阶段进入"
            BossPhase prevPhase = BossPhase.byOrdinal(this.entityData.get(DATA_PHASE));
            BossPhase phase = music.tick();
            // 【0.0.5 性能】仅在阶段真正变化时写同步数据：阶段通常持续数百 tick，
            // 每 tick 无脑 set 会持续触碰 SynchedEntityData 的脏标记路径（并可能引发无谓的同步开销）。
            int phaseOrdinal = phase.ordinal();
            if (this.entityData.get(DATA_PHASE) != phaseOrdinal) {
                this.entityData.set(DATA_PHASE, phaseOrdinal);
            }

            // 2. 二阶段判定已移至第7步（applyLockHealth 之后），基于 lockMark 而非血量，
            //    确保锁血回弹先于阶段切换，避免高伤跳过锁血直接进二阶段。

            // 3. 阶段切换处理：进入高潮/低谷强制打断当前施法 + 效果清除
            if (music.consumeSegmentChanged()) {
                if (phase == BossPhase.CLIMAX) {
                    interruptAllCasts(level);
                    // 进入高潮：强效解除身上所有负面效果（侵蚀/黑暗/中毒/凋零等）
                    cleanseEffects(level, false);
                } else if (phase == BossPhase.VALLEY) {
                    interruptAllCasts(level);
                    // 进入低谷：强行解除身上所有正面效果（速度/力量/抗性/吸收等）
                    cleanseEffects(level, true);
                } else if (phase == BossPhase.BUILDUP && prevPhase == BossPhase.CLIMAX && music.isPhase2()) {
                    // 【第二十二轮】二阶段低谷段已按铺垫处理（MusicController 相位转换）：
                    // 高潮→铺垫切换时必须打断高潮并行通道，否则通道冻结在busy、
                    // 已锁池的聚晶永远不归还（一阶段该路径走 VALLEY 分支自带打断）
                    interruptAllCasts(level);
                }
            }

            // 4. 冷却池推进
            pools.tickCooldowns();

            // 5. 阶段行为
            switch (phase) {
                case BUILDUP -> tickBuildup(level);
                case CLIMAX -> tickClimax(level);
                case VALLEY -> tickValley(level);
            }

            // 5.2 【第二十四轮】嘲讽彩蛋：一阶段被激怒且目标在6-12格时随机触发
            //     （蹲起4轮→反向跳跃逃跑 / 或蹲望片刻；仅运动层表现，不打断施法）
            tickTaunt(level);

            // 5.25 【第三十一轮】施法瞄准蹲姿：前摇期间 boss 保持下蹲瞄准（Pose.CROUCHING，
            //      客户端 TunerModel 已映射原版蹲姿动画）。优先级高于嘲讽彩蛋的蹲起循环：
            //      嘲讽触发已加"非施法中"门控（二者不会同时开始），但嘲讽进行中允许施法，
            //      此时蹲姿以施法瞄准为准。逃跑子状态(2)例外——移动中保持站姿观感更自然。
            // 【0.0.5 性能】加 getPose() != CROUCHING 判断：前摇可持续数十 tick，
            //      原实现每 tick 都写一次 Pose 同步数据（setPose 无相等性早退），纯属重复写入。
            if (this.entityData.get(DATA_CAST_STATE) == 1 && tauntState != 2
                    && this.getPose() != Pose.CROUCHING) {
                this.setPose(Pose.CROUCHING);
            }

            // 5.3 【第二十六轮】二阶段周期性自施药水 + 回血机制
            if (music.isPhase2()) {
                tickPhase2Buffs();
                tickPhase2Regen();
            }

            // 6. 重音
            if (music.consumeAccent()) {
                onAccent(level, phase);
            }
        } else {
            // 无仇恨：立即停演（打断全部施法；音乐冻结在当前进度，恢复仇恨后继续演奏）
            interruptAllCasts(level);
        }

        // 6.5 【第二十二轮】瞬移追击/异常位置修正：全程每tick判定（原先只在铺垫期与
        //     二阶段高潮期调用——一阶段高潮/低谷期、或脱战边缘玩家跑远时不会追击，观感即"瞬移追击失效"）。
        //     目标=最近可索敌玩家（不依赖仇恨），触发仍受距离区间
        //     (teleportMinDistance, teleportChaseMaxDistance) 与瞬移间隔限制，不会跨图追人。
        //     ★注意：本步起已在 hasAggro 仇恨门控**之外**，故编号接在第 6 步之后。
        tickTeleport(level);

        // 6.6 【第二十三轮】非玩家瞬移追击（独立新逻辑，未改动 tickTeleport）：
        //     当 boss 处于被激怒状态（存在存活仇恨目标）、仇恨目标不是玩家、
        //     且 96格内没有任何可参战的生存/冒险模式玩家时生效——
        //     典型场景：boss 被铁傀儡/其他生物激怒、或玩家已全部远离，boss 不会卡在远处干瞪眼。
        //     有可参战玩家在场时本逻辑不介入（追击一律交给玩家版 tickTeleport）。
        tickTeleportNonPlayer(level);

        // 6.7 【2026-08-18 第十四轮】二阶段仆从连续清理（4轮，每15tick，防史莱姆分裂残留）
        if (phase2MinionCleansRemaining > 0 && --phase2CleanTimer <= 0) {
            cleanOwnedMinions(level);
            phase2MinionCleansRemaining--;
            phase2CleanTimer = 15;
        }

        // 7. 锁血与上限复位
        applyLockHealth();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

        // 7.5 【2026-08-18 第十四轮】二阶段判定（基于lockMark，确保锁血回弹先于阶段切换）
        if (!music.isPhase2() && lockMark >= TunerCommonConfig.PHASE2_LOCK_MARK.get()) {
            music.enterPhase2();
            this.entityData.set(DATA_PHASE2, true);
            onEnterPhase2(level);
        }

        // 7.6 【第三十一轮】二阶段进场重音连发：进入二阶段时连续 phase2EntryBurstCount 次
        //     重音击退脉冲（每5tick一次、音调逐次递升），气势上"砸"出二阶段开场。
        //     放在仇恨门控之外：进场瞬间必然有仇恨，即使脱战边沿也把连发打完。
        if (phase2EntryBurstRemaining > 0 && --phase2EntryBurstTimer <= 0) {
            phase2EntryBurstTimer = PHASE2_ENTRY_BURST_INTERVAL;
            int total = Math.max(0, TunerCommonConfig.PHASE2_ENTRY_BURST_COUNT.get());
            int index = Math.max(0, total - phase2EntryBurstRemaining);
            phase2EntryBurstRemaining--;
            accentKnockbackPulse(level, TunerCommonConfig.ACCENT_KNOCKBACK_CLIMAX.get(),
                    0.8F + index * 0.15F);
        }

        // 8. 音乐同步包
        if (--syncTimer <= 0) {
            syncTimer = music.getSyncInterval();
            broadcastMusicSync();
        }

        // 9. 动态评分结算（低频）
        damageTracker.tick(level, this, pools);
        summonTracker.tick(level, this, pools);
    }

    // ---- 铺垫：轮换单通道 + 传送行为 ----
    private void tickBuildup(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (target != null) {
            this.getLookControl().setLookAt(target, 30F, 30F);
        }
        // 【第二十六轮】二阶段铺垫期施法前摇 = phase2WarmupFactor()（按锁血档位递减）
        double warmupMult = music.isPhase2()
                ? phase2WarmupFactor()
                : 1.0D;
        if (activeBuildupChannel == null || !activeBuildupChannel.isBusy()) {
            // 【2026-08-18 第十三轮】beginCast 失败（抽空/条件不满足）时退避5tick再换下一个角色，
            // 避免每tick空转换角色造成"boss没在干活"的观感。
            if (buildupRetryTimer > 0) {
                buildupRetryTimer--;
            } else {
                FocusCategory[] rotation = currentRotation();
                FocusCategory next = rotation[rotationIndex % rotation.length];
                rotationIndex++;
                CastChannel ch = buildupChannelFor(next);
                boolean started = ch.beginCast(level, minionFillRatio(), isSummonBlocked(), warmupMult);
                if (started) {
                    activeBuildupChannel = ch;
                    buildupRetryTimer = 0;
                } else {
                    buildupRetryTimer = 5;
                }
            }
        } else {
            activeBuildupChannel.tick(level, minionFillRatio(), isSummonBlocked(), warmupMult);
        }
        // 瞬移追击已移至 aiStep（第二十二轮：全程生效，不限于铺垫期）
    }

    // ---- 高潮：多通道并行，不移动 ----
    private void tickClimax(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (target != null) {
            this.getLookControl().setLookAt(target, 30F, 30F);
        }
        this.getNavigation().stop();
        double mult = TunerCommonConfig.CLIMAX_WARMUP_MULTIPLIER.get();
        // 【第二十六轮】二阶段高潮期施法前摇再乘 phase2WarmupFactor()（按锁血档位递减：
        // 档位6=1.0，每升一档减10%，档位12=0.4；越打技能放得越快）
        if (music.isPhase2()) {
            mult *= phase2WarmupFactor();
        }
        for (CastChannel ch : climaxChannels) {
            if (!ch.isBusy()) {
                ch.beginCast(level, minionFillRatio(), isSummonBlocked(), mult);
            } else {
                ch.tick(level, minionFillRatio(), isSummonBlocked(), mult);
            }
        }
    }

    // ---- 低谷：无法施法，药水效果 ----
    private void tickValley(ServerLevel level) {
        this.getNavigation().stop();
        // boss：侵蚀12 + 黑暗
        // 【0.0.13】注意：这两个效果原本就是 `visible=false`（无粒子），所以玩家**看不出**它们生效——
        // 机制上确实每 tick 都在刷新（常驻），只是没有任何视觉反馈。见 applySelfEffect 的日志。
        applySelfEffect(new MobEffectInstance(GoetyEffects.SAPPED.get(), 30, 11, true, false));
        applySelfEffect(new MobEffectInstance(MobEffects.DARKNESS, 30, 0, true, false));
        // 仆从：生命恢复1、伤害吸收1、缓慢3、虚弱2
        for (Mob minion : ownedMinions(level)) {
            minion.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30, 0, true, false));
            minion.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 30, 0, true, false));
            minion.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2, true, false));
            minion.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 1, true, false));
        }
    }

    /**
     * 【0.0.13】给**自身**施加药水效果，并在被拒绝时打 WARN。
     *
     * <p>起因：审计「药水效果是否真的在生效」时发现，全类 8 处 {@code addEffect(...)} 的
     * **boolean 返回值都被丢弃**——而 `LivingEntity.addEffect` 的第一步就是 {@link #canBeAffected}
     * （本类覆写它来拒绝 SummonDown），Forge 的 `MobEffectEvent.Applicable` 也会在这一步否掉。
     * 一旦被拒，效果**静默失效**、日志里一个字都没有 ⇒ 只能进游戏靠肉眼猜。
     * 现在失败会留下 `[Tuner] addEffect rejected: ...` 痕迹，一次实测即可判定。
     */
    private void applySelfEffect(MobEffectInstance inst) {
        if (!this.addEffect(inst)) {
            GoetyTuner.LOGGER.warn("[Tuner] addEffect rejected: {} amp={} (被 canBeAffected / MobEffectEvent.Applicable 拦下)",
                    net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(inst.getEffect()),
                    inst.getAmplifier());
        }
    }

    // ---- 重音：斥力 + 无前摇施法 ----
    private void onAccent(ServerLevel level, BossPhase phase) {
        double strength = switch (phase) {
            case VALLEY -> {
                double v = TunerCommonConfig.ACCENT_KNOCKBACK_VALLEY.get();
                // 二阶段低谷：重音击退进一步加剧
                if (music.isPhase2()) {
                    v *= TunerCommonConfig.PHASE2_VALLEY_KNOCKBACK_MULTIPLIER.get();
                }
                yield v;
            }
            case CLIMAX -> TunerCommonConfig.ACCENT_KNOCKBACK_CLIMAX.get();
            default -> TunerCommonConfig.ACCENT_KNOCKBACK_BASE.get();
        };
        float pitch = switch (phase) {
            case CLIMAX -> 1.4F;
            case VALLEY -> 0.6F;
            default -> 0.9F;
        };
        // 【第三十一轮】击退+粒子+提示音提取为可复用脉冲（二阶段进场连发也调用）
        accentKnockbackPulse(level, strength, pitch);
        // 二阶段低谷：重音附带视角震颤（向附近玩家发摄像机抖动包）
        if (phase == BossPhase.VALLEY && music.isPhase2()) {
            int shakeTicks = TunerCommonConfig.ACCENT_SHAKE_TICKS.get();
            if (shakeTicks > 0) {
                float shakeStrength = TunerCommonConfig.ACCENT_SHAKE_STRENGTH.get().floatValue();
                for (ServerPlayer p : level.players()) {
                    if (p.distanceToSqr(this) < 48 * 48) {
                        TunerNetwork.sendToPlayer(
                                new com.tiaolvshi.goetytuner.network.SShakePacket(shakeTicks, shakeStrength), p);
                    }
                }
            }
        }
        // 无前摇释放其他类聚晶
        switch (phase) {
            case BUILDUP -> buildupChannel.instantCast(level, FocusCategory.OTHER);
            case CLIMAX -> {
                for (int i = 0; i < 3; i++) {
                    buildupChannel.instantCast(level, FocusCategory.OTHER);
                }
            }
            default -> {
            }
        }
    }

    /**
     * 【第三十一轮】重音击退脉冲（从 onAccent 提取，供其与二阶段进场连发共用）：
     * 以自身为中心把 8 格内存活生物沿径向推出 strength 强度，附三波冲击环+音符爆发粒子
     * 与阶段音调的紫水晶提示音。
     *
     * <p>【0.0.18】**四件事的实现已全部抽到 {@link com.tiaolvshi.goetytuner.combat.AccentRipple}**，
     * 因为新增的「调律波纹聚晶」要求与铺垫期重音**完全同款**——共用同一份代码
     * （同款=字面上同一处实现，而不是复制一份出来），本项目红线：同一职责只允许一处实现。
     * 本方法现在只剩"取参 + 转发"：力量/音调由调用方（阶段）决定，Boss 无"友军豁免"需求故传 null。
     */
    private void accentKnockbackPulse(ServerLevel level, double strength, float pitch) {
        com.tiaolvshi.goetytuner.combat.AccentRipple.knockback(level, this, strength, null);
        com.tiaolvshi.goetytuner.combat.AccentRipple.particles(level, this);
        com.tiaolvshi.goetytuner.combat.AccentRipple.wave(level, this);
        com.tiaolvshi.goetytuner.combat.AccentRipple.chime(level, this, pitch);
    }

    // ---- 传送行为 ----
    private void tickTeleport(ServerLevel level) {
        // 【2026-08-19 第十六轮】瞬移目标统一为"最近的能索敌玩家"（排除创造/旁观/死亡），
        // 不再依赖当前仇恨目标（仇恨目标可能不是最近玩家）。
        LivingEntity target = nearestTargetablePlayer(level);
        boolean badPosition = this.isInWater() || this.isInLava() || this.isInFluidType()
                || this.getBlockStateOn().isAir() || this.getY() < 0 || this.isInWall();

        // 异常位置（水里/岩浆/悬空/卡墙）：立即修正，不受主动瞬移间隔限制
        // 但有最小节流（TELEPORT_RESCUE_INTERVAL）防止每tick搜索方块
        if (target != null && badPosition) {
            if (teleportTimer <= 0) {
                // 【第三十一轮】目标悬空（飞行单位/空中坐骑）时目标附近可能整片无地面可站，
                // tryTeleportNear 16 次随机全部失败 → 悬空卡死。救援失败退化为逐跳逼近。
                if (!tryTeleportNear(level, target)) {
                    tryTeleportHop(level, target);
                }
                teleportTimer = TunerCommonConfig.TELEPORT_RESCUE_INTERVAL.get();
            }
            return;
        }

        if (teleportTimer > 0) {
            teleportTimer--;
            return;
        }
        if (target == null) {
            return;
        }
        // 【2026-08-19 第十六轮】追击区间：距离大于 teleportMinDistance 且小于 teleportChaseMaxDistance
        // 才瞬移（超过上限视为脱战距离，不跨越地图追人）
        double dist = this.distanceTo(target);
        boolean inChaseRange = dist > TunerCommonConfig.TELEPORT_MIN_DISTANCE.get()
                && dist < TunerCommonConfig.TELEPORT_CHASE_MAX_DISTANCE.get();
        if (inChaseRange) {
            // 【第三十一轮】追击瞬移改为"逐跳逼近"：每次只向目标方向瞬移
            // teleportHopDistance 格（默认8），跳完仍满足追击区间时经 teleportHopInterval
            //（默认10tick=0.5秒）后继续跳——远距离追击表现为一串连续小瞬移，
            // 且落点始终沿自身→目标连线推进，不再要求目标身边有地面（修空中目标追击bug）。
            tryTeleportHop(level, target);
            chaseChainActive = true;
            teleportTimer = TunerCommonConfig.TELEPORT_HOP_INTERVAL.get();
        } else if (chaseChainActive) {
            // 追击链结束（已贴近/超出追击上限）：恢复正常追击冷却
            chaseChainActive = false;
            teleportTimer = currentTeleportInterval();
        }
    }

    /**
     * 【第二十三轮】非玩家瞬移追击（独立于 {@link #tickTeleport}，未改动其任何逻辑）。
     *
     * 生效条件（全部满足）：
     * 1. boss 处于被激怒状态：存在存活且未移除的仇恨目标；
     * 2. 仇恨目标不是玩家（铁傀儡/召唤物/其他生物等）；
     * 3. teleportChaseMaxDistance（默认96格）内不存在任何可参战的生存/冒险模式玩家
     *    （创造/旁观/死亡玩家不算）——只要有玩家在场就一律让位给玩家版追击。
     *
     * 追击规则（【第三十一轮】与玩家版同步改为逐跳逼近）：距离在
     * (teleportMinDistance, teleportChaseMaxDistance) 区间内且独立计时器归零时，
     * 朝目标方向跳 teleportHopDistance 格（复用 tryTeleportHop）；
     * 链内间隔 teleportHopInterval，链结束恢复正常冷却。
     */
    private void tickTeleportNonPlayer(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (target == null || target instanceof Player || !target.isAlive() || target.isRemoved()) {
            return;
        }
        // 96格内有可参战玩家（生存/冒险、存活）→ 本逻辑不介入
        double guardRange = TunerCommonConfig.TELEPORT_CHASE_MAX_DISTANCE.get();
        for (var p : level.players()) {
            if (!p.isSpectator() && !p.isCreative() && p.isAlive()
                    && this.distanceToSqr(p) <= guardRange * guardRange) {
                return;
            }
        }
        if (nonPlayerTeleportTimer > 0) {
            nonPlayerTeleportTimer--;
            return;
        }
        double dist = this.distanceTo(target);
        if (dist > TunerCommonConfig.TELEPORT_MIN_DISTANCE.get()
                && dist < TunerCommonConfig.TELEPORT_CHASE_MAX_DISTANCE.get()) {
            tryTeleportHop(level, target);
            nonPlayerTeleportTimer = TunerCommonConfig.TELEPORT_HOP_INTERVAL.get();
        }
    }

    /**
     * 【第二十四轮】战略性嘲讽彩蛋（一阶段专属）。
     *
     * <p>触发条件（全部满足，随机判定）：非二阶段 + 被激怒（仇恨目标存活）+
     * 仇恨目标距离在 (6, 12] 格 + 冷却结束 + 每tick 0.2% 概率。
     *
     * <p>触发后二选一：
     * <ul>
     *   <li>70%：面向目标快速蹲起 4 轮（蹲/站各4tick）→ 向<b>远离目标方向</b>边跳边跑约5格；</li>
     *   <li>30%：仅下蹲看向目标 2~3.5 秒后站起（"有时会下蹲看向目标，过一段时间又站起"）。</li>
     * </ul>
     *
     * <p>实现要点（资源占用最小化）：
     * <ul>
     *   <li>不进 Goal 系统——aiStep 内联状态机，待机时每tick仅几次数值比较；
     *   <li>蹲姿用原版 {@link Pose#CROUCHING}（DATA_POSE 数据同步，零额外网络包），
     *       客户端 TunerModel 把 pose 映射到 HumanoidModel.crouching 即获原版蹲姿动画；
     *   <li>逃跑不用导航（避免与高潮"不移动"的 navigation.stop 每tick打架），
     *       直接 setDeltaMovement 朝目标点 + JumpControl 周期起跳，呈"一边跳一边跑"。
     * </ul>
     */
    private void tickTaunt(ServerLevel level) {
        if (tauntCooldown > 0) {
            tauntCooldown--;
        }
        LivingEntity target = this.getTarget();
        boolean targetValid = target != null && target.isAlive() && !target.isRemoved();

        if (tauntState == 0) {
            // 【第三十一轮】新增门控：施法中（前摇瞄准蹲姿进行时）不触发新嘲讽——
            // 否则嘲讽蹲起循环/逃跑会与施法瞄准蹲姿互相覆盖姿态，且逃跑朝向会带偏施法方向。
            // （嘲讽进行中允许施法：姿态以施法瞄准为准，见 aiStep 5.25）
            if (tauntCooldown > 0 || music.isPhase2() || !targetValid
                    || this.entityData.get(DATA_CAST_STATE) == 1) {
                return;
            }
            double dist = this.distanceTo(target);
            if (dist <= 6.0D || dist > 12.0D) {
                return;
            }
            if (this.random.nextFloat() >= TAUNT_TRIGGER_CHANCE) {
                return;
            }
            // 触发：停下当前移动，二选一进入序列
            this.getNavigation().stop();
            tauntCrouched = false;
            if (this.random.nextFloat() < 0.3F) {
                tauntState = 3; // 蹲望
                tauntTimer = TAUNT_STARE_MIN + this.random.nextInt(TAUNT_STARE_EXTRA);
            } else {
                tauntState = 1; // 蹲起循环
                tauntCycle = 0;
                tauntTimer = TAUNT_CROUCH_TICKS;
                tauntCrouched = true;
            }
        }

        switch (tauntState) {
            case 1 -> { // 快速蹲起循环（始终面向目标）
                if (!targetValid) {
                    endTaunt();
                    return;
                }
                this.getNavigation().stop();
                this.getLookControl().setLookAt(target, 30F, 30F);
                if (--tauntTimer <= 0) {
                    tauntCrouched = !tauntCrouched;
                    if (!tauntCrouched) {
                        tauntCycle++;
                    }
                    tauntTimer = tauntCrouched ? TAUNT_CROUCH_TICKS : TAUNT_STAND_TICKS;
                }
                this.setPose(tauntCrouched ? Pose.CROUCHING : Pose.STANDING);
                if (tauntCycle >= TAUNT_ROUNDS && !tauntCrouched) {
                    startTauntFlee(target); // 4轮完成 → 反向逃跑
                }
            }
            case 2 -> { // 反向跳跃逃跑（手动位移，不依赖导航）
                if (--tauntTimer <= 0) {
                    endTaunt();
                    return;
                }
                double dx = tauntFleeX - this.getX();
                double dz = tauntFleeZ - this.getZ();
                double dSqr = dx * dx + dz * dz;
                if (dSqr < 1.0D) { // 已到达逃跑点附近
                    endTaunt();
                    return;
                }
                double len = Math.sqrt(dSqr);
                this.setDeltaMovement(dx / len * 0.25D, this.getDeltaMovement().y, dz / len * 0.25D);
                // 朝向逃跑方向（yaw = atan2(z,x)*180/π - 90）
                float yaw = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
                this.setYRot(yaw);
                this.yBodyRot = yaw;
                this.setYHeadRot(yaw);
                // 边跑边跳：着地时每6tick起跳一次
                if (this.onGround() && tauntTimer % 6 == 0) {
                    this.getJumpControl().jump();
                }
            }
            case 3 -> { // 蹲望：下蹲看向目标，过段时间站起
                if (!targetValid) {
                    endTaunt();
                    return;
                }
                this.getNavigation().stop();
                this.getLookControl().setLookAt(target, 30F, 30F);
                this.setPose(Pose.CROUCHING);
                if (--tauntTimer <= 0) {
                    endTaunt();
                }
            }
            default -> {
            }
        }
    }

    /** 计算远离目标的逃跑落点（约5格），进入逃跑子状态 */
    private void startTauntFlee(LivingEntity target) {
        double dx = this.getX() - target.getX();
        double dz = this.getZ() - target.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01D) { // 与目标重叠：随机方向
            double ang = this.random.nextDouble() * Math.PI * 2.0D;
            dx = Math.cos(ang);
            dz = Math.sin(ang);
            len = 1.0D;
        }
        tauntFleeX = this.getX() + dx / len * TAUNT_FLEE_DISTANCE;
        tauntFleeZ = this.getZ() + dz / len * TAUNT_FLEE_DISTANCE;
        tauntState = 2;
        tauntTimer = TAUNT_FLEE_TICKS;
        this.setPose(Pose.STANDING);
    }

    /** 嘲讽结束：回站姿 + 进入冷却 */
    private void endTaunt() {
        tauntState = 0;
        tauntCooldown = TAUNT_COOLDOWN_TICKS;
        this.setPose(Pose.STANDING);
    }

    /** 最近的能索敌玩家（排除创造/旁观/死亡）；追击瞬移与异常位置救援的目标 */
    @Nullable
    private LivingEntity nearestTargetablePlayer(ServerLevel level) {
        net.minecraft.server.level.ServerPlayer nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (var p : level.players()) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive()) {
                continue;
            }
            double d = this.distanceToSqr(p);
            if (d < nearestDist) {
                nearestDist = d;
                nearest = p;
            }
        }
        return nearest;
    }

    /**
     * 主动瞬移间隔：二阶段乘以配置倍率。
     * 注意 phase2TeleportIntervalFactor 的范围是 0.1~1.0（默认 1.0 = 与一阶段相同），
     * 即该配置**只能缩短**二阶段间隔、无法拉长；下限 20 tick 兜底。
     */
    private int currentTeleportInterval() {
        int base = TunerCommonConfig.TELEPORT_INTERVAL.get();
        if (music.isPhase2()) {
            return Math.max(20, (int) (base * TunerCommonConfig.PHASE2_TELEPORT_INTERVAL_FACTOR.get()));
        }
        return base;
    }

    /** 在目标玩家半径16内寻找可站立方块传送；【第三十一轮】返回是否成功（失败时调用方可退化兜底） */
    private boolean tryTeleportNear(ServerLevel level, LivingEntity target) {
        int radius = TunerCommonConfig.TELEPORT_SEARCH_RADIUS.get();
        BlockPos center = target.blockPosition();
        for (int i = 0; i < 16; i++) {
            int dx = this.random.nextInt(-radius, radius + 1);
            int dz = this.random.nextInt(-radius, radius + 1);
            BlockPos pos = center.offset(dx, 0, dz);
            // 从上往下找地面
            BlockPos.MutableBlockPos mut = pos.mutable();
            for (int y = 0; y < 4; y++) {
                BlockPos ground = mut.below();
                BlockState groundState = level.getBlockState(ground);
                if (groundState.blocksMotion()
                        && level.getBlockState(mut).getCollisionShape(level, mut).isEmpty()
                        && level.getBlockState(mut.above()).getCollisionShape(level, mut).isEmpty()) {
                    this.teleportTo(mut.getX() + 0.5, mut.getY(), mut.getZ() + 0.5);
                    return true;
                }
                mut.move(0, 1, 0);
            }
        }
        return false;
    }

    /**
     * 【第三十一轮】逐跳逼近瞬移：朝目标<b>水平方向</b>瞬移 teleportHopDistance 格（默认8），
     * 不再一次性跨到目标身边——跳完仍在追击区间时，经 teleportHopInterval（默认10tick）
     * 后再次触发，远距离追击呈现为一串连续小瞬移。
     *
     * <p>落点搜索（修"索敌空中单位瞬移出 bug"）：
     * <ul>
     *   <li>优先在落点 ±3 格高度内找"地面可站"位置（顺带支持逐跳爬坡，向高台目标逼近）；</li>
     *   <li>若整片无地面（目标悬空于峡谷/海面等，旧逻辑在目标身边搜不到立足点导致追击失效/卡死）：
     *       兜底接受"两格可通行"的空中位——boss 落下坠并继续下一跳，
     *       表现为朝空中目标的连续跳跃追击；</li>
     *   <li>水平距离过近（&lt;4格，如目标在头顶悬停）不再跳，避免原地抖动。</li>
     * </ul>
     */
    private boolean tryTeleportHop(ServerLevel level, LivingEntity target) {
        double hopDist = TunerCommonConfig.TELEPORT_HOP_DISTANCE.get();
        if (hopDist <= 0) {
            return false;
        }
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 4.0D) {
            return false; // 已到目标正下方/正上方：不再水平跳
        }
        double step = Math.min(hopDist, horiz - 3.0D); // 不越过目标身位
        if (step < 1.0D) {
            return false;
        }
        BlockPos center = BlockPos.containing(
                this.getX() + dx / horiz * step, this.getY(), this.getZ() + dz / horiz * step);
        // 1) 地面可站（±3 高度扫描，含逐跳爬升，可借台阶/坡地向高处目标逼近）
        for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3}) {
            BlockPos feet = center.offset(0, dy, 0);
            if (level.getBlockState(feet.below()).blocksMotion()
                    && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                this.teleportTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
                return true;
            }
        }
        // 2) 兜底：无地面（目标悬空）→ 两格可通行的空中位即可（boss 下坠，跳链继续）
        for (int dy : new int[]{0, 1, -1}) {
            BlockPos feet = center.offset(0, dy, 0);
            if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                this.teleportTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
                return true;
            }
        }
        return false;
    }

    /**
     * 【2026-08-19 第十六轮】身后短距离瞬移（后撤步）：一阶段铺垫期攻击法术完毕后调用。
     * 朝 boss 朝向的反方向（水平投影）瞬移 buildupBackstepDistance 格（默认4），
     * 在目标点小半径内找可站立方块；找不到则放弃本次（不强制瞬移）。
     */
    private void tryTeleportBackward(ServerLevel level) {
        int dist = TunerCommonConfig.BUILDUP_BACKSTEP_DISTANCE.get();
        if (dist <= 0) {
            return; // 配置关闭
        }
        Vec3 look = this.getLookAngle();
        Vec3 back = new Vec3(-look.x, 0.0, -look.z);
        if (back.lengthSqr() < 1.0E-4) {
            back = new Vec3(0.0, 0.0, 1.0);
        }
        back = back.normalize();
        BlockPos center = BlockPos.containing(
                this.getX() + back.x * dist, this.getY(), this.getZ() + back.z * dist);
        for (int i = 0; i < 8; i++) {
            int dx = i == 0 ? 0 : this.random.nextInt(-2, 3);
            int dz = i == 0 ? 0 : this.random.nextInt(-2, 3);
            // 从中心高度向上/向下各扫描2格找可站立方块
            for (int dy : new int[]{0, -1, 1, -2, 2}) {
                BlockPos feet = center.offset(dx, dy, dz);
                if (level.getBlockState(feet.below()).blocksMotion()
                        && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                    this.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                    return;
                }
            }
        }
    }

    /**
     * 【2026-08-19 第十七轮】身前半圆弧瞬移：二阶段铺垫期攻击法术开始前调用。
     * 以 boss 水平朝向为正前方，在半径 r（phase2BuildupArcRadius，默认6格）的
     * 身前半圆弧（±75°）上随机取一点，±2格高度扫描找可站立方块；找不到则放弃本次。
     * 计算简化：直接把朝向向量绕Y轴旋转随机角度后缩放到半径长度。
     */
    private void tryTeleportFrontArc(ServerLevel level) {
        double radius = TunerCommonConfig.PHASE2_BUILDUP_ARC_RADIUS.get();
        if (radius <= 0) {
            return; // 配置关闭
        }
        Vec3 look = this.getLookAngle();
        double lx = look.x;
        double lz = look.z;
        if (lx * lx + lz * lz < 1.0E-4) {
            lz = 1.0; // 朝天/朝地时兜底朝南
        }
        // 身前半圆：±75° 随机偏航
        double theta = Math.toRadians(this.random.nextDouble() * 150.0 - 75.0);
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        // 绕Y轴旋转朝向向量
        double dx = lx * cos - lz * sin;
        double dz = lx * sin + lz * cos;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) {
            return;
        }
        BlockPos center = BlockPos.containing(
                this.getX() + dx / len * radius, this.getY(), this.getZ() + dz / len * radius);
        // 弧上找不到就附近小扰动再试（共3轮）
        for (int i = 0; i < 3; i++) {
            int ox = i == 0 ? 0 : this.random.nextInt(-2, 3);
            int oz = i == 0 ? 0 : this.random.nextInt(-2, 3);
            for (int dy : new int[]{0, -1, 1, -2, 2}) {
                BlockPos feet = center.offset(ox, dy, oz);
                if (level.getBlockState(feet.below()).blocksMotion()
                        && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                    this.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                    return;
                }
            }
        }
    }

    // ================= 锁血 =================

    /**
     * 阶梯式锁血（V2 + 宽限期）：
     * lockMark = 已触发锁血次数。下一档地板 = maxHealth - interval*(lockMark+1)。
     * 血量跌破地板 → setHealth(地板)，lockMark++，进入宽限期（默认0.5秒）。
     * 宽限期内血量持续钉在本次地板值（回弹机制保持），窗口结束才继续掉血。
     *
     * <p><b>本方法只负责"活着时的锁血地板与宽限期"，<u>不做任何死亡回弹</u>。</b>
     * （0.0.11 删除了此处原本那段与 {@link #maintainDeathState()} 重复的"死亡自愈"分支：
     * 它不看 `/kill` 后门、白吃一档锁血、且不复位客户端动画，实测把被 `/kill` 打死的 Boss 又复活了。
     * 死亡回弹的唯一权威实现是 `tick()` 里的 {@code maintainDeathState()}。详见 0.0.11 提交说明。）
     */
    private void applyLockHealth() {
        ServerLevel level = (ServerLevel) this.level();
        int interval = TunerCommonConfig.LOCK_HEALTH_INTERVAL.get();
        float maxHealth = TunerCommonConfig.BOSS_MAX_HEALTH.get();
        if (this.getMaxHealth() != maxHealth) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        }
        int maxMark = (int) (maxHealth / interval); // 默认 216/18 = 12

        // 【0.0.11 修复】死亡 / 死亡动画期间一律不碰锁血。
        //
        // 这里原有一段"死亡状态自愈（兜底分支）"，注释断言它"在死亡期间实际不可达"（理由是
        // "aiStep 在死亡后不执行"）。**该断言是错的，且已造成实测 bug**：
        //   · 反编译实证：`LivingEntity.tick()`（366 行字节码）里 `aiStep()` 只有 **1 处调用、偏移 179、
        //     完全无条件**；`isDeadOrDying` / `serverAiStep` / `tickDeath` 在该方法内**一次都没出现**。
        //     真正被死亡把关的是 `baseTick()`：偏移 357 判 `isDeadOrDying()` → 偏移 375 调 `tickDeath()`
        //     （`tickDeath` 里 `deathTime++`，≥20 时播事件 60 并 `remove(KILLED)`）。
        //     ⇒ 注释把 `aiStep()` 与 `serverAiStep()` 混为一谈，**死亡期间 `aiStep()` 照常执行**。
        //   · 后果（用户日志铁证，latest.log L4793~L4799）：
        //     `10:15:24.577 /kill backdoor (health=29.48, mark=10)`
        //     → 下一 tick 的 aiStep 里本分支命中，`10:15:24.625 Revived from death → lock at 18.0 (mark=11/12)`
        //     ⇒ **`/kill` 被这条兜底分支击败**（Boss 带 18 血复活，还白吃一档锁血），用户只能再打一次 `/kill`；
        //       而且本分支只写 `deathTime = 0`，**不走 `resetDeathAnimation()`**（不发 `SEntityRevivePacket`、
        //       不清 hurtTime/pose）⇒ 客户端停在死亡动画 = 用户最初反馈的"血量不为 0 但已是死亡动画"复发。
        //     （第二次 `/kill` 之所以成功：此时 lockMark=11=maxMark-1，本分支的 `lockMark < maxMark-1` 不再成立。）
        //
        // 因此：**死亡回弹的唯一权威实现是 `tick()` 里的 `maintainDeathState()`**
        // （它尊重 `/kill` 后门、且通过 `resetDeathAnimation()` 同步复位客户端动画）。
        // 本方法只负责"活着时的锁血地板 / 宽限期"，不再承担任何回弹职责。
        // 保留这个早退守卫，是为了让"锁血逻辑绝不与死亡流程抢同一具尸体"这一约束显式可见。
        if (this.isDeadOrDying() || this.deathTime > 0) {
            return;
        }

        // 【2026-08-18 第十五轮修复】宽限期递减必须先于"锁血耗尽 return"。
        // 此前宽限期块在 lockMark >= maxMark-1 的 return 之后，导致最后一档触发宽限后
        // lockGraceTicks 永不递减 → hurt() 永久 return false → boss 持续无敌（实测bug）。
        if (lockGraceTicks > 0) {
            lockGraceTicks--;
            if (this.getHealth() < lockGraceFloor) {
                this.setHealth(lockGraceFloor);
            }
            return;
        }

        if (lockMark >= maxMark - 1) {
            return; // 锁血耗尽，可被正常击杀
        }
        float nextFloor = maxHealth - interval * (float) (lockMark + 1);
        if (nextFloor <= 0) {
            return;
        }

        if (this.getHealth() < nextFloor) {
            this.setHealth(nextFloor);
            lockMark++;
            onLockTriggered(level, nextFloor, maxMark, "Lock health");
        }
    }

    /**
     * 锁血触发后的统一处理：宽限期 + 强制瞬移 + 日志。
     * 【2026-08-18 第十四轮】每次触发锁血档位都强制瞬移一次（脱离危险位置 / 迷惑玩家）。
     */
    private void onLockTriggered(ServerLevel level, float floor, int maxMark, String reason) {
        startLockGrace(floor);
        GoetyTuner.LOGGER.info("[Tuner] {} → lock at {} (mark={}/{})", reason, floor, lockMark, maxMark);
        // 强制瞬移到目标附近
        LivingEntity target = this.getTarget();
        if (target != null) {
            tryTeleportNear(level, target);
            GoetyTuner.LOGGER.info("[Tuner] Force teleport on lock trigger (mark={})", lockMark);
        }
    }

    /** 触发锁血宽限期（钉住地板值的窗口） */
    private void startLockGrace(float floor) {
        int grace = TunerCommonConfig.LOCK_GRACE_TICKS.get();
        if (grace > 0) {
            lockGraceTicks = grace;
            lockGraceFloor = floor;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 【0.0.9】/kill 后门：管理员指令必须能真正击杀，无视锁血的一切保护。
        // （KillCommand → Entity.kill() → LivingEntity.kill() → hurt(genericKill, MAX_VALUE)，字节码核实）
        // 否则 /kill 会被宽限期免疫吞掉、或被致死截断压到剩 1 血，管理员反而杀不死自己的 Boss。
        if (source.is(DamageTypes.GENERIC_KILL)) {
            this.adminKillPending = true;
            GoetyTuner.LOGGER.info("[Tuner] /kill backdoor: bypassing lock-health protection (health={}, mark={})",
                    this.getHealth(), lockMark);
            return super.hurt(source, amount);
        }
        // 【0.0.17】索命聚晶后门（详见字段注释）：goety:death 伤害不再被锁血体系拦截。
        // 与 /kill 后门同一套语义——置标记后本次伤害原样交给原版结算（跳过身份免疫/宽限期免疫/致死截断），
        // 且后续 maintainDeathState() 不回弹、canStillRevive() 放行 remove(KILLED) ⇒ 真正死亡。
        //
        // ⚠️ 条件里的 `source.getEntity() != this` 是**必需的防自杀护栏**：
        // KillingSpell 的字节码实证显示它 `deathCurse(caster)` 造源，然后**施法者与目标双方都吃这个伤害**——
        // 先 `caster.hurt(deathCurse, …)`（125% 反噬，SpellResult 偏移 172），再 `target.hurt(...)`（偏移 196），
        // 而伤害源的 getEntity() **始终是施法者**。于是：玩家咒 Boss 时 getEntity()=玩家（≠Boss）→ 后门正常生效；
        // 而**若 Boss 自己施放索命**，它的反噬会让 getEntity()==Boss 自己 → 被这里挡下，
        // 否则它会当场把自己处决（对玩家满血时长矛反噬极大，等于自杀）。目前 `goety:killing_focus`
        // 已被黑名单拉黑、Boss 不会施放它，但这道护栏让"黑名单"不再承担防自杀的唯一责任。
        if (TunerCommonConfig.DEATH_CURSE_EXECUTION.get() && source.is(GOETY_DEATH_CURSE)
                && source.getEntity() != this) {
            this.deathCursePending = true;
            GoetyTuner.LOGGER.info("[Tuner] Death-curse backdoor (goety:death): bypassing lock-health protection "
                    + "(health={}, mark={}, amount={})", this.getHealth(), lockMark, amount);
            return super.hurt(source, amount);
        }
        // 【第二十三轮】Boss身份伤害免疫：摔落、原版火焰（含火焰/岩浆/燃烧，
        // 覆盖 DamageTypeTags.IS_FIRE 全部火系）、窒息（卡墙）、溺水。
        // （不免疫魔法/爆炸/普通攻击等玩家可造成/可操作的伤害类型）
        // 【0.0.19】实现已抽到 TunerDamageRules.isIdentityImmune —— 调律师仆从要求
        // "减伤机制与本体一致"，两处共用同一份代码（见该类注释）。
        if (TunerDamageRules.isIdentityImmune(source)) {
            return false;
        }
        // 【第二十九轮】近战易伤：直接近身物理攻击（玩家/生物的手持武器挥击与横扫）
        // 伤害 ×(1+meleeVulnerability)，默认+25%，让贴身近战成为有效输出手段。
        // 投射物/爆炸/魔法等远程手段不受加成。
        // 【0.0.19】同样抽到 TunerDamageRules（仆从按"与本体一致"一并适用）。
        amount = TunerDamageRules.applyMeleeVulnerability(source, amount,
                TunerCommonConfig.MELEE_VULNERABILITY.get());
        // 【2026-08-18 第十四轮】锁血宽限期内免疫伤害（真正无敌窗口，而非事后拉回）
        // 此前宽限期内 hurt 照常生效、applyLockHealth 事后 setHealth 拉回，
        // 多段/高频伤害在 tick 中间可能穿透。改为宽限期内直接 return false。
        if (lockGraceTicks > 0) {
            return false;
        }
        // 锁血未耗尽时拦截致死伤害：保留1血，由 applyLockHealth 在下一tick锁到下一档
        int interval = TunerCommonConfig.LOCK_HEALTH_INTERVAL.get();
        float maxHealth = TunerCommonConfig.BOSS_MAX_HEALTH.get();
        int maxMark = (int) (maxHealth / interval);
        if (lockMark < maxMark - 1 && this.getHealth() - amount <= 0) {
            amount = Math.max(0, this.getHealth() - 1);
        }
        return super.hurt(source, amount);
    }

    // ================= 【0.0.6】限伤 / 限DPS（伤害节流） =================

    /**
     * 【0.0.6 / 0.0.19】限伤 + 限DPS 的状态与算法都在 {@link DamageThrottle} 里
     * （0.0.19 从本类抽出，供调律师仆从共用同一份实现；语义与 0.0.6 完全一致）。
     */
    private final DamageThrottle damageThrottle = new DamageThrottle();

    /**
     * 【0.0.6】限伤（单次伤害上限）+ 限DPS（每秒伤害上限）：在**最终结算处**节流 boss 承受的伤害。
     *
     * <p>两项规则的实现与理由见 {@link DamageThrottle}（Boss 与调律师仆从共用）。
     * 本方法只负责 Boss **特有**的后门豁免：这些伤害不受任何限伤约束，
     * 否则会出现「管理员杀不死、索命处决失效」的破状态。
     */
    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        // BYPASSES_INVULNERABILITY（/kill 的 generic_kill、掉出世界等）不受任何限伤约束，
        // 否则会出现「管理员杀不死、掉进虚空也不死」的诡异状态。
        // 【0.0.9】额外显式判定 GENERIC_KILL：不依赖 tag 内容的假设（数据包/其它模组可能改动它），
        // 保证 /kill 这条后门在任何环境下都成立。
        // 【0.0.17】索命聚晶的 goety:death 同样跳过限伤/限DPS —— 否则"等同于目标当前生命值"的致死伤害
        // 会被限伤削掉、变成打不死，后门也就失效了（它不在任何 BYPASSES_* tag 里，必须显式列出）。
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && !source.is(DamageTypes.GENERIC_KILL)
                && !(TunerCommonConfig.DEATH_CURSE_EXECUTION.get() && source.is(GOETY_DEATH_CURSE))) {
            float allowed = damageThrottle.apply(
                    this.level().getGameTime(),
                    amount,
                    this.getMaxHealth(),
                    TunerCommonConfig.MAX_HIT_DAMAGE_PERCENT.get(),
                    TunerCommonConfig.MAX_DAMAGE_PER_SECOND.get());
            if (allowed < 0.0F) {
                return; // 每秒预算耗尽：本次伤害被完全吸收（hurt 已返回 true，动画/击退/无敌帧照常）
            }
            amount = allowed;
        }
        super.actuallyHurt(source, amount);
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        // 【第二十九轮】免疫召唤冷却：Goety 的召唤冷却（SummonDown）是在施法者身上施加
        // SUMMON_DOWN 药水效果（ISummonSpell.hasSummonDown 检查该效果决定能否再次召唤）。
        // boss 拒绝此效果即可无视召唤冷却连续召唤（SpellConfig.SummonDown 全局开关不受影响，
        // 玩家侧冷却照常生效）。
        if (effect.getEffect() == GoetyEffects.SUMMON_DOWN.get()) {
            return false;
        }
        // 【0.0.14】二阶段免疫「回复 / 减伤」类效果（用户要求）。
        //
        // 目的：二阶段本就靠锁血档位 + 回血撑住强度，若再被外部来源叠上抗性/吸收/再生
        // （其它模组的增益光环、玩家丢的增益药水、Goety 的治疗类效果、仆从的辅助法术…），
        // Boss 会变成"打不动 + 自动回血"，战斗直接失去节奏。
        //
        // 实现说明：本方法是 `LivingEntity.addEffect(...)` 的**第一道**判定（`forceAddEffect` 同样先走它），
        // 所以在这里返回 false 就是真正的"免疫"，而不是加完再清。
        // 注意**不要**误伤 Boss 自己的二阶段自施：那是 DAMAGE_BOOST(力量) 与 RALLYING(重振)，
        // 不在下面的名单里，因此照常生效（见 tickPhase2Buffs）。
        if (TunerCommonConfig.PHASE2_EFFECT_IMMUNITY.get() && music.isPhase2()
                && isPhase2Immune(effect.getEffect())) {
            return false;
        }
        return super.canBeAffected(effect);
    }

    /**
     * 【0.0.14】二阶段免疫名单：回复 / 减伤类。
     *
     * <p>只列**明确的**这几项（而不是"所有 beneficial"）——因为 Boss 自己的二阶段增益
     * （力量、重振）也是 beneficial，一刀切会把它们一起禁掉。
     * 若今后要再挡某个效果（例如某个附属模组的自定义减伤），往这里加一行即可。
     */
    private static boolean isPhase2Immune(MobEffect effect) {
        return effect == MobEffects.DAMAGE_RESISTANCE   // 抗性提升
                || effect == MobEffects.ABSORPTION      // 伤害吸收
                || effect == MobEffects.REGENERATION    // 生命恢复
                || effect == MobEffects.HEAL            // 瞬间治疗
                || effect == MobEffects.HEALTH_BOOST;   // 生命提升
    }

    /** 打断全部施法（阶段切换/二阶段进入时） */
    private void interruptAllCasts(ServerLevel level) {
        buildupChannel.interrupt(level);
        if (activeBuildupChannel != null) {
            activeBuildupChannel.interrupt(level);
        }
        for (CastChannel c : climaxChannels) {
            c.interrupt(level);
        }
    }

    /**
     * 清除效果：进入低谷时清除所有正面效果（beneficial=true），
     * 进入高潮时清除所有负面效果（beneficial=false）。
     * 使用 MobEffect.isBeneficial() 判定正负面。
     */
    private void cleanseEffects(ServerLevel level, boolean beneficial) {
        var toRemove = this.getActiveEffects().stream()
                .filter(inst -> inst.getEffect().isBeneficial() == beneficial)
                .toList();
        for (var inst : toRemove) {
            this.removeEffect(inst.getEffect());
        }
        if (!toRemove.isEmpty()) {
            GoetyTuner.LOGGER.info("[Tuner] Cleanse {} {} effects on phase change",
                    toRemove.size(), beneficial ? "positive" : "negative");
            // TODO(美术/特效): 净化粒子效果
        }
    }

    // ================= 二阶段 =================

    /**
     * 【第二十六轮】二阶段施法前摇倍率：按当前锁血档位(lockMark)递减。
     * 公式 = 1.0 - (lockMark - 6) * 0.1，钳制到 [0.1, 1.0]。
     * 档位6（刚进二阶段=半血）=1.0原速；每升一档快10%；档位11=0.5、档位12=0.4。
     * 理念：二阶段后越打技能放得越快。
     */
    private double phase2WarmupFactor() {
        int tier = lockMark;
        double f = 1.0 - (tier - 6) * 0.1;
        return Math.max(0.1, Math.min(1.0, f));
    }

    /** 二阶段周期性自施药水计时器（每40tick=2秒触发一次） */
    private int phase2BuffTimer = 0;
    /** 二阶段回血计时器（每40tick=2秒触发一次） */
    private int phase2RegenTimer = 0;
    /** 二阶段 SPELL_POTENCY 属性 modifier 的固定 UUID（法术伤害加成，非冷却/非前摇） */
    private static final UUID PHASE2_POTENCY_UUID =
            UUID.nameUUIDFromBytes("goetytuner:boss_phase2_spell_potency".getBytes(StandardCharsets.UTF_8));

    /**
     * 【第二十六轮】二阶段周期性自施药水：每2秒给自己3秒强化。
     * 【第三十三轮】bug2 修复：原自施 Goety 的 BUFF（强健）与 RALLYING（重振）——
     * 反编译确认两者都是「属性修改器型效果」：BUFF=+攻击(ADD)/+移速(MULTIPLY)、RALLYING=+10%攻击/级，
     * 加成全部落在近战属性上，对 boss 的法术输出毫无增益，玩家观察即"不奏效"。
     * 改为自施原版力量 MobEffects.DAMAGE_BOOST（每级+3近战伤害，等级可配置，肉眼可见），
     * RALLYING 保留作近战补强。总开关 {@link TunerCommonConfig#PHASE2_BUFFS_ENABLED}。
     */
    private void tickPhase2Buffs() {
        AttributeInstance potAttr = this.getAttribute(ModAttributes.SPELL_POTENCY.get());
        if (!TunerCommonConfig.PHASE2_BUFFS_ENABLED.get()) {
            phase2BuffTimer = 40; // 保持节拍，关闭时不自施
            // 关闭时清理已挂的属性 modifier（防配置热重载后残留）
            if (potAttr != null && potAttr.getModifier(PHASE2_POTENCY_UUID) != null) {
                potAttr.removeModifier(PHASE2_POTENCY_UUID);
            }
            return;
        }
        if (--phase2BuffTimer > 0) {
            return;
        }
        phase2BuffTimer = 40; // 2秒
        boolean highTier = lockMark >= 10;
        int strengthLevel = highTier
                ? TunerCommonConfig.PHASE2_STRENGTH_LEVEL_HIGH.get()
                : TunerCommonConfig.PHASE2_STRENGTH_LEVEL_LOW.get();
        int rallyAmp = (highTier ? 8 : 5) - 1;  // 重振：档位<10→等级5(amp4)；≥10→等级8(amp7)
        int duration = 60; // 3秒
        this.applySelfEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, strengthLevel - 1, false, true, true));
        this.applySelfEffect(new MobEffectInstance(GoetyEffects.RALLYING.get(), duration, rallyAmp, false, true, true));
        // 【第三十七轮】核心修复：力量(RANGE_BOOST)只加近战伤害，Boss 是法系输出，药水粒子"不奏效"。
        // 额外挂 SPELL_POTENCY 属性 modifier（MULTIPLY_TOTAL），通过 Goety 原生施法通道真正提升法术伤害。
        // 数值 = strengthLevel * 0.1（等级2=20%、等级5=50%），与力量等级联动配置。
        if (potAttr != null) {
            double potency = strengthLevel * 0.1;
            AttributeModifier existing = potAttr.getModifier(PHASE2_POTENCY_UUID);
            if (existing == null || Math.abs(existing.getAmount() - potency) > 1.0E-4D) {
                potAttr.removeModifier(PHASE2_POTENCY_UUID);
                potAttr.addTransientModifier(new AttributeModifier(
                        PHASE2_POTENCY_UUID, "TunerPhase2SpellPotency", potency,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
    }

    /**
     * 【第二十六轮】二阶段回血机制：锁血档位7-12内，若不处于锁血宽限期、
     * 且血量未达当前档位上限（=上一档的锁血血量 = maxHealth - interval*(lockMark-1)），
     * 每2秒回复1点生命。
     */
    private void tickPhase2Regen() {
        if (lockMark < 7 || lockMark > 12) {
            return;
        }
        if (lockGraceTicks > 0) {
            return; // 锁血宽限期内不回血
        }
        int interval = TunerCommonConfig.LOCK_HEALTH_INTERVAL.get();
        float maxHealth = TunerCommonConfig.BOSS_MAX_HEALTH.get();
        float cap = maxHealth - interval * (float) (lockMark - 1); // 当前档位上限=上一档地板
        if (this.getHealth() >= cap) {
            return; // 已达档位上限，不再回血
        }
        if (--phase2RegenTimer > 0) {
            return;
        }
        phase2RegenTimer = 40; // 2秒
        this.heal(1.0F);
    }

    private void onEnterPhase2(ServerLevel level) {
        // 打断当前施法
        interruptAllCasts(level);
        // 【第三十一轮】进场重音连发：连续 phase2EntryBurstCount（默认6）次重音击退脉冲，
        // 每5tick一次、音调逐次递升（0.8→1.55，"调音上行"起手）；延迟10tick出第一发，
        // 与阶段切换的清场动画错开。处理在 aiStep 7.6。
        int burst = TunerCommonConfig.PHASE2_ENTRY_BURST_COUNT.get();
        if (burst > 0) {
            phase2EntryBurstRemaining = burst;
            phase2EntryBurstTimer = 10;
        }
        // 【第二十四轮】嘲讽彩蛋是一阶段专属：进二阶段立即终止（回站姿，不进冷却）
        if (tauntState != 0) {
            tauntState = 0;
            tauntCooldown = 0;
            this.setPose(Pose.STANDING);
        }
        // 【第二十二轮】弧形走位计数归零（每轮施法重新计数）
        phase2ArcCastCounter = 0;
        // 机制变换：高潮并行通道按二阶段角色序列重建（如 222=三端攻击）
        rebuildClimaxChannels();
        // 杀死所有属于自己的仆从（复现玩家端"解散召唤物"功能）
        // 【2026-08-18 第十四轮】史莱姆类召唤物死亡会分裂出更小个体，一次性清理杀不干净，
        // 改为立即清1轮 + 后续3轮（每15tick，共4轮），间隔加长让分裂体有时间生成后再清。
        cleanOwnedMinions(level);
        phase2MinionCleansRemaining = 3;
        phase2CleanTimer = 15;
        // 【第二十一轮】音乐：一/二阶段全程同一版本（客户端 BossMusicManager 循环实例），
        // 阶段切换不重启、不变速、不重置进度。
        // TODO(美术): 天空盒渲染替换、模型/动画破碎形态 —— 客户端阶段实现
    }

    /** 清理一轮当前所有己方仆从；返回清理数量 */
    private int cleanOwnedMinions(ServerLevel level) {
        int killed = 0;
        for (Mob minion : ownedMinions(level)) {
            // genericKill 绕过无敌/抗性，确保致死；死亡会正常进入评分结算
            minion.hurt(minion.damageSources().genericKill(), Float.MAX_VALUE);
            killed++;
        }
        if (killed > 0) {
            GoetyTuner.LOGGER.info("[Tuner] Phase2 cleanup: dismissed {} owned minions", killed);
        }
        return killed;
    }

    // ================= 仆从管理 =================

    public List<Mob> ownedMinions(ServerLevel level) {
        return summonTracker.ownedMinions(level, this);
    }

    public double minionFillRatio() {
        if (!(this.level() instanceof ServerLevel level)) {
            return 0;
        }
        int max = TunerCommonConfig.MAX_MINIONS.get();
        return Math.min(1.0D, (double) summonTracker.countOwned(level, this) / max);
    }

    /** 召唤物满员：召唤评分暂时归0，直到数量 < 上限-hysteresis */
    public boolean isSummonBlocked() {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        int max = TunerCommonConfig.MAX_MINIONS.get();
        int hysteresis = TunerCommonConfig.MINION_REFILL_HYSTERESIS.get();
        int count = summonTracker.countOwned(level, this);
        if (summonTracker.isBlockedFlag()) {
            return count >= max - hysteresis; // 满员后需低于 上限-参数 才恢复
        }
        boolean blocked = count >= max;
        summonTracker.setBlockedFlag(blocked);
        return blocked;
    }

    // ================= 施法回调 =================

    /** 伤害事件路由（CombatEvents → 评分追踪） */
    public void routeDamageForScoring(LivingEntity victim, DamageSource source, float amount) {
        damageTracker.recordDamage(victim, source, amount, this);
    }

    public void routeMinionDamageForScoring(LivingEntity victim, DamageSource source, float amount) {
        summonTracker.recordMinionDamage(victim, source.getDirectEntity(), amount, this);
    }

    @Override
    public void remove(RemovalReason reason) {
        // 【0.0.8】锁血未耗尽时挡住 KILLED 移除：死亡动画跑满 20 tick 后原版会 remove(KILLED)，
        // 一旦移除就再也回弹不了（实体已不在世界里）。挡下这一次，由下一 tick 的
        // maintainDeathState() 负责复活；锁血耗尽/总开关关闭时照常放行。
        if (reason == RemovalReason.KILLED && !this.level().isClientSide && canStillRevive()) {
            GoetyTuner.LOGGER.info(
                    "[Tuner] Blocked KILLED removal — lock tiers remain (mark={}), will revive next tick", lockMark);
            return;
        }
        super.remove(reason);
        if (!this.level().isClientSide) {
            CombatEvents.unregisterBoss(this);
        }
    }

    @Override
    public LivingEntity boss() {
        return this;
    }

    @Override
    public FocusPoolManager pools() {
        return pools;
    }

    @Override
    public void onCastStart(com.tiaolvshi.goetytuner.focus.FocusEntry entry) {
        changeCastCategory(entry, 1);
        // 【0.0.16】「逐渐学习」：记一次施法，推进该个体的动态评分权重系数
        // （只统计"用聚晶施法"的前摇起手；瞬发/重音不经过本回调，故不计入）
        pools.noteCast();
        // 【第三十一轮】计数器化：并行高潮通道下"任一通道前摇中"即施法中（见 castEnded 注释）
        castStarted();
        // 【2026-08-19 第十七轮】二阶段铺垫期：攻击法术开始前瞬移至 boss 身前半圆弧
        // （r=phase2BuildupArcRadius，默认6格）上的可站立点——"释放攻击法术前"的走位。
        // 【第二十二轮】频率调整：每 phase2BuildupArcEveryN 次（默认3，即一轮"222"轮换施法）
        // 攻击施法才瞬移一次，不再每次攻击都瞬移。
        if (entry.getCategory() == FocusCategory.ATTACK
                && this.level() instanceof ServerLevel level
                && music.isPhase2()
                && music.getCurrentPhase() == BossPhase.BUILDUP) {
            int everyN = Math.max(1, TunerCommonConfig.PHASE2_BUILDUP_ARC_EVERY_N.get());
            if (++phase2ArcCastCounter >= everyN) {
                phase2ArcCastCounter = 0;
                tryTeleportFrontArc(level);
            }
        }
    }

    @Override
    public void onCastFinish(com.tiaolvshi.goetytuner.focus.FocusEntry entry) {
        changeCastCategory(entry, -1);
        castEnded();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        // 召唤类法术结算后登记新仆从（供动态评分与数量上限）
        if (entry.getCategory() == FocusCategory.SUMMON) {
            summonTracker.registerNewMinions(level, this, entry);
        }
        // 攻击类法术结算后开始统计本次伤害（供动态评分）
        if (entry.getCategory() == FocusCategory.ATTACK) {
            damageTracker.beginAttribution(level, this, entry);
            // 【2026-08-19 第十六轮】一阶段铺垫期：攻击法术施展完毕后向身后短距离瞬移（后撤步走位）
            if (!music.isPhase2() && music.getCurrentPhase() == BossPhase.BUILDUP) {
                tryTeleportBackward(level);
            }
        }
    }

    // ================= 同步 =================

    /**
     * 【第三十一轮】施法状态计数维护：onCastStart 递增、finish/interrupt/fail 递减，
     * 归零才清 DATA_CAST_STATE。修复两处旧问题：
     * ① 高潮并行通道 A 完成、B 仍在施法时被 onCastFinish 误清为 0；
     * ② CastChannel 异常自愈路径不经过 finish/interrupt，状态永久卡在 1（蹲姿瞄准会卡死下蹲）。
     */
    private void castStarted() {
        activeWarmups++;
        this.entityData.set(DATA_CAST_STATE, 1);
    }

    private void castEnded() {
        activeWarmups = Math.max(0, activeWarmups - 1);
        if (activeWarmups == 0) {
            this.entityData.set(DATA_CAST_STATE, 0);
            // 瞄准蹲姿收势：无嘲讽状态机接管姿态时恢复站姿
            //（嘲讽蹲起循环/蹲望进行中时由其继续控制，避免打断节奏）
            if (tauntState == 0 || tauntState == 2) {
                this.setPose(Pose.STANDING);
            }
        }
    }

    @Override
    public void onCastInterrupted(com.tiaolvshi.goetytuner.focus.FocusEntry entry) {
        changeCastCategory(entry, -1);
        // 【第三十一轮】阶段切换打断：此前未覆写导致 DATA_CAST_STATE 不清（旧bug）
        castEnded();
    }

    @Override
    public void onCastFailed(com.tiaolvshi.goetytuner.focus.FocusEntry entry) {
        changeCastCategory(entry, -1);
        // 【第三十一轮】CastChannel 异常自愈（聚晶需玩家来源等）：施法已开始过，需递减计数。
        // 注意 beginCast 的 startSpell 异常路径不触发本回调（施法尚未开始，见 CastChannel）。
        castEnded();
    }

    private void broadcastMusicSync() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        // 【0.0.5 性能】先确认 128 格内有玩家再构造同步包。
        // 构造包要复制分段表/重音表并分配数组（getSegments/getAccents 各自 List.copyOf）；
        // 而 Boss 设了 setPersistenceRequired，附近无玩家时 aiStep 仍会跑，
        // 原实现每 20 tick 为"没人接收"的包做一次完整构造。
        boolean anyNearby = false;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < 128 * 128) {
                anyNearby = true;
                break;
            }
        }
        if (!anyNearby) {
            return;
        }
        SMusicSyncPacket pkt = new SMusicSyncPacket(
                this.getId(),
                music.getMusicProgressTick(),
                music.getTotalDuration(),
                music.getCurrentPhase().ordinal(),
                music.isPhase2(),
                music.getSegments(),
                music.getAccents(),
                musicPlaying,
                music.getSpeed());
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < 128 * 128) {
                TunerNetwork.sendToPlayer(pkt, p);
            }
        }
    }

    // ================= Boss事件/音效/NBT =================

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
        // 【第二十一轮】音频已客户端化：不再由服务端 playSound——
        // 客户端 BossMusicManager 收到 playing=true 的同步包后自动起播循环实例
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void checkDespawn() {
        // 敌对生物标准行为：和平模式下消失。除此之外不自然消失（持久boss）。
        if (this.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            this.discard();
        }
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null; // TODO(音频): 死亡音效
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource src) {
        return null; // TODO(音频): 受击音效
    }

    /** 【任务#118】仪式召唤法杖快照（持久化于 "OriginalWand" 键，见 SaveData） */
    public void setOriginalWand(ItemStack stack) {
        this.originalWand = stack.copy();
    }

    /** 【任务#118】读取仪式召唤法杖快照；非仪式召唤（刷怪蛋/指令）返回 EMPTY */
    public ItemStack getOriginalWand() {
        return originalWand;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("LockMark", lockMark);
        tag.putBoolean("LockV2", true); // 标记新语义，旧档无此key时读取重置为0
        tag.putBoolean("Phase2", music.isPhase2());
        if (!originalWand.isEmpty()) {
            // 1.20.1 无 HolderLookup 重载：save(CompoundTag) 直写
            tag.put("OriginalWand", originalWand.save(new CompoundTag()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // lockMark 语义 V2：旧档（无 LockV2 标记）使用旧语义（8=满血档），读取时重置为0
        if (tag.getBoolean("LockV2")) {
            this.lockMark = tag.getInt("LockMark");
        } else {
            this.lockMark = 0; // 旧档迁移：视为未锁过
        }
        if (tag.getBoolean("Phase2")) {
            music.enterPhase2();
            this.entityData.set(DATA_PHASE2, true);
            // 旧档恢复：高潮通道需按二阶段角色序列重建
            rebuildClimaxChannels();
        }
        // 【任务#118】仪式召唤快照读回（非仪式召唤的旧档/新刷怪无此键 → EMPTY）
        if (tag.contains("OriginalWand")) {
            this.originalWand = ItemStack.of(tag.getCompound("OriginalWand"));
        } else {
            this.originalWand = ItemStack.EMPTY;
        }
        // 【2026-08-18 第八轮修复】旧档自愈：实体从 NBT 恢复时，super 会把主手还原成
        // 存档中的值（修复前生成的 Tuner 主手为空），导致 SoulUsingItemHandler.get 崩溃
        // （"ItemStack is missing item capability"）。readAdditionalSaveData 在构造函数
        // 之后执行，故此处兜底：主手非法杖则补发调律师法杖（IWand 自带 SoulUsing capability）。
        // 【0.0.19】这里也顺带完成"从 goety:dark_wand 迁移到 goetytuner:tuner_wand"——
        // 老存档里存的仍是 dark_wand，它会走 IWand 判定通过（dark_wand 也是 IWand），
        // 但它**无法**支撑长按类法术（见构造函数注释），所以按注册名精确迁移。
        if (!(this.getMainHandItem().getItem() instanceof com.Polarice3.Goety.api.items.magic.IWand)
                || isLegacyDarkWand(this.getMainHandItem())) {
            this.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(
                            com.tiaolvshi.goetytuner.init.ModItems.TUNER_WAND.get()));
        }
    }

    /** 【0.0.19】老存档的主手是 {@code goety:dark_wand} 时判定为需要迁移。 */
    private static boolean isLegacyDarkWand(net.minecraft.world.item.ItemStack stack) {
        return stack.getItem() == com.Polarice3.Goety.common.items.ModItems.DARK_WAND.get();
    }

    /**
     * 【任务#118】掉落经验 × 配置倍率（默认 4 倍）。
     * 基础 xpReward=500（构造器设置），返回四舍五入后的整数。
     */
    @Override
    public int getExperienceReward() {
        return (int) Math.round(super.getExperienceReward() * TunerCommonConfig.XP_MULTIPLIER.get());
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(name == null
                ? Component.translatable("entity.goetytuner.tuner") : name);
    }

    // ================= 客户端查询 =================

    public BossPhase getClientPhase() {
        return BossPhase.byOrdinal(this.entityData.get(DATA_PHASE));
    }

    public boolean isClientPhase2() {
        return this.entityData.get(DATA_PHASE2);
    }

    public boolean isClientCasting() {
        return this.entityData.get(DATA_CAST_STATE) == 1;
    }

    /** 通道查询（按功能分块取铺垫通道；高潮通道为独立数组） */
    private CastChannel buildupChannelFor(FocusCategory category) {
        return buildupChannels.computeIfAbsent(category, c -> new CastChannel(c, this));
    }
}
