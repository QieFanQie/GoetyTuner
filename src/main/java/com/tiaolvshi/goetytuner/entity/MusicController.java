/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.config.TunerCommonConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 音乐总谱：服务端权威计时。
 *
 * - musicProgressTick 总tick推进（按音乐实际时长循环）。
 * - 分段表 segments：[{phase:"buildup", ticks:1200}, {phase:"climax", ticks:600}, {phase:"valley", ticks:400}, ...]
 *   分布根据实际音乐调整；加载自 config/goetytuner/music_score.json（缺失则生成默认）。
 * - 重音表 accents：乐谱中的重音tick列表；演奏到重音时触发斥力/无前摇施法等。
 *
 * 【2026-08-20 第二十一轮】音频彻底客户端化：客户端 BossMusicManager 以循环音效实例
 * 播放（pitch=pitchPhase1 恒定），服务端不再 playSound——一/二阶段全程同一版本同一速度，
 * 阶段切换/循环翻页均不重启音频，从根上消除双实例重叠。脱战→再演奏边沿乐谱归零对齐。
 *
 * 阶段切换由服务端根据分段表自行决定，客户端仅接收同步包渲染节奏条。
 */
public class MusicController {

    public static class Segment {
        public final BossPhase phase;
        public final int ticks;

        public Segment(BossPhase phase, int ticks) {
            this.phase = phase;
            this.ticks = ticks;
        }
    }

    private final List<Segment> segments = new ArrayList<>();
    private final List<Integer> accents = new ArrayList<>();
    private final Set<Integer> accentSet = new HashSet<>();

    private float progressF = 0.0F;      // 【第二十轮】浮点进度（按播放速度推进）
    private int musicProgressTick = 0;   // 总进度（循环内，progressF取整）
    private int totalDuration = 0;
    private int currentSegmentIndex = 0;
    private boolean phase2 = false;      // 二阶段：机制标记（音乐不再变速/重启，第二十一轮）

    // ---- 上帧状态（供boss感知变化） ----
    private BossPhase currentPhase = BossPhase.BUILDUP;
    private boolean accentThisTick = false;
    private boolean segmentChanged = false;

    public MusicController() {
        loadScore();
    }

    // ================= 乐谱加载 =================

    private void loadScore() {
        Path file = FMLPaths.CONFIGDIR.get().resolve(GoetyTuner.MOD_ID).resolve("music_score.json");
        try {
            if (!Files.exists(file)) {
                writeDefaultScore(file);
            }
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            segments.clear();
            accents.clear();
            for (var el : root.getAsJsonArray("segments")) {
                JsonObject o = el.getAsJsonObject();
                segments.add(new Segment(BossPhase.byId(o.get("phase").getAsString()),
                        o.get("ticks").getAsInt()));
            }
            for (var el : root.getAsJsonArray("accents")) {
                accents.add(el.getAsInt());
            }
        } catch (Exception ex) {
            GoetyTuner.LOGGER.error("Failed to load music score, fallback to default", ex);
            segments.clear();
            accents.clear();
            segments.add(new Segment(BossPhase.BUILDUP, 1200));
            segments.add(new Segment(BossPhase.CLIMAX, 600));
            segments.add(new Segment(BossPhase.VALLEY, 400));
        }
        accentSet.clear();
        accentSet.addAll(accents);
        totalDuration = segments.stream().mapToInt(s -> s.ticks).sum();
        if (totalDuration <= 0) {
            segments.add(new Segment(BossPhase.BUILDUP, 1200));
            totalDuration = 1200;
        }
    }

    /**
     * 【2026-08-20 第二十轮】默认乐谱 = 随 mod 附带的 boss_music_phase1.ogg 实测标注：
     * 音频 98.27s（1965tick）/ BPM120（1拍=0.5s=10tick，节拍网格锚定4s处）。
     * 分段：0-4s铺垫 / 4s-52s高潮 / 52s-1:27低谷 / 1:27-结束铺垫（循环无缝衔接开头）。
     * 重音：高潮每4拍（2s，即每小节强拍）、低谷与铺垫每8拍（4s），
     * 并强制命中三个分段转换点：80t(4s)/1040t(52s)/1740t(1:27) + 翻页点0。
     */
    private void writeDefaultScore(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        JsonObject root = new JsonObject();
        JsonArray segs = new JsonArray();
        segs.add(seg(BossPhase.BUILDUP, 80));   // 0s-4s
        segs.add(seg(BossPhase.CLIMAX, 960));   // 4s-52s
        segs.add(seg(BossPhase.VALLEY, 700));   // 52s-1:27
        segs.add(seg(BossPhase.BUILDUP, 225));  // 1:27-98.25s（衔接循环开头的4s铺垫）
        root.add("segments", segs);
        JsonArray acc = new JsonArray();
        // 翻页点（循环起点）
        acc.add(0);
        // 高潮 80..1040 每40tick（4拍=1小节强拍）
        for (int t = 80; t <= 1040; t += 40) {
            acc.add(t);
        }
        // 低谷 1120..1680 每80tick（8拍）
        for (int t = 1120; t <= 1680; t += 80) {
            acc.add(t);
        }
        // 低谷→铺垫转换点（1:27，节拍网格上但不在80tick等差线上）
        acc.add(1740);
        // 尾部铺垫：1820(91s)、1900(95s)
        acc.add(1820);
        acc.add(1900);
        root.add("accents", acc);
        Files.writeString(file, root.toString());
    }

    private static JsonObject seg(BossPhase p, int ticks) {
        JsonObject o = new JsonObject();
        o.addProperty("phase", p.getId());
        o.addProperty("ticks", ticks);
        return o;
    }

    // ================= 每tick推进 =================

    /**
     * 【第二十一轮】当前乐谱推进速度 = pitchPhase1（全程恒定）。
     * 二阶段不再变速：音频由客户端 BossMusicManager 以同一 pitch 的循环实例播放，
     * 乐谱与音频时间轴全程 1:1。
     */
    public float getSpeed() {
        return (float) Math.max(0.05, TunerCommonConfig.MUSIC_PITCH_PHASE1.get());
    }

    /** 服务端每tick调用。返回当前阶段；重音/分段变化通过getter查询。 */
    public BossPhase tick() {
        segmentChanged = false;
        accentThisTick = false;

        int prevTick = (int) progressF;
        progressF += getSpeed();
        if (progressF >= totalDuration) {
            progressF %= totalDuration; // 翻页：从头继续（音频由客户端循环实例无缝衔接）
            currentSegmentIndex = 0;
            segmentChanged = true;
        }
        musicProgressTick = (int) progressF;

        // 重音判定：本tick跨越的整数tick区间 (prevTick, newTick] 内是否夹着重音
        // （speed>1时单tick可能跨多个整数刻度，须逐一检查防止漏判）
        accentThisTick = crossedAccent(prevTick, musicProgressTick);

        // 计算当前所处分段
        int acc = 0;
        int idx = 0;
        for (int i = 0; i < segments.size(); i++) {
            acc += segments.get(i).ticks;
            idx = i;
            if (musicProgressTick < acc) {
                break;
            }
        }
        if (idx != currentSegmentIndex) {
            currentSegmentIndex = idx;
            segmentChanged = true;
        }
        currentPhase = segments.get(currentSegmentIndex).phase;
        // 【第二十二轮】二阶段相位转换：低谷段按铺垫期处理（音乐条分段颜色、
        // 阶段文字、boss行为——铺垫轮换施法+弧形走位——全部生效），
        // 一阶段乐谱分段保持原样不变。
        if (phase2 && currentPhase == BossPhase.VALLEY) {
            currentPhase = BossPhase.BUILDUP;
        }
        return currentPhase;
    }

    /** (prevTick, newTick] 区间（含翻页回绕）是否夹着重音 */
    private boolean crossedAccent(int prevTick, int newTick) {
        if (newTick > prevTick) {
            for (int t = prevTick + 1; t <= newTick; t++) {
                if (accentSet.contains(t)) {
                    return true;
                }
            }
        } else if (newTick < prevTick) {
            // 翻页回绕：检查 (prevTick, total) 与 [0, newTick] 两段
            for (int t = prevTick + 1; t < totalDuration; t++) {
                if (accentSet.contains(t)) {
                    return true;
                }
            }
            for (int t = 0; t <= newTick; t++) {
                if (accentSet.contains(t)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ================= 二阶段 =================

    /**
     * 半血进入二阶段：仅机制标记（通道重建/仆从清理/瞬移等见 TunerBoss.onEnterPhase2）。
     * 【第二十一轮】音乐全程同一版本同一速度，不重置进度、不重启音频。
     */
    public void enterPhase2() {
        phase2 = true;
    }

    public boolean isPhase2() {
        return phase2;
    }

    /**
     * 【第二十一轮】乐谱归零：脱战→再演奏边沿调用。客户端音频从头起播，
     * 乐谱同步归零保证两端 0 点对齐（否则音频从头、乐谱续播造成错位）。
     */
    public void restart() {
        progressF = 0.0F;
        musicProgressTick = 0;
        currentSegmentIndex = 0;
    }

    // ================= 同步用 =================

    public int getMusicProgressTick() {
        return musicProgressTick;
    }

    public int getTotalDuration() {
        return totalDuration;
    }

    public BossPhase getCurrentPhase() {
        return currentPhase;
    }

    public boolean consumeAccent() {
        boolean a = accentThisTick;
        accentThisTick = false;
        return a;
    }

    public boolean consumeSegmentChanged() {
        boolean s = segmentChanged;
        segmentChanged = false;
        return s;
    }

    public int getSyncInterval() {
        return Math.max(5, TunerCommonConfig.MUSIC_SYNC_INTERVAL.get());
    }

    /**
     * 【2026-08-18 第十三轮】分段表只读副本（同步给客户端HUD渲染真实分段颜色）。
     * 【第二十二轮】二阶段低谷段转换为铺垫段下发（客户端音乐条二阶段不再出现低谷色块），
     * 一阶段原样下发。
     */
    public List<Segment> getSegments() {
        if (!phase2) {
            return List.copyOf(segments);
        }
        List<Segment> out = new ArrayList<>(segments.size());
        for (Segment s : segments) {
            out.add(s.phase == BossPhase.VALLEY ? new Segment(BossPhase.BUILDUP, s.ticks) : s);
        }
        return out;
    }

    /** 【2026-08-18 第十三轮】重音tick表只读副本（同步给客户端HUD绘制重音刻度） */
    public List<Integer> getAccents() {
        return List.copyOf(accents);
    }
}
