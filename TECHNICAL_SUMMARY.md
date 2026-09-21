# Goety Tuner（调律师）技术摘要

> **0.0.20（第 59 轮 · 当前工作版本）**：用户提了**一条回归反馈 + 三条新功能**，本轮全部处理。新增 **4 个类**（`.java` **55 → 59** = `combat/ServantWandBlessing`、`ritual/TunerServantSummonRitual`）+ **1 个仪式配方 json**；改 **6 个既有 Java 文件** + **2 个 lang**（键数 **34 → 43**，**+9 / −0**）；**配置 71 → 76 项**（`[servant]` 段 **3 → 7**，新增 `wandBlessingEnabled`；段数仍 **11**）；jar 条目 **117 → 122**（新增 3、**无删除**）；协议仍 **2.1**。**① 【回归】左右键的聚晶指令提示被我误删了，已回补** —— 第 58 轮用户说"不要冗余提示"，我把 `TunerServantInteractions` 里**所有**动作栏提示都删了，连**操作结果反馈**一起删掉；用户指出"这个提示是需要的" ⇒ 现在明确分开：**要有**"一次主动操作产生了什么状态变化"（`不释放：X` / `已取消不释放：X` / `优先释放：X` / `已取消优先释放：X` / 批量版 / `这不是你的调律师仆从`，共 9 个新 lang 键），**不要**物品介绍与刷怪蛋放置提示（那部分没有回退）。**② 仆从的仪式召唤** —— **每秒 10 能量、10 秒、魔法仪式**：`soulCost: 10` + `duration: 10`（反编译 `DarkAltarBlockEntity` 实证**两者都是"每秒"口径**：灵魂扣除与 `currentTime++` 同在 `gameTime % 20 == 0` 分支里），材料 **紫水晶碎片 ×4 / 红石 / 钻石 / 金锭 / 青金石** 共 8 个基座，**中心放一把带"调律加成"的法杖**才能激活（`activation_item` 表达不了 NBT ⇒ 完全重写 `identify`；两种调律加成**任一 > 0** 即认），召唤出的仆从 **tame=true 认主**，法杖被祭坛消耗但快照留给仆从。**③ 杖的加成决定仆从强度** —— `combat/ServantWandBlessing` 按 **`调律·巫法加成`** 的 `pct = 加成 × 100` 给持续性增益：**强健**（`goety:buff`）`1 + floor((pct−10)/20)` 级、**生命恢复**（>20 Ⅰ / >60 Ⅱ）、**抗性提升**（>80 Ⅰ / >100 Ⅱ，封顶）。⚠️ 0.0.20 按用户反馈**把判定来源从「魔法伤害加成」改成「巫法加成」**（前者单次默认 +40% —— 打一次就几乎满配；后者 +10% —— 要反复击杀才爬得上去），**阈值一个都没动**；做法是**低频自愈式刷新**（1 秒一次、每次挂 3 秒，数值未变则跳过）。⚠️ **一个必须告知用户的坑**：`goety:buff`（强健）实际**只加 `ATTACK_DAMAGE`**（Goety 原文："每级增加 1 点的近战攻击伤害"），而本模组的仆从**刻意没有近战手段** ⇒ 强健对它的战斗力**几乎无影响**（忠实照搬了规格，但"越强"的直觉不成立，可选修法见 §七.14）。**④ 仆从死亡掉落召唤用杖**（**原样返回**：保留原有加成、不再额外升级 —— 与 Boss"快照 + 叠加升级"刻意对照）。**⑤ 聚晶包 / 多晶大袋批量指令** —— 手持袋子左键 = 袋内**全部**设为不释放、右键 = 全部设为优先；**全部已是该状态 ⇒ 整体清除**（与单晶的"再按一次取消"语义一致）；用通用 `ForgeCapabilities.ITEM_HANDLER` 读内容物（聚晶包 11 格 / 多晶大袋 21 格，字节码实证），非聚晶物品跳过、同 id 去重。**⑥ 顺带的做法收敛**：`WandUpgradeEvents#magicBonusOf` 提升为 `public static magicDamageBonus`（"法杖加成是多少"的唯一实现，现有三个消费方）；`TunerServant` 的互斥语义收敛到 `setDisabledExclusive` / `setPriorityExclusive` 两个私有方法（单个与批量共用）。**⑦ 【追加·用户要求】「强健」等级 → 法术强度**（`combat/BuffSpellPower` + 新增 `[casting] buffSpellPowerPerLevel`，**Int 默认 1**）：作用域**只限本模组的调律师一族**（本体 + 仆从，各按自身强健等级）。⚠️ **本轮最重要的实证**：`goety:spell_potency` 注册基础值是 **0.0**、读取是 `(int)` 截断、法术当**平铺点数**用 ⇒ **任何百分比 modifier（`MULTIPLY_TOTAL`）在该属性上恒等于 0**；因此本类用 **`ADDITION`**、比例必须是**整数点**（0.1 会被截成 0）。⚠️ 顺带查出**两处早已存在的死代码**（玩家的「调律:巫法加成」、Boss 二阶段的「力量等级×0.1」，**都是 MULTIPLY_TOTAL ⇒ 从未生效**），本轮**只报告未改**（修法需重新定义数值语义，属平衡决策），详见 §七.17。**⑧ 【追加】修掉两处「百分比法术加成从未生效」的死代码**（用户："保证实际生效和文字描述相同"）—— 新增 `combat/SpellDamageBonus`（百分比法术伤害的**唯一实现**，走 `LivingDamageEvent` ×(1+加成)），删除 `WandUpgradeEvents#refreshWitchcraftModifier` + 它的两个监听器、以及 `TunerBoss#tickPhase2Buffs` 里的 `SPELL_POTENCY` modifier；Boss 改为新增 `phase2SpellDamageBonus()`（读自身力量效果等级）。⚠️ 同时修掉**判定覆盖面**：原判定只认 `forge:is_magic`（Goety 39 个伤害类型里只有 8 个），Boss 的腐化光束/火球/hellfire 全都不在内 ⇒ 现在并上「`goety` 命名空间」判定。⚠️ 顺带不再放大自伤（索命反噬不再被自己的加成放大）。⚠️ **平衡影响**：Boss 二阶段法术伤害从"实际 +0%"变成 **+20%/+50%**，玩家巫法加成从 0 变成真 **+10%**（都是文字早就承诺过的）。⚠️ **本轮同样只做到编译 + 字节码 / 资源核对，未进游戏实测**（见 §3.21 与 §七.13）。

> **0.0.19 修补（第 58 轮）**：用户对第 57 轮交付**又提三条反馈，本轮全部处理**（**不升版本号**）。改 **5 个既有 Java 文件**（`entity/ai/CastChannel`、`entity/TunerServant`、`entity/TunerServantInteractions`、`init/TunerServantSpawnEggItem`、`focus/FocusPoolManager`）+ **2 个 lang**（键数 **43 → 34**，**−9 / +0**）；**没有新增或删除任何类与资源** ⇒ jar 条目 **仍 117**；**配置项数仍 71 / 11 段（一个键都没动）**；协议仍 **2.1**。**① 提示精简到一行** —— 只留 `tooltip.goetytuner.servant.spawn_egg`；刷怪蛋**不再有放置提示**（`useOn` 覆写已删）、`appendHoverText` **不再调 `super`**；仆从交互的 `wild_hint` / `not_owner_hint` **两条提示已删**（⚠️ **行为不变**：被拒绝的交互**仍然不吞掉**，只是**不再弹字**，拒绝原因改打 **INFO 日志**）⇒ 规律：**诊断信息走日志、不走动作栏**。**② 修「不释放的调整功能似乎完全不起效了」** —— 两条可能的病根都堵掉：**（a）野生仆从被拒（最可能的真凶）** —— 第 57 轮把 `isOwner` 收紧成"只认主人"，而刷怪蛋**默认直接放 = 野生** ⇒ **用户按默认方式放的仆从有一条算一条全被 `canCommand` 拒绝**；现在 **`owner == null`（野生）也放行**，**只有别人家的仆从**才拒绝。**（b）潜行劫持左键** —— 第 57 轮自己加的"**潜行 + 左键 = 清空全部指令**"会在潜行时抢走左键（得到的是"清空"、看起来就是"左键切不动"）⇒ 该功能连同 `clearFocusCommands` / `disabledCount` / `priorityCount` **一并删除** ⇒ 规律：**不要给同一个输入加"隐藏的组合键"**。**③ 修「箭雨聚晶几乎没有持续」** —— **识别没问题**（`ArrowRainSpell extends EverChargeSpell`，本就在闭包内），**真因是通道预算把蓄力算进了总时长**：`ArrowRainSpell.castUp` 解算默认 **20 tick**，而第 57 轮的预算正是 `channelMaxTicks` 默认 **20** ⇒ **蓄力吃光预算、一发即收**（任何 `castUp` ≥ 20 的长按类聚晶都如此）。**修法 = 两段预算**：`蓄力 = clamp(round(castUp × 倍率), 0, casting.maxCastWindowTicks)` + `持续 = max(5, casting.channelMaxTicks)`；并**删掉"按 `shotsNumber` 提前收手"**（`DarkWand` 只用它记发数、**从不据此停火**）。**实测口径**：腐化光束 **20 tick**、箭雨 **40 tick**。⚠️ **平衡风险照旧**：腐化光束每 tick 造成伤害，`channelMaxTicks` 调到 100 以上**基本等于必杀**。⚠️ **本轮同样只做到编译 + 字节码 / 资源核对，未进游戏实测**（见 §3.20）。

> 用户对 0.0.18 交付的五条反馈全部处理（**第 57 轮**）：① 聚晶指令语义重做（两种状态**互斥**，「不释放」现在必然可取消）；② 仆从的**血量/护甲/减伤限伤与本体一致**（抽出 `DamageThrottle` / `TunerDamageRules` 共用实现）；③ 仆从刷怪蛋改成**Goety 仆从蛋范式**（直接放=野生、潜行放=认主）；④ 修「长按持续释放类聚晶只放一瞬间就停」（两层根因：只结算一次 + `AbstractBeam` 靠 `isUsingItem` 判定存活）并给出用时上限 `casting.channelMaxTicks`；⑤ 聚晶图标改灰黑+白。详见下文 §3.19。下文旧版本说明保留为历史记录。

> **0.0.19 发布（第 57 轮）**：改 **6 个既有 Java 文件 + 4 个新类**（`.java` 共动 **10** 个，总数 **51 → 55**）+ **2 个 lang**（键数 37 → **43**，即 **+7 新增 / −1 删除**）+ **1 个新模型 json** + **1 个重绘图标 png** + **1 个修改的 art 脚本** + 版本号；**配置项数不变仍 71 项 / 11 段**（新增 `casting.channelMaxTicks` 1 项、**删除**死配置 `servant.health` 1 项）；jar 条目 **112 → 117**。新增 4 个类：`combat/DamageThrottle`、`combat/TunerDamageRules`、`focus/TunerWand`、`init/TunerServantSpawnEggItem`；新增资源 `models/item/tuner_wand.json`（内容仅 `{"parent": "goety:item/dark_wand"}`）；聚晶图标重新生成为灰黑+白（694 → **627 B**）。① **【④ 长按类法术只放一瞬间 —— 本轮最有价值的一处修复】** 现象：腐化 / 震撼 / 炼狱这类"长按持续释放"的聚晶在调律师身上只放一瞬间就停，被当成瞬发用了。**两层根因（均已反编译实证）**：（a）**只结算一次** —— 玩家路径 `DarkWand.onUseTick` 对 `IChargingSpell` 是"蓄力到 `castUp` 后每 `Cooldown` tick 调一次 `MagicResults`（→`SpellResult`）"直到松手，而 `CastChannel` 原先只在前摇结束时调**一次** `SpellResult`（于是轰炸/雷电/暴雪这类"每发生成一个实体"的法术只出一发）；（b）**实体下一 tick 就自毁** —— `AbstractBeam.tick()`（腐化光束的基类）里有 `if (itemBase && !MobUtil.isSpellCasting(owner)) discard();`，而 `MobUtil.isSpellCasting` = `isUsingItem() && getUseItem().getItem() instanceof IWand && !WandUtil.findFocus(e).isEmpty()`，**Mob 从不 `startUsingItem`** ⇒ 光束刚生成就被丢弃 ⇒ 就是玩家看到的"闪一下"。**修法**：`CastChannel` 新增通道型路径 `tickChannel` —— ① `startUsingItem(MAIN_HAND)` 让施法者**真的在"使用法杖"**（每 tick 自愈式重设，结束/打断/异常一律 `stopUsingItem`）；② 按法术自己的 `Cooldown` / `shotsNumber` 节奏反复释放。**为此自备 `focus/TunerWand`**：不能直接让 Mob 用 `goety:dark_wand`，因为 `DarkWand.onUseTick` 对**非玩家施法者**会走 `MagicResults` 的 `failParticles + FIRE_EXTINGUISH` 分支（既不放法术、又冒白烟响灭火音）；`TunerWand implements IWand` 且 `onUseTick` 空实现、`getUseDuration=72000`、显式委托 `IWand.super.initCapabilities`（`SoulUsingItemHandler` 依赖它）、`SpellType.NONE`，外观完全一致（模型继承 `goety:item/dark_wand`）。Boss 与仆从主手均改为 `goetytuner:tuner_wand` 并在 `readAdditionalSaveData` 做**旧档迁移**；顺带落地了遗留待办「Boss 专属法杖（TunerWand）」。**用时上限**：新增 **`casting.channelMaxTicks`（Int，默认 20 = 1 秒，5~200）**，三条独立收口（总时长 / 法术自身 `shotsNumber` / `MAX_CHANNEL_SHOTS=400`）。⚠️ **同时澄清**：旧键 `casting.maxCastWindowTicks`（默认 50）**一直在生效**，但它管的是"把前摇截断到 2.5 秒"、**不管持续释放** —— 长按类法术的 `castDuration` 默认 72000 被它截成 50，所以旧行为就是"站桩 2.5 秒 → 放一发 → 结束"，正是这个 bug 的一部分。② **【① 聚晶指令「不能取消」】** 语义重做：0.0.18 用两张**互不相干**的表存「不释放」/「优先」，同一聚晶可同时命中（此时"不释放"胜出）⇒ "先右键设优先、再左键设不释放，然后按右键想取消"会出现**按了没反应**的错觉，这就是"不能取消"的来源。现在两态**互斥**（左键顺手清"优先"、右键顺手清"不释放"），任一按钮都必然能让该聚晶回到中立；新增**潜行+左键 = 清空全部指令**的逃生通道；提示文案改为**列出两张表的条目数**便于验证。③ **【② 仆从与本体一致】** `TunerServant` 构造函数直接读 `boss.maxHealth`(216) / `boss.equivalentArmor`(16)，`KNOCKBACK_RESISTANCE` 对齐 1.0（原 `servant.health`=40 的死配置已删）；并把 Boss 内联的减伤/限伤抽成**共用实现**：`combat/TunerDamageRules`（身份免疫 / 近战易伤判定，`TunerBoss` 改为调用它、**行为不变**）+ `combat/DamageThrottle`（限伤 + 滑动 1 秒限DPS，0.0.6 时内联在 `TunerBoss` 里的环形缓冲整体搬入，两边各持一份实例、共用同一份代码）；仆从新增 `hurt`/`actuallyHurt` 覆写，唯一差别是**没有后门豁免**。⚠️ **刻意不含锁血阶梯**（`lockMark`/宽限期/致死截断/`/kill` 与索命后门）—— 那是 Boss 招牌机制，仆从若也锁血就成了打不死的怪。④ **【③ 刷怪蛋范式】** 新增 `init/TunerServantSpawnEggItem`，**直接继承 Goety 本体的 `ServantSpawnEggItem`**（本体所有仆从蛋用它）：潜行放置 ⇒ `setTrueOwner(player)`、直接放置 ⇒ 野生（无主人 ⇒ `Summoned.finalizeSpawn` 会 `setWandering(true)`）；本类只外加动作栏提示与一行 tooltip。**0.0.18 的"野生仆从第一次被下指令时认主"（`adoptOwnerIfUnowned`）已彻底删除** —— 归属只在放置时确定，`TunerServantInteractions#isOwner` 只认主人，野生/他人仆从给出提示且**不吞掉**本次攻击/交互。⑤ **【⑤ 图标】** `art/gen_ripple_focus_icon.py` 配色由紫改灰黑+白（近黑轮廓 + 深灰→近黑盘面 + 白色亮环/圆心），重生成 **627 B**。**验证程度**：`gradlew build` 成功（约 1 分钟，仅既有基准噪声）+ jar 条目 112→117 + 原始字节串核对 + `javap` 核对被 reobf 的覆写与调用（`TunerWand.m_5929_/m_8105_/m_7203_`；`TunerServant.m_6469_/m_6475_/m_7301_`；`CastChannel.tickChannel` 内确有 `LivingEntity.m_6117_()`=isUsingItem 与 `m_6672_()`=startUsingItem，`stopChannelUse` 内确有 `m_5810_()`=stopUsingItem）+ 配置/资源核对（define 71 处 / 11 段 / lang 各 43 键 / `mods.toml` 0.0.19）+ 部署产物逐字节比对。⚠️ **五条修复的运行时表现全部未实测**；图标仍是程序化生成、作者 agent 从未看过。部署：`goetytuner-0.0.19.jar`（**1,785,988 B / md5 `77DAAFB0A5B77907C3D7A22461731FB8` / jar 内 117 条目**）→ `versions\测试\mods\`（旧 0.0.18 已删）。详见 §3.19。
>
> **上一轮（0.0.18，第 56 轮）发布**：**调律波纹聚晶**（与调律师铺垫期重音同款的一次性涟漪，5 灵魂 / 0.2s 蓄力 / 0.5s 冷却，已写入黑名单）＋ **调律师仆从**（Goety `Summoned` 体系 + 复用 Boss 的聚晶池与施法通道，可由玩家手持聚晶左键/右键下达"不释放 / 优先释放"指令）；改 **16 个既有 Java 文件 + 6 个新 Java 类**（`.java` **45 → 51**）+ **2 个 lang**（键数 **27 → 37**）+ **3 个新资源** + 版本号；**新增 1 个配置段** `[servant]`（4 项）并把 `focus.blacklist` 的**默认值**改了 ⇒ 配置 **67 → 71 项**、section 数 **10 → 11**；jar 条目 **102 → 112**。本轮第一次加入**给玩家用的内容**（此前 55 轮全部围绕 Boss 本身），因此多了三条新约束：玩家物品的数值要走 Goety 的**玩家施法管线**、仆从要走 Goety 的 **`Summoned`** 体系、与 Boss 共用的表现层必须**同源**而不是各写一份。① **`combat/AccentRipple`（本轮最重要的一处重构）**：把 `TunerBoss#accentKnockbackPulse` 里的四件事（径向击退 / END_ROD 冲击环+NOTE / 声波涟漪 / 紫水晶提示音）整体抽成**唯一实现**，Boss 侧只剩四行转发 —— 用户要求聚晶"与铺垫期重音**同款**"，而"同款"的正确做法是**共用同一份代码**（本项目红线：同一职责只允许一处实现）。Boss 与聚晶的差别只有两处、都由调用方决定：**力量/音调**（Boss 按阶段 0.4/0.8/1.2 与 0.9/1.4/0.6；聚晶固定取铺垫期 0.4 / 0.9）与**友军豁免**（`isFriendlyTo`：自己 / 自己的宠物 / 自己的 `IOwned` 仆从；Boss 无友军故传 null）。② **`SAccentWavePacket` 载荷由「实体 id」改为「三个 double 的世界坐标」**（通道 id 仍为 3）：原先客户端要靠 `level.getEntity(id) instanceof TunerBoss` **反查位置**，而玩家放的涟漪锚点是一个**裸坐标**、无实体可查；改成下发坐标后服务端算好的触发点即权威值（顺带更准）。**同 id 不同结构会解码错位** ⇒ 协议号 **`2.0` → `2.1`**（严格匹配不变）；广播方式由 `TRACKING_ENTITY` 改为"锚点 64 格内按玩家广播"。③ **调律波纹聚晶**（`focus/RippleSpell` + `goetytuner:tuner_ripple_focus`）：**用户指定的 5 / 0.2s / 0.5s 分别落在 `ISpell` 的 `defaultSoulCost()`=5、`defaultCastDuration()`=4 tick、`defaultSpellCooldown()`=10 tick 上**，没有绕管线；物品类直接复用 Goety 的 `MagicFocus`（自带标准 tooltip / 附魔规则 / `IFocus` 契约）。⚠️ 这三个是**基础值**，Goety 会再叠施法者修正（耗蓝乘 SoulDiscount/环境加成、蓄力乘 `ModAttributes.getCastingSpeed` 与"减半施法时间"饰品、冷却乘 `ModAttributes.getCooldownDiscount`）—— 这是 Goety 所有聚晶的统一规则，刻意不覆写绕过。效果尊重 `music.accentParticles`/`accentWave`/`accentSound` 三个开关。④ **聚晶的注册次序（用户点名要求）**：`FocusPoolManager#initIfNeeded()` 在 `ServerStartingEvent` 遍历 `ForgeRegistries.ITEMS` 收集全部 `IFocus`，因此三条约束必须同时满足 ——（a）物品必须注册在**同一个 `ModItems.ITEMS` DeferredRegister** 上（该 register 在 mod 构造函数第一时间注册、在远早于 `ServerStartingEvent` 的 `RegisterEvent` 统一落地；另起一个 register 却忘了 `.register(modBus)` 就永远看不到 —— 这正是本项目**在刷怪蛋上踩过的注册时序坑**）；（b）`getSpell()` 必须立刻返回非 null 单例（`MagicFocus` 构造函数即存字段，天然满足）；（c）**必须同时拉黑** ⇒ `focus.blacklist` 默认值改为 `goetytwilight:destruction_focus, goetytuner:tuner_ripple_focus`（否则 Boss 会抽到它：白得一个 0.5 秒冷却的免费重音，而且那发涟漪会把 Boss 自己一起推开）。⚠️ **这是"改已有键的值"不是"加键"**，Forge **不会**用新默认值覆盖老 toml（红线 8）⇒ 老存档必须手工迁移；已提供 `scripts/add_ripple_focus_blacklist.py`（自动探测编码 / 备份 / 只改那一行 / `difflib` 复核差异仅限该行 / 校验 CRLF 与无 BOM）并**已对游玩实例跑过**（现为 `goetytwilight:destruction_focus, goety:killing_focus, goetytuner:tuner_ripple_focus`，备份 `.bak-before-blacklist-ripple`），也可用 0.0.14 起的游戏内「聚晶黑名单」输入框补。⑤ **调律师仆从**（`entity/TunerServant`）：照诡厄巫法的"生物↔仆从"惯例 —— `extends com.Polarice3.Goety.common.entities.ally.Summoned` ⇒ 主人归属 / 跟随 / 目标牵引 / 仆从加血与传送 / 日照规则 / 指令管理**全部继承**（这就是"一般仆从的性质"）；实体注册照抄 Goety 仆从写法（`MobCategory.MONSTER` + `sized(0.6F,1.95F)` + `clientTrackingRange(8)`，**与 Boss 同尺寸**）。与 Boss **同源复用**：同一个 `FocusPoolManager`（每只仆从各一份**实例**）、同一个 `CastChannel`（实现 `TunerCastCallback` 即可）、同一套模型贴图与渲染层（三个渲染类**泛型化** + 新增 `entity/OrbHighlightSource` 接口让 `TunerOrbLayer` 不再依赖具体实体类；仆从侧只新增 `TunerServantRenderer` 一个文件）；`FocusCategory.parseRoleSpec` 从 `TunerBoss` 上提到枚举供两边共用。**刻意不带**：音乐三阶段、锁血、瞬移、重音、嘲讽、二阶段。⑥ **聚晶指令（本轮唯一的新玩法）**：`entity/TunerServantInteractions` 订阅 `AttackEntityEvent`（左键=切换「不释放」）与 `PlayerInteractEvent.EntityInteract`（右键=切换「优先释放」），手持 `IFocus` 时生效、再按一次取消；**`javap` 反编译映射版 MC 实证钩子位置** —— `Player.attack` 偏移 0 即 `ForgeHooks.onPlayerAttackTarget`（取消即 return，故不会真的打到仆从）、`Player.interactOn` 偏移 30 即 `ForgeHooks.onInteractEntity`（早于 `Entity.interact` 偏移 57 与 `interactStack`）。**⚠️ 关键坑：只在服务端判定与取消** —— 两个事件在客户端也会触发一次，若在客户端也取消，`LocalPlayer` 会提前 return、**连攻击包都不发**，机制直接失效。指令存 `FocusPoolManager` 的**实例字段**（不是 static，理由同 0.0.16 的 `castCount`）并落盘 NBT；优先聚晶经新增的 `CastChannel.beginCast(level, entry, mult)` **插队**（跳过抽签、其余流程完全共用 `startCast`），禁放经 `draw/drawUniform` 的 `isUsable` 过滤（禁放优先于优先）。**认主**：刷怪蛋/指令生成的仆从无主，若严格要求"主人才能指挥"则该机制**完全不可达**（用户已确认仆从只用刷怪蛋获取）⇒ 无主仆从在**第一次被下达聚晶指令时认该玩家为主人**；别人家的仆从完全不干预。⑦ 三处与 Boss 对齐的细节：**免疫 `GoetyEffects.SUMMON_DOWN`**（与 Boss 同取舍，否则仆从越召唤越废）、主手暗法杖 **`setDropChance(MAINHAND, 0)`**（配发装备不该在死亡时掉出来）、召唤物数量按 `gameTime` **缓存**（红线 13：热路径不许每 tick 全量扫描）。⑧ 仆从**刻意不调用** `FocusPoolManager#noteCast()` —— 0.0.16 的「逐渐学习」是给 Boss 设计的，仆从应永远以初始分类为准（保持起始权重 0.15），已写在字段 javadoc 里以免后人误判成漏调。**验证程度**：`gradlew build` 成功（**1m19s**，编译期只有项目既有的基准噪声：3 条 FML deprecated 警告 + 1 条 `TunerBoss` 过时 API 注记，**无新增警告**）+ jar 条目比对（102 → 112，**无删除**）+ 原始字节串核对 + 被 reobf 成 SRG 名的覆写用 `javap` 核对（`TunerServant.m_7301_` 正确 `invokespecial Summoned.m_7301_`；构造函数确有 `m_21409_(EquipmentSlot,F)`=setDropChance）+ 配置/资源核对（define 71 处 / `[servant]` 4 项 / lang 各 37 键 / `mods.toml` 版本 0.0.18 / png 魔数）。⚠️ **一切运行时表现均未实测**（聚晶手感、涟漪观感、仆从 AI 与指令交互、认主、联机协议 2.1），且**聚晶图标是程序化生成的，作者 agent 不具备图像输入能力、从未看过它**。部署：`goetytuner-0.0.18.jar`（**1,778,838 B / md5 `5946D71FA425B8F84E2F7E1CF4B5A8E3` / jar 内 112 条目**）→ `versions\测试\mods\`（旧 0.0.17 已删）。详见 §3.18。
>
> **上一轮（0.0.17，第 55 轮）发布**：索命聚晶后门（`goety:death` 可无视锁血直接处决调律师）＋ 修客户端死亡动画残留（血量回正后自愈复位）；改 **2 个 Java 文件**（`entity/TunerBoss`、`config/TunerCommonConfig`）+ 版本号，**只改代码、不动任何贴图/资源**（`.java` 仍 **45** 个、jar 条目仍 **102**，无新增类/贴图）；**新增 1 个配置键** ⇒ 配置 **66 → 67 项**（`boss` 段 **19 → 20**，其余段**未变**，section 数仍 **10**）。**用户反馈**：玩家用**索命聚晶**打中调律师时，Boss 会进入"动画已经死了、血量却回弹"的破状态（索命造成的是"等同于目标当前生命值"的致死伤害，被锁血体系拦住后又复活，客户端动画却留在原地）。用户说「如果可以的话给索命的成功伤害直接开个后门；如果麻烦的话修一下动画问题」——**两个都做了**。① **索命聚晶后门**（`entity/TunerBoss` + `config/TunerCommonConfig`）：**先做实证、再动手** —— 反编译 Goety 的 `KillingSpell.SpellResult` 得调用链 `ModDamageSource.deathCurse(target)` → `MobUtil.hurtCalculation(...)` → `target.hurt(source, amount)`（**走 `hurt()`，我们的覆写能看到**）；`ModDamageSource.DEATH` 的键名在静态初始化里是 `create("death")`，jar 内亦有 `data/goety/damage_type/death.json`（`message_id` = `goety.death`）⇒ 伤害类型是 **`goety:death`**；**全 jar 扫描 `deathCurse` 的引用只有 `KillingSpell` 一处**（外加其在 `ModDamageSource` 的定义）⇒ 用该伤害类型匹配**恰好等价于"索命聚晶"**，不会误伤别的法术。**实现**：新增字段 `deathCursePending` + 常量 `GOETY_DEATH_CURSE`（`ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("goety","death"))` —— **不需要编译期依赖 Goety 的类**，未装 Goety 时该键永不匹配）；`hurt()` 识别后置标记 + 打 INFO + `return super.hurt(...)`（跳过身份免疫 / 宽限期免疫 / 致死截断）；`maintainDeathState()` 见标记直接 `return`（不回弹）；`canStillRevive()` 返回 false（不拦 `remove(KILLED)`）⇒ **索命能真正处决，无视剩余锁血档位**。**关键坑**：`actuallyHurt()` 的限伤 / 限DPS 也必须为它开口子 —— `goety:death` **不在任何 `BYPASSES_*` tag 里**，不显式列出的话"等同于目标当前生命值"的致死伤害会被限伤削掉、后门等于失效。状态正常时清除标记；新增开关 **`boss.deathCurseExecution`**（默认 **true**，false = 关闭后门回到旧行为）。**代价**：索命会对施法者反噬"目标当前生命值 125%"，所以这是**有代价的处决手段**。② **修「动画死了血量回弹」的根因（客户端残留）**：`deathTime` **不是同步数据**（0.0.8 已实证）—— 客户端的 `deathTime` 由客户端自己的 `LivingEntity.tickDeath()` 递增，而它只看**客户端本地血量**；服务端复活时只发**一次** `SEntityRevivePacket`，若那一刻客户端的血量同步还没落地，客户端会在复位后**又自己把 deathTime 加上去**，此后血量虽变正、动画再没人清 ⇒ **永久躺着**；服务端侧的"情形 A"治不了这一侧。修法：在 `tick()` 的**客户端分支**加一条**对称自愈** —— 客户端同样知道血量（`DATA_HEALTH_ID` 是同步数据），`deathTime > 0 && getHealth() > 0` 即视为脏状态、直接 `resetDeathAnimation(...)`（该方法只在服务端发包，客户端调用不产生网络流量）⇒ **至多残留 1~2 tick**。⚠️ **尚未进游戏实测**（本轮只做到编译通过 + jar 条目核对）。部署：`goetytuner-0.0.17.jar`（**1,757,576 B / md5 `B68C4D4FF9FC650796EDB003D2C53009` / jar 内 102 条目**）→ `versions\测试\mods\`（旧 0.0.16 已删）。详见 §3.17。
>
> **上一轮（0.0.16，第 54 轮）发布**：「逐渐学习」——给**动态评分**（实战学到的偏移）加一个随**该调律师个体**施法次数爬升的权重系数，开局用低权重让**初始评分**（配置 / LLM 分类）主导，随实战逐次把话语权交棒给**动态反馈**。改 **3 个 Java 文件**（`focus/FocusEntry`、`focus/FocusPoolManager`、`entity/TunerBoss`）+ 版本号，**只改代码、不动任何贴图/资源**（`.java` 仍 **45** 个、jar 条目仍 **102**，无新增/删除类与资源）；**新增 3 个配置键** ⇒ 配置 **63 → 66 项**（`scoring` 段 **3 → 6**，其余段**未变**，section 数仍 **10**）。① **`FocusEntry.rouletteWeight(...)` 新增第 4 参数 `dynamicScale`**：权重由 `|静态评分 + 动态偏移| + 保底基数` 改为 `|静态评分 + 动态偏移 × dynamicScale| + 保底基数` —— **只缩动态部分，静态评分（初始分类）完全不受影响**；攻击类与召唤类两条公式都改（召唤类是生存分 / 输出分**两处**偏移各乘一次）。② **`FocusPoolManager` 新增每实例学习状态**：`castCount`（该个体施法次数；存在**实例字段而非 static** —— `createFightPools()` 每只 Boss 各建一份、`FocusEntry` 也是实例级复制 ⇒ 学习进度**天然是个体私有**的，与动态偏移生命周期一致）、`noteCast()`（由 `TunerBoss.onCastStart` 每次成功施法调用，按 `w = start + (max − start) × min(1, castCount / ramp)` **线性**重算，**每跨 10% 里程碑打一条 INFO** `[Tuner] Learning weight ...`）、`learningWeight()` / `castCount()` getter；`draw()` 把系数作为**第 4 实参**喂给 `rouletteWeight`（**已用 `javap` 验证调用链**：偏移 178 `learningWeight()` → 局部变量 14 → 偏移 234 `dload 14` → 偏移 236 `invokevirtual rouletteWeight:(D[DZD)D`）。**`drawUniform()`（防御 / 其他类）本来就不走评分，不受影响**。③ `entity/TunerBoss.onCastStart` 里调 `pools.noteCast()`（**只统计"用聚晶施法"的前摇起手**；瞬发 / 重音不经过该回调，故**不计入**）。④ **新增 3 个配置键**（`scoring` 段）：`learningWeightStart`（默认 **0.15**，0~1）、`learningWeightMax`（默认 **1.0**，0~2）、`learningWeightRampCasts`（默认 **60**，1~1000）。**默认曲线**：施法 0 次→**0.150**、10→0.292、20→0.433、30→0.575、40→0.717、50→0.858、60→**1.000**（之后封顶）⇒ 开局由**初始分类**主导、随实战逐次交棒给**动态反馈**。⚠️ **`FocusEntry.getEffectiveAttackScore()` / `getEffectiveSurvivalScore()` 现为"未缩权的"视角**（原先正是轮盘权重的输入），**保留作诊断用、不再被抽取路径调用**（**未删除**）。⚠️ **尚未进游戏实测手感**（本轮只做到编译通过 + `javap` 字节码核对 + jar 条目核对）。部署：`goetytuner-0.0.16.jar`（**1,756,720 B / md5 `816C6640E9486FFAFE3B88FA5B2C6D0E` / jar 内 102 条目**）→ `versions\测试\mods\`（旧 0.0.15 已删）。详见 §3.16。
>
> **更早（0.0.15，第 53 轮）发布**：**只有贴图精修 + 版本号 + 文档，零 Java 改动、无新增/删除资源、配置项数不变（仍 63 项 / 10 段）**。精修 `src/main/resources/assets/goetytuner/textures/entity/tuner.png`（64×64 RGBA，2520 → **2676 B**）：保留紫黑礼服 / 紫色袖口裤靴 / 浅色领巾 / 青色胸饰，精修**翻领边缘、衣袖明暗、袖口细边、裤缝、靴口层次**；原稿由 imagegen 生成，再**按最近邻采样重新装配回原有 UV**（生成图未严格保持矩形位置，故重新测量图块）。**已独立核验（以 0.0.14 为基准逐像素差分，不依赖其自带脚本）**：头部区 **y0..15 全宽 64 列 0 个像素差异**、全图 **alpha 蒙版完全一致**（未增删不透明像素）、身体区改动 **1247 像素**、6 个主要 UV 面无透明空洞。制作过程入库：`art/REFINEMENT.md`（报告 + 最终提示词）、`art/assemble-refinement.ps1`（可复现装配）、`art/body-refined-source.png`（原稿）、`art/tuner-before-refinement.png`（精修前备份）、更新后的 `art/preview.png`。⚠️ **未做游戏内画面验收**——观感仍待人类 / 能读图的模型确认；`art/preview.png` 是**平面正背面拼接图，不是游戏截图**。部署：`goetytuner-0.0.15.jar`（**1,755,456 B / md5 `1865ECF4EB22989319FD746806320776` / jar 内 102 条目**）→ `versions\测试\mods\`（旧 0.0.14 已删）。
>
> **更早（0.0.14，第 52 轮）发布**：该轮两件事，改 **4 个 Java 文件**（`client/TunerConfigScreen`、`focus/FocusPoolManager`、`entity/TunerBoss`、`config/TunerCommonConfig`）+ **2 个 lang** + 版本号，**只改代码、不动任何贴图/资源**（`.java` 文件数仍 45、jar 条目仍 102）；**新增 1 个配置项** `phase2_buffs.phase2EffectImmunity`（Boolean，默认 **true**）⇒ 配置 **62 → 63 项**（`phase2_buffs` 段 **3 → 4**，`boss` 段仍 **19**，section 数仍 10）。① **配置界面新增「聚晶黑名单」输入框**（`client/TunerConfigScreen` + `focus/FocusPoolManager`）：`focus.blacklist` 是 common 配置，而本模组的 `TunerConfigScreen` **顶替了 Forge 默认的 toml 编辑器**，该键此前在游戏内**完全没有入口**、只能手改文件；现补一个单行 `EditBox`（预填当前值，提示「namespace:path，英文逗号分隔；留空=不屏蔽」），**点「完成」关屏时保存**（最自然的"改完了"信号），点「开始评分」时也**顺带保存**；保存动作 = `FOCUS_BLACKLIST.set(v)` + `.save()` + `FocusPoolManager.refreshBlacklist()` ⇒ **改完立即生效**（`getBlacklist()` 以 raw 字符串为缓存键，值一变缓存自动失效 ⇒ 下一次抽取就过滤）。**新增 `FocusPoolManager.refreshBlacklist()`**：`initIfNeeded()` 扫描时是**直接 `continue` 跳过**黑名单聚晶（它们不进 `ALL_ENTRIES`），所以"**新增**拉黑"能靠 `draw()/drawUniform()` 的实时过滤立刻生效，但"**取消**拉黑"必须重扫才会回来；而重扫若 `new` 出全新 `FocusEntry`，会让实体侧那些**按对象身份**记录的状态失效（`TunerBoss.activeVisualCasts` 身份集合、`CastChannel.current`）⇒ 出现「立方体高亮卡住 / 施法收尾回调对不上」这类隐蔽问题。故新方法按 `namespace:path` 建索引**复用已有 `FocusEntry` 对象**，只为"这次才被解禁"的聚晶新建（它们此前不可能在施法中，故安全），然后重建 `STATIC_POOLS` 并重新 `applyTo` 分类。新增 4 个 lang 键（zh_cn / en_us 各 4 个）：`config.goetytuner.blacklist.label` / `.blacklist` / `.blacklist.hint` / `.blacklist.saved`。限制：连他人的服务器时改的是**本地**那份 common toml，服务器侧不受影响（单机/局域网主机同进程，正常生效）。② **二阶段免疫「回复 / 减伤」类药水效果**（`entity/TunerBoss` + `config/TunerCommonConfig`）：扩展现有的 `canBeAffected` 覆写（它同时是 `addEffect` 与 `forceAddEffect` 的**第一道**判定，在这里返回 false 就是真正的免疫，而不是"加完再清"）；免疫名单 = **抗性提升** `DAMAGE_RESISTANCE` / **伤害吸收** `ABSORPTION` / **生命恢复** `REGENERATION` / **瞬间治疗** `HEAL` / **生命提升** `HEALTH_BOOST`，**仅当 `music.isPhase2()`** 时生效。**刻意只列这 5 项而不是"所有 beneficial"**：Boss 自己的二阶段增益（力量 `DAMAGE_BOOST` 与重振 `RALLYING`）也是 beneficial，一刀切会把它们一起禁掉；名单集中在私有 `isPhase2Immune(...)`，以后要加（例如某个附属的自定义减伤）加一行即可。新增配置 `phase2_buffs.phase2EffectImmunity`（Boolean，**默认 true**），false = 回到旧行为；不影响玩家的同类效果，也不影响 Boss 自己的二阶段自施。详见 §3.15。
>
> **更早（0.0.13，第 51 轮）修复**：该轮改了 **7 个 Java 文件** + 版本号，**只改代码、不动任何贴图/资源**（`.java` 文件数仍 45、jar 条目仍 102）；**新增 1 个配置项** `music.accentDensityDivisor` ⇒ 配置 **61 → 62 项**（`music` 段 12 → 13，section 数仍 10）。① **修「玩家被击杀复活后（未走出索敌范围）背景音乐丢失」**：`BossMusicManager` 原先只以静态字段 `music != null` 当作「在播」，**从不与声音引擎核对**，而死亡/复活会让引擎把循环实例悄悄摘除（RECORDS 音量为 0 / channel 被停止 / `SoundEngine.reload()→destroy()→stopAll()` / `play()` 在未 loaded 时静默返回），字段却仍非 null ⇒ 只要服务端 `playing` 一直为 true（没脱战）`startMusic` **永不重入** = **永久静音**；且 `onPlaySound` 同样只看字段 ⇒ **连原版背景音乐也一起被永久取消**（用户症状「整个 BGM 都没了」）。改为每 tick 用 **`SoundManager.isActive(music)`**（1.20.1 自带 API）核实实例是否真的在响，失效即清空字段、下一 tick 自动重建（**自愈**），并加 **10 tick 防抖**；`onPlaySound` 判据同步改为 `music != null && isActive(music)`。详见 §3.14(1)。② **重音标记滚动平滑 + 高潮标记改小长条**（`MusicBarHud`）：`fill()` 只能落在整数像素、而刻度是 1~2px 细条，取整后**逐像素跳动**；改为**亚像素覆盖**（小数部分按比例摊到相邻两列，亮度重心连续移动）；高潮的「中」字（贯通竖线 + 空心方框）改为**小长条**（高潮 2×6 / 低谷 1×6 / 铺垫 1×4，竖向居中）。③ **涟漪多波共存**（`AccentWaveRenderer` 的 `Map` 改 `List` + `MAX_WAVES = 32`）：二阶段进场重音每 5 tick 一发，原先后一发**顶掉**前一发、只看到一条波反复重播。④ **重音抽稀**：新增 `music.accentDensityDivisor`（默认 **3**、范围 1~9）在**服务端加载乐谱时**「每 N 个保留 1 个」⇒ HUD 刻度（经 `SMusicSyncPacket` 全量同步）/ 击退 / 涟漪 / 提示音**一起**变稀疏、客户端零改动（默认乐谱 37 → **13** 个重音）。⑤ **立方体高亮改「向白插值」+ 两层自发光外壳当发光**（`TunerOrbLayer`）：原 `min(1, color*(1+glow*0.8))` 因各分量已接近 1 而被截断、几乎看不出高亮；发光**不能用原版描边**（MC 的发光是**整实体级** framebuffer 后处理 `OutlineBufferSource`，无法只描一颗立方体），故改用 `entityTranslucentEmissive` 外壳。⑥ **药水可观测性**：8 处 `addEffect` 的 boolean 返回值原先全被丢弃 ⇒ 被 `canBeAffected` / `MobEffectEvent.Applicable` 拒绝时静默失效；新增 `applySelfEffect` 打 WARN（已用于 boss 自身 4 处），并完成药水现状审计（详见 §3.14(6)）。⑦ 配置侧把 **`goety:killing_focus`（索命聚晶）** 加入 `focus.blacklist`——**这是配置侧改动、不在 jar 内**；且 `focus.blacklist`（乃至整个 `goetytuner-common.toml`）**无法在游戏内配置界面修改**。0.0.10 美术项与 0.0.12 新外观仍待游戏画面验收，见 [ART_ASSETS_REPORT.md](ART_ASSETS_REPORT.md)。

> 版本：0.0.19 ｜ 整理日期：2026-09-20 ｜ 覆盖轮次：第 1~57 轮
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
| 源码规模 | **55 个 Java 源文件**，包根 `com.tiaolvshi.goetytuner`（0.0.17 为 45 个；0.0.18 新增 6 个；0.0.19 新增 4 个） |

**定位**：为 Goety 提供一位可召唤的 Boss「调律师」。Boss **主手常驻一把实体法杖**
`goety:dark_wand`（`TunerBoss` 构造函数 `setItemInHand(MAIN_HAND, ModItems.DARK_WAND)`；
从存档读回时若主手不是 `IWand` 会补发——Goety 的 `SoulUsingItemHandler.get` 要求
`ITEM_HANDLER` capability，缺失会崩服）。施法时由 `BossWandHelper.installFocus` 把从
全体聚晶池抽签得到的聚晶装进这把杖再释放，并按音乐(乐谱/重音)驱动三阶段战斗节奏。

**0.0.18 起，本模组的内容分成两条线**：

| 线 | 面向 | 内容 |
|---|---|---|
| **Boss 线**（第 1~55 轮） | 玩家要打的敌人 | `goetytuner:tuner` —— 三阶段音乐驱动的聚晶指挥家 |
| **玩家线**（0.0.18 起） | 玩家自己用的东西 | `goetytuner:tuner_ripple_focus`（一次性重音涟漪聚晶）、`goetytuner:tuner_servant`（可被玩家下达聚晶指令的仆从） |

两条线**共用底层**：同一个聚晶池/评分/轮盘赌（`FocusPoolManager`）、同一个施法通道
（`CastChannel`）、同一份重音涟漪实现（`combat/AccentRipple`）、同一套模型贴图与渲染层。
详见 §3.18。

---

## 二、总体架构

### 2.1 包结构

```
com.tiaolvshi.goetytuner
├── GoetyTuner.java                  # 主入口，@Mod，总线注册
├── init/
│   ├── ModEntities.java             # 实体注册（TunerBoss；0.0.18 增 TunerServant）
│   ├── ModItems.java                # ForgeSpawnEggItem 注册（0.0.18 增仆人蛋 + 调律波纹聚晶；**聚晶的注册次序约束写在本类 javadoc 里**）
│   ├── ModSounds.java               # 音效注册（音乐走 RECORDS 类）
│   ├── ModEvents.java               # 实体加入拦截 / 掉落 / 召唤物归属等
│   └── ModBusEvents.java            # MOD 总线：属性、图层、屏幕（0.0.18 起注册两个实体的属性表）
├── entity/
│   ├── TunerBoss.java               # Boss 主体（2049 行核心类：AI 状态机 / 阶段 / 战斗数值）
│   ├── TunerServant.java            # 【0.0.18】调律师仆从（Summoned 子类 + 复用 Boss 的聚晶池/施法通道 + 聚晶指令）
│   ├── TunerServantInteractions.java # 【0.0.18】手持聚晶左键/右键仆从的下令入口（FORGE 总线的 AttackEntityEvent / EntityInteract）
│   ├── OrbHighlightSource.java      # 【0.0.18】立方体高亮数据源接口（TunerOrbLayer 因此不再依赖具体实体类）
│   ├── BossPhase.java               # 三阶段枚举（铺垫/高潮/低谷）
│   ├── MusicController.java         # 服务端乐谱推进 / 阶段转换 / 重音触发
│   └── ai/CastChannel.java          # 施法通道（前摇/高潮并行通道/瞬发；0.0.18 增"指定聚晶起手"重载）
├── focus/
│   ├── FocusPoolManager.java        # 聚晶池构建 / 抽签 / 锁池归还 / 黑名单（0.0.18 增每实例的禁放/优先指令表）
│   ├── FocusEntry.java              # 聚晶条目（id/分类/标签）
│   ├── FocusCategory.java           # ATTACK / DEFENSE / SUMMON / OTHER 四类枚举（0.0.18 起承载共用的 parseRoleSpec）
│   ├── RippleSpell.java             # 【0.0.18】调律波纹聚晶的法术（5 灵魂 / 4tick 蓄力 / 10tick 冷却）
│   ├── FocusClassifier.java         # 启发式分类（lang key + 生物名词兜底）
│   ├── LLMClassifier.java           # 可选 LLM 分类（人工标注优先）
│   ├── FocusClassificationConfig.java # 分类配置 + lang 缓存（服务端读 jar）
│   └── BossWandHelper.java          # Boss 用魔杖封装（伤害/耗蓝规则）
├── combat/
│   ├── CombatEvents.java            # 伤害事件（近战易伤/召唤物伤害归因）
│   ├── AccentRipple.java            # 【0.0.18】「重音涟漪」的唯一实现（击退/粒子/声波/提示音，Boss 与聚晶共用）
│   ├── DamageScoreTracker.java      # 输出榜（可投掷物）
│   └── SummonScoreTracker.java      # 召唤榜
├── client/
│   ├── BossMusicManager.java        # 客户端循环音乐实例（FORGE 总线；0.0.13 起每 tick 用 SoundManager.isActive 核实实例、失效即自愈重建）
│   ├── MusicStateClient.java        # 音乐进度本地平滑推进（×speed）
│   ├── MusicBarHud.java             # 音乐条 HUD 204×8（逐行混色分段 + 2px 过渡缝 / 三层柔和投影 / 重音刻度（0.0.13 亚像素覆盖平滑滚动，高潮 2×6、低谷 1×6、铺垫 1×4 小长条）/ 指针 / 像素阶段符号）
│   ├── ClientCameraShake.java       # 重音镜头震动
│   ├── TunerConfigScreen.java       # Configured 配置屏入口（API Key + 多行提示词框（预填标准模板）+ **0.0.14 新增的单行「聚晶黑名单」输入框**，关屏/开始评分时保存并热刷新）
│   ├── TunerToast.java              # 客户端 Toast（LLM 评分/命令结果提示）
│   ├── ClientSetup.java             # 图层定义注册 / 渲染器绑定（0.0.18 起绑定两个渲染器）
│   ├── ClientDeathAnimation.java    # 客户端死亡动画复位（deathTime/hurtTime/姿态，由 SEntityRevivePacket 触发）
│   ├── AccentWaveRenderer.java      # 世界空间声波涟漪渲染（0.0.10 新增，AFTER_PARTICLES；0.0.12 起锚定触发瞬间坐标、只按时间清理；0.0.13 起活跃表由 Map 改 **List**，同一实体多波共存、MAX_WAVES=32 兜底；**0.0.18 起入口改为 `trigger(x,y,z)`**）
│   └── render/                      # TunerRenderer/TunerModel/TunerCape*/TunerOrb*（悬浮立方体层，0.0.10；0.0.13 高亮改向白插值 + 两层 entityTranslucentEmissive 自发光外壳；**0.0.18 三个类泛型化 + 新增 TunerServantRenderer**）
├── network/
│   ├── TunerNetwork.java            # 通道注册（id 0/1/2/3，**协议 2.1** 严格匹配）
│   ├── SMusicSyncPacket.java        # 乐谱/进度/speed 同步（服务端→客户端）
│   ├── SShakePacket.java            # 镜头震动同步
│   ├── SEntityRevivePacket.java     # 死亡动画复位同步（0.0.8 新增，通道 id 2，发给所有追踪者）
│   └── SAccentWavePacket.java       # 声波涟漪触发同步（0.0.10 新增，通道 id 3，限 PLAY_TO_CLIENT；**0.0.18 载荷由实体 id 改为三个 double 的世界坐标**）
├── config/
│   └── TunerCommonConfig.java       # 全部可调参数（common toml，**71 项 / 11 个 section**）
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
  ├─ 重音命中 onAccent ── AccentRipple ─→ 击退+粒子+音效（服务端）  ／      └─→ MusicBarHud 渲染
  │                                   ├→ SShakePacket(震动) ──→ ClientCameraShake
  │                                   └→ SAccentWavePacket(坐标, id3) ──→ AccentWaveRenderer
  ├─ 抽签 CastChannel ── FocusPoolManager.draw → spell.mobSpellResult ─→ Goety 法术生效
  └─ 阶段转换 enterPhase2 ── 自buff/回血/进场连发 ──→ 客户端阶段切换（HUD 分段配色；音频不换速）

玩家线（0.0.18）：
  玩家用法杖放「调律波纹聚晶」 ── Goety DarkWand 管线 ──→ RippleSpell.SpellResult
                                                          └→ AccentRipple（与 Boss 重音同一份实现）
                                                             └→ SAccentWavePacket(坐标) ──→ AccentWaveRenderer
  玩家手持聚晶 左键/右键 自己的调律师仆从
      ├─ AttackEntityEvent / PlayerInteractEvent.EntityInteract（**只在服务端判定与取消**）
      ├─ 无主仆从 → 认主
      └─ FocusPoolManager 实例字段（禁放/优先）+ NBT 落盘

  调律师仆从.aiStep ── 自己的 FocusPoolManager（每只一份实例）
      ├─ beginNextCast：先试「优先释放」名单（CastChannel 指定聚晶起笔），再按 servant.rotation 抽签
      ├─ CastChannel（与 Boss 同一条代码路径）→ Goety 法术生效
      └─ DATA_CAST_CATEGORIES（位掩码同步）──→ TunerOrbLayer 三颗立方体高亮
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
    **0.0.15 精修**了**翻领边缘 / 衣袖明暗 / 袖口细边 / 裤缝 / 靴口层次**（64×64 RGBA，2520 → **2676 B**），
    保留紫黑礼服 / 紫色袖口裤靴 / 浅色领巾 / 青色胸饰；**头部区逐像素不变** —— 以 0.0.14 为基准差分：
    **y0..15 全宽 64 列 0 个像素差异**、**alpha 蒙版完全一致**（未增删不透明像素）、身体区改动 **1247 像素**、
    6 个主要 UV 面**无透明空洞**；原稿由 imagegen 生成后**按最近邻采样重新装配回原有 UV**。
    ⚠️ **观感未做游戏画面验收**（`art/preview.png` 是平面正背面拼接图、不是游戏截图），
    详见 [art/REFINEMENT.md](art/REFINEMENT.md)。
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
  与新包，旧客户端会错误解码；⇒ **联机双方必须同时更新到 0.0.10 及以上版本**。
  **0.0.18 起协议版本为 `"2.1"`**（`TunerNetwork.PROTOCOL_VERSION` 常量，注册处改为引用它）：
  本版把既有包 `SAccentWavePacket` 的**载荷结构**由"实体 id（varint）"改成"三个 double 的世界坐标"
  —— **同 id 不同结构**在新旧客户端之间会解码错位（旧客户端会把 8 字节的 double 当 varint 读），
  所以必须提号；严格匹配的规矩不变（`"2.1"::equals`）⇒ 联机双方仍须同版本。
  历史：协议号 `2.0` 自 0.0.10 起沿用至 0.0.17（0.0.11 ~ 0.0.17 均为 `2.0`），0.0.18 起为 `2.1`，否则连接被拒。
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
- `TunerCommonConfig`：common toml，**67 项、10 个 section**——`boss`(**20**) / `phase2_buffs`(**4**) /
  `summon`(4) / `scoring`(**6**) / `casting`(10) / `focus`(1) / `wand_whitelist`(1) /
  `music`(**13**) / `llm`(2) / `wand_upgrade`(6)，
  Configured 中文分类引导；`music_score.json`、`focus_classification.json` 运行时双写。
  0.0.13 新增键：`music.accentDensityDivisor`（`IntValue`，**默认 3**，范围 1~9，见 §3.14(4)）。
  0.0.14 新增键：`phase2_buffs.phase2EffectImmunity`（`Boolean`，**默认 true**，见 §3.15(2)）——
  注意它落在 **`[phase2_buffs]` 段**（`b.push("phase2_buffs")` 块内），**不是 `[boss]` 段**，故 `boss` 段项数
  **仍为 19、未变**，`phase2_buffs` 段 **3 → 4**；总项数 **62 → 63**。
  0.0.16 新增键（3 个，全在 **`[scoring]` 段**，见 §3.16）：`scoring.learningWeightStart`（`Double`，**默认 0.15**，范围 0~1）、
  `scoring.learningWeightMax`（`Double`，**默认 1.0**，范围 0~2）、`scoring.learningWeightRampCasts`（`Int`，**默认 60**，范围 1~1000）——
  故 `scoring` 段 **3 → 6**、`boss` / `phase2_buffs` / `summon` / `casting` / `focus` / `wand_whitelist` / `music` / `llm` / `wand_upgrade`
  九段**均未变**，section 数仍 **10**；总项数 **63 → 66**。
  0.0.17 新增键（**1 个，在 `[boss]` 段**，见 §3.17(1)）：`boss.deathCurseExecution`（`Boolean`，**默认 true**）——
  与 `maxDamagePerSecond` / `lockDeathRevive` 同段（`b.push("boss")` 块内），故 `boss` 段 **19 → 20**，
  其余九段**均未变**，section 数仍 **10**；总项数 **66 → 67**。false = 关闭索命后门、索命重新受锁血保护。
  ⚠️ **口径核对**（0.0.17 收尾实测）：`TunerCommonConfig.java` 内 `.define` / `.defineInRange` 调用共 **67** 处、`push(...)` 段共 **10** 处，
  `[boss]` 段内共 **20** 处、`[scoring]` 段内共 **6** 处 —— 与上表一致。
- ✅ **可访问性结论（0.0.14 起部分解决）**：`TunerConfigScreen` **顶替**了 Forge 默认的 toml 编辑器，
  界面里目前有 **LLM API Key / 提示词 / 聚晶黑名单** 三个输入框 ⇒ **`focus.blacklist` 已有游戏内入口**
  （0.0.14 新增，点「完成」关屏或点「开始评分」即保存并立即热刷新，见 §3.15(1)）；
  **其余 common 配置项依旧只能手改 `goetytuner-common.toml`**（包括 `boss.*` / `music.*` / `casting.*` 等）。
- **⚠️ 0.0.10 配置迁移（务必告知玩家）**：`music.accentWave`（默认 **true**）是**本次新增的键**，
  Forge 合并配置时会**自动补进**已有 toml；但 `music.accentParticles` 是**已存在的键**，本轮只是把
  **默认值由 `true` 改成 `false`**（键保留为手动兼容选项）——**Forge 不会用新默认值覆盖已有 toml**，
  所以老玩家文件里它仍然写着 `true`，会出现「旧粒子冲击环/音符 + 新声波」同时播放。
  ⇒ 必须**手动改成 `false`**（本次已在用户实例 `versions\测试` 的 toml 手工迁移：
  `accentParticles=false` + 新增 `accentWave=true`）。**口径：「新增键会自动补齐、无需删 toml」
  与「改默认值不会回填老 toml」是两件事**，不要混为一谈。
  0.0.13 新增的 `music.accentDensityDivisor`（默认 `3`）属**新增键** ⇒ Forge 会**自动补进**老 toml、**无需手改**
  （实例的 toml 在下次启动生成该行之前查不到这一键，属正常，不代表没生效——默认值在代码里）。
  0.0.14 新增的 `phase2_buffs.phase2EffectImmunity`（默认 `true`）同理：**新增键、自动补齐、无需手改**。
  0.0.16 新增的 `scoring.learningWeightStart` / `learningWeightMax` / `learningWeightRampCasts` 同样属**新增键**
  ⇒ Forge 会**自动补进**老 toml、**无需手改**（老 toml 在下次启动写入这三行之前查不到它们属正常——默认值在代码里）。
  ⚠️ 但**这三个键在配置界面没有入口**（`TunerConfigScreen` 只有 LLM API Key / 提示词 / 聚晶黑名单三个输入框）
  ⇒ 想调"逐渐学习"的曲线只能**手改 `goetytuner-common.toml` 后重启游戏**（common 配置在 mod 加载时读入内存，
  手改文件不会热生效）；重启同时会把 `FocusPoolManager` 的 `castCount` **一起归零**（该计数不落盘，见 §3.16）。
  0.0.17 新增的 `boss.deathCurseExecution`（默认 `true`）同样属**新增键** ⇒ Forge 会**自动补进**老 toml、**无需手改**
  （想要关闭索命后门就手改成 `false`；**同样没有配置界面入口 ⇒ 改完需重启游戏**，见 §3.17(1)(5)）。
  但 0.0.14 的**黑名单改动是"改值"而非"加键"** —— 若你自己在 toml 里手改过 `focus.blacklist`，
  则要用配置界面/手改把值改回来才会生效（0.0.14 的代码默认值**仍只有** `goetytwilight:destruction_focus`）。

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
- **二阶段免疫「回复 / 减伤」类效果（0.0.14）**：`canBeAffected` 在 `music.isPhase2()` 时额外拒绝
  抗性提升 `DAMAGE_RESISTANCE` / 伤害吸收 `ABSORPTION` / 生命恢复 `REGENERATION` / 瞬间治疗 `HEAL` /
  生命提升 `HEALTH_BOOST` 五项，开关为 `phase2_buffs.phase2EffectImmunity`（默认 **true**）。
  **不影响**上面这条的二阶段自施（力量 `DAMAGE_BOOST` / 重振 `RALLYING` 不在名单里），也**不影响玩家**。详见 §3.15(2)。
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
   ⚠️ **但情形 A 只在服务端跑**（`maintainDeathState()` 被 `if (!level().isClientSide)` 把关）⇒
   它治不了**客户端自己那份 `deathTime`**。0.0.17 实测确认了这一侧会残留：服务端复位包只发**一次**，
   若那一刻客户端血量同步还没落地，客户端的 `tickDeath()` 会**自己再把 `deathTime` 加上去**、之后无人再清。
   ⇒ **0.0.17 在 `tick()` 的客户端分支补了对称自愈**，见 **§3.17(2)**；本节第 2 项（情形 A）与本轮客户端自愈
   是**同一问题的服务端/客户端两半**，两者都在才算修完。

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
- ⚠️ **可访问性结论（原文写于 0.0.13；0.0.14 已部分推翻，保留原文以便追溯）**：`focus.blacklist` **无法在游戏内配置界面修改** —— 本模组的
  `TunerConfigScreen` **顶替**了 Forge 默认的 toml 编辑器，而该界面只有 LLM API Key 与提示词两个输入框。
  **整个 `goetytuner-common.toml` 的所有项都只能手改文件。** 解析规则（`parseBlacklist`）：
  英文逗号分隔、逐段 trim、**大小写敏感**。
  ✅ **0.0.14 更新**：配置界面已补上**聚晶黑名单输入框**（点「完成」关屏或点「开始评分」即保存并**立即生效**），
  本条对 `focus.blacklist` **不再成立**；**其余** common 配置项仍只能手改 toml。详见 §3.15(1)。
- 另：`RUNTIME_BLACKLIST`（运行期自愈拉黑）与配置黑名单是**并集** —— 配置**无法解禁**一个已被
  运行期拉黑的聚晶（要解禁只能重启，或修好那个聚晶的实现）。

### 3.15 0.0.14：配置界面的聚晶黑名单入口 / 二阶段免疫「回复·减伤」类效果

本轮改动 **4 个 Java 文件 + 2 个 lang + 版本号**，**只改代码、不动任何贴图/资源**（`.java` 仍 **45** 个、
jar 条目仍 **102**，无新增/删除类）；**新增 1 个配置项** `phase2_buffs.phase2EffectImmunity` ⇒
配置 **62 → 63 项**（`phase2_buffs` 段 **3 → 4**，`boss` 段**仍 19、未变**，section 数仍 **10**）。
改动文件与实测行数：`entity/TunerBoss.java`（**2050 行**）、`config/TunerCommonConfig.java`（**438 行**）、
`focus/FocusPoolManager.java`（**396 行**）、`client/TunerConfigScreen.java`（**220 行**）；
lang：`assets/goetytuner/lang/zh_cn.json` / `en_us.json`（各 +4 键）。

**（1）配置界面新增「聚晶黑名单」输入框 —— `TunerConfigScreen` + 新增 `FocusPoolManager.refreshBlacklist()`**

- **背景（一个存在了很久的缺口）**：`focus.blacklist` 是 **common** 配置，而本模组的 `TunerConfigScreen`
  **顶替了 Forge 默认的 toml 编辑器**（注册的是 `IConfigScreenFactory`），该界面此前只有 LLM API Key 与
  提示词两个输入框 ⇒ 这个键在游戏内**完全没有入口**，只能手改 `goetytuner-common.toml`
  （0.0.13 的「拉黑索命聚晶」就因此只能靠改文件落地，见 §3.14(7)）。
- **改法**：界面加一个单行 `EditBox`（`cx-150, 138`，`300×18`，`maxLength 1024`），构造时**预填当前值**
  （`TunerCommonConfig.FOCUS_BLACKLIST.get()`），hint 与 lang 文案为「namespace:path，英文逗号分隔；
  留空 = 不屏蔽」；标签 `config.goetytuner.blacklist.label` 画在上一行（`y=126`）。
- **保存时机**：**点「完成」关屏时保存**（`onClose()` → `applyBlacklist()`，"我改完了"最自然的信号），
  **点「开始评分」时也顺带保存一次**（用户可能只改了黑名单就来点这个按钮）；值没变则直接返回、不重扫。
- **保存动作与生效路径**：`FOCUS_BLACKLIST.set(v)` → `FOCUS_BLACKLIST.save()` →
  `FocusPoolManager.refreshBlacklist()`。**只改值就已经立即生效** —— `getBlacklist()` 以 **raw 字符串为
  缓存键**（§3.5 提过的那层缓存），值一变缓存自动失效 ⇒ `draw()/drawUniform()` 的下一次抽取就会
  过滤掉新拉黑的聚晶。保存成功后状态行显示 `config.goetytuner.blacklist.saved`（带当前生效聚晶数），
  并记 INFO `focus.blacklist updated from config screen: '<旧>' -> '<新>'`。
- **为什么要新增 `refreshBlacklist()`（本轮真正的设计点）**：
  - `initIfNeeded()` 扫描 `ForgeRegistries.ITEMS` 时，对黑名单里的聚晶是**直接 `continue` 跳过**的
    —— 它们**根本不进 `ALL_ENTRIES`**。于是"**新增**拉黑"能靠实时过滤立刻生效，
    但"**取消**拉黑"**必须重扫**才会回到池里（单靠改配置值回不来）。
  - 而"重扫"若 **`new` 出全新的 `FocusEntry` 对象**，会让实体侧那些**按对象身份**记录的状态**失效**：
    `TunerBoss.activeVisualCasts`（身份集合，驱动立方体高亮）与 `CastChannel.current`（正在施法的聚晶）
    存的都是对象引用。身份断掉后的症状**极其隐蔽** ——「立方体高亮卡住不熄」、
    「施法收尾回调对不上 ⇒ 施法计数 / 蹲姿永久 > 0」。
  - 因此 `refreshBlacklist()` 先按 **`namespace:path`** 建索引，重扫时**复用已有的 `FocusEntry` 对象**
    （计入 `reused`），**只为"这次才被解禁"的聚晶 `new` 对象**（它们此前不在池里、**不可能正在施法**，故安全）；
    然后重建 `ALL_ENTRIES` / `STATIC_POOLS` 并重新 `classification.applyTo(...)` 分类，
    最后打 INFO `[Tuner] Blacklist refreshed: N foci active (reused X, skipped Y)`。
  - 逐项 `try/catch` 兜底照留：某个附属聚晶在扫描时抛异常只会被**跳过并记 ERROR**，不影响其它聚晶
    （与 `initIfNeeded()` 同款防线，见 §3.10）。
- **新增 4 个 lang 键**（zh_cn / en_us 各 4 个）：`config.goetytuner.blacklist.label` /
  `config.goetytuner.blacklist` / `config.goetytuner.blacklist.hint` / `config.goetytuner.blacklist.saved`。
- ⚠️ **限制（必须知道）**：连**他人**的服务器时，改的是**本地**那份 common toml、**服务器侧不受影响**
  （单机 / 局域网主机是同进程的集成服务器，故正常生效）。
  另：`RUNTIME_BLACKLIST`（运行期自愈拉黑）与配置黑名单仍是**并集**，配置**无法解禁**一个已被运行期拉黑的聚晶。
- 本轮的界面新增输入框**尚未进游戏画面验收**（本轮只做到编译通过 + 部署核对）。

**（2）二阶段免疫「回复 / 减伤」类药水效果 —— `TunerBoss.canBeAffected` + 新配置**

- **需求**：进入二阶段后，Boss 不再能被打上 **抗性提升 / 伤害吸收 / 生命恢复** 之类效果。
  （动机：二阶段本就靠锁血档位 + 回血撑强度，再被外部来源叠上抗性/吸收/再生，Boss 会变成
  "打不动 + 自动回血"，战斗直接失去节奏。）
- **实现**：扩展现有的 `canBeAffected` 覆写（它同时是 `LivingEntity.addEffect` 与 `forceAddEffect` 的
  **第一道**判定）—— 在这里返回 `false` 就是**真正的免疫**，而不是"加完再清"
  （后者对 `ABSORPTION` 这类效果根本来不及，还会留下粒子/音效）。
- **免疫名单**（集中在私有 `isPhase2Immune(MobEffect)`，一行一项）：**抗性提升** `DAMAGE_RESISTANCE` /
  **伤害吸收** `ABSORPTION` / **生命恢复** `REGENERATION` / **瞬间治疗** `HEAL` / **生命提升** `HEALTH_BOOST`，
  **仅当 `music.isPhase2()` 且该配置为 true** 时生效。
- **⚠️ 为什么刻意只列这 5 项，而不是"所有 beneficial"**：Boss 自己的二阶段增益
  **力量 `DAMAGE_BOOST`** 与 **重振 `RALLYING`**（`tickPhase2Buffs`，见 §3.6）**同样是 beneficial** ——
  按 `isBeneficial()` 一刀切会把它们**一起禁掉**，等于顺手废掉二阶段自 buff。
  写成显式白名单后，以后要再挡某个效果（例如某个附属的自定义减伤光环）**加一行**即可。
- **不影响**：**玩家**的同类效果（本方法只作用于本实体），以及 Boss **自己的二阶段自施**
  （`DAMAGE_BOOST` / `RALLYING` 不在名单内）。
- **新增配置** `phase2_buffs.phase2EffectImmunity`（`Boolean`，**默认 true**；⚠️ 键在 **`[phase2_buffs]` 段**，
  不是 `[boss]` 段）：`false` = **回到旧行为**（不再免疫）。属**新增键** ⇒ Forge 会自动补进老 toml、无需手改。
- **实测状态**：本轮只做到**编译通过 + jar 条目核对**，免疫效果本身**尚未进游戏实测**
  （验收方式：二阶段给 Boss 丢抗性提升 / 生命恢复药水应完全不上身；把配置改成 `false` 后应恢复可施加）。

### 3.16 0.0.16：「逐渐学习」——动态评分权重系数随该个体施法次数爬升

本轮改动 **3 个 Java 文件 + 版本号**，**只改代码、不动任何贴图/资源**（`.java` 仍 **45** 个、jar 条目仍 **102**，
无新增/删除类与资源）；**新增 3 个配置项**（全在 **`[scoring]` 段**）⇒ 配置 **63 → 66 项**
（`scoring` 段 **3 → 6**，其余九段均**未变**，section 数仍 **10**）。
改动文件与实测行数：`entity/TunerBoss.java`（**2053 行**，0.0.15 为 2050）、
`focus/FocusPoolManager.java`（**458 行**，0.0.14 为 396）、`focus/FocusEntry.java`（**154 行**）、
`config/TunerCommonConfig.java`（**456 行**，0.0.14 为 438）。

**（1）动机（用户反馈）**

- **动态评分**（`combat/DamageScoreTracker`、`combat/SummonScoreTracker` 在实战中累加出来的偏移）原本**与静态评分同权**
  地进入轮盘权重 ⇒ 它会把**初始评分**（`focus_classification.json`，即配置 / LLM 分类的结果）的影响**大幅冲淡**：
  开局没打几下，初始分类**就基本失效**了——"这是个会学习的指挥家"的表现力很弱，看起来更像"随便乱放"。
- **用户要求**：用一个**较低的权重乘数**大幅降低动态评分的影响，并随**该调律师个体**的施法次数**缓慢增加**该乘数。

**（2）权重公式改动 —— 只缩动态部分，静态评分完全不动**

- `focus/FocusEntry.java`：`rouletteWeight(...)` **新增第 4 参数 `dynamicScale`**，权重公式由
  `|静态评分 + 动态偏移| + 保底基数` 改为 **`|静态评分 + 动态偏移 × dynamicScale| + 保底基数`**。
- **攻击类**（`return Math.abs(attackScore + dynamicAttackOffset * dynamicScale) + baseWeight;`）
  与**召唤类**两条公式**都改**；召唤类（生存分 / 输出分加权）是 **`dynamicSurvivalOffset` 与 `dynamicAttackOffset`
  两处偏移各乘一次** `dynamicScale`：
  `useScore = (survivalScore + dynamicSurvivalOffset * dynamicScale) * w1 + fillRatio * (attackScore + dynamicAttackOffset * dynamicScale) * w2`。
- **关键性质**：缩权的对象**只有动态偏移**（`attackScore` / `survivalScore` 这两个静态分**原样参与**）——
  这正是"初始分类不被冲淡"的来源；`dynamicScale = 1.0` 即**完全等价于旧行为**。

**（3）每实例学习状态 —— 以及"为什么不能是 static"**

- `focus/FocusPoolManager.java` 新增三个**实例字段**（与 `pools` / `cooldownPools` 同级）：
  - `private int castCount = 0;` —— 该个体已完成的**施法次数**；
  - `private double learningWeight = -1.0;` —— 当前系数（`-1` = 尚未初始化，首次访问时按当前配置算）；
  - `private int lastLoggedMilestone = -1;` —— 已打过日志的里程碑（见下）。
- **为什么必须是实例字段（而非 `static`）**：`TunerBoss` 的字段初始化式是
  `private final FocusPoolManager pools = FocusPoolManager.createFightPools();` —— **每个实体实例各建一份池**，
  且 `createFightPools()` 内部对每个 `FocusEntry` 做的是**实例级复制**（避免多只 Boss 共享动态偏移，见该方法的注释）。
  ⇒ "学习进度"**天然是该个体私有**的，与**动态偏移的生命周期完全一致**（两者一起随实体重建而重置）。
  **副作用（需要知道）**：`castCount` **不落盘** ⇒ 退出重进游戏 / 该实体被卸载后重新加载 / 换实体 / 重召唤，
  **学习进度都会归零**（与动态偏移一起）。
- `noteCast()`（由 `TunerBoss.onCastStart` 每次成功施法调用）：`castCount++` 后调 `refreshLearningWeight()`。
- `refreshLearningWeight()`（私有）按当前配置与 `castCount` 重算，公式：
  **`w = start + (max − start) × min(1, castCount / ramp)`**（**线性**），并在 `milestone = (int)(t*100)/10*10`
  变化时打一条 INFO：
  `[Tuner] Learning weight <start>/<max> = <w> (casts <castCount>/<ramp>)`
  （t 从 0 到 1 共 11 个里程碑 0/10/…/100 ⇒ 默认 60 次爬升约每 **6 次**施法一条日志，便于观察"逐渐学习"）。
- `learningWeight()`（getter）首次访问时按配置初始化；`castCount()`（getter）供调试 / 展示。
- `draw()` 把系数作为**第 4 实参**喂给 `rouletteWeight`：
  `double dynamicScale = learningWeight(); ... usable.get(i).rouletteWeight(base, ctx, summonBlocked, dynamicScale);`

**（4）`javap` 调用链验证（reobf jar 里方法显示 SRG 名，字符串检查会漏报 ⇒ 必须核字节码）**

偏移（offset）级链路已核对一致：

```
偏移 178  invokevirtual  FocusPoolManager.learningWeight:()D      ← draw() 取系数
偏移 178+ → 存入局部变量 14（double 占两个槽）                    ← 局部变量 14/15
偏移 234  dload 14                                                ← 载入该 double
偏移 236  invokevirtual  FocusEntry.rouletteWeight:(D[DZD)D       ← 第 4 实参 = dynamicScale
```

说明：`rouletteWeight` 的新描述符为 **`(D[DZD)D`** —— `double baseWeight`、`double[] summonContext`、
`boolean summonBlocked`、**`double dynamicScale`**，返回值 `double`。

**（5）三个新配置键与默认曲线**

| 键（`[scoring]` 段） | 类型 | 默认 | 范围 | 含义 |
|---|---|---|---|---|
| `learningWeightStart` | `Double` | **0.15** | 0 ~ 1 | 开局动态评分**只按 15% 计入权重**（0 = 完全无视动态评分、纯用初始分类） |
| `learningWeightMax` | `Double` | **1.0** | 0 ~ 2 | 施法足够多之后的**上限**（1 = 与旧行为持平；>1 = 后期比旧行为更强势） |
| `learningWeightRampCasts` | `Int` | **60** | 1 ~ 1000 | 从起始权重**线性**升到上限所需的**施法次数** |

**默认曲线**（`start=0.15` / `max=1.0` / `ramp=60`，四舍五入到三位小数）：

| 施法次数 | 0 | 10 | 20 | 30 | 40 | 50 | 60 | ≥60 |
|---|---|---|---|---|---|---|---|---|
| 系数 `w` | **0.150** | 0.292 | 0.433 | 0.575 | 0.717 | 0.858 | **1.000** | 1.000（封顶） |

⇒ 观感上：**开局以「初始分类」主导**（配置 / LLM 评分的结论说了算），随实战逐次把话语权**交棒给动态反馈**——
"会学习"这件事从"立刻接管"变成"逐渐学会"。

**（6）`drawUniform()` 不受影响 / 统计口径**

- `FocusPoolManager.drawUniform(category)`（防御 / 其他类的均匀抽取）**本来就不走评分**（不走 `rouletteWeight`），
  故**完全不受**本轮改动影响 —— 这两类的抽取行为与 0.0.15 一模一样。
- `noteCast()` 挂在 `TunerBoss.onCastStart` 里，**只统计"用聚晶施法"的前摇起手**；
  **瞬发（instantCast）/ 重音不经过该回调，故不计入**施法次数。

**（7）`FocusEntry` 那两个 getter 的现状（不要误读为"已删除"）**

- `getEffectiveAttackScore()` / `getEffectiveSurvivalScore()` 现在的语义是"**未缩权的**视角"——
  它们**原先是轮盘权重的输入**，本轮改完之后**不再被抽取路径调用**（`draw()` 改走 `rouletteWeight(..., dynamicScale)`）。
- **它们并没有被删除**，而是**保留作诊断 / 展示用途**（例如日志、调参时看"若不做缩权，动态偏移会把权重推到多少"）。
  ⚠️ 引用这两个值时**务必注明它们不含学习系数缩权**，别当成"当前实际参与抽取的权重"。

**（8）实测状态**

- 本轮只做到**编译通过**（主副本就地 `gradlew clean build` 成功，**1m32s**，仍只有原有 3 条 Forge 弃用警告）
  + `javap` 字节码核对 + **jar 条目核对（102 条）**；
- ⚠️ **"逐渐学习"的手感尚未进游戏实测**（见 §七）。
  验收建议路径：① 开局前 ~10 次施法应**大体沿用初始分类**（高分类聚晶明显更常出现）；② 到 ~60 次施法左右
  动态反馈应接管（表现与旧行为接近）；③ 按 10% 里程碑核对日志 `Learning weight ...` 是否**单调递增**；
  ④ 把 `learningWeightStart` 设成 `0`（完全无视动态评分）与 `1`（旧行为）各打一场做**对照手感**。

### 3.17 0.0.17：索命聚晶后门（`goety:death` 可无视锁血处决）+ 客户端死亡动画残留自愈

本轮改动 **2 个 Java 文件 + 版本号**，**只改代码、不动任何贴图/资源**（`.java` 仍 **45** 个、jar 条目仍 **102**，
无新增/删除类与资源）；**新增 1 个配置项**（在 **`[boss]` 段**）⇒ 配置 **66 → 67 项**
（`boss` 段 **19 → 20**，其余九段均**未变**，section 数仍 **10**）。
改动文件与实测行数：`entity/TunerBoss.java`（**2111 行**，0.0.16 为 2053）、
`config/TunerCommonConfig.java`（**466 行**，0.0.16 为 456）。

**（1）动机（用户反馈）——两个方案都做了**

- **用户反馈**：玩家用**索命聚晶**打中调律师时，Boss 会进入**「动画已经死了、血量却回弹」**的破状态。
  成因是两条机制叠在一起：索命造成的是**"等同于目标当前生命值"**的致死伤害，本该是处决；
  但锁血体系在宽限期/致死截断处把它拦下，随后 `maintainDeathState()` 又把 Boss 回弹复活——
  **血量回来了、客户端动画却没人清**（客户端残留的机理见本节第 (4) 条）。
- **用户原话**：「如果可以的话给索命的成功伤害直接开个后门；如果麻烦的话修一下动画问题」
  —— 本轮**两个都做了**：既给索命开**真死**的后门（治本），也补上**客户端动画自愈**（治残留），
  两者互相独立、各自都能单独工作。

**（2）索命后门的实证依据（先实证、再动手——这是"精确限定在索命"的依据）**

- **调用链实证**（反编译 Goety 的 `KillingSpell.SpellResult`）：
  **`ModDamageSource.deathCurse(target)` → `MobUtil.hurtCalculation(...)` → `target.hurt(source, amount)`**。
  ⇒ 索命的伤害**走 `hurt()`**，也就是**我们的 `hurt()` 覆写能看到它**——这是后门能成立的前提。
  （对比：`setHealth(0)` / `kill()` 这类绕过伤害管线的手段，`hurt()` 根本收不到，只能靠 §3.11 的脏状态分支兜。）
- **伤害类型的确认方式**：`ModDamageSource.DEATH` 在**静态初始化**里由 **`create("death")`** 生成
  （`invokestatic create` → `putstatic DEATH`）；jar 内亦有 **`data/goety/damage_type/death.json`**
  （`message_id` = **`goety.death`**）⇒ 该伤害类型就是 **`goety:death`**。
- **"精确限定在索命"的关键实证**：**全 jar 扫描 `deathCurse` 的引用，只有 `KillingSpell` 一处**
  （外加它在 `ModDamageSource` 里的定义本身）。
  ⇒ 用伤害类型 `goety:death` 做匹配**恰好等价于"索命聚晶"**，**不会误伤别的法术**
  （这一点必须写清楚：否则"按伤害类型开后门"很容易变成"给一整类伤害开后门"）。
- **实现上不需要编译期依赖 Goety 的类**：常量只用资源位置字符串构造——
  `ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("goety", "death"))`。
  ⇒ 本模组的 `config` / `entity` 包**不会因为这条后门而绑死 Goety**；**未装 Goety 时该键永不匹配**（`source.is(...)` 恒 false），
  后门自然失效、退回旧行为，不会出现"缺依赖即崩"。

**（3）实现：四处联动，`actuallyHurt()` 也必须开口子**

与 §3.11 的 `/kill` 后门**同一套语义**（见该节「0.0.9」小节）——置标记后，本次伤害原样交给原版结算：

1. **新增字段** `private boolean deathCursePending = false;` + **常量** `GOETY_DEATH_CURSE`（见上）。
2. **`hurt()`**：`if (TunerCommonConfig.DEATH_CURSE_EXECUTION.get() && source.is(GOETY_DEATH_CURSE))`
   → 置 `deathCursePending = true` + 打 **INFO** + **`return super.hurt(source, amount)`**
   ⇒ **跳过**其后的身份免疫（摔落/火/窒息/溺水）、**近战易伤乘法**、**宽限期免疫**、**致死伤害截断**。
   日志串：`[Tuner] Death-curse backdoor (goety:death): bypassing lock-health protection (health={}, mark={}, amount={})`
3. **`maintainDeathState()` 最前面**：`if (this.adminKillPending || this.deathCursePending) return;`
   ⇒ **不做回弹、也不做脏状态清理**，让死亡流程正常走完（否则刚被索命打死就被下一 tick 弹回来）。
4. **`canStillRevive()` 最前面**：`if (this.deathCursePending) return false;`
   ⇒ **不拦 `remove(KILLED)`**，Boss 真正死亡、不会被"锁血未耗尽"的名义留住。
   **⇒ 合起来：索命能真正处决，无视剩余锁血档位。**
5. **`actuallyHurt()` 的限伤 / 限DPS 也必须开口子**（**这是最容易漏、漏了后门就等于失效的一处**）：
   `goety:death` **不在任何 `BYPASSES_*` tag 里**（不像 `/kill` 的 `generic_kill` 那样天然被
   `DamageTypeTags.BYPASSES_INVULNERABILITY` 放行），所以**必须显式列出**：
   `if (!source.is(BYPASSES_INVULNERABILITY) && !source.is(DamageTypes.GENERIC_KILL)
   && !(DEATH_CURSE_EXECUTION.get() && source.is(GOETY_DEATH_CURSE))) { …限伤/限DPS… }`。
   **不这么写的话**：索命"等同于目标当前生命值"的致死伤害会被 `maxHitDamagePercent`（默认 25% 最大生命）削掉，
   打不死 Boss —— **表面上后门已开、实际完全没有生效**，而且日志里看不出异常（只会看到一次被限伤的命中）。
6. **状态正常时清除标记**：`maintainDeathState()` 里"既没死也不在死亡动画"的分支中
   `this.deathCursePending = false;`（与 `adminKillPending` 一起清）——防止"那次索命被其它模组取消"导致
   保护被**永久**关闭（那会让 Boss 此后对任何致死伤害都不再回弹）。

**（4）修「动画死了血量回弹」的根因：客户端残留 → 客户端对称自愈**

- **根因**：`deathTime` **不是同步数据**（0.0.8 已实证，见 §3.11 第一条）。客户端的 `deathTime`
  由**客户端自己**的 `LivingEntity.tickDeath()` 递增，而它**只看客户端本地的血量**。
  服务端复活时只发**一次** `SEntityRevivePacket`；**若那一刻客户端的血量同步还没落地**，
  客户端会在复位后**又自己把 `deathTime` 加上去**——此后血量虽然变正、动画却**再没人清** ⇒ **永久躺着**。
  服务端侧的"情形 A"（`maintainDeathState()`）**治不了这一侧**：它在 `if (!level().isClientSide)` 分支里，
  客户端那份 `deathTime` 它碰不到。
- **修法（`tick()` 的客户端分支，对称自愈）**：客户端**同样知道血量**（`DATA_HEALTH_ID` 是同步数据），
  所以判据与服务端情形 A 完全对称：
  `if (this.deathTime > 0 && this.getHealth() > 0.0F) { this.resetDeathAnimation("client stale death animation (health=… > 0)"); }`
  ⇒ **至多残留 1~2 tick**（下一 tick 客户端自己就清掉了），不再是"永久"。
- **为什么安全 / 不产生网络流量**：`resetDeathAnimation()` 只在 `this.level() instanceof ServerLevel` 时才
  `TunerNetwork.sendToTracking(...)` ⇒ **客户端调用它不会发出任何包**，只是把本地
  `deathTime` / `hurtTime` / `hurtDuration` / `Pose.DYING` 清干净（客户端本来就该做这个）。
- **与服务端侧的分工**：服务端情形 A 负责"血量回正时把复位指令**下发**给客户端"，
  客户端自愈负责"万一下发那一瞬间没落地，客户端自己**补一次**"——两者是同一问题的两半（见 §3.11 末尾的交叉引用）。

**（5）新配置、代价与实测状态**

- **新增配置** `boss.deathCurseExecution`（`Boolean`，**默认 `true`**）：`false` = **关闭后门、索命重新受锁血保护**
  （回到 0.0.17 之前的行为）。⚠️ 键落在 **`[boss]` 段**（`b.push("boss")` 块内，与 `maxDamagePerSecond` /
  `lockDeathRevive` 同段），**不是 `[phase2_buffs]` 段**，故 `boss` 段 **19 → 20**、`phase2_buffs` 段仍 4。
  与其它新增键一样：**Forge 会自动补进老 toml**；但 `TunerConfigScreen` 里**没有这个键的入口**
  ⇒ 想关掉只能**手改 `goetytuner-common.toml` 后重启游戏**。
- **代价（必须写清楚，这不是白拿的）**：索命会**对施法者反噬"目标当前生命值 125%"**
  （即玩家打 Boss 时自己也要挨 1.25 倍于 Boss 当时血量的反噬）⇒ 它是**有代价的处决手段**，
  不是"免费秒杀"。这也是"要不要打开后门"值得由玩家自己决定的理由（所以做成了配置项）。
- **与 `focus.blacklist` 的关系（要不要继续拉黑索命）**：0.0.13 曾把 `goety:killing_focus` 加进游玩实例的
  `focus.blacklist`（配置侧，不在 jar 内），目的是躲开"Boss 抽到索命 → 破状态"。**0.0.17 之后多了一个选择**：
  **拉黑** = Boss 根本不抽这张聚晶；**开后门**（默认）= Boss 会被抽到，但一旦打中就能真正处决。
  两者**不冲突**，由玩家自行决定；代码默认值**仍只有** `goetytwilight:destruction_focus`（未动）。
- **实测状态**：本轮只做到**编译通过**（主副本就地 `gradlew clean build` 成功，**1m9s**，仍只有原有 3 条 Forge 弃用警告）
  + **jar 条目核对（102 条）**；⚠️ **尚未进游戏实测**（见 §七）。
  验收建议路径：① 战斗中让 Boss 抽到索命并命中 → Boss 应**真正死亡**（日志出现 `Death-curse backdoor`），
  不出现回弹、也不再"躺着"；② 把 `boss.deathCurseExecution` 改成 `false` 重启 → 应回到旧行为（索命被锁血拦下）；
  ③ 索命**未**命中（被闪避/免伤）时 Boss 状态应正常（标记被清除，不会从此免疫致死伤害）。

### 3.18 0.0.18：调律波纹聚晶 + 调律师仆从（本模组第一次"往外伸手"）

本轮改动 **16 个既有 Java 文件 + 6 个新 Java 类**（`.java` **45 → 51**）+ **2 个 lang** + **3 个新资源**
+ 版本号；**新增 1 个配置段**（`[servant]`，4 项）并把 `focus.blacklist` 的**默认值**改了
⇒ 配置 **67 → 71 项**、section 数 **10 → 11**；jar 条目 **102 → 112**。

这一轮与前 55 轮的性质不同：此前所有工作都围绕**调律师 Boss 本身**，本轮第一次加入
**给玩家用的内容**（一个聚晶 + 一个仆从），因此多了三条以往不存在的约束：
「玩家物品的数值要走 Goety 的玩家施法管线」「仆从要走 Goety 的 `Summoned` 体系」
「与 Boss 共用的表现层必须**同源**而不是各写一份」。

**（1）`combat/AccentRipple` —— 「重音涟漪」的唯一实现（本轮最重要的一处重构）**

用户要求聚晶的效果**与铺垫期重音同款**。要做到"同款"有两条路：抄一份代码，或者**共用同一份代码**。
本项目红线是后者（「同一职责只允许一处实现」，0.0.11 的重复分支教训）。于是把
`TunerBoss#accentKnockbackPulse` 里那四件事整体抽成 `AccentRipple`：

| 方法 | 做什么 | 配置开关 |
|---|---|---|
| `knockback(level, source, strength, alsoExclude)` | 把 `RADIUS`(8.0) 格内存活生物沿径向推出，`push(dir*strength, strength*0.5, dir*strength)` + `hurtMarked=true` | 无（力量由调用方给） |
| `particles(level, source)` | 三波 END_ROD 同心冲击环 + 12 颗 NOTE 音符爆发 | `music.accentParticles` |
| `wave(level, source)` / `waveAt(level, x, y, z)` | 逐层非对称径向声波涟漪（发给锚点 64 格内的玩家） | `music.accentWave` |
| `chime(level, source, pitch)` | 紫水晶提示音（原版音效，无需资源） | `music.accentSound` |

`TunerBoss.accentKnockbackPulse(...)` 现在只剩四行转发。**Boss 与聚晶的差别只有两处、都由调用方决定**：

1. **力量与音调**：Boss 按阶段取 0.4/0.8/1.2 与 0.9/1.4/0.6；聚晶固定取**铺垫期**的
   `music.accentKnockbackBase`（默认 0.4）与 0.9。
2. **友军豁免**：`AccentRipple.isFriendlyTo(caster, other)`（自己 / `OwnableEntity#getOwner()==caster` /
   `IOwned#getTrueOwner()==caster`）。Boss 没有友军故传 `null`；**玩家放这一发时若不豁免，
   等于把自己的仆从/宠物一起轰飞**，纯负体验。

⚠️ 两个兼容性后果（都已处理）：
- `SAccentWavePacket` 的**载荷由「实体 id」改成「三个 double 的世界坐标」**（通道 id 仍是 3）。
  根因：原先客户端收到包后用 `level.getEntity(id) instanceof TunerBoss` **反查位置**——
  这条路径隐含要求"涟漪必须由某只 Boss 发出"。玩家放的涟漪锚点是一个**裸坐标**、没有实体可查。
  改成下发坐标后服务端算好的触发点即权威值（顺带也更准：原先客户端读到的是该刻的实体位置）。
  **同 id 不同结构会解码错位**（旧客户端会把 double 当 varint 读）⇒ 协议号 **`2.0` → `2.1`**（严格匹配不变）。
- 广播方式由 `PacketDistributor.TRACKING_ENTITY` 改为「**锚点半径内按玩家广播**」——
  坐标版无法再用实体追踪分发器；涟漪本身只有几格大，64 格足够。

**（2）`focus/RippleSpell` + `goetytuner:tuner_ripple_focus` —— 调律波纹聚晶**

用户指定的三个数值，全部落在 `ISpell` 的标准回调上（没有绕管线）：

| 项 | 用户要求 | 实现 | Goety 对应回调 |
|---|---|---|---|
| 灵魂能量消耗 | 5 | `defaultSoulCost() = 5` | `ISpell#defaultSoulCost` |
| 蓄力 | 0.2 秒 | `defaultCastDuration() = 4` tick | `ISpell#defaultCastDuration` |
| 冷却 | 0.5 秒 | `defaultSpellCooldown() = 10` tick | `ISpell#defaultSpellCooldown` |

- **物品类直接复用 Goety 的 `MagicFocus`**（`new MagicFocus(new RippleSpell())`），而不是自己写一个
  `IFocus`：它自带标准 tooltip（灵魂消耗 / 系别 / 说明）、附魔规则与 `IFocus` 契约，
  且与本模组 `BossWandHelper.installFocus` 的装配路径天然兼容。
- `getSpellType() = SpellType.NONE`（通用系别，与 `goety:dark_wand` 的默认系别一致）；
  `acceptedEnchantments()` 返回空表（工具类聚晶，不给无意义的附魔组合）。
- `CastingSound` = 原版紫水晶音、`castingPitch()` 1.35（蓄力偏高）→ 结算时落回 **0.9**（= Began 铺垫期
  重音音调），形成"下行"听感。
- ⚠️ **三个数值是基础值**：Goety 的 `SoulCalculation` 会再乘 SoulDiscount 与环境加成、
  `castDuration` 会乘 `ModAttributes.getCastingSpeed` 与"减半施法时间"饰品、
  `spellCooldown` 会乘 `ModAttributes.getCooldownDiscount`。这是 Goety 所有聚晶的统一规则，
  **刻意不去覆写绕过**（覆写会与玩家的长袍/饰品体系打架）。
- 结算走 `AccentRipple` 的四件事，并**尊重** `accentParticles` / `accentWave` / `accentSound` 三个开关
  （玩家为性能关掉的观感，不该在聚晶上复活）。

**（3）聚晶的注册次序（用户点名要求处理）——三条约束 + 一条迁移**

用户提醒："模组存在在初始化最后读取 MC 内所有聚晶数据的功能，需要考虑该聚晶声明的次序"。
指的就是 `FocusPoolManager#initIfNeeded()`（由 `ModEvents#onServerStarting` 在 `ServerStartingEvent` 触发，
遍历 `ForgeRegistries.ITEMS` 收集全部 `IFocus`）。本轮的结论与处理（写在 `ModItems` 的类 javadoc 里）：

1. **必须注册在同一个 `ModItems.ITEMS` DeferredRegister 上**。该 register 在 mod 构造函数第一时间
   `ModItems.ITEMS.register(modBus)`；`DeferredRegister` 在 `RegisterEvent`（mod 加载早期，
   **远早于** `ServerStartingEvent`）统一落地 ⇒ 放在本类里的物品**一定**会被后续扫描看到。
   反之，**为它另起一个 DeferredRegister 却忘了 `.register(modBus)`（或注册得更晚）**，
   物品压根不进 `ForgeRegistries.ITEMS`，扫描永远看不到——这正是本项目**在刷怪蛋上踩过的"注册时序坑"**
   （§4.2 第 1 条）。
2. **`getSpell()` 必须立刻返回非 null 的单例**。扫描的准入条件就是 `focus.getSpell() != null`；
   `MagicFocus` 在构造函数里就把 spell 存成字段，天然满足。若写成延迟创建，扫描时可能拿到 null 而被静默跳过。
3. **必须同时拉黑**（用户要求）。`focus.blacklist` 的默认值改为
   `goetytwilight:destruction_focus, goetytuner:tuner_ripple_focus`。理由：Boss 的卖点是
   "程序化演奏全部聚晶"，不限制的话它会抽到这个聚晶——等于**白得一个 0.5 秒冷却、5 灵魂的免费重音**，
   而且那发涟漪**会把 Boss 自己一起推开**（`AccentRipple.knockback` 的排除表里没有它）。
4. ⚠️ **迁移是必须的，不能只改代码默认值**：`focus.blacklist` 是**改已有键的值**，不是加键，
   Forge **不会**用新默认值覆盖玩家已有的 toml（红线 8 / 0.0.10 的配置迁移陷阱）
   ⇒ 老存档必须手工补，否则"拉黑"这条要求对老玩家静默失效。
   仓库提供 `scripts/add_ripple_focus_blacklist.py`（自动探测 utf-8/utf-8-sig/gbk、备份、
   只改那一行、用 `difflib` 复核差异仅限该行、校验 CRLF 与无 BOM），并**已对游玩实例跑过**：
   `versions\测试\config\goetytuner-common.toml` 现为
   `goetytwilight:destruction_focus, goety:killing_focus, goetytuner:tuner_ripple_focus`
   （备份 `goetytuner-common.toml.bak-before-blacklist-ripple`）。
   也可用 0.0.14 起的游戏内入口（Mods → Config → 「聚晶黑名单」）补，改完立即生效。
   （`[servant]` 段则是**新增键** ⇒ Forge 自动补进老 toml，**无需手工迁移**——两件事不要混为一谈。）

**（4）`entity/TunerServant` —— 调律师仆从**

诡厄巫法的"生物 ↔ 仆从"惯例是：**同形同大小的独立实体，一律继承 `Summoned`**。
本轮照办：`TunerServant extends com.Polarice3.Goety.common.entities.ally.Summoned`
⇒ 主人归属（`IOwned#getTrueOwner`）、跟随主人、受主人目标牵引、仆从加血/传送/日照规则、
`/summon` 一类指令管理——**这些"一般仆从的性质"全部继承，本类不重复实现**。

实体注册照抄 Goety 仆从的写法：`MobCategory.MONSTER` + `sized(0.6F, 1.95F)` + `clientTrackingRange(8)`
（如 `goety:warlock_servant` 同构），**与 Boss 同尺寸**（"同形"正是 Goety 的惯例）。

与 Boss **同源复用**（这是本轮省下最多代码的地方）：

| 能力 | 共用对象 |
|---|---|
| 聚晶池 / 静态评分 / 轮盘赌 / 冷却池 | `FocusPoolManager.createFightPools()`（每只仆从各一份**实例**，与 Boss 完全同一套代码） |
| 前摇 / 锁池 / 朝向钉死 / 异常自愈 | `CastChannel`（实现 `TunerCastCallback` 即可，Boss 与仆从是同一个接口的两个实现） |
| 模型 / 贴图 / 披风层 / 三颗立方体层 | `TunerModel` / `TunerCapeLayer` / `TunerOrbLayer`（三个类**泛型化**，见下） |
| 轮换序列解析 | `FocusCategory.parseRoleSpec`（从 `TunerBoss` 上提到枚举，Boss 侧改成一行薄委托） |

**没有**的部分（仆从刻意不带的 Boss 机制）：音乐三阶段、锁血阶梯、瞬移走位、重音、嘲讽彩蛋、二阶段。

仆从的主循环（`aiStep`，服务端）：推进冷却池 → 若在施法则只推进当前通道 → 有目标则每 tick 钉朝向 →
冷却未到则等待 → `beginNextCast`。`beginNextCast` 先试**优先释放**名单（插队），
再按 `servant.rotation` 的序列依次尝试各类别通道（某一类池空/条件不满足就顺延，最多试一整轮）。

三处与 Boss 对齐的细节：
- **免疫 `GoetyEffects.SUMMON_DOWN`**（覆写 `canBeAffected`，与 Boss 的取舍一致）。
  Goety 的召唤冷却是给施法者挂这个效果；对玩家是平衡，对 NPC 施法者只会表现为"越召唤越废"。
- **主手暗法杖 `setDropChance(MAINHAND, 0)`**：这把杖是配发装备，生物默认 8.5% 掉落概率
  ⇒ 不加这一行等于"每只仆从死亡有 8.5% 白送一把暗法杖"。
- **召唤物数量按 `gameTime` 缓存**（`ownedMinionCount` 走 `level.getAllEntities()` 全量扫描）：
  本项目红线 13 —— 热路径不许每 tick 全量扫描。`tickCasting` 每 tick 可能问它好几次
  （召唤类池的 `minionFill` + 满员判定），故同一 tick 内复用结果（与 `SummonScoreTracker` 在 0.0.5 的修法一致）。

**刻意不做的一件事**：仆从**不调用** `FocusPoolManager#noteCast()`，因此它的 0.0.16「逐渐学习」系数
永远停在起始值（默认 0.15）。理由：0.0.16 是给 Boss 设计的（让**动态评分**随该个体施法次数逐步接管），
而仆从应当永远以**初始分类**为准——它的池子每只一份、生命周期短，"临场学"只会让同一批仆从各打各的。
这一点写在 `TunerServant` 的字段 javadoc 里，**以免后人误判成漏调**。

**（5）渲染层泛型化（复用三件，唯一新增一个文件）**

原来三个渲染类都写死成 `TunerBoss`，仆从要用就必须能动它们。做法是把"渲染层真正需要的信息"
抽出来，其余全部参数化：

| 改动 | 内容 |
|---|---|
| `TunerModel` | `extends HumanoidModel<TunerBoss>` → **`TunerModel<T extends LivingEntity>`** |
| `TunerCapeLayer` | `RenderLayer<TunerBoss, TunerModel>` → **`RenderLayer<T, TunerModel<T>>`**（本层不用实体任何字段，纯粹去掉对 Boss 的无谓依赖） |
| `TunerOrbLayer` | `RenderLayer<TunerBoss, TunerModel>` → **`RenderLayer<T, TunerModel<T>>`，且 `T extends LivingEntity & OrbHighlightSource`** |
| **新增** `entity/OrbHighlightSource` | 只有一个方法 `float getOrbHighlight(int category, float partialTick)`。Boss 与仆从都实现它 ⇒ **渲染层不再依赖具体实体类** |
| `TunerRenderer` / **新增** `TunerServantRenderer` | 各自 `HumanoidMobRenderer<自己, TunerModel<自己>>`，挂同两条层；贴图由 `TunerRenderer.TEXTURE` 共享（`static final`） |

**（6）聚晶指令（本轮唯一的新玩法）—— `entity/TunerServantInteractions`**

用户要求：**玩家手持聚晶**左键 / 右键仆从，分别设为**不释放** / **优先释放**该聚晶。

- **为什么用 Forge 事件而不是覆写 `Item` 的方法**：要生效的是"手上拿着**任意**一个聚晶"，
  而聚晶来自 Goety 本体与任意附属——不可能去改它们的 `Item` 子类，也不可能给每个都加 mixin。
  Forge 的那两个事件在**交互发生之前**触发，既能读到手上的物品、又能取消默认行为：
  `AttackEntityEvent`（左键）/ `PlayerInteractEvent.EntityInteract`（右键）。
- **钩子位置的字节码实证**（`javap` 反编译映射版 MC `forge-1.20.1-47.3.22_mapped_official_1.20.1.jar`）：
  - `Player.attack`：**偏移 0** 就是 `ForgeHooks.onPlayerAttackTarget(this, target)`，返回 false 即 `return`
    ⇒ 事件在 `attackStrengthTicker` / `isAttackable` **之前**，取消它就**不会造成任何伤害**
    （指挥自家仆从顺手揍它一下显然不对）。
  - `Player.interactOn`：**偏移 30** 是 `ForgeHooks.onInteractEntity(...)`，**早于** `Entity.interact`
    （偏移 57）与 `itemstack.interactLivingEntity` ⇒ 取消它就不会再走那条交互链。
- **⚠️ 只在服务端判定与取消（关键坑）**：两个事件在**客户端也会触发一次**（本地预测）。
  若在客户端也 `setCanceled(true)`，左键那条路径会让 `LocalPlayer` **提前 return、连攻击包都不发**，
  服务端永远收不到指令 ⇒ **机制直接失效**。所以客户端一律放行（预测结果无害：聚晶本身没有任何交互行为），
  真正的判定、取消与状态修改全在服务端。
- **状态存哪里**：`FocusPoolManager` 的**实例字段** `disabledFoci` / `priorityFoci`
  （不是 static，理由与 0.0.16 的 `castCount` 完全一致：pools 每只仆从各一份 ⇒ 指令天然只作用于这一只；
  做成 static 会让对一只仆从的指令连带影响场上所有调律师与仆从）。落盘到 NBT
  （`DisabledFoci` / `PriorityFoci`，`ListTag<StringTag>`），重启后仍然有效。
- **优先释放怎么"插队"**：`CastChannel` 新增重载
  `beginCast(ServerLevel, FocusEntry entry, double warmupMultiplier)` —— 与抽签版唯一的区别是
  "聚晶不是抽出来的，而是调用方点名的"，**其余流程完全共用** `startCast`
  （conditionsMet → 装配法杖 → 锁池 → startSpell → 锁朝向 → 音效 → onCastStart）。
  调用方要把条目路由到**它自己类别**的通道上（`entry.getCategory()` 决定），
  否则 `tick()` 里"攻击类蓄力中目标丢失则打断"的判定会张冠李戴。
  `FocusPoolManager#takeSpecific(id)` 负责"按 id 取一个当前可用的条目"（不在池中/被禁放/被全局拉黑 ⇒ null，
  调用方退回常规抽签）。
- **禁放怎么生效**：`FocusPoolManager#draw` / `#drawUniform` 的过滤谓词从 `!isBlacklisted(id)`
  改为 `isUsable(id)`（= 未被全局拉黑 **且** 未被本个体禁放）。同一个聚晶同时被"优先"和"禁放"时
  **禁放优先**（禁放是明确的否决）。
- **认主（本轮为可用性做的一个决定）**：刷怪蛋/指令生成的仆从**没有主人**；若严格要求"只有主人能指挥"，
  这条机制将**完全不可达**（用户已确认仆从只用刷怪蛋/指令获取）。
  因此：`TunerServant#adoptOwnerIfUnowned` —— **无主的调律师仆从会在第一次被下达聚晶指令时
  认那位玩家为主人**（附 SOUL 粒子 + INFO 日志 + 动作栏提示）。
  **别人家的仆从则完全不干预**（交回默认的攻击/交互行为）。

**（7）配置（`[servant]` 段，4 项）**

| 键 | 类型 | 默认 | 范围 | 说明 |
|---|---|---|---|---|
| `servant.health` | Int | **40** | 4~1024 | 仆从最大生命（Boss 216 的约 1/5，属"普通仆从"量级） |
| `servant.followRange` | Int | **32** | 8~128 | 索敌半径（写入 `FOLLOW_RANGE`） |
| `servant.castIntervalTicks` | Int | **40** | 0~600 | 两次施法之间的间隔（与聚晶自身的法术冷却是两回事，取更长者） |
| `servant.rotation` | String | **`"23"`** | — | 施法轮换序列（1=防御 2=攻击 3=召唤 4=其他；默认攻击/召唤各半） |

`[servant]` 是**新增段 ⇒ 全部是新增键 ⇒ Forge 自动补进老 toml、无需手工迁移**。
`focus.blacklist` 那一条则**必须手工迁移**（见本节第 (3) 条第 4 点）——**同一轮里两种性质并存，别混为一谈**。

**（8）实测状态**

- `gradlew build` **成功**（**1m19s**），编译期**只有项目既有的基准噪声**
  （3 条 FML deprecated 警告 + 1 条 `TunerBoss` 过时 API 注记），**无新增警告**。
- **jar 条目比对**：102 → **112**，新增 10 条 = 6 个新 class + 2 个模型 json + `textures/item/` 目录
  + 图标 png，**无删除**。
- **原始字节串搜索核对**（不用 javap 文本匹配——javap 会把长字符串折行造成误报）：
  `TunerNetwork` 含 `2.1`；`SAccentWavePacket` 含 `writeDouble`/`readDouble`；
  `ModItems` 含三个物品 id；`ModEntities` 含 `tuner_servant`；`TunerServantInteractions` 含
  `AttackEntityEvent`/`PlayerInteractEvent$EntityInteract`/`IFocus`；`FocusPoolManager` 含
  `takeSpecific`/`isUsable`/`restoreFocusCommands`/`priorityFoci`；`ClientSetup` 含 `TunerServantRenderer`；
  `RippleSpell`、`AccentRipple`、`OrbHighlightSource`、`TunerServantRenderer` 均在 jar 内。
- **`javap` 核对被 reobf 成 SRG 名的覆写**（字节串搜索会漏报）：`TunerServant.m_7301_(MobEffectInstance)`
  确实存在、且正确 `invokespecial Summoned.m_7301_`；构造函数里确有 `m_21409_(EquipmentSlot, F)`
  （= `Mob#setDropChance`）与 `m_21008_(InteractionHand, ItemStack)`（= `setItemInHand`）。
- **配置/资源核对**：`define` 调用 **71** 处、`[servant]` 段 **4** 项、黑名单默认值正确、
  两份 lang **各 37 键**（键数 **27 → 37 / +10**；⚠️ 早期条目里的"各 29"是**文件行数**口径，
  用 `git show HEAD:…lang/zh_cn.json` 对照实测的键数是 27，行数 29 → 39）且 JSON 合法、
  两个模型 json 合法、jar 内 `mods.toml` 版本 `0.0.18`、
  png 魔数正确（694 B / 16×16 RGBA）。
- ⚠️ **一切运行时表现都未实测**，见 §七。**聚晶图标是程序化生成的**（`art/gen_ripple_focus_icon.py`），
  作者 agent **不具备图像输入能力、从未看过它**——`art/preview_ascii.py` 是唯一可用的检视手段
  （ASCII 灰度近似，不是真实观感）。

### 3.19 0.0.19：修「长按类聚晶只放一瞬间」（两层根因）+ 用户另四条反馈

本轮改 **6 个既有 Java 文件 + 4 个新类**（`.java` 共动 **10** 个，总数 **51 → 55**）+ **2 个 lang**
（键数 37 → **43**，即 **+7 新增 / −1 删除**）+ **1 个新模型 json** + **1 个重绘图标 png** + **1 个修改的 art 脚本**
+ 版本号；**配置项数不变仍 71 项 / 11 段**（新增 `casting.channelMaxTicks`、
删除死配置 `servant.health`，净变化 0）；jar 条目 **112 → 117**。

⚠️ **口径说明（本轮两个计数容易被记错，故写明）**：
- 「**6 个既有 Java 文件**」= `entity/ai/CastChannel`、`init/ModItems`、`entity/TunerBoss`、
  `entity/TunerServant`、`entity/TunerServantInteractions`、`config/TunerCommonConfig`。
  **不能**用 `git status` 数 —— 0.0.18 尚未提交，工作区里那 16 个 `M java` 是 0.0.18 留下的
  （本轮是**在 0.0.18 的未提交改动之上**继续改的）。判定方法是 mtime
  （0.0.18 的 clean build 完成于 18:22，本轮改动全部落在 18:54 之后）。
- 「**lang 37 → 43**」是**净变化**：新增 7 个键、**删除 1 个** `info.goetytuner.servant.adopted`
  （随"第一次下指令认主"一起删掉）。对 `git HEAD`(0.0.17) 的 27 键而言则是 **+16 / −0**。

新增 4 个类：

| 类 | 作用 |
|---|---|
| `focus/TunerWand` | 本模组自备法杖（`implements IWand`，`onUseTick` 空实现）—— 让 Mob 能"真的在使用法杖"，见 (1) |
| `combat/TunerDamageRules` | 身份免疫 / 近战易伤判定的**共用实现**（Boss 与仆从），见 (2) |
| `combat/DamageThrottle` | 限伤 + 滑动 1 秒限DPS 的**共用实现**，见 (2) |
| `init/TunerServantSpawnEggItem` | 仆从刷怪蛋（继承 Goety 的 `ServantSpawnEggItem`），见 (3) |

**（1）【用户第 4 条 · 本轮最有价值的一处修复】「长按持续释放」类聚晶只放一瞬间就停**

- **现象（用户原话）**：腐化聚晶、震撼聚晶、炼狱聚晶这类在玩家视角中"长按持续释放"的聚晶，
  调律师（以及仆从）只会释放一瞬间就停下，被当作瞬发法术用了。
- **根因一层 · 只结算一次**：玩家施法路径（Goety `DarkWand.onUseTick`）对
  `IChargingSpell` 的实现是"蓄力到 `castUp` 之后，每 `Cooldown` tick 调一次
  `MagicResults`（→ `SpellResult`），一直持续到松手"；而 `CastChannel` 原先只在**前摇结束时**
  调**一次** `SpellResult`。于是轰炸 / 雷电 / 暴雪 / 箭雨这类"每发生成一个实体"的法术只出了一发。
- **根因二层 · 实体下一 tick 就自毁**（腐化光束最直接的原因，反编译实证）：
  `AbstractBeam.tick()`（`CorruptedBeam` 的基类）里有
  ```java
  if (owner == null || !owner.isAlive() || (this.itemBase && !MobUtil.isSpellCasting(owner))) {
      this.discard(); return;
  }
  ```
  而 `MobUtil.isSpellCasting` 的实现是
  ```java
  livingEntity.isUsingItem()
      && livingEntity.getUseItem().getItem() instanceof IWand
      && !WandUtil.findFocus(livingEntity).isEmpty();
  ```
  **Mob 从不 `startUsingItem`** ⇒ `isSpellCasting` 恒为 false ⇒ 光束刚生成就被丢弃
  ⇒ 玩家看到的就是"闪一下"。
- **修法 · `CastChannel` 新增通道型路径 `tickChannel`**：
  1. **让施法者真的"在使用法杖"**：`startUsingItem(MAIN_HAND)`，并在每 tick 自愈式重设
     （被打断 / 其它模组 `stopUsingItem` 时能恢复）；`finishCast` / `interrupt` /
     `startSpell` 异常 / 外层兜底 catch 四处一律 `stopUsingItem()`（否则光束会永远不消失）。
  2. **按法术自己的节奏反复释放**：`castTicksElapsed >= castUp` 之后，每
     `IChargingSpell#Cooldown(caster, staff, shots)` tick 调一次 `SpellResult`。
  3. 判定依据是 Goety 自己的 `IChargingSpell`（腐化 / 震撼 / 暴雪 / 轰炸 / 旋风 / 箭雨 /
     电击 / 水流 / 蒸汽 / 念力 / 吸取 / 掘地 / 进食 / 飞行 / 防护 / 流星雨 … 全是它的子类），
     **不写死任何聚晶 id** ⇒ 附属模组的同类法术自动受益。
- **⚠️ 关键坑 · 为什么必须自备 `TunerWand` 而不能继续用 `goety:dark_wand`**：
  让 Mob 去"使用" dark_wand 会让 `DarkWand.onUseTick` 每 tick 跑起来，它的收尾是
  `this.MagicResults(stack, worldIn, livingEntity, spell)`；而 `MagicResults` 对
  **非玩家施法者**走的是
  ```java
  } else { this.failParticles(worldIn, caster); worldIn.playSound(..., SoundEvents.FIRE_EXTINGUISH, ...); }
  ```
  分支 —— **既不会释放法术，又会每 `Cooldown` tick 冒一把白烟 + 响一声灭火音**。
  所以本轮自备 `focus/TunerWand implements IWand`：
  - `onUseTick(...)` **空实现**（这就是本类存在的核心理由）；
  - `getUseDuration(stack)` 返回 `72000`（只为不让 `LivingEntity` 自动 `completeUsingItem`，
    真正决定何时松手的是 `CastChannel`）；
  - `initCapabilities` 显式委托 `IWand.super.initCapabilities(...)`（`SoulUsingItemHandler`
    依赖这条 capability，`BossWandHelper.installFocus` 与 `IWand.getFocus` 都走它）；
  - `getSpellType()` = `SpellType.NONE`（与 dark_wand 一致，接受所有聚晶）；
  - 模型 `assets/goetytuner/models/item/tuner_wand.json` 内容只有
    `{"parent": "goety:item/dark_wand"}` ⇒ **外观与之前完全一样**。

  Boss 与仆从的主手都从 `goety:dark_wand` 换成 `goetytuner:tuner_wand`，并在
  `readAdditionalSaveData` 里做**旧档迁移**（老存档主手仍是 dark_wand 时自动换成新杖）。
  **顺带落地了遗留待办「Boss 专属法杖（TunerWand）未做」**（原先用 dark_wand 占位）。
- **用时上限（用户点名要求「给一个用时上限，防止它停不下来」）**：新增配置
  **`casting.channelMaxTicks`**（`IntValue`，**默认 20 = 1 秒**，范围 5~200）。
  三条**独立**的收口条件，谁先到算谁：
  1. 总时长到 `channelMaxTicks`；
  2. 法术自己的 `IChargingSpell#shotsNumber(...)`（`> 0` 时）放完；
  3. 防御性硬上限 `MAX_CHANNEL_SHOTS = 400`。

  到点必然 `finishCast()`（内含 `stopUsingItem`）。

  ⚠️ **第 58 轮已修改这套收口**（见 §3.20(3)）：**第 2 条（`shotsNumber` 放完）已删除** ——
  反编译实证 `DarkWand` 只用 `shotsNumber` 记发数与算释放冷却、**从不据此停火**；
  同时预算改成**两段**（蓄力 + 持续），否则 `castUp` 较大的法术（如箭雨默认 20）会被蓄力吃光预算。
- **⚠️ 顺带澄清用户那个疑问**（"我记得已经给过一种时间限额，我不清楚它是否奏效、
  是不是应用于这一部分"）：**旧键 `casting.maxCastWindowTicks`（默认 50）一直在生效，
  但它管的是"把前摇截断到 2.5 秒"，并不管持续释放。** 长按类法术的
  `defaultCastDuration()` 默认是 **72000**，会被它截成 50 ⇒ 旧行为就是
  "站桩 2.5 秒 → 放**一发** → 结束"，**这正是本 bug 的一部分**。现在两个键分工明确：

  | 键 | 默认 | 管什么 |
  |---|---|---|
  | `casting.maxCastWindowTicks` | 50 | **普通法术**的蓄力截断（2.5 秒；玩家提前松手是合法释放路径） |
  | `casting.channelMaxTicks` | **20** | **长按类法术**（`IChargingSpell`）的**持续段**上限 —— ⚠️ **第 58 轮起明确为"不含蓄力"**，见 §3.20(3) |
- **⚠️ 平衡风险（必须告知）**：腐化光束是**每 tick 造成伤害**的（Goety 默认
  `CorruptedBeamDamage = 10.0`/次，且它把目标 `invulnerableTime` 清零以绕过无敌帧），
  上限 20 已经能打出很高总伤害，调到 100 以上基本等于必杀。觉得太强就把
  `casting.channelMaxTicks` 调小，或把该聚晶写进 `focus.blacklist`。
- **与旧行为的关系**：普通法术（非 `IChargingSpell`）**完全不变** —— 仍是
  `warmup = min(castDuration × 倍率, maxCastWindowTicks)` 然后单次结算。

**（2）【用户第 2 条】仆从的血量 / 护甲 / 减伤限伤与本体一致**

- **血量 / 护甲**：`TunerServant` 构造函数改为直接读 **Boss 的配置键**
  `boss.maxHealth`（默认 216）与 `boss.equivalentArmor`（默认 16），
  `KNOCKBACK_RESISTANCE` 也对齐成 1.0；`createAttributes()` 的占位常量同步改成同一组
  （避免"config 未加载时创建出的实体数值不一致"这种边角差异）。
  **`servant.health`（默认 40）已删除** —— 它现在是死配置。
  ⚠️ 删除一个**已存在**的键 ⇒ 老 toml 里那一行会变成孤儿条目，Forge 会自行处理，
  **不需要手工迁移**（与"改已有键的值"是两回事，见 §3.18(3)）。
- **减伤 / 限伤**：把 Boss 原先**内联**的规则抽成**共用实现**（本项目红线：同一职责只允许
  一处实现），两边各持一份实例、共用同一份代码：

  | 新类 | 内容 | 谁在用 |
  |---|---|---|
  | `combat/TunerDamageRules` | `isIdentityImmune`（摔落 / 火焰 `IS_FIRE` 全系 / 窒息 / 溺水）、`isDirectMelee`、`applyMeleeVulnerability`（近战易伤 ×(1+`boss.meleeVulnerability`)，默认 +25%） | `TunerBoss.hurt`（**行为不变，只是搬家**）+ `TunerServant.hurt` |
  | `combat/DamageThrottle` | 限伤（单次 ≤ 最大生命 × `boss.maxHitDamagePercent`）+ 限DPS（滑动 1 秒窗口 `boss.maxDamagePerSecond`，预算耗尽返回 `-1` = 整段吸收）。0.0.6 时这段是 `TunerBoss` 里的环形缓冲 + 3 字段 + 3 方法，现整体搬入 | `TunerBoss.actuallyHurt`（**行为不变**）+ `TunerServant.actuallyHurt` |

- 仆从的 `actuallyHurt` 与 Boss 的唯一差别是**没有后门豁免**：
  `BYPASSES_INVULNERABILITY`（`/kill` 的 generic_kill、掉出世界）本来就该正常生效，
  仆从不需要"管理员杀不死"这种保护；索命后门同理（详见 §3.17）。
- ⚠️ **刻意不含 Boss 的锁血阶梯**（`lockMark` / 宽限期免疫 / 致死截断 / `/kill` 后门 /
  索命后门）—— 那是 Boss 的招牌机制，仆从若也锁血就成了打不死的怪。
  这一取舍写在 `TunerDamageRules` / `TunerServant` 的类注释里，**以免后人误判成漏做**。

**（3）【用户第 3 条】仆从刷怪蛋改成 Goety 的范式：直接放 = 野生、潜行放 = 认主**

- 新增 `init/TunerServantSpawnEggItem`，**直接继承 Goety 本体的
  `com.Polarice3.Goety.common.items.ServantSpawnEggItem`**（本体所有仆从蛋都用它）。
  它的逻辑正是用户描述的那个范式：
  ```java
  Entity entity = entitytype.spawn(serverLevel, itemstack, player, pos, MobSpawnType.SPAWN_EGG, ...);
  if (player != null && entity instanceof IOwned owned && !owned.isHostile()) {
      if (player.isCrouching()) {                       // ← 潜行 = 认主
          owned.setTrueOwner(player);
          ForgeEventFactory.onFinalizeSpawn(mob, serverLevel, ..., MobSpawnType.SPAWN_EGG, null, tag);
      }
  }                                                     // ← 不潜行 = 野生（无主人）
  ```

  本子类只外加两件事：① 放置时给**动作栏提示**（认主 / 野生两种文案）；
  ② tooltip 多一行本模组专属说明（Goety 的 `super` 已经加了它自己那条通用提示）。
  （"对着液体放置"那条路径同样享有该范式，因为它在父类里。）
- **归属不再因交互而改变（这是用户投诉的点）**：0.0.18 的
  **"野生仆从第一次被下聚晶指令时认主"（`TunerServant#adoptOwnerIfUnowned`）已彻底删除**。
  现在归属**只在刷怪蛋放置时**确定，`TunerServantInteractions#isOwner` 只认主人：
  - **野生仆从**：被手持聚晶左/右键时给出提示（`info.goetytuner.servant.wild_hint`），
    且**不吞掉**这次攻击/交互（不 `setCanceled`），行为完全可预测；
  - **别人家的仆从**：同理，提示 `info.goetytuner.servant.not_owner_hint`；
  - 野生仆从本身仍然会活动（`Summoned.finalizeSpawn` 在无主人时 `setWandering(true)`；
    `SummonTargetGoal` 的谓词对无主 `IOwned` 也会索敌敌对生物）—— 它是"野生动物"，
    只是不接受指挥。

**（4）【用户第 1 条】「左键『不释放』似乎不能取消」——语义重做**

- **先如实说明**：本轮**没能复现一个机械故障**（Forge 钩子位置已用 `javap` 实证：
  `AttackEntityEvent` 在 `Player.attack` 偏移 0 处**无条件**触发；服务端取消不会阻止客户端
  发攻击包；两次点击的状态机在代码上是对称的）。但**存在一个必然导致该症状的语义陷阱**，已修：

  | 0.0.18（有陷阱） | 0.0.19（已修） |
  |---|---|
  | 「不释放」与「优先」是**两张互不相干的表**，同一聚晶可同时命中（此时"不释放"胜出） | 两态**互斥**：左键顺手清该聚晶的"优先"、右键顺手清"不释放" |
  | 于是"先右键设优先、再左键设不释放，然后**按右键想取消**" ⇒ **按了没反应**（右键改的是"优先"，而"不释放"还在） | 任一个按钮都**必然**能让该聚晶回到中立状态，语义单一可逆 |
  | 提示只说"已设为/已取消 X"，无法确认到底有没有生效 | 提示**列出两张表的条目数**（例如「优先释放：火焰聚晶 ｜不释放 2 项 / 优先 1 项」） |

- 实现：`TunerServant#toggleFocusDisabled` / `#toggleFocusPriority`（互相清理），
  新增 `#clearFocusCommands()` + `#disabledCount()` / `#priorityCount()`；
  新增 **潜行 + 左键 = 清空该仆从的全部聚晶指令**（`info.goetytuner.servant.cleared`）
  作为**必然可用的"重置"逃生通道**。

**（5）【用户第 5 条】调律波纹聚晶的图标改灰黑 + 白**

- `art/gen_ripple_focus_icon.py` 的配色由紫改成灰黑：
  轮廓 `#080808`、盘面 `#3A3A3A → #161616` 渐变、亮环 `#F0F0F0`、圆心 `#FFFFFF`；
  原先"白→青"的圆心渐变也去掉了（灰黑配色下不再引入第三色）。
- 重新生成：**694 → 627 B**，尺寸仍是 16×16 RGBA8。脚本仍是**字节可复现**的
  （连跑两次 sha256 相同，已实测）。
- ⚠️ 作者 agent **不具备图像输入能力、从未看过它**，只做了 ASCII 灰度近似检视
  （`art/preview_ascii.py`）。观感仍需人类过目。

**（6）附：两个被追问过的设计问题（0.0.19 收尾补充）**

**(6a) 「长按类 / cooldown / shotsNumber」是自动检测的吗？——是，且是接口闭包级的自动**

`CastChannel#startCast` 的判定只有一条：

```java
channeled = spell instanceof com.Polarice3.Goety.api.magic.IChargingSpell;
```

三个量全部**从法术自己的接口方法现读**，不写死任何聚晶 id、也不缓存：

| 量 | 来源 | 语义 |
|---|---|---|
| 是否"长按类" | `spell instanceof IChargingSpell` | Goety 自己的接口；`IBreathingSpell extends IChargingSpell`、`ChargingSpell implements IChargingSpell`、`EverChargeSpell extends ChargingSpell`、`BreathingSpell extends EverChargeSpell implements IBreathingSpell` |
| 蓄力多久 | `IChargingSpell#castUp(caster, staff)` | 读 Goety 的 `SpellConfig.*ChargeUp`（如 `CorruptionChargeUp` 默认 0） |
| 每发间隔 | `IChargingSpell#Cooldown(caster, staff, shots)` | 读 `SpellConfig.*CoolDown`；`EverChargeSpell`/`IBreathingSpell` 固定返回 0 = **每 tick 一发** |
| 最多几发 | `IChargingSpell#shotsNumber(caster, staff)` | 读 `SpellConfig.*Duration`；**0 = 无限**（Goety 配置注释原话："setting it to 0 will allow the spell to be cast indefinitely"）⇒ 本模组由 `casting.channelMaxTicks` 兜底 |

因为走的是**继承闭包**，本体与**任意附属**的同类法术自动覆盖。用
`scripts/scan_charging_spells.py`（真·class 文件解析：常量池 + `super_class` + `interfaces[]`）
扫过本机整合包，落在这个闭包里的具体法术共 **26 个**：

| 来源 | 个数 | 例 |
|---|---|---|
| Goety 本体 2.5.56.5 | 19 | `CorruptedBeamSpell`(腐化) / `ShockingSpell`(震撼) / `BlizzardSpell` / `BombardmentSpell` / `MeteorShowerSpell` / `WhirlwindSpell` / `ArrowRainSpell` / `FireBreathSpell` / `FrostBreathSpell` / `SwarmSpell` / `WaterJetSpell` / `SteamSpell` / `TelekinesisSpell` / `LeechingSpell` / `BurrowingSpell` / `FeastSpell` / `FlyingSpell` / `WardingSpell` / `SurgingSpell` |
| GoetyAwaken（觉醒）1.3.9.1 | 4 | `AgonyFocusSpell` / `ChipRainFocusSpell` / `FireballFeastFocusSpell` / `HeavenRiftSpell` |
| goety_ladder（阶梯）1.1.6 | 2 | `DragonBreathSpell` / `VoidArrowSpell` |
| goety_cataclysm（灾变）1.20-1.9.1 | 1 | `AshenBreathSpell` |

（另有 4 个是接口/抽象基类本身：`IBreathingSpell` / `ChargingSpell` / `EverChargeSpell` / `BreathingSpell`。）

⇒ **本模组从来没写过、也不需要写任何聚晶白名单**；换个附属、加个附属，判定自动跟上。
**这条也可在日志里直接验证**：0.0.19 起启动时会打一行
`[Tuner] Channelled (IChargingSpell) foci auto-detected: N / M — e.g. …`
（`FocusPoolManager#logChanneledFoci`，扫描与黑名单热刷新时各打一次），
N 就是**实际注册成聚晶**的同类数量。

**（6b）「Boss 专属法杖」会不会与「用法杖召唤 Boss → 击败后拿强化法杖」自相矛盾？——不会，两条链完全不相交**

这是两个**不同的物品、不同的角色**，Boss 那把**从来不是奖励、也拿不到**：

| | **玩家的法杖** | **Boss 的调律师法杖** `goetytuner:tuner_wand` |
|---|---|---|
| 是什么 | 玩家自己拥有的任何 `goety:wands` 标签法杖（或 `wand_whitelist` 里补的附属法杖） | 模组配发给 AI 的**施法载体**，构造函数 `setItemInHand(MAIN_HAND, …)` |
| 用途 | ① 在暗黑祭坛上**激活召唤仪式**（`TunerSummonRitual#identify`：`goety:wands` 标签 ∨ 配置白名单）② 死亡时作为**快照**被掉落 | 只有一个：装聚晶 + 撑起 `MobUtil.isSpellCasting` 要求的"正在使用一把 IWand" |
| 玩家能拿到吗 | **能**，而且带"调律"加成（巫法 +10% / 魔法伤害 +40%，可叠加，经验 ×4） | **不能**：`setDropChance(MAINHAND, 0.0F)`；且它 `use()` 返回 PASS、`onUseTick` 空实现 ⇒ 拿到也**无法施法**；也**没有**进创造模式标签页 |
| 掉落来源 | `WandUpgradeEvents` 掉 `boss.getOriginalWand()`（= 仪式激活瞬间对**玩家手持法杖**做的 `copy()` 快照） | 无 |

**所以"击败 Boss 获得强化法杖"这条设计一个字都没变** —— 掉落的仍是**玩家自己那把杖**，
加成 NBT 也仍然写在它上面。`tuner_wand` 只是**技术替代品**，它相对 `goety:dark_wand` 的
"特殊性"**全部是机制性的、不是玩法强度**：

1. **`onUseTick` 空实现**（它存在的**唯一**理由）：`DarkWand.onUseTick` 对**非玩家施法者**会走
   `MagicResults` 的 `failParticles + FIRE_EXTINGUISH` 分支 —— 冒白烟、响灭火音、还**不放法术**。
   0.0.19 为了让 Mob"真的在使用法杖"（修长按类聚晶）必须让它 `startUsingItem`，于是这个分歧就致命了。
2. **`initCapabilities` 显式委托 `IWand.super.initCapabilities(...)`**：保证
   `SoulUsingItemHandler` capability 存在（`BossWandHelper.installFocus` 与 `IWand.getFocus` 都依赖它）。
   与 dark_wand 等价。
3. **`getUseDuration` = 72000**：让 `LivingEntity` 不会自动 `completeUsingItem`，何时松手由
   `CastChannel` 决定。
4. **外观零变化**：模型 `{"parent": "goety:item/dark_wand"}`。

**桥接**（新老存档、以及和玩家链路的衔接）：

- 老存档的 Boss 主手存的是 `goety:dark_wand` ⇒ `TunerBoss#readAdditionalSaveData` 里有**精确迁移**
  （`isLegacyDarkWand` 判定后换成 `tuner_wand`），玩家无感。
- **没有加进 `goety:wands` 标签**、也没加进 `wand_whitelist` ⇒ 它**不能**激活召唤仪式，
  不会和"玩家用哪把杖召唤"这件事产生任何交集。
- Boss 主手掉落概率已显式置 0（顺带说明：`javap` 反编译确认 `Mob.getEquipmentDropChance(MAINHAND)`
  读的是 `handDropChances[0]`、默认本来就是 0.0F，所以**以前也没掉过**；显式写 0 是为了把意图钉进代码）。
  否则玩家会得到一把"看着像暗法杖却不能用"的废品，还会连带掉出当时装在杖里的聚晶。

**（6b-补充）法杖的"模型 / 手持渲染"会受影响吗？—— 不会，已逐条核实；但顺带查出一处必须对齐的成员**

**① 模型：`tuner_wand` 是 `dark_wand` 的逐字别名，不存在"两套模型"。**

```json
// assets/goetytuner/models/item/tuner_wand.json
{ "parent": "goety:item/dark_wand" }
```

模型父级是跨命名空间的 `ResourceLocation`，Minecraft 正常解析（唯一前提是 Goety 已加载 —— 本模组硬依赖它）。
于是**几何（5 个 elements）+ 贴图（`goety:item/dark_wand`）+ 整个 `display` 块（first/third person、gui、
ground、fixed）全部原样继承**。已核实父级资源确实存在于**部署的** `goety-2.5.56.5.jar` 里：
`assets/goety/models/item/dark_wand.json`（3366 B）+ `assets/goety/textures/item/dark_wand.png`（481 B）。
⇒ **画面上像素级一致**，没有任何模型需要重做。

**② 生物手持渲染链（与"是否正在使用"无关，已用 `javap` 逐段确认）**：

```
TunerRenderer/TunerServantRenderer (HumanoidMobRenderer)
  └─ 构造器 addLayer(new ItemInHandLayer(this, ctx.getItemInHandRenderer()))   // 字节码确认
       └─ ItemInHandLayer.render → renderArmWithItem(..., ItemDisplayContext.THIRD_PERSON_RIGHT_HAND / _LEFT_HAND, ...)
            └─ ItemInHandRenderer.renderItem(...)  → 按物品模型 + display 变换绘制
```

- **`ItemInHandLayer` 完全不读 `isUsingItem`**（字节码里没有该调用）⇒ 0.0.19 新增的
  `startUsingItem` **不会**改变法杖的握持外观。
- **1.20.1 的 `HumanoidMobRenderer` 里根本没有 `getArmPose`/`setModelProperties`**（整个类只有构造器 +
  三个 `addLayer`）⇒ 手臂姿态不受手持物影响，也不存在"举杖"姿态差异。
- `DarkWand.DarkWandClient`（`IClientItemExtensions`：手臂姿态 + 第一人称手持变换）**只服务玩家** ——
  它是 `PlayerRenderer`/`LocalPlayer` 路径，对 Mob 无效，所以本模组的杖**不需要**、也没有实现它。
  （副作用：玩家若用指令拿到 `tuner_wand`，第一人称手持变换是默认的 —— 但该物品不在创造标签页、
  也不可合成，属预期。）

**③ 「Boss 手持的法杖是跟随召唤用法杖的」这个前提不成立（重要澄清）。**
Goety/本模组**都没有**"Boss 手持外观跟随玩家召唤用杖"这种机制：`TunerBoss` 的主手是**构造函数写死**的
（0.0.8 起是 `goety:dark_wand`，0.0.19 起是 `goetytuner:tuner_wand`），玩家仪式里用哪把杖**只影响掉落物**
（`TunerSummonRitual` 对**玩家手持杖**做 `copy()` 快照 → `WandUpgradeEvents` 掉它）。
所以"自定义后会不会影响跟随"这个担心没有对应的既有行为 —— **原本就没有"跟随"**。

**④ 顺带查出一处真需要对齐的 `IWand` 成员：`getWandVisualHeight`。**
`IWand` 的 default 是 **0.8F**，而 `goety:dark_wand` 覆写成 **0.4F**（`DarkStaff` 又是另一个值）。
它被用来把"从杖尖发出"的东西对齐到杖尖，共 8 处引用：

| 用处 | 位置 | 是否作用于 Mob |
|---|---|---|
| 水流喷射 / 掘地激光 / 棱镜光束三条世界空间光束的起点 | `WaterJetRenderer` / `BurrowingLaserRenderer` / `PrismaBeamRenderer` | **否** —— `ClientEvents` 用 `for (Player player1 : players)` 驱动、方法签名收 `Player` |
| 法杖粒子起点高度 | `Spell#useParticle` 里发 `SStaffParticlePacket` | **否** —— 显式写在 `if (caster instanceof Player player)` 分支，非玩家走 `else` 的 `gatheringParticles(...)` |

⇒ 对 Boss/仆从而言该值**当下没有可见影响**；但 `TunerWand` 仍已覆写成 **0.4F**，理由是本杖的定位是
dark_wand 的**完全等价替身**：凡是 dark_wand 定制过的 `IWand` 成员一律对齐（`javap` 列出 `DarkWand` 的
全部覆写后确认，**`getWandVisualHeight` 是唯一需要对齐的一个** —— 其余全是玩家专用路径：
施法、`onLeftClickEntity`/`interactLivingEntity`/`useOn`、tooltip、NBT 记账 `SoulCost/CastDuration/Cooldown`）。
万一将来 Goety 或某个附属把这条路径扩展到非玩家施法者，我们就是对的。

**（7）实测状态**

- `gradlew build` **成功**（约 1 分钟），编译期**只有项目既有的基准噪声**
  （3 条 FML deprecated 警告 + 1 条 `TunerBoss` 过时 API 注记），**无新增警告**。
- **jar 条目比对**：112 → **117**（新增 5 = 4 个新 class + `tuner_wand.json`）。
- **原始字节串搜索核对**：`CastChannel` 含 `tickChannel`/`channeled`/`channelEndTick`/`IChargingSpell`；
  `TunerWand` 含 `initCapabilities`；`TunerServant` 含
  `toggleFocusDisabled`/`toggleFocusPriority`/`clearFocusCommands`/`disabledCount`/`priorityCount`/
  `TUNER_WAND`/`DamageThrottle`/`TunerDamageRules`；`TunerServantSpawnEggItem` 含
  `ServantSpawnEggItem`/`spawn.tamed`/`spawn.wild`；`TunerServantInteractions` 含
  `isOwner`/`wild_hint`/`not_owner_hint`；`ModItems` 含 `tuner_wand`。
- **`javap` 核对被 reobf 成 SRG 名的覆写与调用**（字节串搜索**会漏报**这类）：
  `TunerWand` 方法表 → `m_5929_`(=onUseTick) / `m_8105_`(=getUseDuration) / `m_7203_`(=use) /
  `initCapabilities`；`TunerServant` → `m_6469_`(=hurt) / `m_6475_`(=actuallyHurt) /
  `m_7301_`(=canBeAffected)；`CastChannel.tickChannel` 字节码内确有
  `LivingEntity.m_6117_()`(=isUsingItem) 与 `m_6672_(InteractionHand)`(=startUsingItem)，
  `stopChannelUse` 内确有 `m_5810_()`(=stopUsingItem)，且正确
  `invokeinterface IChargingSpell.castUp / Cooldown / shotsNumber`。
- **配置 / 资源核对**：`define` 调用 **71** 处、section **11** 个、`channelMaxTicks` 存在、
  `servant.health` 已移除；两份 lang **各 43 键**且 JSON 合法、jar 内 `mods.toml` 版本
  `0.0.19`、png 魔数正确。
- **部署核对**：构建产物与已部署 jar **逐字节相同**（同一 md5）。
- ⚠️ **五条修复的运行时表现全部未实测** —— 见 §七.11（⚠️ 其中「用时上限」的收口条件在**第 58 轮已被 §3.20 修改**：删掉了 `shotsNumber` 收口、预算改成两段）。

---

### 3.20 0.0.19 修补（第 58 轮）：提示精简 + 修「不释放不起效」+ 修「箭雨不持续」

**版本号没有变（仍是 `0.0.19`）**，只改代码与 lang。改 **5 个既有 Java 文件**：
`entity/ai/CastChannel`、`entity/TunerServant`、`entity/TunerServantInteractions`、
`init/TunerServantSpawnEggItem`、`focus/FocusPoolManager` + **2 个 lang**（键数 **43 → 34**，即 **−9 / +0**）。
**没有新增或删除任何类与资源** ⇒ jar 条目 **117 → 117（无增无删）**；
**配置项数仍 71 / 11 段 —— 本轮一个配置键都没动**；网络协议仍 **2.1**。

**（1）【用户第 1 条】提示精简到一行**（原话："不需要更多的物品介绍和放置/交互后的提示"）

- **lang**：两份各 **43 → 34 键（净 −9）**，**只保留** `tooltip.goetytuner.servant.spawn_egg` =
  「调律师仆从刷怪蛋，潜行使用会生成你自己的仆从.」（en：`Tuner Servant Spawn Egg. Sneak-use to spawn one of your own.`）。
  被删掉的键包括刷怪蛋放置时的两条动作栏提示、`info.goetytuner.servant.wild_hint` /
  `not_owner_hint`，以及随功能一起消失的 `...adopted`（已在 0.0.19 第 57 轮删除）等。
- **`init/TunerServantSpawnEggItem`**：**删掉 `useOn` 覆写**（原先在里面打完动作栏提示再转 `super`）⇒
  本类现在**只剩两件事** —— ① 继承 Goety `ServantSpawnEggItem` 拿到"直接放 = 野生 / 潜行放 = 认主"的范式；
  ② `appendHoverText` 里加**唯一那一行** tooltip，**并且不再调用 `super`**（连 Goety 自己那句通用说明也去掉）。
- **`entity/TunerServantInteractions`**：**删掉两条提示**（`wild_hint` / `not_owner_hint`）。
  ⚠️ **语义没变** —— 仍然是"**只给提示、不吞掉**"的那个行为（**交互 / 攻击照常生效**），
  只是**不再往动作栏弹字**；`canCommand` 拒绝时改打 **INFO 日志**：
  `[Tuner] Servant focus command refused (owner=<uuid>) for <focus id>`。
- **规律（本轮沉淀）**：**诊断信息走日志，不要走动作栏** —— 动作栏是玩家的，日志是开发者的。
  面向玩家的文字**只留必要的一行**；状态变化（如聚晶被切成"不释放"）写 INFO 即可。

**（2）【用户第 2 条】修「不释放的调整功能似乎完全不起效了」**

⚠️ **如实说明**：本轮**没有复现证据**（未进游戏），以下是**两条最可能的解释**，两条**都已堵掉**。

- **病根（a）· 野生仆从被 `canCommand` 拒绝（最可能的真凶）**：
  第 57 轮把 `isOwner` 收紧成"只认主人"（`owner != null && owner == player`），而
  **刷怪蛋的默认用法是"直接放" = 野生（`owner == null`）** ⇒ **用户按默认方式放出来的仆从，
  每一条聚晶指令都在 `canCommand` 被拒绝** —— 从玩家视角看就是"这个功能**完全**不起效"，
  而且第 57 轮把提示也做成了"只给提示、不吞掉" ⇒ **连一句解释都没有**，更显得是坏掉了。
  **修法**：`canCommand` 改为 **`owner == null || owner == player`** ——
  **野生仆从本来就是可配置的**（它没有主人，也就该听所有玩家的指挥），
  **只有"别人家的仆从"（`owner != null && owner != player`）才拒绝**。
- **病根（b）· "潜行 + 左键"劫持了左键**：第 57 轮自己加的
  **`clearFocusCommands`（潜行 + 左键 = 清空该仆从全部聚晶指令）** 会在**潜行状态下抢走左键** ——
  玩家潜行按左键得到的是"清空"，**看起来就是"左键切不动不释放"**（用户报的正是"左键的不释放"）。
  **修法**：该功能与配套的 `clearFocusCommands` / `disabledCount` / `priorityCount` **一并删除** ⇒
  左键语义**只剩**"切换不释放"、右键**只剩**"切换优先释放"，两者**仍然互斥**
  （`toggleFocusDisabled` 顺手清 `priorityFoci`、`toggleFocusPriority` 顺手清 `disabledFoci`，
  任一键都能把该聚晶切回中立）。
- **规律（本轮沉淀）**：**不要给同一个输入加"隐藏的组合键"** —— 组合键会**静默劫持**基础操作，
  让基础操作"看起来坏了"；要"一键恢复"就再按一次同一个键，不要发明新组合。
  另一半：**拒绝一个操作时，必须留下可查的痕迹**（日志），否则玩家只会得到"没反应"。

**（3）【用户第 3 条】修「箭雨聚晶几乎没有持续」（用户怀疑"是否能完美地识别持续类"）**

- **先核对识别：识别没有问题。** `scripts/scan_charging_spells.py`（真·class 文件解析，
  枚举出 **26 个**具体长按类法术）实证 **`ArrowRainSpell extends EverChargeSpell`**
  （`EverChargeSpell → ChargingSpell implements IChargingSpell`）⇒ **本来就在闭包内、本来就被识别**。
- **真因 · 通道预算把"蓄力"算进了"总时长"**：`ArrowRainSpell.castUp` 由
  `new ArrowRainChargeUp(...)` 解算、**默认 20 tick**；而第 57 轮的预算是
  `channelEndTick = channelMaxTicks`（默认 **20**）⇒ **蓄力一个人就吃光了整个预算**，
  到达 `castUp` 的同时也就到了 `channelEndTick` ⇒ **只放一发就收手**。
  ⚠️ 这**不是箭雨独有** —— 任何 `castUp` 接近或超过 `casting.channelMaxTicks` 的长按类聚晶都会这样
  （第 57 轮只验证过"腐化光束能持续"，而它的 `castUp` 极小、恰好绕过了这个坑）。
- **修法 · 两段预算**：
  ```java
  int sustain   = Math.max(5, CHANNEL_MAX_TICKS.get());                                  // 持续段
  int charge    = Mth.clamp((int) Math.round(castUp * warmupMultiplier), 0, MAX_CAST_WINDOW_TICKS); // 蓄力段
  channelChargeTicks = charge;  channelEndTick = charge + sustain;
  ```
  ⇒ **`casting.channelMaxTicks` 现在只承诺"持续段"的时长**（配置注释已写清"**不含蓄力**"）。
  收口条件随之只剩 **三条**：持续段到点 / `MAX_CHANNEL_SHOTS = 400` 防御性硬上限 / 被打断。
- **顺带删掉「按 `shotsNumber` 提前收手」**：反编译实证 Goety `DarkWand` 对 `shotsNumber` **只用来
  记发数与计算释放后的冷却，从不据此停火**（玩家不松手就一直放）⇒ 如果据此收手，就是**不忠实的模拟**。
- **实测口径**（默认配置）：**腐化光束共 20 tick**（`castUp` 极小 ⇒ 几乎全给了持续）、
  **箭雨共 40 tick**（20 蓄力 + 20 持续）、震撼 / 炼狱等按各自 `castUp` 顺延。
  ⚠️ **平衡风险照旧**：腐化光束**每 tick 造成伤害**（`CorruptedBeamDamage` 默认 10.0 且清零
  `invulnerableTime`），**`channelMaxTicks` 调到 100 以上基本等于必杀**。

**（4）本轮验证做到什么程度（同样没有进游戏实测）**

- `gradlew build` **成功**（**约 1 分钟**，只有项目既有基准噪声：3 条 FML deprecated 警告 +
  1 条 `TunerBoss` 过时 API 注记，**无新增警告**）。
- **jar 条目比对**：**117 → 117**（无增无删）。
- **原始字节串搜索核对**：`CastChannel` 含 `channelChargeTicks` / `channelEndTick` / `IChargingSpell`；
  `TunerServant` 含 `toggleFocusDisabled` / `toggleFocusPriority` / `TUNER_WAND` 且**不再含**
  `clearFocusCommands` / `disabledCount` / `priorityCount`；`TunerServantInteractions` 含 `canCommand` /
  `Servant focus command refused` 且**不再含** `wild_hint` / `not_owner_hint`；
  `TunerServantSpawnEggItem` **不再有 `useOn`**；`FocusPoolManager` 含 `setFocusDisabled` / `setFocusPriority`。
- **配置 / 资源核对**：`define` 调用 **71** 处、section **11** 个、**两份 lang 各 34 键且 JSON 合法**、
  **无残留** `info.goetytuner.servant.*`、jar 内 `mods.toml` 版本 `0.0.19`、png 魔数正确（627 B / 16×16 RGBA）。
- **部署核对**：构建产物与已部署 jar **逐字节相同**（1,785,988 B / `77DAAFB0…` / 117 条目）。
  ⚠️ **部署时游戏正在运行**（`java-runtime-epsilon`，19:45 启动）⇒ **运行中的实例仍是旧字节码**，
  修复**必须完全重启游戏**才生效。
- ⚠️ **三条修复的运行时表现全部未实测** —— 见 §七.12。

---

### 3.21 0.0.20：提示回补 + 聚晶包批量指令 + 仆从的仪式召唤（杖决定强度）

**版本号 `0.0.19 → 0.0.20`**。用户在这一轮提了**一条回归反馈 + 三条新功能**。

**规模**：新增 **4 个 Java 类**（`.java` **55 → 59**）+ **1 个仪式配方 JSON**；
改 **6 个既有 Java 文件**（`entity/TunerServant`、`entity/TunerServantInteractions`、
`ritual/ModRituals`、`ritual/WandUpgradeEvents`、`config/TunerCommonConfig`、`gradle.properties`）
+ **2 个 lang**（键数 **34 → 43**，即 **+9 / −0**）；
**配置 71 → 76 项**（`[servant]` 段 **3 → 7**，新增 `wandBlessingEnabled`；段数仍 **11**）；
jar 条目 **117 → 122**（新增 3 = 2 个新 class + `tuner_servant_ritual.json`，**无删除**）；
网络协议仍 **2.1**（未动）。

新增 2 个类：

| 类 | 作用 |
|---|---|
| `combat/ServantWandBlessing` | 「召唤用杖的调律·魔法伤害加成 → 仆从持续性增益」的**唯一实现**，见 (3) |
| `ritual/TunerServantSummonRitual` | 调律师仆从的仪式召唤（中心放带调律加成的法杖），见 (2) |

**（1）【回归】左右键的聚晶指令提示被误删 —— 已回补**

> ⚠️ **后续纠正（0.0.20 追加4）**：本节与 §3.21(2) 讨论的是"提示"与另外两条附带问题；
> **"左键完全不起效"的真根因**在 §3.21(5d) —— **攻击事件在按住左键期间每 tick 触发一次**
> （原版先发包、后节流），所以 toggle 会以 20Hz 翻转。
> 那两条（野生仆从被拒 / 潜行劫持）是**确实存在、也确实修掉了**的问题，但**不是主因**。


- **发生了什么**：0.0.19 第 58 轮用户要求"不要冗余提示"，我把
  `TunerServantInteractions` 里**所有**动作栏提示都删了 —— 包括**左右键的指令结果提示**。
  用户随后指出："左键和右键功能都没有提示了；是不是我让你删提示的时候你把左右键的提示也删了，
  **这个提示是需要的**。"
- **教训**：用户说"不要提示"时指的是**物品介绍 / 放置提示**这类**冗余信息**，
  **不包含"操作结果反馈"** —— 后者是交互闭环的一部分，删掉会让玩家无法判断指令是否生效
  （正是红线 17 说的"静默拒绝 = 看起来没反应"）。
  ⇒ **现在的取舍（写进红线 18）**：
  - **要有提示**：一次主动操作**产生了什么状态变化**（设成了什么、取消了没有、为什么被拒）；
  - **不要提示**：物品介绍、自动触发的环境提示（如放置刷怪蛋）。
- **恢复的文案**（动作栏，aqua 色；被拒时红色）：

  | 操作 | 成功 | 再操作一次 |
  |---|---|---|
  | 左键 | `不释放：%s` | `已取消不释放：%s` |
  | 右键 | `优先释放：%s` | `已取消优先释放：%s` |
  | 左键（袋子） | `不释放：%s 个聚晶（聚晶包）` | `已取消不释放：%s 个聚晶（聚晶包）` |
  | 右键（袋子） | `优先释放：%s 个聚晶（聚晶包）` | `已取消优先释放：%s 个聚晶（聚晶包）` |
  | 别人家的仆从 | `这不是你的调律师仆从` | — |

  `%s` 是**聚晶自己的显示名**（`ItemStack#getHoverName`，走玩家的语言文件），不是注册 id。
- ⚠️ **仍然保持"不要"的部分**：刷怪蛋**放置**时不弹提示、物品说明只有那一行 tooltip
  （见 §3.20(1)）—— 这部分没有被回退。
- 无论有没有提示，**状态变化一律同时打 INFO 日志**（`FocusPoolManager#setFocusDisabled/setFocusPriority`），
  "看起来没反应"永远查得到。

**（2）【新功能】调律师仆从的仪式召唤**

用户规格："调律师仆从的召唤：**每秒 10 能量，10 秒，魔法仪式**；2 个紫水晶、2 个紫水晶、1 个红石、
1 个钻石、1 个金锭、1 个青金石；**中心放上一把「有调律加成」的法杖**即可召唤。"
（配方里的"2 个紫水晶 ×2"已与用户确认 = **4 个紫水晶碎片**。）

- **配方**：`data/goety/recipes/tuner_servant_ritual.json`
  ```json
  { "type": "goety:ritual", "ritual_type": "goetytuner:tuner_servant_summon",
    "activation_item": { "tag": "goety:wands" }, "craftType": "magic",
    "entity_to_summon": "goetytuner:tuner_servant",
    "soulCost": 10, "duration": 10,
    "ingredients": [ amethyst_shard ×4, redstone, diamond, gold_ingot, lapis_lazuli ] }
  ```
- **⚠️ `soulCost` / `duration` 的单位（反编译实证，`DarkAltarBlockEntity`）**：
  灵魂消耗与 {@code currentTime++} **都在同一个 `gameTime % 20 == 0` 分支里**
  ```java
  if (level.getGameTime() % 20L == 0L) {                    // ← 每秒一次
      if (cursedCage.getSouls() >= recipe.getSoulCost()) {
          cursedCage.decreaseSouls(recipe.getSoulCost());
          ... this.currentTime++;                            // ← 计时也只在每秒加一
      } else { /* info.goety.ritual.noSouls.fail */ }
  }
  ```
  ⇒ **`soulCost` = 每秒消耗的灵魂数，`duration` = 仪式持续的秒数**。
  "每秒 10 能量、10 秒" 因此**正好**是 `soulCost: 10, duration: 10`（共 100 灵魂）。
  （对照：Boss 那条是 `soulCost 3 / duration 10`；Goety 本体的仆从召唤多为 `1 / 10`。）
- **`craftType: "magic"`** = Goety 的 `MagicRitualType`（魔法仪式），与 Boss 仪式同类型。
- **"中心放法杖"能生效的原因**（与 0.0.18 的 Boss 仪式同一条链）：
  Goety 黑暗祭坛的 `activate()` → 遍历配方 → `RitualRecipe.matches()` →
  `Ritual.identify(level, pos, player, stack)`，其中 `stack` 就是**祭坛中心格**里的物品；
  仪式结束时 `SummonRitual.finish` 会 `shrink(1)` 掉它（**法杖被消耗**）。
- **激活条件是 NBT 级的**：`activation_item` 只能写物品 / 标签，**表达不了 NBT**，
  所以 `TunerServantSummonRitual` **完全重写 `identify`**：
  要求中心物品是 `IWand` **且** `goetytuner.magic_damage_bonus > 0`
  （即 tooltip 上有「调律:魔法伤害加成」那一行）。不满足 ⇒ 返回 false ⇒
  **复用 Goety 自己的失败提示**（"无效的仪式" / "物品不是仪式催化剂"），不另造文案。
  ⚠️ 判定复用了 `WandUpgradeEvents#magicDamageBonus`（本轮的**唯一实现**抽取，见下）。
- **`tame = true`：召唤出来的仆从是玩家自己的**。
  `SummonRitual(recipe, tame, noVariant)` 的 `tame` 最终走到
  `Ritual.prepareLivingEntityForSpawn(..., tame)` → `MobUtil.summonTame(entity, player)`
  （字节码实证：偏移 0 即 `iload 6; ifeq` 跳 `MobUtil.summonTame`；本类构造出的字节码为
  `iconst_1, iconst_0` ⇒ tame=true、noVariant=false）⇒ 仆从**认主**、跟随、接受聚晶指令。
  这与刷怪蛋"直接放 = 野生"是**两条互不影响的获取路径**。
- **召唤用杖要留下来**：法杖会被 `finish` 消耗，所以必须在 `super.finish` **之前**复制
  （与 `TunerSummonRitual` 同一条实证结论），然后交给仆从的 `SummonWand` NBT ——
  它同时是 **(3) 的强度依据** 和 **(4) 的死亡掉落物**。
  ⚠️ **主手仍然是 `goetytuner:tuner_wand`**（AI 施法载体），召唤用杖**不装到手上**。
- ⚠️ **Goety 还有一道全局闸门**：`SummonRitual.isValid` = `super.isValid(...) &&
  RitualRequirements.canSummon(level, player, entityToSummon)`，
  而 `canSummon` 会数**玩家已有的 `IOwned` 仆从**并与 Goety 自己的上限比较 ⇒
  仆从满员时仪式不成立（这是 Goety 对**所有**仆从召唤的统一规则，不是本模组的 bug）。

**（3）【新功能】法杖加成越高，仆从越强（`combat/ServantWandBlessing`）**

用户规格：
> ① 根据调律·魔法伤害加成的多少，**持续性地**给予自身 n 级**强健**效果（10 时为强健 1，每 20 加一级）；
> ② 加成高于 20 时给**生命恢复 1**，高于 60 时改为**生命恢复 2**，高于 80 时改为**生命恢复 2 + 抗性提升 1**，
> 高于 100 时改为**生命恢复 2 + 抗性提升 2**（到达上限）。

- **单位口径**：法杖 NBT 是**小数**（`0.10` = +10%），用户说的是**百分数** ⇒
  本类统一按 `pct = bonus × 100` 判定，阈值常量写成百分数，**与 tooltip 显示的数字一致**。
- **⚠️ 判定来源：用「调律·巫法加成」，不是「调律·魔法伤害加成」**（用户 0.0.20 修正）。
  用户实测反馈："我发现按照调律·魔法伤害加成的计数规则**有点太容易触发**了，
  改为按照调律·巫法加成来判定，**数值不变**。"
  <p>症结是**两个加成的单次默认值差 4 倍**（`[wand_upgrade]` 段）：

  | 加成 | 每次击败 Boss 的默认增量 | 打一次就到哪 |
  |---|---|---|
  | 调律:魔法伤害加成 | **+40%** | 强健 Ⅱ + 生命恢复 Ⅰ（几乎立刻满配） |
  | 调律:巫法加成（**现在用它**） | **+10%** | 强健 Ⅰ（真正需要反复击杀） |

  ⇒ **阈值（10 / 20 / 60 / 80 / 100）一个都没改**，只是换了读哪个 NBT 键
  （`WandUpgradeEvents#witchcraftBonus`，与 `magicDamageBonus` 一样公开）。
  按巫法加成 +10%/次 的爬升曲线：

  | 击杀次数 | 巫法加成 | 强健 | 生命恢复 | 抗性提升 |
  |---|---|---|---|---|
  | 0 | +0% | — | — | — |
  | 1 | +10% | Ⅰ | — | — |
  | 3 | +30% | Ⅱ | Ⅰ | — |
  | 7 | +70% | Ⅳ | Ⅱ | — |
  | 9 | +90% | Ⅴ | Ⅱ | Ⅰ |
  | 11 | +110% | Ⅵ+（继续升） | Ⅱ | Ⅱ（封顶） |

  （多次击败的叠加由 `wand_upgrade.wandBonusStack` 决定，默认 true = 加法叠加。）
  ⚠️ **注意与"仪式激活判定"的分工**：仪式那边看的是**两种加成任一 > 0**
  （见 (2)，因为它回答的是"这杖调过律没有"这种定性问题），
  而**强度档位只看巫法加成**。极端配置下（只给魔法伤害加成、巫法加成为 0）
  能召唤成功但拿不到增益 —— **这是分工，不是 bug**。
- **"持续性地给予"的做法**：**低频自愈式刷新** ——
  `aiStep` 每 tick 调用一次 `tick(...)`，内部按 `tickCount % 20 == 0` 降频（1 秒），
  每次挂 `60 tick`（3 秒）时长。`apply` 里**若已是目标档位且剩余时长 > 半个周期则直接跳过**
  ⇒ 平时每秒最多 3 次 `addEffect`，且数值不变时不产生无谓的网络同步。
  （也因此玩家用牛奶 / 净化类效果清掉后，最多 1 秒就自动回来。）
- **调用位置**：`TunerServant#aiStep` 里**放在所有 early-return 之前**（紧跟 `ServerLevel` 判定）——
  仆从施法中（`activeChannel != null` 那条 `return`）也必须照常维持增益。
- **药水机制与本体独立（用户要求）**：用户明确"仆从的药水机制和调律师的独立" ⇒
  本类**只服务仆从**，不碰 `TunerBoss` 的任何效果逻辑：不继承二阶段增益免疫
  （`phase2_buffs.phase2EffectImmunity`）、不继承低谷的 SAPPED / DARKNESS、不受 `[boss]` 段任何键影响；
  唯一开关是本模组自己的 `servant.wandBlessingEnabled`。
  仆从自己的免疫规则仍然只有它自己那一条（拒绝 Goety 的召唤冷却 `SUMMON_DOWN`，
  见 `TunerServant#canBeAffected`）；增益走 `LivingEntity#addEffect`，天然要过那道判定，
  而它只拒绝 SUMMON_DOWN ⇒ 不会被自己挡住。
- ⚠️ **只有仪式召唤的仆从吃得到**：刷怪蛋 / `/summon` 出来的仆从没有召唤用杖 ⇒ 不加任何增益
  （这是有意的：用户的设计就是"用带调律加成的法杖召唤出的仆从才强"）。
- ⚠️ **实测口径 / 一个必须知道的坑（见 §七.14 已知边界）**：
  **`goety:buff`（强健）实际只修改 `ATTACK_DAMAGE` 属性**
  —— Goety 语言文件里它的说明是"每级增加 1 点的近战攻击伤害"，
  反编译 `GoetyEffects` 的注册 lambda 也实证是
  `GoetyBaseEffect(...).addAttributeModifier(Attributes.ATTACK_DAMAGE, uuid, 1.0, ADDITION)`。
  **而本模组的仆从刻意没有近战手段**（不装 `MeleeAttackGoal`，"它是指挥家不是战士"）⇒
  **强健在当前实现下对它的实际战斗力几乎没有影响**（只影响 HUD 显示与属性面板）。
  这一条**忠实照搬了用户规格**，但**效果与"越强"的直觉不符** ——
  已在交付说明里明确告知，并把可选修法写在 §七.14（把强健等级同时换算成法术强度）。

**（4）【新功能】仆从死亡掉落召唤用杖**

用户规格："当调律师仆从死亡时，掉落召唤用的法杖。（并不会有加成，**原样返回**）"

- **已与用户确认语义**：掉的是**召唤时那把杖的完整副本**（附魔 / 聚晶 / **它原有的调律加成**全部保留），
  **不额外加任何东西**。括号里的"并不会有加成"指的是"**不会再被加成一次**"，
  而不是"把原有的加成剥掉"。
- 与 Boss 的掉落**刻意对照**（同一个事件处理器里的两个分支，见
  `WandUpgradeEvents#onLivingDrops`）：

  | | Boss | 仆从 |
  |---|---|---|
  | 掉什么 | 仪式时的**原始法杖快照** | **召唤用杖快照** |
  | 加成 | **叠加** `applyWandUpgrade`（击杀奖励） | **不加不减** |
  | 无快照时（刷怪蛋/指令） | 保持原行为（不掉杖） | 保持原行为（不掉杖） |

- 同样先 `removeIf(item instanceof IWand)` 清掉主手那把 `tuner_wand`
  （它是配发给 AI 的施法载体、`setDropChance(MAINHAND, 0)` 本来就掉不出来；
  这里再兜一道，防止将来有人改掉落概率时白送一把杖）。
- 日志：`[Tuner] Tuner servant died, returned its summoning wand: <uuid> (<name>)`。

**（4b）【0.0.20 追加·用户要求】「强健」等级 → 法术强度（`combat/BuffSpellPower`）**

用户规格："将强健等级换算成法术强度，**且只对调律师生效**
（即调律师本人根据自身的强健等级增加法术伤害）。"
作用域经确认 = **本模组「调律师一族」**（`TunerBoss` + `TunerServant`），
**各自按自身的强健等级**加成；**不是**全局规则（别的模组的生物 / 玩家不会被强化）。

- **⚠️⚠️ 本轮最重要的一次实证：`goety:spell_potency` 上"百分比"恒等于 0。**
  三条证据链：
  1. 属性注册：`new RangedAttribute("attribute.name.goety.spell_potency", 0.0D, 0.0D, 2048.0D)`
     ⇒ **基础值是 0.0**（不是 1.0）；
  2. 唯一读取入口 `ModAttributes.getPotency(LivingEntity)` =
     `(int) livingEntity.getAttributeValue(SPELL_POTENCY)` ⇒ **返回 int**；
  3. 法术把它当**平铺伤害点数**用（`SpellStat.getPotency()`；Goety 的伤害公式普遍是
     "配置基础伤害 **+** potency"，不是乘算）。
  ⇒ 因为 Minecraft 的 `MULTIPLY_TOTAL` 是 `total *= 1 + amount`，
     **基础值 0.0 乘任何数还是 0.0** ⇒ **任何百分比 modifier 在本属性上都是死代码**。
  这也决定了本类的实现：**必须用 `ADDITION`，且数值必须是整数级别**
  （`0.1` 会被 `(int)` 截成 0 ⇒ 白挂）。
  ⚠️ **顺带查出两处早已存在的同类死代码**（见已知边界 17，本轮只报告未改）：
  玩家的「调律:巫法加成」与 Boss 二阶段的「力量等级 × 0.1」**都是 `MULTIPLY_TOTAL` ⇒ 都没生效**。
- **换算比例**：新增配置 **`[casting] buffSpellPowerPerLevel`**（**Int**，**默认 1**，范围 0~100）。
  ⚠️ 之所以是 **Int 而不是 Double**：法强属性是 int 语义、小数会被截断成 0；
  默认 **1** ⇒ 强健 Ⅰ = 每发 **+1 点**、Ⅴ = **+5 点**
  （与 Goety 原版「强健」`ATTACK_DAMAGE +1/级` 的节奏一致）。
  **0 = 关闭**（并会主动移除已挂的 modifier）。
- **"只对调律师生效"怎么保证**：`BuffSpellPower.tick(...)` **只被这两个实体调用**
  （`TunerBoss#tick` 的服务端分支、`TunerServant#aiStep` 的服务端分支）——
  本模组没有任何全局事件 / 属性广播，别的实体拿不到这个 modifier。
- **刷新方式**（与 `ServantWandBlessing` 同款）：每 tick 调用、内部按 **1 秒**降频、
  **数值没变就一个字节都不写**；强健消失（被净化 / 到期）⇒ 目标值归 0 ⇒ **主动 `removeModifier`**。
  仆从身上两个调用的**顺序有意固定**：先 `ServantWandBlessing.tick`（把强健挂上）、
  再 `BuffSpellPower.tick`（同一次 `aiStep` 就能读到）——且都在**所有 early-return 之前**。
- **实体到底有没有这个属性？** 有。Goety 的 `ModAttributes#modifyEntityAttributes`
  （订阅 `EntityAttributeModificationEvent`）把 Goety 的**全部属性**加给**全部实体类型**
  （字节码：`event.getTypes().forEach(t -> ATTRIBUTES.getEntries().forEach(a -> event.add(t, a)))`）
  ⇒ 属性一定存在（本类仍保留 null 兜底）。
- ⚠️ **本体的现状**：调律师**今天并不自施强健**（二阶段自施的是**原版力量**
  `MobEffects.DAMAGE_BOOST` —— 第 33 轮特意把 Goety 的强健换掉的，理由"加成全落在近战属性上"）
  ⇒ 对本体的效果目前是**每秒一次的幂等短路**（"确保没有该 modifier"）；
  将来若给本体加强健，它**自动生效**、无需再改代码。

**（4c）【0.0.20 追加·用户要求】让两处「百分比法术加成」真正生效（`combat/SpellDamageBonus`）**

用户要求："你修一下这两个问题；**保证实际生效和文字描述相同**即可。"
⇒ 目标：文字写 `调律:巫法加成 +10%` 就真的 +10% 法术伤害；
配置写"等级2=20%、等级5=50%"就真的 +20%/+50%。

- **新增 `combat/SpellDamageBonus` = 「法术伤害百分比加成」的唯一实现**（含 `LivingDamageEvent` 处理器）。
  职责划分因此变得干净：
  | 通道 | 负责 | 实现 |
  |---|---|---|
  | **百分比**法术伤害 | 巫法加成 / 魔法伤害加成 / Boss 二阶段力量 | `SpellDamageBonus`（伤害事件 ×(1+加成)） |
  | **点数**法术强度 | 强健等级 → 法术强度 | `BuffSpellPower`（`SPELL_POTENCY` + `ADDITION`） |
- **⚠️ 同时修掉的第二个问题：判定覆盖面本来就是错的。**
  原判定是 `forge:is_magic` 标签 + 原版 `magic`/`indirectMagic`，
  而该标签**只标了 Goety 39 个伤害类型里的 8 个**
  （phobia / ice_bouquet / acid / spike / magic_bolt / wind_blast / soul_leech / life_leech）
  ⇒ **Boss 的主力法术根本不在里面**（腐化光束用的是 `goety:magic_fire`、
  火球是 `goety:magic_fireball`、还有 `hellfire` / `shock` / `direct_freeze` / `frost_breath` …
  全是 `goety:` 命名空间）。
  现在判定 = **三者并集**：① `forge:is_magic` 标签；② 原版 `magic`/`indirectMagic`；
  ③ **`goety` 命名空间下的任意伤害类型**（用命名空间而不是法术清单：数据驱动、不写死、
  与 Goety 的 39 个 `data/goety/damage_type/*.json` 天然对齐）。
  实现用 `DamageSource#typeHolder().unwrapKey()` 取 id（1.20.1 的 `DamageSource` 没有
  `isMagic()` —— 那是 1.20.2+）。⚠️ 代价：**其它模组**的法术若既不在标签里、也不是原版
  `magic`/`indirectMagic`，就不会被本加成覆盖（**宁可漏、不可错伤近战**）。
- **删掉的死代码**（这是本轮的"修"的主体）：
  - `WandUpgradeEvents`：删除 `refreshWitchcraftModifier` + `POTENCY_MODIFIER_UUID/NAME`
    + **两个监听器**（`LivingEquipmentChangeEvent`、每 32 tick 兜底的 `TickEvent.PlayerTickEvent`）
    + 原 `onLivingDamage` + `isMagicDamage`/`FORGE_IS_MAGIC`；
    **顺带省掉一个常驻的玩家 tick 订阅**（原来是每 32 tick 一次幂等检查）。
    本类现在只剩：法杖 NBT 读写（`witchcraftBonus`/`magicDamageBonus`）、死亡掉落、tooltip。
  - `TunerBoss`：删除 `PHASE2_POTENCY_UUID` 与 `tickPhase2Buffs` 里的 modifier 块，
    改为新增 **`phase2SpellDamageBonus()`** —— 直接读**自己身上 `DAMAGE_BOOST` 效果的等级**
    （`(amplifier+1) × 0.1`）。好处：buff 到期自动归零、玩家用别的途径给 Boss 叠/削力量时加成会跟着变，
    也就是"**根据自身的力量等级**"字面兑现。
- **加成怎么算**（`SpellDamageBonus#bonusOf`）：
  - **玩家**：主手 + 副手两把杖的「巫法加成」与「魔法伤害加成」**四项相加**
    ⇒ 总倍率 `1 + 巫法 + 魔法`（一把满配杖 = **×1.50**；相加而非相乘，避免同一把杖的两条加成互相放大）。
  - **调律师**：`phase2SpellDamageBonus()`（+20%/+50%）+（若 `wandBonusAppliesToBoss=true`）手持杖的魔法伤害加成。
- **⚠️ 顺带修掉一个隐患：不再放大自伤。**
  `src.getEntity() == event.getEntity()`（攻击者就是受害者）时直接放行 ——
  否则「索命聚晶」对施法者的反噬（目标当前生命值的 125%）会被自己的加成再放大，
  变成"加成越高、自杀越快"，那不是任何文字承诺过的行为。（修复前的 `magic_damage_bonus` 缺这条保护。）
- **数字口径（改完之后）**：
  | 谁 | 加成 | 实际倍率 |
  |---|---|---|
  | 玩家持满配升级杖 | 巫法 +10% ＋ 魔法 +40% | **×1.50** |
  | 调律师二阶段（档位<10） | 力量 2 级 ⇒ +20% | **×1.20** |
  | 调律师二阶段（档位≥10） | 力量 5 级 ⇒ +50% | **×1.50** |
  ⚠️ **平衡影响（必须告知）**：Boss 二阶段的法术伤害**从"实际 +0%"变成 +20%/+50%** ——
  这是第 37 轮就写在代码注释里的原意，只是一直没生效；玩家侧的「巫法加成」同理从 0 变成真 +10%。
- **未进游戏实测**（与本轮其它内容一样）：`javap` 已确认 `SpellDamageBonus` 用
  `typeHolder().unwrapKey()`、`WandUpgradeEvents`/`TunerBoss` 里那批死方法**确实已从字节码消失**、
  全 jar **只有一个** `LivingDamageEvent` 处理器、`phase2SpellDamageBonus` 存在。

**（5）【新功能】聚晶包 / 多晶大袋的批量指令**

用户规格："既然聚晶可以左/右键设置不释放/优先释放；那么玩家也可以拿着**聚晶包/多晶大袋**等进行批量设置。"

- **判定**：手持物品 `instanceof FocusBag`（Goety 的 `FocusPack extends FocusBag`，
  所以**一个 instanceof 同时覆盖聚晶包与多晶大袋**）。
- **读取内容物**：用**通用**的 `ForgeCapabilities.ITEM_HANDLER` capability，
  而不是 Goety 的内部类 ——
  字节码实证两者都在 `initCapabilities` 里挂了 `FocusBagItemCapability`
  （**聚晶包 11 格**（`bipush 11`）、**多晶大袋 21 格**（`bipush 21`））。
  袋内**非聚晶物品直接跳过**（药剂、杂物不影响批量指令），**同 id 去重**。
- **语义（已与用户确认）**：与单个聚晶**完全一致**的"再按一次就取消"，只是作用域是整个袋子 ——
  **袋内全部已是该状态 ⇒ 整体清除；否则 ⇒ 全部设为该状态**。
- **不吞掉的情况**：袋里**一个聚晶都没有**时直接 `return`（不 cancel），
  让原版行为继续；被拒绝（别人家的仆从）时也不 cancel。
- **互斥语义只留一处实现**：单个切换与批量都走 `TunerServant` 的两个私有方法
  `setDisabledExclusive` / `setPriorityExclusive`
  （左键顺手清"优先"、右键顺手清"不释放"）——
  避免"批量一套规则、单点另一套"。

**（5b）【追加·用户实测反馈】「强健」等级其实是对的 —— 是原版不显示**

用户反馈："似乎并没有正确地给予强健等级，+1000% 巫法加成的法杖对应的仆从，也只显示「强健」。"

- **结论：等级算对了，是原版把数字藏起来了。** 1.20.1 客户端
  `EffectRenderingInventoryScreen#getEffectName` 的字节码是：
  ```java
  MutableComponent name = instance.getEffect().getDisplayName().copy();
  if (instance.getAmplifier() >= 1 && instance.getAmplifier() <= 9) {      // ← 关键
      name.append(SPACE).append(Component.translatable("enchantment.level." + (amplifier + 1)));
  }
  return name;
  ```
  即 **只有 amplifier 1~9（等级 II~X）才附罗马数字**：
  - 等级 1（amplifier 0）⇒ 只有「强健」；
  - **等级 ≥ 11（amplifier ≥ 10）⇒ 也只有「强健」**（连数字都不加）。
  另核实原版语言文件里 `enchantment.level.*` **只有 1~10 十个键**。
  ⇒ +1000% 巫法按公式 = `1 + floor((1000−10)/20)` = **50 级**（amplifier 49 落在"隐藏"区间）
  ⇒ **用户看到"只有强健"恰好说明等级很高，而不是没生效。**
- **修法（让等级可见）**：新增配置 **`[servant] buffMaxLevel`（Int，默认 10）**
  ⇒ 实际挂的等级 = `min(公式值, buffMaxLevel)`，默认钉在 **10**，这样**任何情况下都能看到 `强健 X`**。
  ⚠️ 这是**显示上限**而不是平衡上限：调大它（1~255）会按公式给更高真实等级，
  但**超过 10 就又看不见数字了**（原版限制，改不了）。
- **诊断日志（让等级可查证）**：
  - 等级**变化**时打一条 INFO：
    `[Tuner] Servant <uuid> blessing from wand '<名字>': bonus <X>% -> Buff <n> (raw <m>, capped by servant.buffMaxLevel=<cap>) / Regen <r> / Resistance <s>`
    —— 只有"等级真的变了"才打（纯粹的时长续期不算变化，否则每 2 秒一条）。
  - **仪式召唤时**额外打一条：
    `[Tuner] Servant summon: captured wand '<名字>' (witchcraft=+X%, magic=+Y%) -> <uuid>`
    —— 日后出现"等级不对"的反馈时，这一行能立刻区分
    **"杖不对 / 快照是空的"** 与 **"等级公式不对"**（本轮用户报告的最大不确定点）。

**（5c）【追加·用户要求】加强仆从的限伤机制**

用户："加强仆从的限伤机制。"

- **改动前的实际状况**（本轮才发现是个漏洞）：`TunerServant#actuallyHurt` 读的是
  **`[boss]` 段的两个键** —— `maxHitDamagePercent`（默认 0.25）与
  `maxDamagePerSecond`（**默认 0 = 关闭**）⇒ 仆从只有"单次 ≤ 25% 最大生命"（216 血 = 54 点）
  这一道限，**没有任何每秒上限** ⇒ 高爆发几下就能打死。
- **改动**：`[servant]` 段新增**仆从自己的**两个键（与本体**各自独立**），并给出更狠的默认值：

  | 键 | 默认 | 含义（216 血时） | 本体对照 |
  |---|---|---|---|
  | `servant.maxHitDamagePercent` | **0.15** | 单次最多掉 **32** 点 | `[boss]` 是 0.25 = 54 点 |
  | `servant.maxDamagePerSecond` | **0.50** | 每秒最多掉 **108** 点 ⇒ 至少 2 秒才能打死 | `[boss]` 默认 0 = 关闭 |

  `TunerServant#actuallyHurt` 改为读这两个键；**0 = 关闭**对应项。
  ⚠️ 窗口仍是**滑动 1 秒**；预算耗尽时那一次伤害被**整段吸收**（`DamageThrottle` 的既有语义，未改）。
  ⚠️ `/kill` 之类的 `BYPASSES_INVULNERABILITY` 伤害**照旧绕过限伤**（有意保留：管理员应当能杀）。

**（5d）【0.0.20 追加·真根因】「左键禁用/取消禁用」为什么一直"不起效" —— 事件其实是每 tick 都在触发**

用户反馈（第三次）："聚晶/聚晶包的左键禁用/取消禁用功能**又**不行了。你检查一下，不要堆叠代码。"

- **真根因（这次有完整字节码证据，前两轮的"野生仆从被拒 / 潜行劫持"都只是附带问题、不是主因）**：
  1. 原版客户端**按住左键期间每 tick 都会调一次** `MultiPlayerGameMode.attack`
     （字节码：偏移 13 `createAttackPacket` 发交互包、偏移 31 `Player.attack`）；
  2. 原版的伤害节流（`attackStrengthScale > 0.9`）是在 `Player.attack` **内部之后**才判的，
     **拦不住发包** ⇒ 服务器每 tick 收到一个攻击包；
  3. 而我们的处理器挂在 `Player.attack` 的**第一条指令**上
     （`ForgeHooks.onPlayerAttackTarget`，偏移 0；服务端路径
     `ServerGamePacketListenerImpl$1.onAttack()` → `ServerPlayer.attack` 偏移 83，均已实证）
     ⇒ **按住不放时服务器每 tick 执行一次指令处理器** ⇒ 状态以 **20Hz 来回翻转**，
     松手时落在哪一侧全看时机 ⇒ 玩家看到的就是"点了没用"。
  4. 右键同理（`rightClickDelayTimer` 到点就重发 ⇒ 按住时每 4 tick 一次），
     只是频率低一些；同样需要去抖。
  - ⚠️ 顺带解释了一个对照：**Goety 自己的左键聚晶（call/command/order/troop）之所以没这个问题，
    是因为它们的左键动作是"幂等"的**（保存/选中，重复执行无害），
     而我们的左键是**切换（toggle）**，重复执行必然来回翻。
- **修法（上升沿去抖，不加层）**：`TunerServant#acceptCommandClick(now, leftClick)` ——
  左右键**各自独立**计时；**只在"新一轮按压的第一个包"上返回 true**，
  且**无论接受与否都刷新时间戳** ⇒
  - 按住不放：第 1 个包生效，之后每包间隔恒为 1 tick ⇒ **一次按压只翻转一次**；
  - 松手后再点（相隔 > **5 tick** = 0.25s）⇒ 正常生效；
  - 手抖双击被算作同一次按压（避免误翻两下）。
  - ⚠️ 时间戳初值用 `-1000` 而**不是** `Long.MIN_VALUE`：后者参与减法会溢出成负数，
    会把"史上第一条指令"吞掉。
  - 去抖只在"已经确定要下指令"之后才消耗（先过 `canCommand`），
    所以点到别人家的仆从不会白白吃掉一次按压。
- **验收**：单击一次 ⇒ 正好翻转一次（提示只出现一次）；**按住不放** ⇒ 也只翻转一次、
  松手后状态保持不变；连点两次（间隔 > 0.25s）⇒ 翻回去。

**（5e）【0.0.20 追加·新功能】下界之星右键：只释放优先聚晶**

用户："用下界之星右键仆从，可以让它只释放优先聚晶（若没有优先聚晶仍可这么设置，但无效，
按照正常的来）再右键取消。"

- 语义（严格按用户描述）：
  | 开关 | 优先聚晶表 | 行为 |
  |---|---|---|
  | 开 | 非空 | **只放优先聚晶**（复用原有的插队逻辑 `tryPriorityCast`） |
  | 开 | 空 | **开关无效，照常按轮换序列抽签**（用户明确要求） |
  | 开 | 非空但此刻都不可用（冷却中/被禁放） | **不放**（「只放」的字面含义） |
  | 关 | —— | 原有行为（优先插队 + 常规轮换） |
- 实现：`TunerServant#priorityOnly` 字段 + `togglePriorityOnly()` + `isPriorityOnly()` + NBT `PriorityOnly`
  （落盘，否则重进存档悄悄失效）；`beginNextCast` 里插队失败后加一条
  `if (priorityOnly && !pools.priorityFoci().isEmpty()) return false;`
  —— **只设过优先聚晶时才拦常规轮换**，正是"没有优先聚晶就按正常的来"。
- 交互：`TunerServantInteractions` 的右键分支里**最先**判 `Items.NETHER_STAR`
  （在聚晶包/单个聚晶之前），同样走右键去抖；三种提示文案：
  `只释放优先聚晶` / `只释放优先聚晶（尚未设过优先聚晶，暂时照常释放）` / `已取消「只释放优先聚晶」`。
- lang **43 → 46 键**（+3）。

**（6）本轮做的"唯一实现"抽取**

- `WandUpgradeEvents#magicBonusOf`（`private`）→ **`public static magicDamageBonus(ItemStack)`**：
  它是"法杖的调律·魔法伤害加成是多少"的**唯一实现**，本轮出现**三个消费方** ——
  ① 本类的魔法伤害加成（原有）；② `TunerServantSummonRitual#isTunedWand`
  （判定仪式中心那把杖算不算"有调律加成"）；③ `ServantWandBlessing`
  （换算成百分数决定仆从增益档位）。**不允许任何一处再抄一遍 NBT 读法。**

**（7）本轮验证做到什么程度（同样没有进游戏实测）**

- `gradlew build` **成功**（**1m3s**，只有项目既有基准噪声：3 条 FML deprecated 警告 +
  1 条 `TunerBoss` 过时 API 注记，**无新增警告**）。
- **jar 条目比对**：**117 → 120**（新增 2 个 class + 1 个配方 json，**无删除**）。
- **`javap` 核对**（本轮新增/改动的部分）：
  - `TunerServant` 新增 `applyFocusDisabledBatch` / `applyFocusPriorityBatch`
    （参数 `java.util.Collection<java.lang.String>`）、`getSummonWand` / `setSummonWand`，
    以及三个私有方法 `setDisabledExclusive` / `setPriorityExclusive` / `allDisabled` / `allPriority`；
  - `TunerServant.aiStep` 字节码里确有
    `invokestatic ServantWandBlessing.tick:(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V`；
  - `ServantWandBlessing` 的 `tick` 里确有 `GoetyEffects.BUFF` 与
    `MobEffects.f_19605_` / `f_19606_`（在**未 reobf 的 dev class** 上复核为
    `MobEffects.REGENERATION` / `MobEffects.DAMAGE_RESISTANCE` ——
    ⚠️ 只数 SRG 名容易认错效果，必须用 dev class 或映射表比对）；
  - `TunerServantSummonRitual` 构造函数字节码为 `iconst_1, iconst_0` ⇒
    `SummonRitual.<init>(recipe, true, false)`（**tame=true**）；
  - `TunerServantInteractions` 的 `onAttackEntity` / `onEntityInteract` 里确有
    `instanceof FocusBag`（两处）、`ForgeCapabilities.ITEM_HANDLER` 与
    `ItemStack#getCapability`，并新增了 `message` / `focusIdsInBag` 两个私有方法；
  - `WandUpgradeEvents` 新增 `public static double magicDamageBonus(ItemStack)` 与
    `private static void dropServantSummonWand(LivingDropsEvent, TunerServant)`。
- **配置 / 资源核对**：`define` 调用 **72** 处（71 → 72）、section **11** 个（未变，
  `[servant]` 段 **3 → 7** 项）、两份 lang **各 43 键**且 JSON 合法、
  jar 内 `mods.toml` 版本 **0.0.20**、配方 JSON 合法且
  **8 个基座物品 = 紫水晶碎片 ×4 / 红石 / 钻石 / 金锭 / 青金石**、
  `soulCost=10`、`duration=10`、`craftType=magic`、`ritual_type=goetytuner:tuner_servant_summon`。
- **部署核对**：构建产物与部署 jar **逐字节相同**
  （1,799,637 B / md5 `05DCDEE8E6199DF0E3F86612E90345C9` / 122 条目）。
  同时**删除了旧的 `goetytuner-0.0.19.jar`** —— 同名版本换了版本号，
  两个 jar 并存会被 Forge 判为**重复 mod** 而启动失败。
- ⚠️ **四条内容的运行时表现全部未实测** —— 见 §七.13。

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
  `0.1.0→0.2.0→…→0.7.1→0.7.2→0.0.0→0.0.1→0.0.2→0.0.3→0.0.4→0.0.5→0.0.6→0.0.7→0.0.8→0.0.9→0.0.10→0.0.11→0.0.12→0.0.13→0.0.14→0.0.15→0.0.16→0.0.17`），否则游戏 mods 里
  替换失败用户以为"没变化"；且**旧配置文件锁旧值**，大改默认值需删 toml 重新生成。
  ⚠ 版本号被重置为 0.0.x 后，对外发布排序会小于 0.7.2，后续建议跳到 `1.0.0`。

---

## 五、版本演进时间线（第 1~55 轮浓缩）

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

| v0.0.14 | 52 | ① **配置界面新增「聚晶黑名单」输入框**（`client/TunerConfigScreen` + `focus/FocusPoolManager`）：`focus.blacklist` 是 common 配置，而本模组的 `TunerConfigScreen` **顶替了 Forge 默认的 toml 编辑器**，该键此前在游戏内**完全没有入口**、只能手改文件；现补一个单行 `EditBox`（预填当前值，提示「namespace:path，英文逗号分隔；留空=不屏蔽」），**点「完成」关屏时保存**（最自然的"改完了"信号）、点「开始评分」时也顺带保存；保存 = `FOCUS_BLACKLIST.set(v)` + `.save()` + `FocusPoolManager.refreshBlacklist()` ⇒ **改完立即生效**（`getBlacklist()` 以 raw 字符串为缓存键，值一变缓存自动失效 ⇒ 下一次抽取就过滤）。**新增 `FocusPoolManager.refreshBlacklist()`**：`initIfNeeded()` 扫描时**直接 `continue` 跳过**黑名单聚晶（它们不进 `ALL_ENTRIES`），故"**新增**拉黑"能靠实时过滤立刻生效、但"**取消**拉黑"必须重扫；而重扫若 `new` 出全新 `FocusEntry`，会让实体侧**按对象身份**记录的状态失效（`TunerBoss.activeVisualCasts` 身份集合、`CastChannel.current`）⇒ 出现「立方体高亮卡住 / 施法收尾回调对不上」这类隐蔽问题；故新方法按 `namespace:path` 建索引**复用已有 `FocusEntry` 对象**，只为"这次才被解禁"的聚晶新建（它们此前不可能在施法中，故安全），再重建 `STATIC_POOLS` 并重新 `applyTo` 分类。新增 4 个 lang 键（zh_cn / en_us 各 4 个）。② **二阶段免疫「回复 / 减伤」类药水效果**（`entity/TunerBoss` + `config/TunerCommonConfig`）：扩展现有的 `canBeAffected` 覆写（它同时是 `addEffect` 与 `forceAddEffect` 的**第一道**判定 ⇒ 返回 false 就是**真正的免疫**，而不是"加完再清"），免疫名单 = **抗性提升** `DAMAGE_RESISTANCE` / **伤害吸收** `ABSORPTION` / **生命恢复** `REGENERATION` / **瞬间治疗** `HEAL` / **生命提升** `HEALTH_BOOST`，**仅当 `music.isPhase2()`** 时生效；**刻意只列这 5 项而不是"所有 beneficial"**——Boss 自己的二阶段增益（力量 `DAMAGE_BOOST` 与重振 `RALLYING`）也是 beneficial，一刀切会把它们一起禁掉，名单集中在私有 `isPhase2Immune(...)`、以后加一行即可；新增配置 `phase2_buffs.phase2EffectImmunity`（Boolean，默认 **true**），false = 回到旧行为 ⇒ 配置 **62 → 63 项**（`phase2_buffs` 段 **3 → 4**，`boss` 段仍 **19**）。**只改代码，不动任何贴图/资源**；`.java` 仍 45 个、jar 仍 **102** 条目 |
| v0.0.15 | 53 | 本体贴图精修（头部逐像素不变，身体改 1247 像素；零代码改动） |
| v0.0.16 | 54 | 「逐渐学习」：动态评分权重系数随该个体施法次数从 0.15 线性爬升到 1.0（默认 60 次） |
| v0.0.17 | 55 | 索命聚晶后门（goety:death 可无视锁血处决）+ 修客户端死亡动画残留（血量回正后自愈复位） |

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
| 0.0.19 | `goetytuner-0.0.19.jar` | 1,785,988 B | `77DAAFB0A5B77907C3D7A22461731FB8` | 已删除（被 0.0.20 取代；**不能与 0.0.20 并存**，同 mod id 会让 Forge 报重复 mod） |
| 0.0.20 | `goetytuner-0.0.20.jar` | 1,799,637 B | `05DCDEE8E6199DF0E3F86612E90345C9` | `versions\测试\mods\`（该目录只保留这一个 goetytuner jar） |

> 本轮（0.0.19）jar 内共 **117 条目**（0.0.18 为 112）：新增 5 条 = 4 个新 class
> （`DamageThrottle` / `TunerDamageRules` / `TunerWand` / `TunerServantSpawnEggItem`）
> + `assets/goetytuner/models/item/tuner_wand.json`，**无删除**；
> 构建在主副本就地 `gradlew build` 成功（约 1 分钟，仍只有原有 3 条 Forge 弃用警告 + 1 条 `TunerBoss` 注记）。
> ⚠️ **配置迁移提示**：本轮**删除**了 `[servant]` 段的 `health` 键（改为跟随本体），
> 老 toml 里那一行会变成孤儿条目 —— **Forge 会自行处理，无需手工迁移**
> （与 0.0.18 改 `focus.blacklist` **值**那种"必须手工迁移"是两回事）。
> 新增键 `casting.channelMaxTicks` 是**新增键 ⇒ Forge 自动补齐**。
> （上一轮 0.0.18：`goetytuner-0.0.18.jar`，1,778,838 B / md5 `5946D71FA425B8F84E2F7E1CF4B5A8E3`，jar 内 112 条目。）

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
> **0.0.15 又一条实证**：同一份只改了贴图的源码，本轮**另一位 agent 自建为 1,755,455 B、主副本重建为 1,755,456 B**——源码相同，差异同样只在 `MANIFEST.MF` 的时间戳。
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
3. 哪些聚晶需永久写入配置黑名单——以运行日志 ERROR 行为准。**0.0.18 起代码默认值有两条**：
   `goetytwilight:destruction_focus`（毁坏链锤对非玩家 owner 必崩）与
   `goetytuner:tuner_ripple_focus`（本模组新增的玩家用聚晶，用户要求拉黑）；
   **0.0.13 起游玩实例的 toml 还额外拉黑了 `goety:killing_focus`**（索命聚晶，对施法者反噬 125%）
   —— 注意这几条中"实例 toml 里多出来的部分"是**配置侧**改动、**不在 jar 内**，
   换实例 / 新玩家**不会**自动生效；而 `focus.blacklist` 的**默认值本身**在 0.0.18 变过，
   Forge 不会回填老 toml ⇒ 老存档需手工迁移（见 §3.18(3)）。
   ✅ **可访问性一项已完成（0.0.14）**：`focus.blacklist` **已有游戏内入口** —— 配置界面新增的单行输入框，
   点「完成」关屏或点「开始评分」即 `set + save + FocusPoolManager.refreshBlacklist()`、**改完立即生效**
   （**新增**拉黑靠实时过滤、**取消**拉黑靠重扫，两向都即时）。⚠️ 但 `TunerConfigScreen` **依旧顶替**了 Forge 的
   toml 编辑器，**除黑名单外的其余 common 配置项仍然只能手改 `goetytuner-common.toml`**；
   且连他人服务器时改的是**本地** toml、服务器侧不受影响。
   另：`RUNTIME_BLACKLIST` 与配置黑名单是**并集**，配置**无法解禁**一个已被运行期拉黑的聚晶。详见 §3.14(7) 与 §3.15(1)。
   ✅ **0.0.17 补充（索命这一条的性质变了）**：索命聚晶的「破状态」已从**根上**处理——
   ① **后门已开**：`boss.deathCurseExecution`（默认 `true`，见 §3.17(1)(5)）让 `goety:death` 伤害
   **无视剩余锁血档位真正处决** Boss（跳过宽限期免疫 / 致死截断 / 限伤·限DPS / 死亡回弹 / `remove(KILLED)` 拦截）；
   ② **动画已自愈**：`tick()` 客户端分支的对称自愈（§3.17(2)）让"血量回正却还躺着"至多残留 **1~2 tick**。
   ⇒ **"要不要继续把 `goety:killing_focus` 拉黑"现在是玩家的自由选择**，不再是必需手段：
   **拉黑** = Boss 干脆不抽这张聚晶；**开后门**（默认）= Boss 会被抽到、但打中就能真死。
   两者的取舍还牵涉代价——索命会对施法者反噬"目标当前生命值 **125%**"（§3.17(5)）。**待实测**（见下方第 9 条）。
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
   ✅ **0.0.14 新增同期待验收项**：① 配置界面新增的**聚晶黑名单输入框**（排版/输入/保存提示/状态行文案，
   以及"点完成关屏保存 + 点开始评分顺带保存"两条路径）；② 改完黑名单后**取消拉黑是否真的立即回到池里**
   （`refreshBlacklist()` 的重扫 + 对象复用路径）。均**尚未进游戏实测**。
   ✅ **0.0.15 新增同期待验收项**：本体贴图精修（`tuner.png` 的翻领边缘 / 衣袖明暗 / 袖口细边 / 裤缝 / 靴口层次）
   **同样未做游戏画面验收** —— 本轮只以逐像素差分证明**头部区 0 差异 / alpha 蒙版完全一致 / 身体区改 1247 像素 /
   6 个主要 UV 面无空洞**，属**结构性核验**、不能替代观感确认（`art/preview.png` 是平面正背面拼接图，**不是游戏截图**）。
   ✅ **0.0.16 新增同期待验收项**：**「逐渐学习」的手感**（`scoring.learningWeightStart` / `learningWeightMax` /
   `learningWeightRampCasts`）—— 开局前 ~10 次施法是否**确实以初始分类主导**、~60 次施法后动态反馈是否**平顺接管**、
   以及"会不会显得太死板 / 太迟才学会"。本轮只做到**编译通过 + `javap` 字节码核对 + jar 条目核对**，
   **尚未进游戏实测**；建议用 `learningWeightStart=0`（纯初始分类）与 `=1`（旧行为）两场做**对照**。详见 §3.16(8)。
7. **药水效果的可观测性与现状（0.0.13 结论 + 0.0.14 补充）**：boss 自身 4 处自施已改走 `applySelfEffect`（被拒打 WARN），
   但 **`tickPhase2Buffs` 自身零日志** ⇒ 二阶段自施药水的**实际数值收益仍只能进游戏实测**；
   一阶段低谷的 `SAPPED` / `DARKNESS` 与仆从四效果虽**机制上生效**，但都构造为 `visible=false`（**无粒子**），
   **玩家看不出效果** —— 是否补粒子 / 加提示属**表现层待决策项**（不是 bug）。详见 §3.14(6)。
   ✅ **0.0.14 补充**：新增的**二阶段「回复/减伤」免疫**（`phase2_buffs.phase2EffectImmunity`，默认 true）
   正好堵住"外部来源给二阶段 Boss 叠抗性/吸收/再生"这一类**反向**干扰，见 §3.15(2)。
8. ✅ **「动态评分冲淡初始评分」（0.0.16 已处理，待游戏实测验证手感）**：动态评分（实战学到的偏移）原先与静态评分
   同权进入轮盘权重 ⇒ 开局没打几下**初始分类**（配置 / LLM 评分）就基本失效，"会学习的指挥家"表现力很弱。
   0.0.16 给**动态偏移**加了随**该个体施法次数**线性爬升的权重系数（默认 `0.15 → 1.0` / 60 次施法），
   **静态评分完全不受影响**；实现与配置见 **§3.16**，配置项见 §六 / `DEVELOPMENT_PLAN.md` §六。
   ⚠️ **仍待游戏实测验证手感**（开局是否确实由初始分类主导、接管是否平顺、曲线是否合适）
   —— 起手调参见 `DEVELOPMENT_PLAN.md` §十一「动态评分太强势 / 想强化『逐渐学习』」一行。
   顺带说明：`FocusEntry.getEffectiveAttackScore()` / `getEffectiveSurvivalScore()` **未删除**，
   现状是"**未缩权的诊断视角**"、不再被抽取路径调用（§3.16(7)）。
9. ⚠️ **0.0.17 索命后门 + 客户端死亡动画自愈：均待游戏实测**（本轮只做到**编译通过 + jar 条目核对**）。
   ① **索命后门**（`boss.deathCurseExecution`，默认 `true`）：索命聚晶命中 Boss 时应**真正处决**
   （日志出现 `[Tuner] Death-curse backdoor (goety:death): bypassing lock-health protection`），
   **不再**出现"动画已死、血量回弹"，也**不再**出现"血量不为 0 却躺着"。
   待验证的边界：**索命未命中**（被闪避 / 免伤 / 被其它模组取消）时 Boss 状态应正常
   —— 即 `deathCursePending` 标记会被清除、**不会**从此对任何致死伤害都免疫（若标记残留，Boss 会永久失去锁血保护）。
   对照实验：把 `boss.deathCurseExecution` 改成 `false` 并重启 → 应回到**旧行为**（索命被锁血拦下、仍有破状态风险）。
   ⚠️ 另需实测确认**代价侧**：索命对施法者反噬"目标当前生命值 125%"是否让玩家自己也被打死（这是设计内代价，非 bug）。
   ② **客户端死亡动画自愈**（`tick()` 客户端分支，无配置开关、始终生效）：任何"血量 > 0 但 `deathTime > 0`"的
   客户端残留都应**至多 1~2 tick** 内自动复位（`Death animation reset (client stale death animation …)` 走 DEBUG 日志，
   需开 DEBUG 才能看到）。验收路径：索命打中 → 服务端复活/真死两种情形下客户端模型都应立刻站直、无红色受伤层残留。
   详见 **§3.17**（动机/实证/实现/代价/验收建议全在那节）。
10. ⚠️ **0.0.18 两条新内容：全部待游戏实测**（本轮只做到编译通过 + jar 条目核对 + 字节码/资源核对；
    **聚晶图标还从未被人眼看过**——作者 agent 不具备图像输入能力）。验收清单：
    **① 调律波纹聚晶（最优先，数值可量化）**
    - 拿到聚晶（`/give @p goetytuner:tuner_ripple_focus`）→ 装进任意法杖（`goety:dark_wand` 默认 `SpellType.NONE`，接受所有聚晶）
      → tooltip 应显示**灵魂消耗 5**、系别"无"、以及 `.info` 说明；蓄力应约 **0.2 秒**、两次施放间隔应约 **0.5 秒**。
    - 释放时脚下应荡开一圈**由内向外扩散的声波**（同一份 `AccentRipple` 实现，见 §3.18(1)），
      8 格内的生物沿径向被推开，并响一声紫水晶音。
    - **必须对照 Boss 的铺垫期重音**：两者观感与击退幅度应**完全一致**（同一份代码、同一组参数）。
    - **自己人豁免**：牵着自己的仆从/宠物放这一发 → 它们**不应**被轰飞（Boss 侧无此豁免，属有意差异）。
    - 三个观感开关（`music.accentParticles` / `accentWave` / `accentSound`）关掉时，聚晶的对应表现也应一起消失
      （有意：玩家为性能关掉的观感不该在聚晶上复活）。
    - ⚠️ **黑名单是否真生效**（这是用户点名要求的一条）：重启后看日志有无
      `[Tuner] Skipping blacklisted focus: goetytuner:tuner_ripple_focus`；
      若**没有**这行，说明老 toml 未迁移（`focus.blacklist` 是改值不是加键，Forge 不回填）——
      手改动或用 Mods → Config → 「聚晶黑名单」补上。
      战斗中也**不应**看到 Boss 放出这一发。
    **② 调律师仆从**
    - `/give @p goetytuner:tuner_servant_spawn_egg` → 刷出来的仆从应与 Boss **同形同大小**（同模型/贴图/披风/三颗立方体），
      并且**没有主人**（不会跟随任何玩家）。
    - **认主**：手持任意聚晶**右键**它一次 → 动作栏应提示认主 + 设为优先释放；此后它开始跟随、索敌、施法
      （有目标时应该看到它放聚晶，且施法瞬间**对应颜色的立方体变亮**、姿态下蹲）。
    - **右键 = 优先释放**：设为优先后，只要该聚晶不在冷却，下一次施法应**就是它**（插队）；再右键一次取消。
    - **左键 = 不释放**：左键后该聚晶应**完全不再出现**在它的施法里；再左键一次取消。
      ⚠️ 重点确认**左键不会对仆从造成伤害**（也不应有击退/受击动画）——这依赖 `AttackEntityEvent`
      在 `Player.attack` **第一条指令**处被取消（字节码实证见 §3.18(6)）。
    - **落盘**：设置完指令后退回主菜单重进（或重载存档）→ 指令应仍在（NBT `DisabledFoci` / `PriorityFoci`）。
    - **个体隔离**：刷两只仆从，只对其中一只下指令 → 另一只**不受影响**（两条表在 `FocusPoolManager` **实例**上，不是 static）。
    - **别人家的仆从**：对**已有主人**（非自己）的仆从手持聚晶左键/右键 → 应走默认行为，不弹提示、不改设置。
    - **调参**：`servant.rotation`（默认 `23` = 攻击/召唤各半，改成 `2` = 纯攻击）、
      `servant.castIntervalTicks`（默认 40）、`servant.health`（默认 40）、`servant.followRange`（默认 32）。
      ⚠️ 这四个是**新增键 ⇒ Forge 自动补进老 toml、无需手工迁移**；改完需**重启游戏**（common 配置在启动时读入）。
    **③ 联机**：协议号已是 **`2.1`** 且**严格匹配** ⇒ 双方必须同为 0.0.18+，否则连接被拒。
    **④ 图标观感**：`assets/goetytuner/textures/item/tuner_ripple_focus.png`（16×16，程序化生成）
    在背包里是否好看、是否与其他聚晶风格协调——**纯人眼判断**，我看不了图；
    不满意可直接改 `art/gen_ripple_focus_icon.py` 的参数重跑（脚本是字节可复现的）。
11. ⚠️ **0.0.19 五条修复：全部待游戏实测**（本轮只做到编译通过 + 字节码/资源核对 + 部署产物比对）。
    ⚠️ **本条第 ②③④ 部分里的"提示文案 / 潜行清空 / 用时上限"已在第 58 轮被修改 —— 以第 12 条为准**：
    动作栏提示**已全部删除**、"潜行 + 左键清空"**已删除**、用时上限改成**两段预算**（持续段不含蓄力）。
    ① **长按类聚晶（优先，这是本轮的主修复）**：让 Boss/仆从抽到（或**优先释放**）`goety:corruption_focus`
    （腐化光束，最直观）、`goety:shocking_focus`（震撼）、以及任意其它"按住右键持续放"的聚晶
    （Goety 的 `IChargingSpell` 一大家子），应当看到法术**持续释放**而不是闪一下：
    - 腐化光束应**一直挂着**（跟随施法者视线）直到用时上限（默认 1 秒）才消失；
    - 震撼/轰炸/暴雪这类"每发生成"的应**连续出多发**；
    - 日志 DEBUG 下应出现 `[Tuner] Channelled cast <focus> (charge=N, total=20)`。
    ⚠️ **平衡**：腐化光束是每 tick 造成伤害的，上限 20 已经很高；被秒就把
    `casting.channelMaxTicks` 调小（5~200），或把该聚晶写进 `focus.blacklist`。
    **回归**：普通法术（火焰球之类非长按类）的前摇/释放节奏应**完全没变**。
    ② **聚晶指令**（对应"不能取消"）：任选一个聚晶 → 左键（提示"不释放：X ｜…"）→ **再左键应按"已取消不释放"**
    → 右键（提示"优先释放：X ｜…"）→ **再右键应按"已取消优先释放"**；
    交叉操作也应可逆（左键设不释放后按右键 ⇒ 变成优先；再按右键 ⇒ 回中立）。
    **潜行 + 左键**应清空全部指令。提示里的两个计数应随操作增减。
    ③ **仆从变肉**：`/summon` 或刷怪蛋弄一只，用普通武器打——应有**216 血 + 护甲 16** 的手感；
    摔落/火焰/窒息/溺水对其无效；近战比远程多掉 25%。
    （⚠️ 仆从**没有锁血**，别拿"打不死"当预期。）
    ④ **刷怪蛋范式**：**直接放置** ⇒ 提示"野生的调律师仆从"、它不认识你、手持聚晶点它只给提示
    （且左键仍然会**正常攻击**它，因为我们不吞掉那次交互）；**潜行放置** ⇒ 提示"认你为主人"、
    它开始跟随、且接受聚晶指令。tooltip 里应有两条说明。
    ⑤ **图标**：打开创造模式物品栏看那个聚晶图标是不是"黑底 + 白环 + 白心"。
12. ⚠️ **0.0.19 修补（第 58 轮）三条修复：全部待游戏实测**（本轮只做到编译通过 + 字节码/资源核对 + 部署产物比对）。
    ⚠️ **测试前先确认游戏已完全重启** —— 部署新 jar 时游戏正在运行，**运行中的实例仍是旧字节码**；
    这可能本身就是"第 57 轮的修复看起来没起效"的来源之一。
    ① **「不释放」是否终于起效**（**最重要的一条**）：
    - **直接放**一只仆从（= **野生**）→ 手持任意聚晶**左键**点它 ⇒ 该聚晶应变成"不释放"；
      ★ 这是本轮真正修掉的东西（原来**野生仆从每条指令都被 `canCommand` 拒绝**）；
    - **再左键一次** ⇒ 应切回中立；**右键** ⇒ 优先释放，**再右键** ⇒ 切回中立；
    - **潜行 + 左键** ⇒ **不再有任何特殊行为**（原来的"清空全部指令"已删，别把它当功能测）；
    - **别人家的仆从** ⇒ 被拒绝，且应在**日志**里看到 `[Tuner] Servant focus command refused`，
      **动作栏不应出现任何文字**（提示全删了，这是有意的）。
    ② **箭雨 / 长按类聚晶的持续时间**：让 Boss（或仆从）抽到 / **优先释放** **箭雨**（`goety:arrow_rain_focus`）⇒
    应看到**先蓄力约 1 秒、再持续放约 1 秒**（共 ~40 tick、**不再是放一发就停**）；
    **腐化光束**应是**一条跟着走、持续存在约 1 秒**的光束；**震撼 / 炼狱**按各自 `castUp` 顺延。
    日志 DEBUG 下应能对上 `charge` / `total` 两个数与上述口径一致。
    ⚠️ 若仍嫌"持续太短" ⇒ 调大 `casting.channelMaxTicks`（**只管持续段、不含蓄力**）；
    ⚠️ **平衡**：腐化光束每 tick 造成伤害，**调到 100 以上基本等于必杀**。
    ③ **提示精简**：刷怪蛋说明**只剩一行**；**放置时不再有动作栏提示**、**左/右键仆从时不再有动作栏提示**
    （**故意删掉的、不是 bug**）；`/give` 拿到蛋时也不应再看到 Goety 那句通用说明（`appendHoverText` 不调 `super`）。
    ④ **回归**：被拒绝的交互**仍然照常生效**（不吞掉攻击 / 交互）；野生 / 认主的归属仍**只在放置时**确定；
    协议仍 **2.1**；普通（非长按类）法术表现应与 0.0.18 完全一致。
    ⚠️ **本条 ③ 已被 0.0.20 部分推翻**：用户随后指出"左键和右键功能都没有提示了……这个提示是需要的"
    ⇒ 左右键的**指令结果提示已恢复**（物品介绍与放置提示仍然不要），**以第 13 条为准**。
13. ⚠️ **0.0.20（第 59 轮）四条内容：全部待游戏实测**（本轮只做到编译通过 + 字节码 / 资源核对 + 部署产物比对）。
    ⚠️ **测试前必须完全重启游戏** —— 新 jar 换了文件名（`0.0.19 → 0.0.20`），
    运行中的实例仍是旧字节码（部署时游戏正在运行）。
    ① **左右键提示是否回来了（最优先，这是本轮修的那条回归）**：
    - 手持**单个聚晶**左键仆从 ⇒ 屏幕上方应出现 `不释放：<聚晶名>`；**再左键一次** ⇒ `已取消不释放：<聚晶名>`；
    - 手持单个聚晶右键 ⇒ `优先释放：<聚晶名>`；再右键 ⇒ `已取消优先释放：<聚晶名>`；
    - 对**别人家的仆从**做同样的操作 ⇒ 红色 `这不是你的调律师仆从`（并且**这次攻击/交互照常生效**）；
    - ⚠️ 提示里的 `<聚晶名>` 应当**随你的语言设置显示中文聚晶名**，不是 `goety:xxx_focus` 这种 id；
    - ⚠️ 同时确认**没有回退过头**：刷怪蛋**放置时仍然不弹提示**、物品说明仍然**只有一行**。
    ② **聚晶包 / 多晶大袋的批量指令**：
    - 往**聚晶包**（或**多晶大袋**）里塞几颗不同的聚晶（可以再混点别的物品，应当被跳过）⇒
      **左键**仆从 ⇒ `不释放：N 个聚晶（聚晶包）`，随后在日志里应看到 N 条
      `[Tuner] Servant focus <id> -> DISABLED`；**再左键一次** ⇒ `已取消不释放：N 个聚晶（聚晶包）`；
    - **右键** ⇒ `优先释放：N 个聚晶（聚晶包）`，再右键 ⇒ 整体清除；
    - 语义确认：袋内**全部**已是该状态时才整体清除；**只要有一颗不是**，这次操作就是"全部设为该状态"；
    - ⚠️ **空袋子**（里面一颗聚晶都没有）左键/右键仆从 ⇒ **不应有任何反应**，而且这次攻击/交互**照常生效**。
    ③ **仪式召唤仆从**（需要黑暗祭坛 + 8 个基座）：
    - 基座摆 **紫水晶碎片 ×4 + 红石 + 钻石 + 金锭 + 青金石**；
    - **祭坛中心放一把带「调律:魔法伤害加成」的法杖**（打一次调律师拿到的那把）⇒ 用任意法杖右键祭坛启动；
    - 应当消耗 **100 灵魂**（`soulCost 10 × duration 10`：**每秒 10、持续 10 秒**）—— 灵魂不足会走 Goety 自己的
      `info.goety.ritual.noSouls.fail`（"没有足够的灵魂能量"）；
    - 完成后**仆从认主**（会跟随你、接受聚晶指令），且**中心那把杖被消耗**；
    - ⚠️ **反向验证**：中心放一把**没有调律加成**的普通法杖 ⇒ **仪式不成立**
      （Goety 会给"无效的仪式 / 物品不是仪式催化剂"），这是有意的；
    - ⚠️ 若仪式不启动，先看玩家是不是已经带了很多 Goety 仆从 ——
      `RitualRequirements.canSummon` 有**全局仆从上限**（Goety 对所有仆从召唤的统一规则）。
    ④ **杖的加成 → 仆从强度**：
    - **判定用的是「调律·巫法加成」**（0.0.20 按用户反馈改的；**不是**魔法伤害加成）——
      看召唤用杖 tooltip 上那行 `调律:巫法加成 +X%`，对着仆从看它的效果图标确认；
    - **默认 +10%/次**：打 **1** 次 ≈ +10% ⇒ **只有强健 Ⅰ**（没有生命恢复）；
      **3** 次 ≈ +30% ⇒ **强健 Ⅱ + 生命恢复 Ⅰ**；
      **7** 次 ≈ +70% ⇒ **强健 Ⅳ + 生命恢复 Ⅱ**；
      **9** 次 ≈ +90% ⇒ **强健 Ⅴ + 生命恢复 Ⅱ + 抗性提升 Ⅰ**；
      **11** 次 ≈ +110% ⇒ **抗性提升 Ⅱ（封顶）**；
      ⚠️ 想快速验证可以直接改杖的 NBT（`goetytuner` → `witchcraft_bonus`），
      或调 `wand_upgrade.wandWitchcraftBonus`（改完需重启）；
    - ⚠️ **必须知道的坑（见已知边界 15）**：**强健（`goety:buff`）只加近战攻击伤害，而仆从没有近战手段**
      ⇒ 它在**战斗力上几乎不产生差异**（HUD / 属性面板会显示）。
       ⇒ 它本身**不直接产生战斗力差异**；真正让仆从变强的是下面这两条（0.0.20 都已落地）：
    - ⚠️ **「强健」等级怎么读**（§3.21(5b)）：原版**只在等级 II~X 显示罗马数字** ——
      **等级 1 与等级 ≥11 都只显示「强健」**；本模组已把等级上限默认钉在 10（`servant.buffMaxLevel`）
      ⇒ 正常情况下应当看到 `强健 X` 这种形式；
      **等级的真实值看日志**：`[Tuner] Servant ... blessing from wand '<名>': bonus <X>% -> Buff <n> ...`，
      召唤时还应有一行 `[Tuner] Servant summon: captured wand '<名>' (witchcraft=+X%, magic=+Y%)`；
    - ⚠️ **仆从限伤（§3.21(5c)）**：216 血时**单次最多掉 32 点、每秒最多掉 108 点**
      ⇒ 用高爆发武器打它应当明显"打不动"（需要至少 2 秒才打死）；
      ⚠️ `/kill` 仍然能直接杀死它（`BYPASSES_INVULNERABILITY` 有意绕过限伤）。
    - ⚠️ 生命恢复 / 抗性提升是**真生效**的，可以拿它挨打对比（尤其抗性提升 Ⅱ 的减伤很明显）；
   - ⚠️ **顺带验两处刚修好的百分比**（§3.21(4c)）：玩家手持带「调律:巫法加成」的杖打同一目标，
     伤害应当**确实高 10%**（`/attribute` 看 `spell_potency` 仍是 0 —— 那是对的，百分比走伤害事件）；
     Boss 进二阶段后它的法术伤害应当**确实高 20%/50%**，且**原来漏判的那批法术**（腐化光束 / 火球 / hellfire）也在范围内；
    - **刷怪蛋 / `/summon` 出来的仆从不应有任何增益**（没有召唤用杖），这是有意的。
    ⑤ **仆从死亡掉落召唤用杖**：
    - 把仪式召唤的仆从**打死**（`/kill` 或打到死）⇒ 地上应出现**召唤时那把法杖**，
      **附魔 / 聚晶 / 它原有的调律加成全部还在**（tooltip 上那两行「调律:…」应当**照旧显示**），
      即"**原样返回、不再额外升级**"（与 Boss 死亡掉落"叠加升级"刻意不同）；
    - 日志应有 `[Tuner] Tuner servant died, returned its summoning wand: <uuid> (<name>)`；
    - ⚠️ **刷怪蛋召唤的仆从死亡不应掉杖**（它没有召唤用杖）；
    - ⚠️ 主手那把 `goetytuner:tuner_wand` **任何情况下都不应掉落**。
    ⑥ **左键/右键指令去抖 + 下界之星（本轮修的第三个「左键不起效」）**：
    - **单击一次只翻转一次**（动作栏提示只出现一次）；**按住左键不放也只翻转一次**、
      松手后状态保持不变；间隔 0.25s 以上再点 ⇒ 正常翻回去；
    - ⚠️ 同时确认**右击**（优先聚晶 / 聚晶包 / 下界之星）也不再翻转（右键按住时每 4 tick 重发一次交互包）；
    - **下界之星右键仆从** ⇒ 提示 `只释放优先聚晶`（没设过优先聚晶时是
      `只释放优先聚晶（尚未设过优先聚晶，暂时照常释放）`）⇒ 此时它**只放**你设过的优先聚晶；
      ⚠️ **没设过优先聚晶时应当照常释放**（这是设计，不是 bug）；
      有优先聚晶但此刻都在冷却 / 被禁放 ⇒ **不放**（「只放」的字面含义）；
      再右键 ⇒ `已取消「只释放优先聚晶」`；⚠️ 重进存档后开关仍在（NBT `PriorityOnly`）。
    ⑦ **回归**：① 聚晶指令的单体语义（互斥 + 可撤销）与 0.0.19 一致；
    ② 刷怪蛋"直接放 = 野生 / 潜行放 = 认主"不变，且**野生仆从仍可被任何玩家配置**；
    ③ 仆从的血量 / 护甲 / 减伤限伤仍与本体一致（216 血 / 护甲 16）；
    ④ 联机协议仍 **2.1**（双方都要 0.0.20）。

### 已知缺陷与待办（0.0.20 时点）

以下为 0.0.4 复核代码后新确认、到 **0.0.19** 时点仍未修复的问题（个别条目已在此期间解决，见条目标注）：

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
8. **【0.0.18 新增·已知边界】调律师仆从的「聚晶指令」依赖 `AttackEntityEvent`**：
   该事件在 `Player.attack` 的第一条指令处触发（字节码实证），因此**只要玩家左键就一定会到**；
   但它同时意味着**只要手持聚晶左键自家仆从就必然是"下指令"**，玩家无法在手持聚晶时用手打自家仆从
   （这是有意的：命令优先，且不小心揍自家仆从本来就不是期望行为）。
   若将来需要"潜行时跳过指令"，在 `TunerServantInteractions` 里加一个 `player.isShiftKeyDown()` 判断即可。
9. **【0.0.18 新增·已知边界】仆从没有近战手段**：刻意不装 `MeleeAttackGoal`（它是"指挥家"不是战士），
   因此贴脸时只会继续放聚晶。另：它**不会被 Boss 的召唤物清理逻辑波及**（`cleanOwnedMinions` 只清
   `getTrueOwner() == Boss` 的实体），但反过来**玩家仆从也不会被 Boss 的二阶段清场带走**——
   与 Goety 本体的仆从行为一致。
10. **【0.0.18 新增·已知边界】仆从的 `FocusPoolManager` 是"每实例一份全量拷贝"**：
   每只仆从构造时都会 `createFightPools()`（复制全部聚晶条目）。当前 ~200 个聚晶的量级下可忽略，
   但**若将来聚晶数上千、且场上同时存在大量仆从**，这里会变成一处分配热点
   （Boss 侧自 0.0.10 起就是这个模式，本轮沿用未改）。真要优化的话应做成"全局只读池 + 每实例只存偏差"。
11. **【0.0.19 新增·已知边界】长按类聚晶的强度完全取决于 Goety 自己的数值**：
   `casting.channelMaxTicks`（默认 20）只是**时长**上限，每次结算打多少仍是 Goety 的配置
   （例如腐化光束 `CorruptedBeamDamage` 默认 10.0**每 tick**，且它会清零目标的 `invulnerableTime`
   以绕过无敌帧）。因此"调大上限"与"伤害爆炸"是同一件事 —— 想加强观感而不要秒杀的话，
   应当去调 Goety 自己的配置，而不是只调本模组的键。
12. **【0.0.19 新增·已知边界】高潮期三个并行通道会共用同一把主手法杖**：
   0.0.10 起 Boss 的三条并行通道都往同一只主手装聚晶（互相 `extract`/`insert`）。
   0.0.19 引入 `startUsingItem` 之后，多个通道型法术同时开火时"谁在松手"会互相干扰 ——
   已用"每 tick 自愈式重设 `startUsingItem`"兜住，最坏情况是**某一 tick 的 `isSpellCasting` 抖动**
   （表现为光束极短暂地闪断一帧）。若实测明显，正确修法是给每个通道各自的法杖叠，或让通道串行。
13. **【0.0.19 修补（第 58 轮）新增·已知边界】`casting.channelMaxTicks` 只约束"持续段"，总施法时间还要加上蓄力**：
   第 57 轮把它当"总时长"用 ⇒ `castUp` 接近或超过它的法术（箭雨默认 20）会被蓄力吃光预算、**只放一发**。
   现在 `channelEndTick = clamp(round(castUp × 倍率), 0, maxCastWindowTicks) + max(5, channelMaxTicks)`
   ⇒ **同一个 `channelMaxTicks` 在不同聚晶上的"总时长"是不同的**（腐化光束 20 tick、箭雨 40 tick）。
   这是**有意**的（蓄力是法术自己的节奏，不该被本模组的持续上限吃掉），但**读配置的人容易误会**，
   故配置注释已写清"**不含蓄力**"。若将来要一个真正意义上的"总时长硬上限"，应当**另加一个键**，
   不要偷偷改这个键的语义（否则又是一次"看起来没生效"）。
14. **【0.0.19 修补（第 58 轮）新增·已知边界】野生仆从会听"任何玩家"的聚晶指令**：
   这是修「不释放完全不起效」的**直接代价** —— 第 57 轮的 `canCommand` 要求"必须是主人"，
   而**刷怪蛋默认直接放 = 野生（`owner == null`）** ⇒ 那样等于**功能整体不可用**（真实症状）。
   现在 `canCommand` = `owner == null || owner == player` ⇒
   **野生仆从谁都能指挥**（它没有主人、也就没有"别人家的"可言），**只有别人家的仆从**才拒绝。
   若将来觉得"野生仆从应只对放置者开放"，正确做法是**在放置时另记一个"放置者"字段**，
   **不要**复用 owner 语义 —— 否则又会回到"归属随交互改变"的老坑（见 §七.12 与红线 14）。
15. **【0.0.20 新增·已知边界 · 必须告知用户】`goety:buff`（强健）对本模组的仆从几乎不产生战斗力**
   用户要求"加成越高，持续性地给予自身 n 级**强健**效果"，本轮**忠实照搬**（`GoetyEffects.BUFF`）。
   但实证下来它的作用面非常窄：
   - Goety 语言文件：`effect.goety.buff.description` = **"每级增加 1 点的近战攻击伤害"**；
   - 反编译 `GoetyEffects` 的注册 lambda（`lambda$static$7`）：`GoetyBaseEffect(BENEFICIAL, 0x6A0000)`
     `.addAttributeModifier(Attributes.ATTACK_DAMAGE /* f_22281_ */, uuid, 1.0, ADDITION)`
     （外加一条 `MULTIPLY_TOTAL` 的 `f_22283_` 修正）—— **只动近战攻击属性**；
   - 而本模组的仆从**刻意没有近战手段**（不装 `MeleeAttackGoal`，"它是指挥家不是战士"），
     伤害全部来自它释放的聚晶 ⇒ **强健加的那点 ATTACK_DAMAGE 打不出去**。
   ⇒ **结论**：用户规格里的 ①（强健）在当前实现下**只影响 HUD / 属性面板显示**，
   真正让仆从变强的是 ②（生命恢复 / 抗性提升）。
   **可选修法（未做，等用户决定）**：把强健等级**同时**换算成一个
   `ModAttributes.SPELL_POTENCY` 的 modifier（与法杖的"巫法加成"走同一条通道、
   按等级线性递增），这样"加成越高 ⇒ 法术越强"才成立。
   ⚠️ **不要自作主张加** —— 那是新增机制，属于用户的设计决策。
16. **【0.0.20 新增·已知边界】仪式召唤的仆从强度**完全依赖**中心那把杖**，且必须**真的消耗掉**它
   - 只有**仪式召唤**的仆从有"召唤用杖"（`TunerServant#SummonWand` NBT）⇒
     刷怪蛋 / `/summon` 出来的仆从**永远没有增益、死了也不还杖**（有意的，见 §3.21(3)）；
   - 杖在仪式结束时被祭坛 `shrink(1)` **消耗**，仆从 NBT 里存的是它的**快照** ⇒
     玩家"用一把杖换来一只强力仆从，仆从死了才能把杖拿回来"（**这正是用户要的循环**）；
   - ⚠️ 反过来说：**仆从活着的时候那把杖不在你手上**（不是"杖还在 + 白得一只仆从"）。
     若将来想让"杖留在玩家手里"（仆从不消耗它），必须改 `TunerServantSummonRitual#finish`
     去阻止父类的 `shrink` —— 那会改变平衡，**需要用户明确要求**。
18. **【0.0.20 查出·⚠️ 原版只在 amplifier 1~9 时显示效果等级】**
   即 **等级 1 与等级 ≥11 都只显示效果名、看不到数字**（1.20.1
   `EffectRenderingInventoryScreen#getEffectName` 字节码：
   `if (amplifier >= 1 && amplifier <= 9) 才 append enchantment.level.<n>`；
   原版语言文件里 `enchantment.level.*` 只有 1~10 十个键）。
   **⇒ 教训：不要拿"效果等级数字"当作玩家可见的反馈通道** ——
   设计上一旦可能超过 X 级，玩家就会以为"没生效"（本轮用户实测正是如此：
   +1000% 巫法 = 50 级，客户端只显示「强健」）。
   **本模组的处理**：`servant.buffMaxLevel` 默认钉在 10（可见范围内）+
   等级变化时写 INFO 日志（真实等级、原始等级、杖名全在里面）。
   若将来还需要更高的真实等级，正确做法是**另给一个可见的载体**
   （动作栏提示 / 属性面板 / 自定义 HUD），而不是依赖原版的效果名。

   17. **【0.0.20 查出并已修复·两处死代码：法术强度不能用百分比】**（⚠️ 起初**只报告**，
     用户随后要求"修一下这两个问题、保证实际生效和文字描述相同" ⇒ **同轮已修**，见 §3.21(4c)）
     ⚠️ **历史记录（修复前的状态）**：
   实证见 §3.21(4b)：`goety:spell_potency` **基础值 0.0**、读取是 `(int)` 截断、
   法术当**平铺点数**用 ⇒ **`MULTIPLY_TOTAL` 恒等于 0**（0.0 × 任何数 = 0.0）。
   于是本模组这两处"调律加成"实际上**从未生效过**：
   - **`WandUpgradeEvents#refreshWitchcraftModifier`**：玩家的「调律:巫法加成」
     （`wand_upgrade.wandWitchcraftBonus`，默认 0.10）用 `MULTIPLY_TOTAL` 挂 SPELL_POTENCY
     ⇒ **tooltip 会显示 `调律:巫法加成 +10%`，但法术伤害一点没变**；
   - **`TunerBoss#tickPhase2Buffs`**：第 37 轮为二阶段补的
     "力量等级 × 0.1（等级2=20% / 等级5=50%）"同样是 `MULTIPLY_TOTAL` ⇒ **同样没生效**。
   **⇒ 已按"文字怎么写就怎么生效"修复（同轮）**：两处的**百分比语义保持不变**
   （不改成点数，因为文字承诺的就是百分比），而是把**通道**从属性 modifier 换成
   **伤害事件 ×(1+加成)**（新增 `combat/SpellDamageBonus`）：
   - 「调律:巫法加成 +10%」⇒ 玩家法术伤害 **×1.10**（与魔法伤害加成相加，满配杖合计 ×1.50）；
   - Boss 二阶段「力量 2/5 级」⇒ 其法术伤害 **×1.20 / ×1.50**（读效果等级，buff 到期即归零）。
   ⚠️ 同时发现并修掉了判定覆盖面的问题：原判定只认 `forge:is_magic` 标签
   （Goety 39 个伤害类型里只有 8 个），Boss 的腐化光束（`goety:magic_fire`）、
   火球（`goety:magic_fireball`）、`hellfire` / `shock` / `direct_freeze` 等**全都不在内**
   ⇒ 现在并上「**`goety` 命名空间**」判定，覆盖 Goety 全部法术伤害类型。
   ⚠️ 另注：`wand_upgrade.wandBonusStack`（多次击败叠加）与仆从增益档位**不受此问题影响** ——
   前者只决定 NBT 里的数值大小，后者读的是 NBT 数字而不是属性。
   **两条通道的最终分工**：**百分比 → 伤害事件（`SpellDamageBonus`）；
   点数 → `SPELL_POTENCY`（`BuffSpellPower`）**。

---

*本文档由开发记忆库自动整理，与 DEVELOPMENT_PLAN.md / README.md 互为补充；*
*本文件是**技术现状的最新权威文档**，优先级高于 DEVELOPMENT_PLAN.md（后者是阶段性计划书，
进度表可能滞后）。*
*两副本（D:\tiaolvshi\goety-tuner 与 D:\测试\tiaolvshi\goety-tuner）需保持同步。*
