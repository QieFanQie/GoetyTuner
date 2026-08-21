/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.network;

import com.tiaolvshi.goetytuner.client.MusicStateClient;
import com.tiaolvshi.goetytuner.entity.BossPhase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 音乐同步包（服务端权威 → 客户端HUD）。
 *
 * 每 N tick（默认20）由 TunerBoss 广播：
 * - entityId: boss实体id（多boss区分）
 * - progressTick: 音乐当前进度tick（服务端权威）
 * - totalDuration: 总时长（HUD指针位置 = progress/total * 宽度）
 * - phaseOrdinal: BossPhase枚举序号
 * - phase2: 二阶段标记（HUD切"坠落"滚动模式：指针固定左侧、内容左移）
 * - segments: 分段表（phase+ticks），客户端按真实乐谱画颜色分段
 * - accents: 重音tick表，客户端画重音刻度（随二阶段内容一起滚动）
 *
 * 【2026-08-18 第十三轮】新增 segments/accents 全量同步（每20tick一次，数据量小，带宽可接受）。
 * 【2026-08-20 第二十轮】新增 speed（乐谱推进速度=音乐pitch）：客户端本地平滑推进
 * 乘以该系数，二阶段 1.25 倍速时音乐条与重音随音频等比加速。
 */
public class SMusicSyncPacket {

    /** 分段（网络传输用扁平数组：phaseOrdinals[] + ticks[]） */
    public static final class SegData {
        public final int[] phaseOrdinals;
        public final int[] ticks;

        public SegData(int[] phaseOrdinals, int[] ticks) {
            this.phaseOrdinals = phaseOrdinals;
            this.ticks = ticks;
        }
    }

    public final int entityId;
    public final int progressTick;
    public final int totalDuration;
    public final int phaseOrdinal;
    public final boolean phase2;
    public final SegData segments;
    public final int[] accents;
    /** 【第十五轮】是否有仇恨（演奏中）：false 时客户端隐藏音乐条 */
    public final boolean playing;
    /** 【第二十轮】乐谱推进速度（=音乐pitch；客户端平滑推进用） */
    public final float speed;

    public SMusicSyncPacket(int entityId, int progressTick, int totalDuration, int phaseOrdinal, boolean phase2,
                            List<? extends com.tiaolvshi.goetytuner.entity.MusicController.Segment> segments,
                            List<Integer> accents, boolean playing, float speed) {
        this.entityId = entityId;
        this.progressTick = progressTick;
        this.totalDuration = totalDuration;
        this.phaseOrdinal = phaseOrdinal;
        this.phase2 = phase2;
        this.playing = playing;
        this.speed = speed;
        int n = segments.size();
        int[] ords = new int[n];
        int[] tks = new int[n];
        for (int i = 0; i < n; i++) {
            ords[i] = segments.get(i).phase.ordinal();
            tks[i] = segments.get(i).ticks;
        }
        this.segments = new SegData(ords, tks);
        this.accents = accents.stream().mapToInt(Integer::intValue).toArray();
    }

    public static void encode(SMusicSyncPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.entityId);
        buf.writeVarInt(pkt.progressTick);
        buf.writeVarInt(pkt.totalDuration);
        buf.writeByte(pkt.phaseOrdinal);
        buf.writeBoolean(pkt.phase2);
        buf.writeBoolean(pkt.playing);
        buf.writeFloat(pkt.speed);
        // segments
        buf.writeVarInt(pkt.segments.phaseOrdinals.length);
        for (int i = 0; i < pkt.segments.phaseOrdinals.length; i++) {
            buf.writeByte(pkt.segments.phaseOrdinals[i]);
            buf.writeVarInt(pkt.segments.ticks[i]);
        }
        // accents
        buf.writeVarInt(pkt.accents.length);
        for (int a : pkt.accents) {
            buf.writeVarInt(a);
        }
    }

    public static SMusicSyncPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        int progressTick = buf.readVarInt();
        int totalDuration = buf.readVarInt();
        int phaseOrdinal = buf.readByte();
        boolean phase2 = buf.readBoolean();
        boolean playing = buf.readBoolean();
        float speed = buf.readFloat();
        int segN = buf.readVarInt();
        List<com.tiaolvshi.goetytuner.entity.MusicController.Segment> segs = new ArrayList<>(segN);
        int[] ords = new int[segN];
        int[] tks = new int[segN];
        for (int i = 0; i < segN; i++) {
            ords[i] = buf.readByte();
            tks[i] = buf.readVarInt();
            segs.add(new com.tiaolvshi.goetytuner.entity.MusicController.Segment(
                    BossPhase.values()[Math.min(ords[i], BossPhase.values().length - 1)], tks[i]));
        }
        int accN = buf.readVarInt();
        List<Integer> accents = new ArrayList<>(accN);
        for (int i = 0; i < accN; i++) {
            accents.add(buf.readVarInt());
        }
        return new SMusicSyncPacket(entityId, progressTick, totalDuration, phaseOrdinal, phase2, segs, accents, playing, speed);
    }

    public static void consume(SMusicSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        MusicStateClient.handleSync(pkt)));
        ctx.get().setPacketHandled(true);
    }
}
