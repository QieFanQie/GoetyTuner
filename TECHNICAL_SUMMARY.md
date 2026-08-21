# Goety Tuner（调律师）技术摘要

> 版本：v0.6.1 ｜ 整理日期：2026-08-20 ｜ 覆盖轮次：第 1~32 轮
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
| 源码规模 | 36 个 Java 源文件，包根 `com.tiaolvshi.goetytuner` |

**定位**：为 Goety 提供一位可召唤的 Boss「调律师」。Boss 不持有实体法杖，而是通过
Goety 的聚晶(Focus)体系施法——从全体聚晶池中抽签施放玩家装填的法术，并按音乐
(乐谱/重音)驱动三阶段战斗节奏。

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
│   ├── TunerBoss.java               # Boss 主体（AI 状态机 / 阶段 / 战斗数值）
│   ├── BossPhase.java               # 三阶段枚举（铺垫/高潮/低谷）
│   ├── MusicController.java         # 服务端乐谱推进 / 阶段转换 / 重音触发
│   └── ai/CastChannel.java          # 施法通道（前摇/高潮并行通道/瞬发）
├── focus/
│   ├── FocusPoolManager.java        # 聚晶池构建 / 抽签 / 锁池归还 / 黑名单
│   ├── FocusEntry.java              # 聚晶条目（id/分类/标签）
│   ├── FocusCategory.java           # ATTACK / UTILITY / SUMMON / BUFF / DEBUFF
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
│   ├── TunerConfigScreen.java       # Configured 配置屏入口
│   ├── ClientSetup.java             # 图层定义注册 / 渲染器绑定
│   └── render/                      # TunerRenderer/TunerModel/TunerCape*
├── network/
│   ├── TunerNetwork.java            # 通道注册
│   ├── SMusicSyncPacket.java        # 乐谱/进度/speed 同步（服务端→客户端）
│   └── SShakePacket.java            # 镜头震动同步
└── config/
    └── TunerCommonConfig.java       # 全部可调参数（common toml）
```

### 2.2 数据流（一图流）

```
服务端                               客户端
TunerBoss.aiStep ─┐
  ├─ 乐谱推进 MusicController.tick ──→ SMusicSyncPacket(进度/speed/分段) ──→ MusicStateClient.smoothed()
  ├─ 重音命中 onAccent ──────────────→ 击退+粒子+音效（服务端）  ／        └─→ MusicBarHud 渲染
  │                                   └→ SShakePacket(震动) ──→ ClientCameraShake
  ├─ 抽签 CastChannel ── FocusPoolManager.draw → spell.mobSpellResult ─→ Goety 法术生效
  └─ 阶段转换 enterPhase2 ── 自buff/回血/进场连发 ──→ 客户端音频 pitch 切换
```

---

## 三、核心系统设计

### 3.1 Boss AI 与战斗状态机

- **索敌与仇恨**：`targetRange = 96`（FOLLOW_RANGE 常量同步）；`hasAggro` 门控 AI 主循环；
  脱战边缘检测（玩家跑远/死亡）→ 回血 + 乐谱重启对齐。
- **三阶段节奏**（由音乐驱动而非纯计时器）：
  - 一阶段：铺垫 → 高潮（三并行施法通道，聚晶池高权重抽签）→ 低谷（清正面效果）→ 铺垫 循环；
  - 二阶段：高潮结束后低谷**替换为铺垫**（节奏加快、无低谷喘息）；
    每 40tick 自 buff（强健3/重振5，lockMark≥10 升级 6/8）；lockMark 7~12 按档位回血。
- **瞬移走位**：`tickTeleport`（玩家目标）与 `tickTeleportNonPlayer`（无参战玩家时追非玩家目标）
  独立计时器；aiStep 全程判定（距离区间 + 间隔）。
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
- **嘲讽机制**：原地嘲讽循环（蹲起 + 音效），高潮施法中不触发。

### 3.2 施法系统（Goety 聚晶集成）

- **聚晶池**：扫描所有已注册 Focus（Goety 本体 + 附属），按 `FocusClassifier` 分类
  （ATTACK/UTILITY/SUMMON/BUFF/DEBUFF）→ 各阶段按权重抽签（高潮攻击池权重 222）。
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
  一/二阶段共用，二阶段以 pitch 1.25 加速。WAV→OGG 必须**分块流式写入**
  （soundfile 一次性 sf.write 大文件崩溃）。
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
- `TunerCommonConfig`：common toml，约 30 项（boss.* / music.* / focus.* / phase2.*），
  Configured 中文分类引导；`music_score.json`、`focus_classification.json` 运行时双写。

### 3.6 战斗数值（v0.5.0 / v0.6.0 调整后）

- 血量 216（12 档 × 18），锁血间隔 18，二阶段入口=第 6 档（108 血=半血）。
- 蓄力上限 50tick（2.5s）；二阶段前摇 = `clamp(1.0-(lockMark-6)*0.1, 0.1, 1.0)` 递减。
- 近战易伤 `meleeVulnerability`(0.25)：hurt 中 isDirectMelee（player_attack/mob_attack
  或 direct==entity 且非投射非爆炸）×(1+倍数)。
- Boss 身份免疫：摔落/火焰(IS_FIRE 标签)/窒息/溺水 → 免疫；魔法/爆炸/普攻正常吃。
- 召唤冷却免疫：`canBeAffected` 拒绝 GoetyEffects.SUMMON_DOWN。

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
- 打包产物重名坑：新版本必须 bump mod_version（0.1.0→0.2.0→…），否则游戏 mods 里
  替换失败用户以为"没变化"；且**旧配置文件锁旧值**，大改默认值需删 toml 重新生成。

---

## 五、版本演进时间线（第 1~32 轮浓缩）

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
```

工具脚本（scripts/）：`clear_refmaps.py`（清 refmap）、`replace_mixin_classes.py`
（FG 反混淆 jar 的 mixin 类替换）、`mods-backup/`（原版 jar 备份）、
`goety-2.5.56.5-devfix.jar`（dev 测试专用）。

---

## 七、遗留问题与后续方向

1. **二阶段切换音频重叠**（第二十一轮已客户端化后基本解决，仍保留 EntitySoundInstance
   备选方案；音量>1 对无衰减实例是线性增益放大可能失真）。
2. **DoT 伤害归因**、**召唤物 owner 识别**（E4/E5，低优先级）。
3. 哪些聚晶需永久写入配置黑名单——以运行日志 ERROR 行为准（目前默认仅
   `goetytwilight:destruction_focus`）。
4. 联机场景：服务器/客户端 common config 不互通（音量/音调取客户端本地配置；
   pitch 实际用同步包 speed 优先）——单人/LAN 无碍。
5. Boss 专属魔杖（C 计划）、等效护甲显示（A4）、死亡/受击音效（E7）未实施。
6. 挂载在 goetytwilight 等附属上的兼容测试（D 计划）待扩展。

---

*本文档由开发记忆库自动整理，与 DEVELOPMENT_PLAN.md / README.md 互为补充；*
*两副本（D:\tiaolvshi\goety-tuner 与 D:\测试\tiaolvshi\goety-tuner）需保持同步。*
