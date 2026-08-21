/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.client.render;

import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.entity.TunerBoss;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 调律师渲染器（【2026-08-19 第十九轮】原版渲染方案定稿）。
 *
 * <p>GeckoLib 路线已废弃（Goety 2.5.56.5 本体零 geckolib 引用，本项目亦未使用）。
 * 当前实现：
 * <ul>
 *   <li>原版 {@link HumanoidModel}（玩家体型）+ 64x64 自定义贴图：
 *       躯干/四肢深紫、头部内亮外深径向渐变淡蓝、帽子层贴图透明不显示；</li>
 *   <li>{@link TunerCapeLayer}：深紫披风（独立模型层 + 64x32 贴图，跟随躯干+摆动）；</li>
 *   <li>不挂 HumanoidArmorLayer（等效护甲16不显示，天然无冲突）。</li>
 * </ul>
 * 后续打磨：施法抬臂姿态、二阶段破碎形态变体贴图、传送粒子。
 */
public class TunerRenderer extends HumanoidMobRenderer<TunerBoss, TunerModel> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(GoetyTuner.MOD_ID, "textures/entity/tuner.png");

    public TunerRenderer(EntityRendererProvider.Context ctx) {
        // 【第二十四轮】TunerModel：把服务端 Pose.CROUCHING 映射到 crouching 字段，
        // 使嘲讽蹲起（TunerBoss.tickTaunt）获得原版蹲姿动画（HumanoidModel 自带分支）
        super(ctx, new TunerModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        // 深紫披风渲染层（模型层定义注册见 ClientSetup#onRegisterLayers）
        this.addLayer(new TunerCapeLayer(this, ctx.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(TunerBoss entity) {
        return TEXTURE;
    }
}
