# Goety Tuner（调律师）技术摘要

> 版本：0.0.7 ｜ 整理日期：2026-09-19 ｜ 覆盖轮次：第 1~45 轮
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
| 源码规模 | 39 个 Java 源文件（约 303 KB），包根 `com.tiaolvshi.goetytuner` |

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
│   ├── TunerBoss.java               # Boss 主体（1671 行核心类：AI 状态机 / 阶段 / 战斗数值）
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
│   ├── BossMusicManager.java        # 客户端循环音乐实例（FORGE 总线）
│   ├── MusicStateClient.java        # 音乐进度本地平滑推进（×speed）
│   ├── MusicBarHud.java             # 音乐条 HUD（分段色块/重音刻度/指针）
│   ├── ClientCameraShake.java       # 重音镜头震动
│   ├── TunerConfigScreen.java       # Configured 配置屏入口（多行提示词框，预填标准模板）
│   ├── TunerToast.java              # 客户端 Toast（LLM 评分/命令结果提示）
│   ├── ClientSetup.java             # 图层定义注册 / 渲染器绑定
│   └── render/                      # TunerRenderer/TunerModel/TunerCape*
├── network/
│   ├── TunerNetwork.java            # 通道注册
│   ├── SMusicSyncPacket.java        # 乐谱/进度/speed 同步（服务端→客户端）
│   └── SShakePacket.java            # 镜头震动同步
├── config/
│   └── TunerCommonConfig.java       # 全部可调参数（common toml，60 项 / 10 个 section）
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
  │                                   └→ SShakePacket(震动) ──→ ClientCameraShake
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
- **速度化时间轴**：`MusicController.progressF` 浮点推进 ×=pitch（getSpeed）；
  speed 随 SMusicSyncPacket 下发，客户端 `smoothed()` 本地推进 ×speed；
  跨多 tick 时 `crossedAccent` 逐 tick 查重音（Set，含翻页回绕两段检查）。
- **HUD**：260×6 音乐条，分段配色（铺垫蓝 0xFF5B9BE0 / 高潮橙 0xFFF26A4B / 低谷紫 0xFFB068E8）；
  指针与二阶段判定线 5px；重音刻度分阶段样式（铺垫细线 / 高潮「中」字 / 低谷加粗）；
  本地越线检测触发亮黄描边闪烁 8 帧渐隐（免额外网络包）。
- **重音特效**（服务端 onAccent）：三波 END_ROD 同心冲击环 + 12 个 NOTE 音符爆发 +
  原版紫水晶音（阶段差异化音调 0.9/1.4/0.6）；二阶段进场连发 6 次
  `accentKnockbackPulse`（击退+冲击环+音效，每 5tick 一发，音调递升 0.8+0.15i）。

### 3.4 渲染（原版模型方案，无 GeckoLib）

- Boss 用原版 `HumanoidMobRenderer` + 自定义贴图（PIL 生成，anaconda python 才有 PIL）：
  tuner.png 64×64（深紫渐变躯干 + 头部径向渐变 + 透明帽子层）、tuner_cape.png 64×32。
- 自定义披风层：`TunerCapeModel`（EntityModel 子类，`LAYER_LOCATION` 必须
  **ModelLayerLocation**，须实现 renderToBuffer 委托）；`TunerCapeLayer` 用
  `body.translateAndRotate` 跟随躯干，`Axis.XP` 正角度 = 下摆向身后(+z)摆。
- 刷怪蛋：16×16 圆润椭圆蛋形贴图（黑紫渐变 + 双紫斑 + 高光阴影）+ 无 tintindex 的
  `models/item/tuner_spawn_egg.json`（不被 ForgeSpawnEggItem 自动染色）。
- HUD 裁剪：`enableScissor(GuiGraphics)` 裁剪音乐条可视区。

### 3.5 网络与配置

- `TunerNetwork` 通道：SMusicSyncPacket（进度/speed/分段/演奏实体集）、SShakePacket。
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
- `TunerCommonConfig`：common toml，**60 项、10 个 section**——`boss`(19) / `phase2_buffs`(3) /
  `summon`(4) / `scoring`(3) / `casting`(10) / `focus`(1) / `wand_whitelist`(1) /
  `music`(11) / `llm`(2) / `wand_upgrade`(6)，
  Configured 中文分类引导；`music_score.json`、`focus_classification.json` 运行时双写。

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

日志实证（`versions\测试\logs\latest.log`，同一份 log）：

```
14:19:54.896 Lock health → lock at 198.0 (mark=1/12)
14:19:55.545 → 180.0 (mark=2/12)
14:19:56.097 → 162.0 (mark=3/12)
14:19:56.697 → 144.0 (mark=4/12)     ... 到 14:19:48 那轮共 11 档约 10 秒
```

**每档 ≈ 0.5~1.0 秒 = `lockGraceTicks`(10t=0.5s) + 玩家下一次命中的间隔**，与伤害量无关。同一份日志里
14:18:59→14:19:07、14:19:16→14:19:54、14:20:33→14:21:11、14:23:04→14:24:24、14:30:17→14:30:26
反复出现同样的阶梯，说明整场战斗约 10~20 秒。

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

**（2）限DPS**：`TunerBoss` 内有一个 `float[20]` 环形缓冲（槽位 = `gameTime % 20`，槽内只存该 tick 生效的伤害）
+ `damageWindowSum` 总和；每次结算先 `advanceDamageWindow(now)` 把 `(lastTick, now]` 这些 tick 的旧槽位清零，
再算 `预算 = 上限 - 窗口内已造成伤害`：预算 > 0 → 本次伤害取 `min(伤害, 预算)` 并把生效值记入当前 tick 槽位；
预算 ≤ 0 → 本次伤害被**完全吸收**（`applyDpsCap` 返回 -1，`actuallyHurt` 直接 return 不调 super）。
默认关闭的原因：与限伤不同，限DPS 没有「天然宽松值」，合适数值取决于希望这场战斗最短打多久——
按默认 216 血、纯输出估算，**20 ≈ 11 秒 / 30 ≈ 7 秒 / 40 ≈ 5.4 秒**。
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
  `0.1.0→0.2.0→…→0.7.1→0.7.2→0.0.0→0.0.1→0.0.2→0.0.3→0.0.4→0.0.5→0.0.6→0.0.7`），否则游戏 mods 里
  替换失败用户以为"没变化"；且**旧配置文件锁旧值**，大改默认值需删 toml 重新生成。
  ⚠ 版本号被重置为 0.0.x 后，对外发布排序会小于 0.7.2，后续建议跳到 `1.0.0`。

---

## 五、版本演进时间线（第 1~45 轮浓缩）

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

---

## 六、构建与部署

```bash
# 环境准备（PowerShell，勿用 git bash）
Remove-Item Env:ACC_PRODUCT_CONFIG_V3
& "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot\bin\java" -version   # 确认 JDK17
.\gradlew.bat build          # 产物 build/libs/goetytuner-<ver>.jar

# 验证（reobf jar 覆写方法显示 SRG 名，字符串检查会漏报 → 用 javap 验证方法表）
# 部署：删游戏 mods 旧 jar → 放新 jar；大改默认值须删 config/goetytuner-common.toml
# 同步：D:\tiaolvshi\goety-tuner（同步副本）↔ D:\测试\tiaolvshi\goety-tuner（编译/运行副本）
# 游戏实例：versions\1.20.1-Forge_47.4.23 已改名 versions\测试（实例内 Goety 2.5.56.5，与开发依赖一致；Forge 47.4.23）
```

工具脚本（scripts/）：`clear_refmaps.py`（清 refmap）、`replace_mixin_classes.py`
（FG 反混淆 jar 的 mixin 类替换）、`mods-backup/`（原版 jar 备份）、
`goety-2.5.56.5-devfix.jar`（dev 测试专用）。

---

## 七、遗留问题与后续方向

1. **二阶段切换音频重叠**：第二十一轮客户端化后已基本解决（阶段切换不再重启音频实例），
   仍保留 EntitySoundInstance 备选方案；音量>1 对无衰减实例是线性增益放大可能失真。
2. **DoT 伤害归因**、**召唤物 owner 识别**（E4/E5，低优先级）。
3. 哪些聚晶需永久写入配置黑名单——以运行日志 ERROR 行为准（目前默认仅
   `goetytwilight:destruction_focus`）。
4. Boss 专属魔杖（C 计划）、等效护甲显示（A4）、死亡/受击音效（E7）未实施——
   E7 现状：`getAmbientSound` / `getDeathSound` / `getHurtSound` 三者均 `return null`，
   代码内带 TODO 注释。
5. 挂载在 goetytwilight 等附属上的兼容测试（D 计划）待扩展。

### 已知缺陷与待办（0.0.7 时点）

以下为 0.0.4 复核代码后新确认、到 **0.0.7** 时点仍未修复的问题（个别条目已在此期间解决，见条目标注）：

> 性能类问题的处理见 §3.7——「仆从全量扫描」「`BossPhase.values()` 数组克隆」「同步包无谓构造」
> 「HUD 每帧全实体扫描」等均已在 0.0.5 优化完毕，不再列入下表。

1. `SummonScoreTracker` 的两个期望基线硬编码：`dpsExpectation = 3.0`、
   `survExpectation = 8.0`，源码注释已标 TODO 配置项，但尚未接入 toml。
2. `CastChannel.beginCast` 中 `BossWandHelper.installFocus(..., null)` 的**附魔注入路径未启用**
   （第三参数恒为 null，TODO：`focus_enchants.json` 附魔表）。
3. `music.phase2ValleyKnockbackMultiplier` 在二阶段**不可达**——二阶段低谷已被
   `MusicController` 映射为铺垫（`getPhase()` / `getSegments()` 两处），`onAccent` 的
   `case VALLEY` 分支在二阶段永不命中，该配置项仍暴露在 toml 中产生误导。
4. `MusicBarHud.BACKGROUND_TEXTURE` 指向不存在的 `textures/gui/music_bar.png`
   （`assets/goetytuner/textures/` 下只有 entity/ 与 item/）；该常量目前仅作为美术接入点注释存在、
   无代码引用（同节的 `ACCENT_U` 常量现已不存在）。
5. `LLMClassifier` 在 common 代码里 import 了客户端类
   `net.minecraft.client.resources.language.I18n`——当前不会崩（服务端不调用该路径），
   但服务端若调用 `collectFocusDescriptions()` 会 `NoClassDefFoundError`。
6. ~~`focus_classification.json` 只有 3 条示例条目、LLM 批量评分尚未真正跑过一轮~~
   ✅ **已解决**（评分写入在 0.0.6 前完成，0.0.7 又修好大规模覆盖问题）：现已有 **252 条**评分
   （离线脚本 `scripts/llm_score_foci.py` + 游戏内「开始评分」双路径）。
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
