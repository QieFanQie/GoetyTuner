package com.tiaolvshi.goetytuner.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.entity.TunerBoss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashMap;
import java.util.Map;

/** Bounded active-event list; never scans the world's entities. */
@Mod.EventBusSubscriber(modid = GoetyTuner.MOD_ID, value = Dist.CLIENT)
public final class AccentWaveRenderer {
    public static final int RINGS = 10, SEGMENTS = 32;
    public static final float DELAY = 2, LIFE = 16, DURATION = (RINGS - 1) * DELAY + LIFE;
    // entityTranslucent installs NO_CULL itself, including during deferred batch submission.
    private static final RenderType WAVE = RenderType.entityTranslucent(
            new ResourceLocation(GoetyTuner.MOD_ID, "textures/entity/accent_wave.png"));

    /**
     * 【0.0.12】一次涟漪的完整状态：**锚定在触发瞬间的坐标**。
     *
     * <p>0.0.10 只存了 `startTick`，渲染时每帧去读 Boss 当前位置，于是涟漪**跟着 Boss 跑**；
     * 而调律师在每次锁血触发时都会**强制瞬移**（`onLockTriggered` → `tryTeleportNear`），
     * 跟着跑会让"由内向外荡开的声波"看起来被拖着走，观感完全不对。
     * 现在触发时就把脚下的世界坐标记下来，整条波都在这个固定点上播完。
     */
    private record Wave(long startTick, double x, double y, double z) {
    }

    private static final Map<Integer, Wave> ACTIVE = new HashMap<>();
    private static ClientLevel activeLevel;

    private static void checkLevel(ClientLevel level) {
        if (activeLevel != level) {
            ACTIVE.clear();
            activeLevel = level;
        }
    }

    public static void trigger(int entityId) {
        ClientLevel level = Minecraft.getInstance().level;
        checkLevel(level);
        if (level != null && level.getEntity(entityId) instanceof TunerBoss boss) {
            // Retrigger replaces, never stacks. 坐标取"触发瞬间"的脚下位置（实体位置=脚底）。
            ACTIVE.put(entityId, new Wave(level.getGameTime(), boss.getX(), boss.getY(), boss.getZ()));
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientLevel level = Minecraft.getInstance().level;
        checkLevel(level);
        if (level == null || ACTIVE.isEmpty()) return;
        // 【0.0.12】只按时间清理。**不再**因为 Boss 死亡/被移除/离开视野就掐掉涟漪：
        // 波已经锚定在固定坐标上，与实体无关，应当把 34 tick 播完（否则 Boss 一死波就凭空消失）。
        long now = level.getGameTime();
        ACTIVE.entrySet().removeIf(entry -> now - entry.getValue().startTick() >= DURATION);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        checkLevel(mc.level);
        if (mc.level == null || ACTIVE.isEmpty()) return;
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(WAVE);
        PoseStack pose = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        long now = mc.level.getGameTime();
        for (var entry : ACTIVE.entrySet()) {
            Wave wave = entry.getValue();
            float elapsed = now - wave.startTick() + partial;
            if (elapsed < 0 || elapsed >= DURATION) continue;
            pose.pushPose();
            // 锚点固定：worldPos - cameraPos（与 Goety 本体 PrismaBeamRenderer 的写法一致）
            pose.translate(wave.x() - camera.x, wave.y() - camera.y + 0.01, wave.z() - camera.z);
            for (int ring = RINGS - 1; ring >= 0; ring--) {
                float progress = (elapsed - ring * DELAY) / LIFE;
                if (progress <= 0 || progress >= 1) continue;
                float envelope = (float) Math.sin(Math.PI * progress);
                float alpha = 0.32F * envelope;
                float radius = 0.6F + ring * 0.6F;
                for (int segment = 0; segment < SEGMENTS; segment++) {
                    double a = segment * Math.PI * 2 / SEGMENTS;
                    double b = (segment + 1) * Math.PI * 2 / SEGMENTS;
                    float ax = (float) Math.cos(a) * radius, az = (float) Math.sin(a) * radius;
                    float bx = (float) Math.cos(b) * radius, bz = (float) Math.sin(b) * radius;
                    vertex(vertices, pose, ax, 0, az, alpha * 0.35F);
                    vertex(vertices, pose, bx, 0, bz, alpha * 0.35F);
                    vertex(vertices, pose, bx, height(b, elapsed, ring, envelope), bz, alpha);
                    vertex(vertices, pose, ax, height(a, elapsed, ring, envelope), az, alpha);
                }
            }
            pose.popPose();
        }
        buffers.endBatch(WAVE);
    }

    private static float height(double angle, float elapsed, int ring, float envelope) {
        double wave = Math.sin(3 * angle + elapsed * 0.10 - ring * 0.28);
        // Unequal peaks/troughs; absolute top <= 0.01 + 0.56 * 1.4 = 0.794 blocks.
        return (float) (0.56 * envelope * (1 + wave * (wave > 0 ? 0.40 : 0.27)));
    }

    private static void vertex(VertexConsumer vertices, PoseStack pose, float x, float y, float z, float alpha) {
        vertices.vertex(pose.last().pose(), x, y, z).color(1F, 1F, 1F, alpha).uv(0, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.last().normal(), 0, 1, 0).endVertex();
    }
}
