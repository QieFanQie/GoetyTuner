/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.entity.TunerBoss;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 【2026-08-19 第十九轮】调律师披风渲染层（深紫披风，原版渲染方案）。
 *
 * <p>渲染逻辑：
 * <ul>
 *   <li>跟随躯干（{@code getParentModel().body}）的 translateAndRotate，躯干前倾时披风随动；</li>
 *   <li>绕披风顶边（肩线）X 轴旋转产生摆动：
 *       行走时向后飘（limbSwingAmount 比例）+ 静止呼吸微摆（cos 时间正弦）；</li>
 *   <li>独立贴图 textures/entity/tuner_cape.png（64x32），RenderType.entitySolid。</li>
 * </ul>
 */
public class TunerCapeLayer extends RenderLayer<TunerBoss, TunerModel> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(GoetyTuner.MOD_ID, "textures/entity/tuner_cape.png");

    private final TunerCapeModel model;

    public TunerCapeLayer(RenderLayerParent<TunerBoss, TunerModel> parent, EntityModelSet modelSet) {
        super(parent);
        this.model = new TunerCapeModel(modelSet.bakeLayer(TunerCapeModel.LAYER_LOCATION));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, TunerBoss entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        poseStack.pushPose();
        // 跟随躯干朝向/倾斜
        this.getParentModel().body.translateAndRotate(poseStack);
        // 摆动：行走后飘 + 静止呼吸微摆（正值角度使下摆向 +z 即身后摆动）
        float walkSwing = limbSwingAmount * 20.0F;
        float idleSway = (Mth.cos(ageInTicks * 0.09F) + 1.0F) * 1.5F;
        poseStack.mulPose(Axis.XP.rotationDegrees(walkSwing + idleSway));

        VertexConsumer vc = buffer.getBuffer(RenderType.entitySolid(TEXTURE));
        this.model.getCape().render(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }
}
