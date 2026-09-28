/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.focus;

import com.Polarice3.Goety.api.items.magic.IWand;
import com.Polarice3.Goety.api.magic.SpellType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import javax.annotation.Nullable;

/**
 * 【0.0.19】「调律师法杖」—— 调律师 Boss 与调律师仆从的配发法杖。
 *
 * <h2>为什么要自己造一把，而不是继续用 {@code goety:dark_wand}</h2>
 *
 * <p>起因是用户报的 bug：**腐化 / 震撼 / 炼狱这类"长按持续释放"的聚晶，在调律师身上
 * 只放一瞬间就停了**。反编译定位到根因（Goety 2.5.56.5 字节码/源码实证）：
 *
 * <ol>
 *   <li>{@code AbstractBeam.tick()}（腐化光束等"以法杖为基准"的光束）有这一行：
 *       <pre>{@code if (owner == null || !owner.isAlive() || (this.itemBase && !MobUtil.isSpellCasting(owner))) { this.discard(); return; } }</pre></li>
 *   <li>而 {@code MobUtil.isSpellCasting} 是：
 *       <pre>{@code livingEntity.isUsingItem() && livingEntity.getUseItem().getItem() instanceof IWand && !WandUtil.findFocus(livingEntity).isEmpty() }</pre></li>
 * </ol>
 *
 * <p>⇒ 光束要活着，施法者必须**真的正在"使用"一把装着聚晶的 IWand**。
 * 玩家按住右键天然满足；而 Mob 从不 {@code startUsingItem}，所以调律师放出的光束
 * **下一 tick 就被 discard** —— 玩家看到的就是"闪了一下就没了"。
 *
 * <p>修法是让调律师的通道在施法期间调用 {@code startUsingItem(MAIN_HAND)}（见 {@code CastChannel}）。
 * 但**不能**直接让 Mob 去"使用" {@code goety:dark_wand}（及其子类 {@code DarkStaff} /
 * {@code NamelessStaff}）：它被"使用"起来以后会冒白烟、响灭火音，而且一个法术都不放。
 *
 * <h2>【0.0.21 修正】精确的触发链（不是原来说的"每冷却 tick 的 onUseTick"）</h2>
 * <p>0.0.19 这里的说明写着「每 {@code Cooldown} tick 走一次 {@code MagicResults}」——
 * 那是**玩家侧**的路径。对 Mob 而言链条更短、频率更高，四环如下（均已用 jar 内字节码核对）：
 * <ol>
 *   <li>{@code DarkWand#getUseDuration}（SRG {@code m_8105_}）= {@code tag.getInt("Cast Time")}，
 *       而 **Mob 的持有物永远不跑 {@code ItemStack#inventoryTick}**（1.20.1 只有
 *       {@code Inventory#tick} 调它 ⇒ 仅玩家）⇒ 该键要么不存在（0）、要么是玩家留下的陈旧值；</li>
 *   <li>{@code LivingEntity#startUsingItem} 把 {@code getUseDuration()} 当 {@code useItemRemaining}；
 *       为 0 时 {@code updateUsingItem} **连 {@code onUseTick} 都不调**
 *       （前置条件 {@code getUseItemRemainingTicks() > 0}），直接 {@code completeUsingItem()}；</li>
 *   <li>⇒ {@code DarkWand#finishUsingItem}（SRG {@code m_5922_}）⇒ {@code MagicResults(...)}；</li>
 *   <li>{@code MagicResults} 整段包在 {@code caster instanceof Player} 里 ⇒ 非玩家掉进 else：
 *       {@code failParticles} = {@code for (i < random.nextInt(35) + 10)} 反复 {@code ParticleTypes.CLOUD}
 *       —— **恰好 10~44 个白烟粒子** —— 外加一声 {@code FIRE_EXTINGUISH}，**法术一个都不放**。</li>
 * </ol>
 * <p>{@code CastChannel#tickChannel} 的"每 tick 自愈"会让 ①②③④ 每 tick 重演一次
 * ⇒ Boss/仆从变成冒烟机器。**这就是用户报的那个 bug 的根因**。
 *
 * <h2>本类为什么是安全的（惰性的三个条件，缺一不可）</h2>
 * <ol>
 *   <li>{@link #onUseTick} 空实现 ⇒ 即使被使用也什么都不做；</li>
 *   <li>{@link #getUseDuration} 恒为 {@link #USE_DURATION}（72000）⇒ 通道（≤ 蓄力+持续，默认百 tick 量级）
 *       结束前**永远不会** {@code completeUsingItem()} ⇒ 第 3 环的 {@code finishUsingItem} 进不来
 *       （所以本类也**不需要**覆写 {@code finishUsingItem} —— {@code Item} 的默认实现什么副作用都没有）；</li>
 *   <li>不覆写 {@code releaseUsing}（{@code stopUsingItem} 会走它）⇒ 松手也没有任何副作用。</li>
 * </ol>
 *
 * <p>所以本模组自备一把 **行为干净** 的法杖：同一个 {@link IWand} 契约、同样的
 * {@code ITEM_HANDLER} capability（{@code SoulUsingItemHandler} 依赖它），
 * 但 {@link #onUseTick} 是空实现。模型直接继承 Goety 的 {@code goety:item/dark_wand}
 * （见 {@code models/item/tuner_wand.json}），所以**外观与之前完全一样**。
 *
 * <p>【0.0.21】配套的三处"必须让主手是这把杖"的收口（缺了任何一处，上面那条链就会在某个来源上复活）：
 * {@code TunerBoss}/{@code TunerServant} 构造 + 旧档迁移（按"是不是 TunerWand"判定、
 * 而不是只认 {@code goety:dark_wand} 注册名）、{@code TunerSummonRitual#initSummoned}（仪式召唤）、
 * 以及 {@code CastChannel#ensureInertWand}（运行时兜底 + 换杖）。
 *
 * <p>顺带把本项目遗留待办里的「Boss 专属法杖（TunerWand）」一并落地了
 * （此前用 {@code goety:dark_wand} 占位，见 {@code TECHNICAL_SUMMARY.md} §七）。
 */
public class TunerWand extends Item implements IWand {

    /**
     * "使用中"的时长（tick）。取值很大只为让 {@code LivingEntity} 不会自动
     * {@code completeUsingItem()}——真正决定何时松手的是 {@code CastChannel}。
     * （Goety 的 {@code IChargingSpell.defaultCastDuration()} 也是 72000，量级一致。）
     */
    public static final int USE_DURATION = 72000;

    public TunerWand() {
        super(new Properties()
                .rarity(Rarity.RARE)
                .setNoRepair()
                .stacksTo(1));
    }

    /** 通用系别（与 {@code dark_wand} 一致，接受所有聚晶）。 */
    @Override
    public SpellType getSpellType() {
        return SpellType.NONE;
    }

    /**
     * 【0.0.19 收尾】法杖"视觉高度"：与 {@code DarkWand} 保持一致（**0.4F**）。
     *
     * <p>{@code IWand} 的 default 是 **0.8F**，而 {@code goety:dark_wand} 覆写成 **0.4F**
     * （{@code DarkStaff} 又是另一个值）。这个值被用来把"从杖尖发出"的东西对齐到杖尖：
     * <ul>
     *   <li>{@code WaterJetRenderer} / {@code BurrowingLaserRenderer} / {@code PrismaBeamRenderer}
     *       —— 三条世界空间光束的起点（`new Vec3(0, 0.55, -staffHeight)` / 第一人称的近平面偏移）；</li>
     *   <li>{@code Spell#useParticle} 里给玩家发的 {@code SStaffParticlePacket}（法杖粒子起点高度）。</li>
     * </ul>
     *
     * <p><b>诚实说明（反编译实证，别误读）</b>：上面两条路径**目前都只对 Player 生效** ——
     * 三个渲染器由 {@code ClientEvents} 用 `for (Player player1 : players)` 驱动、方法签名也收
     * {@code Player}；{@code Spell} 那几处则显式写在 `if (caster instanceof Player player)` 分支里，
     * 非玩家走 `else` 的 `gatheringParticles(...)`。⇒ **对调律师 Boss / 仆从而言这个覆写当下没有可见影响。**
     * 之所以仍然写上：本杖的定位是 {@code goety:dark_wand} 的**完全等价替身**，
     * 凡是 dark_wand 定制过的 IWand 成员都应当一并对齐（这也是本轮唯一一处需要对齐的成员 ——
     * 其余覆写全是玩家专用路径：施法、交互、tooltip、NBT 记账）。
     * 万一将来 Goety 或某个附属把这条路径扩展到非玩家施法者，我们就是正确的。
     */
    @Override
    public float getWandVisualHeight(Level level, LivingEntity entity, ItemStack stack) {
        return 0.4F; // = goety:dark_wand
    }

    /**
     * **空实现（本类存在的核心理由之一）**。
     *
     * <p>{@code DarkWand.onUseTick} 会对非玩家施法者走 {@code MagicResults} 的
     * {@code failParticles + FIRE_EXTINGUISH} 分支（既不放法术、又冒烟响音）。调律师需要的是
     * "被 {@code MobUtil.isSpellCasting} 认作正在施法"，而**不需要** Item 层的任何副作用
     * —— 法术生命周期完全由 {@code CastChannel} 驱动。
     *
     * <p>⚠️ 注意：这条对本模组手里这把杖而言**实际上是第二道保险**。真正被触发的路径是
     * {@code getUseDuration() == 0} ⇒ {@code completeUsingItem()} ⇒ {@code finishUsingItem}
     * （见类注释的四环链条）；{@code onUseTick} 在那条链上根本不会被调用
     * （{@code updateUsingItem} 要求 {@code getUseItemRemainingTicks() > 0}）。
     * 两条都必须干净，所以两条都留着。
     */
    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remainingUseDuration) {
        // 故意留空，见方法 javadoc。
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    /**
     * 【0.0.21】**忠实代理**原杖的「手持属性修饰符」。
     *
     * <p>为什么需要：{@code goety:dark_staff}（以及继承它的 {@code NamelessStaff}）覆写了
     * {@code getAttributeModifiers(MAINHAND, stack)}，给**主手**挂
     * {@code ATTACK_DAMAGE} / {@code ATTACK_SPEED} modifier（{@code goety:dark_wand} 没有这一手）。
     * 0.0.20 及以前仪式把玩家那把杖塞进 Boss 主手 ⇒ Boss 顺带吃了这份加成；现在主手换成惰性载体杖
     * （这是修"每 tick 冒白烟"的前提），不做这一步就会**悄悄少一截近战伤害**
     * —— 那就不叫"忠实保留原杖属性"了。
     *
     * <p>做法是**代理**而不是抄数值：直接问 tag 里那份原杖快照
     * （{@link BossWandHelper#displayWand(ItemStack)}）。原杖给什么就是什么，
     * 将来 Goety 或附属改了数值我们也自动跟上（本模组**不写死任何数字**）。
     * 没有快照（刷怪蛋 / 指令召唤 / 原杖本来就是 {@code dark_wand}）时走默认实现。
     */
    @Override
    public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
            net.minecraft.world.entity.ai.attributes.AttributeModifier> getAttributeModifiers(
            net.minecraft.world.entity.EquipmentSlot slot, ItemStack stack) {
        ItemStack display = BossWandHelper.displayWand(stack);
        if (!display.isEmpty()) {
            var fromOriginal = display.getAttributeModifiers(slot);
            if (!fromOriginal.isEmpty()) {
                return fromOriginal;
            }
        }
        return super.getAttributeModifiers(slot, stack);
    }

    /**
     * 物品 capability：必须提供 {@code SoulUsingItemHandler}。
     *
     * <p>{@link IWand} 已经用 default 方法给出了实现（返回 {@code SoulUsingItemCapability}），
     * 这里显式委托给它——{@code BossWandHelper.installFocus} 与 {@code IWand.getFocus} 都依赖
     * 这条 capability（缺失会抛 "ItemStack is missing item capability"）。
     */
    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return IWand.super.initCapabilities(stack, nbt);
    }

    /**
     * 玩家手持时的右键行为：直接交给 Goety 的常规法杖流程是不行的（本类刻意不实现施法），
     * 所以只返回 PASS —— 这把杖是**配发给 AI 的**，不是给玩家用的。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}
