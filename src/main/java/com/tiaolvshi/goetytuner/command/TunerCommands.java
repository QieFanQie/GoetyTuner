/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.command;

import com.Polarice3.Goety.api.items.magic.IWand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.ritual.WandUpgradeEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 调律命令：手动给手中法杖添加/清除调律加成。
 * <pre>
 * /goetytuner tune witchcraft <amount>   设定巫法加成百分比（0.1=10%）
 * /goetytuner tune magic <amount>        设定魔法伤害加成百分比（0.4=40%）
 * /goetytuner tune add                   追加默认加成（配置值，叠加模式）
 * /goetytuner tune clear                 清除所有调律加成
 * /goetytuner tune info                  查看当前加成
 * </pre>
 * 需要 OP 2 权限。仅对主手法杖生效。
 */
@Mod.EventBusSubscriber(modid = GoetyTuner.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TunerCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("goetytuner")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("tune")
                                .then(Commands.literal("witchcraft")
                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0, 10.0))
                                                .executes(ctx -> setBonus(ctx, true, DoubleArgumentType.getDouble(ctx, "amount")))))
                                .then(Commands.literal("magic")
                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0, 10.0))
                                                .executes(ctx -> setBonus(ctx, false, DoubleArgumentType.getDouble(ctx, "amount")))))
                                .then(Commands.literal("add")
                                        .executes(TunerCommands::addDefaultBonus))
                                .then(Commands.literal("clear")
                                        .executes(TunerCommands::clearBonus))
                                .then(Commands.literal("info")
                                        .executes(TunerCommands::showInfo))
                        )
        );
        GoetyTuner.LOGGER.info("[Tuner] Commands registered: /goetytuner tune <witchcraft|magic|add|clear|info>");
    }

    /**
     * 设定指定加成值（覆盖该单项，另一项保留）。
     * @param witchcraft true=设巫法加成, false=设魔法伤害加成
     */
    private static int setBonus(CommandContext<CommandSourceStack> ctx, boolean witchcraft, double amount) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        Player player = src.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof IWand)) {
            src.sendFailure(Component.literal("Main hand must be a Goety wand!"));
            return 0;
        }
        CompoundTag t = stack.getOrCreateTagElement(WandUpgradeEvents.NBT_KEY);
        if (witchcraft) {
            t.putDouble(WandUpgradeEvents.WITCHCRAFT_KEY, amount);
        } else {
            t.putDouble(WandUpgradeEvents.MAGIC_DAMAGE_KEY, amount);
        }
        String label = witchcraft ? "Witchcraft" : "Magic Damage";
        src.sendSuccess(() -> Component.literal(
                String.format("[Tuner] %s bonus set to %.0f%% on %s",
                        label, amount * 100.0, stack.getDisplayName().getString())), true);
        return 1;
    }

    /** 追加配置默认加成（与仪式掉落逻辑一致，叠加模式） */
    private static int addDefaultBonus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        Player player = src.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof IWand)) {
            src.sendFailure(Component.literal("Main hand must be a Goety wand!"));
            return 0;
        }
        WandUpgradeEvents.applyWandUpgrade(stack);
        src.sendSuccess(() -> Component.literal(
                "[Tuner] Applied default tuning bonus to " + stack.getDisplayName().getString()), true);
        return 1;
    }

    /** 清除所有调律加成（移除 goetytuner NBT 键，不影响附魔/无法破坏等原数据） */
    private static int clearBonus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        Player player = src.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof IWand)) {
            src.sendFailure(Component.literal("Main hand must be a Goety wand!"));
            return 0;
        }
        if (stack.hasTag() && stack.getTag().contains(WandUpgradeEvents.NBT_KEY)) {
            stack.getTag().remove(WandUpgradeEvents.NBT_KEY);
            src.sendSuccess(() -> Component.literal(
                    "[Tuner] Cleared all tuning bonuses from " + stack.getDisplayName().getString()), true);
        } else {
            src.sendFailure(Component.literal("This wand has no tuning bonus."));
        }
        return 1;
    }

    /** 显示当前法杖的调律加成信息 */
    private static int showInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        Player player = src.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof IWand)) {
            src.sendFailure(Component.literal("Main hand must be a Goety wand!"));
            return 0;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(WandUpgradeEvents.NBT_KEY)) {
            src.sendSuccess(() -> Component.literal(
                    "[Tuner] " + stack.getDisplayName().getString() + " has no tuning bonus."), false);
            return 0;
        }
        CompoundTag t = tag.getCompound(WandUpgradeEvents.NBT_KEY);
        double witch = t.getDouble(WandUpgradeEvents.WITCHCRAFT_KEY);
        double magic = t.getDouble(WandUpgradeEvents.MAGIC_DAMAGE_KEY);
        src.sendSuccess(() -> Component.literal(
                String.format("[Tuner] %s | Witchcraft: %.0f%% | Magic Damage: %.0f%%",
                        stack.getDisplayName().getString(), witch * 100.0, magic * 100.0)), false);
        return 1;
    }
}
