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
 * 但**不能**直接让 Mob 去"使用" {@code goety:dark_wand}：{@code DarkWand.onUseTick} 会被
 * 每 tick 调用，而它的收尾是 {@code MagicResults(...)}；该方法对**非玩家施法者**走的是
 * {@code failParticles(...) + FIRE_EXTINGUISH} 分支（不会释放法术），
 * 结果是每 {@code Cooldown} tick 冒一把白烟、响一声灭火音 —— 不可接受。
 *
 * <p>所以本模组自备一把 **行为干净** 的法杖：同一个 {@link IWand} 契约、同样的
 * {@code ITEM_HANDLER} capability（{@code SoulUsingItemHandler} 依赖它），
 * 但 {@link #onUseTick} 是空实现。模型直接继承 Goety 的 {@code goety:item/dark_wand}
 * （见 {@code models/item/tuner_wand.json}），所以**外观与之前完全一样**。
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
     * **空实现（本类存在的核心理由）**。
     *
     * <p>{@code DarkWand.onUseTick} 会对非玩家施法者走 {@code failParticles + FIRE_EXTINGUISH}
     * 分支（既不放法术、又冒烟响音）。调律师需要的是"被 {@code MobUtil.isSpellCasting}
     * 认作正在施法"，而**不需要** Item 层的任何副作用 —— 法术生命周期完全由
     * {@code CastChannel} 驱动。
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
