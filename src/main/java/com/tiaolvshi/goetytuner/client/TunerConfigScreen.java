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
import com.tiaolvshi.goetytuner.focus.FocusClassificationConfig;
import com.tiaolvshi.goetytuner.focus.FocusPoolManager;
import com.tiaolvshi.goetytuner.focus.LLMClassifier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 「自动配置分类与评分」配置界面（客户端）。
 *
 * 输入大模型 API Key 与提示词 → 点击「开始评分」按钮 → 异步调用 LLM（OpenAI 兼容）→
 * 按每个聚晶的文本描述输出分类+评分 → 经宽松校验/修正后写回 focus_classification.json → 重新分类。
 * 完成后右下角弹出 Toast 提示。
 *
 * 【0.0.5】提示词框改为**多行**并**预填当前生效的提示词**：
 * 已自定义则显示自定义内容，否则直接显示标准模板 —— 用户可在标准提示词基础上修改；
 * 清空则恢复默认；若内容与标准模板完全一致则视为未自定义，不写入配置文件。
 *
 * 【0.0.5】状态文本改用 {@link Component} 字段 + {@code drawWordWrap} 自动换行：
 * 失败信息现在会带上目标 URL / 模型名 / 排查提示，长度可能超过一行。
 *
 * 注意：LLM 请求仅在配置界面操作时发起，不进入正常游戏流程；未填 API Key 时按钮直接返回，不请求。
 */
public class TunerConfigScreen extends Screen {

    private final Screen parent;
    private EditBox apiKeyBox;
    private MultiLineEditBox promptBox;
    /** 【0.0.14】聚晶黑名单输入框（对应 common 配置 `focus.blacklist`） */
    private EditBox blacklistBox;
    private Button runButton;
    /** 状态行（null = 不显示）；直接存 Component，避免每帧 getString() */
    private Component status;

    public TunerConfigScreen(Screen parent) {
        super(Component.translatable("config.goetytuner.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        FocusClassificationConfig cfg = FocusPoolManager.classification();
        int cx = this.width / 2;

        // 1. API Key 输入框
        apiKeyBox = new EditBox(this.font, cx - 150, 34, 300, 18,
                Component.translatable("config.goetytuner.classify.apikey"));
        apiKeyBox.setMaxLength(256);
        apiKeyBox.setValue(cfg.getApiKey() == null ? "" : cfg.getApiKey());
        apiKeyBox.setHint(Component.translatable("config.goetytuner.classify.apikey.hint"));
        this.addRenderableWidget(apiKeyBox);

        // 2. 提示词输入框（多行，预填当前生效的提示词）
        //    构造器签名经 javap 字节码核实：(Font, x, y, width, height, placeholder, message)
        promptBox = new MultiLineEditBox(this.font, cx - 150, 66, 300, 58,
                Component.translatable("config.goetytuner.classify.prompt.hint"),
                Component.translatable("config.goetytuner.classify.prompt"));
        // 沿用旧 EditBox 的 4000 字符上限，避免依赖 MultiLineEditBox 的默认上限
        // （标准提示词约 500 字符；EditBox 默认仅 32，这里显式设定以防被截断）
        promptBox.setCharacterLimit(4000);
        String customPrompt = cfg.getPromptTextOrDefault();
        promptBox.setValue(customPrompt == null || customPrompt.isEmpty()
                ? LLMClassifier.PROMPT_TEMPLATE
                : customPrompt);
        this.addRenderableWidget(promptBox);

        // 3. 【0.0.14】聚晶黑名单输入框
        //    需求背景：`focus.blacklist` 是 common 配置，而本界面**顶替了 Forge 默认的 toml 编辑器**，
        //    此前该键在游戏内完全没有入口、只能手改文件。这里补一个输入框。
        //    格式与 toml 完全一致（英文逗号分隔），失焦/关屏时统一保存并热刷新。
        blacklistBox = new EditBox(this.font, cx - 150, 138, 300, 18,
                Component.translatable("config.goetytuner.blacklist"));
        blacklistBox.setMaxLength(1024);
        blacklistBox.setValue(TunerCommonConfig.FOCUS_BLACKLIST.get());
        blacklistBox.setHint(Component.translatable("config.goetytuner.blacklist.hint"));
        this.addRenderableWidget(blacklistBox);

        // 4. 开始评分 按钮
        runButton = Button.builder(Component.translatable("config.goetytuner.classify.button"),
                        b -> runClassify())
                .bounds(cx - 150, 164, 300, 20)
                .build();
        this.addRenderableWidget(runButton);

        // 5. 完成 / 返回（同时保存黑名单）
        this.addRenderableWidget(Button.builder(Component.translatable("config.goetytuner.done"),
                        b -> onClose())
                .bounds(cx - 150, 188, 300, 20)
                .build());
    }

    /**
     * 【0.0.14】把界面上的黑名单写回 common 配置，并让改动**立即生效**。
     *
     * <p>生效路径：`FOCUS_BLACKLIST.set(...)` 改的是 Forge 的内存值（`getBlacklist()` 以 raw 字符串
     * 为缓存键，值一变缓存自动失效）⇒ `draw()/drawUniform()` 的下一次抽取就会过滤掉新拉黑的聚晶。
     * 再调 {@link FocusPoolManager#refreshBlacklist()} 重建静态池，使**取消拉黑**也能立刻生效
     * （否则要重启才会回到池里）。
     *
     * <p>⚠️ 仅当客户端与这份 common 配置同进程时才有意义（单机/局域网主机的集成服务器就是如此）；
     * 连别人的服务器时改的是**本地**那份 toml，服务器侧不受影响。
     */
    private void applyBlacklist() {
        if (blacklistBox == null) {
            return;
        }
        String value = blacklistBox.getValue();
        value = value == null ? "" : value.trim();
        String old = TunerCommonConfig.FOCUS_BLACKLIST.get();
        if (value.equals(old)) {
            return; // 没改就不动（避免无谓的重新扫描）
        }
        TunerCommonConfig.FOCUS_BLACKLIST.set(value);
        TunerCommonConfig.FOCUS_BLACKLIST.save();
        FocusPoolManager.refreshBlacklist();
        GoetyTuner.LOGGER.info("[Tuner] focus.blacklist updated from config screen: '{}' -> '{}'", old, value);
        status = Component.translatable("config.goetytuner.blacklist.saved",
                FocusPoolManager.allEntries().size());
    }

    private void runClassify() {
        applyBlacklist(); // 【0.0.14】顺带保存黑名单（用户可能只改了它就来点这个按钮）
        String key = apiKeyBox.getValue().trim();
        if (key.isEmpty()) {
            status = Component.translatable("config.goetytuner.classify.status.apikeymissing");
            return;
        }
        FocusClassificationConfig cfg = FocusPoolManager.classification();
        cfg.setApiKey(key);
        // 【0.0.5】提示词框预填了标准模板，故需区分"用户真改过"与"原样未动"：
        // 与标准模板（trim 后）完全一致 → 视为未自定义，写空串。
        // 好处：① 配置文件不冗余存一份默认模板；② 以后模组更新默认提示词时能自动生效。
        String prompt = promptBox.getValue();
        if (prompt != null) {
            prompt = prompt.trim();
        }
        if (prompt == null || prompt.equals(LLMClassifier.PROMPT_TEMPLATE.trim())) {
            prompt = "";
        }
        cfg.setPrompt(prompt);
        cfg.save();

        status = Component.translatable("config.goetytuner.classify.status.running");
        runButton.active = false;

        LLMClassifier.runAsync(key, prompt,
                count -> {
                    status = Component.translatable("config.goetytuner.classify.status.done", count);
                    runButton.active = true;
                    showToast(Component.translatable("config.goetytuner.toast.title"),
                            Component.translatable("config.goetytuner.toast.done", count));
                },
                err -> {
                    status = Component.translatable("config.goetytuner.classify.status.failed", err);
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
        // 【0.0.14】关屏即保存黑名单（这是最自然的"我改完了"信号）
        applyBlacklist();
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
        int cx = this.width / 2;
        gfx.drawCenteredString(this.font, this.title, cx, 12, 0xFFFFFF);
        gfx.drawCenteredString(this.font,
                Component.translatable("config.goetytuner.classify.apikey"), cx, 22, 0xCCCCCC);
        gfx.drawCenteredString(this.font,
                Component.translatable("config.goetytuner.classify.prompt"), cx, 54, 0xCCCCCC);
        gfx.drawCenteredString(this.font,
                Component.translatable("config.goetytuner.blacklist.label"), cx, 126, 0xCCCCCC);
        if (status != null) {
            // 自动换行（宽度 300）：失败信息可能包含 URL/模型/排查提示，较长
            gfx.drawWordWrap(this.font, status, cx - 150, 214, 300, 0xAAAAFF);
        }
    }

    /** 仅用于本类内部日志，避免对 GoetyTuner 的额外 import 噪声 */
    private static final class GoetyTunerLogger {
        static void warn(String msg, Throwable t) {
            com.tiaolvshi.goetytuner.GoetyTuner.LOGGER.warn(msg, t);
        }
    }
}
