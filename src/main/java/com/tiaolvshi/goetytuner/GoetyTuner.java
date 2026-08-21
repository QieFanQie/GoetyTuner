/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner;

import com.mojang.logging.LogUtils;
import com.tiaolvshi.goetytuner.config.TunerCommonConfig;
import com.tiaolvshi.goetytuner.init.ModEntities;
import com.tiaolvshi.goetytuner.init.ModItems;
import com.tiaolvshi.goetytuner.init.ModSounds;
import com.tiaolvshi.goetytuner.network.TunerNetwork;
import com.tiaolvshi.goetytuner.ritual.ModRituals;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * 「调律师」—— 诡厄巫法附属Boss模组。
 *
 * 核心设计：
 * - 聚晶池系统：启动末尾扫描全部注册的 IFocus（诡厄巫法及其附属），分为
 *   攻击/防御/召唤/其他 四大功能池，配对四个冷却池，轮盘赌抽取。
 * - 评分系统：静态（配置文件/LLM自动生成）+ 动态（战斗中根据DPS/召唤物表现修正偏移）。
 * - 音乐阶段：服务端权威计时器驱动 铺垫/高潮/低谷 三段式演奏，客户端仅渲染节奏条。
 */
@Mod(GoetyTuner.MOD_ID)
public class GoetyTuner {
    public static final String MOD_ID = "goetytuner";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GoetyTuner() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITY_TYPES.register(modBus);
        ModSounds.SOUND_EVENTS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.CREATIVE_TABS.register(modBus);
        // 【任务#118】把自定义仪式工厂追加进 Goety 的 goety:ritual_factory 注册表
        // （必须先于配方加载注册——DeferredRegister 挂 MOD 总线天然满足）
        ModRituals.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TunerCommonConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(TunerNetwork::init);
        // 注意：聚晶池的初始化在服务器启动后（所有模组注册完成时）进行，
        // 见 FocusPoolManager#initIfNeeded，不在此处扫描。
        LOGGER.info("Goety Tuner (The Tuner) loaded. Waiting for registry scan of foci...");
    }
}
