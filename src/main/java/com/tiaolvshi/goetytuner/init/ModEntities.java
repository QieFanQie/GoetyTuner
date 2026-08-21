/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.init;

import com.tiaolvshi.goetytuner.GoetyTuner;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, GoetyTuner.MOD_ID);

    public static final RegistryObject<EntityType<com.tiaolvshi.goetytuner.entity.TunerBoss>> TUNER =
            ENTITY_TYPES.register("tuner", () ->
                    EntityType.Builder.of(com.tiaolvshi.goetytuner.entity.TunerBoss::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.95F) // 人形，类玩家
                            .clientTrackingRange(32)
                            .fireImmune()
                            .build("tuner"));
}
