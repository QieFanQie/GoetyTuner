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
 * 输入大模型API Key → 点击按钮 → 异步调用LLM（OpenAI兼容）→
 * 按每个聚晶的文本描述输出分类+评分 → 写回 focus_classification.json → 重新分类。
 *
 * 注意：LLM请求仅在配置界面操作时发起，不进入正常游戏流程。
 */
public class TunerConfigScreen extends Screen {

    private EditBox apiKeyBox;
    private Button runButton;
    private String status = "";

    public TunerConfigScreen() {
        super(Component.translatable("config.goetytuner.title"));
    }

    @Override
    protected void init() {
        FocusClassificationConfig cfg = FocusPoolManager.classification();

        apiKeyBox = new EditBox(this.font, this.width / 2 - 100, 60, 200, 18,
                Component.translatable("config.goetytuner.classify.apikey"));
        apiKeyBox.setMaxLength(256);
        apiKeyBox.setValue(cfg.getApiKey() == null ? "" : cfg.getApiKey());
        apiKeyBox.setHint(Component.translatable("config.goetytuner.classify.apikey"));
        this.addRenderableWidget(apiKeyBox);

        runButton = Button.builder(Component.translatable("config.goetytuner.classify.button"), b -> runClassify())
                .bounds(this.width / 2 - 100, 90, 200, 20)
                .build();
        this.addRenderableWidget(runButton);

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 - 100, 120, 200, 20)
                .build());
    }

    private void runClassify() {
        String key = apiKeyBox.getValue().trim();
        if (key.isEmpty()) {
            status = "API Key empty";
            return;
        }
        FocusClassificationConfig cfg = FocusPoolManager.classification();
        cfg.setApiKey(key);
        cfg.save();
        status = Component.translatable("config.goetytuner.classify.status.running").getString();
        runButton.active = false;
        LLMClassifier.runAsync(key,
                count -> status = Component.translatable("config.goetytuner.classify.status.done", count).getString(),
                err -> {
                    status = Component.translatable("config.goetytuner.classify.status.failed", err).getString();
                    runButton.active = true;
                });
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        gfx.drawCenteredString(this.font, this.title, this.width / 2, 40, 0xFFFFFF);
        if (!status.isEmpty()) {
            gfx.drawCenteredString(this.font, status, this.width / 2, 150, 0xAAAAFF);
        }
    }
}
