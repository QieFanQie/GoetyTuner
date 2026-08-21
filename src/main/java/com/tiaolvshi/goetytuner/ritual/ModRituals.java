/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.ritual;

import com.Polarice3.Goety.common.ritual.ModRitualFactory;
import com.tiaolvshi.goetytuner.GoetyTuner;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 把自定义仪式工厂注册进 Goety 的 {@code goety:ritual_factory} 注册表。
 *
 * <p>依据（反编译实证）：Goety 本体用
 * {@code DeferredRegister.create(new ResourceLocation("goety","ritual_factory"), "goety")}
 * 创建注册表（key = {@code goety:ritual_factory}），第三方 mod 用同样 key、自己的 modid
 * 创建 DeferredRegister 即可追加条目。配方 JSON 的 {@code ritual_type} 写
 * {@code goetytuner:tuner_boss_summon}，{@link RitualRecipe} 构造时经注册表解析 factory
 * 创建 Ritual 实例（注册必须先于配方加载——DeferredRegister 挂 MOD 总线天然满足）。
 */
public final class ModRituals {

    private ModRituals() {
    }

    public static final DeferredRegister<ModRitualFactory> RITUALS =
            DeferredRegister.create(new ResourceLocation("goety", "ritual_factory"), GoetyTuner.MOD_ID);

    public static final RegistryObject<ModRitualFactory> TUNER_BOSS_SUMMON =
            RITUALS.register("tuner_boss_summon", () -> new ModRitualFactory(TunerSummonRitual::new));

    public static void register(IEventBus modBus) {
        RITUALS.register(modBus);
    }
}
