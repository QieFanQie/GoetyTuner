/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.client;

import com.tiaolvshi.goetytuner.entity.BossPhase;
import com.tiaolvshi.goetytuner.network.SMusicSyncPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * 客户端音乐状态镜像（仅渲染用途；权威状态在服务端）。
 * 每20tick被 SMusicSyncPacket 覆盖；两次同步之间本地按tick平滑推进指针。
 *
 * 【2026-08-18 第十三轮】新增 segments/accents 存储：HUD 用真实乐谱画分段颜色与重音刻度
 * （一阶段内容固定；二阶段"坠落"滚动时这些元素随内容整体左移）。
 */
public class MusicStateClient {

    /** 客户端分段（服务端乐谱镜像） */
    public static class Segment {
        public final BossPhase phase;
        public final int ticks;

        public Segment(BossPhase phase, int ticks) {
            this.phase = phase;
            this.ticks = ticks;
        }
    }

    public static class State {
        public int entityId;
        public int progressTick;
        /** 【第十七轮】浮点平滑进度（渲染用）：progressTick + 毫秒级连续推进，逐帧平移不蠕动 */
        public float progressTickF;
        public int totalDuration;
        public BossPhase phase;
        public boolean phase2;
        /** 【第十五轮】是否演奏中（有仇恨）：false 时 HUD 隐藏音乐条、进度冻结 */
        public boolean playing;
        /** 【第二十轮】乐谱推进速度（=音乐pitch）：本地平滑推进乘此系数 */
        public float speed = 1.0F;
        public List<Segment> segments = new ArrayList<>();
        public List<Integer> accents = new ArrayList<>();
        public long lastSyncMillis;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    /** 上次HUD渲染的tick（用于本地平滑推进） */
    private static long lastFrameTick = 0;

    public static void handleSync(SMusicSyncPacket pkt) {
        State s = STATES.computeIfAbsent(pkt.entityId, k -> new State());
        s.entityId = pkt.entityId;
        s.progressTick = pkt.progressTick;
        s.totalDuration = pkt.totalDuration;
        s.phase = BossPhase.values()[Math.min(pkt.phaseOrdinal, BossPhase.values().length - 1)];
        s.phase2 = pkt.phase2;
        s.playing = pkt.playing;
        s.speed = pkt.speed > 0.0F ? pkt.speed : 1.0F;
        s.segments = new ArrayList<>();
        for (int i = 0; i < pkt.segments.phaseOrdinals.length; i++) {
            s.segments.add(new Segment(
                    BossPhase.values()[Math.min(pkt.segments.phaseOrdinals[i], BossPhase.values().length - 1)],
                    pkt.segments.ticks[i]));
        }
        s.accents = new ArrayList<>();
        for (int a : pkt.accents) {
            s.accents.add(a);
        }
        s.lastSyncMillis = System.currentTimeMillis();
    }

    /**
     * HUD每帧调用：获取平滑推进后的进度。
     * 【第十七轮】浮点连续推进：sinceSync/50.0F 不取整——此前 (int)(sinceSync/50) 每50ms
     * 才跳1tick（每秒20级台阶），60fps下表现为"蠕动"而非平移；现在逐帧连续平移。
     * 【第二十轮】乘以 speed（=音乐pitch）：二阶段1.25倍速时音乐条与音频等比加速。
     * 不做本地取模：渲染端自行处理循环wrap（浮点模会引入精度跳变）。
     */
    public static State smoothed(int entityId) {
        State s = STATES.get(entityId);
        if (s == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        long sinceSync = now - s.lastSyncMillis;
        float localAdvanceF = sinceSync / 50.0F; // 1tick=50ms，连续浮点
        // 平滑副本：不修改同步基准
        State out = new State();
        out.entityId = s.entityId;
        out.totalDuration = s.totalDuration;
        out.phase = s.phase;
        out.phase2 = s.phase2;
        out.playing = s.playing;
        out.speed = s.speed;
        out.segments = s.segments;
        out.accents = s.accents;
        out.lastSyncMillis = s.lastSyncMillis;
        // 非演奏中：进度冻结（服务端音乐暂停推进，客户端不做本地平滑）
        out.progressTickF = s.playing ? s.progressTick + localAdvanceF * s.speed : s.progressTick;
        out.progressTick = (int) out.progressTickF;
        return out;
    }

    /** 目标消失/战斗结束时清理（由实体移除事件调用） */
    public static void clear(int entityId) {
        STATES.remove(entityId);
    }

    /** 【第二十一轮】当前已知boss实体id快照（客户端音频管理/状态清理用） */
    public static List<Integer> activeEntityIds() {
        return List.copyOf(STATES.keySet());
    }

    /** 【第二十一轮】原始状态（非平滑副本；客户端音频管理用，可能为null） */
    @Nullable
    public static State raw(int entityId) {
        return STATES.get(entityId);
    }

    /** 【第二十一轮】全清（退出世界时调用） */
    public static void clearAll() {
        STATES.clear();
    }
}
