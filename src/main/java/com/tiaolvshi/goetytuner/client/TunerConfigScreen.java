/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.client;

import com.tiaolvshi.goetytuner.focus.FocusClassificationConfig;
import com.tiaolvshi.goetytuner.focus.FocusPoolManager;
import com.tiaolvshi.goetytuner.focus.LLMClassifier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 「自动配置分类与评分」配置界面（客户端）。
 *
 * 输入大模型 API Key 与自定义提示词 → 点击「开始评分」按钮 → 异步调用 LLM（OpenAI 兼容）→
 * 按每个聚晶的文本描述输出分类+评分 → 经宽松校验/修正后写回 focus_classification.json → 重新分类。
 * 完成后右下角弹出 Toast 提示。
 *
 * 注意：LLM 请求仅在配置界面操作时发起，不进入正常游戏流程；未填 API Key 时按钮直接返回，不请求。
 */
public class TunerConfigScreen extends Screen {

    private final Screen parent;
    private EditBox apiKeyBox;
    private EditBox promptBox;
    private Button runButton;
    private String status = "";

    public TunerConfigScreen(Screen parent) {
        super(Component.translatable("config.goetytuner.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        FocusClassificationConfig cfg = FocusPoolManager.classification();
        int cx = this.width / 2;

        // 1. API Key 输入框
        apiKeyBox = new EditBox(this.font, cx - 150, 56, 300, 18,
                Component.translatable("config.goetytuner.classify.apikey"));
        apiKeyBox.setMaxLength(256);
        apiKeyBox.setValue(cfg.getApiKey() == null ? "" : cfg.getApiKey());
        apiKeyBox.setHint(Component.translatable("config.goetytuner.classify.apikey.hint"));
        this.addRenderableWidget(apiKeyBox);

        // 2. 自定义提示词输入框（留空=使用默认模板）
        promptBox = new EditBox(this.font, cx - 150, 92, 300, 18,
                Component.translatable("config.goetytuner.classify.prompt"));
        promptBox.setMaxLength(4000);
        promptBox.setValue(cfg.getPromptTextOrDefault());
        promptBox.setHint(Component.translatable("config.goetytuner.classify.prompt.hint"));
        this.addRenderableWidget(promptBox);

        // 3. 开始评分 按钮（触发 LLM 评分事件）
        runButton = Button.builder(Component.translatable("config.goetytuner.classify.button"),
                        b -> runClassify())
                .bounds(cx - 150, 122, 300, 20)
                .build();
        this.addRenderableWidget(runButton);

        // 4. 完成 / 返回
        this.addRenderableWidget(Button.builder(Component.translatable("config.goetytuner.done"),
                        b -> onClose())
                .bounds(cx - 150, 152, 300, 20)
                .build());
    }

    private void runClassify() {
        String key = apiKeyBox.getValue().trim();
        if (key.isEmpty()) {
            status = Component.translatable("config.goetytuner.classify.status.apikeymissing").getString();
            return;
        }
        FocusClassificationConfig cfg = FocusPoolManager.classification();
        cfg.setApiKey(key);
        // 仅当用户真正编辑过提示词时才保存（避免无谓写入默认模板）
        String prompt = promptBox.getValue();
        cfg.setPrompt(prompt == null ? "" : prompt.trim());
        cfg.save();

        status = Component.translatable("config.goetytuner.classify.status.running").getString();
        runButton.active = false;

        LLMClassifier.runAsync(key, prompt,
                count -> {
                    status = Component.translatable("config.goetytuner.classify.status.done", count).getString();
                    runButton.active = true;
                    showToast(Component.translatable("config.goetytuner.toast.title"),
                            Component.translatable("config.goetytuner.toast.done", count));
                },
                err -> {
                    status = Component.translatable("config.goetytuner.classify.status.failed", err).getString();
                    runButton.active = true;
                    showToast(Component.translatable("config.goetytuner.toast.title"),
                            Component.translatable("config.goetytuner.toast.failed"));
                });
    }

    private void showToast(Component title, Component desc) {
        try {
            net.minecraft.client.Minecraft.getInstance().getToasts().addToast(new TunerToast(title, desc));
        } catch (Throwable t) {
            GoetyTunerLogger.warn("Failed to show toast", t);
        }
    }

    @Override
    public void onClose() {
        if (parent != null) {
            net.minecraft.client.Minecraft.getInstance().setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        gfx.drawCenteredString(this.font, this.title, this.width / 2, 24, 0xFFFFFF);
        gfx.drawCenteredString(this.font,
                Component.translatable("config.goetytuner.classify.apikey"), this.width / 2, 44, 0xCCCCCC);
        gfx.drawCenteredString(this.font,
                Component.translatable("config.goetytuner.classify.prompt"), this.width / 2, 80, 0xCCCCCC);
        if (!status.isEmpty()) {
            gfx.drawCenteredString(this.font, status, this.width / 2, 184, 0xAAAAFF);
        }
    }

    /** 仅用于本类内部日志，避免对 GoetyTuner 的额外 import 噪声（保持一致性仍引用主类日志亦可） */
    private static final class GoetyTunerLogger {
        static void warn(String msg, Throwable t) {
            com.tiaolvshi.goetytuner.GoetyTuner.LOGGER.warn(msg, t);
        }
    }
}
