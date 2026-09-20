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
import net.minecraft.util.Mth;
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
    private static final Map<Integer, Long> ACTIVE = new HashMap<>();
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
        if (level != null && level.getEntity(entityId) instanceof TunerBoss) {
            ACTIVE.put(entityId, level.getGameTime()); // Retrigger replaces, never stacks.
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientLevel level = Minecraft.getInstance().level;
        checkLevel(level);
        if (level == null || ACTIVE.isEmpty()) return;
        ACTIVE.entrySet().removeIf(entry -> level.getGameTime() - entry.getValue() >= DURATION
                || !(level.getEntity(entry.getKey()) instanceof TunerBoss boss) || !boss.isAlive());
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
        for (var entry : ACTIVE.entrySet()) {
            if (!(mc.level.getEntity(entry.getKey()) instanceof TunerBoss boss) || !boss.isAlive()
                    || boss.isInvisible()) continue;
            float elapsed = mc.level.getGameTime() - entry.getValue() + partial;
            pose.pushPose();
            pose.translate(Mth.lerp(partial, boss.xOld, boss.getX()) - camera.x,
                    Mth.lerp(partial, boss.yOld, boss.getY()) - camera.y + 0.01,
                    Mth.lerp(partial, boss.zOld, boss.getZ()) - camera.z);
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
