# Goety Tuner · 调律师

> **0.0.12 修复**：本轮三件事 —— 功能改动落在 3 个 Java 文件（`client/AccentWaveRenderer`、`client/MusicBarHud`、`entity/ai/CastChannel`），另 `entity/TunerBoss` **只动了一处方法 javadoc**（无行为变化），加版本号；**无新增类/贴图/配置项**（仍 61 项 / 10 段，`music` 12 项；`.java` 文件数仍 45）。① **径向声波涟漪改为锚定在触发瞬间的坐标**：原实现只存 `startTick`、渲染时每帧读 Boss 当前位置 ⇒ 涟漪**跟着 Boss 跑**；而调律师**每次锁血都会强制瞬移**（`onLockTriggered` → `tryTeleportNear`），跟着跑会让「由内向外荡开的声波」被拖着走，观感完全不对。现触发时记录**脚下世界坐标**（新增私有 `record Wave(long startTick, double x, double y, double z)`），整条波在这个固定点上播完；清理**只按时间**（`DURATION = 34 tick`），**不再**因为 Boss 死亡/被移除/离开视野就提前掐掉波（波已与实体无关，应当播完）。② **音乐条 HUD 外观重做**（用户反馈「太突兀」，四项一起改）：尺寸 `260×6` → **`204×8`**（底部边距 64 → 62）；分段由「硬边纯色块」改为**逐行混色**渐变（新 `fillSegment`）+ 分段交界处 2px 半透明过渡缝（新 `drawSeam`）；单一硬矩形底 `0xAA000000` → **三层由外向内渐深的柔和投影**、最外层四角留空以模拟圆角（新 `drawSoftBackdrop`）；条上方的「铺垫/高潮/低谷」**文字**改为**像素绘制**的 `● ● ●`（高潮）/ `●`（铺垫）/ `- - - - - -`（低谷）（新 `drawPhaseIndicator` + `drawDot`）——**本客户端字体没有 U+26AA 字形**（`include/unifont.json` 的 providers 是空数组、jar 内无任何 ttf、也无 unicode 字形页；`options.txt` 只启用 `vanilla`+`mod_resources`，mods 里也没有 jar 提供字形页），直接写 `⚪` 会显示成空白方块，像素画必定可见且更隐晦；重音刻度纯白 `0xFFFFFFFF` → 半透明白 `0xB8FFFFFF`、闪烁峰值 `0xC0` → `0x88`。③ **`CastChannel` 回调成对性修复**（此前文档记为「仍未修」的已知边界）：`startCast` 尾部原本是 `onCastStart` 之后紧跟 `logCast`，而 `logCast` **会抛异常**、异常逃逸到 `beginCast` 的兜底 `catch`，那里**不补发结束回调** ⇒ `onCastStart` 成为「孤儿回调」，`TunerBoss` 的施法状态计数（`DATA_CAST_STATE` 蹲姿）与 0.0.10 新增的立方体类别位掩码会**永久 > 0**（立方体一直高亮、蹲姿卡住），又因身份集合幂等去重，该聚晶之后**再也无法计入**、状态无法自愈。修复两道防线：① 把 `logCast` 调到 `onCastStart` **之前**，使 `onCastStart` 成为 `return` 前最后一步（**结构上不可能**再被后续代码打断）；② 新增 `startEmitted` 标志（`beginCast` 开头重置），在兜底 `catch` 里补发 `onCastFailed` 让计数平衡。0.0.10 美术项（参考身体贴图 / 8 阶披风 / 悬浮立方体 / 径向声波 / 原版刷怪蛋）仍待游戏画面验收，见 [ART_ASSETS_REPORT.md](ART_ASSETS_REPORT.md)。

诡厄巫法（Goety）附属Boss模组 —— 1.20.1 Forge。

人形无头指挥家「调律师」，以音乐（铺垫/高潮/低谷）驱动战斗节奏，
轮番演奏诡厄巫法及其附属注册的全部聚晶，并带有自学习式聚晶评分系统。

**当前版本：0.0.12**（MC 1.20.1 Forge 47.3.22 / Goety 2.5.56.5 附属）

## 文档索引（按优先级）

| 文档 | 定位 | 时效 |
|---|---|---|
| **[TECHNICAL_SUMMARY.md](TECHNICAL_SUMMARY.md)** | 技术现状权威文档：架构/核心系统/工程红线/版本时间线 | **最新**（已覆盖至 0.0.12 / 第 50 轮，最新一轮；覆盖轮次口径以该文档为准） |
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
- `entity/` Boss 实体 `TunerBoss`（核心，2001 行）、`MusicController` 乐谱与阶段、`BossPhase`
- `entity/ai/` `CastChannel` 施法通道（前摇/锁池/朝向钉死/异常自愈）
- `focus/` 聚晶池系统（分类/评分/轮盘/冷却池/黑名单/LLM与启发式分类/主手杖装配）
- `combat/` 动态评分（伤害 DPS 200tick 归因、召唤物输出/生存二维追踪）
- `ritual/` 仪式召唤 `TunerSummonRitual` + `goety:ritual_factory` 注册 + 法杖升级事件
- `command/` `/goetytuner tune` 命令
- `config/` `TunerCommonConfig`（61 项配置 / 10 个 section）
- `network/` `SMusicSyncPacket`（进度/分段/重音/演奏状态/速度）、`SShakePacket`、`SEntityRevivePacket`（死亡动画复位）、`SAccentWavePacket`（声波涟漪触发，**通道 id 3**，限 `PLAY_TO_CLIENT`）
- `client/` 音乐播放器 `BossMusicManager`、节奏条 HUD（`MusicBarHud`：204×8、逐行混色分段 + 柔和投影 + 像素阶段符号）、配置界面 + Toast、相机震颤、`ClientDeathAnimation`（死亡动画复位）、`AccentWaveRenderer`（世界空间声波涟漪，锚定触发瞬间坐标、只按时间清理，`RenderLevelStageEvent.Stage.AFTER_PARTICLES`）
- `client/render/` 原版 HumanoidModel 渲染 + 自定义披风层 `TunerCapeLayer`/`TunerCapeModel` + 悬浮立方体层 `TunerOrbLayer`/`TunerOrbModel`（模型层 `goetytuner:tuner_orb`，无 GeckoLib）
- 资源：`assets/goetytuner/`（中英 lang、贴图、内置 boss 音乐 ogg）、`data/goety/recipes/tuner_boss_ritual.json`

## 已知事项（0.0.12）

- **0.0.11 死亡状态修复**：`applyLockHealth()` 里曾有一段与 `tick()` / `maintainDeathState()` **重复**的「死亡自愈」分支，注释断言它「实体死亡后 `aiStep()` 不执行 ⇒ 不可达」——**该断言是错的**（反编译实证：`LivingEntity.tick()` 里 `aiStep()` 只有 1 处调用、完全无条件；真正被死亡把关的是 `baseTick()` 里的 `isDeadOrDying()` → `tickDeath()`）。该分支实际可达，后果有三：① 不看 `adminKillPending`，把 `/kill` 后门击败（实测 `/kill` 后约 48 ms Boss 带 18 血复活）；② 白吃一档锁血；③ 只写 `deathTime = 0`、不走 `resetDeathAnimation()`，客户端停在死亡动画。现已**删除**该分支，改为在 `applyLockHealth()` 开头对死亡状态直接早退，死亡回弹**唯一**权威实现是 `maintainDeathState()`。详见 `TECHNICAL_SUMMARY.md` §3.11。
- **0.0.12 `CastChannel` 回调成对性已修**（此前文档里记为「仍未修」的那条已知边界）：`startCast` 尾部原先在 `callback.onCastStart(entry)` **之后**才调 `BossWandHelper.logCast(...)`，而 `logCast` 会抛异常、异常逃逸到 `beginCast` 的兜底 `catch`，那里**不补发结束回调** ⇒ `onCastStart` 成了「孤儿回调」，`TunerBoss` 的施法状态计数（`DATA_CAST_STATE` 蹲姿）与立方体类别位掩码**永久 > 0**（对应立方体一直高亮、蹲姿卡住），且因 `FocusEntry` 身份集合幂等去重，该聚晶之后**再也无法计入**、状态无法自愈。修法两道防线：① `logCast` 调到 `onCastStart` **之前**，使其成为 `return` 前最后一步；② 新增 `startEmitted` 标志，在兜底 `catch` 里补发 `onCastFailed` 平衡计数。详见 `TECHNICAL_SUMMARY.md` §3.13（3）。
- **0.0.12 表现层两项**：① **声波涟漪锚定在触发瞬间的坐标**（原先每帧读 Boss 当前位置、会跟着 Boss 跑，而锁血会强制瞬移；现记脚下世界坐标，整条波在固定点上播完，且**只按 34 tick 计时清理**，不再因实体死亡/移除/离开视野提前掐掉）；② **音乐条 HUD 外观重做**（`260×6`→`204×8`、底距 64→62、逐行混色渐变 + 2px 过渡缝、三层柔和投影（四角留空模拟圆角）、阶段文字改**像素符号** `● ● ●`/`●`/`- - - - - -`、刻度与闪烁调淡）。⇒ 原「铺垫/高潮/低谷」文字提示已被替换，lang 的 `info.goetytuner.music.buildup/.climax/.valley` 三键**当前无代码引用**（保留未删）。两项新外观**尚未进游戏画面验收**。
- **0.0.12 部署**：`goetytuner-0.0.12.jar`（**1,752,068 B / md5 `66F14DFD926A068AA3284360976B78BB` / jar 内 102 条目**）已放 `versions\测试\mods\`（旧 0.0.11 已删）。⚠️ jar 的 **md5 不可复现**（`MANIFEST.MF` 里的 `Implementation-Timestamp`）：本轮连构三次得 `5A63F73F13C102AAD9249F8B49A9763A`（1,752,059 B）→ `148F359EFD69102FC87971D1B9249270`（1,752,059 B）→ `66F14DFD926A068AA3284360976B78BB`（1,752,068 B），**前两次源码完全相同、连大小都一样而 md5 不同**——判断"部署的 jar 对应哪份源码"要逐条目比对，别看整体 md5。
- **0.0.10 配置迁移（需手动改一次）**：`music.accentParticles` 是**已存在的键**，而 Forge 按 key 合并配置、**不会用新默认值覆盖你已有的 toml** —— 所以老配置里它仍然写着 `true`，会出现「旧粒子冲击环/音符 + 新声波涟漪」同时播放。请在 `config/goetytuner-common.toml` 里手动改成 `accentParticles = false`；新声波开关 `music.accentWave` 是**本次新增的键**，会自动补进老 toml（默认 `true`）。这与「新增键会自动补齐、不必删 toml」是两件事：**新增键会回填，改默认值不会**。本机已在 `versions\测试` 实例的 toml 里手工迁移完毕。
- **0.0.10 联机注意**：网络协议由 `1.0` 提升为 `2.0`，且从宽松匹配改为**严格匹配**（`"2.0"::equals`），并新增服务端→客户端包 `SAccentWavePacket`（**通道 id 3**）。**联机双方必须同时更新到 0.0.10 及以上（协议 2.0 在 0.0.11 / 0.0.12 均未变，当前版本 0.0.12）**，否则协议不匹配会被拒绝连接（旧客户端无法正确解码新增的实体同步字段与包）。
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
