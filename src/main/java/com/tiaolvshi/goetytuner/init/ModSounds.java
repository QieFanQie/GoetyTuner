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
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, GoetyTuner.MOD_ID);

    /** boss战音乐（一/二阶段全程同一版本；客户端 BossMusicManager 以循环实例播放，见 client/BossMusicManager） */
    public static final RegistryObject<SoundEvent> BOSS_MUSIC_PHASE1 =
            SOUND_EVENTS.register("boss_music_phase1", () -> SoundEvent.createVariableRangeEvent(
                    new net.minecraft.resources.ResourceLocation(GoetyTuner.MOD_ID, "boss_music_phase1")));
}
