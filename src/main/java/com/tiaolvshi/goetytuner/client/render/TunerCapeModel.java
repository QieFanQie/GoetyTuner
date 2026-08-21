/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.client.render;

import com.tiaolvshi.goetytuner.entity.TunerBoss;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 【2026-08-19 第十九轮】调律师披风模型（原版渲染方案，替代已移除的 GeckoLib 路线）。
 *
 * <p>单个 10 宽 × 16 高 × 1 深的盒子，挂在躯干背部（z=2.1，躯干背面 z=2.0），
 * 顶边对齐肩线（y=0），下摆到 y=16（盖过大腿上部）。摆动动画见 {@link TunerCapeLayer}。
 * 贴图：textures/entity/tuner_cape.png（64x32，深紫）。
 */
public class TunerCapeModel extends EntityModel<TunerBoss> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    new net.minecraft.resources.ResourceLocation(com.tiaolvshi.goetytuner.GoetyTuner.MOD_ID, "tuner_cape"),
                    "main");

    private final ModelPart cape;

    public TunerCapeModel(ModelPart root) {
        this.cape = root.getChild("cape");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("cape",
                CubeListBuilder.create()
                        .texOffs(1, 1)
                        .addBox(-5.0F, 0.0F, 2.1F, 10.0F, 16.0F, 1.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    public ModelPart getCape() {
        return cape;
    }

    @Override
    public void setupAnim(TunerBoss entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // 摆动在 TunerCapeLayer 内直接对 PoseStack 施加（需要躯干级旋转，非部件级）
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.cape.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
