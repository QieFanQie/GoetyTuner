/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.entity;

import com.Polarice3.Goety.api.entities.IOwned;
import com.Polarice3.Goety.api.items.magic.IWand;
import com.Polarice3.Goety.common.effects.GoetyEffects;
import com.Polarice3.Goety.common.entities.ally.Summoned;
import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.combat.BuffSpellPower;
import com.tiaolvshi.goetytuner.combat.DamageThrottle;
import com.tiaolvshi.goetytuner.combat.ServantWandBlessing;
import com.tiaolvshi.goetytuner.combat.TunerDamageRules;
import com.tiaolvshi.goetytuner.config.TunerCommonConfig;
import com.tiaolvshi.goetytuner.entity.ai.CastChannel;
import com.tiaolvshi.goetytuner.focus.FocusCategory;
import com.tiaolvshi.goetytuner.focus.FocusEntry;
import com.tiaolvshi.goetytuner.focus.FocusPoolManager;
import com.tiaolvshi.goetytuner.init.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * 【0.0.18】「调律师仆从」—— 调律师 Boss 的仆从版本。
 *
 * <h2>对应关系（诡厄巫法的"生物 ↔ 仆从"惯例）</h2>
 * <p>Goety 里每一种生物都有同形同大小的仆从版本（僵尸↔僵尸仆从、术士↔术士仆从…），
 * 仆从一律是 {@link Summoned} 的子类：由玩家拥有（{@code IOwned#getTrueOwner}）、跟随主人、
 * 受主人的目标牵引、吃仆从专属的加血/传送/日照规则、可被 {@code /goety summon} 一类的指令管理。
 * 本类照此办理：{@code extends Summoned} ⇒ 上面这些"一般仆从的性质"**全部继承**，
 * 不需要在本类里重复实现（这正是 Goety 给出的标准做法）。
 *
 * <p>与 Boss 的对应关系：
 * <table border="1">
 *   <tr><th></th><th>调律师（Boss）</th><th>调律师仆从</th></tr>
 *   <tr><td>实体</td><td>{@code goetytuner:tuner}</td><td>{@code goetytuner:tuner_servant}</td></tr>
 *   <tr><td>模型/贴图</td><td colspan="2">完全相同（{@code TunerModel} + {@code textures/entity/tuner.png}
 *       + 披风层 + 三颗悬浮立方体层）</td></tr>
 *   <tr><td>战斗驱动</td><td>乐谱三阶段（铺垫/高潮/低谷）</td><td>自己的目标（有目标就按轮换施法）</td></tr>
 *   <tr><td>聚晶来源</td><td colspan="2"><b>同一个</b> {@link FocusPoolManager}：全模组聚晶池 +
 *       静态评分 + 轮盘赌抽签 + 冷却池</td></tr>
 *   <tr><td>施法通道</td><td colspan="2"><b>同一个</b> {@link CastChannel}（前摇/锁池/朝向钉死/异常自愈）</td></tr>
 *   <tr><td>独有机制</td><td>锁血阶梯、瞬移走位、重音、二阶段、音乐</td>
 *       <td>主人的<b>聚晶指令</b>（左键=不释放 / 右键=优先释放）</td></tr>
 * </table>
 *
 * <h2>聚晶指令（本类独有的部分）</h2>
 * <p>玩家手持**聚晶**（{@code IFocus} 物品）左键/右键自己的调律师仆从：
 * <ul>
 *   <li><b>左键</b> = 设为「不释放」该聚晶（再左键一次取消）——该聚晶对本个体不再进入抽签、
 *       也不会被优先释放路径取到；</li>
 *   <li><b>右键</b> = 设为「优先释放」该聚晶（再右键一次取消）——只要它在池中且未在冷却，
 *       下一次施法就**插队**放它；同时设了多个则按设定的先后顺序依次尝试。</li>
 * </ul>
 * 两条指令都存在 {@link FocusPoolManager} 的**实例字段**上（不是 static！），
 * 加上存档序列化 ⇒ 只影响这一只仆从，且重启后依然有效。
 *
 * <h2>为什么刷怪蛋刷出来的仆从也能被指挥</h2>
 * <p>刷怪蛋/指令生成的仆从没有主人。若严格要求"主人才能下指令"，这条机制就完全不可达。
 * 因此：**无主的调律师仆从会在第一次被下达聚晶指令时认那位玩家为主人**
 * （见 {@code TunerServantInteractions}）。认主之后行为与普通仆从一致。
 *
 * <h2>性能（本项目红线 13：热路径不许做全量扫描）</h2>
 * <p>{@link #ownedMinionCount(ServerLevel)} 走的是 {@code level.getAllEntities()} 全量扫描，
 * 但**同一 gameTime 内只算一次**（缓存），因为 {@code tickCasting} 每 tick 可能问它好几次
 * （召唤类权重 + 满员判定）。这与 {@code SummonScoreTracker} 在 0.0.5 的修法一致。
 */
public class TunerServant extends Summoned implements CastChannel.TunerCastCallback, OrbHighlightSource {

    // ---- 同步数据（客户端立方体高亮 / 蹲姿） ----
    private static final EntityDataAccessor<Integer> DATA_CAST_CATEGORIES =
            SynchedEntityData.defineId(TunerServant.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CAST_STATE =
            SynchedEntityData.defineId(TunerServant.class, EntityDataSerializers.INT);

    /** 施法失败/抽空后的重试退避（tick），防止每 tick 空转重抽。 */
    private static final int CAST_RETRY_TICKS = 10;
    /** 施法被打断后的冷却（tick）：不惩罚聚晶（回功能池），但让仆从缓一拍。 */
    private static final int INTERRUPT_COOLDOWN_TICKS = 10;

    // ---- 战斗子系统（与 Boss 同源，但各自一份实例） ----
    /**
     * 本仆从自己的聚晶池（功能池 + 冷却池 + 静态评分 + 玩家下达的禁放/优先指令）。
     *
     * <p><b>刻意不调用 {@code pools.noteCast()}</b>：0.0.16 的「逐渐学习」是给调律师 Boss
     * 设计的（让它的**动态评分**随该个体施法次数逐步接管）。仆从应当永远以**初始分类**
     * 为准——它的池子每只一份、生命周期短，让它"临场学"只会让同一批仆从各打各的。
     * 因此这里保持 0.0.16 的起始权重（默认 0.15）不变：动态评分几乎不参与，初始分类主导。
     */
    private final FocusPoolManager pools = FocusPoolManager.createFightPools();
    private final EnumMap<FocusCategory, CastChannel> channels = new EnumMap<>(FocusCategory.class);
    @Nullable
    private CastChannel activeChannel;

    // ---- 立方体高亮（与 TunerBoss 完全相同的位掩码 + 双缓冲插值方案） ----
    private final int[] activeByCategory = new int[FocusCategory.values().length];
    private final Set<FocusEntry> activeVisualCasts = Collections.newSetFromMap(new IdentityHashMap<>());
    private final float[] orbHighlight = new float[3];
    private final float[] previousOrbHighlight = new float[3];

    // ---- 施法节奏 ----
    /** 轮换游标 */
    private int rotationIndex = 0;
    /** 距离下一次可以开始施法的 tick 数 */
    private int castCooldown = 0;
    /** 施法状态计数（0=站姿） */
    private int activeWarmups = 0;
    /** 轮换序列缓存（按配置原始字符串失效） */
    private String rotationRaw = null;
    private FocusCategory[] rotationCache = new FocusCategory[0];

    // ---- 仆从数量统计缓存（同一 gameTime 内复用；见类注释的性能说明） ----
    private int cachedMinionTick = Integer.MIN_VALUE;
    private int cachedMinionCount = 0;

    // ---- 召唤用杖（0.0.20） ----
    /**
     * 【0.0.20】仪式召唤时消耗的那把法杖的**快照**。
     *
     * <p>它同时承担两个职责（都读这一份，不允许有第二处来源）：
     * <ol>
     *   <li><b>强度依据</b>：{@link ServantWandBlessing} 读它的
     *       {@code goetytuner.magic_damage_bonus} 换算成强健 / 生命恢复 / 抗性提升的档位；</li>
     *   <li><b>死亡掉落</b>：仆从死亡时原样还给玩家（见
     *       {@code WandUpgradeEvents#dropServantSummonWand}）。</li>
     * </ol>
     * ⚠️ **不是主手物品**：主手常驻 {@code goetytuner:tuner_wand}（AI 施法载体，
     * {@code CastChannel} 要求主手是 IWand）。刷怪蛋 / {@code /summon} 出来的仆从这个字段为空
     * ⇒ 没有增益、也不掉杖。
     */
    private ItemStack summonWand = ItemStack.EMPTY;

    public TunerServant(EntityType<? extends Summoned> type, Level level) {
        super(type, level);
        this.xpReward = 10;
        for (FocusCategory c : FocusCategory.values()) {
            channels.put(c, new CastChannel(c, this));
        }
        // 主手常驻调律师法杖：CastChannel 的装配/施法路径都要求主手是 IWand
        // （SoulUsingItemHandler.get 对非 IWand 会抛异常，与 TunerBoss 同因）。
        // 【0.0.19】与 Boss 一样由 dark_wand 换成自备的 tuner_wand（长按类法术需要施法者
        // "真的在使用法杖"，而 DarkWand.onUseTick 对非玩家施法者会冒白烟+响灭火音）。
        this.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TUNER_WAND.get()));
        // 这把杖是"配发装备"，不该在仆从死亡时掉出来（生物默认掉落概率 8.5% ⇒ 白送一把法杖）。
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        // 与 TunerBoss 同样的规矩：createAttributes() 在注册阶段跑、读不到 config，
        // 所以属性表用常量，真正的数值在这里覆盖。
        //
        // 【0.0.19 用户要求】仆从的**血量 / 护甲**与本体保持一致 ⇒ 直接读 Boss 的那两个配置键
        // （boss.maxHealth / boss.equivalentArmor），而不是仆从自己的一套数值。
        // 减伤 / 限伤机制同样与本体共用实现，见 hurt / actuallyHurt 的覆写。
        var health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(TunerCommonConfig.BOSS_MAX_HEALTH.get());
        }
        var armor = this.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(TunerCommonConfig.EQUIVALENT_ARMOR.get());
        }
        var follow = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) {
            follow.setBaseValue(TunerCommonConfig.SERVANT_FOLLOW_RANGE.get());
        }
        this.setHealth(this.getMaxHealth());
    }

    /**
     * 属性表（只能用常量，见构造函数注释）。
     *
     * <p>⚠️ 这里的数值只是"注册阶段的占位"——真正的血量 / 护甲在构造函数里按 config 覆盖。
     * 【0.0.19】为了让"仆从与本体一致"，构造函数的覆盖来源改成了 Boss 的配置键，
     * 因此占位常量也同步改成与 {@code TunerBoss.createAttributes()} 相同的一组，
     * 避免"config 未加载时创建出来的实体数值不一致"这种边角差异。
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 216.0D)         // = boss.maxHealth 默认值
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)        // = servant.followRange 默认值
                .add(Attributes.ARMOR, 16.0D)               // = boss.equivalentArmor 默认值
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D); // = TunerBoss 同值
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_CAST_CATEGORIES, 0);
        this.entityData.define(DATA_CAST_STATE, 0);
    }

    /**
     * 免疫 Goety 的「召唤冷却」（{@code GoetyEffects.SUMMON_DOWN}），与 {@code TunerBoss} 同一取舍。
     *
     * <p>Goety 的召唤冷却实现是给**施法者**挂 SUMMON_DOWN 药水，`ISummonSpell.hasSummonDown`
     * 据此决定后续召唤是否被削弱（等级越高削得越狠）。对玩家来说是平衡，对 NPC 施法者而言
     * 只会表现为"我的仆从越召唤越废"。调律师 Boss 早已拒绝该效果，仆从照办以保持一致。
     *
     * <p>这是 {@code LivingEntity.addEffect}（以及 {@code forceAddEffect}）的**第一道**判定，
     * 所以在这里返回 false 是真正的免疫，而不是"加完再清"。
     */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (effect.getEffect() == GoetyEffects.SUMMON_DOWN.get()) {
            return false;
        }
        return super.canBeAffected(effect);
    }

    // ================= 【0.0.19】与本体一致的减伤 / 限伤 =================
    //
    // 用户要求：「调律师仆从的血量、护甲、减伤限伤机制和本体保持一致」。
    // 血量/护甲在构造函数里直接读 Boss 的配置键；这里补上**承受伤害的规则**。
    //
    // ⚠️ 刻意**不含** Boss 的锁血阶梯（lockMark / 宽限期免疫 / 致死截断 / /kill 后门 /
    //    索命后门）——那是 Boss 的招牌机制，仆从若也锁血就成了打不死的怪。
    //    规则本体与取舍理由见 TunerDamageRules / DamageThrottle 的类注释。

    /** 【0.0.19】限伤 / 限DPS 状态（与 TunerBoss 同一个实现，各持一份实例）。 */
    private final DamageThrottle damageThrottle = new DamageThrottle();

    /**
     * 与本体一致的**身份减伤**与**近战易伤**。
     *
     * <p>身份免疫：摔落 / 火焰（IS_FIRE 全系）/ 窒息 / 溺水 —— 让仆从不会莫名其妙被环境磨死。
     * 近战易伤：直接近身物理攻击 ×(1+{@code boss.meleeVulnerability})（默认 +25%）。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (TunerDamageRules.isIdentityImmune(source)) {
            return false;
        }
        amount = TunerDamageRules.applyMeleeVulnerability(source, amount,
                TunerCommonConfig.MELEE_VULNERABILITY.get());
        return super.hurt(source, amount);
    }

    /**
     * 与本体一致的**限伤 + 限DPS**（在最终结算处节流，受击动画/击退/无敌帧照常）。
     *
     * <p>与 Boss 的唯一差别是**没有后门豁免**：{@code /kill}（generic_kill）与否决类伤害
     * 本来就该正常生效，仆从不需要"管理员杀不死"这种保护。
     */
    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // 【0.0.20】改用**仆从自己的**限伤配置（用户要求"加强仆从的限伤机制"）。
            // 0.0.19 时这里读的是 [boss] 的两个键（限伤 25%、限DPS 关闭）⇒ 仆从照样会被
            // 高爆发几下打死。现在 [servant] 有独立且更狠的一组默认值：
            // 单次 ≤ 15% 最大生命、每秒 ≤ 50% 最大生命 ⇒ 无论 DPS 多高至少 2 秒才能打死。
            float allowed = damageThrottle.apply(
                    this.level().getGameTime(),
                    amount,
                    this.getMaxHealth(),
                    TunerCommonConfig.SERVANT_MAX_HIT_DAMAGE_PERCENT.get(),
                    TunerCommonConfig.SERVANT_MAX_DAMAGE_PER_SECOND.get());
            if (allowed < 0.0F) {
                return; // 每秒预算耗尽：本次伤害被完全吸收
            }
            amount = allowed;
        }
        super.actuallyHurt(source, amount);
    }

    /**
     * 目标/移动 Goal。
     *
     * <p>{@code super.registerGoals()} 已经把"一般仆从"的那一套装好了：
     * {@code Owned} 的主人受击反击/主人目标共享，{@code Summoned} 的跟随主人、
     * 仆从受击反击、仆从索敌。这里只补跑图相关的三个。
     *
     * <p>**刻意不加 LookAtPlayerGoal / RandomLookAroundGoal**（与 TunerBoss 同样的理由）：
     * 它们会与我们在 {@code aiStep} 里每 tick 的"钉住目标朝向"互相覆盖，导致施法时头部乱飘、
     * 法术方向打偏。施法朝向由 {@link CastChannel} 直接写 yRot/xRot，不受 Goal 干扰。
     */
    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(6, new Summoned.WanderGoal<>(this, 1.0D));
        this.goalSelector.addGoal(7, new Summoned.ReturnToGuardPos<>(this, 1.0D, 8));
    }

    // ================= 主循环 =================

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            // 客户端：把同步过来的类别位掩码平滑成 0~1 的高亮强度（与 TunerBoss 同款双缓冲插值）
            int mask = this.isAlive() ? this.getActiveCastCategories() : 0;
            for (int i = 0; i < 3; i++) {
                this.previousOrbHighlight[i] = this.orbHighlight[i];
                this.orbHighlight[i] = Mth.clamp(this.orbHighlight[i]
                        + ((mask & (1 << i)) != 0 ? 1F / 3 : -1F / 3), 0, 1);
            }
            return;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        // 【0.0.20】召唤用杖带来的持续性增益（强健 / 生命恢复 / 抗性提升）。
        // 放在所有 early-return 之前：施法中（activeChannel != null 那条 return）也必须照常维持。
        // 内部自带 1 秒降频与"数值未变则跳过"，每 tick 调用的开销只有一次取模。
        ServantWandBlessing.tick(this, this.summonWand);
        // 【0.0.20】再把「强健」等级换算成法术强度（SPELL_POTENCY，ADDITION）。
        // 顺序有意放在上一行之后：同一次 aiStep 里先把强健挂上，这里立刻就能读到它。
        // 用户要求"只对调律师生效"⇒ 只有本模组的两个实体调用这个方法。
        BuffSpellPower.tick(this);

        // 冷却池推进（与 Boss 一样每 tick 推进：到点的聚晶回功能池）
        this.pools.tickCooldowns();

        LivingEntity target = this.getTarget();

        // 施法中：只推进当前通道，不换目标姿态（朝向由 CastChannel 每 tick 钉死）
        if (this.activeChannel != null) {
            if (this.activeChannel.isBusy()) {
                this.activeChannel.tick(level, this.minionFillRatio(level), this.isSummonBlocked(level), 1.0D);
                return;
            }
            this.activeChannel = null;
        }

        if (target == null || !target.isAlive() || target.isRemoved()) {
            return;
        }
        // 有目标时注视目标（覆盖全部状态；Look Goal 已刻意不装）
        this.getLookControl().setLookAt(target, 30F, 30F);

        if (this.castCooldown > 0) {
            --this.castCooldown;
            return;
        }
        if (!this.beginNextCast(level)) {
            this.castCooldown = CAST_RETRY_TICKS;
        }
    }

    /**
     * 挑一个聚晶开始施法。先试「优先释放」名单（插队），再按配置的轮换序列抽签。
     *
     * @return 是否成功开始了一次施法
     */
    private boolean beginNextCast(ServerLevel level) {
        CastChannel priority = this.tryPriorityCast(level);
        if (priority != null) {
            this.activeChannel = priority;
            return true;
        }
        FocusCategory[] rotation = this.rotation();
        if (rotation.length == 0) {
            return false;
        }
        // 从当前游标开始依次尝试：某一类池空/条件不满足就顺延到下一类，最多试一整轮
        for (int i = 0; i < rotation.length; i++) {
            FocusCategory category = rotation[this.rotationIndex % rotation.length];
            ++this.rotationIndex;
            CastChannel channel = this.channels.get(category);
            if (channel != null && channel.beginCast(level, this.minionFillRatio(level),
                    this.isSummonBlocked(level), 1.0D)) {
                this.activeChannel = channel;
                return true;
            }
        }
        return false;
    }

    /**
     * 「优先释放」插队：按玩家设定的先后顺序，取第一个**当前可用**（在功能池里、未被禁放、
     * 未被全局拉黑）的优先聚晶，交给它自己类别的通道施放。
     *
     * @return 成功开始的通道；没有可用的优先聚晶时返回 null（调用方退回常规抽签）
     */
    @Nullable
    private CastChannel tryPriorityCast(ServerLevel level) {
        for (String id : this.pools.priorityFoci()) {
            FocusEntry entry = this.pools.takeSpecific(id); // 不在池中（冷却中）/被禁放 ⇒ null
            if (entry == null) {
                continue;
            }
            CastChannel channel = this.channels.get(entry.getCategory());
            if (channel == null || channel.isBusy()) {
                continue;
            }
            if (channel.beginCast(level, entry, 1.0D)) {
                return channel;
            }
        }
        return null;
    }

    // ================= 轮换序列 =================

    /** 仆从施法轮换序列（配置驱动，按原始字符串缓存）。 */
    private FocusCategory[] rotation() {
        String raw = TunerCommonConfig.SERVANT_ROTATION.get();
        if (!raw.equals(this.rotationRaw)) {
            this.rotationRaw = raw;
            this.rotationCache = FocusCategory.parseRoleSpec(raw);
        }
        return this.rotationCache;
    }

    // ================= 召唤物数量（召唤类权重用） =================

    /** 以本仆从为主人的存活召唤物数量（同一 gameTime 内缓存，见类注释）。 */
    private int ownedMinionCount(ServerLevel level) {
        int now = (int) level.getGameTime();
        if (this.cachedMinionTick == now) {
            return this.cachedMinionCount;
        }
        int count = 0;
        for (Entity e : level.getAllEntities()) {
            if (e instanceof IOwned owned && owned.getTrueOwner() == this
                    && e instanceof LivingEntity living && living.isAlive()) {
                ++count;
            }
        }
        this.cachedMinionTick = now;
        this.cachedMinionCount = count;
        return count;
    }

    private double minionFillRatio(ServerLevel level) {
        int max = Math.max(1, TunerCommonConfig.MAX_MINIONS.get());
        return Math.min(1.0D, this.ownedMinionCount(level) / (double) max);
    }

    private boolean isSummonBlocked(ServerLevel level) {
        return this.ownedMinionCount(level) >= Math.max(1, TunerCommonConfig.MAX_MINIONS.get());
    }

    // ================= CastChannel.TunerCastCallback =================

    @Override
    public LivingEntity boss() {
        return this;
    }

    @Override
    public FocusPoolManager pools() {
        return this.pools;
    }

    @Override
    public void onCastStart(FocusEntry entry) {
        this.changeCastCategory(entry, 1);
        this.castStarted();
    }

    @Override
    public void onCastFinish(FocusEntry entry) {
        this.changeCastCategory(entry, -1);
        this.castEnded();
        this.castCooldown = TunerCommonConfig.SERVANT_CAST_INTERVAL.get();
    }

    @Override
    public void onCastInterrupted(FocusEntry entry) {
        this.changeCastCategory(entry, -1);
        this.castEnded();
        this.castCooldown = INTERRUPT_COOLDOWN_TICKS;
    }

    @Override
    public void onCastFailed(FocusEntry entry) {
        this.changeCastCategory(entry, -1);
        this.castEnded();
        this.castCooldown = INTERRUPT_COOLDOWN_TICKS;
    }

    private void castStarted() {
        ++this.activeWarmups;
        this.entityData.set(DATA_CAST_STATE, 1);
        if (this.getPose() != Pose.CROUCHING) {
            this.setPose(Pose.CROUCHING);
        }
    }

    private void castEnded() {
        this.activeWarmups = Math.max(0, this.activeWarmups - 1);
        if (this.activeWarmups == 0) {
            this.entityData.set(DATA_CAST_STATE, 0);
            if (this.getPose() == Pose.CROUCHING) {
                this.setPose(Pose.STANDING);
            }
        }
    }

    /**
     * 立方体类别位掩码维护（照搬 TunerBoss 的做法）。
     *
     * <p>用 {@code FocusEntry} 的**身份集合**做幂等去重：同一次施法既可能走到
     * {@code onCastFinish} 又可能被补发 {@code onCastFailed}（见 CastChannel 的成对性说明），
     * 没有这层去重的话计数会被减两次、掩码提前归零。
     */
    private void changeCastCategory(FocusEntry entry, int delta) {
        if (delta > 0 ? !this.activeVisualCasts.add(entry) : !this.activeVisualCasts.remove(entry)) {
            return;
        }
        int index = entry.getCategory().ordinal();
        this.activeByCategory[index] = Math.max(0, this.activeByCategory[index] + delta);
        int mask = 0;
        for (int i = 0; i < this.activeByCategory.length; i++) {
            if (this.activeByCategory[i] > 0) {
                mask |= 1 << i;
            }
        }
        if (this.getActiveCastCategories() != mask) {
            this.entityData.set(DATA_CAST_CATEGORIES, mask);
        }
    }

    // ================= OrbHighlightSource =================

    @Override
    public float getOrbHighlight(int category, float partialTick) {
        return Mth.lerp(partialTick, this.previousOrbHighlight[category], this.orbHighlight[category]);
    }

    public int getActiveCastCategories() {
        return this.entityData.get(DATA_CAST_CATEGORIES);
    }

    public boolean isClientCasting() {
        return this.entityData.get(DATA_CAST_STATE) == 1;
    }

    // ================= 聚晶指令（供 TunerServantInteractions 调用） =================
    //
    // 【0.0.19 语义重做（用户反馈「左键不释放似乎不能取消」）】
    // 从"两张互不相干的表"改成"**同一个聚晶只有三种状态、互斥**"：
    //     中立  --左键-->  不释放  --左键-->  中立
    //     中立  --右键-->  优先    --右键-->  中立
    // 关键点：**左键会顺带清掉"优先"，右键会顺带清掉"不释放"**。
    // 原先两张表可以同时命中同一个聚晶（此时"不释放"胜出），于是"我先右键设了优先、
    // 再左键设了不释放，然后按右键想取消"就会出现"按了没反应"的错觉。
    // 现在任何一个按钮都**必然**能让这个聚晶回到中立状态，语义单一、可逆。

    /**
     * 左键指令：切换「不释放该聚晶」（并清掉它的「优先」）。
     *
     * @return 切换后的状态（true = 现在不释放）
     */
    public boolean toggleFocusDisabled(String itemId) {
        boolean now = !this.pools.isFocusDisabled(itemId);
        setDisabledExclusive(itemId, now);
        return now;
    }

    /**
     * 右键指令：切换「优先释放该聚晶」（并清掉它的「不释放」）。
     *
     * @return 切换后的状态（true = 现在优先释放）
     */
    public boolean toggleFocusPriority(String itemId) {
        boolean now = !this.pools.isFocusPriority(itemId);
        setPriorityExclusive(itemId, now);
        return now;
    }

    // ---- 批量指令（0.0.20：手持聚晶包 / 多晶大袋时） ----

    /**
     * 【0.0.20】批量「不释放」（手持聚晶包 / 多晶大袋左键仆从）。
     *
     * <p>语义与单个聚晶**完全一致**（用户已确认）：
     * <b>袋内全部聚晶都已经是"不释放" ⇒ 整体清除；否则 ⇒ 全部设为"不释放"</b>。
     * 也就是"再按一次就取消"，只不过作用域是整个袋子。
     *
     * @return 指令后的状态（true = 现在这些都处于"不释放"）
     */
    public boolean applyFocusDisabledBatch(Collection<String> itemIds) {
        boolean turnOn = !allDisabled(itemIds);
        for (String id : itemIds) {
            setDisabledExclusive(id, turnOn);
        }
        return turnOn;
    }

    /** 【0.0.20】批量「优先释放」。语义同上（全优先 ⇒ 整体清除，否则全部设为优先）。 */
    public boolean applyFocusPriorityBatch(Collection<String> itemIds) {
        boolean turnOn = !allPriority(itemIds);
        for (String id : itemIds) {
            setPriorityExclusive(id, turnOn);
        }
        return turnOn;
    }

    /** 袋子里的聚晶是否**全部**已在不释放表里（空集合返回 false ⇒ 走"设为"分支）。 */
    private boolean allDisabled(Collection<String> itemIds) {
        if (itemIds.isEmpty()) {
            return false;
        }
        for (String id : itemIds) {
            if (!this.pools.isFocusDisabled(id)) {
                return false;
            }
        }
        return true;
    }

    /** 袋子里的聚晶是否**全部**已在优先表里。 */
    private boolean allPriority(Collection<String> itemIds) {
        if (itemIds.isEmpty()) {
            return false;
        }
        for (String id : itemIds) {
            if (!this.pools.isFocusPriority(id)) {
                return false;
            }
        }
        return true;
    }

    // 互斥语义的**唯一实现**：单个切换与批量指令都走这两个私有方法，
    // 避免"批量是一套规则、单点又是另一套"（本项目红线：同一职责只允许一处实现）。
    // 见 0.0.19 的语义重做：左键顺手清"优先"、右键顺手清"不释放"。

    /** 设为 / 取消「不释放」，并顺手清掉同一聚晶的「优先」。 */
    private void setDisabledExclusive(String itemId, boolean disabled) {
        this.pools.setFocusPriority(itemId, false);
        this.pools.setFocusDisabled(itemId, disabled);
    }

    /** 设为 / 取消「优先释放」，并顺手清掉同一聚晶的「不释放」。 */
    private void setPriorityExclusive(String itemId, boolean priority) {
        this.pools.setFocusDisabled(itemId, false);
        this.pools.setFocusPriority(itemId, priority);
    }

    public boolean isFocusDisabled(String itemId) {
        return this.pools.isFocusDisabled(itemId);
    }

    public boolean isFocusPriority(String itemId) {
        return this.pools.isFocusPriority(itemId);
    }

    // ---- 召唤用杖（0.0.20） ----

    /** 召唤用杖快照（没有则返回 {@link ItemStack#EMPTY}）。 */
    public ItemStack getSummonWand() {
        return this.summonWand;
    }

    /** 仪式召唤时写入（见 {@code TunerServantSummonRitual#initSummoned}）。 */
    public void setSummonWand(ItemStack stack) {
        this.summonWand = (stack == null || stack.isEmpty()) ? ItemStack.EMPTY : stack.copy();
    }

    // ================= 音效 / NBT =================

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null; // TODO(音频): 与 Boss 一致，暂无专属音效
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // 【0.0.18】聚晶指令落盘：不落盘的话重启就丢，玩家会以为"右键设置没用"
        tag.put("DisabledFoci", writeStrings(this.pools.disabledFoci()));
        tag.put("PriorityFoci", writeStrings(this.pools.priorityFoci()));
        // 【0.0.20】召唤用杖落盘：它既是增益依据、又是死亡掉落物，
        // 不落盘的话"重进存档 ⇒ 仆从变弱、死了也不还杖"。
        if (!this.summonWand.isEmpty()) {
            tag.put("SummonWand", this.summonWand.save(new CompoundTag()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.pools.restoreFocusCommands(
                readStrings(tag, "DisabledFoci"), readStrings(tag, "PriorityFoci"));
        // 【0.0.20】恢复召唤用杖（老存档 / 刷怪蛋仆从没有这个键 ⇒ 保持 EMPTY）
        if (tag.contains("SummonWand", 10)) { // 10 = CompoundTag
            this.summonWand = ItemStack.of(tag.getCompound("SummonWand"));
        }
        // 与 TunerBoss 相同的旧档自愈：从 NBT 恢复时主手可能不是法杖（或为空），
        // 而 SoulUsingItemHandler.get 对非 IWand 会抛异常 ⇒ 补发调律师法杖。
        // 【0.0.19】同时完成「goety:dark_wand → goetytuner:tuner_wand」的旧档迁移
        // （dark_wand 也是 IWand、能通过 instanceof 判定，但撑不起长按类法术）。
        if (!(this.getMainHandItem().getItem() instanceof IWand)
                || this.getMainHandItem().getItem()
                == com.Polarice3.Goety.common.items.ModItems.DARK_WAND.get()) {
            this.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TUNER_WAND.get()));
        }
    }

    private static ListTag writeStrings(java.util.Collection<String> values) {
        ListTag list = new ListTag();
        for (String v : values) {
            list.add(StringTag.valueOf(v));
        }
        return list;
    }

    private static List<String> readStrings(CompoundTag tag, String key) {
        List<String> out = new ArrayList<>();
        ListTag list = tag.getList(key, 8); // 8 = StringTag
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
        return out;
    }
}
