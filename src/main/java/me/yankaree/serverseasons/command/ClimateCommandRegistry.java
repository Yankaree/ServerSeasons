package me.yankaree.serverseasons.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.yankaree.serverseasons.config.ClimateConfig;
import me.yankaree.serverseasons.config.ConfigLoader;
import me.yankaree.serverseasons.engine.HumiditySystem;
import me.yankaree.serverseasons.event.ClimateEvent;
import me.yankaree.serverseasons.event.ClimateEventManager;
import me.yankaree.serverseasons.season.Season;
import me.yankaree.serverseasons.season.SeasonManager;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

import java.util.Random;

public class ClimateCommandRegistry {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // --- /season Command Tree ---
        dispatcher.register(Commands.literal("season")
            .requires((CommandSourceStack source) -> source.checkPermission(net.minecraft.resources.Identifier.fromNamespaceAndPath("serverseasons", "admin"), net.minecraft.server.permissions.PermissionLevel.byId(2)))
            .then(Commands.literal("get")
                .executes(ctx -> {
                    Season current = SeasonManager.getCurrentSeason();
                    ctx.getSource().sendSuccess(() -> Component.literal("§aCurrent World Season is: §6" + current.getDisplayName() + " " + SeasonManager.getIcon()), false);
                    return 1;
                })
            )
            .then(Commands.literal("set")
                .then(Commands.literal("spring").executes(ctx -> setSeason(ctx.getSource(), Season.SPRING)))
                .then(Commands.literal("summer").executes(ctx -> setSeason(ctx.getSource(), Season.SUMMER)))
                .then(Commands.literal("autumn").executes(ctx -> setSeason(ctx.getSource(), Season.AUTUMN)))
                .then(Commands.literal("winter").executes(ctx -> setSeason(ctx.getSource(), Season.WINTER)))
            )
            .then(Commands.literal("random")
                .executes(ctx -> {
                    Season[] values = Season.values();
                    Season randomSeason = values[new Random().nextInt(values.length)];
                    return setSeason(ctx.getSource(), randomSeason);
                })
            )
            .then(Commands.literal("reload")
                .executes(ctx -> {
                    boolean success = ConfigLoader.load();
                    if (success) {
                        ctx.getSource().sendSuccess(() -> Component.literal("§aClimate configuration reloaded successfully."), false);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("Failed to reload climate config. Check server logs."));
                    }
                    return success ? 1 : 0;
                })
            )
            .then(Commands.literal("info")
                .executes(ctx -> {
                    Season current = SeasonManager.getCurrentSeason();
                    ClimateConfig cfg = ConfigLoader.getConfig();
                    ctx.getSource().sendSuccess(() -> Component.literal("§e=== Season Info ==="), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7Current Season: §b" + current.getDisplayName() + " " + SeasonManager.getIcon()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7Temperature Modifier: §e" + SeasonManager.getTemperatureModifier() + "°C"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7Weather Profile: §b" + current.getDisplayName()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7Available Events:"), false);
                    for (ClimateEvent e : ClimateEvent.values()) {
                        if (e == ClimateEvent.TROPICAL_STORM && current != Season.SUMMER) continue;
                        if (e == ClimateEvent.BLIZZARD && current != Season.WINTER) continue;
                        ctx.getSource().sendSuccess(() -> Component.literal(" - §a" + e.getDisplayName()), false);
                    }
                    return 1;
                })
            )
        );

        // --- /climate Command Tree ---
        dispatcher.register(Commands.literal("climate")
            .requires((CommandSourceStack source) -> source.checkPermission(net.minecraft.resources.Identifier.fromNamespaceAndPath("serverseasons", "admin"), net.minecraft.server.permissions.PermissionLevel.byId(2)))
            .then(Commands.literal("humidity")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayer) {
                        ServerPlayer p = (ServerPlayer) ctx.getSource().getEntity();
                        BlockPos pos = BlockPos.containing(p.getX(), p.getY(), p.getZ());
                        double hum = HumiditySystem.getHumidity((net.minecraft.server.level.ServerLevel) p.level(), pos);
                        ctx.getSource().sendSuccess(() -> Component.literal("§7Your region humidity: §9" + Math.round(hum * 100) + "%"), false);
                        return 1;
                    }
                    ctx.getSource().sendFailure(Component.literal("You must be a player to execute this command. Use /climate humidity get <player> instead."));
                    return 0;
                })
                .then(Commands.literal("get")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            BlockPos pos = new BlockPos((int) target.getX(), (int) target.getY(), (int) target.getZ());
                            double hum = HumiditySystem.getHumidity((net.minecraft.server.level.ServerLevel) target.level(), pos);
                            ctx.getSource().sendSuccess(() -> Component.literal("§7Region humidity for §e" + target.getName().getString() + "§7: §9" + Math.round(hum * 100) + "%"), false);
                            return 1;
                        })
                    )
                )
            )
            .then(Commands.literal("event")
                .then(Commands.literal("list")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal("§e=== Available Climate Events ==="), false);
                        for (ClimateEvent e : ClimateEvent.values()) {
                            ClimateConfig.EventConfig ec = ConfigLoader.getConfig().events.get(e.getId());
                            boolean enabled = ec != null && ec.enabled;
                            ctx.getSource().sendSuccess(() -> Component.literal(" - §b" + e.getDisplayName() + " §7(Priority: " + e.getPriority() + ") " + (enabled ? "§a[Enabled]" : "§c[Disabled]")), false);
                        }
                        return 1;
                    })
                )
                .then(Commands.literal("current")
                    .executes(ctx -> {
                        ClimateEvent active = ClimateEventManager.getActiveEvent();
                        if (active == null) {
                            ctx.getSource().sendSuccess(() -> Component.literal("§7No active climate event."), false);
                        } else {
                            ctx.getSource().sendSuccess(() -> Component.literal("§7Active event: §e" + active.getDisplayName() + " §7(Remaining: §b" + (ClimateEventManager.getDurationRemaining() / 20) + "s§7)"), false);
                        }
                        return 1;
                    })
                )
                .then(Commands.literal("start")
                    .then(Commands.argument("event", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (ClimateEvent e : ClimateEvent.values()) {
                                b.suggest(e.getId());
                            }
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            String eventId = StringArgumentType.getString(ctx, "event");
                            ClimateEvent event = ClimateEvent.fromId(eventId);
                            if (event == null) {
                                ctx.getSource().sendFailure(Component.literal("Invalid event name. Use /climate event list to see options."));
                                return 0;
                            }
                            ClimateEventManager.startEvent(event, 24000);
                            ctx.getSource().sendSuccess(() -> Component.literal("§aSuccessfully started event §e" + event.getDisplayName()), false);
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("stop")
                    .executes(ctx -> {
                        ClimateEvent active = ClimateEventManager.getActiveEvent();
                        if (active == null) {
                            ctx.getSource().sendSuccess(() -> Component.literal("§7No event is currently active."), false);
                        } else {
                            ClimateEventManager.stopActiveEvent();
                            ctx.getSource().sendSuccess(() -> Component.literal("§aStopped active climate event."), false);
                        }
                        return 1;
                    })
                )
                .then(Commands.literal("reload")
                    .executes(ctx -> {
                        boolean success = ConfigLoader.load();
                        if (success) {
                            ctx.getSource().sendSuccess(() -> Component.literal("§aClimate config and event parameters reloaded."), false);
                        } else {
                            ctx.getSource().sendFailure(Component.literal("Failed to reload climate config."));
                        }
                        return success ? 1 : 0;
                    })
                )
                .then(Commands.literal("enable")
                    .then(Commands.argument("event", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (ClimateEvent e : ClimateEvent.values()) {
                                b.suggest(e.getId());
                            }
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            String eventId = StringArgumentType.getString(ctx, "event");
                            ClimateEvent event = ClimateEvent.fromId(eventId);
                            if (event == null) {
                                ctx.getSource().sendFailure(Component.literal("Invalid event name."));
                                return 0;
                            }
                            ClimateConfig.EventConfig ec = ConfigLoader.getConfig().events.get(event.getId());
                            if (ec != null) {
                                ec.enabled = true;
                                ConfigLoader.save();
                                ctx.getSource().sendSuccess(() -> Component.literal("§aEvent §e" + event.getDisplayName() + " §aenabled."), false);
                                return 1;
                            }
                            return 0;
                        })
                    )
                )
                .then(Commands.literal("disable")
                    .then(Commands.argument("event", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (ClimateEvent e : ClimateEvent.values()) {
                                b.suggest(e.getId());
                            }
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            String eventId = StringArgumentType.getString(ctx, "event");
                            ClimateEvent event = ClimateEvent.fromId(eventId);
                            if (event == null) {
                                ctx.getSource().sendFailure(Component.literal("Invalid event name."));
                                return 0;
                            }
                            ClimateConfig.EventConfig ec = ConfigLoader.getConfig().events.get(event.getId());
                            if (ec != null) {
                                ec.enabled = false;
                                ConfigLoader.save();
                                ctx.getSource().sendSuccess(() -> Component.literal("§cEvent §e" + event.getDisplayName() + " §cdisabled."), false);
                                return 1;
                            }
                            return 0;
                        })
                    )
                )
                .then(Commands.literal("setchance")
                    .then(Commands.argument("event", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (ClimateEvent e : ClimateEvent.values()) {
                                b.suggest(e.getId());
                            }
                            return b.buildFuture();
                        })
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                            .executes(ctx -> {
                                String eventId = StringArgumentType.getString(ctx, "event");
                                ClimateEvent event = ClimateEvent.fromId(eventId);
                                if (event == null) {
                                    ctx.getSource().sendFailure(Component.literal("Invalid event name."));
                                    return 0;
                                }
                                double val = DoubleArgumentType.getDouble(ctx, "value");
                                ClimateConfig.EventConfig ec = ConfigLoader.getConfig().events.get(event.getId());
                                if (ec != null) {
                                    ec.chancePerDay = val;
                                    ConfigLoader.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("§aSet chance per day for §e" + event.getDisplayName() + " §ato §b" + val), false);
                                    return 1;
                                }
                                return 0;
                            })
                        )
                    )
                )
            )
        );
    }

    private static int setSeason(CommandSourceStack source, Season season) {
        SeasonManager.setCurrentSeason(season);
        source.sendSuccess(() -> Component.literal("§aSeason set to: §6" + season.getDisplayName() + " " + SeasonManager.getIcon()), true);
        return 1;
    }
}
