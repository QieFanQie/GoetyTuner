# 调律师 (The Tuner) — 诡厄巫法附属Boss · 开发计划书 V2

> MC 1.20.1 Forge 47.3.22 · 附属模组（依赖 Goety 2.5.56.5）· 当前版本 0.1.0
> 更新日期：2026-08-19
> 作者：toniat0 & vibe-coding · 团队：Goety Tuner Project · <https://github.com/QieFanQie/>
> 许可证：MIT License

---

## 一、项目概述

**调律师**：人形无头指挥家Boss，头部位置只有一枚飘动的黑色立方。它以"演奏"的方式轮番使用诡厄巫法及其附属注册的**所有聚晶（Focus）**，战斗由三段式音乐（铺垫/高潮/低谷）驱动。

当前状态：**程序框架完整可运行**，核心战斗逻辑（聚晶池/评分/音乐同步/锁血/阶段切换/效果清除）均已实现并通过实测，待接入美术/音乐资源并做兼容性打磨。

---

## 二、完成度总览

### 已完成（可运行）

| 模块 | 状态 | 说明 |
|---|---|---|
| 项目骨架 | ✅ | 主类/注册/配置/网络/资源占位 |
| 聚晶池系统 | ✅ | 4功能池+4冷却池+轮盘赌+满员迟滞 |
| 静态分类表 | ✅ | `focus_classification.json`（含API Key存储） |
| 启发式分类器 | ✅ | 按lang描述关键词自动兜底分类（124个聚晶） |
| LLM自动分类 | ✅ | OpenAI兼容HTTP异步请求+JSON写回+ConfigScreen按钮触发 |
| 攻击动态评分 | ✅ | 200tick归因窗口+滚动DPS均值（直伤；DoT未识别） |
| 召唤动态评分 | ✅ | 输出/生存二维评分+护甲/血量/效果综合 |
| Boss实体 | ✅ | 目标选择/传送(异常修正+主动追击)/施法通道 |
| 音乐定时器 | ✅ | `music_score.json`分段表+重音表+循环推进 |
| 阶段行为 | ✅ | 铺垫轮换/高潮三通道并行/低谷禁施法 |
| 锁血V2 | ✅ | 7次阶梯锁血+致死拦截+死亡自愈+旧档迁移 |
| 二阶段 | ✅ | 半血触发+音乐切换+打断+数据同步 |
| 效果清除 | ✅ | 低谷清正面/高潮清负面（本次新增） |
| 重音系统 | ✅ | 分阶段斥力+无前摇瞬发施法 |
| 仆从管理 | ✅ | 数量上限+迟滞恢复+伤害归因 |
| 客户端HUD | ✅ 占位 | 节奏条三色分段+指针+二阶段反向（色块渲染，无贴图） |
| 网络同步 | ✅ | SMusicSyncPacket 20tick推送进度/阶段/二阶段 |
| 占位纹理 | ✅ | 64x64 PNG（黑立方头+紫眼+紫领带） |
| 占位渲染器 | ✅ | HumanoidMobRenderer + PLAYER模型层 |
| 配置系统 | ✅ | 22项可调参数（toml） |
| 实测验证 | ✅ | quickPlay自动进档验证通过（第11轮） |

### 未完成

| # | 模块 | 优先级 | 说明 |
|---|---|---|---|
| A | 美术资源与模型 | 高 | ~~GeckoLib迁移~~（已废弃，走原版渲染）、材质、粒子、GUI贴图 |
| B | 音乐资源与标注 | ✅已完成 | 第二十轮接入实测曲目+分段/重音标注；第二十一轮音频客户端化 |
| C | Boss专属魔杖 | 中 | 替代dark_wand占位、专属施法特效 |
| D | 兼容性测试与适配 | 中 | 其他Goety附属mod共存测试 |
| E | 逻辑完善 | 中 | 见下方详细列表 |

---

## 三、未完成项详细计划

### A. 美术资源与模型（阶段8）

**目标**（【2026-08-19 第十九轮修订】放弃 GeckoLib，改用**原版 HumanoidModel + 自定义贴图 + RenderLayer**）：
Goety 2.5.56.5 本体经全 jar class 扫描实证**零 geckolib 引用**，本项目代码亦从未使用
GeoEntity/GeoEntityRenderer，依赖已彻底移除（build.gradle + libs jar + 文档同步清理）。

#### A1. 原版渲染方案（已实现第十九轮初版）
- [x] `TunerRenderer`：HumanoidMobRenderer + 64x64 humanoid 贴图
  - 全身（躯干/四肢）深紫色
  - 头部：内亮外深渐变淡蓝色（贴图径向渐变）
  - 帽子(hat)覆盖层贴图透明，不显示
- [x] `TunerCapeLayer`：深紫色披风 RenderLayer（独立 64x32 贴图，独立模型层定义，
  跟随身体朝向 + 行走/时间摆动）
- [ ] 后续打磨：施法抬臂姿态、二阶段破碎形态变体贴图、传送粒子

#### A2. 资源文件
```
assets/goetytuner/
├── textures/entity/tuner.png       ← humanoid 64x64（深紫身体+渐变蓝头）
├── textures/entity/tuner_cape.png  ← 披风 64x32（深紫）
└── textures/gui/music_bar.png       ← 节奏条底图（可选，当前为纯色fill渲染）
```

#### A3. 视觉特效
- [x] 重音触发（第十九轮已实现）：三波 END_ROD 冲击环 + NOTE 音符爆发 + 阶段差异化
  紫水晶提示音（铺垫0.9/高潮1.4/低谷0.6）+ 客户端 HUD 亮黄描边闪烁
- [ ] 传送：紫色烟雾粒子（替代Enderman紫色粒子或自定义）
- [ ] 效果清除：净化粒子环
- [ ] 二阶段进入：碎裂粒子爆发 + 天空盒变色
- [ ] 施法前摇：法杖聚能光效

#### A4. 等效护甲显示
- 当前靠"不渲染护甲层"实现不显示（原版渲染方案下天然不挂 HumanoidArmorLayer）。
- 若与其他护甲显示mod冲突，考虑注册空ArmorRenderer。

**前置依赖**：贴图由脚本生成（可随时重跑调色），后续美术素材可直接替换同名文件。

---

### B. 音乐资源与标注

**目标**：接入实际音乐文件，使战斗节奏与音乐精确同步。
**【2026-08-20 第二十轮】已完成接入**（用户曲目 output.wav → boss_music_phase1.ogg）。

#### B1. 音乐文件
```
assets/goetytuner/sounds/
└── boss_music_phase1.ogg   ← 98.27s（1965tick），一/二阶段共用
```
- 二阶段不换曲不变速（第二十一轮）：全程以 `music.pitchPhase1` 同一速度播放同一文件
- **时长约束**：`music_score.json` 的 segments 总tick数 = ogg时长×20（当前1965）

#### B2. 分段表与重音表标注（第二十轮按 BPM120 实测标注）
- 曲目信息：G Major / BPM120（1拍=0.5s=10tick，节拍网格锚定第4秒）
- 分段：0-4s铺垫 / 4s-52s高潮 / 52s-1:27低谷 / 1:27-98.25s铺垫（循环衔接开头）
- 重音（37个）：高潮每4拍（2s=每小节强拍）、低谷/铺垫每8拍（4s），
  强制命中 4s/52s/1:27 三个分段转换点 + 循环点0
- 已写入代码默认值（MusicController.writeDefaultScore）与 run/config 运行时文件

#### B3. 音乐播放控制
- [x] 音量/播放速度配置（第十九轮）：`music.volume`（0-8）、`music.pitchPhase1`（0.5-2.0）。
  注意：原版音频引擎 pitch 速度与音调绑定，无法只变速不变调。
  **起始播放位置（从第几秒开始）不支持**——原版 playSound 无偏移播放 API，
  如需从中间开始请用音频软件裁剪 ogg（如 Audacity 剪辑后导出）。
- [x] 音乐循环（第二十一轮）：客户端 looping 循环实例原生无缝循环（不再依赖服务端wrap点重发playSound）
- [x] 二阶段音乐（第二十一轮）：**全程同一版本同一速度**（撤销第二十轮1.25x变速方案）；
  enterPhase2 不再重置进度、不重启音频
- [x] 玩家死亡/远离/脱战时停止音乐（第二十一轮）：客户端 BossMusicManager 检测
  playing=false / 同步超时（3s）/ 实体失效，30tick宽限后停播
- [x] Boss死亡时停止所有音乐（第二十一轮）：客户端检测实体移除→清理状态→停止实例
- [x] 一二阶段音频衔接重叠（第二十一轮）：音频客户端化（单一循环实例），从根消除
- [x] 原版背景音乐重叠（第二十一轮）：起播时 MusicManager.stopPlaying() 压掉当前曲目；
  演奏期间 PlaySoundEvent 拦截 MUSIC 类别新声音

#### B4. 重音刻度HUD（第二十轮完成）
- [x] accents 数组随 SMusicSyncPacket 全量同步
- [x] 重音刻度分阶段样式：铺垫=普通细线 / 高潮="中"字（竖线+中部细线方框）/
  低谷=加粗3px（`MusicBarHud.drawAccentMark`）

**当前状态**（第二十一轮）：音频彻底客户端化——客户端 BossMusicManager 维护单一循环
音效实例（forMusic 同款模式+looping），起播/停播完全由同步状态驱动；一/二阶段衔接、
原版音乐重叠、boss死亡停播三个问题均已解决。

---

### C. Boss专属魔杖

**目标**：替代当前 `dark_wand`（黑暗魔杖/初始杖）占位。

#### C1. 物品注册
- [ ] 新建 `TunerWand` 类，继承 Goety `WandItem`（或 `DarkWand`），实现 `IWand`
- [ ] SpellType.NONE（通用系别，接受所有聚晶）
- [ ] 注册到 `ModItems`，创造标签页添加
- [ ] 材质与模型：指挥棒风格（黑色杖身 + 紫色顶端宝石）

#### C2. Boss装备
- [ ] `TunerBoss` 构造函数和 `readAdditionalSaveData` 中将 `DARK_WAND` 替换为 `TUNER_WAND`
- [ ] `BossWandHelper` 注释更新

#### C3. 施法特效
- [ ] 专属施法粒子（紫色音符/音波纹）
- [ ] 重音瞬发时法杖发光

**前置依赖**：需要魔杖材质贴图 + 模型。代码层面可先注册物品用占位贴图。
**当前状态**：`BossWandHelper` 注释已修正为 `DARK_WAND`，代码一致。

---

### D. 兼容性测试与适配

**目标**：确保在存在其他Goety附属时，Tuner正常工作。

#### D1. 聚晶兼容
- [ ] 测试其他附属注册的聚晶是否被正确扫描入池
- [ ] 测试附属聚晶的lang描述是否被启发式分类器正确处理
- [ ] LLM自动分类对附属聚晶的覆盖率验证
- [ ] 异常聚晶（无spell/无lang描述）的容错处理

#### D2. 召唤物兼容
- [ ] 测试Goety本体召唤物owner识别（`OwnableEntity`）
- [ ] 测试附属mod召唤物是否走owner标记
- [ ] 不走owner的召唤物：在 `SummonScoreTracker` 补注册表判断

#### D3. 效果兼容
- [ ] 其他附属的正面/负面效果是否被 `cleanseEffects` 正确识别
- [ ] `MobEffect.isBeneficial()` 对附属效果的准确性验证

#### D4. 性能
- [ ] 多附属环境下聚晶池扫描性能（ServerStarting时的initIfNeeded）
- [ ] 大量仆从时 `ownedMinions` 遍历性能
- [ ] 动态评分 tracker 在高频伤害事件下的性能

#### D5. 已知Goety附属列表（待测）
- Goety Additional（如有）
- 其他基于Goety API的附属mod

**前置依赖**：需要安装其他Goety附属mod进行联合测试。

---

### E. 逻辑完善

#### E1. 音乐播放控制 [高优先级]
- 当前问题：`startSeenByPlayer` 播放音乐后无停止逻辑
- 修复：`stopSeenByPlayer` / Boss死亡 / 玩家死亡时停止
- 音乐循环：RECORDS不自动循环，需定时重发或改用EntitySoundInstance

#### E2. 重音刻度HUD同步 [中优先级]
- 当前问题：accents数据未随包同步，HUD用固定三等分占位
- 修复：扩展SMusicSyncPacket传accents数组，MusicBarHud画刻度线

#### E3. 高潮三通道杖竞态 [中优先级]
- 当前问题：三CastChannel共享一把主手杖，同一tick换装不同聚晶存在竞态
- 现状：三通道先后装杖、`mobSpellResult`在装杖后立即结算，实际影响待实测
- 备选方案：为每通道引入虚拟caster（复杂度高，暂缓）

#### E4. DoT伤害归因 [低优先级]
- 当前问题：攻击类动态评分只识别直伤，DoT（中毒/凋零等）未归因
- 修复：在DamageScoreTracker中追踪DoT来源（需区分直接伤害与持续伤害的DamageSource）

#### E5. 召唤物owner识别 [低优先级]
- 当前问题：依赖 `OwnableEntity#getOwnerUUID`，Goety个别召唤物可能不走该接口
- 修复：在SummonScoreTracker补注册表判断（按entity type硬编码owner关系）

#### E6. 正式生成方式 [低优先级]
- 当前：刷怪蛋 `/give @p goetytuner:tuner_spawn_egg`
- 计划：结构/仪式召唤（设计待定）

#### E7. 死亡/受击音效 [低优先级]
- 当前：`getDeathSound()` / `getHurtSound()` 返回null
- 需要：录制或选取合适的音效资源

---

## 四、新增需求实现状态

### 低谷期清除正面效果 ✅ 已实现
- 位置：`TunerBoss.aiStep()` → `segmentChanged` 处理段
- 逻辑：进入低谷（VALLEY）时调用 `cleanseEffects(level, true)`
- 使用 `MobEffect.isBeneficial()` 判定，清除所有标记为beneficial的效果
- 包括：速度/力量/抗性/吸收/生命恢复等（玩家施加的或防御聚晶产生的）

### 高潮期清除负面效果 ✅ 已实现
- 位置：同上
- 逻辑：进入高潮（CLIMAX）时调用 `cleanseEffects(level, false)`
- 清除所有非beneficial效果
- 包括：侵蚀(SAPPED)/黑暗/中毒/凋零/缓慢/虚弱等（低谷期施加的或玩家施加的）

**编译验证**：BUILD SUCCESSFUL（JDK17，33s，无新增警告）。

---

## 五、AI自动初评分状态

### 启发式分类器 ✅ 已完成
- `FocusClassifier`：按聚晶英文lang描述关键词匹配
- 优先级：SUMMON(19词) → OTHER(22词) → DEFENSE(7词) → ATTACK(35词)
- 未命中配置表的聚晶自动兜底分类，不再全部落入OTHER
- 124个Goety聚晶已覆盖

### LLM自动分类 ✅ 已完成
- `LLMClassifier`：OpenAI兼容HTTP异步请求
- 构造中文提示词，要求模型分类+评分聚晶
- 异步CompletableFuture，解析JSON写回 `focus_classification.json`
- 入口：`TunerConfigScreen` 按钮（需填入API Key）
- 使用JDK原生HttpClient，无额外依赖

### 结论
AI自动初评分**已完整实现**。两条路径：
1. 无API Key → 启发式分类器自动兜底（开箱即用）
2. 有API Key → ConfigScreen按钮触发LLM精确分类（可选增强）

---

## 六、配置系统总览

### `run/config/goetytuner-common.toml`（22项）

| 分类 | 配置项 | 默认值 | 说明 |
|---|---|---|---|
| **boss** | maxHealth | 1024 | Boss血量 |
| | lockHealthInterval | 128 | 锁血档距（7次锁血+最后128可杀） |
| | equivalentArmor | 16.0 | 等效护甲 |
| | teleportInterval | 200 | 主动瞬移间隔（tick，10秒） |
| | teleportRescueInterval | 20 | 异常位置修正间隔（tick，1秒） |
| | teleportMinDistance | 12.0 | 触发主动瞬移的距离 |
| | teleportSearchRadius | 16 | 传送搜索半径 |
| **summon** | maxMinions | 32 | 召唤上限 |
| | refillHysteresis | 4 | 归零解除阈值 |
| | survivalWeight | 1.0 | 召唤评分参数1（生存权重） |
| | attackWeight | 1.0 | 召唤评分参数2（输出权重） |
| **scoring** | baseRouletteWeight | 2.0 | 轮盘保底基数 |
| | dynamicScoreCap | 5.0 | 动态偏移上限 |
| | dpsAdjustRate | 0.5 | DPS修正速率 |
| **casting** | extraCastCooldown | 20 | 额外施法冷却 |
| | climaxWarmupMultiplier | 0.5 | 高潮前摇倍率 |
| **music** | syncInterval | 20 | 音乐同步间隔 |
| | accentKnockbackBase | 0.4 | 普通重音斥力 |
| | accentKnockbackValley | 0.8 | 低谷重音斥力 |
| | accentKnockbackClimax | 1.2 | 高潮重音斥力 |
| **llm** | apiUrl | openai.com | LLM端点 |
| | model | gpt-4o-mini | LLM模型 |

### `run/config/goetytuner/music_score.json`
```json
{
  "segments": [
    { "phase": "buildup", "ticks": 1200 },
    { "phase": "climax", "ticks": 600 },
    { "phase": "valley", "ticks": 400 }
  ],
  "accents": [100, 200, 300, ...]
}
```

### `run/config/goetytuner/focus_classification.json`
- 静态聚晶分类+评分表
- LLM API Key存储
- 运行时自动生成，可手动编辑覆盖

---

## 七、优先级排序与建议开发顺序

### 第一优先级：逻辑补全（可立即做，无外部依赖）
1. **E1 音乐播放控制**：停止/循环/切换逻辑
2. **E2 重音刻度HUD同步**：扩展包+渲染刻度
3. 实测验证效果清除、锁血V2、传送间隔的实际手感

### 第二优先级：美术接入（需要美术素材）
4. **A1-A2 原版渲染打磨**：初版已实现（贴图+披风），后续可替换美术贴图/姿态
5. **A3 视觉特效**：粒子/光环/碎裂（重音特效已实现）
6. ~~**B1-B2 音乐接入+标注**~~：第二十轮已完成（曲目转码+分段/重音标注+二阶段变速）

### 第三优先级：内容扩展
7. **C Boss专属魔杖**：代码可先做，贴图后补
8. ~~**B3-B4 音乐播放控制完善+重音HUD**~~：循环/变速已实现；剩余"停止旧音频实例"待 EntitySoundInstance 方案

### 第四优先级：兼容性打磨
9. **D 兼容性测试**：安装其他Goety附属联合测试
10. **E3-E5 逻辑边界case**：竞态/DoT/owner识别

### 第五优先级：收尾
11. **E6 正式生成方式**：结构/仪式设计
12. **E7 音效**：死亡/受击音效
13. **A4 护甲显示**：确认无冲突

---

## 八、风险评估

| 风险 | 等级 | 缓解措施 |
|---|---|---|
| ~~GeckoLib迁移引入新mixin冲突~~ | 已消除 | 第十九轮彻底移除GeckoLib（Goety本体零引用实证） |
| 音乐与分段表不同步 | 中 | 标注工具+实测校准；分段表可热改config |
| 高潮三通道杖竞态 | 低 | 当前实现先后装杖+即时结算，待实测确认影响 |
| 附属mod聚晶异常（无spell/崩溃） | 低 | BossWandHelper已有try-catch防御 |
| 大量聚晶时扫描性能 | 低 | ServerStarting时一次性扫描+缓存 |
| LLM分类结果不稳定 | 低 | 启发式兜底+可手动覆盖 |

---

## 九、实测记录

### 第八轮（2026-08-18）
- ✅ 进世界实测：quickPlay自动进档，旧Tuner实体NBT恢复后正常施法
- ✅ 主手法杖缺失崩溃修复（dark_wand + readAdditionalSaveData自愈）
- ✅ 占位纹理生成（黑立方头+紫眼+紫领带）

### 第九轮（2026-08-18）
- ✅ 传送频率修复：主动瞬移间隔60→200tick（10秒），异常位置独立短间隔
- ✅ 锁血V2：lockMark初始0起步，7次阶梯锁血，最后128可杀
- ✅ 锁血防死：hurt拦截致死 + 死亡状态自愈（/kill不僵局）
- ✅ BossWandHelper注释修正（NAMELESS_STAFF → DARK_WAND）

### 第十轮（2026-08-18，本次）
- ✅ 效果清除实现：低谷清正面/高潮清负面
- ✅ 全面审计：23个Java文件+8个资源文件，22项配置
- ✅ 新计划书V2编写

### 第十一~十七轮（2026-08-18~19）
- ✅ 索敌去视线约束、施法窗口封顶(MAX_CAST_WINDOW_TICKS=40)、聚晶池锁池
- ✅ 锁血宽限期(LOCK_GRACE_TICKS)+免疫伤害、二阶段触发lockMark化(4→5)
- ✅ 仇恨门控（无仇恨不演奏不施法）、最后一档锁血永久无敌修复
- ✅ 追击瞬移(最近可索敌玩家+距离上限)、一阶段后撤步、二阶段攻击前弧形瞬移(r=6)
- ✅ 音乐条：二阶段连续条带坠落+判定线对齐+浮点progressTickF丝滑平移

### 第十八轮（2026-08-19，本次）
- ✅ 文件完整性检查：两副本全量 diff 一致，无空文件/截断/编码异常（保存意外未造成损坏）
- ✅ 复核 Configured/GeckoLib 依赖：build.gradle 声明在位，libs jar 均为 refmap 清空 devfix 版
- ✅ 复核 tuner 实体 attributes：ModBusEvents(MOD总线 EntityAttributeCreationEvent) 注册在位，
  createAttributes 纯常量（不读 config），构造函数按 config 覆盖 + null 防御
- ✅ 作者信息：gradle.properties(mod_authors)、mods.toml(displayURL/issueTrackerURL)、
  README、DEVELOPMENT_PLAN、全部 Java 源文件头注释 → toniat0 & vibe-coding + GitHub 链接

### 第十九轮（2026-08-19，本次）
- ✅ 重音特效增强：三波 END_ROD 冲击环 + NOTE 音符爆发 + 阶段差异化紫水晶音（0.9/1.4/0.6）
  + 客户端 HUD 亮黄描边闪烁（本地越线检测，无额外网络包）；配置 accentParticles/accentSound
- ✅ GeckoLib 彻底移除：Goety 本体全 jar class 扫描零引用实证；删依赖/libs jar/文档指引
- ✅ 原版渲染方案初版：PIL 生成 64x64 贴图（深紫身体+内亮外深渐变淡蓝头）+
  TunerCapeLayer 深紫披风（独立 64x32 贴图，跟随躯干+行走/呼吸摆动）
- ✅ 音乐播放配置：music.volume / music.pitchPhase1 / music.pitchPhase2；
  起始偏移不支持（原版API限制），说明已写入 sounds/README-音乐资源说明.txt
- ✅ 音乐条样式：260x6（更扁更长）、三段配色提亮（亮蓝/亮橙红/亮紫）、指针与判定线加粗5px

### 第二十轮（2026-08-20，本次）
- ✅ 音乐接入：用户曲目 output.wav（98.27s/BPM120/G大调）转码为 boss_music_phase1.ogg 打包进资源
- ✅ 乐谱标注：分段 80/960/700/225（铺垫/高潮/低谷/铺垫，总1965tick=98.25s）；
  重音37个（高潮每4拍、低谷/铺垫每8拍，命中4s/52s/1:27三转换点+循环点0），写入代码默认值+运行时配置
- ✅ 二阶段1.25倍速：MusicController 浮点速度推进（getSpeed=pitchPhase2）、
  SMusicSyncPacket 增加 speed 字段、客户端平滑推进乘 speed——音乐条/重音/分段切换与变速音频严格对齐
- ✅ 无间隔循环：乐谱wrap点（consumeLooped）自动重发playSound
- ✅ 二阶段复用一阶段音频：playBossMusic 统一入口（phase2=1.25 pitch）；删除 BOSS_MUSIC_PHASE2 注册与sounds.json条目
- ✅ 重音刻度分阶段样式：铺垫=细线 / 高潮="中"字（竖线+中部空心方框）/ 低谷=加粗3px

### 第二十一轮（2026-08-20，本次）
- ✅ 音频客户端化：新增 client/BossMusicManager——单一循环音效实例
  （SimpleSoundInstance：looping + Attenuation.NONE + relative，原版 forMusic 同款模式），
  起播/停播由 MusicStateClient 同步状态驱动；服务端不再 playSound（playBossMusic 已删除）
- ✅ 问题1·一二阶段衔接重叠：撤销第二十轮1.25x变速方案——全程 pitch=pitchPhase1 恒定，
  enterPhase2 不重置进度不重启音频；pitchPhase2 配置项删除，looped/consumeLooped 机制移除
- ✅ 问题2·原版背景音乐重叠：起播时 MusicManager.stopPlaying()；演奏期间
  PlaySoundEvent 拦截 MUSIC 类别新声音（boss音乐走 RECORDS 不受影响）
- ✅ 问题3·boss死后音乐不停：客户端tick检测实体移除/死亡/同步超时(3s)→清理状态→停止实例
- ✅ 脱战→再演奏边沿：服务端 music.restart() 乐谱归零 + 立即同步（客户端音频从头起播，两端0点对齐）；
  演奏→脱战由客户端30tick宽限后停播

### 第二十二轮（2026-08-20，本次）
- ✅ 问题1·瞬移追击失效修复：根因= tickTeleport 只在铺垫期/二阶段高潮期被调用，
  一阶段高潮/低谷期或脱战边缘不追击；改为 aiStep 全程每tick判定（距离区间与间隔限制不变）
- ✅ 问题2·弧形走位频率：二阶段铺垫期攻击前半圆弧瞬移从"每次攻击施法"改为
  每 phase2BuildupArcEveryN 次（默认3=一轮"222"轮换施法）触发一次
- ✅ 问题3·二阶段低谷转铺垫：MusicController 相位转换（phase2 时 VALLEY→BUILDUP），
  服务端行为（轮换施法+弧形走位）与客户端音乐条分段颜色/阶段文字/重音样式同步转换；
  一阶段乐谱分段不变；高潮→铺垫切换时打断高潮通道（防冻结busy锁池）
- ✅ 问题4·二阶段前摇×0.8 + 仇恨保留：新增 phase2WarmupFactor（默认0.8，
  高潮实际前摇=climaxWarmupMultiplier×0.8，铺垫前摇=1.0×0.8）；
  targetRange 默认48→96（FOLLOW_RANGE 属性，createAttributes 常量同步），
  teleportChaseMaxDistance 默认64→96（与索敌范围对齐，追击区间内始终能瞬移追上）

### 第二十三轮（2026-08-20 追加）
- ✅ Boss身份伤害免疫：hurt() 顶部拦截摔落(FALL)/原版火焰(IS_FIRE标签，覆盖火焰/岩浆/燃烧/
  热沙)/窒息(IN_WALL)/溺水(DROWN)，return false；魔法/爆炸/普通攻击不受影响
- ✅ 非玩家瞬移追击（独立新方法 tickTeleportNonPlayer，未改动玩家版 tickTeleport）：
  生效条件=有存活仇恨目标 且 目标非玩家 且 teleportChaseMaxDistance(96格)内无任何
  可参战的生存/冒险玩家（创造/旁观/死亡不算）；独立计时器 nonPlayerTeleportTimer，
  追击区间与间隔配置与玩家版一致（复用 tryTeleportNear/currentTeleportInterval）

### 第二十四轮（2026-08-20 追加）：嘲讽彩蛋
- ✅ 战略性嘲讽（一阶段专属，aiStep 内联状态机，不进 Goal 系统）：
  触发=非二阶段 + 被激怒（仇恨目标存活）+ 目标距离(6,12]格 + 冷却结束 + 每tick 0.2%概率
  （平均~25秒一次；冷却10秒）。触发后二选一：
  - 70%：面向目标快速蹲起4轮（蹲/站各4tick）→ 向远离目标方向边跳边跑约5格
    （手动 setDeltaMovement+JumpControl 周期起跳，不用导航——避免与高潮"不移动"的
    navigation.stop 每 tick 打架）
  - 30%：仅下蹲看向目标 2~3.5 秒后站起
- ✅ 蹲姿视觉：服务端 setPose(Pose.CROUCHING)（原版 DATA_POSE 数据同步，零额外网络包）；
  字节码核实：HumanoidModel.setupAnim 自带蹲姿动画分支但只读 crouching 字段，且只有
  PlayerRenderer 会写该字段（HumanoidMobRenderer 不写）——新增 TunerModel 子类在
  setupAnim 前把 pose 映射到 crouching 字段即获原版蹲姿动画；TunerRenderer/TunerCapeLayer
  泛型同步改为 TunerModel
- 进二阶段立即终止嘲讽（onEnterPhase2 强制回站姿）；嘲讽期间不打断施法/阶段行为

---

## 十、构建与运行

```bash
# 必须 JDK 17（Gradle 8.1.1 不支持 Java 21）
# 必须用 PowerShell 跑 gradlew.bat（git bash 下 xargs 环境过大）
# 先清除环境变量
Remove-Item Env:ACC_PRODUCT_CONFIG_V3 -ErrorAction SilentlyContinue

# 编译验证
.\gradlew.bat compileJava

# 开发环境运行
.\gradlew.bat runClient

# 无人值守自动进档验证
.\gradlew.bat runClient --args="--quickPlaySingleplayer test0"

# 游戏内生成Boss
/give @p goetytuner:tuner_spawn_egg
```

**两副本同步**：
- `D:\tiaolvshi\goety-tuner`（主副本）
- `D:\测试\tiaolvshi\goety-tuner`（编译/运行路径）
- 修改任一后必须 `cp` 同步到另一个

---

## 十一、调参速查

| 场景 | 配置项 | 调整建议 |
|---|---|---|
| Boss太肉/太脆 | `maxHealth` / `lockHealthInterval` | 降血量或增大档距=更少锁血次数 |
| 传送太频繁/太少 | `teleportInterval` | 200=10秒，400=20秒 |
| 召唤太多/太少 | `maxMinions` / `refillHysteresis` | hysteresis=满员后需低于max-N才恢复 |
| 高潮太难/太易 | `climaxWarmupMultiplier` | 0.5=前摇减半（更快施法），1.0=正常 |
| 重音推力太强/弱 | `accentKnockback*` | 0.4/0.8/1.2 = 普通/低谷/高潮 |
| 动态评分变化太慢/快 | `dpsAdjustRate` / `dynamicScoreCap` | rate=修正速率，cap=偏移上限 |
