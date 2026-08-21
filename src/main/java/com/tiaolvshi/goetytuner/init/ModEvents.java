/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.init;

import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.combat.CombatEvents;
import com.tiaolvshi.goetytuner.entity.TunerBoss;
import com.tiaolvshi.goetytuner.focus.FocusPoolManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * FORGE 总线事件：
 * - 服务器启动：触发聚晶池延迟初始化（所有模组注册完成后）
 * - boss加入/移出世界：登记战斗事件路由
 *
 * <p>实体属性注册见 {@link ModBusEvents}（EntityAttributeCreationEvent 是 mod bus 事件）。
 */
@Mod.EventBusSubscriber(modid = GoetyTuner.MOD_ID)
public class ModEvents {

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // 初始化时机：模组初始化阶段的最后（服务器启动），扫描全部注册聚晶并分类
        FocusPoolManager.initIfNeeded();
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) {
            return; // 只处理服务端逻辑侧
        }
        if (event.getEntity() instanceof TunerBoss boss) {
            CombatEvents.registerBoss(boss);
        }
        // 【第二十七轮】诡厄：暮色「毁坏链锤」崩溃修补。
        // DestructionEntity.onHitBlock 构造 BlockEvent.BreakEvent 时假设 owner 必为玩家，
        // 非玩家 owner（如 boss 施法生成）会传 null player，BreakEvent 构造器立即 NPE 崩服。
        // 崩溃发生在实体 tick 阶段，聚晶黑名单/施法 try-catch 均无法覆盖，故在实体进入
        // 世界时直接消灭 owner 非玩家的链锤（零编译期依赖，按注册 id 判断，不引用其类）。
        Entity entity = event.getEntity();
        if (entity instanceof OwnableEntity ownable) {
            ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            if (typeId != null && "goetytwilight".equals(typeId.getNamespace())
                    && "destruction".equals(typeId.getPath())) {
                Entity owner = ownable.getOwner();
                if (owner == null || !(owner instanceof Player)) {
                    GoetyTuner.LOGGER.warn("[Tuner] Discarding goetytwilight:destruction (owner={}) "
                            + "to prevent BreakEvent NPE crash.", owner == null ? "null" : owner.getType());
                    entity.discard();
                    event.setCanceled(true);
                }
            }
        }
    }
}
