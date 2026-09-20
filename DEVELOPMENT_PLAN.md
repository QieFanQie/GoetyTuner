# 调律师 (The Tuner) — 诡厄巫法附属Boss · 开发计划书 V2

> **0.0.12 修复**：本轮三件事 —— 功能改动落在 3 个 Java 文件（`client/AccentWaveRenderer`、`client/MusicBarHud`、`entity/ai/CastChannel`），另 `entity/TunerBoss` **只动了一处方法 javadoc**（无行为变化），加版本号；**无新增类/贴图/配置项**（仍 61 项 / 10 段，`music` 12 项；`.java` 文件数仍 45）。① **径向声波涟漪改为锚定在触发瞬间的坐标**（原先只存 `startTick`、渲染时每帧读 Boss 当前位置 ⇒ 涟漪跟着 Boss 跑；而调律师每次锁血都会强制瞬移，观感完全不对）——触发时记下脚下世界坐标（新增私有 `record Wave`），整条波在固定点上播完，**清理只按时间**（34 tick），不再因 Boss 死亡/被移除/离开视野提前掐掉。② **音乐条 HUD 外观重做**（用户反馈「太突兀」）：`260×6` → **`204×8`**（底距 64→62）、硬边纯色块 → **逐行混色**渐变 + 2px 过渡缝、单一硬矩形底 → **三层柔和投影**（四角留空模拟圆角）、阶段**文字** → **像素符号** `● ● ●`/`●`/`- - - - - -`（本客户端字体无 U+26AA 字形，直接写 `⚪` 会显示成空白方块）、重音刻度与闪烁调淡、一阶段分段按条宽做 scissor 裁剪防溢出。③ **`CastChannel` 回调成对性修复**（此前本文档记为「仍未修」的已知边界）：`logCast` 原先排在 `onCastStart` 之后且会抛异常，异常逃逸到 `beginCast` 兜底 `catch` 而那里不补发结束回调 ⇒ 孤儿回调使施法状态计数与立方体类别掩码**永久 > 0**（立方体一直高亮、蹲姿卡住，且身份集合幂等让该聚晶再也无法计入）；现把 `logCast` 调到 `onCastStart` **之前** + 新增 `startEmitted` 兜底补发 `onCastFailed`。0.0.10 美术项（参考身体贴图 / 8 阶披风 / 悬浮立方体 / 径向声波 / 原版刷怪蛋）仍待游戏画面验收，见 [ART_ASSETS_REPORT.md](ART_ASSETS_REPORT.md)。

> MC 1.20.1 Forge 47.3.22 · 附属模组（依赖 Goety 2.5.56.5）· 当前版本 0.0.12
> 更新日期：2026-09-20
> 作者：toniat0 & vibe-coding · 团队：Goety Tuner Project · <https://github.com/QieFanQie/>
> 许可证：MIT License
>
> 本文档是**阶段性计划书**（含历史实测记录，进度类内容随轮次回填）；
> 项目技术现状以 `TECHNICAL_SUMMARY.md` 为准（该文档更新到第 50 轮），
> 单任务设计稿见 `DESIGN_RITUAL_WAND_UPGRADE.md`。

---

## 一、项目概述

**调律师**：人形无头指挥家Boss，头部位置只有一枚飘动的黑色立方。它以"演奏"的方式轮番使用诡厄巫法及其附属注册的**所有聚晶（Focus）**，战斗由三段式音乐（铺垫/高潮/低谷）驱动。

当前状态（0.0.12 / 第 50 轮）：**程序框架完整可运行**，核心战斗逻辑（聚晶池/评分/音乐同步/锁血/阶段切换/效果清除）、音乐资源接入（第 20~21 轮）、仪式召唤与法杖升级（第 36 轮）、游戏内配置与 LLM 评分界面（第 41 轮）均已落地并通过实测；第 48 轮（0.0.10）美术交付已实现、**待游戏画面验收**；剩余 Boss 专属魔杖、兼容性打磨与少量逻辑边界项。第 43 轮（0.0.5）成果：LLM 评分错误诊断与提示词编辑体验改进、一轮保持功能不变的系统性性能优化（详见 TECHNICAL_SUMMARY.md）；第 44 轮（0.0.6）成果：限伤（单次伤害上限，默认开启 25% 最大生命）+ 限DPS（滑动 1 秒预算，默认关闭）；第 45 轮（0.0.7）成果：LLM 批量评分改分批请求（修「200 个聚晶只应用 25 条」）+ 修线程泄漏 + 提示词格式化加固 + 资源改名；并实证「锁血机制本身已隐含限伤，限伤/限DPS 在当前设计下作用有限，真正旋钮是 lockGraceTicks 与档位数」；第 46 轮（0.0.8）成果：附属模组增删的健壮性加固（逐项扫描兜底 + 施法全流程 try 兜底 + returnEntry 幂等）；死亡状态完善（新增 SEntityRevivePacket 复位客户端死亡动画、脏状态只清动画不消耗锁血档位、锁血未耗尽时拦截 remove(KILLED)）；第 47 轮（0.0.9）成果：为 /kill 打开后门（识别 DamageTypes.GENERIC_KILL 后跳过锁血体系的全部保护，使管理员指令能真正击杀）；第 48 轮（0.0.10）成果：**美术交付**——身体贴图按用户参考图重画（头部区逐像素不变）、8 阶色带披风、红/蓝/灰三颗悬浮立方体（位掩码高亮 + IdentityHashMap 幂等计数）、非对称径向声波涟漪（新增 SAccentWavePacket 通道 id 3，旧粒子默认关闭）、刷怪蛋改走原版 template_spawn_egg（紫/亮蓝染色）；配置 61 项（`music` 段 11→12），网络协议 1.0→**2.0 严格匹配**（联机双方须同时更新）；**上一轮（0.0.11）成果：修掉用户玩出来的真 bug** —— `applyLockHealth()`（由 `aiStep()` 调用）里与 `maintainDeathState()` 重复的「死亡自愈」分支其实**可达**（反编译实证：`LivingEntity.tick()` 里 `aiStep()` 只有 1 处无条件调用，真正被死亡把关的是 `baseTick()` 的 `isDeadOrDying()` → `tickDeath()`；原注释把它与 `serverAiStep()` 混为一谈），后果是 `/kill` 后门被击败（日志实证：`/kill` 后 48 ms Boss 带 18 血复活，只得再杀一次）、白吃一档锁血、且只写 `deathTime=0` 不复位客户端动画（旧「血量不为 0 但已是死亡动画」复发）。修复：删除该分支，改为死亡时在方法开头直接早退；死亡回弹**唯一**权威实现是 `tick()` 里的 `maintainDeathState()`（尊重 `/kill` 后门并同步复位客户端动画）。本轮只改 2 个文件（版本号 + `TunerBoss`），无新增类/贴图/配置。**本轮（0.0.12）成果：表现层重做 + 一个回调成对性修复** —— ① **径向声波涟漪锚定触发瞬间的坐标**（原先每帧读 Boss 当前位置、会跟着 Boss 跑，而每次锁血都会强制瞬移；现记脚下世界坐标，整条波在固定点上播完，且**清理只按 34 tick 计时**，不再因实体死亡/移除/离开视野提前掐掉）；② **音乐条 HUD 外观重做**（用户反馈「太突兀」）：`260×6`→**`204×8`**、底距 64→62、硬边纯色块→**逐行混色**+2px 过渡缝、单一硬矩形底→**三层柔和投影**（四角留空模拟圆角）、阶段**文字**→**像素符号** `● ● ●`/`●`/`- - - - - -`（实测本客户端字体无 U+26AA 字形，直接写 `⚪` 会显示成空白方块）、重音刻度 `0xB8FFFFFF` 与闪烁峰值 `0x88` 调淡、一阶段分段按条宽做 scissor 裁剪防溢出；③ **`CastChannel` 回调成对性修复**（此前记为「仍未修」的已知边界）：`logCast` 原先排在 `onCastStart` 之后且会抛异常，异常逃逸到 `beginCast` 兜底 `catch` 而那里不补发结束回调 ⇒ 孤儿回调使施法状态计数与立方体类别掩码**永久 > 0**（立方体一直高亮、蹲姿卡住，且身份集合幂等让该聚晶再也无法计入）；现把 `logCast` 调到 `onCastStart` **之前**（结构上不可能再被打断）+ 新增 `startEmitted` 标志兜底补发 `onCastFailed`。另顺手修正 `applyLockHealth()` 的方法 javadoc（仍在描述 0.0.11 已删除的行为，纯注释、无行为变化）。本轮无新增类/贴图/配置项（jar 条目 101→102 只是多了 `AccentWaveRenderer$Wave` 内部类）。

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
| 锁血V2 | ✅ | 12 档阶梯锁血（216 血 / 间隔 18），致死伤害截断 + 宽限期免疫 + 覆写 tick() 的死亡自愈 + 旧档迁移 |
| 二阶段 | ✅ | 半血触发+音乐切换+打断+数据同步 |
| 效果清除 | ✅ | 低谷清正面/高潮清负面（本次新增） |
| 重音系统 | ✅ | 分阶段斥力+无前摇瞬发施法 |
| 仆从管理 | ✅ | 数量上限+迟滞恢复+伤害归因 |
| 客户端HUD | ✅ | **204×8** 音乐条（0.0.12 重做：逐行混色分段 + 2px 过渡缝、三层柔和投影（四角留空模拟圆角）、阶段**像素符号** `● ● ●`/`●`/`- - - - - -`）、三色分段（铺垫 0xFF5B9BE0 / 高潮 0xFFF26A4B / 低谷 0xFFB068E8）、分阶段重音样式（细线/「中」字/加粗，半透明白 0xB8FFFFFF）、二阶段锚定坠落条带 + scissor 裁剪 + wrap 补位、越线亮黄闪烁（峰值 0x88） |
| 网络同步 | ✅ | SMusicSyncPacket 20tick推送进度/阶段/二阶段；共 4 个通道（含 0.0.10 新增 `SAccentWavePacket` id 3，限 `PLAY_TO_CLIENT`），协议 2.0 严格匹配 |
| 正式纹理 | ✅ | `tuner.png` 64x64 身体按参考图服装重画、头部仍是黑立方+蓝渐变（0.0.10）；另有 8 阶披风、立方体与声波贴图，自定义蛋贴图已删（改走原版模板） |
| 渲染方案 | ✅ | 原版 HumanoidMobRenderer + `TunerModel` + `TunerCapeLayer` + 悬浮立方体 `TunerOrbLayer`/`TunerOrbModel`（0.0.10） |
| 配置系统 | ✅ | 61 项 / 10 个 section（toml） |
| 实测验证 | ✅ | quickPlay自动进档验证通过（第 9/11 轮） |
| 客户端音乐播放器 | ✅ | `BossMusicManager` 客户端循环实例（`SimpleSoundInstance` looping + `Attenuation.NONE` + relative），解决阶段切换重叠/原版音乐重叠/Boss 死后不停（第 21 轮） |
| 重音特效 | ✅ | 非对称径向声波涟漪（0.0.10；旧粒子默认关闭） + 阶段差异化紫水晶音（0.9/1.4/0.6）；二阶段进场连发 6 次（第 19/31 轮） |
| 披风渲染层 | ✅ | `TunerCapeModel` + `TunerCapeLayer`（第 19 轮） |
| 聚晶黑名单 + 施法自愈 | ✅ | 配置 `focus.blacklist`（String 容错解析）+ 运行期 `RUNTIME_BLACKLIST` + 实体级拦截 `goetytwilight:destruction`（第 26/29 轮） |
| 分类三层防线 | ✅ | 手动配置 → `instanceof ISummonSpell` 权威判定 → `describe()` 兼容 `.info`/`.desc` 双后缀 → 关键词兜底（第 29 轮） |
| 仪式召唤 + 法杖升级 | ✅ | 任务 #118：`goety:ritual_factory` 注册、快照制掉落、10% 巫法（`SPELL_POTENCY` MULTIPLY_TOTAL）+ 40% 魔法伤害、经验×4（第 36 轮） |
| 调律命令 | ✅ | `/goetytuner tune <witchcraft\|magic\|add\|clear\|info>`（OP2）（第 37b 轮） |
| 法杖白名单 | ✅ | `wand_whitelist`（第 38 轮） |
| 游戏内配置界面 + LLM 评分 UI | ✅ | `IConfigScreenFactory` 注册（Mods 菜单出现 Config 按钮）、提示词输入框、`TunerToast` 结果提示、LLM 结果宽松校验（第 41 轮） |

### 未完成

| # | 模块 | 优先级 | 说明 |
|---|---|---|---|
| A | 美术资源与模型 | 高 | ~~GeckoLib迁移~~（已废弃，走原版渲染）、材质、粒子、GUI贴图 |
| B | 音乐资源与标注 | ✅已完成 | 第二十轮接入实测曲目+分段/重音标注；第二十一轮音频客户端化 |
| C | Boss专属魔杖 | 中 | 替代dark_wand占位、专属施法特效 |
| D | 兼容性测试与适配 | 中 | 其他Goety附属mod共存测试 |
| E | 逻辑完善 | 中 | 仅剩 E3 竞态实测 / E4 DoT 归因 / E5 召唤物 owner 识别 / E7 音效 |
| F | 死代码/注释卫生 | 低 | 部分公有方法无引用、若干注释与默认值不符（0.0.4 已清理一批；0.0.5 继续清理并补齐注释） |

---

## 三、未完成项详细计划

### A. 美术资源与模型（阶段8）

**目标**（【2026-08-19 第十九轮修订】放弃 GeckoLib，改用**原版 HumanoidModel + 自定义贴图 + RenderLayer**）：
Goety 2.5.56.5 本体经全 jar class 扫描实证**零 geckolib 引用**，本项目代码亦从未使用
GeoEntity/GeoEntityRenderer，依赖已彻底移除（build.gradle + libs jar + 文档同步清理）。

#### A1. 原版渲染方案（已实现第十九轮初版）
- [x] `TunerRenderer`：HumanoidMobRenderer + 64x64 humanoid 贴图
  - 身体按参考图制作紫黑外套、浅紫领口、紫色内衬与青蓝链饰（0.0.10）
  - 头部：内亮外深渐变淡蓝色（贴图径向渐变）
  - 帽子(hat)覆盖层贴图透明，不显示
- [x] `TunerCapeLayer`：8 阶紫黑到淡紫披风 RenderLayer（独立 64x32 贴图，独立模型层定义，
  跟随身体朝向 + 行走/时间摆动）
- [ ] 后续打磨：施法抬臂姿态、二阶段破碎形态变体贴图、传送粒子

#### A2. 资源文件
```
assets/goetytuner/
├── textures/entity/tuner.png       ← humanoid 64x64（参考服装+原样蓝头）
├── textures/entity/tuner_cape.png  ← 披风 64x32（8 阶紫色）
└── textures/gui/music_bar.png       ← 节奏条底图（可选，当前为纯色fill渲染）
```

#### A3. 视觉特效
- [x] 重音触发（0.0.10 更新）：10 环非对称径向声波，旧粒子默认关闭 + 阶段差异化
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
- [x] 二阶段音乐（第二十一轮）：**全程同一版本同一速度**（撤销第二十轮的二阶段变速方案）；
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
- [x] 大量仆从时 `ownedMinions` 遍历性能：0.0.5 改为同一 `gameTime` 内复用缓存
  （原实现每 tick 被调用 2~6 次、每次遍历全部已加载实体；详见 TECHNICAL_SUMMARY.md §3.7）
- [ ] 动态评分 tracker 在高频伤害事件下的性能

#### D5. 已知Goety附属列表（待测）
- Goety Additional（如有）
- 其他基于Goety API的附属mod

**前置依赖**：需要安装其他Goety附属mod进行联合测试。

---

### E. 逻辑完善

#### E1. 音乐播放控制 [已完成，第 20~21 轮]
- 服务端不再 `playSound`（`playBossMusic` 已删除），音频完全客户端化
- 客户端 `BossMusicManager` 持有单一 looping `SimpleSoundInstance`
  （`Attenuation.NONE` + relative，原版 `forMusic` 同款模式），原生无缝循环
- 生命周期由 `ClientTickEvent` 驱动：实体不在 / 实体死亡 / 同步超时 3s → 停播；
  无演奏状态 30 tick 宽限 → 停播
- `PlaySoundEvent` 拦截 `SoundSource.MUSIC` 新声音并 `MusicManager.stopPlaying()`，
  压掉原版背景音乐（Boss 音乐走 RECORDS 类别，不受影响）
- 脱战→演奏边沿：`music.restart()` + `syncTimer=0`，让乐谱与音频从头对齐

#### E2. 重音刻度HUD同步 [已完成，第 13/19 轮]
- `SMusicSyncPacket` 已下发全量 segments（phase+ticks 扁平数组）与 accents 数组
- 客户端 `MusicStateClient` 存真实分段/重音，`MusicBarHud` 画分阶段样式的重音刻度
  （铺垫细线 / 高潮「中」字 / 低谷加粗）
- 本地越线检测触发亮黄描边闪烁 8 帧（免额外网络包）
- 分段数据缺失时回退三等分近似渲染

#### E3. 高潮三通道杖竞态 [中优先级]
- 当前问题：三CastChannel共享一把主手杖，同一tick换装不同聚晶存在竞态
- 现状：三通道先后装杖、`mobSpellResult`在装杖后立即结算，实际影响待实测
- 已缓解：聚晶池已用 `removeEntry`/`returnEntry` 解决重复抽签；三通道共享一把主手杖的
  换装竞态仍需实测（`BossWandHelper.installFocus` 每轮改写主手杖 NBT）
- 备选方案：为每通道引入虚拟caster（复杂度高，暂缓）

#### E4. DoT伤害归因 [低优先级]
- 当前问题：攻击类动态评分只识别直伤，DoT（中毒/凋零等）未归因
- 修复：在DamageScoreTracker中追踪DoT来源（需区分直接伤害与持续伤害的DamageSource）

#### E5. 召唤物owner识别 [低优先级]
- 当前问题：依赖 `OwnableEntity#getOwnerUUID`，Goety个别召唤物可能不走该接口
- 修复：在SummonScoreTracker补注册表判断（按entity type硬编码owner关系）

#### E6. 正式生成方式 [已完成，第 36 轮]
- 已实现仪式召唤：配方 `data/goety/recipes/tuner_boss_ritual.json`
  （`ritual_type=goetytuner:tuner_boss_summon`、`activation_item=#goety:wands`、
  `craftType=magic`、4×下界之星、`soulCost=3`/soulSpeed 每秒、`duration=10` 秒，总消耗 30 灵魂）
- 刷怪蛋与 `/summon` 仍可用（但不掉落升级法杖，因为无原始法杖快照）

#### E7. 死亡/受击音效 [低优先级]
- 当前：`getDeathSound()` / `getHurtSound()` 返回null；另 `getAmbientSound()` 也返回 null
- 需要：录制或选取合适的音效资源

---

## 四、新增需求实现状态

### 低谷期清除正面效果 ✅ 已实现
- 位置：`TunerBoss.aiStep()` → `segmentChanged` 处理段
- 逻辑：进入低谷（VALLEY）时调用 `cleanseEffects(level, true)`
- 使用 `MobEffect.isBeneficial()` 判定，清除所有标记为beneficial的效果
- 包括：速度/力量/抗性/吸收/生命恢复等（玩家施加的或防御聚晶产生的）
- 实现细节：统一走 `cleanseEffects(level, boolean beneficial)`，用 `MobEffect.isBeneficial()` 过滤；
  进 VALLEY 清正面、进 CLIMAX 清负面，两侧均有日志输出（净化粒子待做）

### 高潮期清除负面效果 ✅ 已实现
- 位置：同上
- 逻辑：进入高潮（CLIMAX）时调用 `cleanseEffects(level, false)`
- 清除所有非beneficial效果
- 包括：侵蚀(SAPPED)/黑暗/中毒/凋零/缓慢/虚弱等（低谷期施加的或玩家施加的）
- 实现细节：与低谷同一方法，`cleanseEffects(level, false)` 分支，同样有日志（粒子待做）

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

### LLM评分UI与结果容错（第 41 轮完整化）
- `TunerConfigScreen` 新增提示词输入框（留空则用默认模板）与「开始评分」按钮
  （API Key 为空时直接返回，不发起请求）
- 完成/失败在右下角弹 `TunerToast`（160×32，5 秒）
- `FocusClassificationConfig.writeLLMResult` 跳过非法 id
  （正则 `^[a-z0-9_.-]+:[a-z0-9_/.*-]+$`）、分数钳制 [0,10] 并按 0.5 取整、
  分类走 `byIdLenient` 宽松兜底
- `load()` 使用 `JsonReader.setLenient(true)`，容忍手改文件里的瑕疵

### 结论
AI自动初评分**已完整实现**。两条路径：
1. 无API Key → 启发式分类器自动兜底（开箱即用）
2. 有API Key → ConfigScreen按钮触发LLM精确分类（可选增强）

---

## 六、配置系统总览

### `run/config/goetytuner-common.toml`（61项 / 10个 section，第50轮实况）

| 分类 | 配置项 | 默认值 | 说明 |
|---|---|---|---|
| **boss**（19项） | maxHealth | 216 | Boss血量 |
| | lockHealthInterval | 18 | 锁血档距（12档阶梯锁血：216/18） |
| | lockGraceTicks | 10 | 每次锁血后的宽限期免疫（tick） |
| | lockDeathRevive | true | 致死伤害被截断后的死亡自愈开关 |
| | equivalentArmor | 16.0 | 等效护甲 |
| | meleeVulnerability | 0.25 | 近战易伤倍率 |
| | teleportInterval | 200 | 主动瞬移间隔（tick，10秒） |
| | teleportRescueInterval | 20 | 异常位置修正间隔（tick，1秒） |
| | teleportMinDistance | 12.0 | 触发主动瞬移的距离 |
| | teleportChaseMaxDistance | 96.0 | 追击瞬移最大距离（与索敌范围对齐） |
| | teleportHopDistance | 8.0 | 逐跳瞬移的单跳距离 |
| | teleportHopInterval | 10 | 逐跳瞬移间隔（tick） |
| | teleportSearchRadius | 16 | 传送搜索半径 |
| | buildupBackstepDistance | 4 | 铺垫期后撤步距离 |
| | targetRange | 96 | 索敌范围（FOLLOW_RANGE 属性） |
| | phase2LockMark | 6 | 触发二阶段的锁血档位 |
| | phase2EntryBurstCount | 6 | 二阶段进场重音连发次数 |
| | maxHitDamagePercent | 0.25 | 限伤：单次伤害上限 = 最大生命×该值（0=关闭） |
| | maxDamagePerSecond | 0.0 | 限DPS：滑动 1 秒窗口伤害上限（0=关闭；建议 20/30/40） |
| **phase2_buffs**（3项） | phase2BuffsEnabled | true | 二阶段强化药水开关 |
| | phase2StrengthLevelLow | 2 | 低档力量等级 |
| | phase2StrengthLevelHigh | 5 | 高档力量等级 |
| **summon**（4项） | maxMinions | 32 | 召唤上限 |
| | refillHysteresis | 4 | 满员迟滞（低于 max-N 才恢复） |
| | survivalWeight | 1.0 | 召唤评分参数1（生存权重） |
| | attackWeight | 1.0 | 召唤评分参数2（输出权重） |
| **scoring**（3项） | baseRouletteWeight | 2.0 | 轮盘保底基数 |
| | dynamicScoreCap | 5.0 | 动态偏移上限 |
| | dpsAdjustRate | 0.5 | DPS修正速率 |
| **casting**（10项） | extraCastCooldown | 20 | 额外施法冷却 |
| | maxCastWindowTicks | 50 | 单次施法窗口上限（tick） |
| | climaxWarmupMultiplier | 0.5 | 高潮前摇倍率 |
| | phase1BuildupRotation | "123" | 一阶段铺垫期通道轮换 |
| | phase2BuildupRotation | "222" | 二阶段铺垫期通道轮换 |
| | phase1ClimaxChannels | "123" | 一阶段高潮三通道 |
| | phase2ClimaxChannels | "222" | 二阶段高潮三通道 |
| | phase2TeleportIntervalFactor | 1.0 | 二阶段瞬移间隔系数（范围 0.1~1.0，只能缩短二阶段瞬移间隔） |
| | phase2BuildupArcRadius | 6.0 | 二阶段铺垫期弧形瞬移半径 |
| | phase2BuildupArcEveryN | 3 | 每 N 次铺垫攻击触发一次弧形瞬移 |
| **focus**（1项） | blacklist | "goetytwilight:destruction_focus" | 聚晶黑名单（容错解析：单 id / 英文逗号分隔 / 数组写法，实时解析不缓存） |
| **wand_whitelist**（1项） | whitelist | "" | 法杖白名单（格式同上，供未加入 `goety:wands` 标签的附属法杖激活仪式） |
| **music**（12项） | syncInterval | 20 | 音乐同步间隔 |
| | accentKnockbackBase | 0.4 | 普通重音斥力 |
| | accentKnockbackValley | 0.8 | 低谷重音斥力 |
| | accentKnockbackClimax | 1.2 | 高潮重音斥力 |
| | phase2ValleyKnockbackMultiplier | 2.0 | 二阶段低谷斥力倍率（注意：二阶段低谷被替换为铺垫，故该键在二阶段不可达） |
| | accentShakeTicks | 10 | 重音镜头抖动持续 tick |
| | accentShakeStrength | 2.0 | 重音镜头抖动强度 |
| | accentParticles | false | 旧重音粒子开关（兼容选项） |
| | accentWave | true | 非对称径向声波涟漪（0.0.12 起锚定在触发瞬间的坐标、只按 34 tick 计时清理） |
| | accentSound | true | 重音音效开关 |
| | volume | 4.0 | Boss 音乐音量 |
| | pitchPhase1 | 1.0 | 音乐播放速度（一/二阶段共用同一速度） |
| **llm**（2项） | apiUrl | "https://api.openai.com/v1/chat/completions" | LLM 端点 |
| | model | "gpt-4o-mini" | LLM 模型 |
| **wand_upgrade**（6项） | wandUpgradeEnabled | true | 法杖升级掉落开关 |
| | wandWitchcraftBonus | 0.10 | 升级法杖巫法加成（10%） |
| | wandMagicDamageBonus | 0.40 | 升级法杖魔法伤害加成（40%） |
| | wandBonusStack | true | 加成是否可叠加 |
| | wandBonusAppliesToBoss | false | 加成是否对 Boss 生效 |
| | xpMultiplier | 4.0 | 经验倍率（×4） |

> API Key 不在此 toml 中，存放于 `focus_classification.json`。
>
> `[llm]` 端点默认面向国际（OpenAI：`https://api.openai.com/v1/chat/completions` + `gpt-4o-mini`）；
> **中国大陆环境请改为 `https://api.deepseek.com/v1/chat/completions` + `deepseek-chat`**
> （0.0.5 实测：`api.openai.com` 连接超时、`api.deepseek.com` 可达；评分失败提示现已带目标 URL 与模型名）。
>
> ⚠️ **0.0.10 迁移提示（必须告知老玩家）**：`music.accentParticles` 是**已存在的键**，本轮只是把默认值
> 由 `true` 改成 `false`（键保留为手动兼容选项）——**Forge 按 key 合并配置，不会用新默认值覆盖已有 toml**，
> 所以老配置里它仍写着 `true`，会出现「旧粒子冲击环/音符 + 新声波涟漪」同时播放，需**手动改成 `false`**；
> 而本次新增的 `music.accentWave`（默认 `true`）会被 Forge **自动补进**老 toml。
> **⇒「新增键会自动补齐、无需删 toml」与「改默认值不会回填老 toml」是两件事，不要混为一谈。**
> 本次已在用户实例 `versions\测试` 的 toml 手工迁移完毕（`accentParticles=false` + `accentWave=true`）。

### `run/config/goetytuner/music_score.json`（真实值）
```json
{"segments":[{"phase":"buildup","ticks":80},{"phase":"climax","ticks":960},{"phase":"valley","ticks":700},{"phase":"buildup","ticks":225}],"accents":[0,80,120,160,200,240,280,320,360,400,440,480,520,560,600,640,680,720,760,800,840,880,920,960,1000,1040,1120,1200,1280,1360,1440,1520,1600,1680,1740,1820,1900]}
```
- 总 1965 tick = 98.25 秒，与内置 ogg（98.27 秒）对齐；共 37 个重音

### `run/config/goetytuner/focus_classification.json`
- 结构为 `{apiKey, prompt, foci{<id>:{category,attackScore,survivalScore}}}`
- 静态聚晶分类+评分表；LLM API Key 存储于此（不在 toml）
- 首次生成时只有 **3 条示例条目**（本仓库外的编译副本 `...\run\config\` 目前仍是这 3 条），其余由启发式分类器兜底；
  而**游玩实例**里已用 LLM 批量评分写入 **277 条**（252 条离线 + 25 条游戏内，见 README 与
  `TECHNICAL_SUMMARY.md` §七.6）——两处数字不同是因为**看的不是同一个文件**，不是矛盾
- 运行时自动生成，可手动编辑覆盖

---

## 七、优先级排序与建议开发顺序

### 已完成（第 0.0.12 / 50 轮现状，保留划掉条目以便追溯）
- [x] ~~**E1 音乐播放控制**~~：停止/循环/切换/脱战对齐已全部实现（第 20~21 轮）
- [x] ~~**E2 重音刻度HUD同步**~~：segments + accents 全量同步 + 分阶段样式（第 13/19 轮）
- [x] ~~**E6 正式生成方式**~~：仪式召唤落地（第 36 轮）
- [x] ~~**B1-B2 音乐接入+标注**~~：曲目转码 + 分段/重音标注（第 20 轮）
- [x] ~~**B3-B4 音乐播放控制完善+重音HUD**~~：客户端循环实例彻底解决重叠/不停（第 21 轮）
- [x] ~~**A1-A2 原版渲染打磨**~~：初版贴图 + `TunerCapeModel`/`TunerCapeLayer`（第 19 轮）
- [x] ~~**重音特效**~~：冲击环 + 音符爆发 + 阶段差异化音效（第 19/31 轮；**0.0.10 改为 10 环非对称径向声波涟漪，旧粒子默认关闭**）
- [x] ~~实测验证效果清除、锁血V2、传送间隔手感~~（数值已于第 25~34 轮重调）
- [x] ~~**死亡状态检测 bug**~~：删除 `applyLockHealth()` 里与 `maintainDeathState()` 重复的「死亡自愈」分支
  （它实际可达，曾击败 `/kill` 后门 + 白吃一档锁血 + 把客户端留在死亡动画）；死亡回弹**唯一**权威实现改为
  `tick()` → `maintainDeathState()`（第 49 轮 / 0.0.11，详见 `TECHNICAL_SUMMARY.md` §3.11）
- [x] ~~**`CastChannel` 回调成对性**~~：`logCast` 提到 `onCastStart` **之前**（使其成为 `return` 前最后一步），
  并新增 `startEmitted` 标志在 `beginCast` 兜底 `catch` 里补发 `onCastFailed` 平衡计数 ——
  消除「`onCastStart` 孤儿回调 ⇒ 施法状态计数与立方体类别掩码永久 > 0 ⇒ 立方体一直高亮 / 蹲姿卡住」隐患
  （第 50 轮 / 0.0.12，详见 `TECHNICAL_SUMMARY.md` §3.13(3)）
- [x] ~~**音乐条 HUD「太突兀」**~~：外观重做（`260×6`→`204×8`、逐行混色 + 2px 过渡缝、三层柔和投影、
  阶段文字改像素符号、刻度与闪烁调淡）；同轮把**径向声波涟漪锚定到触发瞬间的坐标**并改为**只按时间清理**
  （第 50 轮 / 0.0.12，详见 `TECHNICAL_SUMMARY.md` §3.13(1)(2)）

### 仍待办
1. **A3 剩余粒子**：传送 / 净化环 / 二阶段碎裂+天空盒 / 施法前摇聚能
2. **C Boss专属魔杖**：`TunerWand` 代码可先做，贴图后补
3. **D 附属兼容与性能测试**：安装其他 Goety 附属联合测试
4. **E3 竞态实测**：三通道共享主手杖换装竞态
5. **E4 / E5**：DoT 伤害归因、召唤物 owner 识别
6. **E7 音效**：死亡/受击/环境音效
7. **A4 护甲显示确认**：确认与其他护甲显示 mod 无冲突
8. **美术/表现项游戏验收**：0.0.10 美术项（披风摆动、三颗立方体轨道与高潮并行高亮、透明声波与地形/水面/着色器交互、原版刷怪蛋观感）；
   以及 **0.0.12 的新外观**（音乐条 204×8 混色/柔和投影/像素阶段符号、涟漪锚定触发点坐标的观感）
   ——离线校验与编译/字节码核对**不能替代画面验收**

---

## 八、风险评估

| 风险 | 等级 | 缓解措施 |
|---|---|---|
| ~~GeckoLib迁移引入新mixin冲突~~ | 已消除 | 第十九轮彻底移除GeckoLib（Goety本体零引用实证） |
| 音乐与分段表不同步 | 中 | 标注工具+实测校准；分段表可热改config |
| 三通道共享主手杖的换装竞态 | 低 | 待实测；聚晶池锁池已解决重复抽签（`BossWandHelper.installFocus` 每轮改写主手杖 NBT） |
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
- ✅ 锁血V2：lockMark初始0起步，阶梯锁血（档数与档距已于第25~34轮大改，见后文），最后一档可杀
- ✅ 锁血防死：hurt拦截致死 + 死亡状态自愈（/kill不僵局）
- ✅ BossWandHelper注释修正（NAMELESS_STAFF → DARK_WAND）

### 第十轮（2026-08-18，本次）
- ✅ 效果清除实现：低谷清正面/高潮清负面
- ✅ 全面审计：Java 源文件 + 资源文件 + 配置项全量盘点（第10轮口径；第41轮规模已远超）
- ✅ 新计划书V2编写

### 第十一~十七轮（2026-08-18~19）
- ✅ 索敌去视线约束、施法窗口封顶（常量，后于第25~34轮由 40 调到 50）、聚晶池锁池
- ✅ 锁血宽限期(LOCK_GRACE_TICKS)+免疫伤害、二阶段触发lockMark化(4→5，后于第25~34轮改为6)
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
- ✅ 音乐播放配置：music.volume / music.pitchPhase1（第二十轮曾加二阶段速度项，第二十一轮已删除）；
  起始偏移不支持（原版API限制），说明已写入 sounds/README-music-resources.txt
  （0.0.7 由中文名 `README-音乐资源说明.txt` 改名而来：原中文路径会触发原版资源包校验的
  `Invalid path in pack` 错误日志，虽然无害但属于模组自身造成的日志噪声）
- ✅ 音乐条样式：260x6（更扁更长）、三段配色提亮（亮蓝/亮橙红/亮紫）、指针与判定线加粗5px
  （**注：0.0.12 已重做为 204×8 + 逐行混色 + 柔和投影 + 像素阶段符号，见第 50 轮**）

### 第二十轮（2026-08-20，本次）
- ✅ 音乐接入：用户曲目 output.wav（98.27s/BPM120/G大调）转码为 boss_music_phase1.ogg 打包进资源
- ✅ 乐谱标注：分段 80/960/700/225（铺垫/高潮/低谷/铺垫，总1965tick=98.25s）；
  重音37个（高潮每4拍、低谷/铺垫每8拍，命中4s/52s/1:27三转换点+循环点0），写入代码默认值+运行时配置
- ✅ 二阶段变速（**第二十一轮已撤销，见下**）：MusicController 浮点速度推进（独立二阶段速度项）、
  SMusicSyncPacket 增加 speed 字段、客户端平滑推进乘 speed——音乐条/重音/分段切换与变速音频严格对齐
- ✅ 无间隔循环：乐谱wrap点（consumeLooped）自动重发playSound（第二十一轮改由客户端 looping 实例接管）
- ✅ 二阶段复用一阶段音频：playBossMusic 统一入口（二阶段加速播放同一文件）；删除 BOSS_MUSIC_PHASE2 注册与sounds.json条目
- ✅ 重音刻度分阶段样式：铺垫=细线 / 高潮="中"字（竖线+中部空心方框）/ 低谷=加粗3px

### 第二十一轮（2026-08-20，本次）
- ✅ 音频客户端化：新增 client/BossMusicManager——单一循环音效实例
  （SimpleSoundInstance：looping + Attenuation.NONE + relative，原版 forMusic 同款模式），
  起播/停播由 MusicStateClient 同步状态驱动；服务端不再 playSound（playBossMusic 已删除）
- ✅ 问题1·一二阶段衔接重叠：撤销第二十轮的二阶段变速方案——全程 pitch=pitchPhase1 恒定，
  enterPhase2 不重置进度不重启音频；二阶段速度配置项删除，looped/consumeLooped 机制移除
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
- ✅ 问题4·二阶段前摇系数 + 仇恨保留：新增 phase2WarmupFactor
  （当时为固定 0.8；后改为随锁血档位推导 `clamp(1.0-(lockMark-6)*0.1, 0.1, 1.0)`，
  高潮实际前摇=climaxWarmupMultiplier×该系数，铺垫前摇=1.0×该系数）；
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

### 第二十五~三十三/三十四轮（2026-08-19~20）
- ✅ 聚晶黑名单双层防护 + 施法自愈：配置黑名单拦抽签，`RUNTIME_BLACKLIST` + 实体级拦截
  `goetytwilight:destruction` 兜住池外/存档内存量实体
- ✅ Boss 数值大改：血量 1024→216、锁血间隔 128→18、`PHASE2_LOCK_MARK` 4→6、蓄力上限 40→50
- ✅ 打包与配置坑：必须 bump `mod_version`；旧 toml 会锁死旧默认值（删配置才生效）；
  `processResources` 需 `filteringCharset UTF-8`
- ✅ 链锤崩溃根治：三份 crash-report 同因——`DestructionEntity.onHitBlock` 把 null 传给
  `BlockEvent$BreakEvent` 构造器 NPE，崩溃在实体 tick 阶段；黑名单拦不住已存在的实体，
  故加实体级拦截 + 默认黑名单；黑名单输入改 String 容错解析
- ✅ 聚晶分类修复（goetytwilight 用 `.desc` 后缀导致 `ice_crystal_focus` 被误判 ATTACK）+
  目光锁定 + 近战易伤 + 召唤冷却免疫
- ✅ 施法方向三调用点（前摇每 tick / `startSpell` 前 / 瞬发前，并同步 `yRotO/xRotO`）；
  前摇下蹲（`DATA_CAST_STATE` 计数器化）；逐跳瞬移追击；二阶段进场连发
- ✅ 死亡回弹改覆写 `tick()` + `reviveIfDead()`。**决定性发现：`LivingEntity.tick` 在
  `isDeadOrDying` 时不再执行 `serverAiStep`，原来放在 aiStep 里的死亡自愈是死代码，从未触发**
- ✅ 二阶段药水改原版力量 + `SPELL_POTENCY` modifier
  （Goety 的 BUFF/RALLYING 都只加近战属性，法术输出零增益）

### 第三十五~三十六轮（2026-08-20）
- ✅ 任务 #118 设计稿三问钉死：Boss 受法杖影响仅作聚晶容器；法杖无"基础属性"可改；
  `ModAttributes` 26 个全是实体属性、无 per-wand NBT 加成
- ✅ **设计稿盲点修订**：`CastChannel` 每次 `installFocus` 会改写主手杖聚晶槽，直接掉主手
  会洗掉玩家聚晶 → 改为 `OriginalWand` 快照制 + `onLivingDrops` 掉快照
- ✅ v0.7.0 落地 3 个新文件 + 5 处改动
- ✅ 1.20.1 API 坑：`ItemStack.save()/of()` 无 `HolderLookup.Provider` 重载、
  `DamageSource` 无 `isMagic()` → 改用 `forge:is_magic` tag + msgId 兜底

### 第三十七轮（2026-08-20）
- ✅ 仪式 `duration` 单位实证为**秒**（非 tick）、`soulCost` 为每秒消耗
- ✅ 仪式中断根因 = 灵魂不足（600 秒 × 1/秒 = 600 灵魂，玩家 150 秒时耗尽）
- ✅ `logoFile` 必须在 `[[mods]]` 块内部；`duration 600→10`、`soulCost 1→3`
- ✅ `/goetytuner tune` 命令（`getPlayerOrException` 受检异常，command 方法须
  throws `CommandSyntaxException`）；二阶段药水补 `SPELL_POTENCY` modifier

### 第三十八~四十一轮（2026-08-20~21）
- ✅ v0.0.0 版本号重置 + 法杖白名单（`identify()` 重写）
- ✅ v0.0.1 图标热修复（1250×450 → 512×184）
- ✅ v0.0.2 署名 + 整理 + 初始化 git（远程 `https://github.com/QieFanQie/GoetyTuner.git`，
  推送需用户本机执行）
- ✅ v0.0.3 配置界面可见性修复（根因：配置注册为 COMMON 且未注册 `IConfigScreenFactory`，
  Mods 菜单不显示 Config 按钮，`TunerConfigScreen` 从未被打开）+ LLM 评分 UI 完整化

### 第 42~43 轮（0.0.4 / 0.0.5）
- 0.0.4：修复 CastChannel 在 startSpell 异常路径误调 onCastFailed（并行高潮下会把 activeWarmups 提前减到 0、
  导致 DATA_CAST_STATE 误清 0、施法蹲姿提前收势——游戏内实测确认修复成功）；清理 16 处死代码与 5 处未用 import；
  修正 8 处与代码不符的注释/默认值。
- 0.0.5：LLM 评分失败信息带上目标 URL 与排查提示（此前只报 HTTP connect timed out，
  且换任何 Key 报错相同——根因是配置仍指向不可达的 api.openai.com）；
  提示词框改多行并预填标准模板；一轮系统性性能优化（详见 TECHNICAL_SUMMARY.md §3.7）。

### 第 44 轮（0.0.6）
- 新增「限伤」：单次伤害上限 = 最大生命 × `maxHitDamagePercent`（默认 0.25 ≈ 54 点，相对宽松）；
  实现照搬 Goety 本体做法（覆盖 `actuallyHurt` 取 min + 跳过 `BYPASSES_INVULNERABILITY`），
  受击动画/击退/无敌帧照常。参照值：Goety 的 Apostle/Vizier/EnderKeeper 为固定 20、RedstoneMonstrosity 为 25。
- 新增「限DPS」：滑动 1 秒（20 tick）窗口预算，预算耗尽则本次伤害被完全吸收；默认关闭（0），
  建议开启值 20/30/40（对应最短战斗约 11/7/5.4 秒）。限伤管「单次」、限DPS 管「每秒」，两者互补。
  ⚠️ **该"11/7/5.4 秒"估算已被第 45 轮实证推翻**（锁血阶梯按宽限窗口推进、不按每秒伤害量推进），
  见下方第 45 轮条目与 `TECHNICAL_SUMMARY.md` §3.8。
- 配置总数 58 → 60。

### 第 45 轮（0.0.7）
- 从游玩实例日志发现：本实例 200 个聚晶，点「开始评分」后仅 `applied 25 foci`（配置文件 252→277 条），
  且无非法 id 告警、无解析失败 → 单请求 200 条被输出长度限制截断。改为 60 条/批顺序分批 + 显式 max_tokens=4096 +
  覆盖不足时 WARN。离线脚本同策略实测 252/252 全部返回。
- 修线程泄漏：`Executors.newSingleThreadExecutor()` 原写在方法体内，每点一次泄漏一条常驻线程；
  改为静态守护线程单例。
- 提示词格式化加固：`String.format` → `replace("%s", ...)`，避免自定义提示词含 `%` 时抛异常、按钮永久卡在「正在评分…」。
- 资源改名：`sounds/README-音乐资源说明.txt` → `README-music-resources.txt`，消除原版资源包校验的
  `Invalid path in pack` 错误日志。
- **重要实证（锁血机制）**：读 `applyLockHealth` + 日志时间戳确认——血量低于本档地板即被恢复到地板并进入宽限期，
  超出伤害被丢弃 ⇒ 打 1000 点与打 18 点在阶梯上都只推进一档。每档耗时 = `lockGraceTicks`(0.5s 下限) + 玩家命中间隔：
  7 轮完整的 11 档阶梯实测总时长 7.25 / 32.90 / 21.50 / 7.90 / 57.50 / 79.87 / 9.50 秒，**每档中位 2.15 秒**
  （最小 0.72 / 最大 7.99）。⇒ 锁血本身就是一个比 0.0.6 限伤更严格的隐式限伤；**限伤/限DPS 在当前设计下作用有限**，
  真正决定战斗时长的是 `lockGraceTicks` 与档位数（maxHealth/lockHealthInterval）。

### 第 46 轮（0.0.8）
- 用户提问「聚晶数量随附属变化，系统会不会出问题」→ 审计结论：聚晶池是运行期扫描，增删附属重启即自适应，
  配置里多余条目不会被匹配（无害）、缺少的走启发式兜底。但发现并修掉 4 个脆弱点：
  ① initIfNeeded 扫描无逐项兜底（某个附属 IFocus 抛异常会让整个扫描失败）；
  ② applyTo 分类阶段同样可能抛异常（已整体兜底，失败保留默认分类）；
  ③ CastChannel.beginCast 原本只有 startSpell 被 try 包住，conditionsMet/installFocus/castDuration/
     CastingSound/castingVolume 都在 try 之外 → 附属法术在此抛异常会一路冒泡到 serverAiStep 崩服；
     现统一兜底（beginCast→startCast），instantCast/interrupt(stopSpell)/finishCast(spellCooldown) 同样加固；
  ④ returnEntry 改幂等（避免多路径归还把同一聚晶复制多份、放大抽取权重）。
- 死亡状态完善（用户反馈：特殊手段会让 Boss 处于"血量不为 0 但已在死亡动画"）：反编译实证
  LivingEntity.deathTime 是普通字段非同步数据、全 MC jar 只有 5 个类引用它、唯一归零处是
  LocalPlayer.resetPos（玩家复活专用）、handleEntityEvent 无 35 分支（图腾复活广播的事件在 1.20.1
  不产生客户端行为）⇒ 服务端复活后客户端模型会一直躺着。
  三层修复：① 新增 SEntityRevivePacket（通道 id2）+ ClientDeathAnimation 复位客户端 deathTime/hurtTime/姿态；
  ② maintainDeathState 新增"血量>0 但 deathTime>0"的脏状态分支（只清动画、不吃锁血档位）；
  ③ 锁血未耗尽时拦截 remove(KILLED)（原版 deathTime=20 时移除后无法再回弹）。

### 第 47 轮（0.0.9）
- 用户要求"为 /kill 的击杀打开后门"：0.0.8 的死亡回弹 + remove(KILLED) 拦截会让管理员 /kill 也杀不死 Boss
  （被宽限期免疫吞掉 / 被致死截断压到 1 血 / 被回弹救活 / 被 remove 拦截）。
- 字节码实证调用链：KillCommand → Entity.kill() →（虚分派）LivingEntity.kill() →
  hurt(damageSources().genericKill(), Float.MAX_VALUE)。故用 DamageTypes.GENERIC_KILL 精准识别管理员指令，
  无需权限判断。
- 实现：新增 adminKillPending；hurt() 最前面识别 GENERIC_KILL → 置位并直接 super.hurt（跳过身份免疫/近战易伤/
  宽限期免疫/致死截断）；maintainDeathState() 与 canStillRevive() 均先看该标记（不回弹、不拦 remove(KILLED)）；
  状态正常时清除标记；限伤/限DPS 也显式跳过 GENERIC_KILL（不依赖 BYPASSES_INVULNERABILITY tag 内容）。

### 第 48 轮（0.0.10）
- **本轮是美术/表现层交付，不改战斗数值与施法流程**（新增 Java 4 个、改动 6 个、贴图 2 改 2 增 1 删）：
  - 身体贴图 `tuner.png`（64×64）按用户参考图重画：紫黑外套、浅紫 V 领、紫色内衬/手套/裤靴、青蓝链饰；
    **头部区（y 0..15 全宽）与 0.0.9 逐像素完全一致**（独立脚本验证 0 像素差异）。
  - 披风 `tuner_cape.png`（64×32）改为 **8 条 × 每条 2 像素高**的色带
    `#1E0D32 #38204D #523367 #6C4882 #875E9E #A078BA #BA96D8 #D4B6F2`，自上而下亮度单调递增。
  - 悬浮立方体（新增 `TunerOrbLayer`/`TunerOrbModel`，模型层 `goetytuner:tuner_orb`）：3 颗边长 **0.30 格**、
    公转半径 **1.05 格**、离脚约 **1.20 格**、上下浮动 ±0.06 格；三颗相位差 120°，公转 80 tick(4s)、
    自转 Y 40 tick(2s) / X 60 tick(3s)；红 `#FF3B30`(攻击)/蓝 `#2FA8FF`(防御)/灰 `#C8C8C8`(召唤)，
    施法时 RGB 增益至 1.8 并叠加 1.15 倍壳体（峰值 alpha 0.25），**3 tick 过渡**。
  - 声波涟漪（新增 `AccentWaveRenderer`）：**10 环 × 32 角分段**，半径 0.6~6.0 格（间距 0.6），相邻环延迟
    **2 tick**，每环升 8 / 落 8 tick（LIFE=16），总 **34 tick**；淡白半透明（顶部 alpha 峰值 0.32，
    底部为顶部的 0.35 倍）；高度 `h=0.56*sin(πp)*(1+sin(3θ+0.10t−0.28i)*a)`，**峰侧 a=0.40 / 谷侧 a=0.27**
    （非对称），地面偏移 0.01，**绝对最高 0.794 格**（设计 ≤0.8 格）；每次重音覆盖同实体旧波不叠加，
    实体死亡/移除/换世界或超时清理，无活跃波时渲染器立即返回、不扫描世界实体。
  - 刷怪蛋改走原版 `{"parent": "minecraft:item/template_spawn_egg"}`（主色 `0x8A2BE2` 紫 / 副色 `0x2FA8FF` 亮蓝），
    自定义蛋贴图 `textures/item/tuner_spawn_egg.png` **已删除**。
- **代码要点**：`TunerBoss` 新增 `DATA_CAST_CATEGORIES`（INT 位掩码，bit0=ATTACK/bit1=DEFENSE/bit2=SUMMON/bit3=OTHER）
  驱动立方体高亮——**高潮是三通道并行施法，必须能同时点亮多颗**，单值「最后一次施法分类」会被后到的包覆盖；
  计数用 `IdentityHashMap` 身份集合做**幂等**，避免「finish 之后又 failed」重复递减打穿并行通道计数
  （详见 `TECHNICAL_SUMMARY.md` §3.12）。
- **渲染实证**：`RenderType.entityTranslucent` **自带 NO_CULL**（圆柱面双面可见，无需手动 `disableCull`）；
  顶点必须按 `NEW_ENTITY` 顺序写入 POSITION→COLOR→UV→OVERLAY→LIGHT→NORMAL（顺序错位不报错、只静默错乱）。
- **配置与网络**：新增 `music.accentWave`（默认 **true**），并把**已有键** `music.accentParticles` 的默认值由
  `true` 改为 `false`（键保留作手动兼容选项）；配置总数 **61 项 / 10 section**（`music` 段 11→12）。
  网络协议 `1.0` → **`2.0` 且改严格匹配**（`"2.0"::equals`），注册第 4 个包 `SAccentWavePacket`（**通道 id 3**，
  显式限 `PLAY_TO_CLIENT`）⇒ **联机双方必须同时更新到 0.0.10**，否则连接被拒。
- ⚠️ **迁移（与「新增键自动补齐」是两件事）**：`accentParticles` 是**已有键**，Forge 按 key 合并、**不会用新默认值
  覆盖已有 toml** → 老玩家文件里仍是 `true`（旧粒子与新声波同时出现），需**手动改成 `false`**；`accentWave`
  是新增键，会自动补进老 toml。本次已在用户实例 `versions\测试` 的 toml 手工迁移
  （`accentParticles=false` + `accentWave=true`）。
- 部署产物：`goetytuner-0.0.10.jar`（1,750,152 B / md5 `5C05779CEC4CD4765BD2510EB4D4A6ED`）→ `versions\测试\mods\`。
- **待验收**：披风摆动、立方体轨道与高潮并行高亮、声波与地形/水面/着色器交互、原版刷怪蛋观感均**尚未进游戏实测**
  （离线校验只证明参数/逐像素/编译正确），见 `ART_ASSETS_REPORT.md`。

### 第 49 轮（0.0.11）
- **本轮只修一个真 bug，只改 2 个文件**（`gradle.properties` 版本号 + `entity/TunerBoss.java`）：
  **无新增类、无新增贴图、无新增配置项**（仍 61 项 / 10 段，`music` 段 12 项），战斗数值与施法流程未动。
- **起因（用户玩出来的）**：用户反馈「每 tick 检测死亡状态似乎有 bug」。查用户实例 `versions\测试\logs\latest.log` 得铁证：
  `L4793 10:15:24.577 /kill backdoor: bypassing lock-health protection (health=29.485922, mark=10)`
  → `L4794 10:15:24.625 Revived from death → lock at 18.0 (mark=11/12)`
  → `L4799 10:15:25.477 /kill backdoor: bypassing lock-health protection (health=18.0, mark=11)`。
  即 `/kill` 刚打死 Boss，**48 ms 后 Boss 又带 18 血复活**，用户只能再打一次 `/kill`。
- **根因**：`applyLockHealth()`（由 `aiStep()` 调用）里有一段与 `maintainDeathState()` **重复**的「死亡自愈」分支，
  其注释断言「实体死亡后 `aiStep()` 根本不执行 ⇒ 本分支是死代码、不可达」。**该断言是错的**：
  ① 反编译实证 `LivingEntity.tick()` 共 **366 行字节码**，其中 **`aiStep()` 只有 1 处调用（偏移 179、完全无条件）**，
  `isDeadOrDying` / `serverAiStep` / `tickDeath` 在该方法内**一次都没出现**；
  ② 真正被死亡把关的是 `baseTick()`：**偏移 357** 判 `isDeadOrDying()` → **偏移 375** 调 `tickDeath()`。
  原注释把 `aiStep()` 与 `serverAiStep()` 混为一谈，故该分支**实际可达**。
- **三重后果**：① 该分支**不看 `adminKillPending`** ⇒ `/kill` 后门被它击败；② 它**白吃一档锁血**（`lockMark++`）；
  ③ 它只写 `deathTime = 0`、**不走 `resetDeathAnimation()`**（不发 `SEntityRevivePacket`、不清 `hurtTime`/`hurtDuration`/`Pose.DYING`）
  ⇒ **客户端停在死亡动画**，即更早反馈的「血量不为 0 但已是死亡动画」复发。
  **为何第二次 `/kill` 就成功**：此时 `lockMark = 11 = maxMark-1`，该分支的 `lockMark < maxMark-1` 不再成立（与日志吻合）。
- **修复**：① 删除该重复分支，改为在 `applyLockHealth()` **开头**对死亡状态直接早退
  （`if (this.isDeadOrDying() || this.deathTime > 0) return;`）；
  ② 明确「**死亡回弹的唯一权威实现是 `tick()` 里的 `maintainDeathState()`**」（它尊重 `/kill` 后门，
  并通过 `resetDeathAnimation()` 同步复位客户端动画），并写进 `maintainDeathState()` 的 javadoc；
  ③ 重写 `tick()` 的 javadoc：删掉基于错误前提的旧说明，换成上述反编译实证的调用链，并说明**为什么必须放在 `super.tick()` 之前**
  （`tickDeath()` 在 `baseTick()` 里才递增 `deathTime`，`aiStep()` 又在其后无条件执行，两者都不是回弹该待的地方）；
  ④ 以 `javap` 验证部署版 jar 的 `applyLockHealth` **不再包含** `Revived from death` 调用
  （该类仍含该字符串，来自 `maintainDeathState`，属正常）。
- **「死亡检测要不要降频」的结论：不需要**。`maintainDeathState()` 每 tick 的**热路径只有两次读取**
  （`getHealth() <= 0` 判定 + `deathTime` 字段比较）后立即 return；真正有开销的动作（复位、发 `SEntityRevivePacket`、
  打日志、`onLockTriggered()` 里的强制瞬移）**只在确实死亡或脏状态时才执行**，属极少数 tick。且**不能简单降采样**：
  「情形 A」（血量 > 0 但死亡动画残留）必须**及时**发现，隔 N tick 才查会让玩家多看到几帧躺倒；
  将来真要省应走**事件驱动**（`hurt()`/`setHealth()` 置「待检查」标志 + 末端低频兜底），而非降低检查频率。
- 部署产物：`goetytuner-0.0.11.jar`（**1,750,088 B / md5 `14B6ABD6EDE84786ED125DB6B10EB8D1` / jar 内 101 条目**）→ `versions\测试\mods\`（旧 0.0.10 已删）；
  构建在主副本就地 `gradlew build` 成功（48 s，仍只有原有 3 条 Forge 弃用警告）。
- **仍未修（不要误记为本轮已修）**：`CastChannel` 开始回调之后仍有日志调用、异常路径下外层兜底回调可能不配平
  —— 本轮没碰施法流程，见 `TECHNICAL_SUMMARY.md` §七；0.0.10 美术项的游戏画面验收也仍未做。
  ⚠️ **上述 `CastChannel` 一条已于下一轮（第 50 轮 / 0.0.12）修复**：`logCast` 移到 `onCastStart` 之前 +
  `startEmitted` 标志兜底补发 `onCastFailed`，见下方「第 50 轮」与 `TECHNICAL_SUMMARY.md` §3.13(3)。

### 第 50 轮（0.0.12）
- **本轮三件事：表现层重做 ×2 + 一个回调成对性修复**。功能改动落在 3 个 Java 文件
  （`client/AccentWaveRenderer.java` 124 行、`client/MusicBarHud.java` 453 行、`entity/ai/CastChannel.java` 408 行），
  另 `entity/TunerBoss.java`（2001 行）**只改了一处方法 javadoc**（无行为变化）+ 版本号；
  **无新增类/贴图/配置项**（`.java` 仍 45 个，配置仍 61 项 / 10 段、`music` 12 项；jar 条目 101 → 102）。
- **① 径向声波涟漪锚定触发瞬间的坐标（`AccentWaveRenderer`）**：
  - 原实现只存 `startTick`，渲染时**每帧读 Boss 当前位置** ⇒ 涟漪**跟着 Boss 跑**；
    而调律师**每次锁血触发都会强制瞬移**（`onLockTriggered` → `tryTeleportNear`），
    跟着跑会让「由内向外荡开的声波」被实体拖着走，观感完全不对。
  - 改为触发时记录**脚下的世界坐标**（新增私有 `record Wave(long startTick, double x, double y, double z)`），
    整条波在这个固定点上播完；渲染锚点 `worldPos − cameraPos` 与 Goety 本体 `PrismaBeamRenderer` 写法一致。
  - 清理**只按时间**：`DURATION = (RINGS-1)*DELAY + LIFE = 34 tick`；**不再**因 Boss 死亡/被移除/离开视野提前掐掉
    （波已与实体无关，应当播完）。条目数 101 → **102**（多出 `AccentWaveRenderer$Wave` 内部类）。
- **② 音乐条 HUD 外观重做（`MusicBarHud`，用户反馈「太突兀」，四项一起改）**：
  - **尺寸**：`260×6` → **`204×8`**（改短加厚），底部边距 64 → **62**。
  - **柔顺渐变**：分段由「硬边纯色块」改为**逐行混色**（`fillSegment`：上半向白提亮、下半向黑压暗），
    分段交界处叠 **2px 半透明过渡缝**（`drawSeam`），消除「一刀切」的直角接缝。
  - **边框**：单一硬矩形底 `0xAA000000` → **三层由外向内渐深的柔和投影**（`drawSoftBackdrop`），
    最外层**四角留空**以模拟圆角。
  - **阶段提示改隐晦符号**：原先在条上方画「铺垫/高潮/低谷」**文字**，改为**像素绘制**的
    `● ● ●`（高潮）/ `●`（铺垫）/ `- - - - - -`（低谷）（`drawPhaseIndicator` + `drawDot`）。
    **为什么不用 `⚪` 字符（实测结论）**：本客户端字体只有拉丁/希腊/西里尔等**位图字形**
    （`include/unifont.json` 的 providers 是**空数组**、jar 内**无任何 ttf**、也无 unicode 字形页；
    `options.txt` 只启用 `vanilla`+`mod_resources`，mods 里也没有 jar 提供字形页）⇒ **U+26AA 没有字形，
    直接写会显示成空白方块**；像素画必定可见且更隐晦。
    **副作用**：lang 里 `info.goetytuner.music.buildup / .climax / .valley` 三个键**当前已无任何代码引用**（保留未删，注释已说明）。
  - **重音刻度与闪烁调淡**：刻度纯白 `0xFFFFFFFF` → 半透明白 `0xB8FFFFFF`；闪烁峰值 `0xC0` → `0x88`。
    高潮的「中」字样式方框在 8px 高度下改为**上下各留 2px**。
  - **一阶段分段内容按条宽做 scissor 裁剪**：分段像素宽由浮点换算取整而来，总和可能比 `BAR_WIDTH` 多 1~2px，
    不裁剪会溢出到右侧边框上（1px 缝）⇒ 画分段前 `enableScissor(x, y, x+BAR_WIDTH, y+BAR_HEIGHT)`、
    画完解除**再画指针**（指针要上下探出条外，不能被裁）。
- **③ `CastChannel` 回调成对性修复（此前文档记为「仍未修」的已知边界，本轮已修）**：
  - **根因**：`startCast` 尾部原为 `callback.onCastStart(entry); BossWandHelper.logCast(level, boss, entry); return true;`
    ——`logCast` 排在 `onCastStart` **之后**且**会抛异常**；异常逃逸到 `beginCast` 的兜底 `catch`，
    而该 `catch` **不补发结束回调** ⇒ `onCastStart` 成为**孤儿回调**：`TunerBoss` 的施法状态计数
    （`DATA_CAST_STATE` 蹲姿）与 0.0.10 新增的**立方体类别位掩码**会**永久 > 0** ⇒ 对应立方体一直高亮、蹲姿卡住；
    又因 `TunerBoss` 用 `FocusEntry` **身份集合**做幂等去重，该聚晶之后**再也无法计入**，状态无法自愈。
  - **修复两步**：① 把 `logCast` 调到 `onCastStart` **之前**，使 `onCastStart` 成为 `return` 前最后一步
    （**结构上不可能**再被后续代码打断）；② 新增 `startEmitted` 标志（`beginCast` 开头重置），
    在 `beginCast` 的 `catch` 里检测「已发过 start 却异常逃逸」时**补发 `onCastFailed`** 让计数平衡
    ——这是防将来有人在 `onCastStart` 之后加代码的**第二道保险**。
- **另**：顺手修掉 `applyLockHealth()` 的**方法 javadoc** —— 它此前仍在描述 0.0.11 已删除的
  「死亡状态自愈 / 复活到下一档地板」行为（与同方法内的新注释自相矛盾），由上一轮的文档同步复核指出。
  **纯注释、无行为变化**（`TunerBoss.java` 1998 → 2001 行）。§3.11 的死亡逻辑本轮未再改动。
- 部署产物：`goetytuner-0.0.12.jar`（**1,752,068 B / md5 `66F14DFD926A068AA3284360976B78BB` / jar 内 102 条目**）
  → `versions\测试\mods\`（旧 0.0.11 已删）；构建在主副本就地 `gradlew build` 成功（45 s，仍只有原有 3 条 Forge 弃用警告）。
  ⚠️ **jar 的 md5 不可复现**（`MANIFEST.MF` 的 `Implementation-Timestamp`）：本轮连构三次得
  `5A63F73F13C102AAD9249F8B49A9763A`（1,752,059 B）→ `148F359EFD69102FC87971D1B9249270`（1,752,059 B）
  → `66F14DFD926A068AA3284360976B78BB`（1,752,068 B）——**前两次源码完全相同、大小也一样而 md5 不同**；
  判断"部署的 jar 对应哪份源码"要逐条目比对，别看整体 md5。
- **待验收**：0.0.10 美术项 + 本轮新外观（音乐条 204×8 混色/柔和投影/像素阶段符号、涟漪锚定观感）
  均**尚未进游戏画面验收**（本轮只做到编译通过 + `javap` 字节码核对）。

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

**两副本**（切勿混淆）：
- `D:\tiaolvshi\goety-tuner` —— **git 仓库 / 同步副本**（文档与源码的权威来源）
- `D:\测试\tiaolvshi\goety-tuner` —— **编译运行副本**（gradlew 实际执行处，含 run/ 目录）
- 修改任一后必须同步到另一个；构建前确认在编译副本上操作

---

## 十一、调参速查

| 场景 | 配置项 | 调整建议 |
|---|---|---|
| Boss太肉/太脆 | `maxHealth` / `lockHealthInterval` | 当前 216 / 18（12档）；降血量或增大档距=更少锁血次数 |
| 二阶段触发点 | `phase2LockMark` | 当前 6（默认血量 216 下约半血进入二阶段） |
| 二阶段前摇 | `phase2WarmupFactor`（派生值，不可直配） | `clamp(1.0-(lockMark-6)*0.1, 0.1, 1.0)`，与 `climaxWarmupMultiplier` 相乘 |
| 传送太频繁/太少 | `teleportInterval` | 200=10秒，400=20秒；二阶段可用 `phase2TeleportIntervalFactor`（0.1~1.0）单独缩短 |
| 召唤太多/太少 | `maxMinions` / `refillHysteresis` | hysteresis=满员后需低于max-N才恢复 |
| 高潮太难/太易 | `climaxWarmupMultiplier` | 0.5=前摇减半（更快施法），1.0=正常；施法窗口上限见 `maxCastWindowTicks`（当前 50） |
| 重音推力太强/弱 | `accentKnockback*` | 0.4/0.8/1.2 = 普通/低谷/高潮；二阶段低谷已替换为铺垫，`phase2ValleyKnockbackMultiplier` 不可达 |
| 重音手感 | `accentShakeTicks` / `accentShakeStrength` | 当前 10 tick / 2.0，调低可减轻镜头晃动 |
| 重音特效（声波/粒子） | `accentWave` / `accentParticles` | 0.0.10 起声波 `accentWave=true`（10 环非对称径向涟漪，最高 0.794 格）、旧粒子 `accentParticles=false`；**老 toml 的 `accentParticles` 仍是 `true`，需手改**（见 §六迁移提示） |
| 动态评分变化太慢/快 | `dpsAdjustRate` / `dynamicScoreCap` | rate=修正速率，cap=偏移上限 |
| 附属聚晶崩溃 | `focus.blacklist` | 逗号分隔 id 加入黑名单，重启后不参与抽签 |
| 附属法杖无法启仪式 | `wand_whitelist` | 填入法杖 id（未加入 `goety:wands` 标签的附属法杖） |
| LLM 评分请求超时/失败 | `llm.apiUrl` / `llm.model` | 默认 OpenAI 端点（国际）；中国大陆环境改为 `https://api.deepseek.com/v1/chat/completions` + `deepseek-chat`；失败提示会带目标 URL 与模型名 |
| 打得太快/被秒杀 | **`lockGraceTicks` / 档位数（`maxHealth` ÷ `lockHealthInterval`）**；`maxHitDamagePercent` / `maxDamagePerSecond`（仅作保险） | **实测结论：战斗时长主要由 `lockGraceTicks`（每档最短时长，默认 10t=0.5s）与档位数（默认 216/18 = 12 档）决定**——锁血阶梯把每次命中的有效伤害钳到一档，超出部分被丢弃，所以伤害上限不改变阶梯推进速度。`maxHitDamagePercent`（默认 0.25×216≈54，0=关闭）与 `maxDamagePerSecond`（默认 0=关闭）都只在**阶梯耗尽后**（血量 ≤18 那段）才可能起作用，主要作为防「秒杀式巨额伤害」的保险 |
| Boss 卡在死亡动画不动 | `lockDeathRevive`（默认 true） | 0.0.8 已修（新增 `SEntityRevivePacket` 同步复位客户端死亡动画 + 脏状态只清动画不吃档位 + 锁血未耗尽时拦截 `remove(KILLED)`，**但 `/kill` 例外**）；若仍出现，先确认 `lockDeathRevive` 未被关闭 |
| 想直接击杀 Boss（跳过锁血阶梯） | —（无对应配置） | 用 `/kill`（**0.0.9 起可用**：管理员指令识别 `DamageTypes.GENERIC_KILL`，无视锁血体系的全部保护）；常规输出仍受锁血阶梯约束 |
