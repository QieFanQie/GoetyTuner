# 《仪式召唤 + 法杖升级》设计方案（任务 #118）

> MC 1.20.1 Forge 47.3.22 · 诡厄巫法 Goety 2.5.56.5 附属 · mod id `goetytuner`
> 版本基线：v0.6.2（三个 bug 修复已闭环）· 本文档为 #118 编码前设计稿
> 编写日期：2026-08-19 · 状态：**待审阅**
> 依据：全部关键机制均经 goety-2.5.56.5.jar 反编译实证（见"证据"标注），非猜测

---

## 一、需求复述与目标

原需求（用户原始描述）：

1. 用 **4 个下界之星 + 1 个任意法杖**（法杖放在中心祭坛上），在**魔法仪式**上召唤 Boss；
2. Boss 会**手持召唤所用的法杖**；
3. 击败 Boss 后**掉落该法杖的升级版**，效果：
   - **10% 巫法加成**
   - **40% 魔法属性伤害加成**
   - 若 Goety 本体没有此类加成 → 自创一个**非冷却/非施法前摇**的加成，并写好"**法杖已有此加成则数值叠加**"逻辑；
4. **附魔、无法破坏等其他 NBT 全部保留**（不洗掉、不改动），只新增数据；若做不到则需配置化兜底（首选：直接不改变原数据 + 新增）。

约束（第四轮调研结论 + 本轮反编译）：

- Boss 施法走 `WandUtil.getStats(caster, spell)`，`ModAttributes` 全部**加法**并入 `SpellStat`（duration 为 tick 制）→ 10% 巫法加成若直接加 duration 数值会过猛，须走**实体属性百分比 modifier**；
- Goety **无 per-wand 魔法伤害 NBT**（`ModAttributes` 仅 potency/duration/range/radius/burning/velocity/castingSpeed/cooldownDiscount/soulDiscount + 各系 POTENCY/DISCOUNT）→ 40% 魔法伤害加成需自创落地路径；
- 法杖核心数据（聚晶槽）存在 `SoulUsingItemHandler` 的 ItemStack 中，随 NBT 保存 —— 复制法杖即可整体保留。

---

## 二、总体方案（决策表）

| 决策点 | 方案 | 理由 |
|---|---|---|
| 仪式系统 | **复用 Goety 原生暗黑祭坛仪式**（纯数据包配方 + 自定义 Ritual 子类） | Goety 仪式 = `goety:ritual` 数据包配方，无需自建方块/实体/配方序列化器；配方天然可被玩家数据包修改 |
| 自定义仪式类型 | **注册自定义 `ModRitualFactory` 进 `goety:ritual_factory` 注册表** | `SummonRitual.finish()` 不把激活物品交给实体、且会 `shrink(1)` 消耗，必须覆写才能让 Boss 手持法杖 |
| Boss 手持法杖 | **覆写 `SummonRitual.initSummoned()`**（public 空方法，天然的挂点） | 从祭坛 `itemStackHandler.slot0` 取法杖副本（完整 NBT）→ `setItemSlot(MAINHAND)`；不动 finish 主流程 |
| 结构检查门槛 | **配方 `craftType` 复用内置 `"magic"`（魔法仪式）** | 结构检查是硬门槛（激活时 + 仪式中持续），craftType 必须命中内置 15 种类型之一；"magic" 语义贴合"魔法仪式召唤"，且玩家需按魔法结构搭建，仪式感完整 |
| 掉落升级法杖 | **Boss 死亡掉落逻辑（LivingDropsEvent 或 dropCustomLoot）** | Boss 手持法杖 → 取副本 → NBT 全保留 + 新增两个加成键 → 掉落 |
| 10% 巫法加成 | **Boss 实体属性 `SPELL_POTENCY` 加 `MULTIPLY_TOTAL` 百分比 modifier** | `WandUtil.getStats()` 每施法实时读实体属性，天然生效；百分比不破坏 tick 制加法语义 |
| 40% 魔法伤害加成 | **LivingDamageEvent 拦截**（Boss 作为伤害来源 + 魔法/法术伤害类型判定） | Goety 无对应属性；事件方案零注册、易配置、可精确限定"仅法术伤害" |
| 玩家手持法杖 | **不消耗**（Goety 原版机制如此） | `startRitual` 只把玩家手持物品 `copy()` 进 slot0，仪式完成消耗的是副本 —— 玩家保留原法杖，这是原版设计，文档明确告知 |

---

## 三、仪式系统剖析（反编译实证）

> 证据来源：`RitualRecipe$Serializer`、`DarkAltarBlockEntity`、`Ritual`、`SummonRitual`、`RitualRequirements`、`RitualType`、`ModRituals`、`IRitualType` 及 15 个内置 `*RitualType` 全部 javap 反编译。

### 3.1 配方 JSON（`RitualRecipe$Serializer.fromJson` 键名全部实证）

| 键 | 必填 | 说明 |
|---|---|---|
| `type` | ✅ | 固定 `"goety:ritual"` |
| `ritual_type` | ✅ | RL，指向 `goety:ritual_factory` 注册表；`RitualRecipe` 构造时**立即查注册表并 create Ritual 实例**（配方加载即绑定，注册必须先于配方加载） |
| `activation_item` | ✅ | JsonElement → `Ingredient`；测试**玩家手持物品** |
| `ingredients` | ✅ | JsonArray → 基座材料（NonNullList\<Ingredient\>，非空校验） |
| `result` | ✅ | JsonObject → ItemStack；继承 `ModShapelessRecipe` 强制存在；**召唤配方官方用占位物品 `goety:jei_dummy/none`** |
| `entity_to_summon` | ❌ | RL → `ForgeRegistries.ENTITY_TYPES` |
| `craftType` | ❌ | String，默认 `""`；**决定结构检查**（必须命中内置类型） |
| `duration` | ❌ | int，默认 30，**tick 单位**（官方 summon 配方 10~30） |
| `soulCost` | ❌ | int，默认 0，**每秒费率**（见 3.3） |
| `summonLife` / `research` / `entity_to_sacrifice` / `entity_to_convert` / `structure_to_locate` / `enchantment` / `xpLevelCost` | ❌ | 本项目用不到，不写 |

**官方参照配方**（`data/goety/recipes/summon_apostle.json`）：

```json
{
  "type": "goety:ritual",
  "ritual_type": "goety:summon",
  "activation_item": { "tag": "forge:ingots/netherite" },
  "craftType": "sabbath",
  "entity_to_summon": "goety:summon_apostle",
  "soulCost": 1,
  "duration": 30,
  "ingredients": [ /* 4 件祭品 */ ],
  "result": { "item": "goety:jei_dummy/none" }
}
```

### 3.2 激活流程（`DarkAltarBlockEntity.activate` 全流程实证）

玩家**手持法杖右键祭坛**（这就是"法杖放在中央祭坛上"的实际交互；放基座的是 4 个下界之星）：

```
右键 → checkCage()                                          ← 硬门槛
     │  祭坛正下方一格必须为 goety:cursed_cage（诅咒牢笼）
     │  且牢笼内 getItem() 非空 + getSouls() > 0
     ├─ 失败 → removeItem()（取出祭坛上物品），激活失败
     ↓
配方匹配：RecipeManager.getAllRecipesFor(goety:ritual)
     → 过滤 ritual.identify(level, pos, player, 手持stack)
        = activationItem.test(手持) + areAdditionalIngredientsFulfilled(...)
          （基座物品与 ingredients 一对一匹配，数量一致）
     ├─ 无配方 → 提示 info.goety.ritual.itemProblem.fail
     ↓
ritual.isValid(...)    ← SummonRitual = super.isValid + canSummon
                        canSummon：目标实体非 IOwned → 直接 true（无数量限制）
getProperStructure(craftType, player, altar, pos, level)   ← 硬门槛
     ├─ craftType 查 RitualType.getRitualType() → null → false（激活失败）
     ├─ 命中内置类型 → 该类型 getRequirement()（= getStructures 方块布局检查）
     ↓
research 检查（FORBIDDEN 需 LichScroll；不写 research 则跳过）
     ↓
startRitual(player, 手持stack, recipe)
     → itemStackHandler.insertItem(0, 手持.copy())   ← slot0 = 法杖完整副本（NBT 全保留）
     → ritual.start(...)
     → structureTime = 40
```

### 3.3 仪式进行中（`DarkAltarBlockEntity.tick` 实证）

- **灵魂**：每 20 tick（1 秒）`cursedCage.decreaseSouls(soulCost)` —— `soulCost` 是**每秒费率**；不足 → 提示 + 中断；
- **材料**：基座全部 `setLocked(5)` 锁定；`ritual.consumeAdditionalIngredients(...)` 按 `Math.floor` 分段**逐步消耗**下界之星（失败 → `cannotConsume` → 中断）；
- **结构复查**：每 tick 重跑 `getProperStructure`；失败累计 `structureTime`，**超过 60 tick（3 秒）未修复 → 中断**（玩家必须保持结构完整）；
- **完成**：`currentTime >= duration` → `stopRitual(true)`。

### 3.4 完成与中断（`stopRitual` 实证）

```
stopRitual(true) → ritual.finish(level, pos, altar, castingPlayer, slot0stack)
   SummonRitual.finish:
     → super.finish()          （IRitualType 光柱/音效回调，不消耗物品）
     → slot0stack.shrink(1)     （消耗法杖副本；玩家手中原件不动）
     → 粒子
     → createSummonedEntity()   （entityType.create(level)，普通创建）
     → LivingEntity: prepareLivingEntityForSpawn(...)
       → initSummoned(...)      ★ 默认空方法 —— 我们的覆写点
       → spawnEntity(player, entity, level)（advancement + addFreshEntity）

stopRitual(false) → ritual.interrupt(...) + slot0 物品掉落回地上 + clearRitual()
```

### 3.5 两个必须知道的"坑"（设计依据）

1. **`RitualType.addRitualType` 在结构检查路径上无效**：`getRitualTypeList()` 每次调用**新建 HashMap** 只含 15 个内置类型，`addRitualType` 写入的静态字段永远不会被 `getRitualType()` 读取 → **自定义 craftType 放行方案不可行**，只能复用内置类型名；
2. **`RitualRecipe` 构造时即从 `goety:ritual_factory` 查 factory 创建 Ritual** → 我们的自定义 factory 必须在**配方加载前**注册成功（DeferredRegister 时序天然满足；需在 dev 环境验证）。

---

## 四、方案 A：仪式召唤（核心实现）

### 4.1 新增类：`TunerSummonRitual extends SummonRitual`

```
public class TunerSummonRitual extends SummonRitual {
    public TunerSummonRitual(RitualRecipe recipe) { super(recipe, false, false); }
    // tame=false（Boss 不被驯服）、noVariant=false（TunerBoss 不实现 IOwned，variant 逻辑不触发）

    @Override
    public void initSummoned(LivingEntity living, Level level, BlockPos pos,
                             DarkAltarBlockEntity altar, Player player) {
        super.initSummoned(living, level, pos, altar, player);   // 空实现，语义保留
        if (living instanceof TunerBoss boss) {
            ItemStack wand = altar.itemStackHandler.orElseThrow(...).getStackInSlot(0);
            if (!wand.isEmpty()) {
                boss.setItemSlot(EquipmentSlot.MAINHAND, wand.copy());
                boss.setGuaranteedDrop(EquipmentSlot.MAINHAND);   // 保证死亡掉落主手（配合 5.1）
            }
        }
    }
}
```

要点：
- `finish()` 主流程（消耗副本、粒子、生成实体）**全部不动**，只挂 `initSummoned`；
- `slot0` 法杖 = 玩家仪式时手持物品的**完整副本**（附魔/无法破坏/聚晶等 NBT 一个不少）→ 天然满足"不改变原有数据"；
- 副本随后会被 `finish()` 的 `shrink(1)` 消耗，**Boss 拿到的是 shrink 前的副本** —— 时序安全（finish 先 initSummoned 后 shrink？确认：`SummonRitual.finish` 字节码顺序为 super.finish → shrink(1) → 粒子 → createSummonedEntity → initSummoned → spawnEntity。**shrink 在 createSummonedEntity 之前**，但 shrink 作用于 slot0 的 stack 对象本身（数量-1），`initSummoned` 里 `getStackInSlot(0)` 拿到的是**同一对象的当前状态**。若法杖数量=1，shrink 后 getStackInSlot 返回的 stack 数量为 0 → `isEmpty()` 判断会拦住！**必须改用 `copy()` 时机**：方案是覆写 `finish()` 提前取副本，或 initSummoned 里从 `altar.consumedIngredients` / 直接 `slot0.copy()` 但把 `shrink` 顺序问题绕开 —— 最稳妥：**覆写 `finish()`，在 `super.finish()` 之前先取法杖副本存字段，再 super**（详见 4.2 修订）。

### 4.2 ★ 修订：覆写 `finish()` 而非 `initSummoned()`（时序修正）

反编译 `SummonRitual.finish` 字节码确认：`super.finish → stack.shrink(1) → 粒子 → createSummonedEntity → ... → initSummoned → spawnEntity`。

`srink(1)` 发生在 `createSummonedEntity` 之前，若 slot0 法杖数量为 1（常规情况），到 `initSummoned` 时 `getStackInSlot(0)` 已为 EMPTY。**因此必须在 shrink 之前取副本**：

```java
public class TunerSummonRitual extends SummonRitual {
    private ItemStack wandCopy = ItemStack.EMPTY;

    @Override
    public void finish(Level level, BlockPos pos, DarkAltarBlockEntity altar,
                       Player player, ItemStack stack) {
        // 1) shrink 前先取法杖副本（slot0 = 玩家手持的完整 NBT 副本）
        ItemStack slot0 = altar.itemStackHandler.orElseThrow(NullPointerException::new).getStackInSlot(0);
        if (!slot0.isEmpty()) this.wandCopy = slot0.copy();
        // 2) 原流程：super.finish → shrink(1) → 粒子 → 生成实体 → initSummoned → spawnEntity
        super.finish(level, pos, altar, player, stack);
    }

    @Override
    public void initSummoned(LivingEntity living, Level level, BlockPos pos,
                             DarkAltarBlockEntity altar, Player player) {
        super.initSummoned(living, level, pos, altar, player);
        if (living instanceof TunerBoss boss && !wandCopy.isEmpty()) {
            boss.setItemSlot(EquipmentSlot.MAINHAND, wandCopy);      // 战斗演出用（会被 Boss 改写聚晶槽）
            boss.setOriginalWand(wandCopy.copy());                   // ★ 快照持久化，死亡掉落以此为准（见 5.1 修订）
            wandCopy = ItemStack.EMPTY;   // 防重复注入
        }
    }
}
```

### 4.3 注册自定义 factory 进 `goety:ritual_factory`

`ModRituals.RITUALS` 为 `DeferredRegister.create(new ResourceLocation("ritual_factory"), "goety")` 创建的注册表（registry key **`goety:ritual_factory`**，`RegistryBuilder.disableSaving().setMaxID(Integer.MAX_VALUE-1)`）。第三方注册方式：

```java
// ModBusEvents（或新类 RitualRegistration）：
public static final DeferredRegister<ModRitualFactory> RITUALS =
        DeferredRegister.create(
            new ResourceLocation("goety", "ritual_factory"),   // 已存在的注册表 key
            GoetyTuner.MODID);

public static final RegistryObject<ModRitualFactory> TUNER_BOSS_SUMMON =
        RITUALS.register("tuner_boss_summon",
            () -> new ModRitualFactory(TunerSummonRitual::new));
// RITUALS.register(bus) 挂到 MOD 总线
```

- `ModRitualFactory` 构造器签名 `(Function<RitualRecipe, Ritual>)`（实证）→ `TunerSummonRitual::new` 直接匹配；
- **配方 JSON 的 `ritual_type` 写 `goetytuner:tuner_boss_summon`**；
- 风险与兜底：跨 mod 注册自定义注册表条目在 Forge 属标准能力（DeferredRegister 自动按依赖排序注册事件），但 `disableSaving` 注册表 + 第三方写入需 dev 实测；若注册事件被拒，兜底为在 `RegisterEvent` 中手动 `event.getRegistry(...).register(...)`。

### 4.4 配方 JSON（`src/main/resources/data/goety/recipes/tuner_boss_ritual.json`）

```json
{
  "type": "goety:ritual",
  "ritual_type": "goetytuner:tuner_boss_summon",
  "activation_item": { "tag": "goety:wands" },
  "craftType": "magic",
  "entity_to_summon": "goetytuner:tuner_boss",
  "soulCost": 1,
  "duration": 600,
  "ingredients": [
    { "item": "minecraft:nether_star" },
    { "item": "minecraft:nether_star" },
    { "item": "minecraft:nether_star" },
    { "item": "minecraft:nether_star" }
  ],
  "result": { "item": "goety:jei_dummy/none" }
}
```

- `activation_item` 用 **`#goety:wands` 物品 tag**（实证 = `dark_wand` + 11 把 staff）→ **任意法杖**；
- `craftType: "magic"` → 结构检查走 `MagicRitualType` → `RitualRequirements.getStructures("magic", ...)`（魔法结构：需按 Goety 的魔法仪式布局搭建，激活与仪式全程保持）；
- `duration: 600`（30 秒仪式）+ `soulCost: 1`（每秒 1 灵魂，总量约 30）—— **具体数值进配置或数据包可改**；
- 注意：**配方放 `data/goety/` 而非 `data/goetytuner/`**（recipe type 是 goety 的，玩家能直接看到/改）。

### 4.5 玩家交互与成本汇总（写入 README/游戏内说明）

1. 祭坛正下方放**诅咒牢笼**（内存灵魂 > 0）；
2. 祭坛周围按**魔法仪式结构**摆放（`craftType: magic` 对应布局，见 Goety 文档/游戏内引导）；
3. 4 个**下界之星**分别放在 4 个基座上；
4. **手持任意法杖右键祭坛** → 仪式开始（结构保持 3 秒内不被破坏）；
5. 30 秒后 Boss 从祭坛现身，**手持那把法杖**；
6. 玩家手中法杖**不消耗**（原版机制：消耗的是祭坛内副本）—— 向玩家明示这一设计。

---

## 五、方案 B：掉落升级法杖

### 5.1 触发时机（2026-08-20 修订：必须用"原始法杖快照"，不能直接掉主手）

**★ 新发现问题（源码复核）**：`CastChannel.beginCast/instantCast` 每次施法都调
`BossWandHelper.installFocus(boss.getMainHandItem(), entry, null)` —— 该方法会
`handler.extractItem()` **取出旧聚晶后丢弃**、再 `insertItem(新聚晶)`。也就是说
**Boss 战斗全程在原地改写主手法杖 NBT 的聚晶槽**。若按原方案死亡时掉落主手法杖：

- 掉的是**被 Boss 改写过的法杖**：聚晶槽 = Boss 最后一次抽取的聚晶（含 Boss 注入的附魔）；
- 玩家原装在法杖里的聚晶在第一次施法时就被 extract 后**凭空消失**；
- 违反"附魔等 NBT 全部不改变"的核心需求。

**修订方案（快照制）**：

1. `initSummoned` 注入手持法杖的同时，**另存一份原始快照**到 Boss 实体数据
   （`addAdditionalSaveData`/`readAdditionalSaveData` 持久化键 `OriginalWand`，
   防存档重进丢失）；
2. **不**调 `setGuaranteedDrop(MAINHAND)`（避免掉出被改写的法杖）；Boss 主手照常
   持有并改写（战斗演出需要），但死亡掉落与主手脱钩；
3. `LivingDropsEvent`：实体为 TunerBoss 且快照非空 → 生成 `快照.copy()` + 新增加成键
   （5.2），加入掉落列表；
4. 效果：玩家拿回**与放入仪式时逐字节一致的法杖**（含原聚晶、附魔、无法破坏），只多出
   `goetytuner` 加成复合键 —— 比"复制主手"更严格地满足"不改变原数据"。

### 5.1-old 触发时机（原稿，已被上文修订取代）

- Boss 死亡 → 掉落主手物品（`setGuaranteedDrop(MAINHAND)` 已保证）；
- 升级版 = 主手法杖副本 + 新增 NBT。实现位置二选一（推荐 ①）：
  - ① `LivingDropsEvent`（`ModEvents` 现有总线）：`event.getEntity() instanceof TunerBoss` 且其主手为法杖 → 把对应掉落物替换为升级版副本；
  - ② 覆写 `TunerBoss.dropCustomLoot(...)` / `getLootTable` 旁路 —— 侵入实体类，改动大，不推荐。

### 5.2 NBT 设计（保留 + 新增）

```
升级版法杖 = 原法杖.copy()
  │  ├── 原 NBT 全部保留：附魔（Enchantments）、无法破坏（Unbreakable:1b）、
  │  │    聚晶数据（SoulUsingItemHandler 相关键，含 getSlot 聚晶槽）、Damage、display 等
  │  └── 新增（CompoundTag）：
  │       goetytuner: {
  │         "witchcraft_bonus": <double>,     // 10% 巫法加成
  │         "magic_damage_bonus": <double>    // 40% 魔法属性伤害加成
  │       }
```

**叠加逻辑**（需求"法杖已有此加成则数值叠加"）：
- 读取 `stack.getTag().getCompound("goetytuner")` 中对应键；
- 已有 → 新值 = 旧值 + 本次值（加法叠加，保留旧值语义）；
- 没有 → 直接写入；
- 配置开关 `WAND_UPGRADE_STACK`（默认 true）控制是否叠加（false = 覆盖）。

### 5.3 加成落地（核心机制）

**① 10% 巫法加成 —— 实体属性百分比 modifier**

```java
// 掉落物给玩家后，玩家装备/手持该法杖施法时生效？
```

**关键设计决策**：Goety 的 `WandUtil.getStats(caster, spell)` 读的是**施法者实体**的属性（`ModAttributes.getPotency(caster, spell)` → `caster.getAttributeValue(SPELL_POTENCY)`）。因此让"法杖上的 10% 加成"生效有两种落地方式：

- **方式甲（推荐）：施法者侧属性 modifier**。在 `LivingEquipmentChangeEvent`（或 `ItemEquippedEvent`）中检测玩家主手/副手为升级法杖 → 给玩家 `SPELL_POTENCY` 加 `MULTIPLY_TOTAL` 0.1 modifier；卸下/换武器时移除。优点：走 Goety 原生属性通道，与 `getStats` 完全兼容，对其他 Mod 施法同样生效；缺点：modifier 管理要小心（UUID 固定 + 防重入）。
- **方式乙：伤害侧拦截**。在 `LivingDamageEvent` 对施法者加成 —— 但这本质是伤害加成而非"巫法加成"，与 40% 魔法伤害加成重复，不采用。

> 方式甲的数值语义：`SPELL_POTENCY` 是加法入 `SpellStat.potency` 的，`MULTIPLY_TOTAL` 0.1 表示施法者基础 potency ×1.1（乘法，不破坏 tick 加法）—— 符合"10% 巫法加成"且不会过猛（对比：直接给 potency +1 对低级法术是 100% 增幅）。

**② 40% 魔法属性伤害加成 —— LivingDamageEvent 拦截**

```java
@SubscribeEvent
public static void onLivingDamage(LivingDamageEvent event) {
    // 1) 伤害来源 = 玩家（持升级法杖）或 Boss（持升级法杖）
    // 2) 伤害类型判定：魔法/法术类（damageSource.isMagic() 或 Goety 法术 DamageSource 家族）
    // 3) 若伤害来源方主手/副手为升级法杖 → event.setAmount(amount * (1 + 0.4))
}
```

- 判定细节：优先用 **`damageSource.getEntity()`（直接攻击者）**，兜底 `getDirectEntity()`；法术伤害大多直接实体为施法者；
- 类型过滤用 `damageSource.isMagic()` 命中大部分法术；Goety 自带的 `ModDamageSource` 家族（`OwnedDamageSource`/`NoKnockBackDamageSource` 等）多为魔法系 —— 若 `isMagic()` 覆盖不全，再按 `damageSource.getMsgId()` 前缀或 `instanceof` 白名单扩展；
- **防误伤**：仅当攻击者手持/装备升级法杖时生效；Boss 手持升级法杖时其法术同样 +40%（符合直觉：Boss 用升级版打你更疼，但正常流程 Boss 拿的是未升级原版）。

**③ 玩家手持升级法杖的"生效"总链路**

```
玩家装备升级法杖
  ├─ 10% 巫法：LivingEquipmentChangeEvent → SPELL_POTENCY MULTIPLY_TOTAL +0.1 modifier
  │            → 施法时 WandUtil.getStats 读属性 → potency ×1.1（实时，无冷却/前摇参与）
  └─ 40% 魔法伤害：LivingDamageEvent → 魔法/法术伤害 ×1.4
```

两个加成均为**常驻型**（无冷却、无施法前摇关联）—— 完全满足"自创一个不是冷却/施法前摇的加成"的约束。

### 5.4 非仪式召唤的 Boss（刷怪蛋路径）

- 刷怪蛋/指令生成的 Boss **无手持法杖** → 死亡不掉落升级法杖（正常，只有仪式召唤的 Boss 才携带）;
- `setGuaranteedDrop` 只在主手非空时生效，无副作用。

---

## 六、配置项清单（TunerCommonConfig 新增）

| 配置键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `ritualSoulCostPerSecond` | int | 1 | 仪式每秒灵魂费率（配方 `soulCost`；数据包可改，此为文档值） |
| `ritualDurationTicks` | int | 600 | 仪式时长 tick（数据包可改，此为文档值） |
| `wandWitchcraftBonus` | double | 0.10 | 升级法杖巫法加成（10% → 0.10） |
| `wandMagicDamageBonus` | double | 0.40 | 升级法杖魔法伤害加成（40% → 0.40） |
| `wandBonusStack` | boolean | true | 已有同键加成时叠加（true）或覆盖（false） |
| `wandUpgradeEnabled` | boolean | true | 总开关：关闭则 Boss 只掉原版法杖 |
| `wandBonusAppliesToBoss` | boolean | false | Boss 手持升级法杖时其伤害是否同样加成（默认否，防自伤放大） |

> 注意：仪式**材料配方**（4×下界之星等）不进代码配置 —— 配方是数据包文件，玩家直接改 JSON 即可（更符合模组惯例）。

---

## 七、边界与风险

| # | 风险 | 等级 | 缓解 |
|---|---|---|---|
| 1 | 跨 mod 注册 `goety:ritual_factory` 条目失败（注册表 disableSaving / 时序） | 中 | dev 环境首验；兜底 `RegisterEvent` 手动注册；再兜底：mixin 改 `RitualRecipe` 构造 factory 解析 |
| 2 | `result` 占位物品 `goety:jei_dummy/none` 在无 JEI 环境解析 | 低 | 该物品 Goety 自带（无 JEI 也注册）；官方配方同款写法 |
| 3 | `craftType:"magic"` 结构布局玩家难搭 | 低 | README 写清结构需求；`magic` 结构相对基础（相比 sabbath/adept_nether） |
| 4 | 仪式中结构破坏 3 秒即中断 | 低 | README 提示；Boss 战开始前玩家已站桩完成，不涉及战斗 |
| 5 | `LivingDamageEvent` 误伤非魔法伤害 | 中 | `isMagic()` + 攻击者持杖双条件；附魔伤害可能误判，配置可调关闭 |
| 6 | 玩家侧 `SPELL_POTENCY` modifier 重复叠加/残留 | 中 | 固定 UUID + 事件成对增删；`LivingEquipmentChangeEvent` 槽位快照比对 |
| 7 | 与 Configured 配置界面联动 | 低 | 新键沿用 `TunerCommonConfig` 现有 builder 注释格式 |
| 8 | 双副本同步 | 低 | 照旧：编译副本 `D:\测试\...` 与同步副本 `D:\tiaolvshi\...` diff 一致后打包 |

---

## 八、编码任务拆解（审阅通过后执行）

| # | 任务 | 涉及文件（新增/修改） |
|---|---|---|
| 1 | 新增 `TunerSummonRitual`（覆写 finish + initSummoned，手持法杖注入） | `ritual/TunerSummonRitual.java`（新） |
| 2 | 注册自定义 factory（`goety:ritual_factory` DeferredRegister） | `init/ModBusEvents.java` 或 `ritual/RitualRegistration.java`（新） |
| 3 | 配方 JSON（`data/goety/recipes/tuner_boss_ritual.json`） | `resources/data/goety/recipes/tuner_boss_ritual.json`（新） |
| 4 | 掉落升级法杖（LivingDropsEvent + NBT 保留/叠加） | `combat/CombatEvents.java`（改）或新 `ritual/WandUpgradeEvents.java` |
| 5 | 10% 巫法加成落地（装备事件 + SPELL_POTENCY modifier） | `combat/WandBonusHandler.java`（新） |
| 6 | 40% 魔法伤害加成落地（LivingDamageEvent） | 同上 |
| 7 | 配置项 7 项 + 注释 | `config/TunerCommonConfig.java`（改） |
| 8 | 文档：README 仪式流程说明 | `README.md`（改） |
| 9 | 编译验证 + 双副本同步 + 版本号 bump（0.7.0） | 构建流程（沿用红线） |

---

## 九、验证清单（dev 环境）

- [ ] `goetytuner:tuner_boss_summon` 出现在 `goety:ritual_factory` 注册表（`/forge_registry_name` 或日志）；
- [ ] 配方 JSON 被 RecipeManager 加载（日志无 parse 错误）；
- [ ] 按 4.5 流程实搭：右键激活成功、仪式倒计时、灵魂每秒-1、基座之星逐个消失；
- [ ] 中途拆结构 → 3 秒后中断、法杖副本掉落回地上；
- [ ] 仪式完成 → Boss 手持**原 NBT 法杖**（附魔/无限耐久/聚晶核对）；
- [ ] 击杀 → 掉落升级法杖：NBT 全保留 + `goetytuner` 新键；
- [ ] 玩家持升级法杖施法：伤害面板 potency ×1.1（对比法杖）；
- [ ] 玩家持升级法杖的魔法伤害 ×1.4（对比普通法杖）；
- [ ] 二次掉落同款法杖（已有加成）→ 数值叠加；
- [ ] 刷怪蛋 Boss：无手持、无升级掉落，行为不回归。

---

*本文档待用户审阅。确认后按第八节拆解执行（任务 #118），完成后复用构建部署流程（任务 #119）。*
