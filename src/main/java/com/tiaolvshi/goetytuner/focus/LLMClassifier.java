/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.focus;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.config.TunerCommonConfig;
import net.minecraft.client.resources.language.I18n;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * LLM 自动分类与评分。
 *
 * 原理：每个聚晶都有独立的文本描述（lang: item.<modid>.<focus>.info），
 * 让大模型模拟"玩家初次遇见该聚晶时的认识方式"，输出分类与评分。
 *
 * 使用 JDK11+ HttpClient（无需额外依赖），OpenAI 兼容协议。
 * 异步调用，结果写回 focus_classification.json，随后触发 FocusPoolManager#reclassify。
 */
public class LLMClassifier {

    public static final String PROMPT_TEMPLATE = """
            你是Minecraft模组《诡厄巫法(Goety)》的资深玩家。请将以下聚晶(focus)法术分类并评分。

            分类必须为以下之一：attack(攻击)/defense(防御)/summon(召唤)/other(其他)。
            attack和summon类需要评分(0-10，0.5步进)：
            - attack类给出 attackScore（输出能力）
            - summon类给出 attackScore（召唤物输出能力）和 survivalScore（召唤物生存能力：存活时间/护甲/生命/数量等综合）
            - defense和other类不需要评分（可省略）

            聚晶列表（JSON数组，含id与描述）：
            %s

            请严格只输出JSON对象：{"foci":[{"id":"...","category":"...","attackScore":0,"survivalScore":0}]}，不要输出其他文字。
            """;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /** 收集所有已注册聚晶的描述（id + 本地化描述），供提示词使用。客户端调用时可直接I18n；服务端回退到en_us语言文件。 */
    public static List<String[]> collectFocusDescriptions() {
        List<String[]> out = new ArrayList<>();
        for (FocusEntry e : FocusPoolManager.allEntries()) {
            String id = e.getItemId().toString();
            String descId = e.getFocusItem() instanceof net.minecraft.world.item.Item item
                    ? item.getDescriptionId() + ".info"
                    : "";
            // 优先运行时本地化；不可用时给出key本身，模型仍可依据id推断
            String desc = descId;
            try {
                if (net.minecraftforge.fml.loading.FMLLoader.getDist().isClient()) {
                    String localized = I18n.get(descId);
                    if (localized != null && !localized.equals(descId)) {
                        desc = localized;
                    }
                }
            } catch (Throwable ignored) {
            }
            out.add(new String[]{id, desc});
        }
        return out;
    }

    /**
     * 异步执行自动分类。
     * @param apiKey 大模型API Key（来自 focus_classification.json 的输入）
     * @param promptOverride 自定义提示词（含 %s 占位符则替换聚晶列表；为空则用默认模板）
     * @param onDone 成功回调（已应用条数）；onFail 失败回调（错误信息）
     */
    public static CompletableFuture<Integer> runAsync(String apiKey, String promptOverride,
                                                      Consumer<Integer> onDone, Consumer<String> onFail) {
        List<String[]> foci = collectFocusDescriptions();
        JsonArray arr = new JsonArray();
        for (String[] f : foci) {
            JsonObject o = new JsonObject();
            o.addProperty("id", f[0]);
            o.addProperty("description", f[1]);
            arr.add(o);
        }
        String fociJson = arr.toString();

        // 拼接提示词：优先使用用户自定义（含 %s 占位符时替换聚晶列表），否则追加
        String prompt = (promptOverride == null || promptOverride.trim().isEmpty())
                ? PROMPT_TEMPLATE
                : (promptOverride.contains("%s") ? String.format(promptOverride, fociJson)
                                                 : promptOverride + "\n\n" + fociJson);

        JsonObject body = new JsonObject();
        body.addProperty("model", TunerCommonConfig.LLM_MODEL.get());
        JsonArray messages = new JsonArray();
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", prompt);
        messages.add(msg);
        body.add("messages", messages);
        body.addProperty("temperature", 0.2);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(TunerCommonConfig.LLM_API_URL.get()))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 != 2) {
                    throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
                }
                JsonObject respJson = JsonParser.parseString(resp.body()).getAsJsonObject();
                String content = respJson.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();
                // 【v0.0.3】宽松解析：先剥离 markdown 代码块，再尝试直接解析；
                // 失败则从文本中提取第一个平衡 {…} 对象（容忍前后多余文字）
                content = content.replaceAll("(?s)```(json)?", "").trim();
                JsonArray resultFoci = extractFoci(content);

                FocusClassificationConfig cfg = FocusPoolManager.classification();
                int applied = cfg.writeLLMResult(resultFoci);
                FocusPoolManager.reclassify();
                return applied;
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
            }
        }, java.util.concurrent.Executors.newSingleThreadExecutor()).whenComplete((count, err) -> {
            if (err != null) {
                GoetyTuner.LOGGER.error("LLM auto-classify failed", err);
                onFail.accept(err.getMessage());
            } else {
                onDone.accept(count);
            }
        });
    }

    /**
     * 【v0.0.3】从 LLM 文本中宽鬆提取 foci 数组：
     * ① 直接 JSON 对象含 "foci" → 取之；② 直接 JSON 数组 → 当作 foci；
     * ③ 否则提取首个平衡 {…} 对象并寻找其内部 "foci" 数组；都失败则空数组（不崩溃）。
     */
    private static JsonArray extractFoci(String content) {
        try {
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            if (obj.has("foci") && obj.get("foci").isJsonArray()) {
                return obj.getAsJsonArray("foci");
            }
        } catch (Exception ignored) {
        }
        try {
            JsonArray arr = JsonParser.parseString(content).getAsJsonArray();
            return arr;
        } catch (Exception ignored) {
        }
        // 提取首个平衡花括号块
        int start = content.indexOf('{');
        if (start >= 0) {
            int depth = 0;
            for (int i = start; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        String candidate = content.substring(start, i + 1);
                        try {
                            JsonObject obj = JsonParser.parseString(candidate).getAsJsonObject();
                            if (obj.has("foci") && obj.get("foci").isJsonArray()) {
                                return obj.getAsJsonArray("foci");
                            }
                        } catch (Exception ignored2) {
                        }
                        break;
                    }
                }
            }
        }
        GoetyTuner.LOGGER.warn("[Tuner] Could not parse LLM response into foci array; nothing applied");
        return new JsonArray();
    }
}
