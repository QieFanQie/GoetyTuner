/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tiaolvshi.goetytuner.entity.BossPhase;
import com.tiaolvshi.goetytuner.entity.TunerBoss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 节奏条HUD（客户端渲染；阶段切换由服务端权威同步）。
 *
 * 【2026-08-19 第十六轮】二阶段渲染再修正：
 * - 一阶段：内容固定（真实分段颜色+重音刻度），指针从左往右匀速滑动 —— "演奏进行中"
 * - 二阶段：条带坠落（以当前播放进度为锚点）——判定线（最左）永远对齐"正在播放"的内容：
 *   左侧是刚坠落的过去、右侧是即将坠落的未来；乐谱周期循环补位（±total 各画一份处理
 *   wrap），槽始终保持填满、丝滑连续。此前的实现判定线对应 progress/2 位置，
 *   与服务端实际音乐状态（phase）对不上，已修正为锚定当前 tick。
 * - playing=false（无仇恨停演）时隐藏整个音乐条。
 * - 分段/重音数据由 SMusicSyncPacket 全量同步（服务端乐谱），不再用三等分近似。
 * - 【2026-08-19 第十七轮】进度改浮点连续推进（MusicStateClient.progressTickF），
 *   消除整数tick台阶造成的"蠕动"，实现逐帧平移。
 *
 * 分段三色（第十九轮提亮）：
 * - 铺垫 buildup：亮蓝
 * - 高潮 climax：亮橙红
 * - 低谷 valley：亮紫
 * 重音标记（第二十轮分阶段样式）：
 * - 铺垫：普通白色细线
 * - 高潮："中"字样式（贯通竖线+中间细线方框）
 * - 低谷：略加粗白线
 * 重音反馈（第十九轮）：服务端粒子冲击环+音符爆发+紫水晶音；客户端HUD亮黄描边闪烁。
 *
 * 美术接入点：BACKGROUND_TEXTURE 替换为实际贴图即可。
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class MusicBarHud {

    private static final ResourceLocation BACKGROUND_TEXTURE =
            new ResourceLocation("goetytuner", "textures/gui/music_bar.png"); // TODO(美术)

    // 【2026-08-19 第十九轮】样式调整：更扁更长（182x12 → 260x6）、配色提亮、指针加粗
    private static final int BAR_WIDTH = 260;
    private static final int BAR_HEIGHT = 6;
    private static final int ACCENT_U = 200; // 重音刻度贴图u（示例）

    // ---- 重音闪烁状态（第十九轮：客户端本地检测进度越过重音tick，无需额外网络包） ----
    private static int flashTicks = 0;          // 剩余闪烁帧
    private static int flashBossId = -1;        // 上帧boss实体id（切换boss时重置基准）
    private static float lastProgressF = -1.0F; // 上帧浮点进度（用于越线检测）
    private static final int FLASH_MAX_TICKS = 8;

    @SubscribeEvent
    public static void onRender(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.BOSS_EVENT_PROGRESS.type()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        // 找到可见的调律师
        TunerBoss boss = null;
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof TunerBoss t && t.isAlive()) {
                boss = t;
                break;
            }
        }
        if (boss == null) {
            return;
        }
        MusicStateClient.State s = MusicStateClient.smoothed(boss.getId());
        if (s == null || s.totalDuration <= 0 || !s.playing) {
            return; // 非演奏中（无仇恨）：不显示音乐条
        }
        updateAccentFlash(boss.getId(), s);
        renderBar(event.getGuiGraphics(), mc, s);
    }

    /**
     * 【第十九轮】重音越线检测：当前帧进度与上帧进度之间若夹着重音tick，
     * 触发 HUD 闪烁（亮黄描边渐隐）。进度回绕（翻页）时按两段区间检查。
     */
    private static void updateAccentFlash(int bossId, MusicStateClient.State s) {
        float total = s.totalDuration;
        float progress = s.progressTickF % total;
        if (flashBossId != bossId) {
            flashBossId = bossId;
            lastProgressF = progress;
            return;
        }
        float prev = lastProgressF;
        lastProgressF = progress;
        if (s.accents == null || s.accents.isEmpty()) {
            return;
        }
        boolean hit = false;
        if (prev <= progress) {
            for (int t : s.accents) {
                if (t > prev && t <= progress) { hit = true; break; }
            }
        } else { // 翻页回绕：检查 (prev, total) 和 [0, progress] 两段
            for (int t : s.accents) {
                if (t > prev || t <= progress) { hit = true; break; }
            }
        }
        if (hit) {
            flashTicks = FLASH_MAX_TICKS;
        }
    }

    private static void renderBar(GuiGraphics gfx, Minecraft mc, MusicStateClient.State s) {
        int x = (gfx.guiWidth() - BAR_WIDTH) / 2;
        int y = gfx.guiHeight() - 64;

        RenderSystem.enableBlend();
        // 背景
        gfx.fill(x - 2, y - 2, x + BAR_WIDTH + 2, y + BAR_HEIGHT + 2, 0xAA000000);

        // 【第十七轮】浮点连续进度：wrap到 [0,total) 后用于所有渲染计算，逐帧平移不蠕动
        float progress = s.progressTickF % s.totalDuration;
        float ratio = progress / s.totalDuration;
        // 像素→tick 比例（每tick对应的像素宽）
        float pxPerTick = BAR_WIDTH / (float) s.totalDuration;

        if (!s.phase2) {
            // ============ 一阶段：内容固定，指针左→右 ============
            renderSegments(gfx, s, x, y, 0.0F, pxPerTick);
            int pointerX = x + Math.round(BAR_WIDTH * ratio);
            // 【第十九轮】指针加粗：5px宽，上下各探出4px
            gfx.fill(pointerX - 2, y - 4, pointerX + 2, y + BAR_HEIGHT + 4, 0xFFFFFFFF);
        } else {
            // ============ 二阶段：条带坠落（第十六轮：判定线对齐当前播放位置） ============
            // 【第十九轮】判定线加粗：5px宽
            gfx.fill(x - 2, y - 4, x + 2, y + BAR_HEIGHT + 4, 0xFFFFFFFF); // 判定线（最左）
            gfx.enableScissor(x, y - 1, x + BAR_WIDTH, y + BAR_HEIGHT + 1);
            renderStripAtAnchor(gfx, s, x, y, progress);
            gfx.disableScissor();
        }

        // 【第十九轮】重音瞬间闪烁：亮黄描边渐隐（8帧）
        if (flashTicks > 0) {
            int alpha = (int) (0xC0 * flashTicks / (float) FLASH_MAX_TICKS);
            int c = (alpha << 24) | 0xFFE070;
            gfx.fill(x - 3, y - 3, x + BAR_WIDTH + 3, y - 2, c);
            gfx.fill(x - 3, y + BAR_HEIGHT + 2, x + BAR_WIDTH + 3, y + BAR_HEIGHT + 3, c);
            gfx.fill(x - 3, y - 2, x - 2, y + BAR_HEIGHT + 2, c);
            gfx.fill(x + BAR_WIDTH + 2, y - 2, x + BAR_WIDTH + 3, y + BAR_HEIGHT + 2, c);
            flashTicks--;
        }

        // 当前阶段文字
        String label = switch (s.phase) {
            case BUILDUP -> "§7" + net.minecraft.client.resources.language.I18n.get("info.goetytuner.music.buildup");
            case CLIMAX -> "§c" + net.minecraft.client.resources.language.I18n.get("info.goetytuner.music.climax");
            case VALLEY -> "§5" + net.minecraft.client.resources.language.I18n.get("info.goetytuner.music.valley");
        };
        gfx.drawString(mc.font, label, x, y - 13, 0xFFFFFF);
        RenderSystem.disableBlend();
    }

    /**
     * 画乐谱内容（分段颜色 + 重音刻度）。
     *
     * @param scrollX  内容整体左移像素量（一阶段=0；二阶段=progress/total*BAR_WIDTH）
     * @param pxPerTick 每tick像素宽
     */
    private static void renderSegments(GuiGraphics gfx, MusicStateClient.State s, int x, int y,
                                       float scrollX, float pxPerTick) {
        if (s.segments == null || s.segments.isEmpty()) {
            // 健壮回退：三等分近似（按传入 pxPerTick 缩放；二阶段传入 2×槽宽比例，条画 2×宽）
            int buildup = (int) (s.totalDuration * 0.55F * pxPerTick);
            int climax = (int) (s.totalDuration * 0.27F * pxPerTick);
            int valley = Math.max(1, (int) (s.totalDuration * pxPerTick) - buildup - climax);
            int sx = x - (int) scrollX;
            gfx.fill(sx, y, sx + buildup, y + BAR_HEIGHT, 0xFF5B9BE0);
            gfx.fill(sx + buildup, y, sx + buildup + climax, y + BAR_HEIGHT, 0xFFF26A4B);
            gfx.fill(sx + buildup + climax, y, sx + buildup + climax + valley, y + BAR_HEIGHT, 0xFFB068E8);
            return;
        }
        // 真实乐谱分段
        int cursor = 0; // 当前分段在条上的起始偏移（像素，未减去scroll）
        for (MusicStateClient.Segment seg : s.segments) {
            int segPx = Math.max(1, (int) (seg.ticks * pxPerTick));
            int segStart = x + cursor - (int) scrollX;
            int segEnd = segStart + segPx;
            int color = switch (seg.phase) {
                case BUILDUP -> 0xFF5B9BE0;
                case CLIMAX -> 0xFFF26A4B;
                case VALLEY -> 0xFFB068E8;
            };
            gfx.fill(segStart, y, segEnd, y + BAR_HEIGHT, color);
            cursor += segPx;
        }
        // 重音刻度（第二十轮分阶段样式，随内容滚动）
        if (s.accents != null) {
            for (int accentTick : s.accents) {
                if (accentTick < 0 || accentTick >= s.totalDuration) {
                    continue;
                }
                int ax = x + (int) (accentTick * pxPerTick) - (int) scrollX;
                if (ax >= x - 4 && ax <= x + BAR_WIDTH + 4) { // 可视区内才画（scissor也兜底）
                    drawAccentMark(gfx, ax, y, phaseAtTick(s, accentTick));
                }
            }
        }
    }

    /**
     * 【第二十轮】重音tick所在分段的阶段（无分段数据时返回null=普通样式）。
     */
    private static BossPhase phaseAtTick(MusicStateClient.State s, int tick) {
        if (s.segments == null || s.segments.isEmpty()) {
            return null;
        }
        int acc = 0;
        for (MusicStateClient.Segment seg : s.segments) {
            acc += seg.ticks;
            if (tick < acc) {
                return seg.phase;
            }
        }
        return null;
    }

    /**
     * 【第二十轮】重音刻度分阶段样式：
     * - 铺垫/未知：普通细线（1px）
     * - 高潮："中"字——贯通竖线 + 中间细线空心方框（视觉如汉字"中"）
     * - 低谷：略加粗（3px）
     */
    private static void drawAccentMark(GuiGraphics gfx, int ax, int y, BossPhase phase) {
        if (phase == BossPhase.CLIMAX) {
            // "中"字：贯通竖线
            gfx.fill(ax, y, ax + 1, y + BAR_HEIGHT, 0xFFFFFFFF);
            // 中部细线方框（上/下/左/右四条边，1px描边）
            int bx0 = ax - 2, bx1 = ax + 3;          // 5px宽，竖线居中
            int by0 = y + 1, by1 = y + BAR_HEIGHT - 1; // 上下各留1px
            gfx.fill(bx0, by0, bx1, by0 + 1, 0xFFFFFFFF); // 上边
            gfx.fill(bx0, by1 - 1, bx1, by1, 0xFFFFFFFF); // 下边
            gfx.fill(bx0, by0, bx0 + 1, by1, 0xFFFFFFFF); // 左边
            gfx.fill(bx1 - 1, by0, bx1, by1, 0xFFFFFFFF); // 右边
        } else if (phase == BossPhase.VALLEY) {
            // 低谷：加粗3px
            gfx.fill(ax - 1, y, ax + 2, y + BAR_HEIGHT, 0xFFFFFFFF);
        } else {
            // 铺垫/未知：普通1px
            gfx.fill(ax, y, ax + 1, y + BAR_HEIGHT, 0xFFFFFFFF);
        }
    }

    /**
     * 二阶段条带坠落渲染（第十六轮）：以当前播放进度 progressTick 为锚点。
     * 判定线（条最左端）永远对齐"正在播放"的内容——左侧是刚坠落的过去、
     * 右侧是即将坠落的未来。乐谱周期循环（-total/0/+total 各画一份处理 wrap），
     * 槽始终保持填满。判定线处的分段颜色与服务端 music.getCurrentPhase() 一致。
     */
    private static void renderStripAtAnchor(GuiGraphics gfx, MusicStateClient.State s, int x, int y, float progress) {
        float pxPerTick = BAR_WIDTH / (float) s.totalDuration;
        float anchorPx = progress * pxPerTick;
        float totalPx = s.totalDuration * pxPerTick;
        boolean hasSegments = s.segments != null && !s.segments.isEmpty();

        // 画三份（wrap=-1/0/+1）覆盖"锚点±一整条"的窗口
        for (int wrap = -1; wrap <= 1; wrap++) {
            float basePx = wrap * totalPx - anchorPx; // 该份乐谱起点相对判定线的像素偏移
            int clipL = x - 4;
            int clipR = x + BAR_WIDTH + 4;
            if (hasSegments) {
                int cursorTick = 0;
                for (MusicStateClient.Segment seg : s.segments) {
                    int segPx = Math.max(1, Math.round(seg.ticks * pxPerTick));
                    int segStart = x + Math.round(basePx + cursorTick * pxPerTick);
                    int segEnd = segStart + segPx;
                    cursorTick += seg.ticks;
                    if (segEnd <= clipL || segStart >= clipR) {
                        continue; // 可视区外跳过
                    }
                    int color = switch (seg.phase) {
                        case BUILDUP -> 0xFF5B9BE0;
                        case CLIMAX -> 0xFFF26A4B;
                        case VALLEY -> 0xFFB068E8;
                    };
                    gfx.fill(Math.max(segStart, clipL), y, Math.min(segEnd, clipR), y + BAR_HEIGHT, color);
                }
            } else {
                // 健壮回退：三等分近似（同样以锚点画）
                int bTicks = (int) (s.totalDuration * 0.55F);
                int cTicks = (int) (s.totalDuration * 0.27F);
                int vTicks = Math.max(1, s.totalDuration - bTicks - cTicks);
                int sx = x + Math.round(basePx);
                int bPx = Math.round(bTicks * pxPerTick);
                int cPx = Math.round(cTicks * pxPerTick);
                int vPx = Math.max(1, Math.round(vTicks * pxPerTick));
                gfx.fill(Math.max(sx, clipL), y, Math.min(sx + bPx, clipR), y + BAR_HEIGHT, 0xFF5B9BE0);
                gfx.fill(Math.max(sx + bPx, clipL), y, Math.min(sx + bPx + cPx, clipR), y + BAR_HEIGHT, 0xFFF26A4B);
                gfx.fill(Math.max(sx + bPx + cPx, clipL), y, Math.min(sx + bPx + cPx + vPx, clipR), y + BAR_HEIGHT, 0xFFB068E8);
            }
            // 重音刻度（第二十轮分阶段样式，随内容一起坠落）
            if (s.accents != null) {
                for (int accentTick : s.accents) {
                    if (accentTick < 0 || accentTick >= s.totalDuration) {
                        continue;
                    }
                    int ax = x + Math.round(basePx + accentTick * pxPerTick);
                    if (ax >= clipL && ax <= clipR) {
                        drawAccentMark(gfx, ax, y, phaseAtTick(s, accentTick));
                    }
                }
            }
        }
    }
}
