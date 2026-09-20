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
import com.tiaolvshi.goetytuner.entity.TunerServant;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

/**
 * 【2026-08-20 第二十四轮】嘲讽蹲姿模型。
 *
 * <p>原版事实（forge 1.20.1-47.3.22 字节码核实）：
 * <ul>
 *   <li>{@code HumanoidModel.setupAnim} 内部已实现完整蹲姿动画（body.xRot=0.5、
 *       头/躯干/手臂下移、双腿前移），但只读 {@code crouching} 字段；</li>
 *   <li>只有 {@code PlayerRenderer} 会把实体 pose 写入该字段，
 *       {@code HumanoidMobRenderer} 不会——Mob 设 Pose.CROUCHING 默认无视觉效果。</li>
 * </ul>
 *
 * <p>本子类只做一件事：setupAnim 前把服务端 {@link Pose#CROUCHING}（原版 DATA_POSE
 * 数据同步，零额外网络包）映射到 {@code crouching} 字段，原版蹲姿动画即自动生效。
 * 资源占用：每帧一次枚举比较，可忽略。
 *
 * <p>【0.0.18】改为**泛型**（{@code TunerModel<T extends LivingEntity>}）：
 * 原来写死成 {@code HumanoidModel<TunerBoss>}，而调律师仆从
 * （{@link TunerServant}，与 Boss {@link TunerBoss} 同形同贴图）也要用这套模型。
 * 泛型化之后两个渲染器各自实例化 {@code TunerModel<TunerBoss>} / {@code TunerModel<TunerServant>}，
 * 模型代码仍然只有一份。
 */
public class TunerModel<T extends LivingEntity> extends HumanoidModel<T> {

    public TunerModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.crouching = entity.hasPose(Pose.CROUCHING);
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }
}
