# 本体贴图精修 · 0.0.15

保留 64×64 原版 UV、紫黑礼服、紫色袖口/裤靴、浅色领巾和青色胸饰。调整翻领边缘、衣袖明暗、袖口细边、裤缝与靴口层次。头部和帽层逐像素保留；模型与动画无改动。

- 游戏资源：`../src/main/resources/assets/goetytuner/textures/entity/tuner.png`
- 精修前备份：`tuner-before-refinement.png`
- 内置 image_gen 原稿：`body-refined-source.png`
- 可复现装配：`powershell -ExecutionPolicy Bypass -File art/assemble-refinement.ps1`
- 平面正背面预览：`preview.png`，由 `preview-assets.ps1` 生成，不是游戏截图。

生成图没有严格保持矩形位置，因此重新测量图块，按最近邻采样装配到原有 UV。腿部背面采用裤料图块，避免误用带袖扣的图块。生成图的头部不参与成品装配。

使用 imagegen 技能的内置工具模式；最终提示词如下：

> Edit this existing Minecraft purple conductor clothing texture atlas, precise-object-edit. Keep EXACT image composition, rectangular patch positions, sizes, gaps and silhouettes of this 1263x1246 source. Refine ONLY existing clothing patches in place, same dark black/plum/violet palette and crisp low-resolution pixel art style. Make tailored jacket lapels clearer with narrow muted lavender edge highlights, subtle structured dark-purple fabric folds, clean ivory cravat, existing cyan chest chain a delicate cyan chain, waistcoat buttons, neat cuff piping and single cuff button, restrained trouser seams and crisp boot trim. Preserve overall dark values, purple cuffs and boots, no gold, no new symbols, no elaborate embroidery, no realistic texture or soft gradients. Use large readable pixel clusters so refinement survives sampling each sleeve/leg rectangle to 4x12 pixels and torso to 8x12 pixels. Do not move or resize any rectangle. Upper head patches unchanged. Output the atlas only, transparent background, no text or previews.

验证：`python art/verify_assets.py` 已通过，包括 PNG 格式、头部一致、全部身体 UV 面不透明与现有其他资源检查。尚未进行游戏内实测，也未部署到游玩实例。

独立对比本轮备份：修改 1247 个身体像素，整个头部 RGBA 与全图 alpha 蒙版均保持一致。JDK 17 离线 `gradlew.bat build --offline` 成功；成品 `build/libs/goetytuner-0.0.15.jar` 内贴图与源码资源逐字节一致，版本元数据已核对。
