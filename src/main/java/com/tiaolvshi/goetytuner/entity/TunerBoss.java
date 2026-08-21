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
import com.tiaolvshi.goetytuner.combat.CombatEvents;
import com.tiaolvshi.goetytuner.combat.DamageScoreTracker;
import com.tiaolvshi.goetytuner.combat.SummonScoreTracker;
import com.tiaolvshi.goetytuner.config.TunerCommonConfig;
import com.tiaolvshi.goetytuner.entity.ai.CastChannel;
import com.tiaolvshi.goetytuner.focus.FocusCategory;
import com.tiaolvshi.goetytuner.focus.FocusPoolManager;
import com.tiaolvshi.goetytuner.network.SMusicSyncPacket;
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
public class TunerBoss extends Monster implements CastChannel.TunerCastCallback {

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
    // 例：maxHealth=1024, interval=128 → maxMark=8，地板 896/768/640/512/384/256/128，共7次锁血。
    private int lockMark = 0;
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
        // 【2026-08-18 第八轮修复】主手常驻一把 Goety 暗法杖（dark_wand, SpellType.NONE 通用系别）。
        // 崩溃根因：SoulUsingItemHandler.get(stack) 要求物品具备 ITEM_HANDLER capability，
        // 而 IWand.initCapabilities 正是提供 SoulUsingItemCapability 的入口；此前主手为空，
        // 空 ItemStack 无 capability → orElseThrow 抛 "ItemStack is missing item capability"。
        // 施法前由 BossWandHelper.installFocus 换装当前聚晶（见 CastChannel.beginCast）。
        this.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(com.Polarice3.Goety.common.items.ModItems.DARK_WAND.get()));
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
     */
    private static FocusCategory[] parseRoleSpec(String spec) {
        java.util.List<FocusCategory> out = new java.util.ArrayList<>();
        if (spec != null) {
            for (char c : spec.toCharArray()) {
                switch (c) {
                    case '1' -> out.add(FocusCategory.DEFENSE);
                    case '2' -> out.add(FocusCategory.ATTACK);
                    case '3' -> out.add(FocusCategory.SUMMON);
                    case '4' -> out.add(FocusCategory.OTHER);
                }
            }
        }
        if (out.isEmpty()) {
            out.add(FocusCategory.DEFENSE);
            out.add(FocusCategory.ATTACK);
            out.add(FocusCategory.SUMMON);
        }
        return out.toArray(new FocusCategory[0]);
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
     * 【第三十三轮】死亡回弹主检测（bug1 修复）：
     * 反编译确认 LivingEntity.tick 的 AI 区块为
     * {@code if (isDeadOrDying()) {清零移动输入} else if (isEffectiveAi()) { serverAiStep() }}——
     * 实体死亡后（血量≤0，死亡动画 20 tick 内）aiStep() 根本不再执行，
     * 原 applyLockHealth 里的死亡自愈分支在死亡期间永远无法触发（死代码）。
     * 这里覆写 tick()：每 tick（含死亡动画期）最先检测死亡状态，锁血未耗尽即回弹到下一档地板，
     * 使 deathTime 永远到不了 20（不会触发 remove），平时每 tick 都在检测。
     */
    @Override
    public void tick() {
        if (!this.level().isClientSide && reviveIfDead()) {
            // 本 tick 已从死亡状态回弹：super.tick() 将按正常存活实体走（血量>0、deathTime=0）
        }
        super.tick();
    }

    /**
     * 死亡自愈判定（平时每 tick 检测）：锁血未耗尽（lockMark < maxMark-1，最后一档放行正常击杀）
     * 且处于死亡状态（血量≤0 或死亡动画中）→ 回弹到下一档地板并触发一次锁血处理。
     * 返回是否发生了回弹。由 {@link TunerCommonConfig#LOCK_DEATH_REVIVE} 控制总开关。
     */
    private boolean reviveIfDead() {
        if (!TunerCommonConfig.LOCK_DEATH_REVIVE.get()) {
            return false;
        }
        if (!this.isDeadOrDying() && this.deathTime <= 0) {
            return false; // 未处于死亡状态
        }
        int interval = TunerCommonConfig.LOCK_HEALTH_INTERVAL.get();
        float maxHealth = TunerCommonConfig.BOSS_MAX_HEALTH.get();
        int maxMark = (int) (maxHealth / interval);
        if (lockMark >= maxMark - 1) {
            return false; // 锁血耗尽，可被正常击杀
        }
        float reviveHp = Math.max(maxHealth - interval * (float) (lockMark + 1), 1.0F);
        this.deathTime = 0;
        this.setHealth(reviveHp);
        lockMark++;
        GoetyTuner.LOGGER.info("[Tuner] Death-revive → {} HP (mark={}/{})", reviveHp, lockMark, maxMark);
        if (this.level() instanceof ServerLevel serverLevel) {
            onLockTriggered(serverLevel, reviveHp, maxMark, "Revived from death");
        }
        return true;
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
            BossPhase prevPhase = BossPhase.values()[this.entityData.get(DATA_PHASE)];
            BossPhase phase = music.tick();
            this.entityData.set(DATA_PHASE, phase.ordinal());

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
            if (this.entityData.get(DATA_CAST_STATE) == 1 && tauntState != 2) {
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

        // 3.5 【第二十二轮】瞬移追击/异常位置修正：全程每tick判定（原先只在铺垫期与
        //     二阶段高潮期调用——一阶段高潮/低谷期、或脱战边缘玩家跑远时不会追击，观感即"瞬移追击失效"）。
        //     目标=最近可索敌玩家（不依赖仇恨），触发仍受距离区间
        //     (teleportMinDistance, teleportChaseMaxDistance) 与瞬移间隔限制，不会跨图追人。
        tickTeleport(level);

        // 3.6 【第二十三轮】非玩家瞬移追击（独立新逻辑，未改动 tickTeleport）：
        //     当 boss 处于被激怒状态（存在存活仇恨目标）、仇恨目标不是玩家、
        //     且 96格内没有任何可参战的生存/冒险模式玩家时生效——
        //     典型场景：boss 被铁傀儡/其他生物激怒、或玩家已全部远离，boss 不会卡在远处干瞪眼。
        //     有可参战玩家在场时本逻辑不介入（追击一律交给玩家版 tickTeleport）。
        tickTeleportNonPlayer(level);

        // 4.5 【2026-08-18 第十四轮】二阶段仆从连续清理（4轮，每15tick，防史莱姆分裂残留）
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
        this.addEffect(new MobEffectInstance(GoetyEffects.SAPPED.get(), 30, 11, true, false));
        this.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 30, 0, true, false));
        // 仆从：生命恢复1、伤害吸收1、缓慢3、虚弱2
        for (Mob minion : ownedMinions(level)) {
            minion.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30, 0, true, false));
            minion.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 30, 0, true, false));
            minion.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2, true, false));
            minion.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 1, true, false));
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
     */
    private void accentKnockbackPulse(ServerLevel level, double strength, float pitch) {
        // 【第三十三轮】bug3 修复：旁观/创造玩家白名单——旁观者不应被击退，创造玩家（观战/建筑）
        // 也不应被击退打断操作。非玩家生物保留击退（与 tickTeleportNonPlayer 的过滤写法一致）。
        List<LivingEntity> around = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(this.blockPosition()).inflate(8.0D),
                e -> e != this && e.isAlive()
                        && !(e instanceof Player p && (p.isSpectator() || p.isCreative())));
        for (LivingEntity e : around) {
            Vec3 dir = e.position().subtract(this.position()).normalize();
            e.push(dir.x * strength, strength * 0.5D, dir.z * strength);
            e.hurtMarked = true; // 速度同步
        }
        // 【2026-08-19 第十九轮】重音特效：
        // 1) 三波同心冲击环（END_ROD，逐波外扩+抬升，视觉上"音波"从boss脚下荡开）
        if (TunerCommonConfig.ACCENT_PARTICLES.get()) {
            double px = this.getX(), py = this.getY() + 1.0D, pz = this.getZ();
            for (int wave = 0; wave < 3; wave++) {
                double r = 2.0D + wave * 2.0D;
                int n = 8 + wave * 6;
                for (int i = 0; i < n; i++) {
                    double ang = (Math.PI * 2.0D * i) / n + wave * 0.35D;
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                            px + Math.cos(ang) * r, py + wave * 0.3D, pz + Math.sin(ang) * r,
                            1, 0.0D, 0.03D, 0.0D, 0.0D);
                }
            }
            // 2) 音符爆发（NOTE粒子：speed参数决定音符颜色，随机彩色）
            for (int i = 0; i < 12; i++) {
                double noteSpeed = this.getRandom().nextDouble();
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE,
                        px, py + 1.2D, pz, 1, 0.4D, 0.5D, 0.4D, noteSpeed);
            }
        }
        // 3) 提示音：阶段差异化音调（原版紫水晶音，无需音频资源；铺垫0.9/高潮1.4/低谷0.6）
        if (TunerCommonConfig.ACCENT_SOUND.get()) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 1.6F, pitch);
        }
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

    /** 主动瞬移间隔：二阶段乘以配置倍率（默认0.6=间隔缩短40%） */
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
     * 同时检测死亡状态自愈：若 boss 因 /kill 等绕过 hurt 拦截的方式进入死亡状态，
     * 且锁血未耗尽，则复活到下一档地板。
     */
    private void applyLockHealth() {
        ServerLevel level = (ServerLevel) this.level();
        int interval = TunerCommonConfig.LOCK_HEALTH_INTERVAL.get();
        float maxHealth = TunerCommonConfig.BOSS_MAX_HEALTH.get();
        if (this.getMaxHealth() != maxHealth) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        }
        int maxMark = (int) (maxHealth / interval); // 1024/128=8

        // 死亡状态自愈（兜底分支）：主检测在 tick() 覆写（aiStep 在死亡后不执行，
        // 本分支在死亡期间实际不可达，保留以防 tick 覆写被其他调用路径绕过）。
        // 平时每 tick 检测；锁血未耗尽（lockMark < maxMark-1）时任何死亡状态都回弹到下一档地板。
        if (TunerCommonConfig.LOCK_DEATH_REVIVE.get()
                && (this.isDeadOrDying() || this.deathTime > 0)
                && lockMark < maxMark - 1) {
            float reviveHp = Math.max(maxHealth - interval * (float) (lockMark + 1), 1.0F);
            this.deathTime = 0;
            this.setHealth(reviveHp);
            lockMark++;
            onLockTriggered(level, reviveHp, maxMark, "Revived from death");
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
        // 【第二十三轮】Boss身份伤害免疫：摔落、原版火焰（含火焰/岩浆/燃烧，
        // 覆盖 DamageTypeTags.IS_FIRE 全部火系）、窒息（卡墙）、溺水。
        // （不免疫魔法/爆炸/普通攻击等玩家可造成/可操作的伤害类型）
        if (source.is(DamageTypes.FALL)
                || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.DROWN)) {
            return false;
        }
        // 【第二十九轮】近战易伤：直接近身物理攻击（玩家/生物的手持武器挥击与横扫）
        // 伤害 ×(1+meleeVulnerability)，默认+25%，让贴身近战成为有效输出手段。
        // 投射物/爆炸/魔法等远程手段不受加成。
        if (isDirectMelee(source)) {
            amount *= (float) (1.0D + TunerCommonConfig.MELEE_VULNERABILITY.get());
        }
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

    /**
     * 判定伤害是否为"直接近战物理攻击"：
     * - 原版类型：player_attack / mob_attack（手持武器挥击与横扫；
     *   1.20.1 无 mob_attack_no_cooldown，通用兜底已覆盖该情况）；
     * - 通用兜底：伤害直接来源是生物本体（非投射物实体、非爆炸）——覆盖模组近战武器。
     */
    private static boolean isDirectMelee(DamageSource source) {
        if (source.is(DamageTypes.PLAYER_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK)) {
            return true;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        return source.getDirectEntity() instanceof LivingEntity
                && source.getDirectEntity() == source.getEntity();
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
        return super.canBeAffected(effect);
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
        this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, strengthLevel - 1, false, true, true));
        this.addEffect(new MobEffectInstance(GoetyEffects.RALLYING.get(), duration, rallyAmp, false, true, true));
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
        // 【第三十一轮】阶段切换打断：此前未覆写导致 DATA_CAST_STATE 不清（旧bug）
        castEnded();
    }

    @Override
    public void onCastFailed(com.tiaolvshi.goetytuner.focus.FocusEntry entry) {
        // 【第三十一轮】CastChannel 异常自愈（聚晶需玩家来源等）：施法已开始过，需递减计数。
        // 注意 beginCast 的 startSpell 异常路径不触发本回调（施法尚未开始，见 CastChannel）。
        castEnded();
    }

    private void broadcastMusicSync() {
        if (!(this.level() instanceof ServerLevel level)) {
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
        // 之后执行，故此处兜底：主手非法杖则补发暗法杖（IWand 自带 SoulUsing capability）。
        if (!(this.getMainHandItem().getItem() instanceof com.Polarice3.Goety.api.items.magic.IWand)) {
            this.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(com.Polarice3.Goety.common.items.ModItems.DARK_WAND.get()));
        }
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
        return BossPhase.values()[this.entityData.get(DATA_PHASE)];
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
