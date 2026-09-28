/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tiaolvshi.goetytuner.entity.TunerBoss;
import com.tiaolvshi.goetytuner.entity.TunerServant;
import com.tiaolvshi.goetytuner.focus.BossWandHelper;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 【0.0.21】「实际持惰性杖、画面画原杖」的渲染层。
 *
 * <h2>它要解决什么</h2>
 * <p>调律师一族的主手**必须**是惰性载体杖 {@code goetytuner:tuner_wand}
 * —— 否则通道型施法的 {@code startUsingItem} 会让 {@code goety:dark_wand}（含全部 staff）
 * 对非玩家施法者每 tick 冒 10~44 个白烟粒子 + 一声灭火音且不放法术
 * （完整链条见 {@link com.tiaolvshi.goetytuner.entity.ai.CastChannel#ensureInertWand} 里那段 javadoc）。
 *
 * <p>但用户不想要"用 staff 召唤出来的 Boss 手上变成一把暗法杖"这个外观变化
 * ⇒ 于是：**机制**用载体杖，**画面**画原来那把杖。载体杖的 tag 里存着原杖的完整快照
 * （{@link BossWandHelper#DISPLAY_KEY}），本层只是把"要画的那个栈"换成它。
 *
 * <h2>为什么用一个渲染层，而不是把主手物品本身换回去</h2>
 * <ul>
 *   <li>换回去就等于把 bug 也换回来了（危险的是**物品类**的 Item 层代码，不是外观）；</li>
 *   <li>{@code HumanoidMobRenderer} 的构造器里本来就已经挂了一个原版
 *       {@link ItemInHandLayer}（这一点 0.0.19 的文档写错了，本轮已更正）——
 *       所以这里的做法是"**把它替换成我们的子类**"，而不是"再加一层"
 *       （否则会画两遍）。</li>
 * </ul>
 *
 * <h2>为什么不需要任何封包 / 同步字段</h2>
 * <p>Mob 的主手物品本来就同步给客户端，且 Forge 的 {@code Item#shouldOverrideMultiplayerNbt()}
 * 默认为 {@code true} ⇒ 连 {@code IWand#getShareTag} 里的 tag 一起发；我们那份快照就存在 tag 里
 * ⇒ 客户端天然读得到，也天然跟着存档走。
 *
 * <h2>忠实程度</h2>
 * <p>画的是 `ItemStack#of(原杖快照)`，交给原版 {@code ItemInHandRenderer} 渲染
 * ⇒ 模型 / 贴图 / {@code display} 变换 / 附魔光效 / 自定义名 / 物品自己的
 * {@code getCustomRenderer()}（BEWLR，例如 {@code goety:nameless_staff}）**全部来自原物品本身**，
 * 不是我们仿出来的。属性修饰符由 {@code TunerWand#getAttributeModifiers} 同样代理过去。
 */
public class TunerHandLayer<T extends LivingEntity, M extends EntityModel<T> & ArmedModel>
        extends ItemInHandLayer<T, M> {

    /**
     * 快照解析缓存：key = 手上的载体杖**实例**（ItemStack 不覆写 equals ⇒ 天然是身份比较，
     * 装备变更会换成新实例 ⇒ 缓存自动失效），value = 解析好的显示杖。
     *
     * <p>为什么需要：本方法**每帧**都会被调用，而 {@code ItemStack.of} 要解一遍 NBT。
     * 用 {@link WeakHashMap} 是要求"手上那把栈被回收时缓存一起走"，不会漏内存。
     */
    private static final Map<ItemStack, ItemStack> DISPLAY_CACHE = new WeakHashMap<>();

    public TunerHandLayer(RenderLayerParent<T, M> parent, ItemInHandRenderer itemInHandRenderer) {
        super(parent, itemInHandRenderer);
    }

    @Override
    protected void renderArmWithItem(LivingEntity living, ItemStack stack, ItemDisplayContext ctx,
                                     HumanoidArm arm, PoseStack pose, MultiBufferSource buffer, int light) {
        ItemStack toRender = stack;
        // 只替换"主手那一下"：ItemInHandLayer 会把主手/副手两个栈分别传进来，
        // 副手传的不是 getMainHandItem() 的同一个实例 ⇒ 用身份比较精准区分（与左右手惯用无关）。
        if (living.getMainHandItem() == stack && isTuner(living)) {
            ItemStack display = displayOf(stack);
            if (!display.isEmpty()) {
                toRender = display;
            }
        }
        super.renderArmWithItem(living, toRender, ctx, arm, pose, buffer, light);
    }

    /** 只对本模组的两个实体做替换（别影响别的模组拿着 tuner_wand 的场合）。 */
    private static boolean isTuner(LivingEntity living) {
        return living instanceof TunerBoss || living instanceof TunerServant;
    }

    private static ItemStack displayOf(ItemStack carrier) {
        ItemStack cached = DISPLAY_CACHE.get(carrier);
        if (cached != null) {
            return cached;
        }
        ItemStack display = BossWandHelper.displayWand(carrier);
        DISPLAY_CACHE.put(carrier, display); // EMPTY 也缓存：没快照时不必每帧再查
        return display;
    }
}
