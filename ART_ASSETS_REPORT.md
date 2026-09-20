# 美术交付 · 0.0.10 · 2026-09-20

已完成 TASK_ART_ASSETS 的 A0/A1/A2/B/C 资源与代码。构建、部署和人工验收状态见本文末尾。

## 资源与最终参数

- 身体：`textures/entity/tuner.png`，64×64 RGBA；按用户身体参考制作紫黑外套、浅紫 V 领、紫色内衬/手套/裤靴与青蓝链饰。受原版 8×12 躯干精度限制，按钮与链饰已像素化简化。保持原版模型，没有增加长衣摆几何。前 16 行（整个头部与帽层）RGBA 与原图逐像素相同；其余基础身体六面不透明。
- 披风：64×32 RGBA；8 条，每条 2 像素高。色阶依次 `#1E0D32 #38204D #523367 #6C4882 #875E9E #A078BA #BA96D8 #D4B6F2`。侧面 y=2…17，顶面 x=2…11/y=1，底面 x=12…21/y=1。**原版盒子的顶底面都在侧面条带上方**，任务书的“底面在下方横带”说明不准确，按模型实际 UV 实现。
- 立方体：16×16 中性灰白描边贴图；边长 0.30 格，公转半径 1.05 格，离脚约 1.20 格，上下浮动 ±0.06 格。三颗相差 120°，公转 80 tick/4 秒；自转 Y 40 tick/2 秒，X 60 tick/3 秒。红 `#FF3B30`、蓝 `#2FA8FF`、灰 `#C8C8C8`，全部使用满亮度照明。施法 RGB 增益至 1.8（通道值限幅为 1），附 1.15 倍壳体、峰值 alpha 0.25；3 tick 过渡。
- 高亮同步：INT 位掩码 ATTACK/DEFENSE/SUMMON/OTHER 对应 bit0…3。分类计数保留同类并行施法；按聚晶对象身份去重结束回调，避免完成后异常再进入失败回调时误减另一个通道。客户端每实体保存插值状态，不依赖 FPS 或共享渲染器状态。
- 声波：10 环、32 角分段，半径 0.6…6.0 格，间距 0.6；相邻延迟 2 tick，每环升 8/落 8 tick，总时长 34 tick。淡白，顶部 alpha 峰值 0.32，底部为顶部 0.35 倍。`h=0.56*sin(pi*p)*(1+sin(3θ+0.10t−0.28i)*a)`，峰侧 a=0.40、谷侧 a=0.27。地面偏移 0.01，绝对最高 0.794 格。每次触发覆盖同实体旧波，不叠加；死亡、移除、换世界及超时清理。
- 声波 RenderType 缓存且使用 `entityTranslucent` 内建 `NO_CULL`，确保批次真正提交时也双面可见；完整提交 UV/overlay/light/normal。无活跃波时立即返回，不扫描世界实体。
- `music.accentWave=true`；`music.accentParticles=false`，旧粒子作为手动兼容选项保留。配置总数 61，music 12 项。已有配置的 `accentParticles=true` 不会被 Forge 默认值覆盖；部署脚本只迁移此视觉开关并保留其余用户配置。
- 刷怪蛋选择**方案 1**：原版 `minecraft:item/template_spawn_egg`，主色 `0x8A2BE2`、副色 `0x2FA8FF`。直接继承原版轮廓、斑点和染色机制；删除无人引用的自定义蛋贴图。
- 新增客户端包 id=3，限服务端→客户端。因为新增实体同步字段/包，网络协议提升为 2.0 并要求双方一致，避免旧客户端错误解码；联机双方都需更新 0.0.10。

## 制作记录

使用 **imagegen 技能 / 内置 image_gen**，未使用 API/CLI 后备。生成原稿保存在 `art/body-source.png`；`art/tuner-before.png` 是头部校验基准。生成工具实际输出 1263×1246 图稿，没有满足请求的 64×64 UV，因此用 `art/assemble-assets.ps1` 将生成服装图块最近邻采样装配到正确 UV，并复制原头部；简单几何披风/立方体/白纹理由同脚本精确生成。游戏只加载 `src/main/resources` 中最终 PNG。

最终提示词（输入 1=原皮肤，输入 2=用户身体参考）：

> Edit image 1 Minecraft 64x64 classic Steve skin UV texture atlas. Image 2 reference BODY ONLY: purple-black long tailored coat, pale lavender V collar, purple waistcoat with dark buttons, cyan blue chain on viewer right chest, dark purple sleeves black cuffs purple gloves, purple trousers and boots. Output a flat exact 64x64 RGBA PNG skin atlas (not a 3D render), transparent unused UV regions. Preserve image 1 entire top 16 rows (head and transparent hat) EXACTLY unchanged. Only repaint body/arms/legs vanilla classic 4px arms UV regions below y16. Sharp deliberate single pixel detail, no lighting effects, no labels, no grid, no background. Torso front x20..27 y20..31, right arm front x44..47 y20..31, right leg front x4..7 y20..31, left arm front x36..39 y52..63, left leg front x20..23 y52..63. Match reference clothing across all faces. Keep 64x64 dimensions.

预览：[`art/preview.png`](art/preview.png)，展示正面、背面、披风与 UV；这是平面拼接验收图，**不是游戏截图**。

## 验证与待验收

- 已通过 `python art/verify_assets.py`：PNG 尺寸/RGBA/无交错/无 zTXt、头部逐像素一致、身体/披风/立方体六面覆盖、披风 8 阶单调亮度、原版刷怪蛋模型。
- 验证脚本直接提取生产 Java 方法，用 JDK 17 编译执行：同类/异类并行施法、重复/未知结束回调均通过；采样波顶最大 0.793999 格（数学上限 0.794）。
- 击退与重音音效代码和 HEAD 逐字比较一致；未修改震颤逻辑、依赖或 logo。
- 首轮 JDK 17 `build --offline` 成功；只有原有 3 条 Forge API 弃用警告，没有新增警告。
- **最终构建/部署结果（由接手整合的 agent 于 2026-09-20 补记完成，非本报告作者）**：
  - 构建位置改为**主副本 `D:\tiaolvshi\goety-tuner` 就地构建**（该目录自带 `libs/` 4 个依赖 jar + gradle wrapper；用户已决定不再维护双副本）。
    命令 `.\gradlew.bat clean build`，**BUILD SUCCESSFUL in 1m46s**，仍然只有上述原有 3 条弃用警告、**无新增**。
  - 产物 **`build/libs/goetytuner-0.0.10.jar`：1,750,152 B，md5 `5C05779CEC4CD4765BD2510EB4D4A6ED`，jar 内 101 条目**
    （0.0.9 的 97 + 本轮的 4 个新类；`mods.toml` 内 `version="0.0.10"`、中文 credits 完好）。
  - **已部署**到游玩实例 `D:\落幕曲&原版&灾厄巫咒\.minecraft\versions\测试\mods\`，旧 0.0.9 已删除（该目录下只有这一个 goetytuner jar）。
    部署时游戏未运行，**首次进游戏即会加载 0.0.10**，无需特意重启。
  - **接手方另做的独立核验**（不依赖本报告的 `art/verify_assets.py`）：对 `git HEAD`(0.0.9) 取基准逐像素比对确认**头部区 y0..15 全宽 0 像素差异**；
    jar 内 4 张贴图 + 刷怪蛋模型与源码**逐字节一致**；`javap` 实证 `ENTITY_TRANSLUCENT` 无条件 `NO_CULL`；
    并以 Goety 本体 `PrismaBeamRenderer` 交叉验证相机平移写法为 `translate(worldPos − cameraPos)`，与本轮 `AccentWaveRenderer` 一致。
  - **配置迁移已执行**：游玩实例 toml 由 `accentParticles=true` 改为 `false` 并新增 `accentWave=true`
    （`accentParticles` 是**已存在键**，Forge 不会用新默认值回填，不改会导致新旧特效同现；备份 `.bak-before-0.0.10-migration`）。
- **尚未进行游戏画面验收**：披风摆动、三颗立方体轨道与夜间高亮、高潮多通道实战、透明波与地形/水面/着色器交互、原版蛋并排效果需进入游戏验证。离线验证不能替代这些实测。
- 发现但未修改的既有边界：`CastChannel` 的开始回调之后仍有日志调用，异常时外层兜底不平衡回调；实际第三方法术错误大多发生在开始回调前。本轮没有改动施法流程。

游戏验收：`/summon goetytuner:tuner`、`/give @p goetytuner:tuner_spawn_egg`。分别观察闲置、单通道、高潮并行与中断；从正背/侧/仰角观察披风；旧配置若未迁移，手动设置 accentWave=true、accentParticles=false 后重启。
