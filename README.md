# Goety Tuner · 调律师

> 当前工作版本：**0.0.15**。本体贴图精修，保持原有紫黑像素风与头部；详见 [精修记录](art/REFINEMENT.md)。下文旧版本说明保留为历史记录。

> **0.0.15 发布（第 53 轮）**：**只有贴图精修 + 版本号 + 文档，零 Java 改动、无新增/删除资源、配置项数不变（仍 63 项 / 10 段）**。精修 `src/main/resources/assets/goetytuner/textures/entity/tuner.png`（64×64 RGBA，2520 → **2676 B**）：保留紫黑礼服 / 紫色袖口裤靴 / 浅色领巾 / 青色胸饰，精修**翻领边缘、衣袖明暗、袖口细边、裤缝、靴口层次**；原稿由 imagegen 生成，再**按最近邻采样重新装配回原有 UV**（生成图未严格保持矩形位置，故重新测量图块）。**已独立核验（以 0.0.14 为基准逐像素差分，不依赖其自带脚本）**：头部区 **y0..15 全宽 64 列 0 个像素差异**、全图 **alpha 蒙版完全一致**（未增删不透明像素）、身体区改动 **1247 像素**、6 个主要 UV 面**无透明空洞**。制作过程入库：`art/REFINEMENT.md`（报告 + 最终提示词）、`art/assemble-refinement.ps1`（可复现装配）、`art/body-refined-source.png`（原稿）、`art/tuner-before-refinement.png`（精修前备份）、更新后的 `art/preview.png`。⚠️ **未做游戏内画面验收**——观感仍待人类 / 能读图的模型确认；`art/preview.png` 是**平面正背面拼接图，不是游戏截图**。部署：`goetytuner-0.0.15.jar`（**1,755,456 B / md5 `1865ECF4EB22989319FD746806320776` / jar 内 102 条目**）→ `versions\测试\mods\`（旧 0.0.14 已删）。
>
> **上一轮（0.0.14，第 52 轮）发布**：该轮两件事，改 **4 个 Java 文件**（`client/TunerConfigScreen`、`focus/FocusPoolManager`、`entity/TunerBoss`、`config/TunerCommonConfig`）+ **2 个 lang** + 版本号，**只改代码、不动任何贴图/资源**（`.java` 仍 45 个、jar 条目仍 102）；**新增 1 个配置项** `phase2_buffs.phase2EffectImmunity`（Boolean，默认 **true**）⇒ 配置 **62 → 63 项**（`phase2_buffs` 段 **3 → 4**，`boss` 段仍 **19**，section 仍 10）。① **配置界面新增「聚晶黑名单」输入框**（`client/TunerConfigScreen` + `focus/FocusPoolManager`）：`focus.blacklist` 是 common 配置，而本模组的 `TunerConfigScreen` **顶替了 Forge 默认的 toml 编辑器**，该键此前在游戏内**完全没有入口**、只能手改文件；现补一个单行 `EditBox`（预填当前值，提示「namespace:path，英文逗号分隔；留空=不屏蔽」），**点「完成」关屏时保存**（最自然的"改完了"信号），点「开始评分」时也**顺带保存**；保存动作 = `FOCUS_BLACKLIST.set(v)` + `.save()` + `FocusPoolManager.refreshBlacklist()` ⇒ **改完立即生效**（`getBlacklist()` 以 raw 字符串为缓存键，值一变缓存自动失效 ⇒ 下一次抽取就过滤）。**新增 `FocusPoolManager.refreshBlacklist()`**：`initIfNeeded()` 扫描时是**直接 `continue` 跳过**黑名单聚晶（它们不进 `ALL_ENTRIES`），所以"**新增**拉黑"能靠 `draw()/drawUniform()` 的实时过滤立刻生效，但"**取消**拉黑"必须重扫才会回来；而重扫若 `new` 出全新 `FocusEntry`，会让实体侧那些**按对象身份**记录的状态失效（`TunerBoss.activeVisualCasts` 身份集合、`CastChannel.current`）⇒ 出现「立方体高亮卡住 / 施法收尾回调对不上」这类隐蔽问题。故新方法按 `namespace:path` 建索引**复用已有 `FocusEntry` 对象**，只为"这次才被解禁"的聚晶新建（它们此前不可能在施法中，故安全），然后重建 `STATIC_POOLS` 并重新 `applyTo` 分类。新增 4 个 lang 键（zh_cn / en_us 各 4 个）。限制：连他人的服务器时改的是**本地**那份 common toml，服务器侧不受影响（单机/局域网主机同进程，正常生效）。② **二阶段免疫「回复 / 减伤」类药水效果**（`entity/TunerBoss` + `config/TunerCommonConfig`）：扩展现有的 `canBeAffected` 覆写（它同时是 `addEffect` 与 `forceAddEffect` 的**第一道**判定，在这里返回 false 就是真正的免疫，而不是"加完再清"）；免疫名单 = **抗性提升** `DAMAGE_RESISTANCE` / **伤害吸收** `ABSORPTION` / **生命恢复** `REGENERATION` / **瞬间治疗** `HEAL` / **生命提升** `HEALTH_BOOST`，**仅当 `music.isPhase2()`** 时生效。**刻意只列这 5 项而不是"所有 beneficial"**：Boss 自己的二阶段增益（力量 `DAMAGE_BOOST` 与重振 `RALLYING`）也是 beneficial，一刀切会把它们一起禁掉；名单集中在私有 `isPhase2Immune(...)`，以后要加（例如某个附属的自定义减伤）加一行即可。新增配置 `phase2_buffs.phase2EffectImmunity`（Boolean，**默认 true**），false = 回到旧行为；不影响玩家的同类效果，也不影响 Boss 自己的二阶段自施。详见 `TECHNICAL_SUMMARY.md` §3.15。
>
> **更早（0.0.13，第 51 轮）修复**：该轮改了 **7 个 Java 文件** + 版本号，**只改代码、不动任何贴图/资源**（`.java` 仍 45 个、jar 条目仍 102）；**新增 1 个配置项** `music.accentDensityDivisor` ⇒ 配置 **61 → 62 项**（`music` 段 12 → 13，section 仍 10）。① **修「玩家被击杀复活后（未走出索敌范围）背景音乐丢失」**（`client/BossMusicManager`）：起播唯一入口是 `if (music == null) startMusic()`，而"是否在播"**只**记录为静态字段 `music != null`、**从不与声音引擎核对**；玩家死亡/复活时引擎会把循环实例悄悄摘除（RECORDS 音量为 0、channel 停止、`SoundEngine.reload()→destroy()→stopAll()`、`play()` 在未 loaded 时静默返回），字段却仍非 null ⇒ 只要服务端 `playing` 一直为 true（**没脱战**），`startMusic` **永不重入** = **永久静音**；且 `onPlaySound` 也只看字段 ⇒ **连原版背景音乐也一起被永久取消**（症状「整个 BGM 都没了」）。修法：每 tick 用 **`SoundManager.isActive(music)`**（1.20.1 自带 API，已反编译确认存在）核实实例是否真的在响，失效即清空字段、下一 tick 自动重建（**自愈**），并加 **10 tick 防抖**；`onPlaySound` 判据同步改为 `music != null && isActive(music)`。② **重音标记滚动平滑 + 高潮改细小长条**（`client/MusicBarHud`）：`fill()` 只能落在整数像素、而刻度只有 1~2px ⇒ 取整后**逐像素跳动**；改为**亚像素覆盖**（小数部分按比例摊到相邻两列，亮度重心连续移动，调用点不再预先取整）；高潮的「中」字（贯通竖线 + 空心方框）改为**小长条**：高潮 **2×6** 最亮 / 低谷 **1×6** / 铺垫 **1×4**，全部竖向居中。③ **涟漪多波共存**（`client/AccentWaveRenderer` 的 `Map` 改 `List` + `MAX_WAVES = 32`）：二阶段进场重音每 5 tick 一发、连发多次，原先后一发**顶掉**前一发 ⇒ 只看到一条波反复重播；现每发各成一条波、各自计时，层叠外扩。④ **重音数量与频率降为 1/3**：新增 `music.accentDensityDivisor`（`IntValue`，默认 **3**、范围 1~9），从乐谱重音表"每 N 个保留 1 个"；抽稀在**服务端加载乐谱时**统一进行 ⇒ HUD 刻度（经 `SMusicSyncPacket` 全量同步）、击退、涟漪、提示音**一起**变稀疏，客户端无需改动（默认乐谱 37 → **13** 个重音，INFO `Accent thinning x3: 37 -> 13 accents`）。⑤ **小立方体高亮更明显 + 发光**（`client/render/TunerOrbLayer`）：高亮原为 `min(1, color*(1+glow*0.8))`，红/蓝/灰分量本就近 1、被 `min` 截断后**几乎看不出变化**，改为额外叠加 `glow*0.4` 逐通道**向白靠拢**；「发光」用**两层自发光外壳**（1.30×/alpha 0.36、再叠 1.35×/alpha 0.18，`RenderType.entityTranslucentEmissive`，着色器忽略光照 ⇒ 稳定发亮；已实证它同样无条件 `NO_CULL`）——**不能用原版发光描边**（MC 的发光是**整实体级** framebuffer 后处理 `OutlineBufferSource`，只能整只 Boss 一起描边，**无法只描一颗立方体**）。⑥ **药水效果可观测性**（`entity/TunerBoss`）：8 处 `addEffect(...)` 的 boolean 返回值原先**全被丢弃**，而 `canBeAffected` 与 Forge 的 `MobEffectEvent.Applicable` 都在这一步否掉 ⇒ 被拒时**静默失效、日志无一字**；新增 `applySelfEffect(...)` 打 WARN（已用于 boss 自身 4 处），并完成药水现状审计（见 `TECHNICAL_SUMMARY.md` §3.14(6)）。⑦ **配置侧**把 `goety:killing_focus`（索命聚晶，对施法者反噬 125%）加入 `focus.blacklist`——**这是配置侧改动、不在 jar 内**；且 `focus.blacklist`（乃至整个 `goetytuner-common.toml`）**无法在游戏内配置界面修改**。0.0.10 美术项与 0.0.12 新外观仍待游戏画面验收，见 [ART_ASSETS_REPORT.md](ART_ASSETS_REPORT.md)。

诡厄巫法（Goety）附属Boss模组 —— 1.20.1 Forge。

人形无头指挥家「调律师」，以音乐（铺垫/高潮/低谷）驱动战斗节奏，
轮番演奏诡厄巫法及其附属注册的全部聚晶，并带有自学习式聚晶评分系统。

**当前版本：0.0.15**（MC 1.20.1 Forge 47.3.22 / Goety 2.5.56.5 附属）

## 文档索引（按优先级）

| 文档 | 定位 | 时效 |
|---|---|---|
| **[TECHNICAL_SUMMARY.md](TECHNICAL_SUMMARY.md)** | 技术现状权威文档：架构/核心系统/工程红线/版本时间线 | **最新**（已覆盖至 0.0.15 / 第 53 轮，最新一轮；覆盖轮次口径以该文档为准） |
| [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md) | 阶段性开发计划书：完成度/未完成项/配置总览/实测记录 | 进度表可能滞后，以技术摘要为准 |
| [DESIGN_RITUAL_WAND_UPGRADE.md](DESIGN_RITUAL_WAND_UPGRADE.md) | 单任务设计稿（任务 #118 仪式召唤 + 法杖升级） | 已实施，保留作设计留痕 |

## 作者 / 团队

| | |
|---|---|
| **作者** | QieFanQie & vibe-coding |
| **团队** | Goety Tuner Project |
| **音乐** | 由 [乌鸦Producer] 提供（音频在原曲基础上有改动） |
| **GitHub** | <https://github.com/QieFanQie/> |
| **许可证** | MIT License |

## 快速开始

```bash
# 1. 构建诡厄巫法本体（或直接下载发行jar）
cd ../Goety-2-1.20 && ./gradlew build

# 2. 把 Goety / Patchouli / Curios / Configured 四个 mod 的【发行jar】放入 libs/
#    （libs/ 仅此一处；run/mods/ 必须保持为空，见下方红线。
#     GeckoLib 已移除：Goety 2.5.56.5 本体零 geckolib 引用，boss 渲染走原版模型+贴图）
mkdir -p ../goety-tuner/libs
cp build/libs/Goety-*.jar ../goety-tuner/libs/goety-2.5.56.5.jar
cp <patchouli发行jar> ../goety-tuner/libs/patchouli-1.20.1-81-FORGE.jar
cp <curios发行jar>    ../goety-tuner/libs/curios-forge-5.14.1+1.20.1.jar
cp <configured发行jar> ../goety-tuner/libs/configured-2.2.3.jar

# 3. 构建 & 运行（必须 JDK 17；Gradle 8.1.1 不支持 Java 21）
cd ../goety-tuner
./gradlew runClient
```

> **⚠️ dev 环境依赖红线（第四轮定论 + 第七轮补齐，实测推翻前三轮方案）**：
> - **`run/mods/` 不得放任何正式 jar**。ModLauncher 的 srgtomcp 只映射 minecraft/forge
>   域，`run/mods/` 里的 mod jar 保持 SRG 名不变，dev 环境 MC 是 MCP 名，运行期调用必崩
>   （实证：Patchouli `BookCrashHandler` 调 `Minecraft.m_91087_()` → `NoSuchMethodError`）。
> - 四个 mod 一律用**坐标式 `implementation fg.deobf("blank:<mod>:<version>")`** +
>   build.gradle 顶部 `flatDir { dirs 'libs' }`，由 FG 本地反混淆成 MCP 命名域、全离线
>   运行（依赖走 classpath，不经 run/mods）。注意 `fg.deobf(files(...))` 不受支持
>   （FG6 直接跳过反混淆用原 jar），必须坐标式。
> - **refmap 必须清空（所有带 mixin 的 mod 都要）**：Goety/Patchouli/Curios/
>   Configured 原版 refmap 都是 MCP→SRG 同构结构，dev 环境会把 `@Accessor/@Inject/
>   @ModifyArg` 的 MCP 名解析成 SRG 名找不到目标（实证：Configured ScreenMixin 的
>   `m_280039_`）。修改 libs
>   jar 后运行 `python scripts/clear_refmaps.py <jar...>` 清空 `mappings`/`data`，再重跑
>   `./gradlew compileJava` 触发 FG 重建 mapped jar（缓存路径
>   `~/.gradle/caches/forge_gradle/deobf_dependencies/blank/<mod>/...`）。
> - **升级任一 mod 版本必须重复**：清 refmap → 重跑 compileJava → runClient 验证。
> - 修改版 jar **仅 dev 测试用**，玩家正式环境必须用官方原版 jar（原版备份见
>   `scripts/mods-backup/` 与 `run/mods/*.orig`）。

## 目录导览

源码包 `com.tiaolvshi.goetytuner`（45 个类）：

- `init/` 注册（实体/物品/音效/属性/事件），含链锤实体级拦截
- `entity/` Boss 实体 `TunerBoss`（核心，2050 行）、`MusicController` 乐谱与阶段、`BossPhase`
- `entity/ai/` `CastChannel` 施法通道（前摇/锁池/朝向钉死/异常自愈）
- `focus/` 聚晶池系统（分类/评分/轮盘/冷却池/黑名单/LLM与启发式分类/主手杖装配）
- `combat/` 动态评分（伤害 DPS 200tick 归因、召唤物输出/生存二维追踪）
- `ritual/` 仪式召唤 `TunerSummonRitual` + `goety:ritual_factory` 注册 + 法杖升级事件
- `command/` `/goetytuner tune` 命令
- `config/` `TunerCommonConfig`（63 项配置 / 10 个 section）
- `network/` `SMusicSyncPacket`（进度/分段/重音/演奏状态/速度）、`SShakePacket`、`SEntityRevivePacket`（死亡动画复位）、`SAccentWavePacket`（声波涟漪触发，**通道 id 3**，限 `PLAY_TO_CLIENT`）
- `client/` 音乐播放器 `BossMusicManager`（**0.0.13 起每 tick 用 `SoundManager.isActive` 核实循环实例是否真的在响，失效即清空字段并自愈重建（+10 tick 防抖）**，修掉「被击杀复活后 BGM 丢失」以及连带把原版 BGM 永久取消的问题）、节奏条 HUD（`MusicBarHud`：204×8、逐行混色分段 + 柔和投影 + 像素阶段符号；**0.0.13 起重音刻度改亚像素覆盖平滑滚动、高潮改 2×6 小长条**）、配置界面 + Toast（**0.0.14 起界面上多了「聚晶黑名单」输入框**，关屏/开始评分时保存并立即生效）、相机震颤、`ClientDeathAnimation`（死亡动画复位）、`AccentWaveRenderer`（世界空间声波涟漪，锚定触发瞬间坐标、只按时间清理；**0.0.13 起活跃表由 `Map` 改为 `List` ⇒ 同一实体多波共存、`MAX_WAVES=32` 兜底**，`RenderLevelStageEvent.Stage.AFTER_PARTICLES`）
- `client/render/` 原版 HumanoidModel 渲染 + 自定义披风层 `TunerCapeLayer`/`TunerCapeModel` + 悬浮立方体层 `TunerOrbLayer`/`TunerOrbModel`（模型层 `goetytuner:tuner_orb`，无 GeckoLib；**0.0.13 立方体高亮改「向白插值」+ 两层 `entityTranslucentEmissive` 自发光外壳**——原版发光描边是**整实体级**，无法只描一颗立方体）
- 资源：`assets/goetytuner/`（中英 lang、贴图、内置 boss 音乐 ogg）、`data/goety/recipes/tuner_boss_ritual.json`

## 已知事项（0.0.15）

- **0.0.15 本体贴图精修（头部不变），观感待游戏验证**：本轮**只有贴图 + 版本号 + 文档**，**零 Java 改动、无新增/删除资源、配置项数不变（仍 63 项 / 10 段）**（`.java` 仍 45 个、jar 条目仍 102）。精修 `assets/goetytuner/textures/entity/tuner.png`（**64×64 RGBA，2520 → 2676 B**）：保留紫黑礼服 / 紫色袖口裤靴 / 浅色领巾 / 青色胸饰，精修**翻领边缘、衣袖明暗、袖口细边、裤缝、靴口层次**；原稿由 imagegen 生成，再**按最近邻采样重新装配回原有 UV**（生成图未严格保持矩形位置，故重新测量图块）。✅ **已独立核验**（以 0.0.14 为基准逐像素差分，不依赖其自带脚本）：**头部区 y0..15 全宽 64 列 0 个像素差异**、**全图 alpha 蒙版完全一致**（未增删不透明像素）、身体区改动 **1247 像素**、6 个主要 UV 面**无透明空洞**。⚠️ **未做游戏内画面验收**——观感仍待人类 / 能读图的模型确认；`art/preview.png` 是**平面正背面拼接图、不是游戏截图**。制作过程见 [art/REFINEMENT.md](art/REFINEMENT.md)（报告 + 最终提示词）、`art/assemble-refinement.ps1`（可复现装配）、`art/body-refined-source.png`、`art/tuner-before-refinement.png`。
- **0.0.15 部署**：`goetytuner-0.0.15.jar`（**1,755,456 B / md5 `1865ECF4EB22989319FD746806320776` / jar 内 102 条目**）已放 `versions\测试\mods\`（旧 0.0.14 已删）。构建在主副本就地 `gradlew clean build` 成功（**1m44s**，仍只有原有 3 条 Forge 弃用警告）。⚠️ **jar 的 md5 不可复现**（`MANIFEST.MF` 的 `Implementation-Timestamp`）——本轮又实证一次：同一份只改贴图的源码，**另一位 agent 自建为 1,755,455 B、我重建为 1,755,456 B**，**源码相同、仅时间戳不同**；判断「部署的 jar 对应哪份源码」要**逐条目比对**，别看整体 md5。
- **0.0.14 配置界面终于有「聚晶黑名单」入口了**：`focus.blacklist` 是 common 配置，而本模组的 `TunerConfigScreen` **顶替了 Forge 默认的 toml 编辑器**，该键此前在游戏内**完全没有入口**（只能手改 `goetytuner-common.toml`）。0.0.14 在配置界面补了一个单行输入框（预填当前值，提示「namespace:path，英文逗号分隔；留空=不屏蔽」），**点「完成」关屏时保存**、点「开始评分」时也顺带保存，保存后**立即生效**（新增拉黑靠实时过滤、取消拉黑靠新方法 `FocusPoolManager.refreshBlacklist()` 重扫；该方法**复用已有 `FocusEntry` 对象**以保住实体侧按对象身份记录的状态，避免"立方体高亮卡住 / 施法收尾回调对不上"）。⚠️ 这是**唯一**一个补了入口的 common 键——**其余 common 配置项仍然只能手改 toml**。⚠️ 连他人的服务器时改的是**本地**那份 toml，服务器侧不受影响。
- **0.0.14 二阶段免疫「回复 / 减伤」类药水效果（可按需关闭）**：进入二阶段后，Boss **无法**再被施加 **抗性提升 / 伤害吸收 / 生命恢复 / 瞬间治疗 / 生命提升** 这 5 项（走 `canBeAffected` 覆写，是 `addEffect` 与 `forceAddEffect` 的**第一道**判定 ⇒ 真正的免疫，不是"加完再清"）。**刻意只列这 5 项而非"所有 beneficial"**：Boss 自己的二阶段增益（力量 `DAMAGE_BOOST`、重振 `RALLYING`）也是 beneficial，一刀切会把它们一起禁掉。新增配置 `phase2_buffs.phase2EffectImmunity`（默认 **true**），**改为 `false` 即回到旧行为**。不影响玩家的同类效果，也不影响 Boss 自己的二阶段自施。详见 `TECHNICAL_SUMMARY.md` §3.15(2)。
- **0.0.14 部署**：`goetytuner-0.0.14.jar`（**1,755,283 B / md5 `DA2A20C328B41D71DB9CFCC0AF81DAD2` / jar 内 102 条目**）已放 `versions\测试\mods\`（旧 0.0.13 已删）。构建在主副本就地 `gradlew clean build` 成功（1m20s，仍只有原有 3 条 Forge 弃用警告）。⚠️ **jar 的 md5 不可复现**（`MANIFEST.MF` 的 `Implementation-Timestamp`），判断「部署的 jar 对应哪份源码」要**逐条目比对**，别看整体 md5（实证见下条）。
- **0.0.13 音乐丢失修复（自带自愈）**：玩家被击杀 → 复活、且**没走出索敌范围**时，Boss 背景音乐**有时**永久不响（用户反馈「整个 BGM 都没了」）。根因：起播唯一入口 `if (music == null) startMusic()` 把「是否在播」**只**记在静态字段上，**从不与声音引擎核对** —— 死亡/复活会让引擎把循环实例悄悄摘除（`RECORDS` 音量 0 / channel 停止 / `SoundEngine.reload()→destroy()→stopAll()` / `play()` 未 loaded 时静默返回），字段却仍非 null ⇒ 没脱战时 `startMusic` **永不重入**；`onPlaySound` 同样只看字段 ⇒ 连原版背景音乐也一并被永久取消。现改为每 tick 用 `SoundManager.isActive(music)` 核实 + **10 tick 防抖**，失效即清空字段、下一 tick 自愈重建。调查另确认：**死亡本身不停声音**（`DeathScreen` 无 soundManager 调用）、**同维度复活不替换 `ClientLevel`**，「有时」取决于 `playing=false` 是否越过 `STOP_GRACE_TICKS(30t)` / `SYNC_TIMEOUT_MS(3000ms)`（**单人死亡界面会暂停集成服务器**）。详见 `TECHNICAL_SUMMARY.md` §3.14(1)。
- **0.0.13 重音更稀疏 + 表现层微调**：新增配置 `music.accentDensityDivisor`（默认 **3**、范围 1~9）在**服务端加载乐谱时**「每 N 个保留 1 个」⇒ HUD 刻度 / 击退 / 涟漪 / 提示音**一起**变稀疏、客户端零改动（乐谱文件仍 37 个、运行时 **13** 个，INFO `Accent thinning x3: 37 -> 13 accents`）。同轮：HUD 重音刻度改**亚像素覆盖**（滚动不再逐像素跳动）、高潮「中」字改 **2×6 小长条**（低谷 1×6 / 铺垫 1×4）；涟漪活跃表 `Map`→`List` 让连发**多波共存**；立方体高亮改「向白插值」+ 两层 `entityTranslucentEmissive` 自发光外壳（原版发光描边是**整实体级**，无法只描一颗立方体）。**以上表现层改动均尚未进游戏画面验收**。
- **0.0.13 药水效果现状（审计结论）**：一阶段低谷 boss 自施 `SAPPED`(amp 11) + `DARKNESS` **每 tick 刷新 = 常驻、机制上生效**，但两者都构造为 `visible=false`（**无粒子**）⇒ **玩家看不出效果**；仆从四效果同样只在一阶段低谷（**二阶段不跑 `tickValley`**：VALLEY 段被 `MusicController` 重映射为 BUILDUP，且二阶段清空仆从）。二阶段自施 `DAMAGE_BOOST` + `RALLYING` 可达、不会被 `cleanseEffects` 抹掉、也**不受亡灵免疫影响**（`TunerBoss` 未覆写 `getMobType`，为 `UNDEFINED` 非 `UNDEAD`）；`RALLYING` 只加近战，故另挂了 `SPELL_POTENCY` modifier。`SUMMON_DOWN` 免疫**确认真实生效**。`tickPhase2Buffs` 自身零日志 ⇒ 二阶段自施药水的**实际数值收益只能进游戏实测**。详见 `TECHNICAL_SUMMARY.md` §3.14(6)。
- **0.0.13 索命聚晶拉黑（配置侧，不在 jar 内）**：`focus.blacklist` 改为 `goetytwilight:destruction_focus, goety:killing_focus`（索命聚晶 = **`goety:killing_focus`**，本模组把它评为 `attack` / attackScore 0 ⇒ 轮盘基础权重 2.0、**确实会被抽到**，而该法术对施法者**反噬 125%**）。⚠️ **代码默认值仍只有 `destruction_focus`**，本轮改的是**游玩实例的 toml** ⇒ 换实例/新玩家不会自动带上。⚠️ **可访问性结论（0.0.14 更新）**：`focus.blacklist` **已经在游戏内配置界面提供了输入框**（0.0.14 新增，见上方「0.0.14 配置界面终于有『聚晶黑名单』入口了」一条）——点「完成」关屏或点「开始评分」即保存并**立即生效**，不再必须手改文件。但本模组的 `TunerConfigScreen` **顶替**了 Forge 默认的 toml 编辑器，界面**只有** LLM API Key / 提示词 / 聚晶黑名单三个输入框 ⇒ **除黑名单外的其余 common 配置项依旧只能手改 `goetytuner-common.toml`**。解析规则不变：英文逗号分隔、逐段 trim、**大小写敏感**。另：`RUNTIME_BLACKLIST` 与配置黑名单是**并集**，配置**无法解禁**一个已被运行期拉黑的聚晶。
- **0.0.13 部署**：`goetytuner-0.0.13.jar`（**1,753,132 B / md5 `AE59EBFE8CF0F2D09C16453A515CE7F5` / jar 内 102 条目**）已放 `versions\测试\mods\`（旧 0.0.12 已删）。构建在主副本就地 `gradlew clean build` 成功（50 s，仍只有原有 3 条 Forge 弃用警告）；本轮 build 前因 **Gradle 守护进程锁残留**挂起过一次，`gradlew --stop` 清理后恢复正常。⚠️ **jar 的 md5 不可复现**（`MANIFEST.MF` 的 `Implementation-Timestamp`），判断「部署的 jar 对应哪份源码」要**逐条目比对**，别看整体 md5（上一轮的实证见下条）。
- **0.0.11 死亡状态修复**：`applyLockHealth()` 里曾有一段与 `tick()` / `maintainDeathState()` **重复**的「死亡自愈」分支，注释断言它「实体死亡后 `aiStep()` 不执行 ⇒ 不可达」——**该断言是错的**（反编译实证：`LivingEntity.tick()` 里 `aiStep()` 只有 1 处调用、完全无条件；真正被死亡把关的是 `baseTick()` 里的 `isDeadOrDying()` → `tickDeath()`）。该分支实际可达，后果有三：① 不看 `adminKillPending`，把 `/kill` 后门击败（实测 `/kill` 后约 48 ms Boss 带 18 血复活）；② 白吃一档锁血；③ 只写 `deathTime = 0`、不走 `resetDeathAnimation()`，客户端停在死亡动画。现已**删除**该分支，改为在 `applyLockHealth()` 开头对死亡状态直接早退，死亡回弹**唯一**权威实现是 `maintainDeathState()`。详见 `TECHNICAL_SUMMARY.md` §3.11。
- **0.0.12 `CastChannel` 回调成对性已修**（此前文档里记为「仍未修」的那条已知边界）：`startCast` 尾部原先在 `callback.onCastStart(entry)` **之后**才调 `BossWandHelper.logCast(...)`，而 `logCast` 会抛异常、异常逃逸到 `beginCast` 的兜底 `catch`，那里**不补发结束回调** ⇒ `onCastStart` 成了「孤儿回调」，`TunerBoss` 的施法状态计数（`DATA_CAST_STATE` 蹲姿）与立方体类别位掩码**永久 > 0**（对应立方体一直高亮、蹲姿卡住），且因 `FocusEntry` 身份集合幂等去重，该聚晶之后**再也无法计入**、状态无法自愈。修法两道防线：① `logCast` 调到 `onCastStart` **之前**，使其成为 `return` 前最后一步；② 新增 `startEmitted` 标志，在兜底 `catch` 里补发 `onCastFailed` 平衡计数。详见 `TECHNICAL_SUMMARY.md` §3.13（3）。
- **0.0.12 表现层两项**：① **声波涟漪锚定在触发瞬间的坐标**（原先每帧读 Boss 当前位置、会跟着 Boss 跑，而锁血会强制瞬移；现记脚下世界坐标，整条波在固定点上播完，且**只按 34 tick 计时清理**，不再因实体死亡/移除/离开视野提前掐掉）；② **音乐条 HUD 外观重做**（`260×6`→`204×8`、底距 64→62、逐行混色渐变 + 2px 过渡缝、三层柔和投影（四角留空模拟圆角）、阶段文字改**像素符号** `● ● ●`/`●`/`- - - - - -`、刻度与闪烁调淡）。⇒ 原「铺垫/高潮/低谷」文字提示已被替换，lang 的 `info.goetytuner.music.buildup/.climax/.valley` 三键**当前无代码引用**（保留未删）。两项新外观**尚未进游戏画面验收**。
- **0.0.12 部署（上一轮，md5 不可复现的实证）**：`goetytuner-0.0.12.jar`（**1,752,068 B / md5 `66F14DFD926A068AA3284360976B78BB` / jar 内 102 条目**）已放 `versions\测试\mods\`（旧 0.0.11 已删）。⚠️ jar 的 **md5 不可复现**（`MANIFEST.MF` 里的 `Implementation-Timestamp`）：本轮连构三次得 `5A63F73F13C102AAD9249F8B49A9763A`（1,752,059 B）→ `148F359EFD69102FC87971D1B9249270`（1,752,059 B）→ `66F14DFD926A068AA3284360976B78BB`（1,752,068 B），**前两次源码完全相同、连大小都一样而 md5 不同**——判断"部署的 jar 对应哪份源码"要逐条目比对，别看整体 md5。
- **0.0.10 配置迁移（需手动改一次）**：`music.accentParticles` 是**已存在的键**，而 Forge 按 key 合并配置、**不会用新默认值覆盖你已有的 toml** —— 所以老配置里它仍然写着 `true`，会出现「旧粒子冲击环/音符 + 新声波涟漪」同时播放。请在 `config/goetytuner-common.toml` 里手动改成 `accentParticles = false`；新声波开关 `music.accentWave` 是**本次新增的键**，会自动补进老 toml（默认 `true`）。这与「新增键会自动补齐、不必删 toml」是两件事：**新增键会回填，改默认值不会**。本机已在 `versions\测试` 实例的 toml 里手工迁移完毕。**0.0.13 新增的 `music.accentDensityDivisor`（默认 3）同样是新增键 ⇒ 会自动补进老 toml、无需手改。0.0.14 新增的 `phase2_buffs.phase2EffectImmunity`（默认 `true`）也属新增键 ⇒ 同样自动补齐、无需手改。**
- **0.0.10 联机注意**：网络协议由 `1.0` 提升为 `2.0`，且从宽松匹配改为**严格匹配**（`"2.0"::equals`），并新增服务端→客户端包 `SAccentWavePacket`（**通道 id 3**）。**联机双方必须同时更新到 0.0.10 及以上（协议 2.0 在 0.0.11 / 0.0.12 / 0.0.13 / 0.0.14 / 0.0.15 均未变，当前版本 0.0.15）**，否则协议不匹配会被拒绝连接（旧客户端无法正确解码新增的实体同步字段与包）。
- **换/加附属模组后重启即可**：聚晶池是**运行期扫描**（服务器启动时扫 `ForgeRegistries.ITEMS` 里所有 `IFocus`），增删附属只需重启游戏，系统自动适配——配置里多出来的聚晶条目不会被匹配（无害），缺少的走启发式兜底。另外，单个附属的聚晶实现有 bug 也**只会被跳过并拉黑**（启动扫描与施法流程都已逐项/全流程兜底），不会影响其它聚晶，也不会崩服。
- **死亡动画已修复**：原版 `deathTime` **不是同步数据**，服务端复活 Boss 后客户端模型会一直保持"倒下"姿势；0.0.8 增加了专用同步包把它一并复位（同时清掉红色受伤叠加层）。其它模组直接置血等特殊手段也无法再把 Boss 卡在"血量不为 0 却在死亡动画"的状态——锁血未耗尽时连 `remove(KILLED)` 都会被挡下（防止实体被移除后再也回弹不了），但 **`/kill` 是例外**：管理员指令（`DamageTypes.GENERIC_KILL`）会跳过锁血体系的全部保护（身份免疫 / 宽限期免疫 / 致死伤害截断 / 死亡回弹 / `remove(KILLED)` 拦截），能真正击杀 Boss。这是 0.0.9 新增的 `/kill` 后门，详见 `TECHNICAL_SUMMARY.md` §3.11。
- **限伤默认已开启**：`boss.maxHitDamagePercent = 0.25`，即单次命中最多打掉最大生命的 25%（默认 216 血 → 约 54 点）。**实测结论：限伤在锁血阶梯耗尽前基本不改变战斗推进速度**——血量一旦跌破本档地板就会被恢复到该档地板并进入宽限期（宽限期内完全免疫），超出该档的伤害被丢弃，打 1000 点与打 18 点在阶梯上都只推进一档。它主要作为**一道保险**：避免阶梯耗尽后被单次巨额伤害瞬间带走；**设为 `0` 可关闭**。
- **限DPS 默认关闭**：`boss.maxDamagePerSecond = 0`；它按「每秒总吞吐」节流，**同样不改变锁血阶梯的推进速度**（阶梯是按宽限窗口推进、而非按每秒伤害量推进），只在阶梯耗尽后（血量 ≤18、可正常击杀的那段）才可能有意义。**真正决定战斗时长的是 `lockGraceTicks`（每档最短时长，默认 10 tick = 0.5 秒，这是主导旋钮）与档位数（`maxHealth / lockHealthInterval`，默认 216/18 = 12 档）**——想让战斗更长应调这两项，而不是伤害上限。
- **LLM 批量评分会分批请求**：聚晶很多时（本机实测 200 个）按 **60 条/批、顺序请求**并累计结果；若日志出现 `covered only X/Y foci`，说明模型只返回了部分条目（**已应用的结果不会丢失**），再点一次「开始评分」即可补齐。
- 内置音乐 `boss_music_phase1.ogg`（98.27s / BPM120）由 [乌鸦Producer] 提供，音频在原曲基础上有改动；一/二阶段共用同一曲目与同一速度（`music.pitchPhase1`）。
- `config/goetytuner/focus_classification.json` **首次生成时只有 3 条示例条目**，其余聚晶靠启发式分类兜底。本机已用 LLM 批量评分写入 **277 条**（其中当前游玩实例的聚晶基本覆盖，仅 2 个 `goetyiron:*_focus` 走启发式兜底）。可在游戏内 Mods 菜单 → Config →「开始评分（大模型）」重新批量生成——它会扫描**当前实例实际注册**的聚晶，是让评分表与整合包精确对齐的正规做法（需自备 OpenAI 兼容 API Key；端点见下条）。
  > 口径说明：**脚本统计**（扫 jar 内 lang）与**模组运行期统计**（已注册且 `getSpell() != null` 的 `IFocus`）会略有差异——本机前者 208、后者 200；两个数字都对，引用时请注明口径。
- **LLM 评分的端点默认是 OpenAI**（`llm.apiUrl = https://api.openai.com/v1/chat/completions`）。**中国大陆网络需在配置里改为 `https://api.deepseek.com/v1/chat/completions` 并把 `llm.model` 改为 `deepseek-chat`**，否则会连接超时；失败时界面会显示带目标 URL 的报错，可据此判断端点是否正确。
- **提示词框已预填标准提示词**，可直接在其基础上修改；清空则恢复默认；若与标准提示词完全一致则不会写入配置文件。
- 升级法杖（仪式召唤专属）：只有通过仪式召唤的 Boss 死亡才掉落带「调律」加成的法杖；刷怪蛋/指令生成的 Boss 无原始法杖快照，不掉落。
- 死亡/受击音效尚未实现（`getAmbientSound/getDeathSound/getHurtSound` 均返回 null）。
