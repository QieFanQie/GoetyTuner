# Goety Tuner · 调律师

诡厄巫法（Goety）附属Boss模组 —— 1.20.1 Forge。

人形无头指挥家「调律师」，以音乐（铺垫/高潮/低谷）驱动战斗节奏，
轮番演奏诡厄巫法及其附属注册的全部聚晶，并带有自学习式聚晶评分系统。

**当前版本：0.0.7**（MC 1.20.1 Forge 47.3.22 / Goety 2.5.56.5 附属）

## 文档索引（按优先级）

| 文档 | 定位 | 时效 |
|---|---|---|
| **[TECHNICAL_SUMMARY.md](TECHNICAL_SUMMARY.md)** | 技术现状权威文档：架构/核心系统/工程红线/版本时间线 | **最新**（已覆盖至 0.0.7，最新一轮；覆盖轮次口径以该文档为准） |
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

源码包 `com.tiaolvshi.goetytuner`（39 个类）：

- `init/` 注册（实体/物品/音效/属性/事件），含链锤实体级拦截
- `entity/` Boss 实体 `TunerBoss`（核心，1671 行）、`MusicController` 乐谱与阶段、`BossPhase`
- `entity/ai/` `CastChannel` 施法通道（前摇/锁池/朝向钉死/异常自愈）
- `focus/` 聚晶池系统（分类/评分/轮盘/冷却池/黑名单/LLM与启发式分类/主手杖装配）
- `combat/` 动态评分（伤害 DPS 200tick 归因、召唤物输出/生存二维追踪）
- `ritual/` 仪式召唤 `TunerSummonRitual` + `goety:ritual_factory` 注册 + 法杖升级事件
- `command/` `/goetytuner tune` 命令
- `config/` `TunerCommonConfig`（60 项配置 / 10 个 section）
- `network/` `SMusicSyncPacket`（进度/分段/重音/演奏状态/速度）、`SShakePacket`
- `client/` 音乐播放器 `BossMusicManager`、节奏条 HUD、配置界面 + Toast、相机震颤
- `client/render/` 原版 HumanoidModel 渲染 + 自定义披风层（无 GeckoLib）
- 资源：`assets/goetytuner/`（中英 lang、贴图、内置 boss 音乐 ogg）、`data/goety/recipes/tuner_boss_ritual.json`

## 已知事项（0.0.7）

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
