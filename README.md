# Goety Tuner · 调律师

诡厄巫法（Goety）附属Boss模组 —— 1.20.1 Forge。

人形无头指挥家「调律师」，以音乐（铺垫/高潮/低谷）驱动战斗节奏，
轮番演奏诡厄巫法及其附属注册的全部聚晶，并带有自学习式聚晶评分系统。

**开发文档：[DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md)**

## 作者 / 团队

| | |
|---|---|
| **作者** | toniat0 & vibe-coding |
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

- `focus/` 聚晶池系统（分类/评分/轮盘/冷却/LLM自动配置）
- `entity/` Boss实体与音乐阶段控制器
- `combat/` 动态评分（伤害DPS/召唤物二维追踪）
- `network/` 音乐同步包（服务端权威）
- `client/` 节奏条HUD、配置界面、渲染器占位
