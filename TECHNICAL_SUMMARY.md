# Goety Tuner（调律师）技术摘要

> **0.0.13 修复**：本轮改了 **7 个 Java 文件** + 版本号，**只改代码、不动任何贴图/资源**（`.java` 文件数仍 45、jar 条目仍 102）；**新增 1 个配置项** `music.accentDensityDivisor` ⇒ 配置 **61 → 62 项**（`music` 段 12 → 13，section 数仍 10）。① **修「玩家被击杀复活后（未走出索敌范围）背景音乐丢失」**：`BossMusicManager` 原先只以静态字段 `music != null` 当作「在播」，**从不与声音引擎核对**，而死亡/复活会让引擎把循环实例悄悄摘除（RECORDS 音量为 0 / channel 被停止 / `SoundEngine.reload()→destroy()→stopAll()` / `play()` 在未 loaded 时静默返回），字段却仍非 null ⇒ 只要服务端 `playing` 一直为 true（没脱战）`startMusic` **永不重入** = **永久静音**；且 `onPlaySound` 同样只看字段 ⇒ **连原版背景音乐也一起被永久取消**（用户症状「整个 BGM 都没了」）。改为每 tick 用 **`SoundManager.isActive(music)`**（1.20.1 自带 API）核实实例是否真的在响，失效即清空字段、下一 tick 自动重建（**自愈**），并加 **10 tick 防抖**；`onPlaySound` 判据同步改为 `music != null && isActive(music)`。详见 §3.14(1)。② **重音标记滚动平滑 + 高潮标记改小长条**（`MusicBarHud`）：`fill()` 只能落在整数像素、而刻度是 1~2px 细条，取整后**逐像素跳动**；改为**亚像素覆盖**（小数部分按比例摊到相邻两列，亮度重心连续移动）；高潮的「中」字（贯通竖线 + 空心方框）改为**小长条**（高潮 2×6 / 低谷 1×6 / 铺垫 1×4，竖向居中）。③ **涟漪多波共存**（`AccentWaveRenderer` 的 `Map` 改 `List` + `MAX_WAVES = 32`）：二阶段进场重音每 5 tick 一发，原先后一发**顶掉**前一发、只看到一条波反复重播。④ **重音抽稀**：新增 `music.accentDensityDivisor`（默认 **3**、范围 1~9）在**服务端加载乐谱时**「每 N 个保留 1 个」⇒ HUD 刻度（经 `SMusicSyncPacket` 全量同步）/ 击退 / 涟漪 / 提示音**一起**变稀疏、客户端零改动（默认乐谱 37 → **13** 个重音）。⑤ **立方体高亮改「向白插值」+ 两层自发光外壳当发光**（`TunerOrbLayer`）：原 `min(1, color*(1+glow*0.8))` 因各分量已接近 1 而被截断、几乎看不出高亮；发光**不能用原版描边**（MC 的发光是**整实体级** framebuffer 后处理 `OutlineBufferSource`，无法只描一颗立方体），故改用 `entityTranslucentEmissive` 外壳。⑥ **药水可观测性**：8 处 `addEffect` 的 boolean 返回值原先全被丢弃 ⇒ 被 `canBeAffected` / `MobEffectEvent.Applicable` 拒绝时静默失效；新增 `applySelfEffect` 打 WARN（已用于 boss 自身 4 处），并完成药水现状审计（详见 §3.14(6)）。⑦ 配置侧把 **`goety:killing_focus`（索命聚晶）** 加入 `focus.blacklist`——**这是配置侧改动、不在 jar 内**；且 `focus.blacklist`（乃至整个 `goetytuner-common.toml`）**无法在游戏内配置界面修改**。0.0.10 美术项与 0.0.12 新外观仍待游戏画面验收，见 [ART_ASSETS_REPORT.md](ART_ASSETS_REPORT.md)。

> 版本：0.0.13 ｜ 整理日期：2026-09-20 ｜ 覆盖轮次：第 1~51 轮
> 项目：诡厄巫法(Goety)附属 Boss 模组 —— 「调律师」，一位指挥灵魂能量交响乐团的指挥家。

---

## 一、项目概览

| 项 | 值 |
|---|---|
| 模组 id / 名称 | `goetytuner` / Goety Tuner: The Tuner |
| MC / Forge | 1.20.1 / 47.3.22（Gradle 8.1.1） |
| 映射 | official（Mojang 名，reobf 后 SRG 发布） |
| 语言 | Java 17（JDK 17：`C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`） |
| 依赖 mod | goety 2.5.56.5、patchouli、curios-forge、configured（均为 dev 坐标式依赖） |
| 作者 | toniat0, vibe-coding（https://github.com/QieFanQie/） |
| 协议 | MIT |
| 源码规模 | 45 个 Java 源文件（约 378 KB / 387,433 B），包根 `com.tiaolvshi.goetytuner` |

**定位**：为 Goety 提供一位可召唤的 Boss「调律师」。Boss **主手常驻一把实体法杖**
`goety:dark_wand`（`TunerBoss` 构造函数 `setItemInHand(MAIN_HAND, ModItems.DARK_WAND)`；
从存档读回时若主手不是 `IWand` 会补发——Goety 的 `SoulUsingItemHandler.get` 要求
`ITEM_HANDLER` capability，缺失会崩服）。施法时由 `BossWandHelper.installFocus` 把从
全体聚晶池抽签得到的聚晶装进这把杖再释放，并按音乐(乐谱/重音)驱动三阶段战斗节奏。

---

## 二、总体架构

### 2.1 包结构

```
com.tiaolvshi.goetytuner
├── GoetyTuner.java                  # 主入口，@Mod，总线注册
├── init/
│   ├── ModEntities.java             # 实体注册（TunerBoss）
│   ├── ModItems.java                # ForgeSpawnEggItem 注册
│   ├── ModSounds.java               # 音效注册（音乐走 RECORDS 类）
│   ├── ModEvents.java               # 实体加入拦截 / 掉落 / 召唤物归属等
│   └── ModBusEvents.java            # MOD 总线：属性、图层、屏幕
├── entity/
│   ├── TunerBoss.java               # Boss 主体（2020 行核心类：AI 状态机 / 阶段 / 战斗数值）
│   ├── BossPhase.java               # 三阶段枚举（铺垫/高潮/低谷）
│   ├── MusicController.java         # 服务端乐谱推进 / 阶段转换 / 重音触发
│   └── ai/CastChannel.java          # 施法通道（前摇/高潮并行通道/瞬发）
├── focus/
│   ├── FocusPoolManager.java        # 聚晶池构建 / 抽签 / 锁池归还 / 黑名单
│   ├── FocusEntry.java              # 聚晶条目（id/分类/标签）
│   ├── FocusCategory.java           # ATTACK / DEFENSE / SUMMON / OTHER 四类枚举
│   ├── FocusClassifier.java         # 启发式分类（lang key + 生物名词兜底）
│   ├── LLMClassifier.java           # 可选 LLM 分类（人工标注优先）
│   ├── FocusClassificationConfig.java # 分类配置 + lang 缓存（服务端读 jar）
│   └── BossWandHelper.java          # Boss 用魔杖封装（伤害/耗蓝规则）
├── combat/
│   ├── CombatEvents.java            # 伤害事件（近战易伤/召唤物伤害归因）
│   ├── DamageScoreTracker.java      # 输出榜（可投掷物）
│   └── SummonScoreTracker.java      # 召唤榜
├── client/
│   ├── BossMusicManager.java        # 客户端循环音乐实例（FORGE 总线；0.0.13 起每 tick 用 SoundManager.isActive 核实实例、失效即自愈重建）
│   ├── MusicStateClient.java        # 音乐进度本地平滑推进（×speed）
│   ├── MusicBarHud.java             # 音乐条 HUD 204×8（逐行混色分段 + 2px 过渡缝 / 三层柔和投影 / 重音刻度（0.0.13 亚像素覆盖平滑滚动，高潮 2×6、低谷 1×6、铺垫 1×4 小长条）/ 指针 / 像素阶段符号）
│   ├── ClientCameraShake.java       # 重音镜头震动
│   ├── TunerConfigScreen.java       # Configured 配置屏入口（多行提示词框，预填标准模板）
│   ├── TunerToast.java              # 客户端 Toast（LLM 评分/命令结果提示）
│   ├── ClientSetup.java             # 图层定义注册 / 渲染器绑定
│   ├── ClientDeathAnimation.java    # 客户端死亡动画复位（deathTime/hurtTime/姿态，由 SEntityRevivePacket 触发）
│   ├── AccentWaveRenderer.java      # 世界空间声波涟漪渲染（0.0.10 新增，AFTER_PARTICLES；0.0.12 起锚定触发瞬间坐标、只按时间清理；0.0.13 起活跃表由 Map 改 **List**，同一实体多波共存、MAX_WAVES=32 兜底）
│   └── render/                      # TunerRenderer/TunerModel/TunerCape*/TunerOrb*（悬浮立方体层，0.0.10；0.0.13 高亮改向白插值 + 两层 entityTranslucentEmissive 自发光外壳）
├── network/
│   ├── TunerNetwork.java            # 通道注册（id 0/1/2/3，协议 2.0 严格匹配）
│   ├── SMusicSyncPacket.java        # 乐谱/进度/speed 同步（服务端→客户端）
│   ├── SShakePacket.java            # 镜头震动同步
│   ├── SEntityRevivePacket.java     # 死亡动画复位同步（0.0.8 新增，通道 id 2，发给所有追踪者）
│   └── SAccentWavePacket.java       # 声波涟漪触发同步（0.0.10 新增，通道 id 3，限 PLAY_TO_CLIENT）
├── config/
│   └── TunerCommonConfig.java       # 全部可调参数（common toml，62 项 / 10 个 section）
├── command/
│   └── TunerCommands.java           # /goetytuner 命令（tune 等）
└── ritual/
    ├── ModRituals.java              # goety:ritual_factory 仪式召唤注册
    ├── TunerSummonRitual.java       # 调律师召唤仪式本体
    └── WandUpgradeEvents.java       # 法杖快照制升级事件
```

### 2.2 数据流（一图流）

```
服务端                               客户端
TunerBoss.aiStep ─┐
  ├─ 乐谱推进 MusicController.tick ──→ SMusicSyncPacket(进度/speed/分段) ──→ MusicStateClient.smoothed()
  ├─ 重音命中 onAccent ──────────────→ 击退+粒子+音效（服务端）  ／        └─→ MusicBarHud 渲染
  │                                   ├→ SShakePacket(震动) ──→ ClientCameraShake
  │                                   └→ SAccentWavePacket(声波, id3) ──→ AccentWaveRenderer
  ├─ 抽签 CastChannel ── FocusPoolManager.draw → spell.mobSpellResult ─→ Goety 法术生效
  └─ 阶段转换 enterPhase2 ── 自buff/回血/进场连发 ──→ 客户端阶段切换（HUD 分段配色；音频不换速）
```

---

## 三、核心系统设计

### 3.1 Boss AI 与战斗状态机

- **索敌与仇恨**：`targetRange = 96`（FOLLOW_RANGE 常量同步）；`hasAggro` 门控 AI 主循环；
  脱战边缘检测（玩家跑远/死亡）→ 回血 + 乐谱重启对齐。
- **三阶段节奏**（由音乐驱动而非纯计时器）：
  - 一阶段：铺垫 → 高潮（三并行施法通道，聚晶池高权重抽签）→ 低谷（清正面效果）→ 铺垫 循环；
  - 二阶段：高潮结束后低谷**替换为铺垫**（节奏加快、无低谷喘息）；
    每 40tick 自 buff（原版力量 等级2/5 + 重振 5/8，`lockMark≥10` 升级；详见 §3.6）；
    `lockMark` 7~12 按档位回血。
- **瞬移走位**：`tickTeleport`（玩家目标）与 `tickTeleportNonPlayer`（无参战玩家时追非玩家目标）
  独立计时器；**瞬移判定位于 `hasAggro` 仇恨门控之外、全程每 tick 判定**（第 22 轮修复：
  原先只在铺垫期与二阶段高潮期调用，导致一阶段高潮/低谷期不追击，观感即"瞬移追击失效"）；
  触发仍受距离区间 (teleportMinDistance, teleportChaseMaxDistance) 与间隔限制。
- **瞬移逐跳追击（v0.6.0）**：`tryTeleportHop` 沿自身→目标水平连线每跳 8 格（间隔 10tick），
  链内保持追击、链结束恢复冷却；落点 ±3 高度找可站立方块（可爬坡），空中目标兜底
  「两格可通行」空中位；水平距 <4 格不跳（防目标头顶抖动）。
- **二阶段弧形走位**：施法 beginCast 成功后按 `phase2BuildupArcEveryN`(3) 触发
  `tryTeleportFrontArc`——朝向绕 Y ±75° × 半径 6 格扫描可站立点。
- **施法朝向钉死（v0.6.0/0.6.1）**：`snapTowardTarget(boss)` 三调用点——
  ① 前摇期每 tick；② beginCast 调 `spell.startSpell` 前（关键，VoidRift/FlameStrike/
  AbyssalBeam/ChipRain 在 startSpell 内沿视线 rayTrace 生成实体）；③ instantCast 结算前。
  同时写 `yRotO/xRotO`（防 partialTicks 插值读旧角度）。方向型法术方向源仅三种
  （getViewVector / yHeadRot / yBodyRot），全量覆盖（125+109 法术逐一反编译验证）。
- **前摇下蹲**：施法中且非嘲讽逃跑 → `setPose(CROUCHING)`（复用 TunerModel 蹲姿映射）；
  嘲讽触发加「非施法中」门控，施法蹲姿优先。
- **嘲讽彩蛋（一阶段专属）**：`tickTaunt` 状态机——待机 / 蹲起循环 / 反向跳跃逃跑 / 蹲望。
  触发条件：**非二阶段 + 有存活仇恨目标 + 距离 (6,12] 格 + 冷却结束(200 tick) +
  非施法中 + 每 tick 0.002 概率**。命中后二选一：70% 蹲起 4 轮后反向跳跃逃跑
  （26 tick / 5 格，`setDeltaMovement` 不走导航，避免与高潮 navigation.stop 打架）、
  30% 蹲望 40~70 tick。常量：蹲/站各 4 tick、逃跑 26 tick、冷却 200 tick。
  **施法中不触发**（第 31 轮加的门控，防嘲讽姿态与施法瞄准蹲姿互相覆盖、逃跑朝向带偏施法方向）。

### 3.2 施法系统（Goety 聚晶集成）

- **聚晶池**：扫描所有已注册 Focus（Goety 本体 + 附属），按 `FocusClassifier` 分类
  （`FocusCategory`：**ATTACK / DEFENSE / SUMMON / OTHER 四个功能聚晶池**）→ 各阶段按权重
  抽签（高潮攻击池权重 222）。
- **分类决策链**（手动/LLM 配置条目 → `spell instanceof ISummonSpell` 权威判定 →
  describe() 双后缀 .info/.desc → 生物名词兜底）。
- **抽签语义坑**：`FocusPoolManager.draw()` **只返回不移除** → 多通道会重复抽同一聚晶；
  必须 beginCast 后 `removeEntry` 锁池、interrupt 时 `returnEntry` 归还（无冷却）。
- **施法窗口**：`castDuration` 默认极大（300 秒=完整蓄力），Boss 必须
  `min(raw, MAX_CAST_WINDOW_TICKS=50)` 封顶；DarkWand 提前松手是合法释放路径。
- **黑名单双层防护**：配置 `focus.blacklist`（String 容错解析：单 id/逗号分隔/数组写法
  自动归一化，实时解析不缓存）+ 运行期 `RUNTIME_BLACKLIST` 自愈
  （beginCast/instantCast/tick 三处 try-catch → 运行时屏蔽 + 归还 + 复位 + ERROR 日志）。
  默认内置 `goetytwilight:destruction_focus`。
- **实体级拦截（治本）**：`ModEvents.onEntityJoinLevel` 按注册 id 拦截
  `goetytwilight:destruction` 且 owner 为 null/非 Player → discard。
  （链锤崩溃根因：`DestructionEntity.onHitBlock` 把 null 传给 BreakEvent 构造器 NPE——
  崩溃在实体 tick 阶段，黑名单拦不住已存在的实体。）
- **施法自愈**：`TunerCastCallback.onCastFailed` 默认接口 + 计数器化 `DATA_CAST_STATE`
  （activeWarmups++/--，归零才清 0）——修复并行高潮通道误清 + 异常路径永久卡 1。

### 3.3 音乐系统（第二十~二十一轮定稿）

- **音频**：`boss_music_phase1.ogg` 内置（用户曲目 98.27s / BPM120 / G 大调转码）；
  一/二阶段共用同一音频、同一速度（第 21 轮撤销了二阶段变速，全程单一
  `music.pitchPhase1`，默认 1.0；配置项 `pitchPhase2` / `MUSIC_PITCH_PHASE2` 已删除）。
  WAV→OGG 必须**分块流式写入**（soundfile 一次性 sf.write 大文件崩溃）。
- **客户端循环实例**（BossMusicManager，FORGE 总线 Dist.CLIENT）：
  `SimpleSoundInstance(RECORDS, looping=true, Attenuation.NONE, relative=true)`；
  ClientTickEvent 管生命周期（实体不在/死亡/同步超时 3s → 停播）；
  PlaySoundEvent 拦截原版背景音乐（setSound(null)）+ 起播时压掉当前曲目。
  解决：阶段切换重叠 / 原版音乐重叠 / Boss 死后不停 三个问题。
- **乐谱**：分段 80/960/700/225（铺垫/高潮/低谷/铺垫 = 1965tick 无缝循环）；
  重音 37 个（高潮每 4 拍 40t，低谷/铺垫每 8 拍 80t，强制命中 80/1040/1740 转换点 + 循环点 0）。
  ⚠️ **0.0.13 起服务端加载乐谱时按 `music.accentDensityDivisor`（默认 3）抽稀**：`music_score.json` 文件里**仍是 37 个**，
  但**运行时生效 13 个**（每 3 个保留 1 个，INFO `Accent thinning x3: 37 -> 13 accents`）；HUD 刻度经 `SMusicSyncPacket`
  **全量同步**，所以 HUD 刻度 / 击退 / 涟漪 / 提示音**一起**变稀疏、客户端零改动，详见 §3.14(4)。
- **速度化时间轴**：`MusicController.progressF` 浮点推进 ×=pitch（getSpeed）；
  speed 随 SMusicSyncPacket 下发，客户端 `smoothed()` 本地推进 ×speed；
  跨多 tick 时 `crossedAccent` 逐 tick 查重音（Set，含翻页回绕两段检查）。
- **HUD**（0.0.12 外观重做，见 §3.13(2)）：**204×8** 音乐条（底距屏幕底 62），分段配色（铺垫蓝 0xFF5B9BE0 / 高潮橙 0xFFF26A4B / 低谷紫 0xFFB068E8）
  改为**逐行混色**渐变（上半向白提亮、下半向黑压暗）+ 分段交界 2px 半透明过渡缝，底色为**三层由外向内渐深的柔和投影**（最外层四角留空模拟圆角）；
  指针与二阶段判定线 5px；重音刻度改半透明白 `0xB8FFFFFF`；
  **0.0.13 起刻度位置改用亚像素覆盖**（小数部分按比例摊到相邻两列，亮度重心随浮点位置连续移动 ⇒ 滚动平滑、不再逐像素跳动，调用点不再预先取整），
  高潮标记由「中」字（贯通竖线 + 空心方框，8px 条高上又粗又花）改为**小长条**（高潮 2×6 最亮 / 低谷 1×6 / 铺垫 1×4，全部竖向居中，只用「宽 × 高 × 亮度」区分强度）；
  阶段提示改为**像素符号** `● ● ●`（高潮）/ `●`（铺垫）/ `- - - - - -`（低谷）——本客户端字体无 U+26AA 字形，故不用 `⚪` 字符；
  本地越线检测触发亮黄描边闪烁 8 帧渐隐（峰值 alpha 0xC0 → 0x88，免额外网络包）。
- **重音特效**（服务端 onAccent）：非对称径向声波涟漪（0.0.10 起默认开启 `accentWave=true`，
  参数与渲染实证见 §3.12） + 原版紫水晶音（阶段差异化音调 0.9/1.4/0.6）；旧的 END_ROD 冲击环 +
  NOTE 音符粒子默认关闭（`accentParticles=false`，键保留作手动兼容选项）；二阶段进场连发 6 次
  `accentKnockbackPulse`（击退+冲击环+音效，每 5tick 一发，音调递升 0.8+0.15i）。
  **0.0.13 起涟漪活跃表由 `Map` 改为 `List`**：连发的每一发各成一条波、各自计时，呈现层叠外扩（不再互相顶掉），见 §3.14(3)。

### 3.4 渲染（原版模型方案，无 GeckoLib）

- Boss 用原版 `HumanoidMobRenderer` + 自定义贴图（PIL / 图像脚本生成，anaconda python 才有 PIL）：
  - `textures/entity/tuner.png` 64×64：身体按用户参考图重画——紫黑外套、浅紫 V 领、紫色内衬/手套/裤靴、
    青蓝链饰（**0.0.10**）；**头部区（y 0..15 全宽）与 0.0.9 逐像素完全一致**（已独立脚本验证 0 像素差异），
    帽子(hat)层贴图透明、不显示。
  - `textures/entity/tuner_cape.png` 64×32：**0.0.10** 改为 **8 条、每条 2 像素高**的逐层色带
    `#1E0D32 #38204D #523367 #6C4882 #875E9E #A078BA #BA96D8 #D4B6F2`，自上而下亮度单调递增。
- 自定义披风层：`TunerCapeModel`（EntityModel 子类，`LAYER_LOCATION` 必须
  **ModelLayerLocation**，须实现 renderToBuffer 委托）；`TunerCapeLayer` 用
  `body.translateAndRotate` 跟随躯干，`Axis.XP` 正角度 = 下摆向身后(+z)摆。
- **悬浮立方体层（0.0.10 新增）**：`TunerOrbModel`（`ModelLayerLocation` = `goetytuner:tuner_orb`，
  模型层定义由 `ClientSetup#onRegisterLayers` 注册）+ `TunerOrbLayer`（由 `TunerRenderer` 挂载）。
  3 颗边长 **0.30 格**的立方体，贴图 `textures/entity/tuner_orb.png` 16×16（中性灰白，颜色由代码乘算）；
  公转半径 1.05 格、离脚约 1.20 格、上下浮动 ±0.06 格；三颗相位差 120°，公转 80 tick(4s)、
  自转 Y 40 tick(2s) / X 60 tick(3s)；颜色红 `#FF3B30`(攻击)/蓝 `#2FA8FF`(防御)/灰 `#C8C8C8`(召唤)，
  施法高亮由实体同步的**位掩码**驱动（见 §3.12）。
  **0.0.13 起高亮改为额外叠加 `glow*0.4` 逐通道向白靠拢**（原先 `min(1, color*(1+glow*0.8))` 因各分量已接近 1
  而被 `min` 截断、几乎看不出变化），并叠**两层自发光外壳**（1.30×/alpha 0.36 + 1.35×/alpha 0.18，
  `RenderType.entityTranslucentEmissive`）作为「发光」，见 §3.14(5)。
- 刷怪蛋（0.0.10 改）：模型 JSON 只写 `{"parent": "minecraft:item/template_spawn_egg"}`（方案 1），
  直接复用原版的轮廓/斑点/染色机制；`ModItems` 颜色改为主色 `0x8A2BE2`（紫）+ 副色 `0x2FA8FF`（亮蓝）；
  原先 16×16 的自定义蛋贴图 `textures/item/tuner_spawn_egg.png` **已删除**（不再需要 tintindex 特判）。
- HUD 裁剪：`enableScissor(GuiGraphics)` 裁剪音乐条可视区。

### 3.5 网络与配置

- `TunerNetwork` 通道（id 0~3）：SMusicSyncPacket（进度/speed/分段/演奏实体集）、SShakePacket、
  SEntityRevivePacket（id 2，0.0.8）、SAccentWavePacket（id 3，0.0.10，声波涟漪触发，
  注册时显式限 `PLAY_TO_CLIENT`）。**0.0.10 起网络协议版本由 `"1.0"` 提升为 `"2.0"`，且从宽松匹配
  改为严格匹配**（注册处写 `"2.0"::equals`）——因为本轮新增了实体同步字段（施法分类位掩码，见 §3.12）
  与新包，旧客户端会错误解码；⇒ **联机双方必须同时更新到 0.0.10 及以上版本**（协议号 `2.0` 自 0.0.10 起未再变动，0.0.11 / 0.0.12 / 0.0.13 均为 `2.0`），否则协议不匹配、连接被拒。
  0.0.5 起 `SMusicSyncPacket` 改为**先检测 128 格内有无玩家，无人接收时不构造、不发送**
  （原先先构造包体——`getSegments()`/`getAccents()` 各自 `List.copyOf` + 分配数组——再判断；
  Boss 设了 `setPersistenceRequired`，附近无玩家时仍会空跑）。
- LLM 端点（`llm.apiUrl` / `llm.model`）：默认面向国际用户保留 OpenAI
  （`https://api.openai.com/v1/chat/completions` + `gpt-4o-mini`）；
  **中国大陆环境需改为 `https://api.deepseek.com/v1/chat/completions` + `deepseek-chat`**
  （0.0.5 实测：`api.openai.com` 连接超时、`api.deepseek.com` 可达；错误报文现已带目标 URL 与模型名）。
- **`LLMClassifier` 批量评分的 0.0.7 修复**（聚晶多时的正确性与稳定性，细节见 §3.9）：
  原先把全部聚晶塞进**单个**请求且**不设 `max_tokens`** → 实测 200 个聚晶只应用了 25 条（模型响应本身残缺，
  日志既无非法 id 告警也无解析失败）。现改为**分批请求**：`FOCI_PER_REQUEST=60` / `CHARS_PER_REQUEST=12000`
  双预算切批，**顺序**串成 CompletableFuture 链依次请求并累计应用条数（200 个聚晶 → 4 批；顺序而非并发，
  避免触发服务端限流、写回顺序也可预测）；并显式 `max_tokens=4096`（`MAX_TOKENS`，60 条约 1500 token，余量充足）。
  可诊断性：每批结束打 INFO `[Tuner] LLM batch i/n: sent X foci -> applied Y`，全部结束后若 `applied < 总数`
  打 **WARN** `LLM classify covered only X/Y foci`（提示模型只返回了部分条目，**已应用的结果不会丢失**，可再点一次「开始评分」补齐）。
  依据：本轮离线脚本 `scripts/llm_score_foci.py` 用同样的分批策略实测 **252/252 全部返回、0 非法 id、0 遗漏、0 幻觉**。
- `TunerCommonConfig`：common toml，**62 项、10 个 section**——`boss`(19) / `phase2_buffs`(3) /
  `summon`(4) / `scoring`(3) / `casting`(10) / `focus`(1) / `wand_whitelist`(1) /
  `music`(**13**) / `llm`(2) / `wand_upgrade`(6)，
  Configured 中文分类引导；`music_score.json`、`focus_classification.json` 运行时双写。
  0.0.13 新增键：`music.accentDensityDivisor`（`IntValue`，**默认 3**，范围 1~9，见 §3.14(4)）。
- ⚠️ **0.0.13 可访问性结论（重要）**：`TunerConfigScreen` **顶替**了 Forge 默认的 toml 编辑器，
  界面里只有 LLM API Key 与提示词两个输入框 ⇒ **`goetytuner-common.toml` 里的所有项都只能手改 toml**
  （包括 `focus.blacklist`，这也解释了为什么"拉黑索命聚晶"只能靠改文件落地，见 §3.14(7)）。
- **⚠️ 0.0.10 配置迁移（务必告知玩家）**：`music.accentWave`（默认 **true**）是**本次新增的键**，
  Forge 合并配置时会**自动补进**已有 toml；但 `music.accentParticles` 是**已存在的键**，本轮只是把
  **默认值由 `true` 改成 `false`**（键保留为手动兼容选项）——**Forge 不会用新默认值覆盖已有 toml**，
  所以老玩家文件里它仍然写着 `true`，会出现「旧粒子冲击环/音符 + 新声波」同时播放。
  ⇒ 必须**手动改成 `false`**（本次已在用户实例 `versions\测试` 的 toml 手工迁移：
  `accentParticles=false` + 新增 `accentWave=true`）。**口径：「新增键会自动补齐、无需删 toml」
  与「改默认值不会回填老 toml」是两件事**，不要混为一谈。
  0.0.13 新增的 `music.accentDensityDivisor`（默认 `3`）属**新增键** ⇒ Forge 会**自动补进**老 toml、**无需手改**
  （实例的 toml 在下次启动生成该行之前查不到这一键，属正常，不代表没生效——默认值在代码里）。

### 3.6 战斗数值（v0.5.0 / v0.6.0 调整后）

- 血量 216（12 档 × 18），锁血间隔 18，二阶段入口=第 6 档（108 血=半血）。
- 蓄力上限 50tick（2.5s）；二阶段前摇 = `clamp(1.0-(lockMark-6)*0.1, 0.1, 1.0)` 递减。
- 近战易伤 `meleeVulnerability`(0.25)：hurt 中 isDirectMelee（player_attack/mob_attack
  或 direct==entity 且非投射非爆炸）×(1+倍数)。
- Boss 身份免疫：摔落/火焰(IS_FIRE 标签)/窒息/溺水 → 免疫；魔法/爆炸/普攻正常吃。
- 召唤冷却免疫：`canBeAffected` 拒绝 GoetyEffects.SUMMON_DOWN。
- **二阶段自 buff**（`tickPhase2Buffs`，每 40 tick）：施原版力量 `DAMAGE_BOOST`
  （amplifier = 等级-1，持续 60 tick）+ Goety `RALLYING`；并额外挂 `SPELL_POTENCY` 的
  `MULTIPLY_TOTAL` modifier（值 = 力量等级 × 0.1），UUID =
  `nameUUIDFromBytes("goetytuner:boss_phase2_spell_potency")`；关闭总开关
  `phase2_buffs.phase2BuffsEnabled` 时移除该 modifier（防热重载残留）。
  **0.0.13 起这两处自施改走 `applySelfEffect`**（被 `canBeAffected` / Forge `MobEffectEvent.Applicable` 拒绝时打
  WARN，不再静默失效）；`RALLYING` 按定义**只加近战攻击**、对法系输出无感（故另挂 `SPELL_POTENCY` modifier 补法术伤害）；
  `TunerBoss` **未覆写 `getMobType`**（为 `UNDEFINED` 而非 `UNDEAD`）⇒ **不受亡灵免疫影响**。药水效果现状审计见 §3.14(6)。
- **二阶段回血**（`tickPhase2Regen`）：lockMark 7~12 且非锁血宽限期、血量低于当前档位上限时，
  每 40 tick `heal(1)`。

### 3.7 性能优化记录（0.0.5，功能语义不变）

| # | 位置 | 原问题（0.0.4 及以前） | 0.0.5 处理 |
|---|---|---|---|
| 1 | `SummonScoreTracker.ownedMinions()` | 走 `level.getAllEntities()`（遍历**全部已加载实体**），而 `TunerBoss.aiStep` 每 tick 会问很多次——铺垫期 2 次（`minionFillRatio` + `isSummonBlocked`）、**高潮期 3 个并行通道各 2 次 = 6 次**、低谷期 1 次（给仆从加药水）＋ 二阶段仆从清理；大型整合包里这是掉帧主因 | 同一 `gameTime` 内复用缓存；`registerNewMinions` 登记新仆从后作废缓存。语义不变（同一 tick 本就在同一世界状态上判断） |
| 2 | `BossPhase.values()` | Java 的 `values()` 每次调用都 clone 数组，而它在热路径上被按序号使用（服务端每 tick 取上一阶段、同步包每次编解码、客户端每次收包） | 新增 `BossPhase.byOrdinal(int)`（缓存 `VALUES` + 越界钳制），替换 4 处调用 |
| 3 | `broadcastMusicSync` | **先构造** `SMusicSyncPacket`（`getSegments()`/`getAccents()` 各自 `List.copyOf` + 分配数组）**再**判断 128 格内有无玩家；Boss 设了 `setPersistenceRequired`，附近无玩家时仍会跑 | 先检测玩家，无人接收直接返回 |
| 4 | `entityData` / 蹲姿同步 | `entityData.set(DATA_PHASE, …)` 每 tick 无条件写；施法蹲姿 `setPose(Pose.CROUCHING)` 每 tick 重复设（前摇可持续数十 tick，`setPose` 无相等性早退） | 仅阶段变化时写 `DATA_PHASE`；蹲姿加 `getPose() != CROUCHING` 判断 |
| 5 | `FocusClassificationConfig.ensureLangLoaded` | 把整个 `en_us.json` 灌进 `LANG_CACHE`（Goety 约 230KB / 数千条，9 个命名空间合计上万条永久驻留） | 只保留 `describe()` 真正会查的 `.info` / `.desc` 键 → 加载压力与内存占用下降约一个数量级，语义完全一致 |
| 6 | `MusicBarHud.onRender` | 每帧遍历 `level.entitiesForRendering()`（客户端全部已加载实体，整合包数百个）找调律师，**任何世界任何时候都在跑** | 新增 `MusicStateClient.anyPlaying()` 做零分配早退 |
| 7 | HUD 阶段文字 | `I18n.get(String, Object...)` 内部**无条件**走一遍 `String.format`，叠加 `"§c" +` 拼接，每帧产生数个临时对象 | 按 `(phase, Language 实例)` 缓存 |
| 8 | 重音所属阶段 | 渲染端每帧用 `phaseAtTick` 做 O(segments) 查找；二阶段 `wrap(-1/0/+1)` 三份条带会把**同一 tick 重算 3 遍** | 同步时预计算 `MusicStateClient.State.accentPhases`（与 `accents` 同序同长），渲染端只做索引读取 |
| 9 | `TunerCapeLayer` | Forge 47.x 的 `RenderType.create` 无内部缓存表，原先每帧每实体都新建 RenderType/缓冲区，且同帧多个调律师无法合批 | `RenderType.entitySolid(TEXTURE)` 缓存为静态常量 |

**明确"不动的部分"**（客户端审计逐条列出，写入文档以防后续误改）：逐格 `gfx.fill`
（直接写共享 GUI 缓冲、无分配，合并极易改错 1px 边界）、`enableScissor/disableScissor`
每帧一对（二阶段条带必要裁剪）、闪烁 `flashTicks--` 按**帧**衰减（改 tick 会改变时序）、
`ClientCameraShake` 的随机抖动、`TunerModel` 每帧一次 `hasPose`、`TunerCapeModel.setupAnim`
故意留空（摆动由 Layer 施加，补上会转两次）、`smoothed()` 的 `System.currentTimeMillis()`
（换游戏刻会跳变）、`renderSegments`/`renderStripAtAnchor` 的三等分回退分支（健壮性）、
可视区 ±4px 预筛边界。

**未采纳的建议（附理由，避免后续重复尝试）**：
① `smoothed()` 复用 State 对象——仅 60 对象/秒，风险收益比不划算，且一旦出现第二个调用点会互相踩踏；
② `BossMusicManager` 每 tick 的 `activeEntityIds()` 快照——20/s、开销极小，且该处
（第 79 行）会在遍历中 `clear(id)` 修改 `STATES`，改成直播视图会 `ConcurrentModificationException`。

### 3.8 限伤 / 限DPS（0.0.6）

两项都在 `TunerBoss.actuallyHurt(DamageSource, float)` 里做最终结算，且都**只作用于 TunerBoss 自身承受的伤害**。

| | 限伤（单次伤害上限） | 限DPS（每秒伤害上限） |
|---|---|---|
| 配置键 | `boss.maxHitDamagePercent`（默认 **0.25**，**默认开启**） | `boss.maxDamagePerSecond`（默认 **0**，**默认关闭**） |
| 约束对象 | **单次**伤害实例 | **每秒总吞吐**（滑动窗口） |
| 需要状态 | 无（纯钳制） | 需要（1 秒伤害历史） |
| 挡得住「一击秒杀」 | ✅ | 只能整段吸收或整段放行，粒度粗、手感突兀 |
| 挡得住「高频小伤害叠加」 | ❌（100 次 10 点照样打满 1000） | ✅ |
| 典型用途 | 防爆发 / 防秒杀 | 压制多段持续爆发（**在本 Boss 上不改变锁血阶梯的推进速度**，见下方实测） |

**实测：限伤在本 Boss 上作用有限（0.0.7 实证）**

读 `TunerBoss.applyLockHealth()`（L1097-1144）确认了锁血的真实机制（每 tick 执行，`aiStep` 第 7 步）：

```java
// 每 tick 执行（aiStep 第 7 步）
if (lockGraceTicks > 0) { lockGraceTicks--; if (getHealth() < lockGraceFloor) setHealth(lockGraceFloor); return; }
if (lockMark >= maxMark - 1) return;                    // 阶梯耗尽，可正常击杀
float nextFloor = maxHealth - interval * (lockMark + 1);
if (getHealth() < nextFloor) { setHealth(nextFloor); lockMark++; onLockTriggered(...); }
```

**关键点：血量一旦低于本档地板，就被「恢复」到该档地板并进入宽限期**（宽限期内 `hurt()` 直接 `return false` = 完全免疫）
⇒ **超出该档的伤害被丢弃** ⇒ **打 1000 点和打 18 点在锁血阶梯上都只推进一档**。

日志实证（`versions\测试\logs\debug.log` 全量统计，7 轮完整的 11 档阶梯）：

```
轮  档数  总时长    每档均    起止(相对秒)
1   11    7.25 s   0.72 s   1139.9 -> 1147.1
2   11   32.90 s   3.29 s   1156.1 -> 1189.0
3   11   21.50 s   2.15 s   1194.9 -> 1216.4
4   11    7.90 s   0.79 s   1233.6 -> 1241.5
5   11   57.50 s   5.75 s   1271.8 -> 1329.4
6   11   79.87 s   7.99 s   1384.9 -> 1464.7
7   11    9.50 s   0.95 s   1817.0 -> 1826.6
=> 每档中位 2.15 秒（最小 0.72 / 最大 7.99）；11 档一轮 7.25~79.87 秒
```

**每档耗时 = `lockGraceTicks`(10t=0.5s，下限) + 玩家下一次命中的间隔**，与伤害量无关；
中位 2.15 秒说明瓶颈是命中节奏而非宽限期。7 轮阶梯说明该玩家总共击杀了 7 次 Boss，
每轮 11 档完成后进入"可正常击杀"段（那时血量 ≤18，一击即走）。

**推论（实测结论，非推测）**：

1. 锁血机制**本身就是一个远比 0.0.6 的限伤更严格的隐式限伤**（把每次命中的有效伤害钳到一档 = `lockHealthInterval`，默认 18 点）；
2. 因此 **0.0.6 的 `maxHitDamagePercent=0.25`（≈54 点/次）在当前设计下几乎是空转的**——它比锁血的隐式钳制宽松得多，
   只在**阶梯耗尽后**（血量 ≤18、可正常击杀的那段）才可能起作用，而那时任何一击都能击杀，所以实际影响可忽略；
3. **真正决定战斗时长的是**：`lockGraceTicks`（每档最短时长，默认 10t=0.5s，**这是主导旋钮**）与**档位数**
   （`maxHealth / lockHealthInterval`，默认 216/18 = 12 档），而不是任何伤害上限；
4. 因此**限DPS（`maxDamagePerSecond`）同样不改变阶梯的推进速度**——阶梯是按「宽限窗口」推进而不是按「每秒伤害量」推进的；
   它只在阶梯耗尽后（≤18 血那段）才可能有意义；
5. 想让战斗更长，应该调（按有效性排序）：① `lockGraceTicks` 10 → 20/30（每档最短 1s / 1.5s，12 档 ≈ 12~18s 下限）；
   ② `maxHealth`/`lockHealthInterval` 增加档位数（例：216/12 = 18 档）；③ 若想真正让「伤害量」影响战斗时长，
   需要改成「锁血不丢弃溢出伤害」或「阶梯按累计伤害推进」的设计——属于机制改动，不在本轮范围。

**（1）限伤**：单次命中最多打掉 `最大生命 × maxHitDamagePercent`；默认 216 血 → **约 54 点/次**，属**相对宽松**
（只削掉「一击秒杀」式巨额单次伤害，常规武器一击基本触不到）。范围 0.0~1.0，**0 = 关闭**。
**准确表述**：限伤默认开启，但**在锁血阶梯未耗尽前基本不改变推进速度**（锁血自身的隐式钳制严格得多，见上方实测）；
它主要作为**一道保险**，避免阶梯耗尽后被单次巨额伤害瞬间带走。

**（2）限DPS**：`TunerBoss` 内有一个 `float[20]` 环形缓冲（槽位 = `gameTime % 20`，槽内只存该 tick 生效的伤害）+ `damageWindowSum` 总和；每次结算先 `advanceDamageWindow(now)` 把 `(lastTick, now]` 这些 tick 的旧槽位清零，
再算 `预算 = 上限 - 窗口内已造成伤害`：预算 > 0 → 本次伤害取 `min(伤害, 预算)` 并把生效值记入当前 tick 槽位；
预算 ≤ 0 → 本次伤害被**完全吸收**（`applyDpsCap` 返回 -1，`actuallyHurt` 直接 return 不调 super）。
默认关闭的原因：与限伤不同，限DPS 没有「天然宽松值」，合适数值取决于希望这场战斗最短打多久——
按默认 216 血、纯输出估算，**20 ≈ 11 秒 / 30 ≈ 7 秒 / 40 ≈ 5.4 秒**。
⚠️ **该估算在 0.0.7 被实测推翻**：本 Boss 的锁血阶梯按「宽限窗口」推进而非按每秒伤害量推进，
所以限DPS **不改变阶梯推进速度**，这组"最短战斗时长"在**阶梯耗尽前不成立**（见上「实测：限伤在本 Boss 上作用有限」）。
两者都开时顺序是**先限伤、再限DPS**：先把单次削到上限，再由每秒预算决定这次能兑现多少。

**（3）参照实现（Goety 本体）**：Goety 已有成熟的「限伤」——4 个 Boss 各有一个单次伤害上限配置：
`AttributesConfig.ApostleDamageCap` / `VizierDamageCap` / `EnderKeeperDamageCap` 默认 **20.0**、
`RedstoneMonstrosityDamageCap` 默认 **25.0**（注释原文：*"The maximum amount of damage an XXX can attain per hit"*）。
实现位置是**覆盖 `actuallyHurt`**，而非 `hurt`：

```java
protected void actuallyHurt(DamageSource source, float amount) {
    float initialAmount = amount;
    if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
        amount = Math.min(initialAmount, AttributesConfig.ApostleDamageCap.get().floatValue());
    }
    ...
    super.actuallyHurt(source, amount);
}
```

（`Apostle.java` / `Vizier.java` / `EnderKeeper.java` / `RedstoneMonstrosity.java` 四处同构；另有
`RobeEvents.DamageEvent` 在 `LivingHurtEvent` 里对戴「不洁之帽」的玩家做同类 clamp。）
`BYPASSES_INVULNERABILITY` 守卫的作用：不拦 `/kill` 的 `generic_kill` 与掉出世界等伤害，
否则会出现「管理员杀不死、掉进虚空也不死」。本项目**照搬这一做法**（`TunerBoss` 覆盖 `actuallyHurt`、
在最终结算处取 `Math.min`、跳过 `BYPASSES_INVULNERABILITY`），并补上 Goety 没有的**限DPS**；
作为参照，Goety 本体是**固定 20 点**，比本项目的 54 点严格得多。

**补充实测（Goety 全量源码 grep，2116 个 java）**：
① **Goety 本体没有「限DPS」**——用 `damageTaken|damageAccum|dpsCap|DpsCap|damageThisSecond|recentDamage|damageWindow`
搜索，**匹配数 0**。即：**限伤是 Goety 已有做法（本项目照搬），限DPS 是 Goety 没有、本项目补上的**——不要误以为两边都是照抄。
② Goety 相邻的机制是 **`moddedInvul`（受击后无敌帧）**，它限制的是**命中频率**而不是 DPS：`Apostle.java` / `Vizier.java` /
`EnderKeeper.java` 都有 `public int moddedInvul = 0;`（`Apostle.java:171`），在 `actuallyHurt` 里判断
`if (this.moddedInvul <= 0) { super.actuallyHurt(...); this.moddedInvul = MobsConfig.BossInvulnerabilityTime.get(); }`，
并在 tick 里递减（`Apostle.java:1189-1190`）；配置 `MobsConfig.BossInvulnerabilityTime` 注释原文
*"How long invulnerability, Default: 15"*，默认 **15（tick）**。语义差别：无敌帧 =「打完一下之后 15 tick 内后续命中无效」，
是**按次数/频率**节流；限DPS =「1 秒内总伤害不超过 N」，是**按累计量**节流——两者不互相替代
（无敌帧挡不住「一次超高伤害」，限DPS 也不改变单次命中的节奏）。

**（4）为什么放 `actuallyHurt` 而不是 `hurt`**：`actuallyHurt` 是伤害真正生效的唯一入口，放在这里
**受击动画、击退、无敌帧（`invulnerableTime`）全部照常发生**——玩家看到的是「打中了，但只掉这么多血」，
而不是「打了完全没反应」。本项目 `hurt()` 里已有的免疫/宽限期/近战易伤/致死截断逻辑完全不动，两者是叠加关系。
限DPS 被完全吸收的那一次同理：虽然不再调 super，但 `hurt` 已返回 true，受击动画/击退/无敌帧仍然照常发生。

**（5）注意（副作用 / 已知局限）**：
- 两者都只作用于 **TunerBoss 自身承受的伤害**；玩家侧（Boss 打玩家的伤害）未做限制，如需另说。
- 限DPS 记的是**实际生效的伤害**（先限伤后记录），所以一次巨额命中不会把整秒预算一次吃光。
- 限DPS 窗口内若伤害被其它 mod 在 `LivingDamageEvent` 里取消，预算仍已被扣（基础款实现的已知局限）。
- 新增两个配置项后，配置总数从 **58 → 60**（`boss` 段 17 → 19 项，section 数仍为 10）。

### 3.9 0.0.7 稳定性修复

四项修复，前三项在 `focus/LLMClassifier.java`、第四项在资源侧（起因是排查游玩实例日志时发现的三个可疑点，其中第一项是真 bug）：

| # | 问题 | 处理 |
|---|---|---|
| 1 | **LLM 批量评分在大规模下只覆盖一小部分**（真 bug）：原实现把全部聚晶塞进**单个**请求、不设 `max_tokens` → 实测本实例 200 个聚晶点「开始评分」后只 `applied 25 foci`（配置 252 → 277 条），且日志既无 `invalid id ... skipped` 告警也无解析失败 ⇒ **模型返回的响应本身就是残缺的**（被输出长度限制截断/敷衍） | **分批请求**：`FOCI_PER_REQUEST=60` / `CHARS_PER_REQUEST=12000` 双预算切批，**顺序**串成 CompletableFuture 链依次请求并**累计**应用条数（200 个聚晶 → 4 批）；显式 `MAX_TOKENS=4096`（60 条约 1500 token，余量充足，不再依赖服务端默认值）；可诊断性：每批 INFO `[Tuner] LLM batch i/n: sent X foci -> applied Y`，全部结束后 `applied < 总数` 则 WARN `LLM classify covered only X/Y foci`（已应用结果不丢失，可再点一次补齐） |
| 2 | **线程泄漏**：`Executors.newSingleThreadExecutor()` 原写在 `runAsync` **方法体内** → 每点一次「开始评分」就新建一个线程池且从不 shutdown，泄漏一条常驻线程（且是非守护线程，还会阻碍 JVM 正常退出） | 改为**静态单例 + 守护线程**（线程名 `goetytuner-llm`、`setDaemon(true)`） |
| 3 | **提示词格式化会抛异常**：原先用 `String.format(promptOverride, fociJson)` 注入聚晶列表，而提示词框自 0.0.5 起是**用户可编辑的多行框**——用户若写了意外的 `%`（如「命中率100%」）会抛 `UnknownFormatConversionException`，该异常发生在 try 之外 → **按钮永久卡在「正在评分…」** | 改用 `promptOverride.replace("%s", fociJson)`（语义等价：替换占位符且不解析其它 `%`） |
| 4 | **模组自身造成的日志噪声**：`assets/goetytuner/sounds/README-音乐资源说明.txt` 的中文文件名触发原版资源包校验的 `ERROR [net.minecraft.Util]: Invalid path in pack: goetytuner:sounds/README-音乐资源说明.txt, ignoring`（无害但属模组自身造成的 ERROR；同类的 `goetyawaken` 中文贴图名也在报同样错） | 改名为 ASCII 的 **`README-music-resources.txt`**，jar 内已确认旧名不存在 |

**分批策略的依据**：本轮离线脚本 `scripts/llm_score_foci.py` 用同样的分批策略实测 **252/252 全部返回、0 非法 id、0 遗漏、0 幻觉**。

> ⚠️ **注意**：`README-音乐资源说明.txt` 只在「0.0.7 改名前的历史名」语境下出现，jar 内已不存在该文件。

### 3.10 附属模组变化时的健壮性（0.0.8）

**起因（用户提问）**：「聚晶数量随附属模组变化，我们的聚晶相关系统会不会出问题？」

**审计结论（先给结论）**：**正常换附属不会出问题**——聚晶系统是**运行期扫描**设计的。
`FocusPoolManager.initIfNeeded()` 在服务器启动时（`ServerStartingEvent`）遍历 `ForgeRegistries.ITEMS`，
取 `instanceof IFocus && getSpell() != null` 的物品建池；评分表/冷却池/战斗池都按**当前实际注册**的聚晶重建。
配置文件里多余的条目**不会被匹配**（无害），缺少的条目走启发式兜底。
⇒ **增删附属只需重启游戏**，系统自动适配，无需手工清配置。

**但审计发现 4 个真实脆弱点，已全部加固（0.0.8）**：

| # | 脆弱点 | 加固 |
|---|---|---|
| 1 | **`initIfNeeded` 的扫描没有逐项兜底**：某个附属的 `IFocus.getSpell()`（或 `FocusEntry` 构造）抛异常会让**整个扫描失败** → 池子为空 + 启动报错 | 改为**逐项 `try/catch(Throwable)`**：跳过坏项并记 ERROR `Skipping focus item {} — scan threw {}`；统计跳过数量后再打一条 WARN `{} focus item(s) skipped during scan (broken addon implementations)`；其余聚晶照常可用 |
| 2 | **分类阶段 `FocusClassificationConfig.applyTo(...)` 同样可能抛异常**（第三方法术类在 `instanceof ISummonSpell`、`describe()` 描述解析等处）。实际加固位置是**调用点**：`FocusPoolManager.initIfNeeded` 里对 `classification.applyTo(ALL_ENTRIES)` 整体 `try/catch(Throwable)` | 失败时记 ERROR `Focus classification failed; keeping default categories`，**保留默认分类继续建池**（不会因为分类异常导致池子为空） |
| 3 | **`CastChannel.beginCast` 原本只有 `startSpell` 被 try 包住**，而 `conditionsMet` / `installFocus` / `castDuration` / `CastingSound` / `castingVolume` **都在 try 之外**——附属法术在这些点抛异常会沿 `tickBuildup/tickClimax → aiStep → serverAiStep` **一路冒泡直接崩服**（整合包换一批附属就可能触发） | **整条流程统一兜底**：`beginCast` 只负责抽签，随后 `startCast(...)` 的全部步骤被 try 包住，任一步抛异常都按「该聚晶不可用」处理 → 运行期拉黑 + 归还功能池 + 复位通道，并返回 false |
| 4 | **`FocusPoolManager.returnEntry` 不幂等**：施法在多条失败路径上归还，会让同一聚晶在池里出现多份、抽取权重被人为放大 | 改为**幂等**：池中已有该条目就不再添加（`if (pool != null && !pool.contains(entry)) pool.add(entry);`） |

**同点 3 一并加固的三处**（都是第三方法术实现的调用点）：
- `instantCast` → `instantCastInternal(...)` 统一兜底（异常 → 拉黑 + 归还 + 返回 false）；
- `interrupt` 的 `spell.stopSpell(...)`（**打断路径必须无论如何都把聚晶归还**，否则永远锁在池外；异常只记 ERROR 不中断归还）；
- `finishCast` 的 `spellCooldown(...)`（抛异常时退化为「仅额外冷却」，否则聚晶既不回功能池也不进冷却池 = 永久丢失）。

> 另说明：`RUNTIME_BLACKLIST` 是**会话级**的（重启清空），永久屏蔽要写配置 `focus.blacklist`。

### 3.11 死亡状态完善（0.0.8）

**起因（用户反馈）**：特殊手段仍会让 Boss 处于「血量不为 0 但已在死亡动画」的状态。

**本轮做了反编译实证**（这是「为什么必须自己发包」的依据）：

- `LivingEntity.deathTime` 是**普通 public 字段、不是 SynchedEntityData**（只在 NBT 里以 `"DeathTime"` 持久化）。
- 扫**整个 Minecraft jar**：引用该字段的类**只有 5 个**——`LocalPlayer`、`LivingEntityRenderer`、`GameRenderer`、`AbstractHorse`、`LivingEntity`；
  而其中**唯一会把它写 0 的地方是 `LocalPlayer.resetPos()`**（玩家复活专用）。**服务端其它任何写入都不会传到客户端。**
- `LivingEntity.handleEntityEvent(byte)` 的 `lookupswitch` 只有 **13 个分支：3 / 29 / 30 / 46 / 47 / 48 / 49 / 50 / 51 / 52 / 54 / 55 / 60**——**没有 35**。
  ⇒ 原版图腾复活广播的**实体事件 35 在 1.20.1 不产生任何客户端行为**（`checkTotemDeathProtection` 里确实是 `bipush 35` + `broadcastEntityEvent`，但客户端无对应分支）。
- `LivingEntityRenderer` 的两处用途：`setupRotations` 用 `deathTime` 做**倒下旋转**（旋转量 ≈ `sqrt((deathTime + partialTick) / 20) * 90°`）；`getOverlayCoords` 在 `hurtTime > 0 || deathTime > 0` 时叠**红色受伤层**。
- ⇒ **结论：服务端把 Boss 复活（血量恢复、`deathTime=0`）后，客户端那份 `deathTime` 永远卡在 > 0 → 玩家看到「血条是满的，但 Boss 躺着演死亡动画」。**这正是用户报告的现象。

**三层修复**：

1. **新增网络包 `SEntityRevivePacket`（通道 id 2）+ 客户端处理类 `client/ClientDeathAnimation`**：
   `TunerNetwork.sendToTracking(...)`（Forge `PacketDistributor.TRACKING_ENTITY`）发给所有追踪者；
   客户端把 `deathTime` / `hurtTime` / `hurtDuration` 归零，并把 `Pose.DYING` 恢复为 `STANDING`。
   （**三样都必须清**：倒下旋转、红色受伤层、垂死姿态各自依赖其一。）
2. **`maintainDeathState()` 新增「脏状态」分支**（替换原 `reviveIfDead()`）：血量 **> 0** 但 `deathTime > 0`
   （被外部手段救活/回血却没清动画）→ **只清动画、不消耗锁血档位**（原实现会把这种状态当成一次死亡而白吃一档）；
   只有**真死（血量 ≤ 0）**才走「回弹到下一档地板 + 锁血档位 +1」的原逻辑。`tick()` 每 tick 调用它（死亡动画期同样运行）。
3. **拦截 `remove(KILLED)`**：原版在 `deathTime` 到 20 时会 `remove(KILLED)`——**一旦移除就再也回弹不了**（实体已不在世界里）。
   现在「锁血未耗尽 + 总开关开启」时挡下这次移除（`canStillRevive()`，日志 `Blocked KILLED removal — lock tiers remain (mark={}), will revive next tick`），交给下一 tick 回弹。
   该判断**合并进类里原有的 `remove(RemovalReason)` 覆写**（那里还要执行 `CombatEvents.unregisterBoss(this)`）。
   只拦 `KILLED`：区块卸载（UNLOADED_*）、和平模式消失（DISCARDED）照常放行；锁血耗尽时也放行（正常击杀路径不变）。
   但 **`/kill` 是例外**（管理员指令的 `DamageTypes.GENERIC_KILL`）：0.0.9 起该拦截对 `/kill` 放行，见本节末尾「0.0.9：为 `/kill` 打开后门」。
4. ~~保留 `applyLockHealth()` 里的「死亡自愈兜底分支」（注释已说明其在死亡动画期不可达）~~ ——
   **⚠️ 那条注释的前提是错的：该分支实际可达，已于 0.0.11 删除**，见本节末尾「0.0.11」小节。

**0.0.9：为 `/kill` 打开后门（0.0.8 两项保护的必要配套）**

**起因（用户要求）**：上面第 1、3 项保护有副作用——**管理员用 `/kill` 也杀不死 Boss 了**：
会被宽限期免疫吞掉、被致死伤害截断压到剩 1 血、被 `maintainDeathState()` 死亡回弹救活、被 `canStillRevive()` 的 `remove(KILLED)` 拦截挡下。用户要求「为 `/kill` 的击杀打开后门」，即管理员指令必须能真正击杀。

**字节码实证的调用链**（这就是"为什么能在 `hurt` 里识别"的依据）：

```
KillCommand → Entity.kill() →（虚分派）LivingEntity.kill() → hurt(damageSources().genericKill(), Float.MAX_VALUE)
```

`javap -c` 实测：`LivingEntity.kill()` 里是 `invokevirtual DamageSources.genericKill()` + `ldc_w float 3.4028235E38f` + `invokevirtual hurt`；`KillCommand` 里是 `invokevirtual net/minecraft/world/entity/Entity.kill:()V`。
⇒ 只要在 `hurt` 里识别 **`DamageTypes.GENERIC_KILL`** 就能精准区分「管理员指令」与「玩家伤害」，**无需给命令加权限判断**。

**实现（`entity/TunerBoss.java`，本轮只改这一个类）**：

1. 新增字段 `private boolean adminKillPending = false;`（`/kill` 后门标记）；
2. **`hurt()` 最前面**新增分支：`if (source.is(DamageTypes.GENERIC_KILL)) { adminKillPending = true; 记 INFO 日志; return super.hurt(source, amount); }`
   —— 直接跳过其后**所有**锁血逻辑（身份免疫检查、近战易伤、**宽限期免疫**、**致死伤害截断**），把原样的 `MAX_VALUE` 交给原版结算；
3. **`maintainDeathState()` 最前面**：`if (adminKillPending) return;` —— 不做回弹、不做脏状态清理，让死亡流程正常走完；
4. **`canStillRevive()` 最前面**：`if (adminKillPending) return false;` —— 于是 `remove(KILLED)` 的拦截也放行，Boss 真正死亡；
5. 状态正常时（既没死也不在死亡动画）清除标记：防止「那次 `/kill` 被其它模组取消」导致保护被永久关闭；
6. `actuallyHurt()` 的限伤 / 限DPS 跳过条件里**额外显式加上** `!source.is(DamageTypes.GENERIC_KILL)`（原本只靠 `BYPASSES_INVULNERABILITY` tag，见 §3.8）——显式判定不依赖 tag 内容，保证后门在任何数据包 / 模组环境下都成立。

日志串实测原文：`[Tuner] /kill backdoor: bypassing lock-health protection (health={}, mark={})`

**要点**：这是 0.0.8 那个 `remove(KILLED)` 拦截的**必要配套**——凡本文档提到「锁血未耗尽时拦截 `remove(KILLED)`」之处，均应理解为「**`/kill`（`DamageTypes.GENERIC_KILL`）例外**」，即本节第 3 项。

**⚠️ 已知限制 / 代价（必须知晓，不是 bug）**：

1. **无法区分「管理员 `/kill`」与「其它模组调用 `entity.kill()`」**：两者走的是**同一条**调用链
   （`Entity.kill()` → `LivingEntity.kill()` → `hurt(damageSources().genericKill(), MAX_VALUE)`）。
   `javap -c` 实测 `DamageSources.genericKill()` 只是返回构造期缓存好的字段，而该字段由
   `source(ResourceKey)` 生成 = `new DamageSource(Holder)`——**用的是不带 `Entity` 形参的那个构造器**，
   ⇒ **`getEntity()` / `getDirectEntity()` 恒为 null，`DamageSource` 里没有任何可用于区分调用方的信息**。
   ⇒ **任何用 `kill()` 杀 Boss 的第三方模组/工具，也会一并绕过锁血阶梯直接真死**。
   若要只给 `/kill` 开口子，需要改成在 `KillCommand` 侧判定命令来源/权限——**当前架构做不到**，
   属已知限制（如需收紧，方案是给命令打标记的自定义 `DamageSource`，但那要 mixin 原版命令，代价更大）。
2. **锁血回弹（情形 B，`Death-revive`）现在极难触发**：走伤害管线的致死伤害会被锁血地板钳住（血量不会到 0），
   而走 `generic_kill` 的击杀现在直接真死 ⇒ 只剩「**不经伤害管线直接把血量归零**」的外部手段
   （其它模组直接 `setHealth(0)`）才会触发回弹。这是打开后门的必然结果。
3. **情形 A（脏状态清理，`stale death animation`）不受影响**，仍然照常生效——那才是用户最初反馈的
   「血量不为 0 但已在死亡动画」的修复，其触发源是外部回血/复活而非 `/kill`。

**0.0.11：删除 `applyLockHealth()` 里重复的「死亡自愈」分支（击败 `/kill` 后门的真 bug）**

**起因（用户玩出来的）**：用户反馈「每 tick 检测死亡状态似乎有 bug」。查用户实例 `versions\测试\logs\latest.log` 得**日志铁证**：

```
L4793  10:15:24.577  /kill backdoor: bypassing lock-health protection (health=29.485922, mark=10)
L4794  10:15:24.625  Revived from death → lock at 18.0 (mark=11/12)
L4799  10:15:25.477  /kill backdoor: bypassing lock-health protection (health=18.0, mark=11)
```

即：`/kill` 刚把 Boss 打死，**48 ms 后 Boss 又带着 18 血复活了**，用户只能再打一次 `/kill`。

**根因**：`applyLockHealth()`（由 `aiStep()` 调用）里有一段**与 `maintainDeathState()` 重复**的「死亡自愈」分支，
其注释断言「实体死亡后 `aiStep()` 根本不执行 ⇒ 本分支是死代码、不可达」。**该断言是错的**：

- **反编译实证 1**：`LivingEntity.tick()` 共 **366 行字节码**，其中 **`aiStep()` 只有 1 处调用（偏移 179、完全无条件）**；
  `isDeadOrDying` / `serverAiStep` / `tickDeath` 在该方法内**一次都没出现**。
- **反编译实证 2**：真正被死亡把关的是 `baseTick()`——**偏移 357** 判 `isDeadOrDying()` → **偏移 375** 调 `tickDeath()`
  （`tickDeath()` 里 `deathTime++`，`>= 20` 时播实体事件 60 并 `remove(KILLED)`）。
- 原注释把 **`aiStep()` 与 `serverAiStep()` 混为一谈**，所以「死亡期间 `aiStep()` 不执行」不成立，**该分支实际可达**。

**三重后果**：

1. 该分支**不看 `adminKillPending`** ⇒ **`/kill` 后门被它击败**（管理员指令刚生效就被回弹）；
2. 它**白吃一档锁血**（`lockMark++`），把本该留给玩家输出的档位消耗掉；
3. 它只写 `deathTime = 0`、**不走 `resetDeathAnimation()`**（既不发 `SEntityRevivePacket`、也不清
   `hurtTime` / `hurtDuration` / `Pose.DYING`）⇒ **客户端停在死亡动画**，即用户更早反馈的
   「血量不为 0 但已是死亡动画」复发。

**为何第二次 `/kill` 就成功了**：第二次时 `lockMark = 11 = maxMark-1`，该分支的 `lockMark < maxMark-1` 条件不再成立
（锁血耗尽即放行）——与日志 `L4799` 完全吻合。

**修复（只改 `entity/TunerBoss.java`）**：

1. **删除** `applyLockHealth()` 里那段重复的回弹分支；改为在方法**开头**对死亡状态直接早退：
   `if (this.isDeadOrDying() || this.deathTime > 0) return;`
2. 明确「**死亡回弹的唯一权威实现是 `tick()` 里的 `maintainDeathState()`**」（它尊重 `/kill` 后门，
   并通过 `resetDeathAnimation()` 同步复位客户端动画），并把这条写进 `maintainDeathState()` 的 javadoc。
3. 重写 `tick()` 的 javadoc：删掉基于错误前提的旧说明（原写 `if (isDeadOrDying()) {...} else if (isEffectiveAi()) { serverAiStep() }`
   所以 death 期间 `aiStep()` 不跑），换成上面两条反编译实证的调用链，并说明**为什么必须放在 `super.tick()` 之前**
   ——`tickDeath()` 在 `baseTick()` 里才递增 `deathTime`，而 `aiStep()` 又在其后无条件执行，两者都不是回弹该待的地方。
4. 已用 `javap` 验证：部署版 jar 的 `applyLockHealth` **不再包含** `Revived from death` 调用
   （该类里仍有该字符串，来自 `maintainDeathState`，属正常）。

**「要不要给死亡检测降频」的结论：不需要，它几乎不花钱。**

- `maintainDeathState()` 每 tick 调用的**热路径只有两次读取**（`getHealth() <= 0` 判定 + `deathTime` 字段比较）后立即 `return`；
  真正有开销的动作（`resetDeathAnimation()` 复位、发 `SEntityRevivePacket`、打日志、`onLockTriggered()` 里的强制瞬移）
  **只在确实处于死亡或脏状态时才执行**，属极少数 tick。
- 而且**不能简单降采样**：「情形 A」（血量 > 0 但死亡动画残留）需要**及时**发现，隔 N tick 才查会让玩家多看到几帧躺倒。
- 若将来真要省，正确做法是**事件驱动**（在 `hurt()` / `setHealth()` 里置「待检查」标志 + 末端加低频兜底），
  而非降低检查频率。

### 3.12 0.0.10 美术交付的技术要点（立方体高亮 / 声波涟漪 / 渲染实证）

本轮是**表现层交付，不改战斗数值与施法流程**：新增 Java 文件 4 个（`client/AccentWaveRenderer`、
`client/render/TunerOrbLayer`、`client/render/TunerOrbModel`、`network/SAccentWavePacket`），
改动 `entity/TunerBoss`、`config/TunerCommonConfig`、`network/TunerNetwork`、`client/ClientSetup`、
`client/render/TunerRenderer`、`init/ModItems`（刷怪蛋配色）；贴图 2 改（`tuner.png`、`tuner_cape.png`）
+ 2 增（`tuner_orb.png`、`accent_wave.png`）+ 1 删（`item/tuner_spawn_egg.png`）。
贴图与预览参数见 `ART_ASSETS_REPORT.md`；下面只记与代码设计有关的四条。

**（1）立方体高亮用「位掩码」而不是「最后一次施法的分类」**

`TunerBoss` 新增同步数据 `DATA_CAST_CATEGORIES`（`SynchedEntityData` 的 **INT 位掩码**：
bit0=ATTACK / bit1=DEFENSE / bit2=SUMMON / bit3=OTHER），客户端据此决定点亮哪几颗立方体
（红 `#FF3B30` 攻击 / 蓝 `#2FA8FF` 防御 / 灰 `#C8C8C8` 召唤）。

- 服务端维护 `activeByCategory[]`（长度 = `FocusCategory.values().length`）计数，计数 > 0 时置对应位；
  **只在掩码真正变化时写 `entityData`**（沿用 §3.7「无变化不写同步数据」的原则）。
- **为什么不能只存"最后一次施法的分类"**：高潮是**三通道并行**施法，同一 tick 可能攻击+防御+召唤
  同时在施法中；单值字段只能表达其中一个，后到的包会把先到的覆盖掉，观感就是「只亮一颗」。
  位掩码天然表达「同时点亮多颗」，而 4 个分类用一个 INT 就够，同步开销与单值字段完全一样。

**（2）计数的幂等去重（`IdentityHashMap` 身份集合）**

并行通道会重复进入同一次施法的收尾回调（典型：`finish` 之后异常路径又走一次 `failed`）。
若只按「回调次数」增减计数，同一条通道会被减两次 → 把仍在施法的其它通道计数打穿、客户端高亮提前熄灭。
⇒ `TunerBoss` 持有**一个**按对象身份的「当前活跃聚晶」集合
（`Collections.newSetFromMap(new IdentityHashMap<>())`，**全实体共用一个即可**——一个 `FocusEntry` 只属于一个分类），
`changeCastCategory(entry, ±1)` 的逻辑是：
`if (delta > 0 ? !set.add(entry) : !set.remove(entry)) return;`——
即 **`add`/`remove` 真的成功了才动计数**（首次加入才算开始、真正移除才算结束），
失败的重复回调直接返回。用**身份**而非 `equals`，是因为池的锁池/归还可能出现等价副本，
只有对象身份才对应「这一次施法实例」。
> ✅ **0.0.12 已修**：原先那条「某次施法 start 之后 finish/interrupted/failed **一个都没来**」的路径
> （`CastChannel.startCast` 里 `logCast` 排在 `onCastStart` 之后并抛异常 → 逃逸到 `beginCast` 的兜底 `catch`，
> 而该 `catch` 不补发结束回调）**已消除**：`logCast` 提到 `onCastStart` 之前 + `startEmitted` 兜底补发 `onCastFailed`，
> 见 §3.13(3)。身份集合幂等仍是必要的第二层保护——它挡的是「重复递减」，与「缺失递减」互补，两者都需要。

**（3）两条渲染实证（省掉不必要的代码）**

- **`RenderType.entityTranslucent(texture)` 自带 `NO_CULL`**——本轮实证确认（含延迟批次提交路径），
  所以声波圆柱面**天然双面可见**，**不需要**再手工翻 `disableCull`/额外状态开关（也避免破坏合批）。
- **顶点必须按 `NEW_ENTITY`（`DefaultVertexFormat.NEW_ENTITY`）的顺序写入**：
  **POSITION → COLOR → UV → OVERLAY → LIGHT → NORMAL**，即 `vertex(...)` → `color(...)` → `uv(...)` →
  `overlayCoords(...)` → `uv2(...)` → `normal(...)` → `endVertex()`。顺序写错**不会抛异常**，
  只会静默出现错乱的颜色/光照/法线——这是 1.20.1 `VertexConsumer` 最隐蔽的一类错。

**（4）声波涟漪：服务端只发「触发」，波形在客户端解析式生成**

- 触发链：`TunerBoss.accentKnockbackPulse()` 在 `music.accentWave` 开关下
  `sendToTracking(new SAccentWavePacket(id))`（**通道 id 3**，限 `PLAY_TO_CLIENT`）→ 客户端
  `AccentWaveRenderer.trigger()` 记下「实体 id → (起始 gameTime, **触发瞬间的脚下坐标**)」（0.0.12 起带坐标）→ 渲染钩子
  `RenderLevelStageEvent.Stage.AFTER_PARTICLES` 逐环画环。**网络里没有任何顶点/几何数据**。
- 参数：**10 环 × 32 角分段**，半径 0.6~6.0 格（间距 0.6），相邻环延迟 **2 tick**，每环升 8 / 落 8 tick
  （`LIFE=16`），总时长 **34 tick**（`(RINGS-1)*DELAY+LIFE`）；淡白半透明（顶部 alpha 峰值 0.32，
  底部取顶部的 0.35 倍）。
- 高度函数 `h = 0.56*sin(πp)*(1 + sin(3θ + 0.10t − 0.28i)*a)`，**峰侧 a=0.40、谷侧 a=0.27**（非对称，
  两侧不等高）；地面偏移 0.01 ⇒ 绝对最高 **0.794 格**（设计要求 ≤0.8 格）。
- 生命周期：每次重音**覆盖同实体旧波、不叠加**；**无活跃波时渲染器立即返回、不扫描世界实体**
  （活跃表是有界 `Map<实体id, 起始tick>`，自然支持多个调律师同时播放且互不干扰）。
  ⚠️ **0.0.12 起这两条变了**：活跃表的 value 改为「起始 tick + **触发瞬间的脚下世界坐标**」，渲染锚点固定、
  **不再读 Boss 当前位置**；清理也**只按时间**（`DURATION = 34 tick`），不再因实体死亡/移除/换世界提前掐掉 —— 见 §3.13(1)。
  ⚠️ **0.0.13 起活跃表又从 `Map<实体id, Wave>` 改为 `List<Wave>`**：同一实体**可同时存在多条波**（连发各成一条、各自计时、
  层叠外扩，不再后发顶掉前发），`MAX_WAVES = 32` 硬上限兜底 —— 见 §3.14(3)。

### 3.13 0.0.12：涟漪锚定 / 音乐条外观重做 / `CastChannel` 回调成对性

本轮是**表现层重做 + 一个回调成对性修复**，不改战斗数值与施法流程（回调顺序除外）。改动文件与实测行数：
`client/AccentWaveRenderer.java`（**124 行**）、`client/MusicBarHud.java`（**453 行**）、`entity/ai/CastChannel.java`（**408 行**），
外加 `entity/TunerBoss.java`（**2001 行**，比 0.0.11 的 1998 行多 3 行）**仅一处方法 javadoc**（见 (4)）。
**无新增类/贴图/配置项**；`.java` 文件数仍 **45**，配置仍 **61 项 / 10 段**（`music` 12 项）；jar 条目 **101 → 102**
（多出 `AccentWaveRenderer$Wave` 内部类）。

**（1）声波涟漪锚定在触发瞬间的坐标（为什么必须锚定）**

- 原实现（0.0.10）只存 `startTick`，渲染时**每帧去读 Boss 当前位置** ⇒ 涟漪**跟着 Boss 跑**；
  而调律师**每次锁血触发都会强制瞬移**（`onLockTriggered` → `tryTeleportNear`），
  跟着跑会让「由内向外荡开的声波」被实体拖着走，观感完全不对。
- 现改为触发时记录**脚下的世界坐标**（取 `boss.getX()/getY()/getZ()`），新增私有
  `record Wave(long startTick, double x, double y, double z)` 作为活跃表的 value；整条波在这个固定点上播完。
- **清理只按时间**：`ACTIVE.entrySet().removeIf(e -> now - e.getValue().startTick() >= DURATION)`，
  `DURATION = (RINGS-1)*DELAY + LIFE = 34 tick`。**不再**因为 Boss 死亡 / 被移除 / 离开视野就提前掐掉波
  ——波已与实体无关，应当播完（否则 Boss 一死，波就凭空消失）。
- 渲染锚点写法 `worldPos − cameraPos`（`pose.translate(wave.x()-camera.x, …)`）与 Goety 本体
  `PrismaBeamRenderer` 一致。
- 波本身仍不读实体、不发网络包（几何在客户端解析式生成）；改动只是把「参考原点」从实体换成固定坐标，
  **网络里依旧没有任何顶点/几何数据**（见 §3.12(4)）。

**（2）音乐条 HUD 外观重做（用户反馈「太突兀」，四项一起改）**

| 项 | 0.0.11 及以前 | 0.0.12 |
|---|---|---|
| 尺寸 | `260×6`（又长又薄） | **`204×8`**（改短加厚；底距 64 → **62**） |
| 分段填充 | 硬边纯色块 | **逐行混色**（`fillSegment`：上半向白提亮、下半向黑压暗）+ 交界 **2px 半透明过渡缝**（`drawSeam`） |
| 边框 | 单一硬矩形底 `0xAA000000` | **三层由外向内渐深的柔和投影**（`drawSoftBackdrop`），最外层**四角留空**以模拟圆角 |
| 阶段提示 | 条上方画「铺垫/高潮/低谷」**文字** | **像素绘制**（`drawPhaseIndicator` + `drawDot`）：`● ● ●`（高潮）/ `●`（铺垫）/ `- - - - - -`（低谷） |
| 重音刻度 / 闪烁 | 纯白 `0xFFFFFFFF` / 峰值 `0xC0` | 半透明白 `0xB8FFFFFF` / 峰值 `0x88` |

- **为什么阶段提示不用 `⚪` 字符（实测结论）**：本客户端字体只有拉丁/希腊/西里尔等**位图字形**
  （`include/unifont.json` 的 providers 是**空数组**、jar 内**无任何 ttf**、也无 unicode 字形页；
  `options.txt` 只启用 `vanilla` + `mod_resources`，mods 里也没有任何 jar 提供字形页）
  ⇒ **U+26AA 没有字形，直接写会显示成空白方块**。像素画必定可见，且更符合「隐晦」的要求。
- **副作用**：lang 里 `info.goetytuner.music.buildup / .climax / .valley` 三个键**当前已无任何代码引用**
  （保留未删，注释已说明：它们是三个阶段的地道中/英名称，将来若要改回文字提示可直接复用）。
- 8px 高度适配：高潮的「中」字样式方框改为**上下各留 2px**（`y+2 … y+BAR_HEIGHT-2`），
  否则在 8px 条高上会被压成一根线。
- **一阶段的分段内容按条宽做了 scissor 裁剪**：分段像素宽由浮点换算取整而来，总和可能比 `BAR_WIDTH` 多 1~2px，
  不裁剪会溢出到右侧边框上（1px 缝）⇒ 画分段前 `enableScissor(x, y, x+BAR_WIDTH, y+BAR_HEIGHT)`、画完解除，
  **再画指针**（指针要上下探出条外，不能被裁）。二阶段条带本来就有 scissor 裁剪，不受影响。

**（3）`CastChannel` 回调成对性修复（消除「立方体永久高亮 / 蹲姿卡住」）**

- **根因**：`startCast` 尾部原本是
  `callback.onCastStart(entry); BossWandHelper.logCast(level, boss, entry); return true;`
  ——`logCast` 排在 `onCastStart` **之后**且**会抛异常**（它要调第三方/IO）；异常逃逸到 `beginCast` 的兜底 `catch`，
  而该 `catch` **不补发结束回调** ⇒ `onCastStart` 成为**孤儿回调**：
  `TunerBoss` 的施法状态计数（`DATA_CAST_STATE`，驱动的施法蹲姿）与 0.0.10 新增的**立方体类别位掩码**
  会**永久 > 0** ⇒ 对应立方体一直高亮、蹲姿卡住；
  又因 `TunerBoss` 用 `FocusEntry` **身份集合**（`IdentityHashMap`）做幂等去重，该聚晶之后**再也无法计入**，
  状态**无法自愈**（这正是 §3.12(2) 注里「只解决重复递减、不解决缺失递减」那条的具体触发路径）。
- **修复（两道防线）**：
  ① **把 `logCast` 调到 `onCastStart` 之前**，使 `onCastStart` 成为 `return` 前**最后一步**——**结构上不可能**
     再被后续代码打断；
  ② 新增 `startEmitted` 标志（`beginCast` 开头重置为 false、在 `onCastStart` 之后置 true），
     在 `beginCast` 的兜底 `catch` 里检测「已发过 start 却异常逃逸」时**补发一次 `onCastFailed`**让计数平衡
     （补发调用自身也 try-catch 包裹）——这是防将来有人在 `onCastStart` 之后加代码的**第二道保险**。
- 与 0.0.4 那次修复（`startSpell` 异常路径**不得**误调 `onCastFailed`，因为那时 `onCastStart` 还没发出去）方向相反、
  互不冲突：判断依据始终是「**`onCastStart` 到底发出去没有**」。

**（4）顺手修正 `applyLockHealth()` 的方法 javadoc（仅注释）**

- 该 javadoc 此前仍在描述 **0.0.11 已删除**的「死亡状态自愈 / 复活到下一档地板」行为，与同方法内的新注释自相矛盾
  （由上一轮的文档同步复核指出）。现改为明确写：**本方法只负责"活着时的锁血地板与宽限期"，不做任何死亡回弹**；
  死亡回弹的**唯一**权威实现是 `tick()` 里的 `maintainDeathState()`。
- **无行为变化**（纯 javadoc），`TunerBoss.java` 因而从 1998 行变为 2001 行。§3.11 的死亡逻辑本轮**未再改动**。

### 3.14 0.0.13：音乐自愈 / 重音抽稀 / 涟漪多波 / 立方体自发光 / 药水可观测性

本轮改动 **7 个 Java 文件 + 版本号**，**只改代码、不动任何贴图与资源**；**新增 1 个配置项**
`music.accentDensityDivisor` ⇒ 配置 **61 → 62 项**（`music` 段 **12 → 13**，10 个 section 不变）；
**无新增/删除类**（`.java` 仍 **45** 个，jar 条目仍 **102**）。
改动文件与实测行数：`entity/TunerBoss.java`（**2020 行**）、`client/MusicBarHud.java`（**463 行**）、
`client/BossMusicManager.java`（**181 行**）、`client/AccentWaveRenderer.java`（**138 行**）、
`entity/MusicController.java`（**319 行**）、`client/render/TunerOrbLayer.java`（**83 行**）、
`config/TunerCommonConfig.java`（**428 行**）。

**（1）修「玩家被击杀复活后（未走出索敌范围）背景音乐丢失」——`BossMusicManager` 用 `SoundManager.isActive` 自愈**

- **症状**：玩家被击杀 → 复活、且**没有走出索敌范围**时，Boss 背景音乐**有时**再也不响；
  用户的原话是「整个 BGM 都没了」。
- **根因**：本类起播的唯一入口是 `if (music == null) startMusic(mc)`，而「是否在播」**只**记录在静态字段
  `music != null` 上——**从不与声音引擎核对**。玩家死亡/复活时引擎会把这条循环实例**悄悄摘除**，
  已知至少四条真实通道：① `RECORDS` 音量为 0；② channel 被停止；③ `SoundEngine.reload()` → `destroy()`
  → `stopAll()`（资源重载）；④ `play()` 在引擎**尚未 loaded** 时会**静默返回**。
  任一路径发生后字段仍非 null ⇒ 只要服务端 `playing` 一直为 true（**没脱战**），`startMusic` **永不重入**
  ⇒ **永久静音**。
- **为什么连原版背景音乐也一起没了**：`onPlaySound` 的判据同样只是 `music != null` ⇒ 假死状态下
  它会把 MUSIC 类别的新声音**永久取消**（连 `MusicManager` 延迟调度的曲目一起）——这正是
  「整个 BGM 都没了」的原因，也说明这条 bug 的影响比"Boss 音乐不响"更大。
- **修法**：每 tick 用 **`SoundManager.isActive(music)`**（1.20.1 自带 API，已反编译确认存在）核实实例
  是否**真的在响**；失效即清空字段（并重置静音计数），下一 tick 若仍在演奏即自动重建 —— **自愈**，
  且天然覆盖上面全部四条通道（以及 `/stopsound`、切音频设备、原版音量设置等同类外部停音）。
  另加 **10 tick 防抖**（`restartCooldown`），避免「播不起来 → 每 tick 重建」的抖动。
  `onPlaySound` 判据同步改为 **`music != null && isActive(music)`**。
- **调查另确认（三条实证，避免后续重复排查）**：
  - **死亡本身不停声音**：`DeathScreen` 里**没有任何** `soundManager` 调用；
  - **同维度复活不替换 `ClientLevel`**：`handleRespawn` **仅换维度**时才 `setLevel`，
    所以不能把问题归因于"世界对象被换掉"；
  - **「有时」取决于两条计时线**：`playing=false` 必须持续越过 `STOP_GRACE_TICKS`（30 tick）
    与 `SYNC_TIMEOUT_MS`（3000 ms）才会真正停播——而**单人死亡界面会暂停集成服务器**，
    死亡停留时间不同就走不同分支，所以表现为"有时"。

**（2）重音标记滚动平滑 + 高潮改为细小长条 —— `MusicBarHud` 亚像素覆盖**

- **不平滑的根因**：`guiGraphics.fill()` **只能落在整数像素**上，而重音刻度是 1~2px 的细条，
  浮点位置被取整后**逐像素跳动**（视觉上是"一格一格挪"）。
- **改法**：**亚像素覆盖** —— 把刻度位置的小数部分**按比例分摊到相邻两列**
  （`x = 10.3` ⇒ 第 10 列 70% 透明度、第 11 列 30%），亮度重心随浮点位置**连续移动**
  ⇒ 滚动变平滑；**调用点不再预先取整**（否则小数信息在进入绘制前就被丢掉）。
- **高潮标记外观**：原「中」字（贯通竖线 + 空心方框）在 8px 条高上又粗又花，改为**小长条**：
  高潮 **2×6** 最亮、低谷 **1×6**、铺垫 **1×4**，全部**竖向居中**，只用「宽 × 高 × 亮度」区分强度。

**（3）涟漪多波共存（三连发不再互相顶掉）—— `AccentWaveRenderer` 的 `Map` 改 `List`**

- 0.0.12 用 `Map<entityId, Wave>`，语义是「同一实体重复触发就**覆盖**旧的」；而**二阶段进场的重音连发**
  是每 5 tick 一发、连发多次，于是后一发**顶掉**前一发 ⇒ 玩家只看到**一条波反复重播**，
  而不是"一波接一波荡开"。
- 改为 `List<Wave>`：每发各成一条波、**各自计时**，呈现**层叠外扩**；加 **`MAX_WAVES = 32`** 硬上限兜底
  （超了丢最旧的）。数量天然有界：单发波活 34 tick、触发最快 5 tick 一次 ⇒ 同时最多约 **7** 条。

**（4）重音数量与频率降为原来的 1/3 —— 新增配置 `music.accentDensityDivisor`**

- 新增 `IntValue` **`music.accentDensityDivisor`**（**默认 3**、范围 1~9）：从乐谱重音表里
  「**每 N 个保留 1 个**」。
- **抽稀放在服务端加载乐谱时统一进行**（`MusicController.loadScore()`，紧跟分段/重音读取之后）
  ⇒ 依赖重音的**所有**玩法一起变稀疏：HUD 刻度（经 `SMusicSyncPacket` **全量同步**）、
  重音击退、涟漪、提示音、无前摇施法。**客户端不需要任何改动**，也不会出现
  「HUD 密、实际触发疏」的不一致。
- 加载时打 INFO **`[Tuner] Accent thinning x3: 37 -> 13 accents`**（默认乐谱 37 个重音 ⇒ 运行时 **13** 个）。
- ⚠️ **口径提醒**：`run/config/goetytuner/music_score.json` **文件本身仍是 37 个重音**（抽稀只在内存里做、
  **不回写文件**）——文档里写「共 37 个重音」指的是**文件**，运行时生效的是 **13** 个，**两个数字都对**，
  引用时请注明口径（与 §七.6 的 lang/聚晶计数口径问题同类）。
- **配置总数 61 → 62 项**、`music` 段 **12 → 13** 项。

**（5）小立方体高亮更明显 + 发光效果 —— `TunerOrbLayer`**

- **高亮原先几乎看不出变化**：原实现 `min(1, color * (1 + glow*0.8))`，而红/蓝/灰的**分量本就接近 1**
  （红 = 1.0 / 0.231 / 0.188）——乘法被 `min(1, …)` 截断后，红的 R 通道**纹丝不动**、只有 G/B 微升。
  改为**额外叠加** `glow * 0.4` **逐通道向白靠拢** ⇒ 高亮时明显「**变白变亮**」。
- **「发光」用两层自发光外壳**：内层 **1.30×**、alpha `0.36`，外层再叠 **1.35×**、alpha `0.18`
  （都乘 `glow`）；渲染类型 **`RenderType.entityTranslucentEmissive`** —— 着色器层面**忽略光照**
  （等价满亮度）⇒ 无论昼夜、无论立方体在多暗的环境下都**稳定发亮**；并已实证它与
  `ENTITY_TRANSLUCENT` 同理**无条件 `NO_CULL`**（见 §3.12(3)），所以放大后的壳从任何角度都可见、
  不会出现背面被剔除的空洞。
- **⚠️ 为何不用原版发光描边（值得记住的取舍）**：MC 的「发光」是**整实体级**的 framebuffer 后处理
  （`OutlineBufferSource`），**只能整只 Boss 一起描边**，**无法只描一颗立方体**；
  外壳方案能精确地只让「高亮的那一颗」发光，代价是高亮时多两个 draw call。后续做类似需求会再遇到这条。

**（6）药水效果可观测性 —— `TunerBoss` 的 `applySelfEffect`，以及药水现状审计**

- **可观测性缺口**：审计发现全类 **8 处** `addEffect(...)` 的 **boolean 返回值全被丢弃**，
  而 `LivingEntity.addEffect` 的第一步就是 `canBeAffected`（Forge 还会在这一步 fire
  `MobEffectEvent.Applicable`）⇒ **效果被拒时静默失效、日志无一字**，无从判断"到底有没有上上去"。
- **新增** 私有 `applySelfEffect(MobEffectInstance)`：被拒时打
  `WARN [Tuner] addEffect rejected: <effect> amp=N (被 canBeAffected / MobEffectEvent.Applicable 拦下)`。
  已用于 **boss 自身的 4 处**：低谷自施 `GoetyEffects.SAPPED` + `MobEffects.DARKNESS`、
  二阶段自施 `MobEffects.DAMAGE_BOOST` + `GoetyEffects.RALLYING`。（仆从那 4 处仍是裸 `addEffect`，本轮未改。）
- **审计结论（药水效果现状）**：
  - **一阶段低谷**：boss 自施 `GoetyEffects.SAPPED`（amp 11）+ `MobEffects.DARKNESS`，
    **每 tick 刷新 = 常驻**，**机制上生效**；但两者都构造为 `visible=false`（**无粒子**）⇒ **玩家看不出效果**。
    仆从的四个效果（`REGENERATION` / `ABSORPTION` / `MOVEMENT_SLOWDOWN` / `WEAKNESS`）**同样只在一阶段低谷**生效。
  - **二阶段不跑 `tickValley`**：`MusicController` 把二阶段的 VALLEY 段**重映射为 BUILDUP**，
    且二阶段会**清空仆从** ⇒ 上述低谷效果**仅一阶段**（与 §七「`phase2ValleyKnockbackMultiplier` 不可达」同根因）。
  - **二阶段自施可达**：`DAMAGE_BOOST` + `RALLYING`（每 40 tick 一次、持续 60 tick）**可达**、
    **不会被 `cleanseEffects` 抹掉**、也**不受亡灵免疫影响**（`TunerBoss` **未覆写 `getMobType`**，
    为 `UNDEFINED` 而非 `UNDEAD`）。`phase2_buffs.phase2BuffsEnabled` 默认 **true**。
    `RALLYING` 按定义**只加近战攻击**，对法系输出无感（代码注释已承认），故另挂了 `SPELL_POTENCY`
    属性 modifier（`MULTIPLY_TOTAL`，值 = `strengthLevel * 0.1`）提升法术伤害。
  - **`SUMMON_DOWN` 免疫确认真实生效**：反编译 Goety 的 `ISummonSpell`，其施加上走
    `LivingEntity.addEffect` → `canBeAffected` → **被本类拒绝** ⇒ `hasSummonDown` **恒 false**，
    召唤冷却免疫不是空转。
  - **局限（必须知晓）**：`tickPhase2Buffs` 自身**零日志** ⇒ 二阶段自施药水的**实际数值收益只能进游戏实测**
    （本轮新增的 WARN 只能证明「没被拒」，**不能**证明「数值符合预期」）。

**（7）索命聚晶加入黑名单（配置侧，不在 jar 内）**

- `focus.blacklist` 由 `goetytwilight:destruction_focus` 改为
  `goetytwilight:destruction_focus, goety:killing_focus`。
- **索命聚晶 = `goety:killing_focus`**（**五重核实**：翻译键 `item.goety.killing_focus` = 索命聚晶、
  注册代码 `ModItems.KILLING_FOCUS`、物品模型、合成配方、手册条目）；本模组把它评为 `attack` /
  `attackScore 0` ⇒ 轮盘**基础权重 2.0**，**确实会被抽到**，而该法术会对施法者**反噬 125%**。
- ⚠️ **这是配置侧改动，不在 jar 内**：`TunerCommonConfig` 里 `blacklist` 的**默认值仍是**
  `goetytwilight:destruction_focus`，本轮改的是**游玩实例的 toml** ⇒ 换实例 / 新玩家**不会**自动带上索命聚晶。
- ⚠️ **可访问性结论（重要）**：`focus.blacklist` **无法在游戏内配置界面修改** —— 本模组的
  `TunerConfigScreen` **顶替**了 Forge 默认的 toml 编辑器，而该界面只有 LLM API Key 与提示词两个输入框。
  **整个 `goetytuner-common.toml` 的所有项都只能手改文件。** 解析规则（`parseBlacklist`）：
  英文逗号分隔、逐段 trim、**大小写敏感**。
- 另：`RUNTIME_BLACKLIST`（运行期自愈拉黑）与配置黑名单是**并集** —— 配置**无法解禁**一个已被
  运行期拉黑的聚晶（要解禁只能重启，或修好那个聚晶的实现）。

---

## 四、关键工程决策与红线（踩坑沉淀）

### 4.1 dev 环境依赖红线（勿再推翻）

1. **run/mods/ 必须为空**：srgtomcp 只映射 minecraft/forge 域，run/mods 正式 jar 保持
   SRG 名，dev 运行期调 MC 成员必崩。
2. **GeckoLib 已彻底移除**（Goety 2.5.56.5 全 jar class 扫描零 geckolib 引用实证推翻
   旧注释）；四个依赖 mod 一律坐标式 `implementation fg.deobf("blank:<mod>:<ver>")`
   + 顶部 flatDir libs/；`fg.deobf(files(...))` 不受支持。
3. **refmap 必须清空**（MCP→SRG 映射在 dev 环境反向致命），所有带 mixin 的 mod 都要清
   （实证：Configured ScreenMixin m_280039_）——`scripts/clear_refmaps.py`。
4. 修改 libs jar 后重跑 compileJava 触发 FG 重建 mapped jar；升级任一 mod 版本 =
   重复「清 refmap → compileJava → runClient 验证」；修改版 jar 仅 dev 测试用。

### 4.2 注册时序坑

- **SpawnEggItem 必须用 ForgeSpawnEggItem**：minecraft:item 的 RegisterEvent 先于
  entity_type fire，原版 SpawnEggItem 在 item 注册 supplier 里立即 `TUNER.get()` 抛
  "Registry Object not present"。ForgeSpawnEggItem 传 RegistryObject（Supplier 延迟解析），
  颜色由 Forge ColorRegisterHandler 自动注册。
- **createAttributes() 不能读 config**：EntityAttributeCreationEvent 在注册阶段 fire，
  config 未加载（"Cannot get config value before config is loaded"）。属性表用默认常量，
  实际数值在实体构造函数按 config 覆盖 + setHealth(getMaxHealth())。
- **服务端读 lang**：Component.translatable().getString() 在服务端返回 key 本身；
  启发式分类从 classpath 读 jar 内 assets/<ns>/lang/en_us.json 缓存。

### 4.3 构建与运行环境

- 必须 JDK 17（Gradle 8.1.1 不支持 Java 21）；必须 PowerShell 跑 `gradlew.bat`
  （git bash 下 xargs 环境过大 + cygpath 失效）；先 `Remove-Item Env:ACC_PRODUCT_CONFIG_V3`。
- PowerShell 重定向日志为 UTF-16，需 python decode 读取；工具 stdout 静默不回显 →
  一律 WriteAllText(UTF8) 落盘 + Read 读回。
- **processResources 编码坑**：mods.toml 加中文后必须 `filteringCharset 'UTF-8'`
  （否则 Windows 默认 GBK 读 UTF-8 文件，全角括号字节被替换成 '?'）。
- 沙箱覆盖层：Remove-Item 报成功但真实文件仍在；bash rm 被 safe-delete genie-trash
  拦（中文路径）→ 删文件用 PowerShell Remove-Item，确认用 git bash ls。
- 打包产物重名坑：新版本必须 bump mod_version（实际序列示例：
  `0.1.0→0.2.0→…→0.7.1→0.7.2→0.0.0→0.0.1→0.0.2→0.0.3→0.0.4→0.0.5→0.0.6→0.0.7→0.0.8→0.0.9→0.0.10→0.0.11→0.0.12→0.0.13`），否则游戏 mods 里
  替换失败用户以为"没变化"；且**旧配置文件锁旧值**，大改默认值需删 toml 重新生成。
  ⚠ 版本号被重置为 0.0.x 后，对外发布排序会小于 0.7.2，后续建议跳到 `1.0.0`。

---

## 五、版本演进时间线（第 1~51 轮浓缩）

| 版本 | 轮次 | 里程碑 |
|---|---|---|
| — | 1~7 | 项目搭建、依赖环境定稿（run/mods 空、refmap 清、坐标式依赖） |
| — | 8~10 | Boss 实体/渲染/基本 AI、乐谱与重音标注初版 |
| — | 11~17 | 音乐条平滑（整数除法蠕动→浮点推进）、二阶段弧形走位、阶段机制 |
| v0.1.0 | 18 | 文件完整性复核 + 作者信息（toniat0 & vibe-coding） |
| — | 19 | GeckoLib 移除、原版渲染方案、重音特效、音乐配置 |
| — | 20 | 音乐接入、二阶段 1.25x、重音 HUD 分样式 |
| — | 21 | 音频客户端化（循环实例，修衔接/重叠/不停三问题） |
| — | 22 | 瞬移追击全程化、二阶段低谷→铺垫、前摇×0.8、索敌 96 |
| — | 23 | Boss 伤害免疫、非玩家瞬移追击 |
| — | 24~25 | 蹲姿映射、施法通道重构（DATA_CAST_STATE） |
| v0.1.0→0.2.0 | 26~28 | 聚晶黑名单双层防护、Boss 数值大改（216 血/锁血/档位）、音乐作者标注、刷怪蛋贴图 |
| v0.3.0 | 29 | 链锤崩溃根治（实体拦截 + 默认黑名单）、蛋贴图重绘 |
| v0.4.0 | 29b | 黑名单输入容错解析（String 归一化）、Configured 中文引导 |
| v0.5.0 | 29c | 聚晶分类修复（ISummonSpell 权威判定）、目光锁定、近战易伤、召唤冷却免疫 |
| v0.6.0 | 31 | 施法方向钉死（三调用点+O字段）、前摇下蹲、逐跳追击、二阶段进场连发 |
| v0.6.1 | 32 | beginCast 起手钉朝向（startSpell 结算前 snap），修无干扰也打偏 |
| v0.6.2 | 34 | 三个 bug 闭环：死亡回弹改覆写 `tick()`+`reviveIfDead()`（原 aiStep 内分支死亡后不可达）、二阶段药水改原版力量+`SPELL_POTENCY`、重音击退旁观白名单 |
| v0.7.0 | 36 | 任务#118 落地：仪式召唤（`goety:ritual_factory`）+ 快照制升级法杖 + tooltip + 经验×4 |
| v0.7.1 | 37 | 仪式参数修正（duration 单位=秒：600→10；soulCost 1→3）+ 模组图标 `logoFile` 移入 `[[mods]]` 块 |
| v0.7.2 | 37b | 图标二次修复（mods.toml 改双引号）+ `/goetytuner tune` 命令 + 二阶段 `SPELL_POTENCY` 法术加成 |
| v0.0.0 | 38 | **版本号重置为正式版初版**；法杖白名单 `WAND_WHITELIST` + `identify()` 重写 |
| v0.0.1 | 39 | 图标热修复（logo 1250×450 → 512×184） |
| v0.0.2 | 40 | 署名「音乐由[乌鸦Producer]提供」+ 项目整理 + 初始化 git（GitHub QieFanQie/GoetyTuner） |
| v0.0.3 | 41 | 配置界面可见性修复（注册 `IConfigScreenFactory`，Mods 菜单出现 Config 按钮）+ LLM 评分 UI 完整化（提示词框/TunerToast/宽松校验） |
| v0.0.4 | 42 | 施法计数缺陷修复（`startSpell` 异常路径不再误调 `onCastFailed`）+ 死代码/注释卫生 + 4 份文档全面回填 |
| v0.0.5 | 43 | LLM 错误报文带 URL/模型；提示词改多行并预填标准模板；性能优化（仆从扫描每 tick 缓存、枚举数组克隆、同步包早退、HUD 早退与预计算、RenderType 缓存、语言缓存瘦身） |
| v0.0.6 | 44 | 限伤（单次伤害上限，默认 25% 最大生命≈54，参照 Goety 本体做法的 actuallyHurt 钳制）+ 限DPS（滑动 1 秒窗口预算，默认关闭） |
| v0.0.7 | 45 | LLM 批量评分改分批请求（修 200 聚晶只应用 25 条）+ 修线程泄漏 + 提示词格式化加固 + 资源改名；实证「锁血机制已隐含限伤，限伤/限DPS 在当前设计下作用有限」 |
| v0.0.8 | 46 | 附属模组变化健壮性加固（扫描逐项兜底、beginCast/instantCast/interrupt/finishCast 全流程兜底、returnEntry 幂等）+ 死亡状态完善（新增 SEntityRevivePacket 复位客户端死亡动画、脏状态只清动画不吃档位、拦截 remove(KILLED)） |
| v0.0.9 | 47 | 为 /kill 打开后门：识别 DamageTypes.GENERIC_KILL 后跳过宽限期免疫/致死截断/死亡回弹/remove 拦截（字节码实证调用链 KillCommand→Entity.kill()→LivingEntity.kill()→hurt(genericKill, MAX_VALUE)） |
| v0.0.10 | 48 | 美术交付：身体贴图按参考图重画（头部区逐像素不变）+ 8 阶色带披风 + 红/蓝/灰三颗悬浮立方体（位掩码高亮 + IdentityHashMap 幂等计数）+ 非对称径向声波涟漪（新增 SAccentWavePacket 通道 id 3，旧粒子默认关闭）+ 刷怪蛋改走原版 template_spawn_egg（紫/亮蓝染色）；网络协议 1.0→**2.0 严格匹配**，联机需双方同版本 |
| v0.0.11 | 49 | 修「每 tick 检测死亡状态」的真 bug：`applyLockHealth()` 里与 `maintainDeathState()` 重复的「死亡自愈」分支**实际可达**（反编译实证 `LivingEntity.tick()` 里 `aiStep()` 只有 1 处无条件调用、`tickDeath()` 由 `baseTick()` 把关；原注释把它与 `serverAiStep()` 混为一谈），它击败 `/kill` 后门（日志：`/kill` 后 48 ms 带 18 血复活）、白吃一档锁血、且不复位客户端死亡动画 ⇒ 已删除该分支并改为死亡时早退，死亡回弹唯一实现为 `tick()`/`maintainDeathState()`。只改版本号 + `TunerBoss`，无新增类/贴图/配置 |
| v0.0.12 | 50 | ① **径向声波涟漪锚定触发瞬间的坐标**（`AccentWaveRenderer` 新增私有 `record Wave(startTick,x,y,z)`；原实现每帧读 Boss 当前位置 ⇒ 涟漪跟着 Boss 跑，而锁血会强制瞬移；清理改为**只按 34 tick 计时**，不再因实体死亡/移除/视野外提前掐掉）。② **音乐条 HUD 外观重做**（`MusicBarHud`：`260×6`→**`204×8`**、底距 64→62、硬边纯色块→**逐行混色**+2px 过渡缝、单一硬底→**三层柔和投影**（四角留空模拟圆角）、阶段**文字**→**像素符号** `● ● ●`/`●`/`- - - - - -`（实测本客户端字体无 U+26AA 字形，直接写 `⚪` 会显示空白方块）、刻度 `0xB8FFFFFF` 与闪烁峰值 `0x88` 调淡；一阶段分段内容按条宽做 scissor 裁剪防溢出）。③ **`CastChannel` 回调成对性修复**（`logCast` 提到 `onCastStart` 之前 + `startEmitted` 兜底补发 `onCastFailed`，消除「立方体永久高亮 / 蹲姿卡住」隐患）。另修 `applyLockHealth()` 的 javadoc（仍在描述 0.0.11 已删行为，纯注释）。无新增类/贴图/配置项 |
| v0.0.13 | 51 | ① **修「玩家被击杀复活后（未走出索敌范围）Boss 背景音乐丢失」**（`BossMusicManager`）：原先只以静态字段 `music != null` 判定「在播」、**从不与声音引擎核对**，而死亡/复活会让引擎把循环实例悄悄摘除（RECORDS 音量 0 / channel 停止 / `SoundEngine.reload()→destroy()→stopAll()` / `play()` 未 loaded 时静默返回），字段却仍非 null ⇒ 服务端 `playing` 为 true 时 `startMusic` **永不重入** = **永久静音**；且 `onPlaySound` 同样只看字段 ⇒ **连原版 BGM 也一起被永久取消**（症状「整个 BGM 都没了」）。改为每 tick 用 `SoundManager.isActive(music)` 核实 + **10 tick 防抖**，失效即清空字段、下一 tick 自愈重建（`onPlaySound` 判据同步）。② **重音标记滚动平滑 + 高潮改细小长条**（`MusicBarHud`）：`fill()` 只能落在整数像素而刻度是 1~2px 细条 ⇒ 取整后逐像素跳动；改为**亚像素覆盖**（小数部分按比例摊到相邻两列，亮度重心连续移动）；高潮「中」字改**小长条**（高潮 2×6 / 低谷 1×6 / 铺垫 1×4，竖向居中）。③ **涟漪多波共存**（`AccentWaveRenderer` 的 `Map` 改 `List` + `MAX_WAVES=32`）：二阶段进场重音每 5 tick 一发，原先后一发顶掉前一发、只看到一条波反复重播。④ **新增配置 `music.accentDensityDivisor`（默认 3、范围 1~9）**：服务端加载乐谱时「每 N 个保留 1 个」抽稀 ⇒ HUD 刻度/击退/涟漪/提示音**一起**变稀疏、客户端零改动（默认乐谱 37 → **13**，INFO `Accent thinning x3: 37 -> 13 accents`）；配置 **61 → 62 项**（`music` 段 12 → 13）。⑤ **立方体高亮改「向白插值」**（原 `min(1, color*(1+glow*0.8))` 因分量近 1 被截断、几乎无变化）+ **两层 `entityTranslucentEmissive` 自发光外壳**当发光（原版发光描边是**整实体级** `OutlineBufferSource`，无法只描一颗立方体）。⑥ **药水可观测性**：8 处 `addEffect` 返回值原先全被丢弃、被 `canBeAffected`/`MobEffectEvent.Applicable` 拒绝时静默失效 ⇒ 新增 `applySelfEffect` 打 WARN（已用于 boss 自身 4 处），并完成药水现状审计（低谷效果仅一阶段且 `visible=false` 无粒子、二阶段自施可达且不受亡灵免疫、`SUMMON_DOWN` 免疫确认真实生效、`tickPhase2Buffs` 零日志故数值收益需实测）。⑦ 配置侧把 `goety:killing_focus`（索命聚晶，对施法者反噬 125%）加入 `focus.blacklist`（**配置侧改动、不在 jar 内**；且该 toml **无法在游戏内配置界面修改**）。**只改代码，不动任何贴图/资源**；`.java` 仍 45 个、jar 仍 **102** 条目 |

---

## 六、构建与部署

```bash
# 环境准备（PowerShell，勿用 git bash）
Remove-Item Env:ACC_PRODUCT_CONFIG_V3
& "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot\bin\java" -version   # 确认 JDK17
.\gradlew.bat build          # 产物 build/libs/goetytuner-<ver>.jar

# 验证（reobf jar 覆写方法显示 SRG 名，字符串检查会漏报 → 用 javap 验证方法表）
# 部署：删游戏 mods 旧 jar → 放新 jar；大改默认值须删 config/goetytuner-common.toml
# 0.0.10 例外：只需手改一行——accentParticles 是【已有键】，Forge 不会用新默认值覆盖老 toml，
#   请把 accentParticles 改成 false（新键 accentWave 会自动补齐，无需删 toml）；详见 §3.5。
# 同步：D:\tiaolvshi\goety-tuner（主副本：git 仓库 / 权威源码）
#       → robocopy src /MIR → D:\测试\tiaolvshi\goety-tuner（编译/运行副本，只用于构建）
# 游戏实例：versions\测试（Forge 47.4.23，实例内 Goety 2.5.56.5，与开发依赖一致）
#           versions\灾厄巫咒 是旧实例（Forge 47.4.16 / Goety 2.5.56.3），勿部署
```

**当前部署产物**（每次部署后更新本表，便于核对"游戏里跑的到底是哪个构建"）：

| 版本 | 文件 | 大小 | md5 | 部署位置 |
|---|---|---|---|---|
| 0.0.13 | `goetytuner-0.0.13.jar` | 1,753,132 B | `AE59EBFE8CF0F2D09C16453A515CE7F5` | `versions\测试\mods\`（该目录只保留这一个 goetytuner jar；旧 0.0.12 已删） |

> 本轮（0.0.13）jar 内共 **102 条目**（与 0.0.12 一致 —— 本轮**无新增/删除类**，只是既有类被改写）；
> 构建在主副本就地 `gradlew clean build` 成功（50 s，仍只有原有 3 条 Forge 弃用警告）。

> 部署前务必确认**没有 java 进程在运行**（jar 被占用会导致替换静默失败）；
> 部署后需**重启游戏**才会加载新 jar。

> ⚠️ **jar 的 md5 不可复现，别把 md5 当成"源码是否一致"的判据**（实证）：
> Forge 会把构建时刻写进 `META-INF/MANIFEST.MF` 的 `Implementation-Timestamp`，因此**源码一字不改地重建，md5 也会变**。
> 曾实测：改 5 处**注释文字**（行数不变）后重建，jar 大小 1,737,090 → 1,737,089 B、md5 全变；
> 但逐条目比对 97 个条目，**除 `MANIFEST.MF`（仅时间戳不同）外 96 个条目字节完全一致**（含 `TunerBoss.class` 的 sha256）。
> **0.0.12 又复现一次，而且更直白——本轮连构三次、三个 md5**：
> `5A63F73F13C102AAD9249F8B49A9763A`（1,752,059 B）→ `148F359EFD69102FC87971D1B9249270`（1,752,059 B）
> → `66F14DFD926A068AA3284360976B78BB`（1,752,068 B）；
> **前两次源码完全相同、连 jar 大小都一模一样，md5 却不同**（只有 `MANIFEST.MF` 的时间戳变了）；
> 第三次确实又改了一处 HUD 裁剪（一阶段分段加 scissor），故大小也变了。
> 要判断"部署的 jar 是否对应当前源码"，应比对**条目内容**（或 `Implementation-Version`），而非整体 md5。
>
> **0.0.13 追加一条构建运维经验**：本轮 build 前因 **Gradle 守护进程锁残留**出现过一次构建**挂起**
> （不报错、也不推进，看起来像卡死），执行 **`gradlew --stop`** 清理守护进程后即恢复正常。
> 注意 `gradle.properties` 里虽有 `org.gradle.daemon=false`，**也不能完全避免**残留的 worker / 文件锁。

工具脚本（scripts/）：`clear_refmaps.py`（清 refmap）、`replace_mixin_classes.py`
（FG 反混淆 jar 的 mixin 类替换）、`mods-backup/`（原版 jar 备份）、
`goety-2.5.56.5-devfix.jar`（dev 测试专用）。

---

## 七、遗留问题与后续方向

1. **二阶段切换音频重叠**：第二十一轮客户端化后已基本解决（阶段切换不再重启音频实例），
   仍保留 EntitySoundInstance 备选方案；音量>1 对无衰减实例是线性增益放大可能失真。
2. **DoT 伤害归因**、**召唤物 owner 识别**（E4/E5，低优先级）。
3. 哪些聚晶需永久写入配置黑名单——以运行日志 ERROR 行为准。**代码默认值仅
   `goetytwilight:destruction_focus`**；**0.0.13 起游玩实例的 toml 额外拉黑了 `goety:killing_focus`**
   （索命聚晶，对施法者反噬 125%）——注意这是**配置侧**改动、**不在 jar 内**（源码默认值未变），
   换实例 / 新玩家**不会**自动生效。另：`focus.blacklist`（乃至整个 `goetytuner-common.toml`）
   **无法在游戏内配置界面修改**（`TunerConfigScreen` 顶替了 Forge 的 toml 编辑器，只有 API Key 与提示词两个输入框），
   **只能手改文件**；且 `RUNTIME_BLACKLIST` 与配置黑名单是**并集**，配置**无法解禁**一个已被运行期拉黑的聚晶。详见 §3.14(7)。
4. Boss 专属魔杖（C 计划）、等效护甲显示（A4）、死亡/受击音效（E7）未实施——
   E7 现状：`getAmbientSound` / `getDeathSound` / `getHurtSound` 三者均 `return null`，
   代码内带 TODO 注释。
5. 挂载在 goetytwilight 等附属上的兼容测试（D 计划）待扩展。
6. **0.0.10 美术项待游戏验收**：披风摆动、三颗立方体轨道与施法高亮（含高潮多通道同时点亮）、
   透明声波与地形/水面/着色器的交互、原版刷怪蛋并排观感，均**尚未进游戏实测**——
   离线脚本只证明了尺寸/参数/逐像素一致与代码编译通过，不能替代画面验收
   （详见 `ART_ASSETS_REPORT.md`「验证与待验收」）。
   ✅ **0.0.12 新增同期待验收项**：音乐条新外观（204×8 / 逐行混色 / 三层柔和投影 / 像素阶段符号）
   与**涟漪锚定触发点坐标**的观感同样**未进游戏画面验收**（本轮只做到编译通过 + `javap` 字节码核对）。
   ~~另**发现但未改动**的既有边界（0.0.10 轮发现，0.0.11 轮同样未触碰施法流程，故保留现状）：`CastChannel` 在开始回调之后仍有日志调用，
   异常路径下外层兜底的回调可能不配平。~~
   ⇒ **本条已于 0.0.12 修复**：`BossWandHelper.logCast` 移到 `onCastStart` **之前**（使其成为 `return` 前最后一步），
   并在 `beginCast` 的兜底 `catch` 里用 `startEmitted` 标志补发 `onCastFailed` 让计数平衡，详见 §3.13(3)。
   ✅ **0.0.13 新增同期待验收项**：① **音乐丢失修复**需按真机路径验证（被击杀 → 在死亡界面停留不同时长 → 复活 → 未脱战时音乐应自动恢复）；
   ② 音乐条**亚像素刻度**滚动是否真的平滑（不再逐像素跳）；③ 涟漪**多波共存**的层叠外扩观感；
   ④ 立方体高亮「变白变亮」与两层自发光外壳的发光强度是否合适；⑤ `music.accentDensityDivisor=3` 下的重音密度手感
   （太密 / 太疏都调这一项）。以上均**尚未进游戏画面验收**（本轮只做到编译通过 + md5/条目核对）。
7. **药水效果的可观测性与现状（0.0.13 结论）**：boss 自身 4 处自施已改走 `applySelfEffect`（被拒打 WARN），
   但 **`tickPhase2Buffs` 自身零日志** ⇒ 二阶段自施药水的**实际数值收益仍只能进游戏实测**；
   一阶段低谷的 `SAPPED` / `DARKNESS` 与仆从四效果虽**机制上生效**，但都构造为 `visible=false`（**无粒子**），
   **玩家看不出效果** —— 是否补粒子 / 加提示属**表现层待决策项**（不是 bug）。详见 §3.14(6)。

### 已知缺陷与待办（0.0.13 时点）

以下为 0.0.4 复核代码后新确认、到 **0.0.13** 时点仍未修复的问题（个别条目已在此期间解决，见条目标注）：

> 性能类问题的处理见 §3.7——「仆从全量扫描」「`BossPhase.values()` 数组克隆」「同步包无谓构造」
> 「HUD 每帧全实体扫描」等均已在 0.0.5 优化完毕，不再列入下表。

1. `SummonScoreTracker` 的两个期望基线硬编码：`dpsExpectation = 3.0`、
   `survExpectation = 8.0`，源码注释已标 TODO 配置项，但尚未接入 toml。
2. `CastChannel.beginCast` 中 `BossWandHelper.installFocus(..., null)` 的**附魔注入路径未启用**
   （第三参数恒为 null，TODO：`focus_enchants.json` 附魔表）。
3. `music.phase2ValleyKnockbackMultiplier` 在二阶段**不可达**——二阶段低谷已被
   `MusicController` 映射为铺垫（`getPhase()` / `getSegments()` 两处），`onAccent` 的
   `case VALLEY` 分支在二阶段永不命中，该配置项仍暴露在 toml 中产生误导。
   ⚠️ **0.0.13 补充**：这与 §3.14(6) 里「二阶段不跑 `tickValley` ⇒ 低谷药水效果仅一阶段」是**同一根因**
   （`MusicController` 把二阶段的 VALLEY 段重映射为 BUILDUP）。缓解方式：要么在 toml 里把它注释说明，
   要么后续直接**删除**该键（属破坏性配置变更，需评估）。
4. `MusicBarHud.BACKGROUND_TEXTURE` 指向不存在的 `textures/gui/music_bar.png`
   （`assets/goetytuner/textures/` 下只有 entity/ 与 item/）；该常量目前仅作为美术接入点注释存在、
   无代码引用（同节的 `ACCENT_U` 常量现已不存在）。
5. `LLMClassifier` 在 common 代码里 import 了客户端类
   `net.minecraft.client.resources.language.I18n`——当前不会崩（服务端不调用该路径），
   但服务端若调用 `collectFocusDescriptions()` 会 `NoClassDefFoundError`。
6. ~~`focus_classification.json` 只有 3 条示例条目、LLM 批量评分尚未真正跑过一轮~~
   ✅ **已解决**（离线脚本先写入 252 条，0.0.7 又修好游戏内大规模覆盖问题）：当前**共 277 条**评分
   （252 条离线 + 0.0.6 那轮游戏内「开始评分」追加的 25 条；0.0.7 的分批修复见 §3.9）。
   0.0.5 的失败根因是 `llm.apiUrl` 仍指向不可达的 `api.openai.com`——在连接阶段即超时、
   请求根本没到服务器，换任何 API Key 报错相同（错误报文现已带目标 URL/模型名，见 §3.5）。
   0.0.7 起游戏内批量评分改为**分批请求**，修复「200 个聚晶只应用 25 条」，见 §3.9。
7. 联机场景服务器/客户端 common config 不互通——音量取客户端本地配置；
   pitch 以同步包 speed 优先。单人/LAN 无碍。

---

*本文档由开发记忆库自动整理，与 DEVELOPMENT_PLAN.md / README.md 互为补充；*
*本文件是**技术现状的最新权威文档**，优先级高于 DEVELOPMENT_PLAN.md（后者是阶段性计划书，
进度表可能滞后）。*
*两副本（D:\tiaolvshi\goety-tuner 与 D:\测试\tiaolvshi\goety-tuner）需保持同步。*
