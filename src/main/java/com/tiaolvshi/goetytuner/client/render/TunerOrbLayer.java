package com.tiaolvshi.goetytuner.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.entity.OrbHighlightSource;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Three category indicators; animation state belongs to each entity, never the shared renderer.
 *
 * <p>【0.0.18】改为**泛型 + 面向接口**：高亮数据来自 {@link OrbHighlightSource}
 * （Boss 与调律师仆从都实现它），渲染层因此不再依赖具体的实体类，
 * 一份代码同时服务 {@code TunerRenderer} 与 {@code TunerServantRenderer}。
 */
public class TunerOrbLayer<T extends LivingEntity & OrbHighlightSource> extends RenderLayer<T, TunerModel<T>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(GoetyTuner.MOD_ID,
            "textures/entity/tuner_orb.png");
    private static final RenderType CORE = RenderType.entitySolid(TEXTURE);
    /**
     * 【0.0.13】高亮光晕用的**自发光**渲染类型。
     *
     * <p>`entityTranslucentEmissive` 在着色器层面忽略光照（等价满亮度），所以光晕壳无论昼夜、
     * 无论立方体处于多暗的环境都会稳定发亮——这正是"发光"该有的表现。
     * （实证：它内部走 `ENTITY_TRANSLUCENT_EMISSIVE` → `lambda$static$8`，与 `ENTITY_TRANSLUCENT` 一样
     * **无条件 `NO_CULL`**，所以放大后的壳从任何角度都可见、不会有背面被剔除的空洞。）
     */
    private static final RenderType GLOW = RenderType.entityTranslucentEmissive(TEXTURE);
    private static final float[][] COLORS = {{1, 59 / 255F, 48 / 255F},
            {47 / 255F, 168 / 255F, 1}, {200 / 255F, 200 / 255F, 200 / 255F}};
    private final TunerOrbModel model;

    public TunerOrbLayer(RenderLayerParent<T, TunerModel<T>> parent, EntityModelSet models) {
        super(parent);
        model = new TunerOrbModel(models.bakeLayer(TunerOrbModel.LAYER_LOCATION));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || !entity.isAlive()) return;
        for (int i = 0; i < 3; i++) {
            float glow = entity.getOrbHighlight(i, partialTick);
            double phase = ageInTicks * Math.PI * 2 / 80 + i * Math.PI * 2 / 3;
            pose.pushPose();
            // Humanoid render coordinates have downward-positive Y; feet are at 1.5.
            pose.translate(Math.cos(phase) * 1.05, 0.30 + Math.sin(phase * 2) * 0.06,
                    Math.sin(phase) * 1.05);
            pose.mulPose(Axis.YP.rotationDegrees(ageInTicks * 9 + i * 120));
            pose.mulPose(Axis.XP.rotationDegrees(ageInTicks * 6 + i * 40));
            pose.scale(1.2F, 1.2F, 1.2F); // 4/16 * 1.2 = 0.30 blocks.
            float[] color = COLORS[i];
            // 【0.0.13】高亮改为"提亮 + 向白插值"。
            // 原因：原实现是 `min(1, color * (1 + glow*0.8))`，而红/蓝/灰三色里
            // 分量本来就接近 1（如红 1.0/0.231/0.188）——乘法被 `min(1, …)` 截断后，
            // 红的 R 通道纹丝不动，只有 G/B 微升，观感上"高亮几乎没变化"。
            // 现在额外叠加一项 `glow * 0.4` 逐通道向白色靠拢，高亮时会明显"变白变亮"。
            float lit = 1F + glow * 0.8F;
            model.renderToBuffer(pose, buffers.getBuffer(CORE), LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    Math.min(1F, color[0] * lit + glow * 0.40F),
                    Math.min(1F, color[1] * lit + glow * 0.40F),
                    Math.min(1F, color[2] * lit + glow * 0.40F), 1);
            if (glow > 0.01F) {
                // 【0.0.13】"发光"效果：两层自发光外壳（内密外疏）形成光晕。
                // 用几何外壳而非原版的发光描边——MC 的发光描边是**整个实体**级别的
                // framebuffer 后处理（`OutlineBufferSource`），只能整只 Boss 一起描边，
                // 没法只描一颗立方体；外壳方案可以精确地只让"高亮的那一颗"发光。
                pose.pushPose();
                pose.scale(1.30F, 1.30F, 1.30F);
                model.renderToBuffer(pose, buffers.getBuffer(GLOW), LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, color[0], color[1], color[2], 0.36F * glow);
                pose.scale(1.35F, 1.35F, 1.35F);
                model.renderToBuffer(pose, buffers.getBuffer(GLOW), LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, color[0], color[1], color[2], 0.18F * glow);
                pose.popPose();
            }
            pose.popPose();
        }
    }
}
