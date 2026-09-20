/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.init;

import com.Polarice3.Goety.common.items.ServantSpawnEggItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 【0.0.19】调律师仆从刷怪蛋 —— **完全沿用诡厄巫法本体仆从刷怪蛋的范式**。
 *
 * <p>范式就在 {@link ServantSpawnEggItem} 里（本体所有仆从蛋都用它）：
 * <pre>{@code
 * if (player.isCrouching()) { owned.setTrueOwner(player); ... }   // 潜行 = 认主
 * }                                                                // 不潜行 = 野生（无主人）
 * }</pre>
 * 本类直接继承它 —— 因此 `useOn` / `use`（对液体放置）两条路径**都**享有该范式，
 * 而本类**刻意不再覆写任何 `useOn`/`use`**。
 *
 * <h2>0.0.19 收尾：按用户要求把提示收敛到"只有一行 tooltip"</h2>
 * <p>用户原话：「不要做这么多冗余的提示。只有：'调律师仆从刷怪蛋，潜行使用会生成你自己的仆从.'
 * 即可，不需要更多的物品介绍和放置/交互后的提示。」
 * <ul>
 *   <li>**删掉了放置时的动作栏提示**（原先会提示"认你为主人 / 野生的"）—— 直接不再覆写 `useOn`；</li>
 *   <li>**tooltip 只有一行**，内容就是用户给的那句（`tooltip.goetytuner.servant.spawn_egg`）；
 *       为此 `appendHoverText` **不调用 `super`** —— 父类会附上 Goety 自己的
 *       `tooltip.goety.servant.spawn_egg` 那一行，那属于"多余的物品介绍"。
 *       （基类 `Item.appendHoverText` 本身不产出任何文本，所以跳过后没有任何东西丢失。）</li>
 *   <li>聚晶指令（不释放 / 优先释放）的**动作栏提示也一并删掉**了，见
 *       {@code TunerServantInteractions}；那里改为**打日志**作为唯一的诊断通道。</li>
 * </ul>
 */
public class TunerServantSpawnEggItem extends ServantSpawnEggItem {

    public TunerServantSpawnEggItem(RegistryObject<? extends EntityType<? extends Mob>> entityTypeSupplier,
                                    int backgroundColor, int highlightColor, Properties properties) {
        super(entityTypeSupplier, backgroundColor, highlightColor, properties);
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable Level level,
                                @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        // 刻意**不调用 super**：父类会加 Goety 自己那条通用仆从蛋提示，而用户要求只保留一行。
        tooltip.add(Component.translatable("tooltip.goetytuner.servant.spawn_egg")
                .withStyle(ChatFormatting.GOLD));
    }
}
