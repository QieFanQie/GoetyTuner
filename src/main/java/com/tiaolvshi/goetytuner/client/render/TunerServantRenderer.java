/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.client.render;

import com.tiaolvshi.goetytuner.entity.TunerServant;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 【0.0.18】调律师仆从渲染器。
 *
 * <p>与 Boss 渲染器 {@link TunerRenderer} 唯一的差别是实体类型；
 * 模型（{@link TunerModel}，泛型化后与 Boss 共用）、贴图（同一张
 * {@code textures/entity/tuner.png}）、披风层、三颗悬浮立方体层**全部复用同一份实现**——
 * 这正是诡厄巫法里"仆从与本体同形"的做法（僵尸仆从用的就是僵尸的模型与贴图）。
 */
public class TunerServantRenderer extends HumanoidMobRenderer<TunerServant, TunerModel<TunerServant>> {

    public TunerServantRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new TunerModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(new TunerCapeLayer<>(this, ctx.getModelSet()));
        this.addLayer(new TunerOrbLayer<>(this, ctx.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(TunerServant entity) {
        return TunerRenderer.TEXTURE;
    }
}
