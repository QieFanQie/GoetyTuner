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

    /**
     * 【0.0.18】调律师仆从。
     *
     * <p>尺寸/类别照抄 Goety 本体仆从的写法（如 {@code goety:warlock_servant}：
     * {@code MobCategory.MONSTER} + {@code sized(0.6F, 1.95F)} + {@code clientTrackingRange(8)}）——
     * "仆从与本体同形同大小"正是诡厄巫法里生物↔仆从的对应惯例。
     * （仆从实体是 {@code Summoned} 子类而非 {@code Monster}，故不继承 {@code fireImmune}。）
     */
    public static final RegistryObject<EntityType<com.tiaolvshi.goetytuner.entity.TunerServant>> TUNER_SERVANT =
            ENTITY_TYPES.register("tuner_servant", () ->
                    EntityType.Builder.of(com.tiaolvshi.goetytuner.entity.TunerServant::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.95F) // 与 Boss 同形（仆从惯例）
                            .clientTrackingRange(8)
                            .build("tuner_servant"));
}
