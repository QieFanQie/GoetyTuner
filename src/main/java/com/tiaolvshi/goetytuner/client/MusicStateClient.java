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
        /**
         * 【0.0.5 性能】与 {@link #accents} 等长：每个重音 tick 所属的阶段。
         * 在 {@link #handleSync} 时预计算一次（同步包每 20 tick 才来一次），
         * 渲染端每帧直接按索引取 —— 原先每帧都要为每个重音重算 O(segments) 的分段查找，
         * 且二阶段 wrap(-1/0/+1) 三份条带会把同一 tick 的阶段算 3 遍。
         * 元素可能为 null（无分段数据时），语义与旧 phaseAtTick 返回 null 一致。
         */
        public BossPhase[] accentPhases = NO_PHASES;
        public long lastSyncMillis;

        /** 该 tick 所在分段的阶段（无分段数据/越界时返回 null = 普通样式） */
        public BossPhase phaseAtTick(int tick) {
            if (segments == null || segments.isEmpty()) {
                return null;
            }
            int acc = 0;
            for (Segment seg : segments) {
                acc += seg.ticks;
                if (tick < acc) {
                    return seg.phase;
                }
            }
            return null;
        }
    }

    /** 空数组常量（避免每次 new） */
    private static final BossPhase[] NO_PHASES = new BossPhase[0];

    private static final Map<Integer, State> STATES = new HashMap<>();
    /** 上次HUD渲染的tick（用于本地平滑推进） */
    private static long lastFrameTick = 0;

    public static void handleSync(SMusicSyncPacket pkt) {
        State s = STATES.computeIfAbsent(pkt.entityId, k -> new State());
        s.entityId = pkt.entityId;
        s.progressTick = pkt.progressTick;
        s.totalDuration = pkt.totalDuration;
        s.phase = BossPhase.byOrdinal(pkt.phaseOrdinal);
        s.phase2 = pkt.phase2;
        s.playing = pkt.playing;
        s.speed = pkt.speed > 0.0F ? pkt.speed : 1.0F;
        s.segments = new ArrayList<>();
        for (int i = 0; i < pkt.segments.phaseOrdinals.length; i++) {
            s.segments.add(new Segment(
                    BossPhase.byOrdinal(pkt.segments.phaseOrdinals[i]),
                    pkt.segments.ticks[i]));
        }
        s.accents = new ArrayList<>();
        for (int a : pkt.accents) {
            s.accents.add(a);
        }
        // 【0.0.5 性能】预计算每个重音所属阶段（渲染端每帧直接索引，见 State.accentPhases）
        s.accentPhases = new BossPhase[s.accents.size()];
        for (int i = 0; i < s.accents.size(); i++) {
            s.accentPhases[i] = s.phaseAtTick(s.accents.get(i));
        }
        s.lastSyncMillis = System.currentTimeMillis();
    }

    /**
     * 【0.0.5 性能】是否存在正在演奏的状态（供 HUD 每帧先做零分配早退）。
     *
     * <p>HUD 为了找到可见的调律师会遍历 {@code level.entitiesForRendering()}（客户端全部已加载实体，
     * 整合包可达数百个）；而在没有任何 Boss 演奏时，后续逻辑必然 return、一个像素都不画。
     * 用本方法提前短路即可完全跳过那次遍历。只读，不修改 STATES。
     */
    public static boolean anyPlaying() {
        for (State s : STATES.values()) {
            if (s.playing) {
                return true;
            }
        }
        return false;
    }

    /**
     * HUD每帧调用：获取平滑推进后的进度。
     * 【第十七轮】浮点连续推进：sinceSync/50.0F 不取整——此前 (int)(sinceSync/50) 每50ms
     * 才跳1tick（每秒20级台阶），60fps下表现为"蠕动"而非平移；现在逐帧连续平移。
     * 【第二十轮】乘以 speed（=音乐pitch），使音乐条与音频等比推进；
     * 【第二十一轮】二阶段变速已撤销，speed 恒为 pitchPhase1（默认 1.0）。
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
        out.accentPhases = s.accentPhases; // 引用共享（同步时预计算，渲染端只读）
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
