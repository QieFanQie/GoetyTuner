package com.tiaolvshi.goetytuner.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.entity.TunerBoss;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;

public class TunerOrbModel extends EntityModel<TunerBoss> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            new ResourceLocation(GoetyTuner.MOD_ID, "tuner_orb"), "main");
    private final ModelPart cube;

    public TunerOrbModel(ModelPart root) { cube = root.getChild("cube"); }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("cube", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2, -2, -2, 4, 4, 4), PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void setupAnim(TunerBoss entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) { }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay,
                               float red, float green, float blue, float alpha) {
        cube.render(pose, vertices, light, overlay, red, green, blue, alpha);
    }
}
