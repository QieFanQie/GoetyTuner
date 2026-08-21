/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.client;

import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.config.TunerCommonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * 【2026-08-20 第二十一轮】Boss战音乐客户端管理器。
 *
 * 音频彻底客户端化（此前服务端 playSound 方案的三大缺陷一并修复）：
 * - 单一循环音效实例：looping=true + Attenuation.NONE + relative（原版 forMusic 同款模式），
 *   一/二阶段全程同一实例同一pitch——阶段切换/循环翻页不再重启音频，无双实例重叠（问题1）
 * - 演奏期间拦截原版背景音乐（MUSIC 类别声音取消 + 起播时停掉正在播的背景音乐）（问题2）
 * - boss死亡/消失/脱战 → 检测同步包失效 → 停止实例（问题3）
 *
 * 起播/停播依据：MusicStateClient 的 playing 状态（服务端每20tick同步）。
 * 服务端在脱战→再演奏边沿会 music.restart() 归零乐谱并立即同步——客户端音频从头起播，
 * 两端 0 点对齐；循环翻页由实例原生 looping 无缝衔接。
 */
@Mod.EventBusSubscriber(modid = GoetyTuner.MOD_ID, value = Dist.CLIENT)
public class BossMusicManager {

    private static final ResourceLocation BOSS_MUSIC_LOCATION =
            new ResourceLocation(GoetyTuner.MOD_ID, "boss_music_phase1");

    /** 同步包超时（ms）：超过视为boss已死亡/消失/脱出128格同步范围，清理其状态 */
    private static final long SYNC_TIMEOUT_MS = 3000;
    /** 无演奏状态持续tick数，超过则停播（防目标短暂闪断造成音乐抖动） */
    private static final int STOP_GRACE_TICKS = 30;

    @Nullable
    private static SoundInstance music;
    private static int silentTicks = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            // 退出世界：停播 + 全部状态清理
            stopMusic(mc);
            MusicStateClient.clearAll();
            return;
        }

        long now = System.currentTimeMillis();
        boolean anyPlaying = false;
        for (int id : MusicStateClient.activeEntityIds()) {
            MusicStateClient.State s = MusicStateClient.raw(id);
            if (s == null) {
                continue;
            }
            // boss实体已不存在/已死亡（含死亡动画结束被移除），或同步包超时 → 清理
            var entity = mc.level.getEntity(id);
            if (entity == null || !entity.isAlive() || now - s.lastSyncMillis > SYNC_TIMEOUT_MS) {
                MusicStateClient.clear(id);
            } else if (s.playing) {
                anyPlaying = true;
            }
        }

        if (anyPlaying) {
            silentTicks = 0;
            if (music == null) {
                startMusic(mc);
            }
        } else if (music != null && ++silentTicks > STOP_GRACE_TICKS) {
            // 脱战（playing=false）/boss已被清理：宽限期后停播
            stopMusic(mc);
        }
    }

    /**
     * 拦截原版背景音乐：boss战音乐演奏期间，取消 MUSIC 类别的新声音（含MusicManager
     * 延迟调度的新曲目）。boss音乐自身走 RECORDS 类别，不受影响。
     */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getOriginalSound();
        if (sound == null) {
            return;
        }
        if (music != null && sound.getSource() == SoundSource.MUSIC) {
            event.setSound(null);
        }
    }

    private static void startMusic(Minecraft mc) {
        stopMusic(mc); // 保险：替换残留旧实例
        // 压掉正在播的原版背景音乐（stopPlaying 只停MusicManager当前曲目，不动其他声音）
        mc.getMusicManager().stopPlaying();
        // pitch 与服务端乐谱推进速度一致（SMusicSyncPacket.speed 同步值；缺省回落本地配置）
        float pitch = playingSpeedOrConfig();
        float volume = TunerCommonConfig.MUSIC_VOLUME.get().floatValue();
        // 原版 forMusic 同款：无衰减、相对定位（不随镜头距离变化）；looping=true 原生无缝循环
        music = new SimpleSoundInstance(BOSS_MUSIC_LOCATION, SoundSource.RECORDS,
                volume, pitch, RandomSource.create(), true, 0,
                SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
        mc.getSoundManager().play(music);
    }

    private static void stopMusic(Minecraft mc) {
        if (music != null) {
            mc.getSoundManager().stop(music);
            music = null;
        }
        silentTicks = 0;
    }

    /** 取任一演奏中状态的同步速度（=pitchPhase1）；无状态时回落本地配置值 */
    private static float playingSpeedOrConfig() {
        for (int id : MusicStateClient.activeEntityIds()) {
            MusicStateClient.State s = MusicStateClient.raw(id);
            if (s != null && s.playing && s.speed > 0.0F) {
                return s.speed;
            }
        }
        return (float) Math.max(0.05, TunerCommonConfig.MUSIC_PITCH_PHASE1.get());
    }
}
