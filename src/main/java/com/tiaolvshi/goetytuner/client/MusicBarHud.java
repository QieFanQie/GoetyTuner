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
import net.minecraft.util.Mth;
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
 *   wrap），槽始终保持填满、丝滑连续。
 * - playing=false（无仇恨停演）时隐藏整个音乐条。
 * - 分段/重音数据由 SMusicSyncPacket 全量同步（服务端乐谱），不再用三等分近似。
 * - 【2026-08-19 第十七轮】进度改浮点连续推进（MusicStateClient.progressTickF），
 *   消除整数tick台阶造成的"蠕动"，实现逐帧平移。
 *
 * 【0.0.12】外观重做（用户反馈"太突兀"），四项一起改：
 * 1. **尺寸**：260×6（又长又薄）→ 见 {@link #BAR_WIDTH}/{@link #BAR_HEIGHT}，改短加厚。
 * 2. **柔顺渐变**：分段不再是硬边纯色块 —— 每行按"顶部提亮、底部压暗"逐行混色
 *    （{@link #fillSegment}），并在分段交界处叠 2px 半透明过渡缝（{@link #drawSeam}），
 *    消除"一刀切"的接缝。
 * 3. **边框更自然**：原来的单一硬矩形底（`0xAA000000`）换成三层由外向内渐深的柔和投影，
 *    且最外层四角留空以模拟圆角（{@link #drawSoftBackdrop}）。
 * 4. **阶段提示改为隐晦符号**：原先在条上方画"铺垫/高潮/低谷"**文字**，现改为**像素绘制**的
 *    `● ● ●`（高潮）/ `●`（铺垫）/ `- - - - - -`（低谷）。**之所以用像素画而不是 `⚪` 字符**：
 *    实测本客户端字体只有拉丁/希腊/西里尔等位图字形（`include/unifont.json` 的 providers 是**空数组**、
 *    jar 内无任何 ttf、也无 unicode 字形页），**U+26AA 没有字形，直接写会显示成空白方块**。
 *    像素画必定可见，且更符合"隐晦"的要求。
 *    ⇒ 由此 lang 里的 `info.goetytuner.music.buildup/climax/valley` **当前已无任何代码引用**
 *      （保留未删：它们是三个阶段的地道中/英名称，将来若想改回文字提示可直接复用）。
 *
 * 分段三色（第十九轮提亮；0.0.12 改为逐行混色以产生渐变）：
 * - 铺垫 buildup：亮蓝 / 高潮 climax：亮橙红 / 低谷 valley：亮紫
 * 重音标记（第二十轮分阶段样式；0.0.12 降低对比度使其不刺眼）：
 * - 铺垫：细线 / 高潮："中"字样式（贯通竖线+中间细线方框）/ 低谷：略加粗
 * 重音反馈：服务端粒子/声波+紫水晶音；客户端HUD亮黄描边闪烁（0.0.12 同样调淡）。
 *
 * 美术接入点：BACKGROUND_TEXTURE 替换为实际贴图即可。
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class MusicBarHud {

    private static final ResourceLocation BACKGROUND_TEXTURE =
            new ResourceLocation("goetytuner", "textures/gui/music_bar.png"); // TODO(美术)

    // 【0.0.12】尺寸重做：260×6 → 204×8。
    // 原值又长又薄、贴脸感强；改短 56px、加厚 2px 后更像一条"乐器滑轨"而不是一条线，
    // 重音刻度在 8px 高度上也有余量画出可辨认的形状（不至于被压成一根线）。
    private static final int BAR_WIDTH = 204;
    private static final int BAR_HEIGHT = 8;
    /** 条底距屏幕底部的距离（比原来的 64 略抬，避开物品栏上沿的拥挤区）。 */
    private static final int BAR_BOTTOM_MARGIN = 62;

    // ---- 分段主色（RGB，不含 alpha） ----
    private static final int C_BUILDUP = 0x5B9BE0;
    private static final int C_CLIMAX = 0xF26A4B;
    private static final int C_VALLEY = 0xB068E8;

    // ---- 重音闪烁状态（客户端本地检测进度越过重音tick，无需额外网络包） ----
    private static int flashTicks = 0;          // 剩余闪烁帧
    private static int flashBossId = -1;        // 上帧boss实体id（切换boss时重置基准）
    private static float lastProgressF = -1.0F; // 上帧浮点进度（用于越线检测）
    private static final int FLASH_MAX_TICKS = 8;

    private static int phaseRgb(BossPhase p) {
        return switch (p) {
            case BUILDUP -> C_BUILDUP;
            case CLIMAX -> C_CLIMAX;
            case VALLEY -> C_VALLEY;
        };
    }

    /** 把两个 RGB 按 t（0..1）线性混合。 */
    private static int lerpRgb(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int argb(int alpha, int rgb) {
        return (alpha << 24) | rgb;
    }

    @SubscribeEvent
    public static void onRender(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.BOSS_EVENT_PROGRESS.type()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        // 【0.0.5 性能】零分配早退：没有任何 Boss 在演奏时直接返回。
        if (!MusicStateClient.anyPlaying()) {
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
        int y = gfx.guiHeight() - BAR_BOTTOM_MARGIN;

        RenderSystem.enableBlend();
        drawSoftBackdrop(gfx, x, y);

        // 【第十七轮】浮点连续进度：wrap到 [0,total) 后用于所有渲染计算，逐帧平移不蠕动
        float progress = s.progressTickF % s.totalDuration;
        float ratio = progress / s.totalDuration;
        // 像素→tick 比例（每tick对应的像素宽）
        float pxPerTick = BAR_WIDTH / (float) s.totalDuration;

        if (!s.phase2) {
            // ============ 一阶段：内容固定，指针左→右 ============
            // 【0.0.12】内容按条宽裁剪：分段像素宽由浮点换算取整而来，总和可能比 BAR_WIDTH 多 1~2px，
            // 不裁剪会溢出到右侧边框上（1px 缝）。指针要探出条外，故画指针前先解除裁剪。
            gfx.enableScissor(x, y, x + BAR_WIDTH, y + BAR_HEIGHT);
            renderSegments(gfx, s, x, y, 0.0F, pxPerTick);
            gfx.disableScissor();
            // 指针：3px 宽、上下各探出 3px，改半透明白以免过于刺眼
            int pointerX = x + Math.round(BAR_WIDTH * ratio);
            gfx.fill(pointerX - 1, y - 3, pointerX + 2, y + BAR_HEIGHT + 3, 0xE8FFFFFF);
        } else {
            // ============ 二阶段：条带坠落（第十六轮：判定线对齐当前播放位置） ============
            gfx.fill(x - 1, y - 3, x + 2, y + BAR_HEIGHT + 3, 0xE8FFFFFF); // 判定线（最左）
            gfx.enableScissor(x, y - 1, x + BAR_WIDTH, y + BAR_HEIGHT + 1);
            renderStripAtAnchor(gfx, s, x, y, progress);
            gfx.disableScissor();
        }

        // 重音瞬间闪烁：亮黄描边渐隐（8帧）——0.0.12 调淡并贴合新边框尺寸
        if (flashTicks > 0) {
            int alpha = (int) (0x88 * flashTicks / (float) FLASH_MAX_TICKS);
            int c = (alpha << 24) | 0xFFE070;
            gfx.fill(x - 3, y - 3, x + BAR_WIDTH + 3, y - 2, c);
            gfx.fill(x - 3, y + BAR_HEIGHT + 2, x + BAR_WIDTH + 3, y + BAR_HEIGHT + 3, c);
            gfx.fill(x - 3, y - 2, x - 2, y + BAR_HEIGHT + 2, c);
            gfx.fill(x + BAR_WIDTH + 2, y - 2, x + BAR_WIDTH + 3, y + BAR_HEIGHT + 2, c);
            flashTicks--;
        }

        // 【0.0.12】阶段提示：像素符号，替代原先的文字
        drawPhaseIndicator(gfx, s.phase, x, y - 11);
        RenderSystem.disableBlend();
    }

    /**
     * 【0.0.12】柔和投影底：三层由外向内渐深 + 最外层四角留空（模拟圆角）。
     *
     * <p>原来是一个 2px 内边距的纯 `0xAA000000` 硬矩形，边缘生硬、和游戏 UI 格格不入。
     * 现在最外一圈只有 ~25% 黑、往里逐层加深，视觉上像一层柔和的阴影而不是一个黑框；
     * 四角不画最外层像素，在 1px 尺度下就能读作"圆角"。
     */
    private static void drawSoftBackdrop(GuiGraphics gfx, int x, int y) {
        int x0 = x - 3, y0 = y - 3, x1 = x + BAR_WIDTH + 3, y1 = y + BAR_HEIGHT + 3;
        // 最外层（~25% 黑）：四边各 1px，四角留空
        gfx.fill(x0 + 1, y0, x1 - 1, y0 + 1, 0x40000000);
        gfx.fill(x0 + 1, y1 - 1, x1 - 1, y1, 0x40000000);
        gfx.fill(x0, y0 + 1, x0 + 1, y1 - 1, 0x40000000);
        gfx.fill(x1 - 1, y0 + 1, x1, y1 - 1, 0x40000000);
        // 中间层（~45% 黑）
        gfx.fill(x - 2, y - 2, x + BAR_WIDTH + 2, y + BAR_HEIGHT + 2, 0x73000000);
        // 最内层（~65% 黑）：紧贴内容，让分段颜色更稳、不与背景糊在一起
        gfx.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, 0xA6000000);
    }

    /**
     * 【0.0.12】画一段渐变条：逐行把主色往"顶部提亮 / 底部压暗"方向混色，
     * 使每个分段自身有竖向明暗过渡（原实现是整块纯色，扁平且边缘发死）。
     *
     * @param x0 左边界（含）　@param x1 右边界（不含）
     */
    private static void fillSegment(GuiGraphics gfx, int x0, int x1, int y, int rgb) {
        if (x1 <= x0) {
            return;
        }
        for (int r = 0; r < BAR_HEIGHT; r++) {
            float t = (r + 0.5F) / BAR_HEIGHT; // 0=顶 1=底
            int rowRgb = t < 0.5F
                    ? lerpRgb(rgb, 0xFFFFFF, (0.5F - t) * 0.20F)  // 上半：向白提亮
                    : lerpRgb(rgb, 0x000000, (t - 0.5F) * 0.34F); // 下半：向黑压暗
            gfx.fill(x0, y + r, x1, y + r + 1, argb(0xFF, rowRgb));
        }
    }

    /**
     * 【0.0.12】分段交界处的过渡缝：叠一条 2px 的半透明"上一段颜色"，
     * 把生硬的直角接缝揉开，读数上仍是分段、观感上不再"一刀切"。
     */
    private static void drawSeam(GuiGraphics gfx, int boundaryX, int y, int leftRgb) {
        gfx.fill(boundaryX - 1, y, boundaryX + 1, y + BAR_HEIGHT, argb(0x4D, leftRgb));
    }

    /**
     * 画乐谱内容（分段渐变颜色 + 重音刻度）。
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
            fillSegment(gfx, sx, sx + buildup, y, C_BUILDUP);
            fillSegment(gfx, sx + buildup, sx + buildup + climax, y, C_CLIMAX);
            fillSegment(gfx, sx + buildup + climax, sx + buildup + climax + valley, y, C_VALLEY);
            drawSeam(gfx, sx + buildup, y, C_BUILDUP);
            drawSeam(gfx, sx + buildup + climax, y, C_CLIMAX);
            return;
        }
        // 真实乐谱分段
        int cursor = 0; // 当前分段在条上的起始偏移（像素，未减去scroll）
        int prevBoundary = Integer.MIN_VALUE;
        int prevRgb = 0;
        for (MusicStateClient.Segment seg : s.segments) {
            int segPx = Math.max(1, (int) (seg.ticks * pxPerTick));
            int segStart = x + cursor - (int) scrollX;
            int segEnd = segStart + segPx;
            int rgb = phaseRgb(seg.phase);
            fillSegment(gfx, segStart, segEnd, y, rgb);
            // 与上一段的接缝（只画落在可视区内的）
            if (prevBoundary != Integer.MIN_VALUE && prevBoundary > x - 2 && prevBoundary < x + BAR_WIDTH + 2) {
                drawSeam(gfx, prevBoundary, y, prevRgb);
            }
            prevBoundary = segEnd;
            prevRgb = rgb;
            cursor += segPx;
        }
        // 重音刻度（第二十轮分阶段样式，随内容滚动）
        if (s.accents != null) {
            // 【0.0.5】阶段已在同步时预计算（accentPhases 与 accents 同序同长），每帧只做索引读取
            BossPhase[] pre = s.accentPhases;
            for (int i = 0; i < s.accents.size(); i++) {
                int accentTick = s.accents.get(i);
                if (accentTick < 0 || accentTick >= s.totalDuration) {
                    continue;
                }
                float ax = x + accentTick * pxPerTick - scrollX;
                if (ax >= x - 6 && ax <= x + BAR_WIDTH + 6) { // 可视区内才画（scissor也兜底）
                    drawAccentMark(gfx, ax, y,
                            pre != null && i < pre.length ? pre[i] : s.phaseAtTick(accentTick), 1.0F);
                }
            }
        }
    }

    /**
     * 【0.0.13】重音刻度：三种阶段**都是"小长条"**，竖向居中、整体更细更整齐。
     *
     * <p>0.0.12 之前高潮用的是贯通竖线 + 中间空心方框拼成的"中"字，在一个 8px 高的细条上
     * 又粗又花、还占了整条高度，与另外两种细线完全不是一套语言。现在统一为居中的小长条，
     * 只用「宽 × 高 × 亮度」三个维度区分强度（高潮最粗最高最亮），观感干净得多。
     *
     * <p>**【滚动不平滑的根因与修法】**：`fill()` 只能落在整数像素上，而重音刻度是 1~2px 宽的细条，
     * 位置取整后会**逐像素跳动**（相邻帧要么不动、要么猛跳 1px），滚动时看着就是一顿一顿的。
     * 这里改用**亚像素覆盖**：把刻度的小数部分按比例分摊到相邻两列（如 x=10.3 ⇒ 第 10 列画 70% 透明度、
     * 第 11 列画 30%），视线感受到的亮度重心就随浮点位置连续移动，滚动自然平滑。
     *
     * @param fx 刻度的浮点横坐标（**不要先取整**）
     * @param alphaScale 整体透明度系数（0~1，用于统一压淡）
     */
    private static void drawAccentMark(GuiGraphics gfx, float fx, int y, BossPhase phase, float alphaScale) {
        int width;
        int height;
        int baseAlpha;
        switch (phase) {
            case CLIMAX -> { width = 2; height = 6; baseAlpha = 0xCC; } // 高潮：最粗最高最亮
            case VALLEY -> { width = 1; height = 6; baseAlpha = 0xB4; } // 低谷：高而细
            default -> { width = 1; height = 4; baseAlpha = 0x99; }     // 铺垫/未知：短细
        }
        int top = y + (BAR_HEIGHT - height) / 2; // 竖向居中 → 读作"轨道上的刻度"而非整条分隔线
        int i0 = Mth.floor(fx);
        float frac = fx - i0;
        int a0 = Math.round(baseAlpha * alphaScale * (1.0F - frac));
        int a1 = Math.round(baseAlpha * alphaScale * frac);
        if (a0 > 0) {
            gfx.fill(i0, top, i0 + width, top + height, argb(a0, 0xFFFFFF));
        }
        if (a1 > 0) {
            gfx.fill(i0 + 1, top, i0 + 1 + width, top + height, argb(a1, 0xFFFFFF));
        }
    }

    /**
     * 【0.0.12】阶段提示：像素符号（隐晦），替代原先的"铺垫/高潮/低谷"文字。
     *
     * <p>高潮 = 三颗圆点、铺垫 = 一颗圆点、低谷 = 一排短横线。全部由 {@code fill} 拼出，
     * **不使用字体**——本客户端字体没有 `⚪`(U+26AA) 的字形，直接写会变成空白方块。
     * 颜色取各阶段主色再压到低对比度的近白色调，第一眼不抢戏、盯一眼能读出阶段。
     */
    private static void drawPhaseIndicator(GuiGraphics gfx, BossPhase phase, int x, int y) {
        int rgb = lerpRgb(phaseRgb(phase), 0xFFFFFF, 0.55F); // 淡化的阶段色
        final int color = argb(0xB0, rgb);
        int centerX = x + BAR_WIDTH / 2;
        if (phase == BossPhase.VALLEY) {
            // ------ ：6 段短横，4px 宽 2px 高，间距 2px
            final int dashW = 4, gap = 2, dashes = 6;
            int totalW = dashes * dashW + (dashes - 1) * gap;
            int sx = centerX - totalW / 2;
            for (int i = 0; i < dashes; i++) {
                int dx = sx + i * (dashW + gap);
                gfx.fill(dx, y + 1, dx + dashW, y + 3, color);
            }
            return;
        }
        int dots = (phase == BossPhase.CLIMAX) ? 3 : 1;
        final int dot = 4, gap = 3;
        int totalW = dots * dot + (dots - 1) * gap;
        int sx = centerX - totalW / 2;
        for (int i = 0; i < dots; i++) {
            drawDot(gfx, sx + i * (dot + gap), y, dot, color);
        }
    }

    /** 4×4 的圆点（四角留空，1px 尺度下读作圆形）。 */
    private static void drawDot(GuiGraphics gfx, int x, int y, int size, int color) {
        int s = size;
        gfx.fill(x + 1, y, x + s - 1, y + 1, color);              // 上边（两端内缩）
        gfx.fill(x, y + 1, x + s, y + s - 1, color);              // 中部整宽
        gfx.fill(x + 1, y + s - 1, x + s - 1, y + s, color);      // 下边（两端内缩）
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
                    int c0 = Math.max(segStart, clipL), c1 = Math.min(segEnd, clipR);
                    fillSegment(gfx, c0, c1, y, phaseRgb(seg.phase));
                    if (segEnd > clipL && segEnd < clipR) {
                        drawSeam(gfx, segEnd, y, phaseRgb(seg.phase));
                    }
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
                fillSegment(gfx, Math.max(sx, clipL), Math.min(sx + bPx, clipR), y, C_BUILDUP);
                fillSegment(gfx, Math.max(sx + bPx, clipL), Math.min(sx + bPx + cPx, clipR), y, C_CLIMAX);
                fillSegment(gfx, Math.max(sx + bPx + cPx, clipL), Math.min(sx + bPx + cPx + vPx, clipR), y, C_VALLEY);
            }
            // 重音刻度（第二十轮分阶段样式，随内容一起坠落）
            if (s.accents != null) {
                // 【0.0.5】预计算索引：原先 phaseAtTick 在 wrap(-1/0/+1) 三层循环里对同一 tick 重算 3 遍
                BossPhase[] pre = s.accentPhases;
                for (int i = 0; i < s.accents.size(); i++) {
                    int accentTick = s.accents.get(i);
                    if (accentTick < 0 || accentTick >= s.totalDuration) {
                        continue;
                    }
                    float ax = x + basePx + accentTick * pxPerTick;
                    if (ax >= clipL - 2 && ax <= clipR + 2) {
                        drawAccentMark(gfx, ax, y,
                                pre != null && i < pre.length ? pre[i] : s.phaseAtTick(accentTick), 1.0F);
                    }
                }
            }
        }
    }
}
