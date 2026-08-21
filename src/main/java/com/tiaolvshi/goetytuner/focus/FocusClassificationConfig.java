/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.focus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.focus.LLMClassifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 静态聚晶分类与评分表：config/goetytuner/focus_classification.json
 *
 * 结构：
 * {
 *   "apiKey": "",                  // LLM API Key（仅存本地）
 *   "foci": {
 *     "goety:vexing_focus": { "category": "summon", "attackScore": 4, "survivalScore": 3 },
 *     "goety:soul_bolt_focus": { "category": "attack", "attackScore": 7 }
 *   }
 * }
 *
 * 该文件可手写，也可由 ConfigScreen 的「自动配置」按钮经 LLM 生成（见 LLMClassifier）。
 */
public class FocusClassificationConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, FocusCategory> CATEGORIES = new LinkedHashMap<>();
    /** 已加载的 lang 缓存（key -> 英文描述）。服务端无客户端语言文件，须自行从 classpath 读 jar 内 en_us.json */
    private static final Map<String, String> LANG_CACHE = new HashMap<>();

    private final Path file;
    /** 聚晶id -> [category, attackScore, survivalScore] */
    private final Map<String, double[]> table = new LinkedHashMap<>(); // [cat, atk, surv]
    private final Map<String, String> rawCategories = new LinkedHashMap<>();
    private String apiKey = "";
    /** 【v0.0.3】LLM 提示词（自定义覆盖默认模板；空=用默认模板） */
    private String prompt = "";

    /** 合法聚晶 id 校验（modid:itemid，宽松） */
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_/.*-]+$");

    public FocusClassificationConfig() {
        this.file = FMLPaths.CONFIGDIR.get().resolve(GoetyTuner.MOD_ID).resolve("focus_classification.json");
    }

    public static FocusClassificationConfig load() {
        FocusClassificationConfig cfg = new FocusClassificationConfig();
        try {
            if (Files.exists(cfg.file)) {
                // 【v0.0.3】宽松解析：容忍尾随逗号等轻微格式瑕疵
                JsonReader reader = new JsonReader(new StringReader(Files.readString(cfg.file)));
                reader.setLenient(true);
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null) root = new JsonObject();
                if (root.has("apiKey")) {
                    cfg.apiKey = root.get("apiKey").getAsString();
                }
                if (root.has("prompt")) {
                    cfg.prompt = root.get("prompt").getAsString();
                }
                if (root.has("foci") && root.get("foci").isJsonObject()) {
                    JsonObject foci = root.getAsJsonObject("foci");
                    for (Map.Entry<String, com.google.gson.JsonElement> e : foci.entrySet()) {
                        JsonObject o = e.getValue().getAsJsonObject();
                        String cat = o.has("category") ? o.get("category").getAsString() : "other";
                        double atk = o.has("attackScore") ? o.get("attackScore").getAsDouble() : 5.0;
                        double surv = o.has("survivalScore") ? o.get("survivalScore").getAsDouble() : 5.0;
                        cfg.rawCategories.put(e.getKey(), cat);
                        cfg.table.put(e.getKey(), new double[]{atk, surv});
                    }
                }
            } else {
                cfg.writeDefaults();
            }
        } catch (Exception ex) {
            GoetyTuner.LOGGER.error("Failed to load focus classification, using defaults", ex);
        }
        return cfg;
    }

    /** 生成示例文件（含注释性的示例条目） */
    private void writeDefaults() {
        rawCategories.put("goety:soul_bolt_focus", "attack");
        table.put("goety:soul_bolt_focus", new double[]{7.0, 5.0});
        rawCategories.put("goety:iron_hide_focus", "defense");
        table.put("goety:iron_hide_focus", new double[]{5.0, 5.0});
        rawCategories.put("goety:rotting_focus", "summon");
        table.put("goety:rotting_focus", new double[]{4.0, 6.0});
        save();
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            if (apiKey != null && !apiKey.isEmpty()) {
                root.addProperty("apiKey", apiKey);
            }
            // 仅在用户真正自定义过提示词时落盘；空则沿用默认模板，避免文件冗余
            if (prompt != null && !prompt.isEmpty()) {
                root.addProperty("prompt", prompt);
            }
            JsonObject foci = new JsonObject();
            for (String id : table.keySet()) {
                JsonObject o = new JsonObject();
                o.addProperty("category", rawCategories.getOrDefault(id, "other"));
                double[] scores = table.get(id);
                o.addProperty("attackScore", scores[0]);
                o.addProperty("survivalScore", scores[1]);
                foci.add(id, o);
            }
            root.add("foci", foci);
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException ex) {
            GoetyTuner.LOGGER.error("Failed to save focus classification", ex);
        }
    }

    // ---- 应用到条目 ----

    /** 从 classpath 加载某命名空间的 en_us.json 到 lang 缓存（幂等，失败静默） */
    private static synchronized void ensureLangLoaded(String namespace) {
        String path = "assets/" + namespace + "/lang/en_us.json";
        if (LANG_CACHE.containsKey("__loaded:" + namespace)) {
            return;
        }
        LANG_CACHE.put("__loaded:" + namespace, "1");
        try (InputStream in = FocusClassificationConfig.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                return;
            }
            JsonObject lang = GSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
            for (Map.Entry<String, com.google.gson.JsonElement> e : lang.entrySet()) {
                LANG_CACHE.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception ex) {
            GoetyTuner.LOGGER.debug("Failed to load lang {}: {}", path, ex.toString());
        }
    }

    /** 取聚晶英文描述：优先 classpath 读 jar 内 en_us.json（服务端可用），客户端语言缺失时兜底 translatable。
     *  【第二十九轮】兼容两种描述 key 后缀：Goety 本体用 .info，诡厄暮色(goetytwilight)等附属用 .desc——
     *  此前只查 .info 查不到时回退到 key 字符串本身（含 "ice"/"thorn" 等词），导致召唤聚晶被误判为攻击 */
    private static String describe(ResourceLocation id) {
        ensureLangLoaded(id.getNamespace());
        String base = "item." + id.getNamespace() + "." + id.getPath();
        String cached = LANG_CACHE.get(base + ".info");
        if (cached == null || cached.isEmpty()) {
            cached = LANG_CACHE.get(base + ".desc");
        }
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        return net.minecraft.network.chat.Component.translatable(base + ".info").getString();
    }

    /** 将静态分类与评分应用到扫描到的条目；未配置的聚晶按 lang 描述启发式分类（FocusClassifier 兜底） */
    public void applyTo(Iterable<FocusEntry> entries) {
        for (FocusEntry entry : entries) {
            String id = entry.getItemId().toString();
            String cat = rawCategories.get(id);
            double[] scores = table.get(id);
            if (cat != null && scores != null) {
                entry.setCategory(FocusCategory.byId(cat));
                entry.setAttackScore(scores[0]);
                entry.setSurvivalScore(scores[1]);
            } else {
                // 【第二十九轮】运行时权威判定：法术实现 ISummonSpell（Goety 召唤法术接口，
                // 诡厄暮色等附属的全部仆从聚晶都继承 SummonSpell→ISummonSpell）→ 一律 SUMMON。
                // lang 启发式对附属 mod 的描述措辞/后缀不可靠（实测 ice_crystal_focus 等
                // 召唤聚晶因 key 含 "ice" 被误判为攻击，混入攻击池），instanceof 是确定性判定。
                if (entry.getSpell() instanceof com.Polarice3.Goety.api.magic.ISummonSpell) {
                    entry.setCategory(FocusCategory.SUMMON);
                    double[] gs = FocusClassifier.guessScores(FocusCategory.SUMMON);
                    entry.setAttackScore(gs[0]);
                    entry.setSurvivalScore(gs[1]);
                    continue;
                }
                // 未配置：启发式按 lang 描述分类（LLM/手写配置仍可覆盖）
                String desc = describe(entry.getItemId());
                FocusCategory guessed = FocusClassifier.guess(desc);
                double[] gs = FocusClassifier.guessScores(guessed);
                entry.setCategory(guessed);
                entry.setAttackScore(gs[0]);
                entry.setSurvivalScore(gs[1]);
            }
        }
    }

    // ---- LLM 结果写回 ----

    /**
     * 【v0.0.3】接收 LLM 输出的 foci 数组，做宽松校验与修正后写回：
     * - 跳过缺失 id / 非法 id（不符合 modid:itemid）的条目
     * - 分类按关键词宽松归一化（byIdLenient），未知→other
     * - 评分钳制到 [0,10] 并按 0.5 步进取整（与设计规范一致）
     */
    public synchronized int writeLLMResult(JsonArray fociArray) {
        int applied = 0;
        for (var el : fociArray) {
            if (el == null || !el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            if (!o.has("id")) {
                GoetyTuner.LOGGER.warn("[Tuner] LLM result entry missing 'id', skipped");
                continue;
            }
            String id = o.get("id").getAsString();
            if (id == null || !ID_PATTERN.matcher(id).matches()) {
                GoetyTuner.LOGGER.warn("[Tuner] LLM result has invalid id '{}', skipped", id);
                continue;
            }
            String catRaw = o.has("category") ? o.get("category").getAsString() : "other";
            String cat = FocusCategory.byIdLenient(catRaw).getId();
            double atk = roundToHalf(clampScore(o.has("attackScore") ? o.get("attackScore").getAsDouble() : 5.0));
            double surv = roundToHalf(clampScore(o.has("survivalScore") ? o.get("survivalScore").getAsDouble() : 5.0));
            rawCategories.put(id, cat);
            table.put(id, new double[]{atk, surv});
            applied++;
        }
        GoetyTuner.LOGGER.info("[Tuner] LLM auto-classify applied {} foci", applied);
        save();
        return applied;
    }

    private static double clampScore(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 5.0;
        return Math.max(0.0, Math.min(10.0, v));
    }

    /** 按 0.5 步进取整（0~10 范围） */
    private static double roundToHalf(double v) {
        return Math.round(v * 2.0) / 2.0;
    }

    public String getApiKey() {
        return apiKey;
    }

    public synchronized void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    /** 返回当前提示词：空则用默认模板（LLMClassifier.PROMPT_TEMPLATE） */
    public String getPrompt() {
        return (prompt == null || prompt.isEmpty()) ? LLMClassifier.PROMPT_TEMPLATE : prompt;
    }

    /** 返回自定义提示词原文（未自定义则返回空串，用于 UI 输入框预填，避免塞入整段默认模板） */
    public String getPromptTextOrDefault() {
        return (prompt == null) ? "" : prompt;
    }

    public synchronized void setPrompt(String p) {
        this.prompt = (p == null) ? "" : p;
    }
}
