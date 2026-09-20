/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.combat;

import com.Polarice3.Goety.api.entities.IOwned;
import com.tiaolvshi.goetytuner.config.TunerCommonConfig;
import com.tiaolvshi.goetytuner.network.SAccentWavePacket;
import com.tiaolvshi.goetytuner.network.TunerNetwork;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * 【0.0.18】「调律师重音涟漪」的**唯一实现**。
 *
 * <p>在此之前，这套效果只存在于 {@code TunerBoss#accentKnockbackPulse} 内部；
 * 0.0.18 新增的「调律波纹聚晶」（{@link com.tiaolvshi.goetytuner.focus.RippleSpell}）要求
 * **与铺垫期重音完全同款**，所以把它抽成本类，由 Boss 与聚晶**共用同一份代码**
 * ——而不是抄一份出来（本项目红线：同一职责只允许一处实现）。
 *
 * <p>一次完整的「重音涟漪」= 四件事，各自可通过配置关掉：
 * <ol>
 *   <li>{@link #knockback} 把 {@link #RADIUS} 格内的生物沿径向推开（力量由调用方给）；</li>
 *   <li>{@link #particles} 三波 END_ROD 同心冲击环 + NOTE 音符爆发（{@code music.accentParticles}）；</li>
 *   <li>{@link #wave} 逐层非对称径向声波涟漪（{@code music.accentWave}）；</li>
 *   <li>{@link #chime} 紫水晶提示音（{@code music.accentSound}，音调由调用方给）。</li>
 * </ol>
 *
 * <p><b>Boss 与聚晶的差别只有两处</b>（都由调用方决定，本类不做区分）：
 * 击退力量/音调（Boss 按阶段取 0.4/0.8/1.2 与 0.9/1.4/0.6；聚晶固定取铺垫期的 0.4 / 0.9），
 * 以及"多排除一批友好目标"（玩家施法时不该把自己的仆从/宠物轰飞，Boss 无此顾虑 ⇒ 传 null）。
 */
public final class AccentRipple {

    /** 击退作用半径（格）——与 0.0.10 起的实现一致。 */
    public static final double RADIUS = 8.0D;

    /**
     * 声波涟漪包的广播半径（格）。
     *
     * <p>0.0.10 用的是 {@code PacketDistributor.TRACKING_ENTITY}（谁在追踪 Boss 就发给谁）；
     * 0.0.18 新增的「玩家在地面上放涟漪」没有实体可作为追踪锚点（涟漪锚定在**世界坐标**上），
     * 所以统一改成"按锚点半径广播"——两种来源走同一条路径，也就只剩一处实现。
     * 涟漪本身只有几格大，64 格足够。
     */
    public static final double WAVE_BROADCAST_RANGE = 64.0D;

    private AccentRipple() {
    }

    // ================= 1. 击退 =================

    /**
     * 以 {@code source} 为中心，把 {@link #RADIUS} 格内的存活生物沿径向推出。
     *
     * @param strength    水平击退力量（Boss 铺垫 0.4 / 低谷 0.8 / 高潮 1.2；聚晶固定 0.4）
     * @param alsoExclude 额外的排除条件（可为 null）。Boss 传 null；
     *                    玩家施放聚晶时传 {@code e -> isFriendlyTo(caster, e)}，
     *                    避免把自己的仆从/宠物一起轰飞。
     */
    public static void knockback(ServerLevel level, LivingEntity source, double strength,
                                 @Nullable Predicate<LivingEntity> alsoExclude) {
        List<LivingEntity> around = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(source.blockPosition()).inflate(RADIUS),
                e -> e != source && e.isAlive()
                        && !(e instanceof Player p && (p.isSpectator() || p.isCreative()))
                        && (alsoExclude == null || !alsoExclude.test(e)));
        for (LivingEntity e : around) {
            Vec3 dir = e.position().subtract(source.position()).normalize();
            e.push(dir.x * strength, strength * 0.5D, dir.z * strength);
            e.hurtMarked = true; // 速度同步
        }
    }

    /** 目标是否站在 {@code caster} 这一边（自己、自己的宠物、自己的仆从）。 */
    public static boolean isFriendlyTo(LivingEntity caster, LivingEntity other) {
        if (other == caster) {
            return true;
        }
        if (other instanceof OwnableEntity ownable && ownable.getOwner() == caster) {
            return true;
        }
        // Goety 的仆从走 IOwned 的 trueOwner（Summoned 同时实现 OwnableEntity 与 IOwned，两者都查一遍最稳）
        return other instanceof IOwned owned && owned.getTrueOwner() == caster;
    }

    // ================= 2. 粒子 =================

    /** 三波同心冲击环（END_ROD）+ 音符爆发（NOTE）。由 {@code music.accentParticles} 开关控制。 */
    public static void particles(ServerLevel level, LivingEntity source) {
        if (!TunerCommonConfig.ACCENT_PARTICLES.get()) {
            return;
        }
        double px = source.getX(), py = source.getY() + 1.0D, pz = source.getZ();
        for (int wave = 0; wave < 3; wave++) {
            double r = 2.0D + wave * 2.0D;
            int n = 8 + wave * 6;
            for (int i = 0; i < n; i++) {
                double ang = (Math.PI * 2.0D * i) / n + wave * 0.35D;
                level.sendParticles(ParticleTypes.END_ROD,
                        px + Math.cos(ang) * r, py + wave * 0.3D, pz + Math.sin(ang) * r,
                        1, 0.0D, 0.03D, 0.0D, 0.0D);
            }
        }
        for (int i = 0; i < 12; i++) {
            double noteSpeed = source.getRandom().nextDouble();
            level.sendParticles(ParticleTypes.NOTE,
                    px, py + 1.2D, pz, 1, 0.4D, 0.5D, 0.4D, noteSpeed);
        }
    }

    // ================= 3. 声波涟漪 =================

    /** 在实体脚下的世界坐标触发一条涟漪（Boss 用）。由 {@code music.accentWave} 开关控制。 */
    public static void wave(ServerLevel level, LivingEntity source) {
        waveAt(level, source.getX(), source.getY(), source.getZ());
    }

    /**
     * 在任意世界坐标触发一条涟漪。
     *
     * <p>锚点是**触发瞬间的坐标**：0.0.12 起涟漪就固定在触发点上播完
     * （调律师每次锁血都会强制瞬移，跟着实体跑会让"由内向外荡开的声波"被拖着走）。
     */
    public static void waveAt(ServerLevel level, double x, double y, double z) {
        if (!TunerCommonConfig.ACCENT_WAVE.get()) {
            return;
        }
        SAccentWavePacket packet = new SAccentWavePacket(x, y, z);
        double r2 = WAVE_BROADCAST_RANGE * WAVE_BROADCAST_RANGE;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(x, y, z) < r2) {
                TunerNetwork.sendToPlayer(packet, p);
            }
        }
    }

    // ================= 4. 提示音 =================

    /** 紫水晶提示音（原版音效，无需音频资源）。由 {@code music.accentSound} 开关控制。 */
    public static void chime(ServerLevel level, LivingEntity source, float pitch) {
        if (!TunerCommonConfig.ACCENT_SOUND.get()) {
            return;
        }
        level.playSound(null, source.getX(), source.getY(), source.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.6F, pitch);
    }
}
