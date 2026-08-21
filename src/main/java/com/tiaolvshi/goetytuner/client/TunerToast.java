/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;

/**
 * 【v0.0.3】轻量自定义提示（Toast）：用于 LLM 评分完成/失败后的右下角消息提示。
 * 仅实现 {@link Toast#render} 即可，getToken 取默认。
 */
public class TunerToast implements Toast {

    private static final int DISPLAY_TIME_MS = 5000;

    private final Component title;
    private final Component description;
    private long startTime = -1;

    public TunerToast(Component title, Component description) {
        this.title = title;
        this.description = description;
    }

    @Override
    public Visibility render(GuiGraphics gfx, ToastComponent toastComponent, long timeSinceStart) {
        if (startTime < 0) {
            startTime = timeSinceStart;
        }
        // 标准 toast 尺寸（与 Minecraft 一致）
        int width = 160;
        int height = 32;

        // 背景面板
        gfx.fill(0, 0, width, height, 0xC0101018);
        gfx.fill(0, 0, width, 2, 0xFF6A4CAF);

        int y = 7;
        if (title != null) {
            gfx.drawString(net.minecraft.client.Minecraft.getInstance().font, title, 8, y, 0xFFFFFF, false);
            y += 12;
        }
        if (description != null) {
            gfx.drawString(net.minecraft.client.Minecraft.getInstance().font, description, 8, y, 0xCCCCCC, false);
        }

        return (timeSinceStart - startTime) >= DISPLAY_TIME_MS ? Visibility.HIDE : Visibility.SHOW;
    }
}
