# 调律师 (The Tuner) — 诡厄巫法附属Boss · 开发计划书 V2

> **0.0.20（第 59 轮 · 当前工作版本）**：用户提了**一条回归反馈 + 三条新功能**，本轮全部处理。**规模**：新增 **4 个 Java 类**（`.java` **55 → 59**：`combat/ServantWandBlessing`、`ritual/TunerServantSummonRitual`）+ **1 个仪式配方 json**；改 **6 个既有 Java 文件**（`entity/TunerServant`、`entity/TunerServantInteractions`、`ritual/ModRituals`、`ritual/WandUpgradeEvents`、`config/TunerCommonConfig`、`gradle.properties`）+ **2 个 lang**（键数 **34 → 43**，**+9 / −0**）；**配置 71 → 76 项**（`[servant]` 段 **3 → 7**，新增 `wandBlessingEnabled`；段数仍 **11**）；jar 条目 **117 → 122**（新增 3、**无删除**）；协议仍 **2.1**。① **【回归】左右键的指令提示被误删，已回补** —— 第 58 轮用户要"不要冗余提示"，我把 `TunerServantInteractions` 里**所有**动作栏提示都删了（连"操作结果反馈"一起删）；用户指出"这个提示是需要的" ⇒ 现在**分开对待**：**要有**"一次主动操作产生了什么状态变化"（`不释放：X` / `已取消不释放：X` / `优先释放：X` / `已取消优先释放：X` / 批量版 / `这不是你的调律师仆从`），**不要**物品介绍与刷怪蛋放置提示（那部分**没有**回退）。② **【新功能】仆从的仪式召唤** —— **每秒 10 能量、10 秒、魔法仪式**（反编译实证 `soulCost`/`duration` **都是"每秒"口径**：灵魂扣除与 `currentTime++` 同在 `gameTime % 20 == 0` 分支里 ⇒ `soulCost: 10, duration: 10` = 100 灵魂 / 10 秒），材料 **紫水晶碎片 ×4 + 红石 + 钻石 + 金锭 + 青金石** 共 8 个基座，**中心放一把带"调律加成"的法杖**才能激活（`activation_item` 只能写物品/标签、表达不了 NBT ⇒ 完全重写 `identify`；**两种调律加成任一 > 0** 即认；不满足就复用 Goety 自己的"无效的仪式"提示），召唤出的仆从 **tame=true 认主**，法杖被祭坛消耗、快照留给仆从。③ **【新功能】杖的加成决定仆从强度** —— ⚠️ **0.0.20 按用户反馈把判定来源从「调律·魔法伤害加成」改为「调律·巫法加成」**（前者单次默认 +40% ⇒ 打一次就几乎满配、**太容易触发**；后者 +10% ⇒ 要反复击杀才爬得上去），**阈值一个都没改**。`combat/ServantWandBlessing` 按 `pct = 加成 × 100` 持续给：**强健**（`goety:buff`）`1 + floor((pct−10)/20)` 级、**生命恢复**（>20% Ⅰ / >60% Ⅱ）、**抗性提升**（>80% Ⅰ / >100% Ⅱ，封顶）；做法是**低频自愈式刷新**（1 秒一次、每次挂 3 秒，数值未变则跳过）。⚠️ **一个必须告诉用户的坑**：`goety:buff`（强健）实际**只加 `ATTACK_DAMAGE`**（Goety 原文"每级增加 1 点的近战攻击伤害"），而本模组的仆从**刻意没有近战手段** ⇒ **强健对它的战斗力几乎无影响**（忠实照搬了规格，但"越强"的直觉不成立；可选修法 = 把等级同时换算成法术强度，**等用户决定**）。④ **【新功能】仆从死亡掉落召唤用杖** —— **原样返回**（保留原有加成、**不再额外升级**，与 Boss 死亡掉落"快照 + 叠加升级"刻意对照）。⑤ **【新功能】聚晶包 / 多晶大袋批量指令** —— 手持袋子左键 = 袋内**全部**设为不释放、右键 = 全部设为优先；**全部已是该状态 ⇒ 整体清除**（与单晶"再按一次取消"语义一致）；用通用 `ForgeCapabilities.ITEM_HANDLER` 读内容物（**聚晶包 11 格 / 多晶大袋 21 格**，字节码实证），非聚晶物品跳过、同 id 去重。⑥ **【追加·用户要求】「强健」等级 → 法术强度** —— 新增 `combat/BuffSpellPower` + 配置 `[casting] buffSpellPowerPerLevel`（**Int、默认 1**）。作用域**只限本模组的调律师一族**（本体 + 仆从，各按**自身**的强健等级，不是全局规则）。⚠️ **本轮最重要的实证**：`goety:spell_potency` 的基础值是 **0.0**、读取是 `(int)` 截断、法术把它当**平铺伤害点数** ⇒ **百分比 modifier（`MULTIPLY_TOTAL`）在该属性上恒等于 0**，所以本类必须用 **`ADDITION`**、且数值得是**整数点**（填 0.1 会被截成 0）。⚠️ 顺带查出**两处早已存在的死代码**：玩家的「调律:巫法加成」与 Boss 二阶段的「力量等级 × 0.1」**都是 MULTIPLY_TOTAL ⇒ 从未生效**（tooltip 显示但伤害不变）—— **只报告未改**（修法要重新定义数值语义，属平衡决策）。⑦ **【追加·用户要求】修掉两处「百分比法术加成从未生效」的死代码**（"保证实际生效和文字描述相同"）—— 新增 `combat/SpellDamageBonus` 作为**百分比法术伤害的唯一实现**（走 `LivingDamageEvent` ×(1+加成)）；删除 `WandUpgradeEvents#refreshWitchcraftModifier` + 它的两个监听器 + 原 `onLivingDamage`，删除 `TunerBoss#tickPhase2Buffs` 里的 `SPELL_POTENCY` modifier、改为 `phase2SpellDamageBonus()`（读自身力量效果等级）。**判定覆盖面同时修宽**（原判定只认 `forge:is_magic`，Goety 39 个伤害类型里只有 8 个 ⇒ Boss 主力法术全漏；现在并上 `goety` 命名空间判定）。**顺带不再放大自伤**。⚠️ **平衡影响**：Boss 二阶段法术伤害从"实际 +0%"变成 **+20%/+50%**、玩家巫法加成从 0 变成真 **+10%**（都是文字早承诺过的）。⑧ **【追加·用户反馈】「强健」等级其实是对的 —— 是原版不显示**：1.20.1 客户端 `EffectRenderingInventoryScreen#getEffectName` **只在 amplifier 1~9（等级 II~X）时附罗马数字** ⇒ 等级 1 与等级 ≥11 都只显示「强健」（+1000% 巫法 = 50 级，正落在隐藏区）。修法：新增 **`servant.buffMaxLevel`（Int，默认 10）** 把等级钉在可见范围，并加两条诊断日志（召唤时打印捕获到的杖与加成；等级变化时打印 `Buff N / Regen M / Resistance K`）。⑨ **【追加·用户要求】加强仆从限伤** —— 发现原来是**读错了键**（仆从借用 `[boss]` 的限伤，而限DPS 默认关闭）⇒ `[servant]` 新增独立且更狠的两个键：`maxHitDamagePercent`=**0.15**（单次 ≤32 点）、`maxDamagePerSecond`=**0.50**（每秒 ≤108 点 ⇒ 至少 2 秒才能打死）。⚠️ **本轮同样只做到编译 + 字节码 / 资源核对，未进游戏实测**（详见 `TECHNICAL_SUMMARY.md` §3.21 与「仍待办 17」）。下文旧版本说明保留为历史记录。

> **0.0.19 修补（第 58 轮）**：用户对第 57 轮交付**又提三条反馈，本轮全部处理**（**没有升版本号**，仍是 `0.0.19`，只改代码与 lang）。① **提示文案精简到一行**（用户原话："不需要更多的物品介绍和放置/交互后的提示"）：删掉刷怪蛋**放置时的动作栏提示**与多余的 tooltip ⇒ 两份 lang **各 43 → 34 键（−9）**，**只留** `tooltip.goetytuner.servant.spawn_egg` = "调律师仆从刷怪蛋，潜行使用会生成你自己的仆从."；顺带删掉 `TunerServantInteractions` 里那两条"只给提示、不吞掉"的 `wild_hint` / `not_owner_hint`（**行为不变，仍不吞掉**，只是不再弹字）；**诊断信息一律改走日志**（`FocusPoolManager.setFocusDisabled` / `setFocusPriority` 每次状态变化打一条 INFO `[Tuner] Servant focus <id> -> DISABLED / not disabled`）。② **"不释放的调整功能似乎完全不起效了"** —— 两条可能的病根**都堵掉**：**（a）野生仆从被拒** 第 57 轮加的 `isOwner` 只认主人，而刷怪蛋**默认直接放 = 野生** ⇒ **每条聚晶指令都在 `canCommand` 被拒绝**（这正是"完全不起效"最可能的真凶）；现在 `owner == null`（野生）**也放行**，**只有别人家的仆从**才拒绝，且拒绝理由改打 **INFO 日志**；**（b）潜行劫持左键** 第 57 轮自己加的"潜行 + 左键 = 清空全部指令"（`clearFocusCommands`）会在**潜行时抢走左键**，按左键变成"清空"而不是"切换不释放" ⇒ 该功能与 `clearFocusCommands` / `disabledCount` / `priorityCount` **一并删除**，左键语义**只剩**"切换不释放"。③ **箭雨聚晶"几乎没有持续"** —— 用户怀疑"是否能完美识别持续类"，**核对结论：识别没问题**（`ArrowRainSpell extends EverChargeSpell`，本就在 `IChargingSpell` 闭包内），真因是**通道预算把蓄力时间算进了总时长**：`ArrowRainSpell.castUp` 的解算默认 **20 tick**（`ArrowRainChargeUp` 的默认值），正好吃光 `channelMaxTicks` 的 20 ⇒ **只放一发就收手**。改成**两段预算** —— `蓄力 = min(castUp × 倍率, casting.maxCastWindowTicks)`、`持续 = max(5, casting.channelMaxTicks)`，`channelEndTick = 蓄力 + 持续`；顺带**删掉"用 `shotsNumber` 提前收手"**（玩家路径 `DarkWand` 只用 `shotsNumber` 记发数与计算释放冷却，**从不据此停火**，据此收手是不忠实的模拟）。结果：**腐化光束共 20 tick**（`castUp` 极小、几乎全给了持续）、**箭雨共 40 tick**（20 蓄力 + 20 持续）。⚠️ **本轮同样只做到编译 + 字节码核对，未进游戏实测**。

> **0.0.19 第一轮（第 57 轮）**：用户对 0.0.18 交付的**五条反馈全部处理**：① **聚晶指令语义重做**（「不释放」与「优先」改为**互斥**，任何一键都能把聚晶切回中立 ⇒ 修掉"左键不释放似乎不能取消"的设计漏洞；另加**潜行 + 左键 = 清空全部指令**（⚠️ **第 58 轮已删除**，见上方）；② **仆从血量 / 护甲 / 减伤限伤与本体一致**（读 `boss.maxHealth`=216 / `boss.equivalentArmor`=16，抽出 `combat/TunerDamageRules` + `combat/DamageThrottle` **共用实现**；`servant.health` 已删除）；③ **仆从刷怪蛋改用 Goety 仆从蛋范式**（**直接放 = 野生 / 潜行放 = 认主** + 文字提示；"第一次下指令认主"**已彻底删除**）；④ **修「长按持续释放类聚晶只放一瞬间就停」**（两层根因：只结算一次 + `AbstractBeam` 靠 `isUsingItem` 判定存活；`CastChannel` 新增 `tickChannel` + 自备 `focus/TunerWand`；用时上限 = 新增 `casting.channelMaxTicks` 默认 20）；⑤ **聚晶图标改灰黑 + 白**。⚠️ 本轮**未进游戏实测**。下文旧版本说明保留为历史记录。

> **0.0.19 发布（第 57 轮）**：**用户对 0.0.18 交付提了五条反馈，本轮全部处理**。**规模**：新增 **4 个 Java 类**（`.java` **51 → 55**，源码 **511,948 B**），改 **6 个既有 Java 文件**（`entity/ai/CastChannel`、`init/ModItems`、`entity/TunerBoss`、`entity/TunerServant`、`entity/TunerServantInteractions`、`config/TunerCommonConfig`）+ **2 个 lang**（各 **37 → 43 键**；⚠️ 是 **+7 新增 / −1 删除** —— `info.goetytuner.servant.adopted` 随"第一次下指令认主"一并删除）+ **1 个模型 json** + 版本号；**配置项数不变仍 71 项 / 11 段**（新增 `casting.channelMaxTicks` 1 项、**删除**死配置 `servant.health` 1 项）；jar 条目 **112 → 117**（新增 5 = 4 个新 class + `tuner_wand.json`，**无删除**）。新增 4 个类：`combat/DamageThrottle`、`combat/TunerDamageRules`、`focus/TunerWand`、`init/TunerServantSpawnEggItem`。
> **① 聚晶指令「左键『不释放』似乎不能取消」—— 语义重做**：0.0.18 用**两张互不相干**的表存「不释放」与「优先」，同一个聚晶可**同时命中两者**（"不释放"胜出）⇒「先设优先 → 再设不释放 → 又按右键想取消」会**按了没反应**，这就是"不能取消"的来源。现在两状态**互斥**：**左键切换"不释放"并顺手清掉"优先"；右键切换"优先"并顺手清掉"不释放"**，任何一键都能把聚晶**切回中立**、语义单一可逆（`TunerServant#toggleFocusDisabled` / `#toggleFocusPriority`）；另加**潜行 + 左键 = 清空该仆从全部聚晶指令**（`clearFocusCommands`）作为逃生通道；提示文案改为**列出两张表条目数**（新增 `disabledCount()` / `priorityCount()`）⇒ 设置是否生效一眼可验。⚠️ **如实说明**：本轮**未进游戏实测**，"原来为什么不能取消"只剩**语义解释、没有复现证据**。
> **② 仆从血量 / 护甲 / 减伤限伤与本体一致**：血量与护甲直接读 **Boss 的配置键** `boss.maxHealth`(216) / `boss.equivalentArmor`(16)，`KNOCKBACK_RESISTANCE` 对齐 1.0，`createAttributes()` 占位常量同步；**`servant.health` 已删除**（死配置；属**删键** ⇒ 老 toml 成孤儿条目、Forge 自行处理、**无需迁移**）。减伤限伤抽出**共用实现**：**`combat/TunerDamageRules`**（`isIdentityImmune` 摔落/火焰全系/窒息/溺水、`isDirectMelee`、`applyMeleeVulnerability` 近战易伤 ×(1+`boss.meleeVulnerability`)）+ **`combat/DamageThrottle`**（限伤 = 单次 ≤ 最大生命 × `boss.maxHitDamagePercent`；限DPS = 滑动 1 秒预算、返回 −1 表示整段吸收）—— `TunerBoss` 改为调用它们（**行为不变，只是搬家**），`TunerServant` 新增 `hurt` / `actuallyHurt` 覆写走同一套。⚠️ **刻意不含锁血阶梯**（`lockMark` / 宽限期免疫 / 致死截断 / `/kill` 与索命后门）：那是 Boss 的招牌机制，仆从若也锁血就成了打不死的怪；仆从也**没有后门豁免**（`/kill` 本来就该正常生效）。
> **③ 刷怪蛋范式**：新增 `init/TunerServantSpawnEggItem`，**直接继承 Goety 的 `ServantSpawnEggItem`**（本体所有仆从蛋都用它）⇒ **直接放 = 野生、潜行放 = 认主**（`if (owned && !hostile && player.isCrouching()) owned.setTrueOwner(player);`；不潜行则由 `Summoned.finalizeSpawn` `setWandering(true)`）；本类只外加 ① 放置时的**动作栏提示**（认主 / 野生两文案）② tooltip 一行本模组专属说明。**归属不再因交互而改变**：0.0.18 的 `adoptOwnerIfUnowned`（第一次下聚晶指令时认主）**已彻底删除**，归属**只在刷怪蛋放置时**确定；`TunerServantInteractions#isOwner` 只认主人，**野生仆从**与**别人家的仆从**被手持聚晶左/右键时**只给提示、不吞掉**这次攻击/交互（`wild_hint` / `not_owner_hint`）。
> **④ 修「长按持续释放类聚晶只放一瞬间就停」（本轮最有价值的一处修复）**：现象 —— 腐化 / 震撼 / 炼狱这类聚晶在调律师（及仆从）身上只放一瞬间就停。**两层根因（均已反编译实证）**：**（a）只结算一次** —— 玩家路径 `DarkWand.onUseTick` 对 `IChargingSpell` 是"蓄力到 `castUp` 后每 `Cooldown` tick 调一次 `MagicResults`（→ `SpellResult`）直到松手"，而 `CastChannel` 原先只在前摇结束时调**一次**（轰炸/雷电/暴雪这类"每发生成一个实体"的法术只出一发）；**（b）实体下一 tick 就自毁** —— `AbstractBeam.tick()`（腐化光束基类）有 `if (itemBase && !MobUtil.isSpellCasting(owner)) discard();`，而 `MobUtil.isSpellCasting` = `isUsingItem() && getUseItem().getItem() instanceof IWand && !WandUtil.findFocus(e).isEmpty()`，**Mob 从不 `startUsingItem`** ⇒ 光束刚生成就被丢弃。**修法**：`CastChannel` 新增通道型路径 **`tickChannel`** —— ① `startUsingItem(MAIN_HAND)` 让施法者**真的在"使用法杖"**（每 tick 自愈式重设；`finishCast` / `interrupt` / `startSpell` 异常 / 外层兜底四处一律 `stopUsingItem`）；② 按法术自己的 `Cooldown` / `shotsNumber` **反复释放**；③ 判定依据是 Goety 自己的 `IChargingSpell`（腐化/震撼/暴雪/轰炸/旋风/箭雨/电击/水流/蒸汽/念力/吸取/掘地/进食/飞行/防护/流星雨…全是它的子类），**不写死任何聚晶 id** ⇒ 附属同类法术自动受益。**⚠️ 为什么必须自备 `focus/TunerWand`**：让 Mob"使用" `goety:dark_wand` 会让 `DarkWand.onUseTick` 每 tick 走 `MagicResults`，而它对**非玩家施法者**走 `failParticles + FIRE_EXTINGUISH` 分支 —— **不放法术、只冒白烟响灭火音**。`TunerWand implements IWand`：`onUseTick` **空实现**、`getUseDuration` = 72000、显式委托 `IWand.super.initCapabilities`（`SoulUsingItemHandler` 依赖它）、`getSpellType()` = NONE；模型 `models/item/tuner_wand.json` 内容只有 `{"parent": "goety:item/dark_wand"}` ⇒ **外观完全一致**。Boss 与仆从主手都换成 `goetytuner:tuner_wand`，并在 `readAdditionalSaveData` 做**旧档迁移**。**顺带落地了遗留待办「C Boss专属魔杖」**。
> **用时上限（用户点名要求）**：新增 **`casting.channelMaxTicks`**（Int，**默认 20 = 1 秒**，范围 5~200）；三条**独立**收口、谁先到算谁：① 总时长到上限；② 法术自己的 `shotsNumber`（>0 时）放完；③ 防御性硬上限 `MAX_CHANNEL_SHOTS = 400`。到点必 `finishCast()`（内含 `stopUsingItem`）。**⚠️ 顺带澄清用户疑问**（"我记得已经给过一种时间限额，是不是应用于这一部分"）：**旧键 `casting.maxCastWindowTicks`（默认 50）一直在生效，但它管的是"把前摇截断到 2.5 秒"、并不管持续释放**；长按类法术的 `defaultCastDuration()` 是 **72000**、被它截成 50 ⇒ **旧行为就是"站桩 2.5 秒 → 放一发 → 结束"，这正是本 bug 的一部分**。现在两键分工：`maxCastWindowTicks` = **普通法术**蓄力截断；`channelMaxTicks` = **长按类**持续上限。普通法术（非 `IChargingSpell`）**行为完全不变**。**⚠️ 平衡风险**：腐化光束**每 tick 造成伤害**（Goety 默认 10.0/次，且清零目标 `invulnerableTime` 绕过无敌帧），上限 20 已能打出很高总伤害、**调到 100 以上基本等于必杀**；觉得太强就调小或拉黑该聚晶。
> **⑤ 聚晶图标改灰黑 + 白**：改了 `art/gen_ripple_focus_icon.py` 的配色 —— 盘面近黑轮廓 `(8,8,8)` + 深灰→近黑渐变 `(58→22)`，亮环 `(240,240,240)`、圆心纯白 `(255,255,255)`，青色圆心渐变去掉（灰黑配色下不再引入第三色）。重新生成 **694 → 627 B**，仍 16×16 RGBA、脚本**字节可复现**。
> ⚠️ **验证做到什么程度（本轮没有进游戏实测）**：`gradlew build` **成功**（**约 1 分钟**，只有既有基准噪声：3 条 FML deprecated 警告 + 1 条 `TunerBoss` 过时 API 注记，**无新增警告**）；jar 条目 **112 → 117**（新增 5、无删除）；**原始字节串搜索**核对 `CastChannel`（`tickChannel`/`channeled`/`channelEndTick`/`IChargingSpell`）、`TunerWand`（`initCapabilities`）、`TunerServant`（`toggleFocusDisabled`/`toggleFocusPriority`/`clearFocusCommands`/`disabledCount`/`priorityCount`/`TUNER_WAND`/`DamageThrottle`/`TunerDamageRules`）、`TunerServantSpawnEggItem`（`ServantSpawnEggItem`/`spawn.tamed`/`spawn.wild`）、`TunerServantInteractions`（`isOwner`/`wild_hint`/`not_owner_hint`）、`ModItems`（`tuner_wand`）；**`javap`** 核对被 reobf 成 SRG 名的覆写/调用 —— `TunerWand` 方法表 `m_5929_`(=onUseTick) / `m_8105_`(=getUseDuration) / `m_7203_`(=use) / `initCapabilities`，`TunerServant` 的 `m_6469_`(=hurt) / `m_6475_`(=actuallyHurt) / `m_7301_`(=canBeAffected)，`CastChannel.tickChannel` 字节码里确有 `m_6117_()`(=isUsingItem) 与 `m_6672_(InteractionHand)`(=startUsingItem)、`stopChannelUse` 里确有 `m_5810_()`(=stopUsingItem)，并正确 `invokeinterface IChargingSpell.castUp/Cooldown/shotsNumber`；配置 / 资源核对 `define` 调用 **71** 处、section **11** 个、`channelMaxTicks` 存在、`servant.health` 已移除、两份 lang 各 **43 键**且 JSON 合法、jar 内 `mods.toml` 版本 **0.0.19**、png 魔数正确；部署核对**构建产物与部署 jar 逐字节相同**（1,787,227 B / 同一个 md5）。⚠️ **五条修复的运行时表现全部未实测**（详见「仍待办 15」）。部署：`goetytuner-0.0.19.jar`（**1,787,227 B / md5 `9363476AD6F24B6296DF59303BE0DBB7` / jar 内 117 条目**）→ `versions\测试\mods\`（**旧 0.0.17 / 0.0.18 都已删**，该目录只剩这一个）。✅ **已提交并 push**（0.0.18 + 0.0.19 + 0.0.20 在第 59 轮末一并入库）。详见 `TECHNICAL_SUMMARY.md` **§3.19**。

> **0.0.18 发布（第 56 轮）**：本轮两件新东西 —— **调律波纹聚晶**（玩家可用）＋ **调律师仆从**（含「聚晶指令」）。**规模**：新增 **6 个 Java 类**（`.java` **45 → 51**，源码 **475,860 B**）、新增资源 **3 个**（两个物品模型 json + `textures/item/tuner_ripple_focus.png`，16×16 RGBA / **694 B**，程序化生成）、两份 lang **27 → 37 键（+10）**（⚠️ 口径：「29」是**旧文件行数**、不是键数；行数 29 → 39）、jar 条目 **102 → 112**（新增 10、**无删除**）；**配置 67 → 71 项 / section 10 → 11**（新增 **`[servant]`** 段 4 项：`health`=40、`followRange`=32、`castIntervalTicks`=40、`rotation`="23"），并**改了已有键** `focus.blacklist` 的**默认值**（`goetytwilight:destruction_focus` → `goetytwilight:destruction_focus, goetytuner:tuner_ripple_focus` —— **是改值、不是加键**）；网络协议 **`2.0` → `2.1`**（严格匹配不变）。**用户需求两条**：① **调律波纹聚晶** —— 与调律师「重音涟漪」**同款效果**的聚晶，灵魂能量消耗 **5**、蓄力 **0.2 秒**、冷却 **0.5 秒**，释放时放一次涟漪并产生击退，效果相当于**铺垫期**的涟漪；用户特别提醒模组存在「初始化最后读取 MC 内所有聚晶」的机制，要考虑该聚晶的**声明次序**以保证能被读取而不出 bug，并要求**把该聚晶写入黑名单**。② **调律师仆从** —— 参考诡厄巫法的「生物 ↔ 仆从」对应关系，做调律师对应的仆从版本，要有**一般仆从的性质**；特别地，**玩家手持聚晶左键 / 右键它**时，将之设置为**不释放 / 优先释放**该聚晶。向用户确认的两个范围问题：仆从**只用刷怪蛋 / 指令召唤**（**不做 summon 聚晶**）、仆从**复用调律师的聚晶池 + 轮盘赌抽签**。**关键设计**：**`combat/AccentRipple` 是「重音涟漪」的唯一实现**（击退 / 粒子 / 声波 / 提示音四件事，Boss 的 `accentKnockbackPulse` 改成**四行转发** ⇒ **Boss 与聚晶共用同一份代码**、而不是抄一份；差别只有「力量与音调」与「聚晶额外豁免自己人」两处，由调用方给）；**`SAccentWavePacket` 载荷由「实体 id」改为「三个 double 的世界坐标」**（通道 id 仍 **3**；**玩家放的涟漪锚点是一个裸坐标、没有实体可查** ⇒ `AccentWaveRenderer.trigger(int)` 改成 `trigger(double x, double y, double z)`），广播方式改「锚点 64 格内按玩家」；**聚晶注册次序**三条约束写在 `ModItems` 类 javadoc（同一 `ITEMS` DeferredRegister / `getSpell()` 立刻返回非 null 单例 / **必须同时拉黑**）；**仆从 `TunerServant extends Goety ...ally.Summoned`** ⇒ 一般仆从性质全继承，复用同一 `FocusPoolManager`（**每仆从一份实例**）与 `CastChannel`，**没有**音乐阶段、锁血、瞬移、嘲讽、二阶段；**聚晶指令**（`TunerServantInteractions`）左键 = 不释放 / 右键 = 优先释放，**客户端一律放行、只在服务端取消**（`javap` 实证 `Player.attack` **偏移 0** 就是 `ForgeHooks.onPlayerAttackTarget`，客户端取消会让攻击包**根本不发**）；优先聚晶走 `CastChannel.beginCast(level, entry, mult)` **插队**；**无主仆从第一次被下达指令时认主**。⚠️ **配置迁移必须做**（红线 8）：`focus.blacklist` 是**改已有键的值**、Forge **不会**回填老 toml ⇒ 已用 `scripts/add_ripple_focus_blacklist.py` 对游玩实例 toml 迁移完毕（备份 `...bak-before-blacklist-ripple`）；`[servant]` 段是**新增键** ⇒ 自动补齐、无需手改。⚠️ **本轮未进游戏实测**：只做到 `gradlew build` 成功（**1m19s**，只有项目既有基准噪声、**无新增警告**）+ jar 条目核对（102 → 112）+ **原始字节串**核对 + `javap` 核对**被 reobf 成 SRG 的覆写**与两个交互钩子的位置 + 配置 / 资源核对。部署 `goetytuner-0.0.18.jar`（**1,778,838 B / md5 `5946D71FA425B8F84E2F7E1CF4B5A8E3` / jar 内 112 条目**）→ `versions\测试\mods\`（旧 0.0.17 已删）；✅ 已在第 59 轮末提交并 push。详见下文「第 56 轮（0.0.18）」，技术现状见 `TECHNICAL_SUMMARY.md` §3.18。

> **0.0.17 发布（第 55 轮）**：改 **2 个 Java 文件**（`entity/TunerBoss`、`config/TunerCommonConfig`）+ 版本号，**只改代码、不动任何贴图/资源**（`.java` 仍 **45** 个、jar 条目仍 **102**，无新增类/贴图）；**新增 1 个配置键** ⇒ 配置 **66 → 67 项**（`boss` 段 **19 → 20**，其余段未变，section 数仍 **10**）。**用户反馈**：玩家用**索命聚晶**打中调律师时，Boss 会进入"动画已经死了、血量却回弹"的破状态（索命造成的是"等同于目标当前生命值"的致死伤害，被锁血体系拦住后复活，客户端动画却留在原地）。用户说「如果可以的话给索命的成功伤害直接开个后门；如果麻烦的话修一下动画问题」——**两个都做了**。① **索命聚晶后门**（`entity/TunerBoss` + `config/TunerCommonConfig`）：**先做实证再动手** —— 反编译 Goety 的 `KillingSpell.SpellResult` 得调用链 `ModDamageSource.deathCurse(target)` → `MobUtil.hurtCalculation(...)` → `target.hurt(source, amount)`（**走 `hurt()`，我们的覆写能看到**）；`ModDamageSource.DEATH` 的键名在静态初始化里是 `create("death")`，jar 内亦有 `data/goety/damage_type/death.json`（`message_id` = `goety.death`）⇒ 伤害类型是 **`goety:death`**；**全 jar 扫描 `deathCurse` 的引用只有 `KillingSpell` 一处**（外加其在 `ModDamageSource` 的定义）⇒ 用该伤害类型匹配**恰好等价于"索命聚晶"**，不会误伤别的法术。**实现**：新增字段 `deathCursePending` + 常量 `GOETY_DEATH_CURSE`（`ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("goety","death"))` —— **不需要编译期依赖 Goety 的类**，未装 Goety 时该键永不匹配）；`hurt()` 识别后置标记 + 打 INFO + `return super.hurt(...)`（跳过身份免疫 / 宽限期免疫 / 致死截断）；`maintainDeathState()` 见标记直接 `return`（不回弹）；`canStillRevive()` 返回 false（不拦 `remove(KILLED)`）⇒ **索命能真正处决，无视剩余锁血档位**。**关键坑**：`actuallyHurt()` 的限伤 / 限DPS 也必须为它开口子 —— `goety:death` **不在任何 `BYPASSES_*` tag 里**，不显式列出的话"等同于目标当前生命值"的致死伤害会被限伤削掉、后门等于失效。状态正常时清除标记；新增开关 **`boss.deathCurseExecution`**（默认 **true**，false = 关闭后门回到旧行为）。**代价**：索命会对施法者反噬"目标当前生命值 125%"，所以这是**有代价的处决手段**。② **修「动画死了血量回弹」的根因（客户端残留）**：`deathTime` **不是同步数据**（0.0.8 已实证）—— 客户端的 `deathTime` 由客户端自己的 `LivingEntity.tickDeath()` 递增，而它只看**客户端本地血量**；服务端复活时只发**一次** `SEntityRevivePacket`，若那一刻客户端的血量同步还没落地，客户端会在复位后**又自己把 deathTime 加上去**，此后血量虽变正、动画再没人清 ⇒ **永久躺着**；服务端侧的"情形 A"治不了这一侧。修法：在 `tick()` 的**客户端分支**加一条**对称自愈** —— 客户端同样知道血量（`DATA_HEALTH_ID` 是同步数据），`deathTime > 0 && getHealth() > 0` 即视为脏状态、直接 `resetDeathAnimation(...)`（该方法只在服务端发包，客户端调用不产生网络流量）⇒ **至多残留 1~2 tick**。⚠️ **尚未进游戏实测**（本轮只做到编译通过 + jar 条目核对）。部署：`goetytuner-0.0.17.jar`（**1,757,576 B / md5 `B68C4D4FF9FC650796EDB003D2C53009` / jar 内 102 条目**）→ `versions\测试\mods\`（旧 0.0.16 已删）。详见 `TECHNICAL_SUMMARY.md` §3.17。
>
> **上一轮（0.0.16，第 54 轮）发布**：「逐渐学习」——给**动态评分**（实战学到的偏移）加一个随**该调律师个体**施法次数爬升的权重系数，开局用低权重让**初始评分**（配置 / LLM 分类）主导，随实战逐次把话语权交棒给**动态反馈**。改 **3 个 Java 文件**（`focus/FocusEntry`、`focus/FocusPoolManager`、`entity/TunerBoss`）+ 版本号，**只改代码、不动任何贴图/资源**（`.java` 仍 **45** 个、jar 条目仍 **102**，无新增/删除类与资源）；**新增 3 个配置键** ⇒ 配置 **63 → 66 项**（`scoring` 段 **3 → 6**，其余段**未变**，section 数仍 **10**）。① **`FocusEntry.rouletteWeight(...)` 新增第 4 参数 `dynamicScale`**：权重由 `|静态评分 + 动态偏移| + 保底基数` 改为 `|静态评分 + 动态偏移 × dynamicScale| + 保底基数` —— **只缩动态部分，静态评分（初始分类）完全不受影响**；攻击类与召唤类两条公式都改（召唤类是生存分 / 输出分**两处**偏移各乘一次）。② **`FocusPoolManager` 新增每实例学习状态**：`castCount`（该个体施法次数；存在**实例字段而非 static** —— `createFightPools()` 每只 Boss 各建一份、`FocusEntry` 也是实例级复制 ⇒ 学习进度**天然是个体私有**的，与动态偏移生命周期一致）、`noteCast()`（由 `TunerBoss.onCastStart` 每次成功施法调用，按 `w = start + (max − start) × min(1, castCount / ramp)` **线性**重算，**每跨 10% 里程碑打一条 INFO** `[Tuner] Learning weight ...`）、`learningWeight()` / `castCount()` getter；`draw()` 把系数作为**第 4 实参**喂给 `rouletteWeight`（**已用 `javap` 验证调用链**：偏移 178 `learningWeight()` → 局部变量 14 → 偏移 234 `dload 14` → 偏移 236 `invokevirtual rouletteWeight:(D[DZD)D`）。**`drawUniform()`（防御 / 其他类）本来就不走评分，不受影响**。③ `entity/TunerBoss.onCastStart` 里调 `pools.noteCast()`（**只统计"用聚晶施法"的前摇起手**；瞬发 / 重音不经过该回调，故**不计入**）。④ **新增 3 个配置键**（`scoring` 段）：`learningWeightStart`（默认 **0.15**，0~1）、`learningWeightMax`（默认 **1.0**，0~2）、`learningWeightRampCasts`（默认 **60**，1~1000）。**默认曲线**：施法 0 次→**0.150**、10→0.292、20→0.433、30→0.575、40→0.717、50→0.858、60→**1.000**（之后封顶）⇒ 开局由**初始分类**主导、随实战逐次交棒给**动态反馈**。⚠️ **`FocusEntry.getEffectiveAttackScore()` / `getEffectiveSurvivalScore()` 现为"未缩权的"视角**（原先正是轮盘权重的输入），**保留作诊断用、不再被抽取路径调用**（**未删除**）。⚠️ **尚未进游戏实测手感**。部署：`goetytuner-0.0.16.jar`（**1,756,720 B / md5 `816C6640E9486FFAFE3B88FA5B2C6D0E` / jar 内 102 条目**）→ `versions\测试\mods\`（旧 0.0.15 已删）。详见 `TECHNICAL_SUMMARY.md` §3.16。
>
> **更早（0.0.15，第 53 轮）发布**：**只有贴图精修 + 版本号 + 文档，零 Java 改动、无新增/删除资源、配置项数不变（仍 63 项 / 10 段）**。精修 `src/main/resources/assets/goetytuner/textures/entity/tuner.png`（64×64 RGBA，2520 → **2676 B**）：保留紫黑礼服 / 紫色袖口裤靴 / 浅色领巾 / 青色胸饰，精修**翻领边缘、衣袖明暗、袖口细边、裤缝、靴口层次**；原稿由 imagegen 生成，再**按最近邻采样重新装配回原有 UV**（生成图未严格保持矩形位置，故重新测量图块）。**已独立核验（以 0.0.14 为基准逐像素差分，不依赖其自带脚本）**：头部区 **y0..15 全宽 64 列 0 个像素差异**、全图 **alpha 蒙版完全一致**（未增删不透明像素）、身体区改动 **1247 像素**、6 个主要 UV 面无透明空洞。制作过程入库：`art/REFINEMENT.md`（报告 + 最终提示词）、`art/assemble-refinement.ps1`（可复现装配）、`art/body-refined-source.png`（原稿）、`art/tuner-before-refinement.png`（精修前备份）、更新后的 `art/preview.png`。⚠️ **未做游戏内画面验收**——观感仍待人类 / 能读图的模型确认；`art/preview.png` 是**平面正背面拼接图，不是游戏截图**。部署：`goetytuner-0.0.15.jar`（**1,755,456 B / md5 `1865ECF4EB22989319FD746806320776` / jar 内 102 条目**）→ `versions\测试\mods\`（旧 0.0.14 已删）。
>
> **更早（0.0.14，第 52 轮）发布**：该轮两件事，改 **4 个 Java 文件**（`client/TunerConfigScreen`、`focus/FocusPoolManager`、`entity/TunerBoss`、`config/TunerCommonConfig`）+ **2 个 lang** + 版本号，**只改代码、不动任何贴图/资源**（`.java` 文件数仍 45、jar 条目仍 102）；**新增 1 个配置项** `phase2_buffs.phase2EffectImmunity`（Boolean，默认 **true**）⇒ 配置 **62 → 63 项**（`phase2_buffs` 段 **3 → 4**，`boss` 段仍 **19**，section 仍 10 个）。① **配置界面新增「聚晶黑名单」输入框**（`client/TunerConfigScreen` + `focus/FocusPoolManager`）：`focus.blacklist` 是 common 配置，而本模组的 `TunerConfigScreen` **顶替了 Forge 默认的 toml 编辑器**，该键此前在游戏内**完全没有入口**、只能手改文件；现补一个单行 `EditBox`（预填当前值，提示「namespace:path，英文逗号分隔；留空=不屏蔽」），**点「完成」关屏时保存**（最自然的"改完了"信号），点「开始评分」时也**顺带保存**；保存动作 = `FOCUS_BLACKLIST.set(v)` + `.save()` + `FocusPoolManager.refreshBlacklist()` ⇒ **改完立即生效**（`getBlacklist()` 以 raw 字符串为缓存键，值一变缓存自动失效 ⇒ 下一次抽取就过滤）。**新增 `FocusPoolManager.refreshBlacklist()`**：`initIfNeeded()` 扫描时是**直接 `continue` 跳过**黑名单聚晶（它们不进 `ALL_ENTRIES`），所以"**新增**拉黑"能靠 `draw()/drawUniform()` 的实时过滤立刻生效，但"**取消**拉黑"必须重扫才会回来；而重扫若 `new` 出全新 `FocusEntry`，会让实体侧那些**按对象身份**记录的状态失效（`TunerBoss.activeVisualCasts` 身份集合、`CastChannel.current`）⇒ 出现「立方体高亮卡住 / 施法收尾回调对不上」这类隐蔽问题。故新方法按 `namespace:path` 建索引**复用已有 `FocusEntry` 对象**，只为"这次才被解禁"的聚晶新建（它们此前不可能在施法中，故安全），然后重建 `STATIC_POOLS` 并重新 `applyTo` 分类。新增 4 个 lang 键（zh_cn / en_us 各 4 个）。限制：连他人的服务器时改的是**本地**那份 common toml，服务器侧不受影响（单机/局域网主机同进程，正常生效）。② **二阶段免疫「回复 / 减伤」类药水效果**（`entity/TunerBoss` + `config/TunerCommonConfig`）：扩展现有的 `canBeAffected` 覆写（它同时是 `addEffect` 与 `forceAddEffect` 的**第一道**判定，在这里返回 false 就是真正的免疫，而不是"加完再清"）；免疫名单 = **抗性提升** `DAMAGE_RESISTANCE` / **伤害吸收** `ABSORPTION` / **生命恢复** `REGENERATION` / **瞬间治疗** `HEAL` / **生命提升** `HEALTH_BOOST`，**仅当 `music.isPhase2()`** 时生效。**刻意只列这 5 项而不是"所有 beneficial"**：Boss 自己的二阶段增益（力量 `DAMAGE_BOOST` 与重振 `RALLYING`）也是 beneficial，一刀切会把它们一起禁掉；名单集中在私有 `isPhase2Immune(...)`，以后要加（例如某个附属的自定义减伤）加一行即可。新增配置 `phase2_buffs.phase2EffectImmunity`（Boolean，**默认 true**），false = 回到旧行为；不影响玩家的同类效果，也不影响 Boss 自己的二阶段自施。详见 `TECHNICAL_SUMMARY.md` §3.15。
>
> **更早（0.0.13，第 51 轮）修复**：该轮改了 **7 个 Java 文件** + 版本号，**只改代码、不动任何贴图/资源**（`.java` 文件数仍 45、jar 条目仍 102）；**新增 1 个配置项** `music.accentDensityDivisor` ⇒ 配置 **61 → 62 项**（`music` 段 12 → 13，section 仍 10 个）。① **修「玩家被击杀复活后（未走出索敌范围）背景音乐丢失」**（`BossMusicManager`）：原先只以静态字段 `music != null` 当作「在播」、**从不与声音引擎核对**，而死亡/复活会让引擎把循环实例悄悄摘除（RECORDS 音量为 0 / channel 被停止 / `SoundEngine.reload()→destroy()→stopAll()` / `play()` 在未 loaded 时静默返回），字段却仍非 null ⇒ 只要服务端 `playing` 一直为 true（没脱战）`startMusic` **永不重入** = **永久静音**，且 `onPlaySound` 同样只看字段 ⇒ **连原版背景音乐也一起被永久取消**（症状「整个 BGM 都没了」）；改为每 tick 用 **`SoundManager.isActive(music)`** 核实 + **10 tick 防抖**，失效即清空字段、下一 tick 自愈重建。② **重音标记滚动平滑 + 高潮改细小长条**（`MusicBarHud`）：`fill()` 只能落在整数像素、刻度只有 1~2px ⇒ 取整后逐像素跳动；改为**亚像素覆盖**（小数部分按比例摊到相邻两列，亮度重心连续移动）；高潮「中」字改为**小长条**（高潮 2×6 / 低谷 1×6 / 铺垫 1×4，竖向居中）。③ **涟漪多波共存**（`AccentWaveRenderer` 的 `Map` 改 `List` + `MAX_WAVES = 32`）：二阶段进场重音每 5 tick 一发，原先后一发**顶掉**前一发、只看到一条波反复重播。④ **重音数量与频率降为 1/3**：新增 `music.accentDensityDivisor`（默认 **3**、范围 1~9），在**服务端加载乐谱时**「每 N 个保留 1 个」⇒ HUD 刻度 / 击退 / 涟漪 / 提示音**一起**变稀疏、客户端零改动（默认乐谱 37 → **13** 个重音）。⑤ **立方体高亮更明显 + 发光**（`TunerOrbLayer`）：高亮改为额外叠加 `glow*0.4` 逐通道**向白靠拢**（原先 `min(1, color*(1+glow*0.8))` 被 `min` 截断、几乎看不出变化），发光用**两层 `entityTranslucentEmissive` 自发光外壳**——**不能用原版发光描边**（MC 的发光是**整实体级** framebuffer 后处理 `OutlineBufferSource`，只能整只 Boss 一起描边，无法只描一颗立方体）。⑥ **药水效果可观测性**：8 处 `addEffect` 的 boolean 返回值原先全被丢弃 ⇒ 被 `canBeAffected` / `MobEffectEvent.Applicable` 拒绝时静默失效；新增 `applySelfEffect` 打 WARN（用于 boss 自身 4 处），并完成药水现状审计。⑦ 配置侧把 **`goety:killing_focus`（索命聚晶，对施法者反噬 125%）** 加入 `focus.blacklist`——**配置侧改动、不在 jar 内**；且 `focus.blacklist`（乃至整个 toml）**无法在游戏内配置界面修改**。0.0.10 美术项与 0.0.12 新外观仍待游戏画面验收，见 [ART_ASSETS_REPORT.md](ART_ASSETS_REPORT.md)。

> MC 1.20.1 Forge 47.3.22 · 附属模组（依赖 Goety 2.5.56.5）· 当前版本 0.0.19
> 更新日期：2026-09-20
> 作者：toniat0 & vibe-coding · 团队：Goety Tuner Project · <https://github.com/QieFanQie/>
> 许可证：MIT License
>
> 本文档是**阶段性计划书**（含历史实测记录，进度类内容随轮次回填）；
> 项目技术现状以 `TECHNICAL_SUMMARY.md` 为准（该文档已更新到第 59 轮 / **0.0.20**，见其 **§3.19** / **§3.20** / **§3.21**），
> 单任务设计稿见 `DESIGN_RITUAL_WAND_UPGRADE.md`。

---

## 一、项目概述

**调律师**：人形无头指挥家Boss，头部位置只有一枚飘动的黑色立方。它以"演奏"的方式轮番使用诡厄巫法及其附属注册的**所有聚晶（Focus）**，战斗由三段式音乐（铺垫/高潮/低谷）驱动。

当前状态（**0.0.20** / 第 59 轮）：**程序框架完整可运行**，核心战斗逻辑（聚晶池/评分/音乐同步/锁血/阶段切换/效果清除）、音乐资源接入（第 20~21 轮）、仪式召唤与法杖升级（第 36 轮）、游戏内配置与 LLM 评分界面（第 41 轮）均已落地并通过实测；**第 57 轮（0.0.19）成果：用户对 0.0.18 交付的五条反馈全部处理** —— ① **聚晶指令语义重做**（修"左键『不释放』似乎不能取消"这个**设计漏洞**）：0.0.18 的「不释放」与「优先」是**两张互不相干的表**、同一个聚晶可同时命中两者（"不释放"胜出）⇒「先设优先 → 再设不释放 → 又按右键想取消」会**按了没反应**。现在两状态**互斥**：**左键切换"不释放"并顺手清掉"优先"、右键切换"优先"并顺手清掉"不释放"**，任何一键都能把聚晶**切回中立**、语义单一可逆；另加**潜行 + 左键 = 清空该仆从全部聚晶指令**（`clearFocusCommands`）作逃生通道；提示文案改为**列出两张表条目数**（`disabledCount()` / `priorityCount()`）⇒ 一眼可验。⚠️ 本轮**未进游戏实测**，"原来为什么不能取消"只剩**语义解释、没有复现证据**。② **仆从血量 / 护甲 / 减伤限伤与本体一致**：血量护甲直接读 **Boss 的配置键** `boss.maxHealth`(216) / `boss.equivalentArmor`(16)、`KNOCKBACK_RESISTANCE` 对齐 1.0；**`servant.health` 已删除**（死配置）。减伤限伤抽出**共用实现**（本项目红线：同一职责只允许一处实现）—— 新增 **`combat/TunerDamageRules`**（身份免疫 摔落/火焰全系/窒息/溺水、`isDirectMelee`、近战易伤 ×(1+`boss.meleeVulnerability`)）与 **`combat/DamageThrottle`**（限伤 = 单次 ≤ 最大生命 × `boss.maxHitDamagePercent`；限DPS = 滑动 1 秒预算、返回 −1 表示整段吸收）；`TunerBoss` 改为调用这两者（**行为不变，只是搬家**），`TunerServant` 新增 `hurt` / `actuallyHurt` 覆写走同一套规则。⚠️ **刻意不含 Boss 的锁血阶梯**（`lockMark` / 宽限期免疫 / 致死截断 / `/kill` 与索命后门）—— 那是 Boss 的招牌机制，仆从若也锁血就成了打不死的怪；仆从也**没有后门豁免**。③ **仆从刷怪蛋改用 Goety 仆从蛋范式**：新增 `init/TunerServantSpawnEggItem`（**直接继承 Goety 的 `ServantSpawnEggItem`**）⇒ **直接放 = 野生、潜行放 = 认主**，并附动作栏提示与本模组专属 tooltip；**归属不再因交互而改变** —— 0.0.18 的"野生仆从第一次被下达聚晶指令时认主"（`adoptOwnerIfUnowned`）**已彻底删除**，归属**只在刷怪蛋放置时**确定，野生仆从 / 别人家的仆从被手持聚晶左键或右键时**只给提示、不吞掉**这次攻击 / 交互。④ **修「长按持续释放类聚晶只放一瞬间就停」（本轮最有价值的一处修复）**：**两层根因（均已反编译实证）**—— **（a）只结算一次**：玩家路径 `DarkWand.onUseTick` 对 `IChargingSpell` 是"蓄力到 `castUp` 后每 `Cooldown` tick 调一次 `MagicResults`（→ `SpellResult`）直到松手"，而 `CastChannel` 原先只在前摇结束时调**一次**（轰炸/雷电/暴雪这类"每发生成一个实体"的法术只出一发）；**（b）实体下一 tick 就自毁**：`AbstractBeam.tick()`（腐化光束基类）有 `if (itemBase && !MobUtil.isSpellCasting(owner)) discard();`，而 `MobUtil.isSpellCasting` = `isUsingItem() && 用物是 IWand && 杖里有聚晶`，**Mob 从不 `startUsingItem`** ⇒ 光束刚生成就被丢弃。**修法**：`CastChannel` 新增通道型路径 **`tickChannel`** —— ① `startUsingItem(MAIN_HAND)` 让施法者**真的在"使用法杖"**（每 tick 自愈式重设；`finishCast` / `interrupt` / `startSpell` 异常 / 外层兜底四处一律 `stopUsingItem`）；② 按法术自己的 `Cooldown` / `shotsNumber` **反复释放**；③ 判定依据是 Goety 自己的 `IChargingSpell`（腐化/震撼/暴雪/轰炸/旋风/箭雨…全是它的子类），**不写死任何聚晶 id** ⇒ 附属同类法术自动受益。**⚠️ 为此必须自备 `focus/TunerWand`**：让 Mob"使用" `goety:dark_wand` 会让 `DarkWand.onUseTick` 对**非玩家施法者**走 `MagicResults` 的 `failParticles + FIRE_EXTINGUISH` 分支 —— **不放法术、只冒白烟响灭火音**。`TunerWand implements IWand`：`onUseTick` **空实现**、`getUseDuration` = 72000、显式委托 `IWand.super.initCapabilities`（`SoulUsingItemHandler` 依赖它）；模型 `models/item/tuner_wand.json` 内容只有 `{"parent": "goety:item/dark_wand"}` ⇒ **外观完全一致**；Boss 与仆从主手都换成 `goetytuner:tuner_wand` 并在 `readAdditionalSaveData` 做**旧档迁移**（**顺带落地了遗留待办「C Boss专属魔杖」**）。**用时上限**（用户点名要求"给一个用时上限，防止它停不下来"）= 新增 **`casting.channelMaxTicks`**（默认 **20 = 1 秒**，5~200），三条独立收口谁先到算谁：总时长 / 法术自己的 `shotsNumber` / `MAX_CHANNEL_SHOTS = 400`。**⚠️ 顺带澄清用户疑问**（"我记得已经给过一种时间限额"）：**旧键 `casting.maxCastWindowTicks`（默认 50）一直在生效，但它只管"把前摇截断到 2.5 秒"、不管持续释放**；长按类法术的 `defaultCastDuration()` 是 **72000**、被它截成 50 ⇒ **旧行为就是"站桩 2.5 秒 → 放一发 → 结束"，这正是本 bug 的一部分**。现在两键分工：`maxCastWindowTicks` = 普通法术蓄力截断；`channelMaxTicks` = 长按类持续上限。**⚠️ 平衡风险**：腐化光束**每 tick 造成伤害**（Goety 默认 10 点/次，且清零目标 `invulnerableTime` 绕过无敌帧），上限 20 已能打出很高总伤害、**调到 100 以上基本等于必杀**。⑤ **聚晶图标改灰黑 + 白**（用户："原本紫色的部分变为黑色"）：配色改为近黑轮廓 `(8,8,8)` + 深灰→近黑渐变 `(58→22)` + 白环 `(240)` + 纯白圆心 `(255)`，青色渐变去掉；**694 → 627 B**，仍 16×16 RGBA、脚本**字节可复现**。**⚠️ 以上五条全部待游戏实测**（详见 §七「仍待办 15」）；**第 56 轮（0.0.18）成果：调律波纹聚晶 + 调律师仆从** —— ① **调律波纹聚晶**（新增 `focus/RippleSpell`，注册在 `init/ModItems`）：与调律师「重音涟漪」**同款效果**的**玩家可用**聚晶，灵魂 **5** / 蓄力 **0.2 s（4 tick）** / 冷却 **0.5 s（10 tick）**、`SpellType.NONE`、**不接受附魔**（⚠️ 这三个是**基础值**，Goety 仍会叠**施法者修正** —— 耗蓝乘 `SoulDiscount` / 环境加成、蓄力乘 `ModAttributes.getCastingSpeed` 与"减半施法时间"饰品、冷却乘 `ModAttributes.getCooldownDiscount`，这是所有聚晶的统一规则、**没有覆写绕过**）；效果 = **铺垫期**的涟漪 + 击退，并**尊重** `music.accentParticles` / `accentWave` / `accentSound` 三个开关（玩家关掉的观感不应在聚晶上复活）。**注册次序（用户点名要处理的点）**：三条约束写进 `ModItems` 的**类 javadoc** —— Ⅰ 必须注册在**同一个 `ITEMS` DeferredRegister**（它在 `RegisterEvent` 统一落地、**远早于** `ServerStartingEvent`，所以聚晶扫描**一定**看得到；另起一个 DeferredRegister 忘 `.register(modBus)` 就**永远看不到** —— 本项目在**刷怪蛋**上踩过这个"注册时序坑"）；Ⅱ `getSpell()` 必须**立刻**返回非 null 单例（复用 Goety `MagicFocus`，构造函数里就存字段、天然满足）；Ⅲ **必须同时拉黑**（否则 Boss 会抽到它：等于**白得一个 0.5 秒冷却的重音**，而且那发涟漪会把 **Boss 自己一起推开**）—— 黑名单默认值已写入 `goetytuner:tuner_ripple_focus`。② **重音涟漪收敛为唯一实现**（新增 `combat/AccentRipple`，147 行）：击退 / 粒子 / 声波 / 提示音**四件事**都收在一处，`TunerBoss.accentKnockbackPulse` 改成**四行转发** ⇒ Boss 与聚晶**共用同一份代码**（而不是抄一份）；差别只有两处、由调用方给：**力量与音调**（Boss 按阶段 0.4/0.8/1.2 与 0.9/1.4/0.6；聚晶固定取**铺垫期** 0.4 / 0.9）与「聚晶**额外豁免"施法者自己人"**（自己 / 自己的宠物 / 自己的 `IOwned` 仆从；Boss 无此需求故传 `null`）。③ **`SAccentWavePacket` 载荷由「实体 id」改为「三个 double 的世界坐标」**（通道 id 仍 **3**）：**玩家放的涟漪锚点是一个裸坐标、没有实体可查**（原先客户端收到包后用 `level.getEntity(id) instanceof TunerBoss` **反查位置**）⇒ `AccentWaveRenderer.trigger(int)` 改成 `trigger(double x, double y, double z)`；广播方式由 `TRACKING_ENTITY` 改为「**锚点 64 格内按玩家广播**」；协议因此 **`2.0` → `2.1`**（**同 id 不同结构会解码错位**）。④ **调律师仆从**（新增 `entity/TunerServant`，522 行）：`extends com.Polarice3.Goety.common.entities.ally.Summoned`（Goety 里**所有仆从的标准基类**）⇒ 主人归属 / 跟随 / 目标牵引 / 仆从加血与传送 / 日照规则**全部继承**，这就是「**一般仆从的性质**」；实体名 `goetytuner:tuner_servant`，`MobCategory.MONSTER` + `sized(0.6F, 1.95F)` + `clientTrackingRange(8)`（照抄 Goety 仆从写法）；**复用**同一个 `FocusPoolManager`（全模组聚晶池 + 静态评分 + 轮盘赌 + 冷却池，**每只仆从各一份实例**）、同一个 `CastChannel`（前摇 / 锁池 / 朝向钉死 / 异常自愈）、同一套模型贴图与渲染层，**没有**音乐阶段、锁血、瞬移、嘲讽、二阶段；**只用刷怪蛋 / 指令召唤**（**不做 summon 聚晶**）、**复用调律师的聚晶池 + 轮盘赌抽签**（两条均为用户确认的范围）。为复用，三处渲染 / 模型类做了**泛型化**：`TunerModel<T extends LivingEntity>`（原写死 `HumanoidModel<TunerBoss>`）、`TunerCapeLayer<T>`、`TunerOrbLayer<T extends LivingEntity & OrbHighlightSource>`（**新增 `entity/OrbHighlightSource` 接口**，Boss 与仆从都实现它 ⇒ 渲染层**不再依赖具体实体类**）；`client/render/TunerServantRenderer` 是**唯一**仆从专属渲染文件。⑤ **聚晶指令（本轮唯一的新玩法，新增 `entity/TunerServantInteractions`，FORGE 总线）**：订阅 `AttackEntityEvent`（左键）与 `PlayerInteractEvent.EntityInteract`（右键）—— 手持 `IFocus` 物品时**左键 = 切换「不释放」**、**右键 = 切换「优先释放」**（再按一次取消）；左键 `setCanceled(true)`（**指挥自家仆从不该顺手揍它**）、右键取消并置结果 `SUCCESS`。**客户端一律放行、只在服务端判定与取消** —— `javap` 实证 `Player.attack` 的**第一条指令**（偏移 0）就是 `ForgeHooks.onPlayerAttackTarget`（false 即 `return`），若在客户端也取消，`LocalPlayer` 会**提前返回、连攻击包都不发**、机制直接失效。指令存 `FocusPoolManager` 的**实例字段**（**非 static**，与 0.0.16 的 `castCount` 同理）并落盘 NBT（`DisabledFoci` / `PriorityFoci`）；**优先聚晶在下次施法时走 `CastChannel.beginCast(level, entry, mult)` 插队**（新重载：**跳过抽签**，其余流程与普通起手完全共用 `startCast`），并按它**自己类别**路由到对应通道。**认主**：刷怪蛋 / 指令生成的仆从**没有主人**，而若严格要求"主人才能指挥"这条机制将**完全不可达**（用户已确认只用刷怪蛋获取）⇒ **无主的调律师仆从会在第一次被下达聚晶指令时认那位玩家为主人**并提示；**别人家的仆从完全不干预**。⚠️ 仆从**刻意不调用** `FocusPoolManager.noteCast()` ⇒ **不参与** 0.0.16 的「逐渐学习」、**永远以初始分类为准**（保持起始权重 0.15 不变）；`TunerServant` 里已写注释、**别误判成 bug**。规模：新增 **6 个类 + 改 16 个既有类** + 版本号 + **3 个新资源**；`.java` **45 → 51**，配置 **67 → 71 项 / section 10 → 11**（新增 `[servant]` 段 4 项），**`focus.blacklist` 默认值变更 ⇒ 老 toml 需手工迁移**（迁移脚本与游玩实例 toml 均已处理）；⚠️ **全部运行时表现仍待游戏实测**（本轮只做到编译通过 + 字节码 / 资源核对），详见下文「第 56 轮（0.0.18）」；**第 55 轮（0.0.17）成果：索命聚晶后门 + 客户端死亡动画残留自愈** —— 用户反馈「用索命聚晶打中调律师时，Boss 会进入"动画已经死了、血量却回弹"的破状态」，并说「如果可以的话给索命的成功伤害直接开个后门；如果麻烦的话修一下动画问题」——**两个都做了**：① **索命后门**：反编译 Goety 的 `KillingSpell.SpellResult` 实证调用链 `ModDamageSource.deathCurse(target)` → `MobUtil.hurtCalculation(...)` → `target.hurt(source, amount)`（**走 `hurt()`**），并确认伤害类型是 **`goety:death`**（`create("death")` + `data/goety/damage_type/death.json`）、且**全 jar 只有 `KillingSpell` 一处引用 `deathCurse`** ⇒ 按该伤害类型匹配**恰好等价于"索命聚晶"**；新增字段 `deathCursePending` + 常量 `GOETY_DEATH_CURSE`（纯资源位置字符串构造，**不需编译期依赖 Goety 类**），四处联动（`hurt()` 置标记跳过身份/宽限期免疫与致死截断、`maintainDeathState()` 不回弹、`canStillRevive()` 不拦 `remove(KILLED)`、**`actuallyHurt()` 的限伤/限DPS 必须显式开口子** —— `goety:death` 不在任何 `BYPASSES_*` tag 里，漏了后门就形同虚设）⇒ **索命能真正处决、无视剩余锁血档位**；新配置 `boss.deathCurseExecution`（默认 **true**，false = 回到旧行为）；**代价**：索命会对施法者反噬"目标当前生命值 125%"。② **客户端动画自愈**：`deathTime` **不是同步数据**，服务端只发**一次**复位包，若那一刻客户端血量同步还没落地，客户端会**自己再把 `deathTime` 加上去** ⇒ 永久躺着（服务端侧"情形 A"治不了这一侧）；在 `tick()` 的**客户端分支**加**对称自愈**（`deathTime > 0 && getHealth() > 0` 即直接 `resetDeathAnimation`，客户端调用**不发包**）⇒ **至多残留 1~2 tick**。改 **2 个既有 Java 文件**（`entity/TunerBoss`、`config/TunerCommonConfig`）+ 版本号，**无新增类/贴图**，配置 **66 → 67 项**（`boss` 段 **19 → 20**）；⚠️ **仍待游戏实测**（详见 `TECHNICAL_SUMMARY.md` §3.17）；**第 54 轮（0.0.16）成果：「逐渐学习」** —— 给**动态评分**（实战学到的偏移）加一个随**该调律师个体**施法次数**线性爬升**的权重系数（默认 `0.15 → 1.0` / 60 次施法），使**初始评分**（配置 / LLM 分类）在**开局主导**、随实战**逐步交棒**给动态反馈（此前动态偏移与静态评分同权，开局没打几下初始分类就基本失效）；改 **3 个既有 Java 文件**（`focus/FocusEntry`、`focus/FocusPoolManager`、`entity/TunerBoss`）+ 版本号，**无新增类/贴图**，配置 **63 → 66 项**（`scoring` 段 **3 → 6**）；⚠️ **手感待游戏实测**（详见 `TECHNICAL_SUMMARY.md` §3.16）；第 48 轮（0.0.10）美术交付已实现、**待游戏画面验收**；剩余 Boss 专属魔杖、兼容性打磨与少量逻辑边界项。第 43 轮（0.0.5）成果：LLM 评分错误诊断与提示词编辑体验改进、一轮保持功能不变的系统性性能优化（详见 TECHNICAL_SUMMARY.md）；第 44 轮（0.0.6）成果：限伤（单次伤害上限，默认开启 25% 最大生命）+ 限DPS（滑动 1 秒预算，默认关闭）；第 45 轮（0.0.7）成果：LLM 批量评分改分批请求（修「200 个聚晶只应用 25 条」）+ 修线程泄漏 + 提示词格式化加固 + 资源改名；并实证「锁血机制本身已隐含限伤，限伤/限DPS 在当前设计下作用有限，真正旋钮是 lockGraceTicks 与档位数」；第 46 轮（0.0.8）成果：附属模组增删的健壮性加固（逐项扫描兜底 + 施法全流程 try 兜底 + returnEntry 幂等）；死亡状态完善（新增 SEntityRevivePacket 复位客户端死亡动画、脏状态只清动画不消耗锁血档位、锁血未耗尽时拦截 remove(KILLED)）；第 47 轮（0.0.9）成果：为 /kill 打开后门（识别 DamageTypes.GENERIC_KILL 后跳过锁血体系的全部保护，使管理员指令能真正击杀）；第 48 轮（0.0.10）成果：**美术交付**——身体贴图按用户参考图重画（头部区逐像素不变）、8 阶色带披风、红/蓝/灰三颗悬浮立方体（位掩码高亮 + IdentityHashMap 幂等计数）、非对称径向声波涟漪（新增 SAccentWavePacket 通道 id 3，旧粒子默认关闭）、刷怪蛋改走原版 template_spawn_egg（紫/亮蓝染色）；配置 61 项（`music` 段 11→12），网络协议 1.0→**2.0 严格匹配**（联机双方须同时更新）；**上一轮（0.0.11）成果：修掉用户玩出来的真 bug** —— `applyLockHealth()`（由 `aiStep()` 调用）里与 `maintainDeathState()` 重复的「死亡自愈」分支其实**可达**（反编译实证：`LivingEntity.tick()` 里 `aiStep()` 只有 1 处无条件调用，真正被死亡把关的是 `baseTick()` 的 `isDeadOrDying()` → `tickDeath()`；原注释把它与 `serverAiStep()` 混为一谈），后果是 `/kill` 后门被击败（日志实证：`/kill` 后 48 ms Boss 带 18 血复活，只得再杀一次）、白吃一档锁血、且只写 `deathTime=0` 不复位客户端动画（旧「血量不为 0 但已是死亡动画」复发）。修复：删除该分支，改为死亡时在方法开头直接早退；死亡回弹**唯一**权威实现是 `tick()` 里的 `maintainDeathState()`（尊重 `/kill` 后门并同步复位客户端动画）。本轮只改 2 个文件（版本号 + `TunerBoss`），无新增类/贴图/配置。**上一轮（0.0.12）成果：表现层重做 + 一个回调成对性修复** —— ① **径向声波涟漪锚定触发瞬间的坐标**（原先每帧读 Boss 当前位置、会跟着 Boss 跑，而每次锁血都会强制瞬移；现记脚下世界坐标，整条波在固定点上播完，且**清理只按 34 tick 计时**，不再因实体死亡/移除/离开视野提前掐掉）；② **音乐条 HUD 外观重做**（用户反馈「太突兀」）：`260×6`→**`204×8`**、底距 64→62、硬边纯色块→**逐行混色**+2px 过渡缝、单一硬矩形底→**三层柔和投影**（四角留空模拟圆角）、阶段**文字**→**像素符号** `● ● ●`/`●`/`- - - - - -`（实测本客户端字体无 U+26AA 字形，直接写 `⚪` 会显示成空白方块）、重音刻度 `0xB8FFFFFF` 与闪烁峰值 `0x88` 调淡、一阶段分段按条宽做 scissor 裁剪防溢出；③ **`CastChannel` 回调成对性修复**（此前记为「仍未修」的已知边界）：`logCast` 原先排在 `onCastStart` 之后且会抛异常，异常逃逸到 `beginCast` 兜底 `catch` 而那里不补发结束回调 ⇒ 孤儿回调使施法状态计数与立方体类别掩码**永久 > 0**（立方体一直高亮、蹲姿卡住，且身份集合幂等让该聚晶再也无法计入）；现把 `logCast` 调到 `onCastStart` **之前**（结构上不可能再被打断）+ 新增 `startEmitted` 标志兜底补发 `onCastFailed`。另顺手修正 `applyLockHealth()` 的方法 javadoc（仍在描述 0.0.11 已删除的行为，纯注释、无行为变化）。本轮无新增类/贴图/配置项（jar 条目 101→102 只是多了 `AccentWaveRenderer$Wave` 内部类）。第 53 轮（0.0.15）成果：**只有本体贴图精修** —— `tuner.png` **2520 → 2676 B**（翻领边缘 / 衣袖明暗 / 袖口细边 / 裤缝 / 靴口层次），**零 Java 改动、无新增/删除资源**（`.java` 仍 45、配置仍 63 项 / 10 段、jar 仍 102 条目）；**独立核验**：头部区逐像素 0 差异、alpha 蒙版完全一致、身体区改 1247 像素、6 个主要 UV 面无透明空洞；⚠️ **观感仍待游戏内画面验收**。
> **更早（0.0.13，第 51 轮）成果：音乐自愈 + 重音减密 + 表现层微调 + 药水可观测性** —— ① **修「玩家被击杀复活后（未走出索敌范围）Boss 背景音乐丢失」**：`BossMusicManager` 原先只以静态字段 `music != null` 判定「在播」、**从不与声音引擎核对**，而死亡/复活会让引擎把循环实例悄悄摘除（RECORDS 音量为 0 / channel 停止 / `SoundEngine.reload()→destroy()→stopAll()` / `play()` 未 loaded 时静默返回），字段却仍非 null ⇒ 服务端 `playing` 为 true 时 `startMusic` **永不重入** = **永久静音**；`onPlaySound` 同样只看字段 ⇒ **连原版 BGM 也一起被永久取消**（症状「整个 BGM 都没了」）。现每 tick 用 `SoundManager.isActive(music)` 核实 + 10 tick 防抖，失效即清空字段、下一 tick 自愈重建。② **重音标记滚动平滑**（`MusicBarHud` 亚像素覆盖：小数部分按比例摊到相邻两列，亮度重心连续移动）+ **高潮「中」字改小长条**（高潮 2×6 / 低谷 1×6 / 铺垫 1×4，竖向居中）。③ **涟漪多波共存**（`AccentWaveRenderer` 的 `Map`→`List`，二阶段进场连发不再互相顶掉；`MAX_WAVES=32` 兜底）。④ **新增配置 `music.accentDensityDivisor`（默认 3、范围 1~9）**：服务端加载乐谱时「每 N 个保留 1 个」，HUD 刻度/击退/涟漪/提示音一起变稀疏、客户端零改动（默认乐谱 37 → 13 个重音）；配置 **61 → 62 项**（`music` 段 12 → 13）。⑤ **立方体高亮改「向白插值」+ 两层自发光外壳**（`entityTranslucentEmissive`；原版发光描边是整实体级 `OutlineBufferSource`，无法只描一颗立方体）。⑥ **药水可观测性**：8 处 `addEffect` 返回值原先全被丢弃（被 `canBeAffected`/`MobEffectEvent.Applicable` 拒绝时静默失效），新增 `applySelfEffect` 打 WARN（boss 自身 4 处）+ 药水现状审计（低谷效果仅一阶段且 `visible=false` 无粒子、二阶段自施可达且不受亡灵免疫、`SUMMON_DOWN` 免疫确认真实生效）。⑦ 配置侧把 `goety:killing_focus`（索命聚晶，对施法者反噬 125%）加入 `focus.blacklist`（**不在 jar 内**；该 toml **无法在游戏内修改**）。

> **更早（0.0.14，第 52 轮）成果：配置界面补上「聚晶黑名单」入口 + 二阶段免疫回复/减伤类效果** —— ① **配置界面新增「聚晶黑名单」输入框**（`client/TunerConfigScreen` + `focus/FocusPoolManager`）：`focus.blacklist` 是 common 配置，而本模组的 `TunerConfigScreen` **顶替了 Forge 默认的 toml 编辑器**，该键此前在游戏内**完全没有入口**（0.0.13 的「拉黑索命聚晶」因此只能靠改文件落地）；现补一个单行 `EditBox`（预填当前值，hint「namespace:path，英文逗号分隔；留空 = 不屏蔽」），**点「完成」关屏时保存**、点「开始评分」时也顺带保存，保存 = `FOCUS_BLACKLIST.set(v)` + `.save()` + `FocusPoolManager.refreshBlacklist()` ⇒ **改完立即生效**（`getBlacklist()` 以 raw 字符串为缓存键，值一变缓存自动失效）。**新增 `FocusPoolManager.refreshBlacklist()`** 的理由：`initIfNeeded()` 扫描时**直接 `continue` 跳过**黑名单聚晶（不进 `ALL_ENTRIES`）⇒"**新增**拉黑"靠实时过滤即可生效，但"**取消**拉黑"必须重扫；而重扫若 `new` 出新 `FocusEntry`，会让实体侧**按对象身份**记录的状态失效（`TunerBoss.activeVisualCasts` 身份集合、`CastChannel.current`）⇒「立方体高亮卡住 / 施法收尾回调对不上」。故新方法按 `namespace:path` 建索引**复用已有对象**，只为"这次才被解禁"的聚晶新建（它们此前不可能在施法中，故安全），再重建 `STATIC_POOLS` 并重新 `applyTo` 分类。新增 4 个 lang 键（zh_cn / en_us 各 4 个）。⚠️ 连他人的服务器时改的是**本地** toml、服务器侧不受影响。② **二阶段免疫「回复 / 减伤」类药水效果**（`entity/TunerBoss` + `config/TunerCommonConfig`）：扩展现有 `canBeAffected` 覆写（它同时是 `addEffect` 与 `forceAddEffect` 的**第一道**判定 ⇒ 返回 false 是**真正的免疫**，不是"加完再清"），名单 = 抗性提升/伤害吸收/生命恢复/瞬间治疗/生命提升，**仅当 `music.isPhase2()`** 生效；**刻意只列这 5 项而非"所有 beneficial"**——Boss 自己的二阶段增益（力量 `DAMAGE_BOOST` 与重振 `RALLYING`）也是 beneficial，一刀切会把它们一起禁掉；名单集中在私有 `isPhase2Immune(...)`，以后加一行即可。新增配置 `phase2_buffs.phase2EffectImmunity`（默认 **true**，false = 回旧行为）；**不影响玩家**、也**不影响 Boss 自己的二阶段自施**。配置 **62 → 63 项**（`phase2_buffs` 段 **3 → 4**）。

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
| 二阶段 | ✅ | 半血触发+音乐切换+打断+数据同步；**0.0.14 起二阶段免疫「回复/减伤」类效果**（抗性提升/伤害吸收/生命恢复/瞬间治疗/生命提升，走 `canBeAffected`，开关 `phase2_buffs.phase2EffectImmunity` 默认 true） |
| 效果清除 | ✅ | 低谷清正面/高潮清负面（本次新增）；**0.0.13 新增 `applySelfEffect`**：boss 自身 4 处自施药水被 `canBeAffected`/`MobEffectEvent.Applicable` 拒绝时打 WARN（此前全类 8 处 `addEffect` 的 boolean 返回值全被丢弃、效果被拒即静默失效） |
| 重音系统 | ✅ | 分阶段斥力+无前摇瞬发施法 |
| 仆从管理 | ✅ | 数量上限+迟滞恢复+伤害归因 |
| **调律波纹聚晶（玩家可用）** | ⚠️ **待游戏实测** | **0.0.18 新增** `goetytuner:tuner_ripple_focus`（`focus/RippleSpell`，复用 Goety `MagicFocus`）：基础值 灵魂 **5** / 蓄力 **4 tick（0.2 s）** / 冷却 **10 tick（0.5 s）**、`SpellType.NONE`、不接受附魔（Goety 再叠施法者修正，未覆写绕过）；效果 = **铺垫期**重音涟漪 + 击退，走 `combat/AccentRipple`、尊重 `music.accentParticles/accentWave/accentSound` 三开关；**默认已写入 `focus.blacklist`**（防 Boss 白得一个 0.5 s 冷却的重音、以及涟漪把 Boss 自己推开） |
| **调律师仆从** | ⚠️ **待游戏实测** | **0.0.18 新增** `goetytuner:tuner_servant`（`entity/TunerServant`）：`Goety ...ally.Summoned` 子类 ⇒ 一般仆从性质全继承；复用同一 `FocusPoolManager`（每仆从一份实例）/ `CastChannel` / 模型贴图，**没有**音乐阶段、锁血、瞬移、嘲讽、二阶段；只用刷怪蛋 / 指令召唤（不做 summon 聚晶）、复用 Boss 聚晶池 + 轮盘赌。**0.0.19 三项改动**：① **血量 / 护甲 / 减伤限伤与本体一致**（读 `boss.maxHealth`=216 / `boss.equivalentArmor`=16、`KNOCKBACK_RESISTANCE` 1.0；新增 `hurt`/`actuallyHurt` 覆写走 `combat/TunerDamageRules` + `combat/DamageThrottle` **共用实现**；**刻意不含锁血阶梯与 `/kill` 后门**）；② **刷怪蛋改用 Goety 仆从蛋范式**（`init/TunerServantSpawnEggItem`：**直接放 = 野生 / 潜行放 = 认主** + 文字提示；**归属不再因交互而改变**，"第一次下指令认主"已删除）；③ **聚晶指令语义改互斥**（左键顺手清「优先」/ 右键顺手清「不释放」；**潜行+左键 = 清空全部指令**；提示文案带两张表条目数） |
| **重音涟漪复用（AccentRipple）** | ✅（结构）/ ⚠️ 待实测 | **0.0.18 新增** `combat/AccentRipple` 为「重音涟漪」**唯一实现**：击退 / 粒子 / 声波 / 提示音四合一，`TunerBoss.accentKnockbackPulse` 改成**四行转发** ⇒ Boss 与聚晶**共用同一份代码**；差异只有「力量与音调」与「聚晶额外豁免自己人」两处，由调用方给 |
| **长按类聚晶持续释放（IChargingSpell）** | ⚠️ **待游戏实测** | **0.0.19 修复**（用户第 4 条反馈，本轮最有价值的一处）：腐化 / 震撼 / 炼狱这类"长按持续释放"聚晶在调律师（及仆从）身上**只放一瞬间就停**。**两层根因（均已反编译实证）**：① `CastChannel` 原先只在前摇结束时调**一次** `SpellResult`（玩家路径按键是法术自己的 `Cooldown` 反复调 `MagicResults`）；② `AbstractBeam.tick()` 靠 `MobUtil.isSpellCasting(owner)` 判定存活（= `isUsingItem() && 用物是 IWand && 杖里有聚晶`），而 **Mob 从不 `startUsingItem`** ⇒ 光束刚生成就被丢弃。**修法**：`CastChannel` 新增 **`tickChannel`**（`startUsingItem` + 每 tick 自愈重设 + 结束/打断/异常一律 `stopUsingItem`，并按 `Cooldown`/`shotsNumber` 反复释放；判定依据 Goety 自己的 `IChargingSpell`、**不写死聚晶 id** ⇒ 附属同类法术自动受益）+ **自备 `focus/TunerWand`**（`onUseTick` **空实现** —— 直接用 `dark_wand` 会让 `DarkWand.onUseTick` 对**非玩家**走 `failParticles + FIRE_EXTINGUISH`：不放法术、只冒白烟响灭火音）；**用时上限** = 新增 `casting.channelMaxTicks`（默认 **20 = 1 秒**） |
| 客户端HUD | ✅ | **204×8** 音乐条（0.0.12 重做：逐行混色分段 + 2px 过渡缝、三层柔和投影（四角留空模拟圆角）、阶段**像素符号** `● ● ●`/`●`/`- - - - - -`）、三色分段（铺垫 0xFF5B9BE0 / 高潮 0xFFF26A4B / 低谷 0xFFB068E8）、分阶段重音刻度（0.0.13 起**亚像素覆盖**平滑滚动，不再逐像素跳动；高潮 2×6 / 低谷 1×6 / 铺垫 1×4 小长条，半透明白 0xB8FFFFFF）、二阶段锚定坠落条带 + scissor 裁剪 + wrap 补位、越线亮黄闪烁（峰值 0x88） |
| 网络同步 | ✅ | SMusicSyncPacket 20tick推送进度/阶段/二阶段；共 4 个通道（含 0.0.10 新增 `SAccentWavePacket` id 3，限 `PLAY_TO_CLIENT`），协议 **2.1** 严格匹配（**0.0.18 起** `SAccentWavePacket` 载荷由「实体 id」改为「三个 double 的世界坐标」，通道 id 仍 3、广播改「锚点 64 格内按玩家」；**0.0.19 未改动协议、仍为 `2.1`**） |
| 正式纹理 | ✅ | `tuner.png` 64x64 身体按参考图服装重画、头部仍是黑立方+蓝渐变（0.0.10）；另有 8 阶披风、立方体与声波贴图，自定义蛋贴图已删（改走原版模板） |
| 渲染方案 | ✅ | 原版 HumanoidMobRenderer + `TunerModel` + `TunerCapeLayer` + 悬浮立方体 `TunerOrbLayer`/`TunerOrbModel`（0.0.10；0.0.13 高亮改「向白插值」+ 两层 `entityTranslucentEmissive` 自发光外壳）；**0.0.18 起 `TunerModel`（`<T extends LivingEntity>`）/`TunerCapeLayer<T>`/`TunerOrbLayer<T extends LivingEntity & OrbHighlightSource>` 全部泛型化**（新增 `entity/OrbHighlightSource` 接口 ⇒ 渲染层不再依赖具体实体类）**以便仆从复用，并新增唯一的仆从专属渲染文件 `TunerServantRenderer`** |
| 配置系统 | ✅ | **71 项** / **11** 个 section（toml）；`phase2_buffs` 段 0.0.14 增至 4 项，`scoring` 段 0.0.16 增至 6 项，`boss` 段 0.0.17 增至 20 项，**0.0.18 新增 `servant` 段 4 项**，**0.0.19 起 `casting` 段 10 → 11 项（新增 `channelMaxTicks`）、`servant` 段 4 → 3 项（删除死配置 `health`，血量护甲改由 `[boss]` 段的 `maxHealth` / `equivalentArmor` 统一提供）⇒ 总项数不变仍 71** |
| 实测验证 | ✅ | quickPlay自动进档验证通过（第 9/11 轮） |
| 客户端音乐播放器 | ✅ | `BossMusicManager` 客户端循环实例（`SimpleSoundInstance` looping + `Attenuation.NONE` + relative），解决阶段切换重叠/原版音乐重叠/Boss 死后不停（第 21 轮）；**0.0.13 修「玩家被击杀复活后（未走出索敌范围）音乐丢失」：每 tick 用 `SoundManager.isActive` 核实实例真的在响 + 10 tick 防抖，失效即清空字段并自愈重建**（第 51 轮） |
| 重音特效 | ✅ | 非对称径向声波涟漪（0.0.10；**0.0.13 起活跃表由 Map 改 List ⇒ 同实体多波共存**，旧粒子默认关闭） + 阶段差异化紫水晶音（0.9/1.4/0.6）；二阶段进场连发 6 次（第 19/31 轮）；**重音密度由 `music.accentDensityDivisor`（默认 3）服务端统一抽稀**（第 51 轮）；**0.0.18 起击退 / 粒子 / 声波 / 提示音四件事统一由 `combat/AccentRipple` 提供 —— Boss 与波纹聚晶共用同一份代码** |
| 披风渲染层 | ✅ | `TunerCapeModel` + `TunerCapeLayer`（第 19 轮） |
| 聚晶黑名单 + 施法自愈 | ✅ | 配置 `focus.blacklist`（String 容错解析）+ 运行期 `RUNTIME_BLACKLIST` + 实体级拦截 `goetytwilight:destruction`（第 26/29 轮）；**0.0.14 起配置界面有「聚晶黑名单」输入框**（关屏/开始评分时保存 + `refreshBlacklist()` 热刷新，取代"只能手改 toml"）；**0.0.18 起默认值改为 `goetytwilight:destruction_focus, goetytuner:tuner_ripple_focus`**（**改已有键的值** ⇒ Forge 不会回填老 toml、需手工迁移，见 §六 的 0.0.18 迁移提示） |
| 分类三层防线 | ✅ | 手动配置 → `instanceof ISummonSpell` 权威判定 → `describe()` 兼容 `.info`/`.desc` 双后缀 → 关键词兜底（第 29 轮） |
| 仪式召唤 + 法杖升级 | ✅ | 任务 #118：`goety:ritual_factory` 注册、快照制掉落、10% 巫法（`SPELL_POTENCY` MULTIPLY_TOTAL）+ 40% 魔法伤害、经验×4（第 36 轮） |
| 调律命令 | ✅ | `/goetytuner tune <witchcraft\|magic\|add\|clear\|info>`（OP2）（第 37b 轮） |
| 法杖白名单 | ✅ | `wand_whitelist`（第 38 轮） |
| 游戏内配置界面 + LLM 评分 UI | ✅ | `IConfigScreenFactory` 注册（Mods 菜单出现 Config 按钮）、提示词输入框、`TunerToast` 结果提示、LLM 结果宽松校验（第 41 轮）；**0.0.14 起界面再加一个「聚晶黑名单」单行输入框**（预填 `focus.blacklist` 当前值，点「完成」关屏或点「开始评分」时 `set + save` 并调 `FocusPoolManager.refreshBlacklist()` 热刷新 ⇒ 改完立即生效） |

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

### `run/config/goetytuner-common.toml`（71项 / 11 个 section，第56轮实况）

| 分类 | 配置项 | 默认值 | 说明 |
|---|---|---|---|
| **boss**（20项，0.0.17 由 19 项增至 20 项） | maxHealth | 216 | Boss血量 |
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
| | **deathCurseExecution** | **true** | **0.0.17 新增**：**索命聚晶后门开关** —— `true` = 玩家用索命聚晶（Goety 的 Killing Focus）命中 Boss 时，它造成的 **`goety:death`** 致死伤害**不再被锁血体系拦截**（跳过身份免疫 / 宽限期免疫 / 致死截断 / 限伤·限DPS / 死亡回弹 / `remove(KILLED)` 拦截）⇒ **索命能真正处决调律师，无视剩余锁血档位**；`false` = 关闭后门，索命重新受锁血保护（回到 0.0.17 之前的旧行为）。**为何限定"恰好是索命"**：反编译实证 Goety 侧调用链 `ModDamageSource.deathCurse(target)` → `MobUtil.hurtCalculation(...)` → `target.hurt(...)`，且**全 jar 只有 `KillingSpell` 一处引用 `deathCurse`** ⇒ 按 `goety:death` 匹配不会误伤别的法术。**代价**：索命会对施法者反噬「目标当前生命值 **125%**」⇒ 这是**有代价的处决手段**。⚠️ 本键在 **`[boss]` 段**（与 `maxDamagePerSecond` / `lockDeathRevive` 同段，不是 `[phase2_buffs]` 段）；属**新增键**（Forge 自动补进老 toml），但**配置界面没有入口 ⇒ 改完需手改 `goetytuner-common.toml` 并重启游戏**。详见 `TECHNICAL_SUMMARY.md` §3.17 |
| **phase2_buffs**（4项） | phase2BuffsEnabled | true | 二阶段强化药水开关 |
| | phase2StrengthLevelLow | 2 | 低档力量等级 |
| | phase2StrengthLevelHigh | 5 | 高档力量等级 |
| | **phase2EffectImmunity** | **true** | **0.0.14 新增**：二阶段免疫「回复 / 减伤」类药水效果 —— 抗性提升(`DAMAGE_RESISTANCE`) / 伤害吸收(`ABSORPTION`) / 生命恢复(`REGENERATION`) / 瞬间治疗(`HEAL`) / 生命提升(`HEALTH_BOOST`) **一律无法施加到 Boss**（走 `canBeAffected`，是 `addEffect` 与 `forceAddEffect` 的**第一道**判定）。**刻意只列这 5 项而非"所有 beneficial"**：Boss 自己的二阶段自施（力量 `DAMAGE_BOOST`、重振 `RALLYING`）也是 beneficial，一刀切会把它们一起禁掉。`false` = 回到旧行为；**不影响玩家**。⚠️ 本键在 **`[phase2_buffs]` 段**（不是 `[boss]` 段） |
| **summon**（4项） | maxMinions | 32 | 召唤上限 |
| | refillHysteresis | 4 | 满员迟滞（低于 max-N 才恢复） |
| | survivalWeight | 1.0 | 召唤评分参数1（生存权重） |
| | attackWeight | 1.0 | 召唤评分参数2（输出权重） |
| **scoring**（**6项**，0.0.16 由 3 项增至 6 项） | baseRouletteWeight | 2.0 | 轮盘保底基数 |
| | dynamicScoreCap | 5.0 | 动态偏移上限 |
| | dpsAdjustRate | 0.5 | DPS修正速率 |
| | **learningWeightStart** | **0.15** | **0.0.16 新增**：**「逐渐学习」起始权重**（0~1）—— 开局的**动态评分只按该比例**计入轮盘权重，让**初始评分**（配置 / LLM 分类）主导；`0` = 完全无视动态评分（纯用初始分类），`1` = 旧行为（一开始就全权重）。三键合起来 = 「动态偏移 × 系数」里的系数，**静态评分不受影响** |
| | **learningWeightMax** | **1.0** | **0.0.16 新增**：**「逐渐学习」权重上限**（0~2）—— 施法次数足够多之后动态评分的权重；`1` = 与旧行为持平。⚠️ 设为 0 则"永远学不会"（动态评分永久被忽略） |
| | **learningWeightRampCasts** | **60** | **0.0.16 新增**：**从起始权重线性升到上限所需的施法次数**（1~1000）—— 默认 60 次（曲线：0 次→0.150、10→0.292、20→0.433、30→0.575、40→0.717、50→0.858、60→**1.000**，之后封顶）。只统计"用聚晶施法"的前摇起手（`onCastStart`；瞬发/重音不计入）；每跨 10% 里程碑打一条 INFO `[Tuner] Learning weight ...`。⚠️ 计数是**每只 Boss 私有**且**不落盘** ⇒ 退出重进 / 重召唤**归零**；三键都**只能在 toml 里改**、**改完需重启** |
| **casting**（**11项**，0.0.19 由 10 项增至 11 项） | extraCastCooldown | 20 | 额外施法冷却 |
| | maxCastWindowTicks | 50 | **普通法术**的单次施法窗口上限（tick）——把前摇截断到 2.5 秒（玩家提前松手是合法释放路径） |
| | **channelMaxTicks** | **20** | **0.0.19 新增**：**「长按持续释放」类聚晶（Goety `IChargingSpell`：腐化光束 / 震撼 / 暴雪 / 轰炸 / 旋风 / 箭雨 / 电击 / 水流 / 蒸汽 / 念力 / 吸取 / 掘地 / 进食 / 飞行 / 防护 / 流星雨 …）的单次施法总时长上限**（tick，**20 = 1 秒**，范围 5~200）。**⚠️ 与 `maxCastWindowTicks` 的分工**：本键管**长按类**的持续时长；`maxCastWindowTicks` 管**普通法术**的蓄力截断（2.5 秒），两者互不影响、只对各自那类法术生效。**为什么需要它**：玩家手里这类法术是"按住多久就放多久"，而 **Mob 没有松手动作** ⇒ 不给上限就会一直放下去。**三条独立收口、谁先到算谁**：① 总时长到本键；② 法术自己的 `IChargingSpell#shotsNumber(...)`（>0 时）放完；③ 防御性硬上限 `MAX_CHANNEL_SHOTS = 400`。**⚠️ 调大有风险**：腐化光束这类法术是**每 tick 造成伤害**的（Goety 默认 `CorruptedBeamDamage = 10.0`/次，且它会清零目标的 `invulnerableTime` 以绕过无敌帧），上限 20 已经能打出很高的总伤害，**调到 100 以上基本等于必杀**。⚠️ 属**新增键**（Forge 自动补进老 toml），但**配置界面没有入口 ⇒ 只能手改 `goetytuner-common.toml`，改完需重启游戏** |
| | climaxWarmupMultiplier | 0.5 | 高潮前摇倍率 |
| | phase1BuildupRotation | "123" | 一阶段铺垫期通道轮换 |
| | phase2BuildupRotation | "222" | 二阶段铺垫期通道轮换 |
| | phase1ClimaxChannels | "123" | 一阶段高潮三通道 |
| | phase2ClimaxChannels | "222" | 二阶段高潮三通道 |
| | phase2TeleportIntervalFactor | 1.0 | 二阶段瞬移间隔系数（范围 0.1~1.0，只能缩短二阶段瞬移间隔） |
| | phase2BuildupArcRadius | 6.0 | 二阶段铺垫期弧形瞬移半径 |
| | phase2BuildupArcEveryN | 3 | 每 N 次铺垫攻击触发一次弧形瞬移 |
| **focus**（1项） | blacklist | **"goetytwilight:destruction_focus, goetytuner:tuner_ripple_focus"**（**0.0.18 改值**） | 聚晶黑名单（容错解析：单 id / 英文逗号分隔 / 数组写法，逐段 trim、**大小写敏感**，实时解析带缓存）。**0.0.18 起代码默认值 = `destruction_focus` + 本模组新增的 `tuner_ripple_focus`**（否则 Boss 会抽到它：等于白得一个 0.5 秒冷却的重音，且那发涟漪会把 Boss 自己一起推开）；0.0.13 起**游玩实例的 toml** 还额外含 `goety:killing_focus`（索命聚晶，对施法者反噬 125%）。⚠️ **这是"改已有键的值"** ⇒ Forge **不会**回填老 toml，**必须手工迁移**（见下方 0.0.18 迁移提示）。✅ **0.0.14 起可在游戏内配置界面修改**（新增单行输入框，点「完成」关屏或点「开始评分」时保存 + 热刷新 ⇒ **改完立即生效**）；⚠️ 仍是**唯一**补了入口的 common 键，**其余配置项依旧只能手改 toml**；连他人服务器时改的是**本地** toml。`RUNTIME_BLACKLIST` 与它是**并集**、配置无法解禁 |
| **wand_whitelist**（1项） | whitelist | "" | 法杖白名单（格式同上，供未加入 `goety:wands` 标签的附属法杖激活仪式） |
| **music**（13项） | syncInterval | 20 | 音乐同步间隔 |
| | accentKnockbackBase | 0.4 | 普通重音斥力 |
| | accentKnockbackValley | 0.8 | 低谷重音斥力 |
| | accentKnockbackClimax | 1.2 | 高潮重音斥力 |
| | phase2ValleyKnockbackMultiplier | 2.0 | 二阶段低谷斥力倍率（注意：二阶段低谷被替换为铺垫，故该键在二阶段不可达） |
| | accentShakeTicks | 10 | 重音镜头抖动持续 tick |
| | accentShakeStrength | 2.0 | 重音镜头抖动强度 |
| | accentParticles | false | 旧重音粒子开关（兼容选项） |
| | accentWave | true | 非对称径向声波涟漪（0.0.12 起锚定在触发瞬间的坐标、只按 34 tick 计时清理；**0.0.13 起活跃表改 List，同一实体多波共存**、连发不再互相顶掉） |
| | accentSound | true | 重音音效开关 |
| | **accentDensityDivisor** | **3** | **0.0.13 新增**：重音抽稀「**每 N 个保留 1 个**」（1~9；1=不抽稀，默认 3 ⇒ 数量与频率约为原来的 1/3）。在**服务端加载乐谱时**统一生效 ⇒ HUD 刻度（经 `SMusicSyncPacket` 全量同步）/ 击退 / 涟漪 / 提示音**一起**变稀疏、客户端零改动；加载时打 INFO `Accent thinning x3: 37 -> 13 accents` |
| | volume | 4.0 | Boss 音乐音量 |
| | pitchPhase1 | 1.0 | 音乐播放速度（一/二阶段共用同一速度） |
| **servant**（**3项**，**0.0.18 新增段**；**0.0.19 由 4 项减至 3 项**） | ~~health~~（**0.0.19 已删除**） | ~~40~~ | **0.0.18 引入、0.0.19 删除的死配置**。用户要求「仆从的血量 / 护甲 / 减伤限伤机制和本体保持一致」⇒ 仆从现在**直接读 `[boss]` 段的 `maxHealth`（默认 216）与 `equivalentArmor`（默认 16）**，`KNOCKBACK_RESISTANCE` 也对齐 1.0 ⇒ 本键失去意义、**已移除**。⚠️ **这是「删键」**：老 toml 里那一行会变成**孤儿条目**，Forge 会自行处理，**无需手工迁移**（与 0.0.18 那种「改已有键的值」是两种性质）。想让仆从改肉薄/改厚 ⇒ 改 `[boss]` 的 `maxHealth`（**注意：那会同时改 Boss 本体**） |
| | followRange | 32 | 仆从索敌半径 / FOLLOW_RANGE 属性（8~128）；仆从是**远程施法者**，默认比近战仆从大一些 |
| | castIntervalTicks | 40 | 两次施法之间的间隔（0~600 tick）；默认 40 = **2 秒**，指「上一发结算完」到「开始下一发前摇」的等待，**与聚晶自身冷却（冷却池）是两回事、二者取更长者** |
| | rotation | "23" | 仆从的施法轮换序列（数字串，每位一个通道角色：1=防御 / 2=攻击 / 3=召唤 / 4=其他，按序循环）；默认 `23` = 攻击、召唤各半。防御/其他类不参与评分、均匀随机；攻击/召唤类走与 Boss **同一套评分与轮盘赌** |
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
>
> ⚠️ **0.0.13 新增键**：`music.accentDensityDivisor`（默认 `3`）是**新增键**，Forge 会**自动补进**老 toml、**无需手改**
> （实例 toml 在下次启动写入该行之前查不到它属正常——默认值在代码里）。**「新增键自动补齐」与「改默认值不会回填」仍是两件事。**
>
> ⚠️ **0.0.13 的"可访问性"条目（0.0.14 已部分推翻，保留以便追溯）**：本模组的 `TunerConfigScreen` **顶替**了 Forge 默认的 toml 编辑器，
> 0.0.13 时该界面只有 LLM API Key 与提示词两个输入框 ⇒ 当时**本表里的所有配置项都只能手改 `goetytuner-common.toml`**。
> ✅ **0.0.14 更新**：界面已补上 **「聚晶黑名单」输入框**（`focus.blacklist` 不再需要手改文件，改完点「完成」或「开始评分」即保存生效），
> 但**除黑名单外的其余配置项依旧只能手改 toml**。⚠️ 另外：新增键（如 `phase2_buffs.phase2EffectImmunity`）Forge 会**自动补进**老 toml；
> 而**连他人服务器**时改的是**本地**那份 toml，服务器侧不受影响（单机 / 局域网主机同进程，正常生效）。
>
> ⚠️ **0.0.16 新增键**：`scoring.learningWeightStart`（默认 `0.15`）、`scoring.learningWeightMax`（默认 `1.0`）、
> `scoring.learningWeightRampCasts`（默认 `60`）三键都是**新增键** ⇒ Forge 会**自动补进**老 toml、**无需手改**
> （老 toml 在下次启动写入这三行之前查不到它们属正常 —— 默认值在代码里）。
> ⚠️ 但**这三个键在游戏内配置界面没有入口**（界面只有 LLM API Key / 提示词 / 聚晶黑名单三个输入框）
> ⇒ 想调"逐渐学习"的曲线**只能手改 `goetytuner-common.toml` 后重启游戏**；重启同时会把该 Boss 的施法计数
> （学习进度）**一起归零**（不落盘，见 `TECHNICAL_SUMMARY.md` §3.16）。
>
> ⚠️ **0.0.17 新增键**：`boss.deathCurseExecution`（默认 `true`）同样是**新增键** ⇒ Forge 会**自动补进**老 toml、**无需手改**
> （老 toml 在下次启动写入该行之前查不到它属正常 —— 默认值在代码里）。
> ⚠️ 它**也没有配置界面入口** ⇒ 想关掉索命后门**只能手改 `goetytuner-common.toml` 后重启游戏**
> （common 配置在 mod 加载时读入内存，手改文件不会热生效）。详见 `TECHNICAL_SUMMARY.md` §3.17。
>
> ⚠️ **0.0.18 迁移提示（必须手工做一次 —— 本项目红线 8）**：`focus.blacklist` 是**改已有键的默认值**，
> 而 Forge **按 key 合并配置、不会用新默认值覆盖你已有的 toml** ⇒ 老存档里**不会**自动出现
> `goetytuner:tuner_ripple_focus`（后果：Boss 会抽到这张聚晶 —— 等于白得一个 0.5 秒冷却的重音，
> 而且那发涟漪还会把 Boss 自己一起推开）。已提供 `scripts/add_ripple_focus_blacklist.py`
> （自动探测编码 / 备份 / 只改那一行 / `difflib` 复核差异仅限该行 / 校验 CRLF 与无 BOM），
> 并且**已经对游玩实例 `versions\测试\config\goetytuner-common.toml` 跑过**
> （值 = `goetytwilight:destruction_focus, goety:killing_focus, goetytuner:tuner_ripple_focus`，
> 备份 `goetytuner-common.toml.bak-before-blacklist-ripple`）；**也可以直接在游戏内 Mods → Config →
> 「聚晶黑名单」输入框**里补（0.0.14 起有入口、改完立即生效、无需重启）。
> ⚠️ **0.0.18 新增的 `[servant]` 段 4 项属「新增键」** ⇒ Forge 会**自动补进**老 toml、**无需手改**
> （老 toml 在下次启动写入这四行之前查不到它们属正常 —— 默认值在代码里）；但它们**配置界面没有入口**
> ⇒ 想改只能手改 toml 后**重启游戏**。
> **⇒「新增键会自动补齐」与「改默认值不会回填」仍是两件事，不要混为一谈。**
>
> ⚠️ **0.0.19 配置变更（一加一删，两种性质都出现了）**：
> ① **新增键** `casting.channelMaxTicks`（默认 **20** = 1 秒）⇒ Forge **自动补进**老 toml、**无需手改**；
> ② **删除键** `servant.health`（0.0.18 引入的死配置）⇒ 老 toml 里那一行变成**孤儿条目**，Forge 会自行处理，
> **同样无需手工迁移**（**删键 ≠ 改键默认值** —— 后者才是必须手工迁移的那种，见上面的 0.0.18 提示）。
> **⇒ 本轮总项数与段数都不变（仍 71 项 / 11 段）**：`casting` 10 → 11、`servant` 4 → 3，净变化 0。
> ⚠️ `channelMaxTicks` 与其余 common 键一样**配置界面没有入口**（界面只有 LLM API Key / 提示词 / 聚晶黑名单三个输入框）
> ⇒ **改完需重启游戏**；**0.0.18 的 `focus.blacklist` 手工迁移仍然有效、不要撤**。

### `run/config/goetytuner/music_score.json`（真实值）
```json
{"segments":[{"phase":"buildup","ticks":80},{"phase":"climax","ticks":960},{"phase":"valley","ticks":700},{"phase":"buildup","ticks":225}],"accents":[0,80,120,160,200,240,280,320,360,400,440,480,520,560,600,640,680,720,760,800,840,880,920,960,1000,1040,1120,1200,1280,1360,1440,1520,1600,1680,1740,1820,1900]}
```
- 总 1965 tick = 98.25 秒，与内置 ogg（98.27 秒）对齐；共 37 个重音
- ⚠️ **0.0.13 起文件里仍是 37 个，但运行时被 `music.accentDensityDivisor`（默认 3）抽稀为 13 个**
  （`MusicController.loadScore()` 里「每 N 个保留 1 个」，加载时打 INFO `Accent thinning x3: 37 -> 13 accents`；
  **抽稀只在内存里做、不回写文件**）——**两个数字都对，引用时请注明是"文件"还是"运行时"**

### `run/config/goetytuner/focus_classification.json`
- 结构为 `{apiKey, prompt, foci{<id>:{category,attackScore,survivalScore}}}`
- 静态聚晶分类+评分表；LLM API Key 存储于此（不在 toml）
- 首次生成时只有 **3 条示例条目**（本仓库外的编译副本 `...\run\config\` 目前仍是这 3 条），其余由启发式分类器兜底；
  而**游玩实例**里已用 LLM 批量评分写入 **277 条**（252 条离线 + 25 条游戏内，见 README 与
  `TECHNICAL_SUMMARY.md` §七.6）——两处数字不同是因为**看的不是同一个文件**，不是矛盾
- 运行时自动生成，可手动编辑覆盖

---

## 七、优先级排序与建议开发顺序

### 已完成（第 0.0.19 / 57 轮现状，保留划掉条目以便追溯）
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
- [x] ~~**玩家死亡复活后 Boss 音乐永久丢失**~~：`BossMusicManager` 原先只以静态字段 `music != null` 判定「在播」、
  **从不与声音引擎核对**，而死亡/复活会让引擎把循环实例悄悄摘除（RECORDS 音量 0 / channel 停止 /
  `SoundEngine.reload()→destroy()→stopAll()` / `play()` 未 loaded 静默返回）⇒ 只要没脱战就**永不重播**，
  且 `onPlaySound` 同样只看字段 ⇒ **连原版 BGM 也一起被永久取消**。现每 tick 用 `SoundManager.isActive(music)`
  核实实例是否真的在响 + **10 tick 防抖**，失效即清空字段、下一 tick 自愈重建
  （第 51 轮 / 0.0.13，详见 `TECHNICAL_SUMMARY.md` §3.14(1)）
- [x] ~~**重音太密 / 标记滚动不平滑 / 连发涟漪互相顶掉**~~：新增 `music.accentDensityDivisor`（默认 3、范围 1~9）
  在**服务端加载乐谱时**统一抽稀（HUD 刻度/击退/涟漪/提示音一起变稀疏，客户端零改动）；HUD 刻度改**亚像素覆盖**
  平滑滚动、高潮「中」字改**小长条**；涟漪活跃表 `Map`→`List` 实现**多波共存**（`MAX_WAVES=32` 兜底）
  （第 51 轮 / 0.0.13，详见 `TECHNICAL_SUMMARY.md` §3.14(2)(3)(4)）
- [x] ~~**立方体高亮几乎看不出变化 / 想要发光**~~：高亮改「向白插值」（额外叠加 `glow*0.4` 逐通道向白靠拢）
  + 两层 `entityTranslucentEmissive` 自发光外壳（**原版发光描边是整实体级 `OutlineBufferSource`，
  只能整只 Boss 一起描边、无法只描一颗立方体**）
  （第 51 轮 / 0.0.13，详见 `TECHNICAL_SUMMARY.md` §3.14(5)）
- [x] ~~**药水效果是否真的生效无从判断**~~：新增 `applySelfEffect`（被 `canBeAffected` / Forge
  `MobEffectEvent.Applicable` 拒绝时打 WARN，已用于 boss 自身 4 处）+ 完成药水现状审计
  （第 51 轮 / 0.0.13，详见 `TECHNICAL_SUMMARY.md` §3.14(6)）
- [x] ~~**`focus.blacklist` 在游戏内没有入口**~~：0.0.13 的结论是「`TunerConfigScreen` 顶替了 Forge 的 toml 编辑器
  ⇒ **整个 `goetytuner-common.toml` 都只能手改**」。0.0.14 已在配置界面补上**聚晶黑名单单行输入框**
  （预填当前值；点「完成」关屏或点「开始评分」时 `FOCUS_BLACKLIST.set + save` 并调新增的
  `FocusPoolManager.refreshBlacklist()` 热刷新 ⇒ **改完立即生效**，新增拉黑与取消拉黑**两向都即时**）；
  该方法**按 `namespace:path` 复用已有 `FocusEntry` 对象**，避免重扫 `new` 出新对象而打断实体侧
  **按对象身份**记录的状态（`TunerBoss.activeVisualCasts` 身份集合、`CastChannel.current`）——
  否则会出现「立方体高亮卡住 / 施法收尾回调对不上」这类隐蔽问题。
  ⚠️ 仍只是**黑名单这一个键**有入口，**其余 common 配置项依旧只能手改 toml**
  （第 52 轮 / 0.0.14，详见 `TECHNICAL_SUMMARY.md` §3.15(1)）
- [x] ~~**Boss 能被叠上抗性提升 / 伤害吸收 / 生命恢复**~~：二阶段扩展现有 `canBeAffected` 覆写
  （`addEffect` 与 `forceAddEffect` 的**第一道**判定 ⇒ 真正的免疫，不是"加完再清"），免疫
  抗性提升 / 伤害吸收 / 生命恢复 / 瞬间治疗 / 生命提升 五项，**仅二阶段**生效；
  **刻意不按"所有 beneficial"一刀切**（否则会把 Boss 自己的二阶段增益 力量 `DAMAGE_BOOST`
  与重振 `RALLYING` 一起禁掉）。新增配置 `phase2_buffs.phase2EffectImmunity`（默认 **true**，
  false = 回旧行为）；不影响玩家（第 52 轮 / 0.0.14，详见 `TECHNICAL_SUMMARY.md` §3.15(2)）
- [x] ~~**「这是个会学习的指挥家」表现力很弱 / 动态评分冲淡初始评分**~~：动态评分（实战学到的偏移）原先与静态评分
  **同权**进入轮盘权重 ⇒ 开局没打几下，**初始分类**（配置 / LLM 评分）就基本失效。0.0.16 给**动态偏移**整体乘一个
  **随该个体施法次数线性爬升的权重系数**（`FocusEntry.rouletteWeight` 新增第 4 参数 `dynamicScale`）——
  权重由 `|静态评分 + 动态偏移| + 保底基数` 改为 `|静态评分 + 动态偏移 × 系数| + 保底基数`，
  **只缩动态部分、静态评分完全不受影响**；系数存在 `FocusPoolManager` 的**每实例**字段（不是 static ——
  每只 Boss 各建一份池、`FocusEntry` 也是实例级复制 ⇒ 学习进度**天然个体私有**），由 `TunerBoss.onCastStart` 调
  `pools.noteCast()` 推进，按 `w = start + (max − start) × min(1, 施法次数 / ramp)` **线性**重算，
  每跨 10% 里程碑打一条 INFO `[Tuner] Learning weight ...`。新增 3 个配置键
  `scoring.learningWeightStart`（默认 **0.15**）/ `learningWeightMax`（默认 **1.0**）/ `learningWeightRampCasts`（默认 **60**）
  ⇒ 配置 **63 → 66 项**（`scoring` 段 **3 → 6**）；默认曲线 0 次→**0.150**、10→0.292、20→0.433、30→0.575、
  40→0.717、50→0.858、60→**1.000**（之后封顶）。防御 / 其他类走 `drawUniform()`、**本来就不走评分、不受影响**；
  瞬发 / 重音不经过 `onCastStart`、**不计入**施法次数。⚠️ `FocusEntry.getEffectiveAttackScore()` /
  `getEffectiveSurvivalScore()` **未删除**，现在是"**未缩权的诊断视角**"、不再被抽取路径调用。
  ⚠️ **手感待游戏实测验证**（本轮只做到编译通过 + `javap` 字节码核对 + jar 条目核对）
  （第 54 轮 / 0.0.16，详见 `TECHNICAL_SUMMARY.md` §3.16）
- [x] ~~**用索命聚晶打中调律师会「动画已经死了、血量却回弹」**~~：用户说「如果可以的话给索命的成功伤害直接开个后门；
  如果麻烦的话修一下动画问题」——**两个都做了**。① **索命后门**：**先实证再动手** —— 反编译 Goety 的
  `KillingSpell.SpellResult` 得调用链 `ModDamageSource.deathCurse(target)` → `MobUtil.hurtCalculation(...)` →
  `target.hurt(source, amount)`（**走 `hurt()`**，我们的覆写能看到）；确认伤害类型是 **`goety:death`**
  （`ModDamageSource` 静态初始化里 `create("death")` + jar 内 `data/goety/damage_type/death.json`，
  `message_id` = `goety.death`）；**全 jar 扫描 `deathCurse` 只有 `KillingSpell` 一处引用** ⇒ 按该伤害类型匹配
  **恰好等价于"索命聚晶"**、不会误伤别的法术。实现：新增字段 `deathCursePending` + 常量 `GOETY_DEATH_CURSE`
  （`ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("goety","death"))` —— **不需要编译期依赖
  Goety 的类**，未装 Goety 时该键永不匹配），四处联动：`hurt()` 置标记 + INFO + `return super.hurt(...)`
  （跳过身份免疫 / 宽限期免疫 / 致死截断）、`maintainDeathState()` 见标记直接 return（不回弹）、
  `canStillRevive()` 返回 false（不拦 `remove(KILLED)`）；**关键坑**：`actuallyHurt()` 的限伤 / 限DPS
  **也必须为它开口子**（`goety:death` **不在任何 `BYPASSES_*` tag 里**，不显式列出的话"等同于目标当前生命值"
  的致死伤害会被限伤削掉、后门等于失效）⇒ **索命能真正处决、无视剩余锁血档位**。新增配置
  `boss.deathCurseExecution`（默认 **true**，false = 回旧行为）；**代价**：索命对施法者反噬「目标当前生命值 **125%**」。
  ② **客户端死亡动画自愈**：根因是 `deathTime` **不是同步数据** —— 客户端的 `deathTime` 由客户端自己的
  `LivingEntity.tickDeath()` 递增、只看**客户端本地血量**；服务端复活只发**一次** `SEntityRevivePacket`，
  若那一刻客户端血量同步还没落地，客户端会在复位后**又自己把 `deathTime` 加上去** ⇒ 血量变正、动画**永久**留着
  （服务端侧"情形 A"治不了这一侧）。修法：`tick()` 的**客户端分支**加**对称自愈** ——
  `deathTime > 0 && getHealth() > 0` 即视为脏状态、直接 `resetDeathAnimation(...)`
  （该方法只在服务端发包，**客户端调用不产生网络流量**）⇒ **至多残留 1~2 tick**。
  配置 **66 → 67 项**（`boss` 段 **19 → 20**）。⚠️ **仍待游戏实测**
  （第 55 轮 / 0.0.17，详见 `TECHNICAL_SUMMARY.md` §3.17）
- [x] ~~**想要一张「调律师同款涟漪」的玩家聚晶 / 该聚晶可能读不到或与 Boss 撞车**~~：
  0.0.18 新增 **调律波纹聚晶** `goetytuner:tuner_ripple_focus`（新增类 `focus/RippleSpell`，复用 Goety `MagicFocus`）——
  基础值 **灵魂 5 / 蓄力 4 tick（0.2 s）/ 冷却 10 tick（0.5 s）**、`SpellType.NONE`、不接受附魔
  （Goety 仍会再叠施法者修正：`SoulDiscount` / `getCastingSpeed` / `getCooldownDiscount`，统一规则、**未覆写绕过**）；
  效果 = **铺垫期**的重音涟漪 + 击退。**注册次序三条约束写进 `ModItems` 类 javadoc**：
  ① 必须注册在**同一个 `ITEMS` DeferredRegister**（它在 `RegisterEvent` 落地、**远早于** `ServerStartingEvent`
  ⇒ 聚晶扫描**一定**看得到；另起一个 DeferredRegister 忘 `.register(modBus)` 就**永远看不到** ——
  本项目在**刷怪蛋**上踩过这个"注册时序坑"）；② `getSpell()` 必须**立刻**返回非 null 单例（`MagicFocus` 天然满足）；
  ③ **必须同时拉黑**（否则 Boss 白得一个 0.5 秒冷却的重音，且涟漪会把 Boss 自己推开）。
  **重音涟漪同时收敛为唯一实现**（新增类 `combat/AccentRipple`，147 行）：击退 / 粒子 / 声波 / 提示音四合一，
  `TunerBoss.accentKnockbackPulse` 改成**四行转发** ⇒ Boss 与聚晶**共用同一份代码**；差异只有
  「力量与音调」（Boss 按阶段 0.4/0.8/1.2 与 0.9/1.4/0.6；聚晶固定 0.4 / 0.9）与「聚晶额外豁免自己人
  （自己 / 宠物 / `IOwned` 仆从；Boss 传 `null`）」。⚠️ **全部待游戏实测**
  （第 56 轮 / 0.0.18）
- [x] ~~**想要调律师对应的「仆从版本」（含玩家指挥它的手段）**~~：0.0.18 新增 **调律师仆从**
  `goetytuner:tuner_servant`（新增类 `entity/TunerServant`，522 行）：`extends Goety ...ally.Summoned`
  ⇒ 一般仆从性质（主人归属 / 跟随 / 目标牵引 / 加血传送 / 日照规则）**全部继承**；复用同一
  `FocusPoolManager`（每仆从一份实例）/ `CastChannel` / 模型贴图，**没有**音乐阶段、锁血、瞬移、嘲讽、二阶段；
  **只用刷怪蛋 / 指令召唤**（不做 summon 聚晶）、**复用 Boss 的聚晶池 + 轮盘赌**（两条均为用户确认的范围）。
  为复用做了三处**泛型化**：`TunerModel<T extends LivingEntity>`、`TunerCapeLayer<T>`、
  `TunerOrbLayer<T extends LivingEntity & OrbHighlightSource>`（新增 `entity/OrbHighlightSource` 接口
  ⇒ 渲染层不再依赖具体实体类），新增唯一的 `client/render/TunerServantRenderer`。
  **聚晶指令**（新增类 `entity/TunerServantInteractions`，FORGE 总线）：手持 `IFocus` **左键 = 切换「不释放」**、
  **右键 = 切换「优先释放」**；左键 `setCanceled(true)`、右键置 `SUCCESS`；**客户端一律放行、只在服务端判定**
  （`javap` 实证 `Player.attack` **偏移 0** 即 `ForgeHooks.onPlayerAttackTarget`，客户端取消会让 `LocalPlayer`
  **连攻击包都不发**）。指令存 `FocusPoolManager` **实例字段**（非 static）+ NBT
  （`DisabledFoci` / `PriorityFoci`）；优先聚晶走新重载 `CastChannel.beginCast(level, entry, mult)`
  **插队**（跳过抽签、其余流程共用 `startCast`）；**无主仆从第一次被下达指令时认主**（否则只用刷怪蛋获取会让
  机制完全不可达）。⚠️ 仆从**刻意不调 `noteCast()`** ⇒ 不参与 0.0.16「逐渐学习」、永远以初始分类为准
  （别误判成 bug）。⚠️ **全部待游戏实测**（第 56 轮 / 0.0.18）
- [x] ~~**联机协议与涟漪载荷的结构性缺陷**~~：`SAccentWavePacket` 载荷由「实体 id」改为
  **「三个 double 的世界坐标」**（通道 id 仍 3）—— **玩家放的涟漪锚点是裸坐标、没有实体可查**，而客户端原先靠
  `level.getEntity(id) instanceof TunerBoss` 反查位置；`AccentWaveRenderer.trigger(int)` 相应改成
  `trigger(double x, double y, double z)`，广播由 `TRACKING_ENTITY` 改为「**锚点 64 格内按玩家广播**」；
  协议 **`2.0` → `2.1`**（**同 id 不同结构会解码错位**）⇒ 联机双方必须同为 0.0.18+。
  ⚠️ **联机与观感均待游戏实测**（第 56 轮 / 0.0.18）
- [x] ~~**长按持续释放类聚晶只放一瞬间就停**~~（用户第 4 条反馈，**本轮最有价值的一处修复**）：
  **两层根因**——① `CastChannel` 原先只在前摇结束时调**一次** `SpellResult`（玩家路径是按法术自己的
  `Cooldown` 反复调 `MagicResults`）；② `AbstractBeam.tick()` 靠 `MobUtil.isSpellCasting(owner)` 判定存活
  （= `isUsingItem() && 用物是 IWand && 杖里有聚晶`），而 **Mob 从不 `startUsingItem`** ⇒ 光束刚生成就被丢弃。
  修法：`CastChannel` 新增 **`tickChannel`**（`startUsingItem` + 每 tick 自愈重设 + `finishCast`/`interrupt`/
  `startSpell` 异常/**外层兜底 catch** 四处一律 `stopChannelUse`，并按 `Cooldown`/`shotsNumber` 反复释放；
  判定依据 Goety 自己的 `IChargingSpell`、**不写死聚晶 id** ⇒ 附属同类法术自动受益）
  + **自备 `focus/TunerWand`**（`onUseTick` **空实现** —— 用 `dark_wand` 会让 `DarkWand.onUseTick` 对非玩家
  走 `failParticles + FIRE_EXTINGUISH`：**不放法术、只冒白烟响灭火音**）+ 用时上限
  `casting.channelMaxTicks`（默认 20）。**顺带落地了遗留待办「C Boss专属魔杖」**。
  ⚠️ **表现待游戏实测**（第 57 轮 / 0.0.19，详见 `TECHNICAL_SUMMARY.md` §3.19(1)）
- [x] ~~**聚晶指令「左键『不释放』似乎不能取消」**~~（用户第 1 条反馈，**修的是设计漏洞**）：
  0.0.18 的「不释放」与「优先」是**两张互不相干的表**、同一聚晶可同时命中两者（"不释放"胜出）
  ⇒「先设优先 → 再设不释放 → 又按右键想取消」会**按了没反应**。现在两状态**互斥**（左键顺手清优先 /
  右键顺手清不释放），任何一键都能把聚晶**切回中立**；另加**潜行 + 左键 = 清空全部指令**；提示文案带
  两张表条目数。⚠️ 本轮**未进游戏实测**，"原来为什么不能取消"只剩**语义解释、没有复现证据**
  （第 57 轮 / 0.0.19，详见 `TECHNICAL_SUMMARY.md` §3.19）
- [x] ~~**仆从与本体机制不一致（血量 / 护甲 / 减伤限伤）**~~（用户第 2 条反馈）：
  血量护甲改读 `boss.maxHealth`(216) / `boss.equivalentArmor`(16)、`KNOCKBACK_RESISTANCE` 1.0，
  并把 Boss 内联的三段判定抽成**共用实现** **`combat/TunerDamageRules`**（身份免疫 / 近战易伤）+
  **`combat/DamageThrottle`**（限伤 / 滑动 1 秒限DPS）⇒ Boss 与仆从**共用一份代码**（Boss 侧行为不变、
  只是搬家）；`servant.health` 删除。⚠️ **刻意不含锁血阶梯**（仆从若也锁血就成了打不死的怪）。
  ⚠️ **仆从是否真的变肉待实测**（第 57 轮 / 0.0.19）
- [x] ~~**仆从归属语义（"第一次下指令就认主"不合理）**~~（用户第 3 条反馈）：
  新增 `init/TunerServantSpawnEggItem`（**继承 Goety 的 `ServantSpawnEggItem`**）⇒ **直接放 = 野生 /
  潜行放 = 认主**（+ 动作栏提示 + tooltip）；**归属只在放置时确定**，0.0.18 的 `adoptOwnerIfUnowned`
  **已彻底删除**；野生 / 他人仆从被手持聚晶左键或右键时**只给提示、不吞掉**攻击与交互。
  ⚠️ **待游戏实测**（第 57 轮 / 0.0.19）
- [x] ~~**聚律波纹聚晶贴图配色（要灰黑 + 白）**~~（用户第 5 条反馈）：
  `art/gen_ripple_focus_icon.py` 配色改为近黑轮廓 `(8,8,8)` + 深灰→近黑渐变 `(58→22)` + 白环 `(240)` +
  纯白圆心 `(255)`（去掉青色渐变）；**694 → 627 B**、仍 16×16 RGBA、脚本**字节可复现**。
  ⚠️ **观感待人类过目**（作者 agent 无图像输入能力）（第 57 轮 / 0.0.19）

### 仍待办
1. **A3 剩余粒子**：传送 / 净化环 / 二阶段碎裂+天空盒 / 施法前摇聚能
2. ~~**C Boss专属魔杖**：`TunerWand` 代码可先做，贴图后补~~
   ✅ **已完成（0.0.19 / 第 57 轮）**：`focus/TunerWand` + `models/item/tuner_wand.json`（模型 `parent`
   指向 `goety:item/dark_wand` ⇒ **外观与占位期完全一致**）。它最初是为修「长按类法术只放一瞬间」而做的
   （需要一把 `onUseTick` **空实现**的 IWand 让 Mob 能"真的在使用法杖"），顺带把这条待办一起落地：
   Boss 与调律师仆从的主手都从 `goety:dark_wand` 换成 `goetytuner:tuner_wand`，并在
   `readAdditionalSaveData` 做**旧档迁移**。⚠️ 注意它是**配发给 AI 的**，玩家手持右键只返回 `PASS`
3. **D 附属兼容与性能测试**：安装其他 Goety 附属联合测试
4. **E3 竞态实测**：三通道共享主手杖换装竞态
5. **E4 / E5**：DoT 伤害归因、召唤物 owner 识别
6. **E7 音效**：死亡/受击/环境音效
7. **A4 护甲显示确认**：确认与其他护甲显示 mod 无冲突
8. **美术/表现项游戏验收**：0.0.10 美术项（披风摆动、三颗立方体轨道与高潮并行高亮、透明声波与地形/水面/着色器交互、原版刷怪蛋观感）；
   以及 **0.0.12 的新外观**（音乐条 204×8 混色/柔和投影/像素阶段符号、涟漪锚定触发点坐标的观感）
   ——离线校验与编译/字节码核对**不能替代画面验收**
9. **0.0.13 待验收**：音乐丢失修复需**真机复现路径**（被击杀 → 在死亡界面停留不同时长 → 复活 → 未脱战时音乐应自动恢复）、
   音乐条**亚像素刻度**的平滑观感、涟漪**多波共存**的层叠外扩观感、立方体高亮「变白变亮」与自发光外壳的强度、
   `music.accentDensityDivisor=3` 下的重音密度手感（太密/太疏都调这一项）
10. ~~**`focus.blacklist` 的可访问性（0.0.13 结论）**：`TunerConfigScreen` 顶替了 Forge 的 toml 编辑器 ⇒
    **整个 `goetytuner-common.toml` 都只能手改**。是否补一个通用 toml 入口、或在配置屏里加黑名单编辑框，
    待决策（短期内"把聚晶加入黑名单"必须靠手改文件）~~
    ✅ **已完成（0.0.14 / 第 52 轮）**：配置屏里加了**聚晶黑名单输入框**（点「完成」关屏或点「开始评分」即保存 +
    `FocusPoolManager.refreshBlacklist()` 热刷新，改完立即生效）。**仍待决策的只剩**：「是否需要再补一个
    **通用 toml 入口**」——其余 common 配置项（`boss.*` / `music.*` / `casting.*` …）依旧只能手改文件
11. **药水效果现状（0.0.13 审计结论）**：一阶段低谷的 `SAPPED`/`DARKNESS` 与仆从四效果**机制上生效**，
    但都构造为 `visible=false`（**无粒子**）⇒ **玩家看不出效果**；`tickPhase2Buffs` **零日志** ⇒
    二阶段自施药水的数值收益只能进游戏实测。是否补粒子 / 加日志属**表现层待决策项**（不是 bug）
12. **0.0.14 待验收**：① 配置界面的**聚晶黑名单输入框**（排版 / 输入 / 保存提示 / 状态行文案，以及
    "点完成关屏保存"与"点开始评分顺带保存"两条路径）；② 改完黑名单后**取消拉黑是否真的立即回到池里**
    （`refreshBlacklist()` 的重扫 + 对象复用路径）；③ 二阶段免疫**实际生效**（给 Boss 丢抗性提升/生命恢复
    应完全不上身；配置改 `false` 后应恢复可施加）
13. **0.0.17 待验收**：① **索命后门**（`boss.deathCurseExecution`，默认 true）—— 战斗中索命聚晶命中 Boss
    应**真正处决**（日志出现 `Death-curse backdoor (goety:death)`），不再"动画已死、血量回弹"、
    也不再"血量不为 0 却躺着"；**边界**：索命**未命中**（被闪避/免伤/被其它模组取消）时 Boss 状态应正常
    （`deathCursePending` 标记被清除，**不会**从此对任何致死伤害都免疫）；**对照**：把该键改成 `false` 重启
    应回到旧行为。② **客户端死亡动画自愈**（无开关、始终生效）—— 任何"血量 > 0 但 `deathTime > 0`"的客户端
    残留应**至多 1~2 tick** 内自动复位（走 DEBUG 日志）。本轮只做到**编译通过 + jar 条目核对**，**均未进游戏实测**。
    另需实测确认**代价侧**：索命对施法者反噬「目标当前生命值 125%」是否让玩家自己也被打死（设计内代价，非 bug）。
    详见 `TECHNICAL_SUMMARY.md` §3.17
14. **0.0.18 待验收（本轮全部运行时表现均未实测）**：① **调律波纹聚晶**（`goetytuner:tuner_ripple_focus`）——
    灵魂 5 / 蓄力 0.2 s / 冷却 0.5 s 的**实际手感**（⚠️ Goety 还会叠施法者修正，故实测值会高于基础值）、
    击退范围与强度是否与 Boss 的**铺垫期**涟漪一致、是否**豁免自己人**（自己 / 宠物 / `IOwned` 仆从）、
    是否**尊重** `music.accentParticles` / `accentWave` / `accentSound` 三个开关；② **重音涟漪共用实现**
    是否让 Boss 的原有表现**完全不变**（`accentKnockbackPulse` 只是四行转发 —— 本项是**回归重点**）；
    ③ **仆从** —— 刷怪蛋 / 指令召唤是否正常、AI（跟随 / 索敌 / 施法轮换 `servant.rotation` = "23"）是否合理、
    **被清空 / 主人死亡 / 换维度**等边界、免疫 `GoetyEffects.SUMMON_DOWN` 与主手杖不掉落是否生效；
    ④ **聚晶指令** —— 左键（不释放）/ 右键（优先释放）的切换与提示文案、**左键不再顺手揍仆从**、
    指令是否随 NBT 持久化、**优先聚晶是否真的插队**、**无主仆从的认主**是否只发生在第一次指令、
    **别人家的仆从是否完全不受影响**；⑤ **联机协议 2.1** —— 双方版本不一致应被拒绝连接、
    双方均为 0.0.18 时涟漪与仆从同步是否正常；⑥ **贴图观感** —— `tuner_ripple_focus.png`
    （16×16 RGBA / 694 B / **程序化生成**）与仆从实体（复用 Boss 的模型 / 贴图 / 渲染层）的实际观感。
    ⚠️ 本轮只做到**编译通过 + jar 条目核对 + 原始字节串核对 + `javap`（SRG 覆写与两个交互钩子位置）
    + 配置 / 资源核对**；⚠️ 作者 agent **不具备图像输入能力、从未看过该图标**
    （`art/preview_ascii.py` 是唯一可用的检视手段，输出只是 ASCII 灰度近似）。
    **⇒ 以上 ①~⑥ 全部仍待游戏实测。**
    ⚠️ **0.0.19 更新（本条有两项已被推翻，按 0.0.19 的行为验收即可）**：
    ④ 里的「**无主仆从的认主**是否只发生在第一次指令」**已作废** —— 0.0.19 起**归属只在刷怪蛋放置时确定**
    （直接放 = 野生 / 潜行放 = 认主），"第一次下指令认主"已彻底删除；④ 的指令语义也改为**互斥 + 可清空**。
    ⑥ 里的图标已**重新生成为灰黑 + 白**（**694 → 627 B**），不再是紫色系。
15. **0.0.19 待验收（本轮五条修复的运行时表现**全部**未实测）**：① **长按类聚晶是否真的持续释放**（本轮最有价值的一处）
    —— 让 Boss（或仆从）抽到/装备 **腐化 / 震撼 / 炼狱 / 暴雪 / 轰炸 / 旋风 / 箭雨** 一类聚晶，观察是否
    **持续放出**（腐化光束应是一条**跟着走、持续存在**的光束，而不是"闪一下"）；同时确认 **1 秒上限生效**
    （`casting.channelMaxTicks` 默认 20 ⇒ 到点必然收手，**不会停不下来**），以及**收手后光束消失**
    （异常路径也已补 `stopChannelUse`，不会永久"使用中"）；**普通法术（非充能类）表现应与 0.0.18 完全一致**（回归重点）。
    ⚠️ **平衡感受**：腐化光束**每 tick 造成伤害**，上限 20 已能打出很高总伤害、**调到 100 以上基本等于必杀**。
    ② **`TunerWand` 外观与旧档迁移** —— Boss / 仆从手里的杖**外观应与之前的 `goety:dark_wand` 完全一样**；
    读一个 0.0.18 的老存档，主手应被**自动换成 `goetytuner:tuner_wand`**；玩家手持它右键**不应有任何施法反应**。
    ③ **聚晶指令语义**（用户第 1 条）—— 左键 / 右键**各自都能把聚晶切回中立**（先设优先再设不释放，然后
    **按右键应能取消**）、**潜行 + 左键清空全部指令**、提示文案里的**条目数**与实际相符；
    ⚠️ 本轮**无复现证据**，请重点确认"原来那种按了没反应"是否真的不再出现。
    ④ **仆从是否真的变肉**（用户第 2 条）—— 仆从应有 **216 血 + 等效护甲 16 + 近战易伤 +25% + 限伤 25%**；
    与 Boss 并排挨同一下攻击对比掉血量；⚠️ 确认**仆从不会被 `/kill` 之外的东西"锁血"**（刻意不含锁血阶梯），
    也确认 **`/kill` 能正常击杀仆从**（无后门豁免）。
    ⑤ **仆从刷怪蛋归属**（用户第 3 条）—— **直接放 = 野生**（不跟随任何人、有提示）、**潜行放 = 认主**
    （跟随并接受聚晶指令、有提示）；**野生仆从与别人家的仆从**被手持聚晶左键 / 右键时**只给提示、
    且这次攻击 / 交互照常生效**（不吞掉）；tooltip 多一行本模组说明。
    ⑥ **图标观感**（用户第 5 条）—— `tuner_ripple_focus.png`（16×16 RGBA / **627 B** / 程序化生成）
    是否确实是"灰黑 + 白"、是否好看。**⚠️ 纯人眼判断**：作者 agent **不具备图像输入能力、从未看过它**
    （`art/preview_ascii.py` 只是 ASCII 灰度近似）；不满意可直接改 `art/gen_ripple_focus_icon.py` 的参数重跑。
    ⑦ **回归** —— 确认 0.0.18 的波纹聚晶、重音涟漪四件事、仆从的跟随 / 索敌 / 轮换、联机协议 **2.1** 全部照旧
    （0.0.19 **未改动协议**；但与 0.0.18 同协议号，**建议双方同为 0.0.19**）。
16. **0.0.19 修补（第 58 轮）待验收 —— 三条修复的运行时表现全部未实测**：
    ① **「不释放」是否终于起效**（用户第 2 条，**最重要的一条**）—— ⚠️ **先确认游戏已重启**（第 58 轮的修复
    只在**新 jar** 里；部署时游戏正在运行，运行中的实例仍是旧字节码）；然后：**直接放**一只仆从（= 野生）
    → **手持任意聚晶左键点它** → 该聚晶应变成"不释放"（★本轮把"野生仆从被拒"这条堵掉了，这正是原来
    "完全不起效"最可能的真凶）；**再左键一次**应切回中立；**右键** = 优先释放、再右键切回中立；
    **潜行 + 左键现在不再有任何特殊行为**（原来的"清空全部指令"已删）；
    **只有别人家的仆从**才会被拒绝 —— 此时应在**日志**里看到 `Servant focus command refused`，
    **动作栏不应再出现任何文字**（本轮把提示全删了）。
    ② **箭雨 / 长按类聚晶的持续时间**（用户第 3 条）—— 让 Boss（或仆从）抽到 / 装备 **箭雨**：
    应能看到**先蓄力约 1 秒、然后持续放约 1 秒**（共 ~40 tick，**不再是放一发就停**）；
    **腐化光束**应是**一条跟着走、持续存在约 1 秒**的光束；**震撼 / 炼狱**同理。
    ⚠️ 若仍觉得"持续太短" ⇒ 调大 `casting.channelMaxTicks`（**只影响持续段、不含蓄力**）；
    ⚠️ **平衡**：腐化光束每 tick 造成伤害，**调到 100 以上基本等于必杀**。
    ③ **提示精简**（用户第 1 条）—— 刷怪蛋的物品说明**只剩一行**"调律师仆从刷怪蛋，潜行使用会生成你自己的仆从."；
    **放置时不再有动作栏提示**、**左键 / 右键仆从时不再有动作栏提示**（这些是本轮**故意删掉**的，
    不是 bug）；`/give` 拿到蛋时也不应再看到 Goety 原本那句通用说明（`appendHoverText` 不再调 `super`）。
    ⚠️ **本条 ③ 已被第 59 轮部分推翻**：用户随后指出"左键和右键功能都没有提示了……这个提示是需要的"
    ⇒ **左右键的指令结果提示已恢复**（物品介绍与放置提示仍然不要），**以第 17 条为准**。
    ④ **回归** —— 「潜行 + 左键清空全部指令」**已删除**（这是有意的）；被拒绝的交互**仍然照常生效**（不吞掉攻击）；
    野生 / 认主的归属仍**只在放置时**确定；协议仍 **2.1**；普通（非长按类）法术表现应与 0.0.18 完全一致。
    ⚠️ 本轮只做到**编译通过（约 1 分钟、无新增警告）+ jar 条目核对（112 → 117）+ 原始字节串核对
    + `javap`（SRG 覆写：`m_5929_`/`m_8105_`/`m_7203_`/`m_6469_`/`m_6475_`/`m_7301_`，以及
    `CastChannel.tickChannel` 里的 `m_6117_()`/`m_6672_()`/`m_5810_()`）+ 配置 / 资源核对
    + 部署产物逐字节比对**。**⇒ 以上 ①~⑦ 全部仍待游戏实测。**
17. **0.0.20（第 59 轮）待验收 —— 四条内容的运行时表现全部未实测**：
    ⚠️ **先完全重启游戏**（新 jar 换了文件名 `0.0.19 → 0.0.20`；部署时游戏正在运行）。
    ① **左右键提示是否回来了（最优先；这正是本轮修的那条回归）** —— 手持**单个聚晶**左键仆从 ⇒
    屏幕上方出现 `不释放：<聚晶名>`；**再左键** ⇒ `已取消不释放：<聚晶名>`；右键 ⇒ `优先释放：<聚晶名>`、
    再右键 ⇒ `已取消优先释放：<聚晶名>`；对**别人家的仆从** ⇒ 红色 `这不是你的调律师仆从`
    （且这次攻击 / 交互**照常生效**）。⚠️ 名字应当是**中文聚晶名**、不是 `goety:xxx_focus` 这种 id。
    ⚠️ 同时确认**没回退过头**：刷怪蛋**放置时仍然不弹提示**、物品说明仍然**只有一行**。
    ② **聚晶包 / 多晶大袋批量指令** —— 往袋里塞几颗不同聚晶（再混点别的物品，应当被跳过）⇒
    **左键** ⇒ `不释放：N 个聚晶（聚晶包）` + 日志里 N 条 `Servant focus <id> -> DISABLED`；
    **再左键** ⇒ 整体清除；右键 ⇒ 优先释放、再右键 ⇒ 整体清除。
    ⚠️ **空袋子**左键 / 右键 ⇒ **不应有任何反应**，且这次攻击 / 交互**照常生效**（不吞掉）。
    ③ **仪式召唤仆从** —— 基座摆 **紫水晶碎片 ×4 + 红石 + 钻石 + 金锭 + 青金石**，
    **中心放一把带「调律加成」的法杖**（打一次调律师拿到的那把；**两种调律加成任一 > 0** 即认），用任意法杖右键祭坛启动；
    应消耗 **100 灵魂**（每秒 10、共 10 秒；不足会走 Goety 自己的"没有足够的灵魂能量"），
    完成后**仆从认主**且**中心那把杖被消耗**。
    ⚠️ **反向验证**：中心放**没有调律加成**的普通法杖 ⇒ **仪式不成立**（Goety 会给"无效的仪式"），这是有意的。
    ⚠️ 若仪式不启动，先看是不是已经带了很多 Goety 仆从 —— `RitualRequirements.canSummon` 有**全局仆从上限**。
    ④ **杖的加成 → 仆从强度** —— ⚠️ **判定用「调律·巫法加成」**（0.0.20 按你的反馈改的，不是魔法伤害加成）：默认 **+10%/次** ⇒ 打 **1** 次 ⇒ 只有**强健 Ⅰ**；**3** 次 ⇒ **强健 Ⅱ + 生命恢复 Ⅰ**；**7** 次 ⇒ **强健 Ⅳ + 生命恢复 Ⅱ**；**9** 次 ⇒ **强健 Ⅴ + 生命恢复 Ⅱ + 抗性提升 Ⅰ**；**11** 次 ⇒ **抗性提升 Ⅱ（封顶）**。
    若有 **+90%** 的杖 ⇒ 应为**强健 Ⅴ + 生命恢复 Ⅱ + 抗性提升 Ⅰ**。
    ⚠️ **必须知道的坑**：**强健（`goety:buff`）只加近战攻击伤害，而仆从没有近战手段**
    ⇒ 它在**战斗力上几乎不产生差异**（HUD / 属性面板会显示）。要"真的更强"就得让我把强健等级
    同时换算成**法术强度**（`ModAttributes.SPELL_POTENCY`）—— **等你的决定**，我没自作主张加。
    ⚠️ **生命恢复 / 抗性提升是真生效的**，可以挨打对比（抗性 Ⅱ 的减伤很明显）。
    ⚠️ **刷怪蛋 / `/summon` 出来的仆从不应有任何增益**（没有召唤用杖），这是有意的。
    ⑤ **仆从死亡掉落召唤用杖** —— 把仪式召唤的仆从打死 ⇒ 地上出现**召唤时那把法杖**，
    **附魔 / 聚晶 / 原有的两行「调律:…」全在**（= 原样返回、**不再额外升级**，与 Boss 掉落刻意不同）；
    日志应有 `Tuner servant died, returned its summoning wand: <uuid> (<name>)`。
    ⚠️ **刷怪蛋召唤的仆从死亡不应掉杖**；⚠️ 主手那把 `goetytuner:tuner_wand` **任何情况下都不应掉落**。
    ⑥ **「强健」等级 → 法术强度**（追加项）—— 仪式召唤一只仆从（它的强健来自法杖），
    用 `/attribute` 看它的 `goety:spell_potency`：应当等于 **强健等级 × `casting.buffSpellPowerPerLevel`（默认 1）**
    （例：强健 Ⅱ ⇒ 2.0），并且**它放的聚晶伤害确实更高**；
    ⚠️ 把 `buffSpellPowerPerLevel` 设 0 ⇒ modifier 应被**移除**（属性回到 0）；
    ⚠️ **本体目前没有强健 ⇒ 它的属性应当一直是 0**（这是**预期**，不是 bug —— 见待办里的"两处死代码"）；
    ⚠️ **顺带可验的两个既有问题**：玩家手持带「调律:巫法加成」的杖时，`spell_potency` **仍然是 0**
    （所以那行 tooltip 目前是"说了不做"）；Boss 进二阶段后同样**不会**涨。
    ⑦ **两处百分比加成是否真的生效**（追加项）—— 玩家手持带「调律:巫法加成」的杖，
    打同一个目标对比伤害：应当**确实高 10%**（满配杖 = 巫法 10% + 魔法 40% ⇒ ×1.50）；
    ⚠️ `/attribute <玩家> goety:spell_potency get` **仍是 0 是对的**（百分比走伤害事件、不走属性）；
    让 Boss 进二阶段：它的法术伤害应当**确实高 20%/50%**（力量 2/5 级）；
    ⚠️ 顺带确认 boss 的腐化光束 / 火球这类**原来判定漏掉**的法术现在也在加成范围内；
    ⚠️ 用索命聚晶时，**施法者自己的反噬不应被加成放大**。
    ⑧ **强健等级与仆从限伤**（追加项）——
    仆从的强健应当显示为 `强健 X`（原版只在等级 II~X 显示数字，超过就只写「强健」，
    所以等级上限默认钉在 10）；**真实等级看日志**
    `[Tuner] Servant ... blessing from wand '...': bonus <X>% -> Buff <n> / Regen <r> / Resistance <s>`，
    召唤时还应有一行 `[Tuner] Servant summon: captured wand '...' (witchcraft=+X%, magic=+Y%)`；
    ⚠️ 用高爆发武器打仆从应当明显"打不动"（单次 ≤32 点、每秒 ≤108 点）；
    ⚠️ 但 `/kill` 仍然应该能直接杀死它。
    ⑨ **回归** —— 单体指令语义（互斥 + 可撤销）与 0.0.19 一致；刷怪蛋"直接放 = 野生 / 潜行放 = 认主"不变、
    **野生仆从仍可被任何玩家配置**；仆从仍是 216 血 / 护甲 16；联机协议仍 **2.1**（双方都要 0.0.20）。

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

### 第 51 轮（0.0.13）
- **本轮范围**：改动 **7 个 Java 文件** + 版本号，**只改代码、不动任何贴图/资源**；**新增 1 个配置项**
  `music.accentDensityDivisor` ⇒ 配置 **61 → 62 项**（`music` 段 **12 → 13**）；`.java` 文件数仍 **45**、
  jar 条目仍 **102**（无新增/删除类，只是既有类被改写）。实测行数：`TunerBoss.java` **2020 行**、
  `MusicBarHud.java` **463 行**、`BossMusicManager.java` **181 行**、`AccentWaveRenderer.java` **138 行**、
  `MusicController.java` **319 行**、`TunerOrbLayer.java` **83 行**、`TunerCommonConfig.java` **428 行**。
- **① 修「玩家被击杀复活后（未走出索敌范围）背景音乐丢失」（`BossMusicManager`）**：
  - 症状：玩家被击杀 → 复活、且**没走出索敌范围**时，Boss 背景音乐**有时**再也不响；用户形容为「整个 BGM 都没了」。
  - **根因**：起播唯一入口是 `if (music == null) startMusic()`，而"是否在播"**只**记录为静态字段 `music != null`、
    **从不与声音引擎核对**。死亡/复活时引擎会把循环实例**悄悄摘除**，已知四条通道：`RECORDS` 音量为 0、
    channel 停止、`SoundEngine.reload()→destroy()→stopAll()`、`play()` 在未 loaded 时静默返回。
    字段仍非 null ⇒ 只要服务端 `playing` 一直为 true（**没脱战**），`startMusic` **永不重入** = **永久静音**。
  - **症状被放大**：`onPlaySound` 的判据同样只是 `music != null` ⇒ 假死状态下把原版背景音乐
    （含 `MusicManager` 延迟调度的曲目）**一并永久取消**，这才是"整个 BGM 都没了"。
  - **修法**：每 tick 用 **`SoundManager.isActive(music)`**（1.20.1 自带 API，反编译确认存在）核实实例**真的在响**；
    失效即清空字段、下一 tick 自动重建（**自愈**，覆盖上述全部通道，也覆盖 `/stopsound`、切音频设备等）；
    加 **10 tick 防抖**避免"播不起来 → 每 tick 重建"的抖动。`onPlaySound` 判据同步改为
    `music != null && isActive(music)`。
  - **调查中另确认（三条实证）**：**死亡本身不停声音**（`DeathScreen` 无任何 soundManager 调用）；
    **同维度复活不替换 `ClientLevel`**（`handleRespawn` 仅**换维度**才 `setLevel`，不能把问题归因于"世界被换掉"）；
    「有时」取决于 `playing=false` 是否越过 `STOP_GRACE_TICKS(30t)` 与 `SYNC_TIMEOUT_MS(3000ms)` 两条计时线
    ——**单人死亡界面会暂停集成服务器**，死亡停留时长不同就走不同分支。
- **② 重音标记滚动平滑 + 高潮改细小长条（`MusicBarHud`）**：
  - **不平滑的根因**：`fill()` **只能落在整数像素**，而刻度是 1~2px 细条，位置取整后**逐像素跳动**。
  - **改法**：**亚像素覆盖** —— 小数部分按比例分摊到相邻两列（`x=10.3` ⇒ 第 10 列 70% 透明度、第 11 列 30%），
    亮度重心随浮点位置**连续移动** ⇒ 滚动平滑；调用点不再预先取整。
  - **高潮标记**：原「中」字（贯通竖线 + 空心方框，8px 条高上又粗又花）改为**小长条**：
    高潮 **2×6** 最亮 / 低谷 **1×6** / 铺垫 **1×4**，全部**竖向居中**，只用「宽 × 高 × 亮度」区分强度。
- **③ 涟漪多波共存（`AccentWaveRenderer`）**：0.0.12 用 `Map<entityId, Wave>`，语义是"重复触发**覆盖**旧的"，
  而二阶段进场重音**每 5 tick 一发、连发多次** ⇒ 后一发**顶掉**前一发，玩家只看到**一条波反复重播**；
  改为 `List<Wave>`：每发各成一条波、各自计时，呈现**层叠外扩**；加 **`MAX_WAVES = 32`** 硬上限兜底
  （单发波活 34 tick、最快 5 tick 一发 ⇒ 同时最多约 7 条，上限只作防御）。
- **④ 重音数量与频率降为原来的 1/3（新配置 `music.accentDensityDivisor`）**：`IntValue`，**默认 3**、范围 1~9，
  从乐谱重音表"**每 N 个保留 1 个**"。抽稀在**服务端加载乐谱时**统一进行（`MusicController.loadScore()`）
  ⇒ HUD 刻度（经 `SMusicSyncPacket` **全量同步**）、击退、涟漪、提示音**一起**变稀疏，**客户端无需任何改动**，
  也不会出现"HUD 密、实际触发疏"的不一致。加载时打 INFO `Accent thinning x3: 37 -> 13 accents`
  （⚠️ **乐谱文件仍 37 个、运行时 13 个，不回写文件**——两个数字都对，引用时注明口径）。
  配置总数 **61 → 62**（`music` 段 **12 → 13**）。
- **⑤ 小立方体高亮更明显 + 发光（`TunerOrbLayer`）**：高亮原为 `min(1, color * (1 + glow*0.8))`，
  而红/蓝/灰的分量本就接近 1（红 = 1.0/0.231/0.188）⇒ 乘法被 `min(1, …)` 截断后**几乎看不出变化**；
  改为额外叠加 `glow * 0.4` **逐通道向白靠拢** ⇒ 高亮时明显"变白变亮"。
  「发光」用**两层自发光外壳**（1.30×/alpha 0.36、再叠 1.35×/alpha 0.18，都乘 `glow`），渲染类型
  **`RenderType.entityTranslucentEmissive`**（着色器忽略光照 ⇒ 稳定发亮；**已实证它与 `ENTITY_TRANSLUCENT`
  同理无条件 `NO_CULL`**，放大后的壳无背面剔除空洞）。
  **为何不用原版发光描边**：MC 的「发光」是**整实体级**的 framebuffer 后处理（`OutlineBufferSource`），
  **只能整只 Boss 一起描边、无法只描一颗立方体**；外壳方案能精确地只让"高亮的那一颗"发光（代价是 2 个额外 draw call）。
- **⑥ 药水效果可观测性（`TunerBoss`）**：审计发现全类 **8 处** `addEffect(...)` 的 **boolean 返回值全被丢弃**，
  而 `canBeAffected` 与 Forge 的 `MobEffectEvent.Applicable` 都在这一步否掉 ⇒ 效果被拒时**静默失效、日志无一字**。
  新增私有 `applySelfEffect(...)`：被拒时打 WARN `[Tuner] addEffect rejected: <effect> amp=N`，
  已用于 boss 自身 4 处（低谷 `SAPPED`/`DARKNESS`、二阶段 `DAMAGE_BOOST`/`RALLYING`）。
- **药水现状审计结论（详见 `TECHNICAL_SUMMARY.md` §3.14(6)）**：
  - 一阶段低谷：boss 自施 `GoetyEffects.SAPPED`(amp 11) + `MobEffects.DARKNESS`，每 tick 刷新=常驻，**机制上生效**；
    但两者都 `visible=false`（**无粒子**）⇒ **玩家看不出效果**。仆从四效果（REGEN/ABSORPTION/SLOW/WEAKNESS）同样只在一阶段低谷。
  - **二阶段不跑 `tickValley`**：`MusicController` 把二阶段 VALLEY 段重映射为 BUILDUP，且二阶段**清空仆从**
    ⇒ 上述低谷效果**仅一阶段**。
  - 二阶段自施 `DAMAGE_BOOST` + `RALLYING`（每 40 tick 一次、持续 60 tick）**可达**、**不会被 `cleanseEffects` 抹掉**、
    也**不受亡灵免疫影响**（`TunerBoss` **未覆写 `getMobType`**，为 `UNDEFINED` 而非 `UNDEAD`）；
    `RALLYING` 按定义**只加近战**攻击、对法系无感，故另挂 `SPELL_POTENCY` 属性 modifier（值 = `strengthLevel*0.1`）。
  - **`SUMMON_DOWN` 免疫确认真实生效**：反编译 Goety 的 `ISummonSpell`，施加上走 `LivingEntity.addEffect` →
    `canBeAffected` → 被本类拒绝 ⇒ `hasSummonDown` **恒 false**。
  - **局限**：`tickPhase2Buffs` 自身**零日志** ⇒ 二阶段自施药水的**实际数值收益只能进游戏实测**
    （新增的 WARN 只能证明"没被拒"，不能证明"数值符合预期"）。
- **⑦ 索命聚晶加入黑名单（配置侧，不在 jar 内）**：`focus.blacklist` 改为
  `goetytwilight:destruction_focus, goety:killing_focus`。索命聚晶 = **`goety:killing_focus`**
  （**五重核实**：翻译键 `item.goety.killing_focus`=索命聚晶、注册代码 `ModItems.KILLING_FOCUS`、物品模型、
  合成配方、手册条目）；本模组把它评为 `attack` / attackScore 0 ⇒ 轮盘**基础权重 2.0**，**确实会被抽到**，
  且该法术会对施法者**反噬 125%**。
  ⚠️ **重要可访问性结论**：`focus.blacklist` **无法在游戏内配置界面修改** —— 本模组的 `TunerConfigScreen`
  **顶替**了 Forge 默认的 toml 编辑器，而该界面只有 LLM API Key 与提示词两个输入框 ⇒
  **整个 `goetytuner-common.toml` 的所有项都只能手改文件**。解析规则：英文逗号分隔、逐段 trim、**大小写敏感**
  （`parseBlacklist`）。另：`RUNTIME_BLACKLIST`（运行期自愈拉黑）与配置黑名单是**并集** —— 配置**无法解禁**
  一个已被运行期拉黑的聚晶。
  ✅ **0.0.14 更新（历史条目）**：配置界面已补上**聚晶黑名单输入框**，`focus.blacklist` **不再只能手改文件**；
  **其余** common 配置项仍只能手改 toml。见下一节「第 52 轮（0.0.14）」与 `TECHNICAL_SUMMARY.md` §3.15(1)。
- 部署产物：`goetytuner-0.0.13.jar`（**1,753,132 B / md5 `AE59EBFE8CF0F2D09C16453A515CE7F5` / jar 内 102 条目**）
  → `versions\测试\mods\`（旧 0.0.12 已删）；构建在主副本就地 `gradlew clean build` 成功
  （50 s，仍只有原有 3 条 Forge 弃用警告）。
  ⚠️ **jar 的 md5 不可复现**（`MANIFEST.MF` 的 `Implementation-Timestamp`）：判断"部署的 jar 对应哪份源码"
  要逐条目比对，别看整体 md5。另追加一条运维经验：本轮 build 前因 **Gradle 守护进程锁残留**出现过一次构建**挂起**
  （不报错也不推进），`gradlew --stop` 清理守护进程后恢复正常（`org.gradle.daemon=false` 也不能完全避免残留的 worker/文件锁）。
- **待验收**：本轮表现层改动（亚像素刻度、涟漪多波共存、立方体高亮/发光外壳）与**音乐自愈的真机复现路径**
  均**尚未进游戏画面验收**（本轮只做到编译通过 + md5/条目核对）。

### 第 52 轮（0.0.14）
- **本轮范围**：改动 **4 个 Java 文件** + **2 个 lang** + 版本号，**只改代码、不动任何贴图/资源**；
  **新增 1 个配置项** `phase2_buffs.phase2EffectImmunity`（`Boolean`，默认 **true**）⇒ 配置 **62 → 63 项**
  （`phase2_buffs` 段 **3 → 4**，**`boss` 段仍 19、未变**，section 数仍 **10**）；`.java` 文件数仍 **45**、
  jar 条目仍 **102**（无新增/删除类，只是既有类被改写）。
  实测行数：`TunerBoss.java` **2050 行**、`TunerConfigScreen.java` **220 行**、`FocusPoolManager.java` **396 行**、
  `TunerCommonConfig.java` **438 行**；lang `zh_cn.json` / `en_us.json` 各 29 行（**各 +4 键**）。
- **① 配置界面新增「聚晶黑名单」输入框（`client/TunerConfigScreen` + `focus/FocusPoolManager`）**：
  - **背景**：`focus.blacklist` 是 common 配置，而本模组的 `TunerConfigScreen` **顶替了 Forge 默认的 toml 编辑器**
    （注册的是 `IConfigScreenFactory`），该键此前在游戏内**完全没有入口** —— 0.0.13 的「拉黑索命聚晶」
    因此只能靠改文件落地（§3.14(7)）。
  - **改法**：加一个单行 `EditBox`（`cx-150, 138`，`300×18`，`maxLength 1024`，标签画在 `y=126`），
    构造时**预填当前值**（`FOCUS_BLACKLIST.get()`），hint 为「namespace:path，英文逗号分隔；留空 = 不屏蔽」。
  - **保存时机**：**点「完成」关屏时保存**（`onClose()` → `applyBlacklist()` —— 最自然的"我改完了"信号），
    **点「开始评分」时也顺带保存**（用户可能只改了黑名单就来点它）；值没变则直接返回、不做无谓重扫。
    保存动作 = `FOCUS_BLACKLIST.set(v)` + `FOCUS_BLACKLIST.save()` + `FocusPoolManager.refreshBlacklist()`。
  - **为什么"只改值"就已经生效**：`getBlacklist()` 以 **raw 字符串为缓存键**（`TunerCommonConfig` 里的解析缓存），
    值一变缓存自动失效 ⇒ `draw()/drawUniform()` 的下一次抽取就会过滤掉新拉黑的聚晶。
    保存后状态行显示 `config.goetytuner.blacklist.saved`（带当前生效聚晶数）并记 INFO 日志。
  - **新增 `FocusPoolManager.refreshBlacklist()`（本轮真正的设计点）**：`initIfNeeded()` 扫描时**直接
    `continue` 跳过**黑名单聚晶（它们不进 `ALL_ENTRIES`）⇒"**新增**拉黑"靠实时过滤立刻生效，
    但"**取消**拉黑"**必须重扫**才会回到池里；而重扫若 `new` 出全新 `FocusEntry`，会打断实体侧那些
    **按对象身份**记录的状态（`TunerBoss.activeVisualCasts` 身份集合、正在施法的 `CastChannel.current`）
    ⇒ 出现「立方体高亮卡住不熄 / 施法收尾回调对不上（施法计数与蹲姿永久 > 0）」这类**极其隐蔽**的问题。
    故新方法先按 **`namespace:path`** 建索引、重扫时**复用已有对象**（计入 `reused`），**只为"这次才被解禁"的
    聚晶新建**（它们此前不在池里、**不可能正在施法**，故安全）；然后重建 `ALL_ENTRIES` / `STATIC_POOLS`
    并重新 `classification.applyTo(...)`，最后打 INFO `[Tuner] Blacklist refreshed: N foci active (reused X, skipped Y)`。
    逐项 `try/catch` 兜底照留（某个附属聚晶抛异常只跳过并记 ERROR，不影响其它聚晶）。
  - **新增 4 个 lang 键**（zh_cn / en_us 各 4 个）：`config.goetytuner.blacklist.label` /
    `config.goetytuner.blacklist` / `config.goetytuner.blacklist.hint` / `config.goetytuner.blacklist.saved`。
  - ⚠️ **限制**：连**他人**的服务器时改的是**本地**那份 common toml、**服务器侧不受影响**
    （单机 / 局域网主机是同进程的集成服务器，故正常生效）；`RUNTIME_BLACKLIST` 与配置黑名单仍是**并集**，
    配置**无法解禁**一个已被运行期拉黑的聚晶。
- **② 二阶段免疫「回复 / 减伤」类药水效果（`entity/TunerBoss` + `config/TunerCommonConfig`）**：
  - **需求**：进入二阶段后，Boss 不再能被打上 抗性提升 / 伤害吸收 / 生命恢复 之类效果。
  - **实现**：扩展现有的 `canBeAffected` 覆写 —— 它同时是 `LivingEntity.addEffect` 与 `forceAddEffect` 的
    **第一道**判定，在这里返回 `false` 就是**真正的免疫**，而不是"加完再清"。
  - **免疫名单**（集中在私有 `isPhase2Immune(MobEffect)`，一行一项）：**抗性提升** `DAMAGE_RESISTANCE` /
    **伤害吸收** `ABSORPTION` / **生命恢复** `REGENERATION` / **瞬间治疗** `HEAL` / **生命提升** `HEALTH_BOOST`，
    **仅当 `music.isPhase2()`**（且配置开启）时生效。
  - **为何刻意只列这 5 项、而不是"所有 beneficial"**：Boss 自己的二阶段增益
    **力量 `DAMAGE_BOOST`** 与 **重振 `RALLYING`**（`tickPhase2Buffs`）**同样是 beneficial**，
    按 `isBeneficial()` 一刀切会把它们**一起禁掉**、等于顺手废掉二阶段自 buff；
    写成显式白名单后，以后要再挡某个效果（例如某个附属的自定义减伤光环）**加一行**即可。
  - **不影响**：**玩家**的同类效果（本方法只作用于本实体），以及 Boss **自己的二阶段自施**。
  - **新增配置** `phase2_buffs.phase2EffectImmunity`（`Boolean`，默认 **true**；⚠️ 键在 **`[phase2_buffs]` 段**、
    不是 `[boss]` 段）：`false` = **回到旧行为**。属**新增键** ⇒ Forge 会自动补进老 toml、无需手改。
- 部署产物：`goetytuner-0.0.14.jar`（**1,755,283 B / md5 `DA2A20C328B41D71DB9CFCC0AF81DAD2` /
  jar 内 **102 条目**）→ `versions\测试\mods\`（旧 0.0.13 已删）；构建在主副本就地 `gradlew clean build` 成功
  （**1m20s**，仍只有原有 3 条 Forge 弃用警告）。
  ⚠️ **jar 的 md5 不可复现**（`MANIFEST.MF` 的 `Implementation-Timestamp`）：判断"部署的 jar 对应哪份源码"
  要逐条目比对，别看整体 md5。
- **待验收**：本轮两项**均尚未进游戏实测** —— ① 配置界面的黑名单输入框（排版 / 输入 / 保存提示 / 状态行文案，
  以及"点完成关屏保存"与"点开始评分顺带保存"两条路径）；② 改完黑名单后**取消拉黑是否真的立即回到池里**
  （`refreshBlacklist()` 的重扫 + 对象复用路径）；③ 二阶段免疫**实际生效**（给 Boss 丢抗性提升 / 生命恢复
  药水应完全不上身；把配置改成 `false` 后应恢复可施加）。

### 第 53 轮（0.0.15）
- **本轮范围**：**只有本体贴图精修 + 版本号 + 文档** —— **零 Java 改动、无新增/删除资源、配置项数不变**
  （仍 **63 项 / 10 段**）；`.java` 文件数仍 **45**、`TunerBoss.java` 仍 **2050 行**、jar 条目仍 **102**。
- **精修范围**：`src/main/resources/assets/goetytuner/textures/entity/tuner.png`（64×64 RGBA，**2520 → 2676 B**）——
  保留原有紫黑礼服 / 紫色袖口裤靴 / 浅色领巾 / 青色胸饰，精修**翻领边缘、衣袖明暗、袖口细边、裤缝、靴口层次**。
  原稿由 imagegen 生成（生成图**未严格保持矩形位置**），再**按最近邻采样重新测量图块并装配回原有 UV**。
  制作过程入库：`art/REFINEMENT.md`（报告 + 最终提示词）、`art/assemble-refinement.ps1`（可复现装配）、
  `art/body-refined-source.png`（原稿）、`art/tuner-before-refinement.png`（精修前备份）、更新后的 `art/preview.png`。
- **独立核验结论**（以 0.0.14 为基准逐像素差分，**不依赖**其自带脚本）：头部区 **y0..15 全宽 64 列 0 个像素差异**；
  全图 **alpha 蒙版完全一致**（未增删不透明像素）；身体区改动 **1247 像素**；6 个主要 UV 面**无透明空洞**。
- **未做**：**游戏内画面验收**（观感仍待人类 / 能读图的模型确认）；`art/preview.png` 是**平面正背面拼接图，不是游戏截图**。
- **部署产物**：`goetytuner-0.0.15.jar`（**1,755,456 B / md5 `1865ECF4EB22989319FD746806320776` / jar 内 102 条目**）
  → `versions\测试\mods\`（旧 0.0.14 已删）；构建在主副本就地 `gradlew clean build` 成功（**1m44s**，仍只有原有 3 条 Forge 弃用警告）。
  ⚠️ **jar 的 md5 不可复现**（`MANIFEST.MF` 的 `Implementation-Timestamp`）：同一份只改贴图的源码，
  **另一位 agent 自建为 1,755,455 B、主副本重建为 1,755,456 B**，源码相同、仅时间戳不同。

### 第 54 轮（0.0.16）
- **本轮范围**：「逐渐学习」—— 给**动态评分**加一个随**该调律师个体**施法次数爬升的权重系数。
  改 **3 个既有 Java 文件**（`focus/FocusEntry.java`、`focus/FocusPoolManager.java`、`entity/TunerBoss.java`）+ 版本号，
  **只改代码、不动任何贴图/资源**（`.java` 文件数仍 **45**、jar 条目仍 **102**，无新增/删除类与资源）。
- **动机（用户反馈）**：动态评分（实战学到的偏移）把**初始评分**（配置 / LLM 分类）的影响**大幅冲淡** ——
  开局没打几下初始分类就基本失效，"这是个会学习的指挥家"表现力很弱；要求用**较低的权重乘数**大幅降低动态评分影响，
  并随**该个体**的施法次数**缓慢增加**该乘数。
- **权重公式改动**（`focus/FocusEntry.java`，`rouletteWeight` 新增第 4 参数 `dynamicScale`）：
  `|静态评分 + 动态偏移| + 保底基数` ⇒ **`|静态评分 + 动态偏移 × dynamicScale| + 保底基数`**。
  **只缩动态部分，静态评分（初始分类）完全不受影响**；**攻击类与召唤类两条公式都改**（召唤类是生存分 / 输出分
  **两处**偏移各乘一次）。`dynamicScale = 1.0` 即完全等价于旧行为。
- **每实例学习状态**（`focus/FocusPoolManager.java`）：新增实例字段 `castCount`（施法次数）、
  `learningWeight`（当前系数，`-1` = 未初始化）、`lastLoggedMilestone`；新增 `noteCast()`（`castCount++` 后重算）、
  私有 `refreshLearningWeight()`、getter `learningWeight()` / `castCount()`。
  **为什么不是 static**：`TunerBoss` 的 `private final FocusPoolManager pools = FocusPoolManager.createFightPools();`
  是**每实例一份**、`FocusEntry` 也是**实例级复制** ⇒ 学习进度**天然是个体私有**的，与动态偏移生命周期一致。
  **副作用**：`castCount` **不落盘**，退出重进 / 卸载重载 / 换实体 / 重召唤都会**归零**。
- **推进时机**：`entity/TunerBoss.onCastStart` 里调 `pools.noteCast()` —— **只统计"用聚晶施法"的前摇起手**；
  瞬发 / 重音不经过该回调，**不计入**。
- **重算公式与日志**：`w = start + (max − start) × min(1, castCount / ramp)`（**线性**）；
  每跨 10% 里程碑打一条 INFO `[Tuner] Learning weight <start>/<max> = <w> (casts <n>/<ramp>)`。
- **`javap` 调用链验证**（reobf jar 里方法显示 SRG 名，字符串检查会漏报 ⇒ 核字节码）：
  偏移 178 `invokevirtual FocusPoolManager.learningWeight:()D` → 存入局部变量 14 → 偏移 234 `dload 14`
  → 偏移 236 `invokevirtual FocusEntry.rouletteWeight:(D[DZD)D`（新描述符：`double` / `double[]` / `boolean` / **`double dynamicScale`**，返回 `double`）。
- **不受影响**：`drawUniform()`（防御 / 其他类）**本来就不走评分**，行为与 0.0.15 完全一致。
- **新增 3 个配置键**（`[scoring]` 段）⇒ 配置 **63 → 66 项**（`scoring` 段 **3 → 6**，其余九段未变，section 仍 **10**）：
  `learningWeightStart`（`Double`，默认 **0.15**，0~1）、`learningWeightMax`（`Double`，默认 **1.0**，0~2）、
  `learningWeightRampCasts`（`Int`，默认 **60**，1~1000）。**默认曲线**：0 次→**0.150**、10→0.292、20→0.433、
  30→0.575、40→0.717、50→0.858、60→**1.000**（之后封顶）。
- **`FocusEntry` 两个 getter 的现状**：`getEffectiveAttackScore()` / `getEffectiveSurvivalScore()` 现为
  "**未缩权的**视角"（原先正是轮盘权重的输入），**保留作诊断用、不再被抽取路径调用**（**未删除**）。
- **实测行数核对**：`entity/TunerBoss.java` **2053 行**（0.0.15 为 2050）、
  `focus/FocusPoolManager.java` **458 行**（0.0.14 为 396）、`focus/FocusEntry.java` **154 行**、
  `config/TunerCommonConfig.java` **456 行**（0.0.14 为 438）；`.java` 总数仍 **45**；
  配置 `.define` 共 **66** 处 / `push(...)` **10** 段 / `[scoring]` 段 `.define` **6** 处。
- **未做**：**游戏内手感实测** —— 本轮只做到编译通过（主副本就地 `gradlew clean build` 成功，**1m32s**，
  仍只有原有 3 条 Forge 弃用警告）+ `javap` 字节码核对 + jar 条目核对。
- **部署产物**：`goetytuner-0.0.16.jar`（**1,756,720 B / md5 `816C6640E9486FFAFE3B88FA5B2C6D0E` / jar 内 102 条目**）
  → `versions\测试\mods\`（旧 0.0.15 已删）。⚠️ **jar 的 md5 不可复现**（`MANIFEST.MF` 的 `Implementation-Timestamp`），
  判断"部署的 jar 对应哪份源码"要**逐条目比对**，别看整体 md5。

### 第 55 轮（0.0.17）
- **本轮范围**：**索命聚晶后门**（`goety:death` 可无视锁血直接处决）+ **修客户端死亡动画残留**（血量回正后自愈复位）。
  改 **2 个既有 Java 文件**（`entity/TunerBoss.java`、`config/TunerCommonConfig.java`）+ 版本号，
  **只改代码、不动任何贴图/资源**（`.java` 文件数仍 **45**、jar 条目仍 **102**，无新增/删除类与贴图）。
- **动机（用户反馈）**：玩家用**索命聚晶**打中调律师时，Boss 会进入「**动画已经死了、血量却回弹**」的破状态
  —— 索命造成的是"等同于目标当前生命值"的致死伤害，本该是处决；被锁血体系拦下后又回弹复活，
  **客户端动画却留在原地**。用户原话：「如果可以的话给索命的成功伤害直接开个后门；如果麻烦的话修一下动画问题」
  ⇒ **两个都做了**（后门治本、动画自愈治残留，二者各自独立可用）。
- **实证依据（先实证、再动手 —— 这是"精确限定在索命"的来源）**：
  - **调用链**（反编译 Goety 的 `KillingSpell.SpellResult`）：`ModDamageSource.deathCurse(target)`
    → `MobUtil.hurtCalculation(...)` → **`target.hurt(source, amount)`** ⇒ 索命**走 `hurt()`**，
    我们的覆写能看到它（这是后门能成立的前提）。
  - **伤害类型确认**：`ModDamageSource.DEATH` 在**静态初始化**里由 **`create("death")`** 生成
    （`invokestatic create` → `putstatic DEATH`）；jar 内亦有 **`data/goety/damage_type/death.json`**
    （`message_id` = **`goety.death`**）⇒ 伤害类型就是 **`goety:death`**。
  - **"只认索命"的关键实证**：**全 jar 扫描 `deathCurse` 的引用，只有 `KillingSpell` 一处**
    （外加它在 `ModDamageSource` 里的定义）⇒ 按 `goety:death` 匹配**恰好等价于"索命聚晶"**、
    **不会误伤别的法术**。
  - **不需要编译期依赖 Goety 的类**：常量只用资源位置字符串构造
    —— `ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("goety", "death"))`；
    **未装 Goety 时该键永不匹配**（`source.is(...)` 恒 false），后门自然失效、退回旧行为。
- **实现：四处联动**（语义与 §3.11 的 `/kill` 后门同一套 —— 置标记后本次伤害原样交给原版结算）：
  1. 新增字段 `private boolean deathCursePending = false;` + 常量 `GOETY_DEATH_CURSE`；
  2. **`hurt()`**：`if (DEATH_CURSE_EXECUTION.get() && source.is(GOETY_DEATH_CURSE))` → 置标记 + **INFO** +
     `return super.hurt(source, amount)` ⇒ **跳过**身份免疫（摔落/火/窒息/溺水）、**宽限期免疫**、**致死伤害截断**
     （日志：`[Tuner] Death-curse backdoor (goety:death): bypassing lock-health protection (health={}, mark={}, amount={})`）；
  3. **`maintainDeathState()` 最前面**：`if (this.adminKillPending || this.deathCursePending) return;` ⇒ **不回弹**、
     也不做脏状态清理，让死亡流程正常走完；
  4. **`canStillRevive()` 最前面**：`if (this.deathCursePending) return false;` ⇒ **不拦 `remove(KILLED)`**。
     ⇒ 合起来：**索命能真正处决，无视剩余锁血档位**。
- **⚠️ 关键坑（最容易漏、漏了后门就等于失效）**：**`actuallyHurt()` 的限伤 / 限DPS 也必须为它开口子**。
  `goety:death` **不在任何 `BYPASSES_*` tag 里**（不像 `/kill` 的 `generic_kill` 天然被
  `DamageTypeTags.BYPASSES_INVULNERABILITY` 放行）⇒ 必须**显式列出**。不这么写的话，
  索命"等同于目标当前生命值"的致死伤害会被 `maxHitDamagePercent`（默认 25% 最大生命）削掉、**打不死 Boss**，
  而且日志里看不出异常（只看到一次被限伤的命中）。
- **状态清理**：`maintainDeathState()` 的"既没死也不在死亡动画"分支里与 `adminKillPending` 一起清掉
  `deathCursePending` —— 防止"那次索命被其它模组取消"导致保护被**永久**关闭（那会让 Boss 此后对任何致死伤害都不再回弹）。
- **新增配置**：**`boss.deathCurseExecution`**（`Boolean`，**默认 `true`**；`false` = 关闭后门、索命重新受锁血保护）
  ⇒ 配置 **66 → 67 项**（`boss` 段 **19 → 20**，其余九段未变，section 仍 **10**）。
  ⚠️ 键在 **`[boss]` 段**（`b.push("boss")` 块内，与 `maxDamagePerSecond` / `lockDeathRevive` 同段）。
  属**新增键**（Forge 自动补进老 toml），但**配置界面没有入口** ⇒ 想关掉只能**手改 toml 后重启游戏**。
- **代价（设计内，非 bug）**：索命会**对施法者反噬「目标当前生命值 125%」** ⇒ 它是**有代价的处决手段**，
  不是"免费秒杀"。这也是把它做成可关闭配置项的理由。
- **客户端死亡动画自愈（第 ② 件事）**：根因是 **`deathTime` 不是同步数据**（0.0.8 已实证）——
  客户端的 `deathTime` 由**客户端自己**的 `LivingEntity.tickDeath()` 递增，而它**只看客户端本地的血量**；
  服务端复活时只发**一次** `SEntityRevivePacket`，**若那一刻客户端的血量同步还没落地**，
  客户端会在复位后**又自己把 `deathTime` 加上去** ⇒ 此后血量虽变正、动画**再没人清** = **永久躺着**。
  服务端侧的"情形 A"（`maintainDeathState()`）在 `if (!level().isClientSide)` 分支里，**治不了这一侧**。
  修法：`tick()` 的**客户端分支**加一条**对称自愈** —— 客户端**同样知道血量**（`DATA_HEALTH_ID` 是同步数据），
  `if (this.deathTime > 0 && this.getHealth() > 0.0F) this.resetDeathAnimation("client stale death animation (...)")`
  ⇒ **至多残留 1~2 tick**。**安全**：`resetDeathAnimation()` 只在 `level() instanceof ServerLevel` 时才发包，
  **客户端调用不产生任何网络流量**，只把本地 `deathTime` / `hurtTime` / `hurtDuration` / `Pose.DYING` 清干净。
- **实测行数核对**：`entity/TunerBoss.java` **2111 行**（0.0.16 为 2053）、
  `config/TunerCommonConfig.java` **466 行**（0.0.16 为 456）；`.java` 总数仍 **45**；
  配置 `.define` / `.defineInRange` 共 **67** 处 / `push(...)` **10** 段 / `[boss]` 段 **20** 处。
- **未做**：**游戏内实测** —— 本轮只做到编译通过（主副本就地 `gradlew clean build` 成功，**1m9s**，
  仍只有原有 3 条 Forge 弃用警告）+ jar 条目核对。
- **部署产物**：`goetytuner-0.0.17.jar`（**1,757,576 B / md5 `B68C4D4FF9FC650796EDB003D2C53009` / jar 内 102 条目**）
  → `versions\测试\mods\`（旧 0.0.16 已删）。⚠️ **jar 的 md5 不可复现**（`MANIFEST.MF` 的 `Implementation-Timestamp`），
  判断"部署的 jar 对应哪份源码"要**逐条目比对**，别看整体 md5（详见 `TECHNICAL_SUMMARY.md` §六）。

### 第 56 轮（0.0.18）
- **本轮范围**：**调律波纹聚晶**（玩家可用）＋ **调律师仆从**（含「聚晶指令」）。新增 **6 个 Java 类**：
  `combat/AccentRipple`（147 行）、`focus/RippleSpell`（121 行）、`entity/OrbHighlightSource`（24 行）、
  `entity/TunerServant`（522 行）、`entity/TunerServantInteractions`（130 行）、
  `client/render/TunerServantRenderer`（33 行）；**改 16 个既有类**（`init/ModItems`、`init/ModEntities`、
  `init/ModBusEvents`、`entity/TunerBoss`、`entity/ai/CastChannel`、`focus/FocusPoolManager`、
  `focus/FocusCategory`、`config/TunerCommonConfig`、`network/TunerNetwork`、`network/SAccentWavePacket`、
  `client/AccentWaveRenderer`、`client/ClientSetup`、`client/render/TunerModel`、`client/render/TunerCapeLayer`、
  `client/render/TunerOrbLayer`、`client/render/TunerRenderer`）+ 版本号。`.java` **45 → 51**、
  源码 **475,860 B**；新增资源 **3 个**（两个物品模型 json + `textures/item/tuner_ripple_focus.png`）；jar 条目 **102 → 112**。
- **用户需求两条**：① **调律波纹聚晶** —— 与调律师「重音涟漪」**同款效果**的聚晶，灵魂能量消耗 **5**、
  蓄力 **0.2 秒**、冷却 **0.5 秒**，释放时放一次涟漪并产生击退，效果相当于**铺垫期**的涟漪；
  用户特别提醒模组存在「**初始化最后读取 MC 内所有聚晶**」的机制，要考虑该聚晶的**声明次序**以保证能被读取
  而不出 bug，并要求**把该聚晶写入黑名单**。② **调律师仆从** —— 参考诡厄巫法的「生物 ↔ 仆从」对应关系做
  调律师对应的仆从版本，要有**一般仆从的性质**；特别地，**玩家手持聚晶左键 / 右键它**时，将之设置为
  **不释放 / 优先释放**该聚晶。**用户确认的两个范围问题**：仆从**只用刷怪蛋 / 指令召唤**（**不做 summon 聚晶**）、
  仆从**复用调律师的聚晶池 + 轮盘赌抽签**。
- **① 重音涟漪收敛为唯一实现（新增 `combat/AccentRipple`）**：击退 / 粒子 / 声波 / 提示音**四件事**都收在它里面，
  `TunerBoss.accentKnockbackPulse` 被改成**四行转发** —— Boss 与聚晶**共用同一份代码**，而不是抄一份。
  **差别只有两处、由调用方给**：**力量与音调**（Boss 按阶段 0.4/0.8/1.2 与 0.9/1.4/0.6；聚晶固定取**铺垫期**的
  0.4 / 0.9），以及聚晶**额外豁免"施法者自己人"**（自己 / 自己的宠物 / 自己的 `IOwned` 仆从）—— Boss 无此需求，
  故传 `null`。
- **② 调律波纹聚晶（新增 `focus/RippleSpell`，注册在 `init/ModItems`）**：复用 Goety 的 `MagicFocus`（构造函数里
  就存好单例 spell）。`defaultSoulCost()` = **5**、`defaultCastDuration()` = **4 tick（0.2 s）**、
  `defaultSpellCooldown()` = **10 tick（0.5 s）**；`getSpellType()` = `SpellType.NONE`；**不接受附魔**。
  ⚠️ 这三个是**基础值**，Goety 会再叠**施法者修正**（耗蓝乘 `SoulDiscount` / 环境加成、蓄力乘
  `ModAttributes.getCastingSpeed` 与"减半施法时间"饰品、冷却乘 `ModAttributes.getCooldownDiscount`）——
  这是 Goety **所有聚晶的统一规则**，**没有去覆写绕过**。效果走 `AccentRipple`，并**尊重**
  `music.accentParticles` / `music.accentWave` / `music.accentSound` 三个开关（与 Boss 同款：**玩家关掉的观感
  不应在聚晶上复活**）。**注册次序（用户点名的那个点）** —— `focus.blacklist` 默认值里写入了
  `goetytuner:tuner_ripple_focus`，另外三条约束写在 `ModItems` 的**类 javadoc** 里：
  - Ⅰ 必须注册在**同一个 `ITEMS` DeferredRegister**：该 register 在 mod 构造函数**第一时间**调用，而
    `DeferredRegister` 在**远早于 `ServerStartingEvent`** 的 `RegisterEvent` 统一落地 ⇒ 聚晶扫描**一定**看得到它；
    **另起一个 DeferredRegister 却忘了 `.register(modBus)` 就会永远看不到** —— 本项目在**刷怪蛋**上踩过这个
    "注册时序坑"（见「注册时序坑（第七轮实证）」）。
  - Ⅱ `getSpell()` 必须**立刻**返回非 null 单例（`MagicFocus` 在构造函数里就存字段，天然满足）。
  - Ⅲ **必须同时拉黑**：否则 Boss 会抽到它 —— 等于**白得一个 0.5 秒冷却的重音**，而且那发涟漪会把
    **Boss 自己一起推开**。
- **③ `SAccentWavePacket` 载荷由「实体 id」改为「三个 double 的世界坐标」（通道 id 仍为 3）**：**原因** ——
  客户端原先收到包后要用 `level.getEntity(id) instanceof TunerBoss` **反查位置**，而**玩家放的涟漪锚点是一个
  裸坐标、没有实体可查**；`AccentWaveRenderer.trigger(int)` 相应改成 `trigger(double x, double y, double z)`。
  协议号因此提升到 **2.1**（`TunerNetwork.PROTOCOL_VERSION`，**同 id 不同结构会解码错位**；严格匹配不变）；
  **广播方式**由 `TRACKING_ENTITY` 改为「**锚点 64 格内按玩家广播**」。
- **④ 调律师仆从（新增 `entity/TunerServant`，522 行）**：`extends com.Polarice3.Goety.common.entities.ally.Summoned`
  （Goety 里**所有仆从的标准基类**）⇒ 主人归属 / 跟随 / 目标牵引 / 仆从加血与传送 / 日照规则**全部继承**，
  这就是用户要的「一般仆从的性质」。实体名 `goetytuner:tuner_servant`，`MobCategory.MONSTER` +
  `sized(0.6F, 1.95F)` + `clientTrackingRange(8)`（照抄 Goety 仆从写法）。**与 Boss 同源复用**的部分：
  同一个 `FocusPoolManager`（全模组聚晶池 + 静态评分 + 轮盘赌 + 冷却池，**每只仆从各一份实例**）、
  同一个 `CastChannel`（前摇 / 锁池 / 朝向钉死 / 异常自愈）、**同一套模型贴图与渲染层**；
  **没有**音乐阶段、锁血、瞬移、嘲讽、二阶段。
- **⑤ 聚晶指令（本轮唯一的新玩法，新增 `entity/TunerServantInteractions`，FORGE 总线）**：订阅
  `AttackEntityEvent`（左键）与 `PlayerInteractEvent.EntityInteract`（右键）—— 手持 `IFocus` 物品时：
  **左键 = 切换「不释放」**、**右键 = 切换「优先释放」**（再按一次取消）。左键会 `setCanceled(true)`
  （**指挥自家仆从不该顺手揍它**），右键取消并置结果 `SUCCESS`。**客户端一律放行、只在服务端判定与取消** ——
  理由是经 `javap` 实证 `Player.attack` 的**第一条指令**（偏移 0）就是 `ForgeHooks.onPlayerAttackTarget`
  （false 即 `return`），**若在客户端也取消，`LocalPlayer` 会提前返回、连攻击包都不发**，机制直接失效。
  指令存 `FocusPoolManager` 的**实例字段**（**不是 static**，与 0.0.16 的 `castCount` 同理）并落盘到 NBT
  （`DisabledFoci` / `PriorityFoci`）；**优先聚晶在下次施法时走 `CastChannel.beginCast(level, entry, mult)`
  插队**（新重载：**跳过抽签**，其余流程与普通起手完全共用 `startCast`），并按它**自己类别**路由到对应通道。
  **认主**：刷怪蛋 / 指令生成的仆从**没有主人**，而若严格要求"主人才能指挥"，这条机制将**完全不可达**
  （用户已确认只用刷怪蛋获取）⇒ **无主的调律师仆从会在第一次被下达聚晶指令时认那位玩家为主人**并提示；
  **别人家的仆从则完全不干预**（交回默认攻击 / 交互行为）。
- **⑥ 仆从另外三处与 Boss 对齐 + 一处刻意不做**：免疫 Goety 的召唤冷却 `GoetyEffects.SUMMON_DOWN`
  （否则它的召唤会**越召唤越废**）、主手暗法杖 `setDropChance(MAINHAND, 0)`（**配发装备不该在死亡时掉出来**）、
  召唤类权重用的召唤物数量按 `gameTime` **缓存**（本项目红线 13：热路径不许每 tick 全量扫描）。
  ⚠️ **刻意不做**：仆从**不调用** `FocusPoolManager.noteCast()` —— 0.0.16 的「逐渐学习」是**给 Boss 设计的**，
  仆从应当**永远以初始分类为准**（保持起始权重 0.15 不变）；`TunerServant` 里已写注释，**以免后人误判成 bug**。
- **⑦ 泛型化与共用实现（为"同源复用"服务）**：三处渲染 / 模型类做了**泛型化** ——
  `TunerModel<T extends LivingEntity>`（原写死 `HumanoidModel<TunerBoss>`）、`TunerCapeLayer<T>`、
  `TunerOrbLayer<T extends LivingEntity & OrbHighlightSource>`（**新增 `entity/OrbHighlightSource` 接口**，
  Boss 与仆从都实现它 ⇒ 渲染层**不再依赖具体实体类**）；`TunerServantRenderer` 是**唯一**仆从专属渲染文件，
  只多了一个实体类型。`FocusCategory.parseRoleSpec(String)` 是把 `TunerBoss.parseRoleSpec` **上提到枚举的
  共用实现**（Boss 侧改成一行薄委托，**4 个调用点不动**），仆从用它解析 `servant.rotation`。
- **配置**：**67 → 71 项 / section 10 → 11**。新增 **`[servant]` 段 4 项**：`health` = **40**、`followRange` = **32**、
  `castIntervalTicks` = **40**、`rotation` = **"23"**（**新增键** ⇒ Forge 自动补进老 toml）。
  ⚠️ 同时**改了已有键** `focus.blacklist` 的**默认值**：`goetytwilight:destruction_focus` →
  `goetytwilight:destruction_focus, goetytuner:tuner_ripple_focus` —— **这是本次唯一的配置迁移项**
  （Forge **不会**回填老 toml，见 §六 0.0.18 迁移提示）。
- **未做**：**游戏内实测** —— 本轮只做到：
  - `gradlew build` **成功**（**1m19s**），编译期只有项目**既有的基准噪声**（3 条 FML deprecated 警告 +
    1 条 `TunerBoss` 过时 API 注记），**无新增警告**；
  - jar 条目比对 **102 → 112**（新增 10 = 6 个新 class + 2 个模型 json + `textures/item/` 目录 + 纹理 png，**无删除**）；
  - 用**原始字节串搜索**核对（**不用 `javap` 文本匹配 —— `javap` 会折行**）：`TunerNetwork` 含 `2.1`；
    `SAccentWavePacket` 含 `writeDouble` / `readDouble`；`ModItems` 含三个物品 id；`ModEntities` 含 `tuner_servant`；
    `TunerServantInteractions` 含 `AttackEntityEvent` / `PlayerInteractEvent$EntityInteract` / `IFocus`；
    `FocusPoolManager` 含 `takeSpecific` / `isUsable` / `restoreFocusCommands` / `priorityFoci`；
    `ClientSetup` 含 `TunerServantRenderer`；
  - 用 `javap` 核对**被 reobf 成 SRG 名的覆写**（字节串搜索会漏报）：`TunerServant.m_7301_(MobEffectInstance)`
    确实存在且正确调 `Summoned.m_7301_`，构造函数里确有 `m_21409_(EquipmentSlot, F)`（= `setDropChance`）；
  - 用 `javap` 反编译**映射版 MC**（`forge-1.20.1-47.3.22_mapped_official_1.20.1.jar`）实证两个交互钩子的位置：
    `Player.attack` **偏移 0** = `ForgeHooks.onPlayerAttackTarget`（false 即 `return`）、
    `Player.interactOn` **偏移 30** = `ForgeHooks.onInteractEntity`，**早于** `Entity.interact`（偏移 57）
    与 `itemstack.interactLivingEntity`；
  - 配置 / 资源核对：`define` 调用 **71** 处、`[servant]` 段 4 项、黑名单默认值正确、两份 lang 各 **37 键**
    且 JSON 合法、两个模型 json 合法、jar 内 `mods.toml` 版本 **0.0.18**、png 魔数正确。
  ⚠️ **一切运行时表现都未实测**（详见「仍待办 14」）。
- **部署产物**：`goetytuner-0.0.18.jar`（**1,778,838 B / md5 `5946D71FA425B8F84E2F7E1CF4B5A8E3` / jar 内 112 条目**）
  → `D:\落幕曲&原版&灾厄巫咒\.minecraft\versions\测试\mods\`（旧的 `goetytuner-0.0.17.jar` 已删；
  部署时**游戏未运行**，`Get-Process java` 为空）。✅ **已提交并 push**（第 59 轮末一并入库）。
  ⚠️ **jar 的 md5 不可复现**（`MANIFEST.MF` 的 `Implementation-Timestamp`），判断"部署的 jar 对应哪份源码"
  要**逐条目比对**，别看整体 md5。

### 第 59 轮（0.0.20）

- **本轮范围**：**一条回归反馈 + 三条新功能**，**版本号 0.0.19 → 0.0.20**。新增 **4 个 Java 类**
  （`combat/ServantWandBlessing`、`ritual/TunerServantSummonRitual`）+ **1 个仪式配方 json**；
  改 **6 个既有 Java 文件** + **2 个 lang**（**34 → 43 键**，+9/−0）；**配置 71 → 76 项**
  （`[servant]` 段 3 → 7 项）；jar 条目 **117 → 122**（新增 3、无删除）；协议仍 **2.1**。
- **① 【回归】左右键的聚晶指令提示：被我误删，已回补**（用户："左键和右键功能都没有提示了；
  是不是我让你删提示的时候你把左右键的提示也删了，**这个提示是需要的**"）
  - **发生了什么**：第 58 轮我把 `TunerServantInteractions` 里**所有**动作栏提示都删了，
    包括**左右键的指令结果提示**。用户当时要的"不要提示"指的是**物品介绍 / 放置提示**，
    **不包含"操作结果反馈"** —— 后者是交互闭环的一部分。
  - **现在的取舍（写进 HANDOVER 红线 18）**：**要有**"一次主动操作产生了什么状态变化"；
    **不要**物品介绍、自动触发的环境提示（如放置刷怪蛋）。
  - 恢复的 5 组文案见 `TECHNICAL_SUMMARY.md` §3.21(1)；`%s` 是**聚晶自己的显示名**（走玩家语言文件）。
  - **没有回退的部分**：刷怪蛋放置提示仍然没有、物品说明仍然只有那一行 tooltip。
- **② 仆从的仪式召唤**（配方 `data/goety/recipes/tuner_servant_ritual.json`）：
  - **单位口径（反编译实证）**：`soulCost` = **每秒**消耗的灵魂、`duration` = **秒数** ——
    灵魂扣除与 `currentTime++` 同在 `gameTime % 20 == 0` 分支里。
    用户说的"每秒 10 能量、10 秒"因此**正好**是 `soulCost: 10, duration: 10`（共 100 灵魂）。
  - 材料 **8 个基座**：紫水晶碎片 ×4、红石、钻石、金锭、青金石（"2 个紫水晶 ×2"已与用户确认为 4 个碎片）。
  - **激活条件是 NBT 级的**：中心那把杖必须是 `IWand` 且**两种调律加成任一 > 0**（`witchcraft_bonus` 或 `magic_damage_bonus`）
    （= tooltip 上有「调律:巫法加成」或「调律:魔法伤害加成」那一行）⇒ 完全重写 `identify`（取并集，避免"这杖明明调过律却因为某项被配成 0 而召唤不了"的死角）；
    不满足时返回 false，**复用 Goety 自己的失败提示**，不另造文案。
  - **`tame = true` ⇒ 认主**（`SummonRitual(recipe, true, false)`，字节码为 `iconst_1, iconst_0`；
    最终走 `MobUtil.summonTame`）。与刷怪蛋"直接放 = 野生"是两条互不影响的获取路径。
  - **法杖被祭坛消耗**，其**快照**存进仆从 NBT（既是强度依据也是死亡掉落物）；
    ⚠️ 仆从**主手**仍是 `goetytuner:tuner_wand`（AI 施法载体），召唤用杖**不装到手上**。
  - ⚠️ **Goety 的全局仆从上限**：`SummonRitual.isValid` 里还有 `RitualRequirements.canSummon`，
    它会数玩家已有的 `IOwned` 仆从 ⇒ 满员时仪式不成立（Goety 对所有仆从召唤的统一规则）。
- **③ 杖的加成 → 仆从强度**（`combat/ServantWandBlessing`，唯一实现）：
  - 按 `pct = 加成 × 100` 判定（法杖 NBT 是小数、用户说的是百分数，**与 tooltip 数字一致**）：

    | 加成 | 强健 | 生命恢复 | 抗性提升 |
    |---|---|---|---|
    | < 10% | — | — | — |
    | 10% | Ⅰ | — | — |
    | 30% | Ⅱ | Ⅰ | — |
    | 40%（击败一次，默认） | Ⅱ | Ⅰ | — |
    | 70% | Ⅳ | Ⅱ | — |
    | 90% | Ⅴ | Ⅱ | Ⅰ |
    | > 100% | Ⅵ+（不封顶） | Ⅱ | Ⅱ（封顶） |

  - **"持续性地给予"= 低频自愈式刷新**：`aiStep` 每 tick 调用，内部按 1 秒降频、每次挂 3 秒，
    **已是目标档位且剩余时长足够就跳过** ⇒ 平时每秒最多 3 次 `addEffect`，不产生无谓同步；
    被牛奶清掉后最多 1 秒自动回来。调用点放在 `aiStep` **所有 early-return 之前**（施法中也要维持）。
  - **药水机制与本体独立**（用户要求）：不继承二阶段增益免疫 / 低谷 SAPPED / DARKNESS、
    不受 `[boss]` 段任何键影响；唯一开关是新增的 `servant.wandBlessingEnabled`。
    仆从自己的免疫规则仍只有它自己那条（拒绝 `SUMMON_DOWN`）。
  - ⚠️ **只有仪式召唤的仆从吃得到**（刷怪蛋 / `/summon` 没有召唤用杖）。
  - ⚠️ **必须告诉用户的坑**：`goety:buff`（强健）**只加 `ATTACK_DAMAGE`**（近战），
    而本模组的仆从**刻意没有近战手段** ⇒ **强健几乎不产生战斗力**（HUD 会显示）。
    这一条**忠实照搬了用户规格**，但"越强"的直觉不成立；**可选修法**（把强健等级同时换算成
    `ModAttributes.SPELL_POTENCY`）**已写进 `TECHNICAL_SUMMARY.md` §七.15，等用户决定**。
- **④ 仆从死亡掉落召唤用杖**（`WandUpgradeEvents#onLivingDrops` 里新增一个分支）：
  - **原样返回**：掉**召唤时那把杖的完整副本**（附魔 / 聚晶 / **它原有的调律加成**全在），
    **不额外叠加** —— 与 Boss 那句"快照 + `applyWandUpgrade`"刻意对照。
    （"并不会有加成"已与用户确认为"不会再被加成一次"，不是"剥掉原有加成"。）
  - 同样先清掉掉落里的 `IWand`（主手 `tuner_wand` 本来就 `setDropChance(0)`，再兜一道）。
  - 日志：`[Tuner] Tuner servant died, returned its summoning wand: <uuid> (<name>)`。
- **⑤ 聚晶包 / 多晶大袋批量指令**（`TunerServantInteractions`）：
  - 判定 `instanceof FocusBag`（`FocusPack extends FocusBag` ⇒ **一个判定覆盖两种袋子**）；
    内容物走**通用** `ForgeCapabilities.ITEM_HANDLER`（字节码实证：聚晶包 11 格、多晶大袋 21 格）。
  - 语义与单晶**完全一致**（已与用户确认）：**全部已是该状态 ⇒ 整体清除，否则 ⇒ 全部设为该状态**。
  - 非聚晶物品跳过、同 id 去重；**空袋子不吞掉**这次攻击 / 交互。
  - 互斥语义收敛到 `TunerServant` 的 `setDisabledExclusive` / `setPriorityExclusive` 两个私有方法，
    **单个切换与批量共用同一实现**。
- **⑥ 顺带的做法收敛**：`WandUpgradeEvents#magicBonusOf`（private）→
  **`public static magicDamageBonus(ItemStack)`** —— "法杖的调律·魔法伤害加成是多少"的**唯一实现**，
  现有三个消费方（伤害加成 / 仪式激活判定 / 仆从增益档位）。
- **⑦ 【追加·用户要求】「强健」等级 → 法术强度**（`combat/BuffSpellPower`）：
  - **作用域**：**只对本模组的调律师一族**（`TunerBoss` + `TunerServant`，各按**自身**的强健等级）——
    本方法只被这两个实体调用，本模组没有任何全局事件 / 属性广播 ⇒ 别的生物与玩家拿不到这个 modifier。
  - **⚠️ 最关键的一处实证（它决定了实现方式）**：`goety:spell_potency`
    ① 注册为 `RangedAttribute(..., 0.0D, 0.0D, 2048.0D)` ⇒ **基础值 0.0**；
    ② 唯一读取入口 `ModAttributes.getPotency(LivingEntity)` = `(int) getAttributeValue(...)` ⇒ **int**；
    ③ 法术把它当**平铺伤害点数**用（`SpellStat.getPotency()`，Goety 公式普遍是"基础伤害 + potency"）。
    ⇒ Minecraft 的 `MULTIPLY_TOTAL` 是 `total *= 1 + amount`，**基础值 0.0 乘任何数还是 0.0**
    ⇒ **任何百分比 modifier 在这个属性上都是死代码**。所以本类用 **`ADDITION`**。
  - **换算比例**：新增 **`[casting] buffSpellPowerPerLevel`**（**Int**，**默认 1**，范围 0~100，**0 = 关闭**）。
    ⚠️ 之所以是 Int：`0.1` 会被 `(int)` 截成 0 ⇒ 白挂。默认 1 ⇒ 强健Ⅰ = 每发 **+1 点**、Ⅴ = **+5 点**
    （与 Goety 原版强健 `ATTACK_DAMAGE +1/级` 同节奏）。
  - **刷新**：与 `ServantWandBlessing` 同款（每 tick 调用、内部 1 秒降频、**数值没变就一个字节都不写**）；
    强健消失 ⇒ 目标值归 0 ⇒ **主动 `removeModifier`**。仆从身上**顺序有意固定**：
    先挂强健（`ServantWandBlessing.tick`）、再换算（`BuffSpellPower.tick`），且都在 `aiStep` 的所有 early-return 之前。
  - **本体现状**：调律师**今天并不自施强健**（二阶段给的是**原版力量**，第 33 轮特意换掉的）
    ⇒ 对本体的效果目前是"每秒一次的幂等短路"；将来若给本体加强健，**自动生效、无需改代码**。
  - ⚠️ **顺带查出的两处死代码**（本轮**只报告未改**）：
    玩家的「调律:巫法加成」（`WandUpgradeEvents#refreshWitchcraftModifier`）与
    Boss 二阶段的「力量等级 × 0.1」（`TunerBoss#tickPhase2Buffs`）**都用了 `MULTIPLY_TOTAL`**
    ⇒ 在 0 基础值属性上**从未生效**（tooltip 会显示、伤害不变）。
    修法必须改成 `ADDITION` 并**重新定义数值含义**（百分比 → 点数量级），属**平衡决策**。
- **⑧ 【追加】两处百分比死代码已修**（见 §3.21(4c)）：
  - **百分比 → 伤害事件**：新增 `combat/SpellDamageBonus`（`LivingDamageEvent`，`×(1+加成)`）——
    玩家 = 主副手两把杖的「巫法加成 + 魔法伤害加成」**相加**（满配杖 **×1.50**）；
    调律师 = `phase2SpellDamageBonus()`（读**自身力量效果**的等级 ⇒ 2 级 +20%、5 级 +50%）
    ＋（可选）`wandBonusAppliesToBoss` 控制的手持杖魔法加成。
  - **删掉的死代码**：`WandUpgradeEvents` 的 `refreshWitchcraftModifier` / `POTENCY_MODIFIER_UUID`
    / `LivingEquipmentChangeEvent` 监听器 / **每 32 tick 的 `PlayerTickEvent` 兜底**；
    `TunerBoss` 的 `PHASE2_POTENCY_UUID` 与 modifier 块。
    ⇒ 顺带**省掉一个常驻玩家 tick 订阅**，全 jar 现在**只有一个** `LivingDamageEvent` 处理器。
  - **判定修宽**（第二个本来就会让修复白做的问题）：原判定 = `forge:is_magic` 标签 + 原版
    `magic`/`indirectMagic`，而标签里**只有 Goety 39 个伤害类型中的 8 个**
    ⇒ 腐化光束（`goety:magic_fire`）、火球（`goety:magic_fireball`）、`hellfire`、`shock`、
    `direct_freeze`、`frost_breath` … **全都不在内**。现在并上「**`goety` 命名空间**」判定
    （数据驱动、不写死法术清单，与 Goety 的 39 个 `damage_type` 对齐）；
    取 id 用 `DamageSource#typeHolder().unwrapKey()`（1.20.1 没有 `isMagic()`）。
    ⚠️ 代价：其它模组的法术若不在标签里、也不是原版 magic，就不被覆盖（**宁可漏、不可错伤近战**）。
  - **自伤不再被放大**：`src.getEntity() == event.getEntity()` 直接放行
    （索命聚晶对施法者的 125% 反噬不再被自己的加成放大）。
  - ⚠️ **通道分工（写进文档）**：**百分比 → 伤害事件（`SpellDamageBonus`）；
    点数 → `SPELL_POTENCY`（`BuffSpellPower`）**。
- **⑨ 【追加】强健等级可见化 + 仆从独立限伤**：
  - **等级其实是对的**：1.20.1 客户端 `EffectRenderingInventoryScreen#getEffectName` 只在
    **amplifier 1~9（等级 II~X）**时附罗马数字 ⇒ 等级 1 与 **等级 ≥11** 都只显示「强健」
    （原版 `enchantment.level.*` 也只有 1~10）⇒ **+1000% 巫法 = 50 级，正好看不见**。
    新增 **`servant.buffMaxLevel`（Int，默认 10）** ⇒ 实际等级 = `min(公式值, 上限)`，
    默认钉在可见范围内；**真实等级写进日志**（召唤时一行 + 等级变化时一行）。
  - **限伤**：`TunerServant#actuallyHurt` 原先读的是 `[boss]` 的两个键
    （限伤 0.25、**限DPS 默认 0=关闭**）⇒ 只有单次上限一道。
    现在 `[servant]` 有两个**独立**键：`maxHitDamagePercent`=**0.15**（216 血 ⇒ 单次 ≤32 点）、
    `maxDamagePerSecond`=**0.50**（⇒ 每秒 ≤108 点，**至少 2 秒才能打死**）。
    ⚠️ `/kill` 等 `BYPASSES_INVULNERABILITY` 伤害**照旧绕过**（有意）。
- **未做**：**游戏内实测**。本轮只做到 `gradlew build` **成功**（**1m3s**，只有项目既有基准噪声、
  **无新增警告**）+ jar 条目核对（**117 → 120**）+ **`javap` 核对**（批量方法签名、
  `aiStep` 里确有 `ServantWandBlessing.tick`、`ServantWandBlessing` 里确有 `GoetyEffects.BUFF` 与
  `REGENERATION`/`DAMAGE_RESISTANCE`（**用未 reobf 的 dev class 复核效果身份** —— 只看 SRG 名会认错）、
  构造函数的 `iconst_1, iconst_0`、`instanceof FocusBag` 与 `ITEM_HANDLER` 引用、
  `magicDamageBonus` 与 `dropServantSummonWand` 的存在）+ 配置 / 资源核对（`define` **76** 处、
  section **11** 段、两份 lang **各 43 键**、`mods.toml` **0.0.20**、配方 JSON 的
  8 个基座与 `soulCost=10 / duration=10 / craftType=magic`）+ 部署核对**逐字节相同**。
  ⚠️ **四条内容的运行时表现全部未实测**（详见「仍待办 17」）。
- **部署产物**：`goetytuner-0.0.20.jar`（**1,798,454 B / md5 `1A90977DEFF0D3192342EB4C4BADD21C` /
  jar 内 122 条目**）→ `versions\测试\mods\`。
  ⚠️ **删除了旧的 `goetytuner-0.0.19.jar`** —— 同 mod id 的两个 jar 并存会被 Forge 判为**重复 mod** 而启动失败。
  ⚠️ **部署时游戏正在运行**（`java-runtime-epsilon`，19:45 启动）⇒ **必须完全重启游戏**才加载 0.0.20。
- 

### 第 58 轮（0.0.19 修补）

- **本轮范围**：**仍是 `0.0.19`（不升版本号）**，处理用户对第 57 轮交付的**三条反馈**。改 **5 个既有 Java 文件**
  （`entity/ai/CastChannel`、`entity/TunerServant`、`entity/TunerServantInteractions`、
  `init/TunerServantSpawnEggItem`、`focus/FocusPoolManager`）+ **2 个 lang**；**没有新增 / 删除任何类与资源**
  ⇒ jar 条目 **仍 117**（无增无删）；**配置项数仍 71 / 11 段，本轮未动任何配置键**；网络协议仍 **2.1**（未动）。
- **① 用户第 1 条 · 提示精简**（原话："不需要更多的物品介绍和放置/交互后的提示"）：
  两份 lang **各 43 → 34 键（净 −9）**，**只保留一行** `tooltip.goetytuner.servant.spawn_egg` =
  "调律师仆从刷怪蛋，潜行使用会生成你自己的仆从."（en_us：`Tuner Servant Spawn Egg. Sneak-use to spawn one of your own.`）。
  - `init/TunerServantSpawnEggItem`：**删掉 `useOn` 覆写里那两条动作栏提示**（认主 / 野生）⇒ 该类现在
    **只做两件事**：继承 Goety `ServantSpawnEggItem` 拿到"直接放 = 野生 / 潜行放 = 认主"的范式，
    `appendHoverText` 里加**唯一那一行** tooltip（**且不再调用 `super`**，把 Goety 原本的说明也一并去掉）。
  - `entity/TunerServantInteractions`：**删掉 `wild_hint` / `not_owner_hint` 两条提示**。**行为不变** ——
    仍然是"**只给提示、不吞掉**"的那个语义，只是**不再弹字**；`canCommand` 拒绝时改打 **INFO 日志**
    （`[Tuner] Servant focus command refused ...`）。
  - **规律（写进红线）**：**诊断信息走日志，不走动作栏**；面向玩家的文字**只留必要的一行**。
- **② 用户第 2 条 · "不释放的调整功能似乎完全不起效了"** —— 两条可能的病根**都堵掉**（本轮**无复现证据**，
  如实说明是"两条最可能的解释"）：
  - **（a）野生仆从被拒（最可能的真凶）**：第 57 轮把 `isOwner` 收紧成"只认主人"，而**刷怪蛋默认直接放 = 野生**
    ⇒ 用户按默认方式放的仆从**有一条算一条全被 `canCommand` 拒绝**，表现就是"**功能完全不起效**"。
    现在 **`owner == null`（野生）也放行** —— 野生仆从**本来就是可配置的**（它没有主人、也就会听所有玩家），
    **只有"别人家的仆从"才拒绝**（`owner != player`）。
  - **（b）潜行劫持左键**：第 57 轮自己加的"**潜行 + 左键 = 清空全部指令**"（`clearFocusCommands`）会在
    **潜行状态下抢走左键** —— 玩家潜行按左键得到的是"清空"，**看起来就是"不释放切不动"**。
    该功能连同 `clearFocusCommands` / `disabledCount` / `priorityCount` **一并删除** ⇒
    左键的语义**只剩**"切换不释放"、右键**只剩**"切换优先释放"，两者**仍然互斥**（任一键都能把聚晶切回中立）。
  - **规律（写进红线）**：**不要给同一个输入加"隐藏的组合键"** —— 组合键会静默劫持基础操作，
    让基础操作"看起来坏了"；**归属也只在"生成"这一个点确定**，被拒绝时就该在**日志**里说清楚原因。
- **③ 用户第 3 条 · 箭雨聚晶"几乎没有持续"**（用户原话："我有点怀疑是否能完美地识别持续类"）：
  **先核对识别、再修真正的病根。**
  - **识别没问题**：`scripts/scan_charging_spells.py`（真·class 文件解析，26 个具体长按类法术）实证
    **`ArrowRainSpell extends EverChargeSpell`**（`EverChargeSpell → ChargingSpell implements IChargingSpell`），
    本来就在闭包内、本来就被正确识别 ⇒ **不是识别漏了**。
  - **真因：通道预算把"蓄力"算进了"总时长"**。`ArrowRainSpell.castUp` 由 `new ArrowRainChargeUp(...)` 解算，
    **默认 20 tick**；而第 57 轮的预算是 `channelEndTick = channelMaxTicks`（默认 **20**）⇒
    **蓄力刚好把整个预算吃光、一发即收**。这不是箭雨独有 —— 任何 `castUp` 接近或超过 20 的长按类聚晶都会这样。
  - **修法（两段预算）**：`蓄力 = clamp(round(castUp × warmupMultiplier), 0, casting.maxCastWindowTicks)`、
    `持续 = max(5, casting.channelMaxTicks)`，`channelEndTick = 蓄力 + 持续` ⇒
    **`channelMaxTicks` 现在只承诺"持续段"的时长**（配置注释已写清"**不含蓄力**"）。
  - **顺带删掉"按 `shotsNumber` 提前收手"**：反编译实证 **`DarkWand` 对 `shotsNumber` 只用来记发数、
    算释放冷却，从不据此停火**（玩家松手前会一直放）⇒ 据此收手是**不忠实的模拟**，已移除。
    现在长按类只有三条收口：**持续段到点** / `MAX_CHANNEL_SHOTS = 400` 防御性硬上限 / 被打断。
  - **实测口径**：**腐化光束共 20 tick**（`castUp` 极小、几乎全给了持续）、**箭雨共 40 tick**（20 蓄力 + 20 持续）、
    **震撼 / 炼狱**等按各自 `castUp` 顺延。⚠️ 平衡风险照旧：腐化光束**每 tick 造成伤害**
    （`CorruptedBeamDamage` 默认 10.0 且清零 `invulnerableTime`），**`channelMaxTicks` 调到 100 以上基本等于必杀**。
- **未做**：**游戏内实测**。本轮只做到 `gradlew build` **成功**（**约 1 分钟**，只有项目既有基准噪声、
  **无新增警告**）+ jar 条目核对（**117 → 117，无增无删**）+ **原始字节串搜索**核对
  （`CastChannel` 的 `channelChargeTicks`、`channelEndTick`、`IChargingSpell`；`TunerServant` 的
  `toggleFocusDisabled` / `toggleFocusPriority` / `TUNER_WAND`；`TunerServantInteractions` 的 `canCommand` /
  `Servant focus command refused`；`TunerServantSpawnEggItem` **不再有 `useOn`**；`FocusPoolManager` 的
  `setFocusDisabled` / `setFocusPriority`）+ **配置 / 资源核对**（`define` **71** 处、section **11** 段、
  两份 lang **各 34 键**且 JSON 合法、**无残留** `info.goetytuner.servant.*`、jar 内 `mods.toml` 版本 **0.0.19**、
  png 魔数正确 627 B）+ 部署核对**构建产物与部署 jar 逐字节相同**。
  ⚠️ **三条修复的运行时表现全部未实测**（详见「仍待办 16」）。
- **部署产物**：`goetytuner-0.0.19.jar`（**1,785,988 B / md5 `77DAAFB0A5B77907C3D7A22461731FB8` / jar 内 117 条目**）
  → `versions\测试\mods\`。⚠️ **部署时游戏正在运行**（PID 8648 / `java-runtime-epsilon` / 19:45 启动）⇒
  **运行中的实例仍是旧字节码，必须完全重启游戏**才加载本轮修复。
- ✅ **0.0.18 + 0.0.19 的改动已于第 59 轮末一并提交并 push**（见第 59 轮条目）。

### 第 57 轮（0.0.19）

- **本轮范围**：**用户对 0.0.18 交付的五条反馈，全部处理**。新增 **4 个 Java 类**：
  `combat/TunerDamageRules`、`combat/DamageThrottle`、`focus/TunerWand`、`init/TunerServantSpawnEggItem`；
  **改 6 个既有 Java 文件**（`entity/ai/CastChannel`、`init/ModItems`、`entity/TunerBoss`、`entity/TunerServant`、
  `entity/TunerServantInteractions`、`config/TunerCommonConfig`）+ **2 个 lang**（各 37 → 43 键）+
  **1 个模型 json**（`models/item/tuner_wand.json`）+ 版本号；另改了 `art/gen_ripple_focus_icon.py` 的配色
  并把 `tuner_ripple_focus.png` 重新生成（694 → 627 B）。`.java` **51 → 55**、源码 **511,948 B**；
  jar 条目 **112 → 117**（新增 5、**无删除**）。
- **配置**：**项数与段数都不变（仍 71 项 / 11 段）** —— **新增** `casting.channelMaxTicks`（默认 **20** = 1 秒，
  范围 5~200）1 项、**删除**死配置 `servant.health`（默认 40）1 项，净变化 0；`casting` 段 10 → 11、
  `servant` 段 4 → 3。**一「加键」一「删键」，两者都无需手工迁移**（与 0.0.18 那种"改已有键的值"是两种性质）。
- **① 用户第 1 条 · 聚晶指令「左键『不释放』似乎不能取消」—— 语义重做**：0.0.18 的「不释放」与「优先」是
  **两张互不相干的表**，同一聚晶可**同时命中两者**（此时"不释放"胜出）⇒「先右键设了优先 → 再左键设了不释放
  → 又**按右键想取消**」就会**按了没反应**，这正是"不能取消"的来源。现在两状态**互斥**：
  **左键切换"不释放"并顺手清掉该聚晶的"优先"；右键切换"优先"并顺手清掉"不释放"**
  （`TunerServant#toggleFocusDisabled` / `#toggleFocusPriority`），任何一键都必然能把聚晶**切回中立**、
  语义单一可逆。另加**逃生通道**：**潜行 + 左键 = 清空该仆从的全部聚晶指令**（`clearFocusCommands`）。
  提示文案改为**列出两张表当前的条目数**（新增 `disabledCount()` / `priorityCount()`）⇒ 设置有没有生效一眼可验。
  ⚠️ **如实说明**：本轮**没有进游戏实测**，所以"原来为什么不能取消"只剩这个**语义解释、没有复现证据**
  （从代码上找不到一个真正的机械故障，但这是一个**必然导致该症状**的陷阱，已修）。
- **② 用户第 2 条 · 仆从的血量 / 护甲 / 减伤限伤与本体一致**：
  **血量 / 护甲**：`TunerServant` 构造函数直接读 **Boss 的配置键** `boss.maxHealth`（默认 **216**）与
  `boss.equivalentArmor`（默认 **16**），`KNOCKBACK_RESISTANCE` 也对齐成 **1.0**；`createAttributes()` 的
  占位常量同步改成同一组（避免"config 未加载时创建出的实体数值不一致"这种边角差异）；**`servant.health`
  （默认 40）已删除**（死配置；属**删键** ⇒ 老 toml 成孤儿条目、Forge 自行处理、**无需迁移**）。
  **减伤 / 限伤**：把 Boss 原先内联的三段判定抽成**共用实现**（本项目红线：同一职责只允许一处实现）——
  - 新增 **`combat/TunerDamageRules`**：`isIdentityImmune`（摔落 / 火焰 `IS_FIRE` 全系 / 窒息 / 溺水）、
    `isDirectMelee`、`applyMeleeVulnerability`（近战易伤 ×(1+`boss.meleeVulnerability`)，默认 +25%）；
    `TunerBoss` 改为调用它 —— **行为不变，只是搬家**。
  - 新增 **`combat/DamageThrottle`**：限伤（单次伤害 ≤ 最大生命 × `boss.maxHitDamagePercent`）+
    限DPS（滑动 1 秒窗口预算，`boss.maxDamagePerSecond`，返回 `-1` = 整段吸收）。0.0.6 时这段内联在
    `TunerBoss` 里（环形缓冲 + 3 字段 + 3 方法），现整体搬进本类，Boss 与仆从**各持一份实例、共用同一份代码**。
  - 仆从新增 `hurt` / `actuallyHurt` 覆写，与本体同一套规则；**唯一差别是没有后门豁免**
    （`/kill` 的 `generic_kill` 本来就该正常生效，仆从不需要"管理员杀不死"）。
  - ⚠️ **刻意不含**：Boss 的**锁血阶梯**（`lockMark` / 宽限期免疫 / 致死截断 / `/kill` 后门 / 索命后门）——
    那是 Boss 的招牌机制，仆从若也锁血就成了打不死的怪。这一取舍写进了两个新类的类注释。
- **③ 用户第 3 条 · 刷怪蛋范式（直接放 = 野生 / 潜行放 = 认主，并附文字提示）**：
  新增 **`init/TunerServantSpawnEggItem`**，**直接继承 Goety 本体的 `ServantSpawnEggItem`**（本体所有仆从蛋
  都用它）—— 它的逻辑就是用户描述的那个范式：`if (entity instanceof IOwned owned && !owned.isHostile()) {
  if (player.isCrouching()) owned.setTrueOwner(player); }`，**不潜行则保持野生**（无主人 ⇒
  `Summoned.finalizeSpawn` 会 `setWandering(true)`）。本类只外加两件事：① 放置时给**动作栏提示**
  （认主 / 野生两种文案 `info.goetytuner.servant.spawn.tamed` / `.wild`）；② tooltip 多一行本模组专属说明
  （Goety 的 `super` 已经加了它自己那条通用提示）。**归属不再因交互而改变（这是用户投诉的点）**：
  0.0.18 的「野生仆从第一次被下达聚晶指令时认主」（`adoptOwnerIfUnowned`）**已彻底删除**，
  lang 键 `info.goetytuner.servant.adopted` 也一并删掉；现在归属**只在刷怪蛋放置时**确定，
  `TunerServantInteractions#isOwner` 只认主人；**野生仆从**被手持聚晶左/右键时给出提示
  （`info.goetytuner.servant.wild_hint`）且**不吞掉**这次攻击 / 交互；**别人家的仆从**同理
  （`info.goetytuner.servant.not_owner_hint`）。
- **④ 用户第 4 条 · 「长按持续释放」类聚晶只放一瞬间就停（本轮最有价值的一处修复）**：
  现象 —— 腐化 / 震撼 / 炼狱这类在玩家视角中"长按持续释放"的聚晶，在调律师（以及仆从）身上**只放一瞬间
  就停下**，被当作瞬发法术用了。**两层根因（均已反编译实证）**：
  - **（a）只结算一次**：玩家施法路径（Goety `DarkWand.onUseTick`）对 `IChargingSpell` 是"蓄力到 `castUp`
    之后，每 `Cooldown` tick 调一次 `MagicResults`（→ `SpellResult`），一直持续到松手"，而 `CastChannel`
    原先只在**前摇结束时**调**一次** `SpellResult` ⇒ 轰炸 / 雷电 / 暴雪这类"每发生成一个实体"的法术只出了一发。
  - **（b）实体下一 tick 就自毁**（"腐化"最直接的原因）：Goety 的 `AbstractBeam.tick()`（腐化光束的基类）有
    `if (owner == null || !owner.isAlive() || (this.itemBase && !MobUtil.isSpellCasting(owner))) { this.discard(); return; }`，
    而 `MobUtil.isSpellCasting` = `livingEntity.isUsingItem() && getUseItem().getItem() instanceof IWand
    && !WandUtil.findFocus(e).isEmpty()` ⇒ **Mob 从不 `startUsingItem`** ⇒ 光束刚生成就被丢弃 ⇒
    玩家看到的就是"闪一下"。
  **修法**：`CastChannel` 新增通道型路径 **`tickChannel`** —— ① 用 `startUsingItem(MAIN_HAND)` 让施法者
  **真的在"使用法杖"**（每 tick 自愈式重设，被打断 / 被其它模组 `stopUsingItem` 时能恢复），
  且 `finishCast` / `interrupt` / `startSpell` 异常 / **外层兜底 catch** 四处一律 `stopChannelUse(...)`
  （否则光束会永远不消失、施法者永远"使用中"）；② 按法术自己的 `Cooldown` / `shotsNumber` 节奏**反复释放**；
  ③ 判定依据是 Goety 自己的 `IChargingSpell`（腐化 / 震撼 / 暴雪 / 轰炸 / 旋风 / 箭雨 / 电击 / 水流 / 蒸汽 /
  念力 / 吸取 / 掘地 / 进食 / 飞行 / 防护 / 流星雨 … 全是它的子类），**不写死任何聚晶 id** ⇒
  附属模组的同类法术**自动受益**。
  **⚠️ 关键坑 · 为什么必须自备 `focus/TunerWand` 而不能继续用 `goety:dark_wand`**：让 Mob 去"使用"
  dark_wand 会让 `DarkWand.onUseTick` 每 tick 跑起来，它的收尾是
  `this.MagicResults(stack, worldIn, livingEntity, spell)`，而该方法对**非玩家施法者**走的是
  `failParticles(...) + FIRE_EXTINGUISH` 分支 —— **既不释放法术，又会每 `Cooldown` tick 冒一把白烟 +
  响一声灭火音**。所以本轮自备 `focus/TunerWand implements IWand`：
  - `onUseTick(...)` **空实现**（这就是本类存在的核心理由）；
  - `getUseDuration(stack)` 返回 **72000**（只为不让 `LivingEntity` 自动 `completeUsingItem`，
    真正决定何时松手的是 `CastChannel`）；
  - `initCapabilities` 显式委托 `IWand.super.initCapabilities(...)`（`SoulUsingItemHandler` 依赖这条
    capability，`BossWandHelper.installFocus` 与 `IWand.getFocus` 都走它）；
  - `getSpellType()` = `SpellType.NONE`（与 dark_wand 一致、接受所有聚晶）；
  - 模型 `assets/goetytuner/models/item/tuner_wand.json` 内容只有 `{"parent": "goety:item/dark_wand"}`
    ⇒ **外观与之前完全一样**。
  Boss 与仆从的主手都从 `goety:dark_wand` 换成 `goetytuner:tuner_wand`，并在 `readAdditionalSaveData` 里做
  **旧档迁移**（老存档主手仍是 dark_wand 时自动换成新杖）。**顺带落地了遗留待办「C Boss专属魔杖」**
  （原先用 dark_wand 占位）。
  **用时上限（用户点名要求「给一个用时上限，防止它停不下来」）**：新增配置
  **`casting.channelMaxTicks`**（`IntValue`，**默认 20 = 1 秒**，范围 5~200）。三条**独立**的收口条件、
  谁先到算谁：① 总时长到 `channelMaxTicks`；② 法术自己的 `IChargingSpell#shotsNumber(...)`（`> 0` 时）放完；
  ③ 防御性硬上限 `MAX_CHANNEL_SHOTS = 400`。到点必然 `finishCast()`（内含 `stopUsingItem`）。
  **⚠️ 顺带澄清用户那个疑问**（"我记得已经给过一种时间限额，我不清楚它是否奏效、是不是应用于这一部分"）：
  **旧键 `casting.maxCastWindowTicks`（默认 50）确实一直在生效，但它管的是"把前摇截断到 2.5 秒"，
  并不管持续释放。** 长按类法术的 `defaultCastDuration()` 默认是 **72000**，会被它截成 50 ⇒
  **旧行为就是"站桩 2.5 秒 → 放一发 → 结束"，这正是本 bug 的一部分**。现在两个键分工明确：
  `maxCastWindowTicks` = **普通法术**的蓄力截断；`channelMaxTicks` = **长按类法术**的持续时长上限。
  **与旧行为的关系**：普通法术（非 `IChargingSpell`）**完全不变** —— 仍是
  `warmup = min(castDuration × 倍率, maxCastWindowTicks)` 然后单次结算。
  **⚠️ 平衡风险（必须告知）**：腐化光束这类法术是**每 tick 造成伤害**的（Goety 默认
  `CorruptedBeamDamage = 10.0`/次，且它把目标 `invulnerableTime` 清零以绕过无敌帧），上限 20 已经能打出
  很高的总伤害，**调到 100 以上基本等于必杀**。觉得太强就把 `casting.channelMaxTicks` 调小，
  或把该聚晶写进 `focus.blacklist`。
- **⑤ 用户第 5 条 · 聚晶图标改为灰黑色 + 白色**（用户原话："聚律波纹聚晶的贴图变为灰黑色和白色，
  即原本紫色的部分变为黑色"）：改了 `art/gen_ripple_focus_icon.py` 的配色 —— 盘面由紫色系改成
  **近黑轮廓 `(8,8,8)` + 深灰→近黑渐变 `(58→22)`**，亮环与圆心改成**白 `(240,240,240)` / 纯白 `(255,255,255)`**，
  青色圆心渐变也去掉了（灰黑配色下不再引入第三色）。重新生成后 **694 → 627 B**，尺寸仍是 16×16 RGBA，
  脚本**仍字节可复现**（重跑 sha256 一致）。
  ⚠️ 作者 agent **不具备图像输入能力、从未看过它**，只做了 ASCII 灰度近似检视（`art/preview_ascii.py`）；
  **观感仍需人类过目**。
- **未做**：**游戏内实测** —— 本轮只做到：
  - `gradlew build` **成功**（**约 1 分钟**，编译期只有项目**既有的基准噪声**：3 条 FML deprecated 警告 +
    1 条 `TunerBoss` 过时 API 注记，**无新增警告**）；
  - jar 条目比对 **112 → 117**（新增 5 = 4 个新 class + `tuner_wand.json`，**无删除**）；
  - 用**原始字节串搜索**核对（**不用 `javap` 文本匹配 —— `javap` 会折行**）：`CastChannel` 含 `tickChannel` /
    `channeled` / `channelEndTick` / `IChargingSpell`；`TunerWand` 含 `initCapabilities`；`TunerServant` 含
    `toggleFocusDisabled` / `toggleFocusPriority` / `clearFocusCommands` / `disabledCount` / `priorityCount` /
    `TUNER_WAND` / `DamageThrottle` / `TunerDamageRules`；`TunerServantSpawnEggItem` 含 `ServantSpawnEggItem` /
    `spawn.tamed` / `spawn.wild`；`TunerServantInteractions` 含 `isOwner` / `wild_hint` / `not_owner_hint`；
    `ModItems` 含 `tuner_wand`；
  - 用 `javap` 核对**被 reobf 成 SRG 名的覆写 / 调用**（字节串搜索会漏报这类）：`TunerWand` 的方法表为
    `m_5929_`(=onUseTick) / `m_8105_`(=getUseDuration) / `m_7203_`(=use) / `initCapabilities`；
    `TunerServant` 的 `m_6469_`(=hurt) / `m_6475_`(=actuallyHurt) / `m_7301_`(=canBeAffected)；
    `CastChannel.tickChannel` 字节码里确有 `LivingEntity.m_6117_()`(=isUsingItem) 与
    `m_6672_(InteractionHand)`(=startUsingItem)、`stopChannelUse` 里确有 `m_5810_()`(=stopUsingItem)，
    并正确 `invokeinterface IChargingSpell.castUp/Cooldown/shotsNumber`；
  - 配置 / 资源核对：`define` 调用 **71** 处、section **11** 个、`channelMaxTicks` 存在、`servant.health` 已移除、
    两份 lang 各 **43 键**且 JSON 合法、jar 内 `mods.toml` 版本 **0.0.19**、png 魔数正确（627 B / 16×16 RGBA）；
  - 部署核对：**构建产物与已部署 jar 逐字节相同**。
  ⚠️ **五条修复的运行时表现全部未实测**（详见「仍待办 15」）。
- **部署产物**：`goetytuner-0.0.19.jar`（第 57 轮首次部署时 **1,787,227 B / md5 `9363476AD6F24B6296DF59303BE0DBB7` /
  jar 内 117 条目**；**第 58 轮三次修补后最终为 1,785,988 B / md5 `77DAAFB0A5B77907C3D7A22461731FB8` / 同样 117 条目**）
  → `D:\落幕曲&原版&灾厄巫咒\.minecraft\versions\测试\mods\`（**旧的 `goetytuner-0.0.17.jar` 与
  `goetytuner-0.0.18.jar` 都已删除**，该目录现在只有 0.0.19 这一个）。
  ⚠️ **第 58 轮部署时游戏正在运行**（`java-runtime-epsilon`，启动于 19:45）—— 覆盖 jar 时**运行中的实例仍持有旧
  字节码**，**新 jar 必须重启游戏才生效**；这也是"第 57 轮的修复看起来没起效"的一个可能来源。
  ✅ **已提交并 push**（0.0.18 + 0.0.19 + 0.0.20 在第 59 轮末一并入库）。
  ⚠️ **jar 的 md5 不可复现**（`MANIFEST.MF` 的 `Implementation-Timestamp`），判断"部署的 jar 对应哪份源码"
  要**逐条目比对**，别看整体 md5（本轮又实证**三次**：加 `CastChannel` 兜底、加"长按类聚晶检测"日志、
  对齐 `getWandVisualHeight`，每次都只是小改，jar 依次为
  1,786,750 B / `C097ED89…` → 1,786,756 B / `8ECE82F3…` → 1,787,227 B / `9363476A…` → 1,785,988 B / `CAA04A08…`
  → `2D90CA05…`（第 58 轮中途）→ **1,785,988 B / `77DAAFB0…`（第 58 轮最终，即当前部署的那一个）**）。

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
| **长按类聚晶放太久 / 把 Boss 变得太强（腐化光束秒人）** | **`casting.channelMaxTicks`** | **0.0.19 新增**，默认 **20 = 1 秒**（范围 5~200），管的是 Goety **`IChargingSpell`**（腐化光束 / 震撼 / 暴雪 / 轰炸 / 旋风 / 箭雨 / 电击 / 水流 / 蒸汽 / 念力 / 吸取 / 掘地 / 进食 / 飞行 / 防护 / 流星雨 …）这类"按住右键持续放"的法术**一次能放多久**（Mob 没有松手动作，所以必须给上限）。**⚠️ 与 `maxCastWindowTicks`（默认 50）的分工**：`maxCastWindowTicks` 管**普通法术**的蓄力截断（2.5 秒）；本键管**长按类**的持续时长，两者互不影响。**调大 = 更强也更危险**：腐化光束**每 tick 造成伤害**（Goety 默认 10 点/次，还会清零目标 `invulnerableTime` 绕过无敌帧），**调到 100 以上基本等于必杀**；想让战斗更温和就调小（5 = 0.25 秒）。另有两条独立上限兜底：法术自己的 `shotsNumber`、以及硬上限 `MAX_CHANNEL_SHOTS = 400`。⚠️ **配置界面没有入口** ⇒ 只能手改 `goetytuner-common.toml` 后**重启游戏** |
| 长按类聚晶**完全不放 / 只闪一下**（0.0.18 及以前的老 bug） | （无需配置） | **0.0.19 已修**。两层根因：① `CastChannel` 原先只在前摇结束时调一次 `SpellResult`（现在按法术自己的 `Cooldown`/`shotsNumber` 反复释放）；② `AbstractBeam.tick()` 靠 `MobUtil.isSpellCasting(owner)`（= `isUsingItem() && 用物是 IWand && 杖里有聚晶`）判定存活，而 **Mob 从不 `startUsingItem`** ⇒ 光束刚生成就被丢弃。现在 `tickChannel` 会 `startUsingItem`，并自备 `focus/TunerWand`（`onUseTick` 空实现，避免 `dark_wand` 对非玩家走 `failParticles + FIRE_EXTINGUISH` 只冒白烟）。**若仍只闪一下**：先确认场上实体主手是 `goetytuner:tuner_wand`（老存档会在 `readAdditionalSaveData` 自动迁移），再开 DEBUG 看有无 `stopChannelUse` 相关日志 |
| 重音推力太强/弱 | `accentKnockback*` | 0.4/0.8/1.2 = 普通/低谷/高潮；二阶段低谷已替换为铺垫，`phase2ValleyKnockbackMultiplier` 不可达 |
| 重音手感 | `accentShakeTicks` / `accentShakeStrength` | 当前 10 tick / 2.0，调低可减轻镜头晃动 |
| **重音太密/太疏** | **`music.accentDensityDivisor`** | **0.0.13 新增**，默认 **3** = 每 3 个重音保留 1 个（数量与频率约为原来的 1/3）；**1 = 不抽稀**（回到乐谱原始的 37 个密度），9 = 最稀疏（约 1/9）。**服务端加载乐谱时统一生效** ⇒ HUD 刻度 / 击退 / 涟漪 / 提示音**一起**变，客户端无需改动。⚠️ 配置在启动时读取、乐谱在 Boss 构造时加载 ⇒ **改完需重启游戏**，已存在的 Boss 不会重新抽稀 |
| 重音特效（声波/粒子） | `accentWave` / `accentParticles` | 0.0.10 起声波 `accentWave=true`（10 环非对称径向涟漪，最高 0.794 格）、旧粒子 `accentParticles=false`；**老 toml 的 `accentParticles` 仍是 `true`，需手改**（见 §六迁移提示）；**0.0.13 起同一实体可同时存在多条波**（连发各成一条、层叠外扩） |
| 动态评分变化太慢/快 | `dpsAdjustRate` / `dynamicScoreCap` | rate=修正速率，cap=偏移上限 |
| **动态评分太强势 / 想强化「逐渐学习」** | **`scoring.learningWeightStart`（开局权重，默认 0.15）、`scoring.learningWeightRampCasts`（爬升所需施法次数，默认 60）、`scoring.learningWeightMax`（上限）** | **0.0.16 新增**。三者合成"动态偏移 × 系数"里的系数：`w = start + (max − start) × min(1, 施法次数 / ramp)` —— **静态评分（初始分类）不受影响**。**开局觉得"没打几下初始分类就失效"** ⇒ 把 `learningWeightStart` 调**低**（`0` = 完全无视动态评分、纯用初始分类）；**想让"学会"来得更慢/更快** ⇒ 调 `learningWeightRampCasts`（默认 60 次施法；调大 = 学得更慢）；**想让后期动态反馈更强** ⇒ `learningWeightMax` 调到 1 以上（默认 1 = 与旧行为持平）；只想**整体削弱**动态评分 ⇒ 把 `learningWeightMax` 也压到 1 以下。默认曲线：0 次→0.150、10→0.292、20→0.433、30→0.575、40→0.717、50→0.858、60→**1.000**（之后封顶）。⚠️ **改完需重启**（三键**只能在 `goetytuner-common.toml` 里改**，配置界面没有入口）；重启会让该 Boss 的施法计数（学习进度）**归零**。⚠️ 只统计"用聚晶施法"的前摇起手（`onCastStart`；瞬发/重音不计入）；防御 / 其他类走 `drawUniform()`、不参与评分 |
| **二阶段太肉 / 自动回血打不动** | **`phase2_buffs.phase2EffectImmunity`** | **0.0.14 新增**，默认 **true** = 二阶段**免疫**「回复 / 减伤」类药水效果（抗性提升 `DAMAGE_RESISTANCE` / 伤害吸收 `ABSORPTION` / 生命恢复 `REGENERATION` / 瞬间治疗 `HEAL` / 生命提升 `HEALTH_BOOST`）。它防的是**外部来源**（其它模组的增益光环、玩家丢的增益药水、Goety 治疗类效果等）把二阶段 Boss 变成"打不动 + 自动回血"；**Boss 自己的二阶段自施（力量 / 重振）不受影响，玩家的同类效果也不受影响**。若你**故意**想给 Boss 上增益（例如做机制向整合包），改成 `false` = 回到旧行为。⚠️ 键在 **`[phase2_buffs]` 段**（不是 `[boss]` 段）；属**新增键** ⇒ Forge 会自动补进老 toml |
| **聚晶黑名单怎么改（不再必须手改文件）** | `focus.blacklist` → **游戏内配置界面** | **0.0.14 起**：Mods 菜单 → Config → 配置界面里的「聚晶黑名单」单行输入框（预填当前值，提示「namespace:path，英文逗号分隔；留空 = 不屏蔽」），**点「完成」关屏时保存**、点「开始评分」时也顺带保存 ⇒ **改完立即生效**（不需要重启；新增拉黑与取消拉黑两个方向都即时）。仍可继续手改 `goetytuner-common.toml`。⚠️ 除黑名单外的**其余** common 配置项**依旧只能手改 toml**；⚠️ 连**他人**的服务器时改的是**本地**那份 toml、服务器侧不受影响（单机/局域网主机同进程，正常生效） |
| 附属聚晶崩溃 | `focus.blacklist` | 逗号分隔 id 加入黑名单（逐段 trim、**大小写敏感**）；**0.0.14 起可直接在游戏内配置界面填**（见上一行），也可手改 `goetytuner-common.toml`。0.0.13 起实例 toml 已含 `goety:killing_focus`（索命聚晶，对施法者反噬 125%）；`RUNTIME_BLACKLIST` 与它是**并集**、配置无法解禁 |
| 附属法杖无法启仪式 | `wand_whitelist` | 填入法杖 id（未加入 `goety:wands` 标签的附属法杖） |
| LLM 评分请求超时/失败 | `llm.apiUrl` / `llm.model` | 默认 OpenAI 端点（国际）；中国大陆环境改为 `https://api.deepseek.com/v1/chat/completions` + `deepseek-chat`；失败提示会带目标 URL 与模型名 |
| 打得太快/被秒杀 | **`lockGraceTicks` / 档位数（`maxHealth` ÷ `lockHealthInterval`）**；`maxHitDamagePercent` / `maxDamagePerSecond`（仅作保险） | **实测结论：战斗时长主要由 `lockGraceTicks`（每档最短时长，默认 10t=0.5s）与档位数（默认 216/18 = 12 档）决定**——锁血阶梯把每次命中的有效伤害钳到一档，超出部分被丢弃，所以伤害上限不改变阶梯推进速度。`maxHitDamagePercent`（默认 0.25×216≈54，0=关闭）与 `maxDamagePerSecond`（默认 0=关闭）都只在**阶梯耗尽后**（血量 ≤18 那段）才可能起作用，主要作为防「秒杀式巨额伤害」的保险 |
| Boss 卡在死亡动画不动 | `lockDeathRevive`（默认 true） | 0.0.8 已修（新增 `SEntityRevivePacket` 同步复位客户端死亡动画 + 脏状态只清动画不吃档位 + 锁血未耗尽时拦截 `remove(KILLED)`，**但 `/kill` 例外**）；若仍出现，先确认 `lockDeathRevive` 未被关闭。**0.0.17 补充**：还有一类"**血量已回正、动画却永久留着**"的形态——根因是 `deathTime` **不是同步数据**，服务端只发**一次**复位包，若那一刻客户端血量同步还没落地，客户端会**自己再把 `deathTime` 加上去**（服务端侧治不了）。0.0.17 已在 `tick()` 的**客户端分支**加**对称自愈**（`deathTime > 0 && getHealth() > 0` 即直接复位，**无配置开关、始终生效**）⇒ 至多残留 1~2 tick。若仍长时间躺着，请开 DEBUG 日志看是否有 `client stale death animation` |
| 想直接击杀 Boss（跳过锁血阶梯） | —（无对应配置） | 用 `/kill`（**0.0.9 起可用**：管理员指令识别 `DamageTypes.GENERIC_KILL`，无视锁血体系的全部保护）；**0.0.17 起「索命聚晶」也是一条路**（`goety:death` 同套后门语义，见下面一行）。常规输出仍受锁血阶梯约束 |
| **被索命秒杀太强 / 想关掉索命后门** | **`boss.deathCurseExecution`** | **0.0.17 新增**，默认 **true** = 玩家用**索命聚晶**命中 Boss 时，`goety:death` 的致死伤害**无视剩余锁血档位直接处决**（跳过宽限期免疫 / 致死截断 / 限伤·限DPS / 死亡回弹 / `remove(KILLED)` 拦截）。**觉得"被索命一发秒掉太强"** ⇒ 改成 **`false`** = 关闭后门、索命重新受锁血保护（回到 0.0.17 之前的旧行为）。⚠️ 键在 **`[boss]` 段**（与 `maxDamagePerSecond` / `lockDeathRevive` 同段）；属**新增键**（Forge 会自动补进老 toml），但**配置界面没有入口** ⇒ **只能手改 `goetytuner-common.toml`，改完需重启游戏**。**取舍提示**：关掉后门后，索命打中 Boss 会回到"被锁血拦下 → 复活"的路径（同时仍可能留下动画残留，不过 0.0.17 的客户端自愈已处理残留）；另一条路是把 `goety:killing_focus` 加进 `focus.blacklist`（**拉黑** = Boss 干脆不抽这张聚晶）。**代价侧**：索命对施法者反噬「目标当前生命值 **125%**」，所以它是有代价的处决手段 |
| **仆从太肉/太脆、施法太频/太慢、轮换不对** | **`servant.followRange` / `servant.castIntervalTicks` / `servant.rotation`** | **0.0.18 新增 `[servant]` 段**（原 4 项）。⚠️ **0.0.19 起 `servant.health` 已删除** —— 仆从的血量 / 护甲改为**与本体一致**，直接读 `[boss]` 段的 **`maxHealth`（默认 216）** 与 **`equivalentArmor`（默认 16）**（另有近战易伤 `meleeVulnerability`、限伤 `maxHitDamagePercent`、限DPS `maxDamagePerSecond` 也一并适用）。**⇒ 想调仆从肉度就改 `[boss].maxHealth`，但那会同时改 Boss 本体**（本轮取舍：用户要求"与本体保持一致"，故不做独立旋钮）。剩余 3 项：`followRange` = 32（索敌半径 / FOLLOW_RANGE，8~128；仆从是远程施法者，比近战仆从大一些）、`castIntervalTicks` = 40（两次施法间隔 = **2 秒**，0~600：指「上一发结算完」到「开始下一发前摇」的等待，**与聚晶自身冷却取更长者**）、`rotation` = "23"（施法轮换序列，每位一个通道角色：**1=防御 2=攻击 3=召唤 4=其他**，按序循环；默认攻击 / 召唤各半 —— 想让仆从更偏输出就写 `"2"`，想要它兼顾召唤就留 `"23"`）。⚠️ 三键都是**新增键**（Forge 会**自动补进**老 toml、无需手改），但**配置界面没有入口** ⇒ 只能手改 `goetytuner-common.toml` 后**重启游戏** |
| **仆从的聚晶指令不生效 / 想清空指令** | （无配置项，游戏内操作） | **0.0.19 起语义**：**只有主人**手持**聚晶**对仆从**左键 = 切换「不释放」/ 右键 = 切换「优先释放」**，两者**互斥**（左键会顺手清掉「优先」、右键会顺手清掉「不释放」）⇒ **任何一个键都能把该聚晶切回中立**。**潜行 + 左键** = **清空该仆从的全部聚晶指令**。提示会带**两张表的条目数**（如「不释放：火焰聚晶 ｜不释放 2 项 / 优先 1 项」）⇒ 便于确认设置真的生效。**归属只在刷怪蛋放置时确定**（**直接放 = 野生 / 潜行放 = 认主**）：**野生仆从**永远不接受指令（会给 `wild_hint` 提示），**别人家的仆从**同理（`not_owner_hint`），且这两种情况下这次左键 / 右键会**照常当作普通攻击 / 交互**处理（不会被吞掉）。指令随 NBT 持久化（`DisabledFoci` / `PriorityFoci`） |
| **波纹聚晶被 Boss 抽到 / 想解禁它给 Boss 用** | `focus.blacklist` | **0.0.18 起代码默认值已含 `goetytuner:tuner_ripple_focus`**（防 Boss **白得一个 0.5 秒冷却的重音**，以及那发涟漪把 **Boss 自己一起推开**）。**若故意想让 Boss 也能抽到它** ⇒ 在游戏内配置界面的「聚晶黑名单」输入框里把它删掉（**改完立即生效**，无需重启），但要自己承担"Boss 被自己的涟漪推开"的表现。⚠️ 老 toml **不会**自动带上这个新默认值（**改默认值不回填**）⇒ 需按 §六 的 **0.0.18 迁移提示**手工补一次，或用 `scripts/add_ripple_focus_blacklist.py`。⚠️ **0.0.19 未改动本键**（本轮是"加键 + 删键"，与它无关） |
| **长按类聚晶被 Boss 抽到太危险（腐化光束秒人）** | `focus.blacklist` **或** `casting.channelMaxTicks` | 0.0.19 让**长按类法术真的能持续释放**了 ⇒ 腐化光束这类**每 tick 造成伤害**（Goety 默认 10 点/次、还会清零目标 `invulnerableTime` 绕过无敌帧）的聚晶**强度显著上升**。**两条降险路径**：① 把 `casting.channelMaxTicks`（默认 20）调小到 5~10；② 直接把该聚晶写进 `focus.blacklist`（**推荐** —— 在游戏内配置界面的「聚晶黑名单」输入框里加 id，改完立即生效），让 Boss 干脆不抽它。⚠️ 黑名单是**全局**的，加进去后**玩家自己也没法用该聚晶**（玩家侧本来就正常，不需要靠这个键兜底） |
