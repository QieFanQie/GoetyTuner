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

    /**
     * 【0.0.6】共享的单线程执行器（守护线程）。
     *
     * <p>原实现把 {@code Executors.newSingleThreadExecutor()} 写在 {@link #runAsync} 的方法体里 ——
     * 每点一次「开始评分」就新建一个线程池且从不 shutdown，**每次点击泄漏一条常驻线程**
     * （非守护线程还会阻碍 JVM 正常退出）。改为静态单例 + 守护线程。
     */
    private static final java.util.concurrent.ExecutorService EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "goetytuner-llm");
                t.setDaemon(true);
                return t;
            });

    /** 【0.0.6】单次请求的聚晶条数上限：超过就分批，避免一次要 200 条导致响应被输出长度限制截断 */
    private static final int FOCI_PER_REQUEST = 60;
    /** 【0.0.6】单次请求的聚晶 JSON 字符预算（双保险：某些聚晶描述特别长时也不会把请求撑爆） */
    private static final int CHARS_PER_REQUEST = 12000;
    /** 【0.0.6】每次请求的输出上限（token）：60 条聚晶约 1500 token，4096 有充足余量 */
    private static final int MAX_TOKENS = 4096;

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
     *
     * 【0.0.6】改为**分批请求**：实测本环境下有 200 个聚晶，一次性塞进单个请求时
     * 模型只返回了 25 条（日志 `applied 25 foci`；既无非法 id 告警也无解析失败，
     * 说明响应本身就是残缺的）。现按 {@link #FOCI_PER_REQUEST} 条 / {@link #CHARS_PER_REQUEST}
     * 字符切批、**顺序**请求并累计应用数（离线脚本实测：分批后 252/252 全部返回）。
     *
     * @param apiKey 大模型API Key（来自 focus_classification.json 的输入）
     * @param promptOverride 自定义提示词（含 %s 占位符则替换聚晶列表；为空则用默认模板）
     * @param onDone 成功回调（累计已应用条数）；onFail 失败回调（错误信息）
     */
    public static CompletableFuture<Integer> runAsync(String apiKey, String promptOverride,
                                                      Consumer<Integer> onDone, Consumer<String> onFail) {
        List<String[]> foci = collectFocusDescriptions();
        List<List<String[]>> batches = partition(foci);

        String url = TunerCommonConfig.LLM_API_URL.get();
        String model = TunerCommonConfig.LLM_MODEL.get();
        String tail = "（model=" + model + "；请检查 llm.apiUrl / llm.model 与网络连通性）";
        GoetyTuner.LOGGER.info("[Tuner] LLM classify start: {} foci in {} batch(es)", foci.size(), batches.size());

        // 顺序串成一条链：避免并发请求触发服务端限流，也让写回顺序可预测
        CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
        for (int i = 0; i < batches.size(); i++) {
            final int index = i + 1;
            final List<String[]> batch = batches.get(i);
            chain = chain.thenCompose(acc -> CompletableFuture.supplyAsync(
                    () -> acc + classifyBatch(apiKey, promptOverride, batch, index, batches.size(), url, model, tail),
                    EXECUTOR));
        }
        return chain.whenComplete((count, err) -> {
            if (err != null) {
                Throwable cause = (err.getCause() != null) ? err.getCause() : err;
                GoetyTuner.LOGGER.error("LLM auto-classify failed", cause);
                onFail.accept(cause.getMessage());
                return;
            }
            int applied = count == null ? 0 : count;
            if (applied < foci.size()) {
                // 明确的"只覆盖了一部分"信号：以后排查不用再翻 HTTP 细节
                GoetyTuner.LOGGER.warn("[Tuner] LLM classify covered only {}/{} foci —— "
                        + "模型只返回了部分条目；已应用的结果不会丢失，可再点一次「开始评分」补齐", applied, foci.size());
            }
            onDone.accept(applied);
        });
    }

    /** 【0.0.6】按条数上限 + 字符预算把聚晶切批 */
    private static List<List<String[]>> partition(List<String[]> foci) {
        List<List<String[]>> out = new ArrayList<>();
        List<String[]> cur = new ArrayList<>();
        int chars = 0;
        for (String[] f : foci) {
            int size = (f[0] == null ? 0 : f[0].length()) + (f[1] == null ? 0 : f[1].length()) + 32;
            if (!cur.isEmpty() && (cur.size() >= FOCI_PER_REQUEST || chars + size > CHARS_PER_REQUEST)) {
                out.add(cur);
                cur = new ArrayList<>();
                chars = 0;
            }
            cur.add(f);
            chars += size;
        }
        if (!cur.isEmpty()) {
            out.add(cur);
        }
        return out;
    }

    /** 【0.0.6】单批：构造提示词 → 请求 → 宽松解析 → 写回；返回本批应用条数 */
    private static int classifyBatch(String apiKey, String promptOverride, List<String[]> batch,
                                     int index, int total, String url, String model, String tail) {
        JsonArray arr = new JsonArray();
        for (String[] f : batch) {
            JsonObject o = new JsonObject();
            o.addProperty("id", f[0]);
            o.addProperty("description", f[1]);
            arr.add(o);
        }
        String fociJson = arr.toString();

        // 拼接提示词：优先使用用户自定义（含 %s 占位符时替换聚晶列表），否则追加。
        // 【0.0.6】用 replace 而非 String.format：提示词框现在可由用户编辑，
        // 若其中含意外的 % 字符（例如写了"100%"），String.format 会抛
        // UnknownFormatConversionException，导致按钮永久卡在"正在评分…"。
        String prompt;
        if (promptOverride == null || promptOverride.trim().isEmpty()) {
            prompt = PROMPT_TEMPLATE;
        } else if (promptOverride.contains("%s")) {
            prompt = promptOverride.replace("%s", fociJson);
        } else {
            prompt = promptOverride + "\n\n" + fociJson;
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", 0.2);
        body.addProperty("max_tokens", MAX_TOKENS);
        JsonArray messages = new JsonArray();
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", prompt);
        messages.add(msg);
        body.add("messages", messages);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        // 阶段一：只负责发请求——连接类失败（超时/DNS/拒绝）在这里给出可定位的报文
        HttpResponse<String> resp;
        try {
            resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            String netErr = e.getMessage() == null ? e.toString() : e.getMessage();
            throw new RuntimeException("请求 " + url + " 失败：" + netErr + tail, e);
        }
        // 阶段二：HTTP 层错误（401/403/429/5xx…）——带 URL 与截断后的响应体
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("请求 " + url + " 返回 HTTP " + resp.statusCode()
                    + "：" + abbrev(resp.body()) + tail);
        }
        // 阶段三：解析与写回
        try {
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
            GoetyTuner.LOGGER.info("[Tuner] LLM batch {}/{}: sent {} foci -> applied {}",
                    index, total, batch.size(), applied);
            return applied;
        } catch (Exception e) {
            String parseErr = e.getMessage() == null ? e.toString() : e.getMessage();
            throw new RuntimeException("解析/写回 " + url + " 的响应失败：" + parseErr + tail, e);
        }
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

    /**
     * 【0.0.5】截断错误响应体：HTTP 错误页（HTML）动辄数十 KB，
     * 直接拼进界面状态行与 Toast 会刷屏并拖慢渲染，故只保留前 300 字符。
     */
    private static String abbrev(String body) {
        if (body == null) {
            return "(empty)";
        }
        String oneLine = body.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 300 ? oneLine : oneLine.substring(0, 300) + "…";
    }
}
