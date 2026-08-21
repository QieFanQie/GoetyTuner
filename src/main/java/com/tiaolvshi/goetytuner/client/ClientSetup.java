/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.client;

import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.init.ModEntities;
import com.tiaolvshi.goetytuner.client.render.TunerCapeModel;
import com.tiaolvshi.goetytuner.client.render.TunerRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = GoetyTuner.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TUNER.get(), TunerRenderer::new);
    }

    /** 【2026-08-19 第十九轮】披风模型层定义（原版渲染方案，TunerCapeLayer 使用） */
    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TunerCapeModel.LAYER_LOCATION, TunerCapeModel::createBodyLayer);
    }

    /**
     * 【v0.0.3】注册配置屏幕工厂：让 Forge 的 Mods 菜单（标题页/暂停页均可进入）出现
     * 「Config」按钮，点击打开 {@link TunerConfigScreen}。COMMON 类型配置默认不显示该按钮，
     * 必须显式注册扩展点。
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (Minecraft minecraft, Screen parent) -> new TunerConfigScreen(parent)));
    }
}
