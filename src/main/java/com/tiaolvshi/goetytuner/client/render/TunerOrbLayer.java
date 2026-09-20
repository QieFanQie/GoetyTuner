package com.tiaolvshi.goetytuner.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.entity.TunerBoss;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Three category indicators; animation state belongs to each entity, never the shared renderer. */
public class TunerOrbLayer extends RenderLayer<TunerBoss, TunerModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(GoetyTuner.MOD_ID,
            "textures/entity/tuner_orb.png");
    private static final RenderType CORE = RenderType.entitySolid(TEXTURE);
    private static final RenderType GLOW = RenderType.entityTranslucent(TEXTURE);
    private static final float[][] COLORS = {{1, 59 / 255F, 48 / 255F},
            {47 / 255F, 168 / 255F, 1}, {200 / 255F, 200 / 255F, 200 / 255F}};
    private final TunerOrbModel model;

    public TunerOrbLayer(RenderLayerParent<TunerBoss, TunerModel> parent, EntityModelSet models) {
        super(parent);
        model = new TunerOrbModel(models.bakeLayer(TunerOrbModel.LAYER_LOCATION));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, TunerBoss entity,
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
            float gain = 1 + glow * 0.8F;
            model.renderToBuffer(pose, buffers.getBuffer(CORE), LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, Math.min(1, color[0] * gain),
                    Math.min(1, color[1] * gain), Math.min(1, color[2] * gain), 1);
            if (glow > 0) {
                pose.scale(1.15F, 1.15F, 1.15F);
                model.renderToBuffer(pose, buffers.getBuffer(GLOW), LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, color[0], color[1], color[2], 0.25F * glow);
            }
            pose.popPose();
        }
    }
}
