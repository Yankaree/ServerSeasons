package me.yankaree.serverseasons.event;

import me.yankaree.serverseasons.config.ClimateConfig;
import me.yankaree.serverseasons.config.ConfigLoader;
import me.yankaree.serverseasons.season.Season;
import me.yankaree.serverseasons.season.SeasonManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class EventForecast {
    private static List<ClimateEvent> upcomingEvents = new ArrayList<>();
    private static long lastForecastDay = -1;
    private static List<ClimateEvent> recentEvents = new ArrayList<>();

    /**
     * Generate forecast for the next 3 days with event limits
     */
    public static void generateDailyForecast(MinecraftServer server) {
        long currentDay = server.getTickCount() / 24000;
        
        if (currentDay == lastForecastDay) {
            return;
        }
        lastForecastDay = currentDay;

        upcomingEvents.clear();
        Random random = new Random();
        Season currentSeason = SeasonManager.getCurrentSeason();
        ClimateConfig cfg = ConfigLoader.getConfig();

        for (int day = 0; day < 3; day++) {
            ClimateEvent predictedEvent = null;
            
            List<ClimateEvent> validEvents = new ArrayList<>();
            
            for (ClimateEvent event : ClimateEvent.values()) {
                if (event == ClimateEvent.TROPICAL_STORM && currentSeason != Season.SUMMER) {
                    continue;
                }
                if (event == ClimateEvent.BLIZZARD && currentSeason != Season.WINTER) {
                    continue;
                }

                ClimateConfig.EventConfig eCfg = cfg.events.get(event.getId());
                if (eCfg != null && eCfg.enabled) {
                    validEvents.add(event);
                }
            }

            List<ClimateEvent> filteredEvents = new ArrayList<>(validEvents);
            
            if (recentEvents.size() >= 2) {
                int recentEventCount = 0;
                for (int i = recentEvents.size() - 1; i >= 0 && i >= recentEvents.size() - 2; i--) {
                    if (recentEvents.get(i) != null) {
                        recentEventCount++;
                    }
                }
                
                if (recentEventCount >= 2) {
                    filteredEvents.clear();
                }
            }
            
            for (int i = 0; i < recentEvents.size(); i++) {
                ClimateEvent event = recentEvents.get(i);
                if (event != null) {
                    int daysSinceEvent = recentEvents.size() - 1 - i;
                    if (daysSinceEvent < 4 && filteredEvents.contains(event)) {
                        filteredEvents.remove(event);
                    }
                }
            }

            if (!filteredEvents.isEmpty()) {
                predictedEvent = filteredEvents.get(random.nextInt(filteredEvents.size()));
            }

            upcomingEvents.add(predictedEvent);
            
            if (predictedEvent != null && day == 2) {
                broadcastEventWarning(server, predictedEvent, day + 1);
            }
        }

        recentEvents.clear();
        recentEvents.addAll(upcomingEvents);

        broadcastForecast(server);
    }

    /**
     * Broadcast forecast to all players
     */
    private static void broadcastForecast(MinecraftServer server) {
        Season season = SeasonManager.getCurrentSeason();
        String seasonEmoji = SeasonManager.getIcon();

        // Title
        server.getPlayerList().broadcastSystemMessage(
            net.minecraft.network.chat.Component.literal("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"),
            false
        );
        server.getPlayerList().broadcastSystemMessage(
            net.minecraft.network.chat.Component.literal("§e📅 3-DAY WEATHER FORECAST §e" + seasonEmoji),
            false
        );
        server.getPlayerList().broadcastSystemMessage(
            net.minecraft.network.chat.Component.literal("§7Season: §b" + season.getDisplayName()),
            false
        );
        server.getPlayerList().broadcastSystemMessage(
            net.minecraft.network.chat.Component.literal("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"),
            false
        );

        // Day by day forecast
        for (int day = 0; day < 3; day++) {
            ClimateEvent event = upcomingEvents.get(day);
            String dayName = getDayName(day);
            
            if (event == null) {
                server.getPlayerList().broadcastSystemMessage(
                    net.minecraft.network.chat.Component.literal("§7" + dayName + ": §aFair Weather ☀️"),
                    false
                );
            } else {
                String eventColor = getEventColor(event);
                String eventEmoji = getEventEmoji(event);
                server.getPlayerList().broadcastSystemMessage(
                    net.minecraft.network.chat.Component.literal("§7" + dayName + ": " + eventColor + event.getDisplayName() + " " + eventEmoji),
                    false
                );
            }
        }

        server.getPlayerList().broadcastSystemMessage(
            net.minecraft.network.chat.Component.literal("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"),
            false
        );
    }

    /**
     * Get day name (Today, Tomorrow, etc.)
     */
    private static String getDayName(int dayOffset) {
        return switch (dayOffset) {
            case 0 -> "§e🌅 Today";
            case 1 -> "§b🌆 Tomorrow";
            case 2 -> "§9🌙 Day After";
            default -> "Day " + dayOffset;
        };
    }

    /**
     * Get color code for event
     */
    private static String getEventColor(ClimateEvent event) {
        return switch (event) {
            case BLIZZARD, COLD_SNAP -> "§9";              // Blue - Cold
            case TROPICAL_STORM, FLOOD -> "§b";             // Cyan - Rain
            case HEATWAVE, DROUGHT -> "§c";                 // Red - Heat
            case SEVERE_THUNDERSTORM -> "§d";              // Magenta - Storm
            default -> "§7";                                // Gray
        };
    }

    /**
     * Get emoji for event
     */
    private static String getEventEmoji(ClimateEvent event) {
        return switch (event) {
            case BLIZZARD -> "❄️";
            case TROPICAL_STORM -> "🌊";
            case FLOOD -> "💧";
            case HEATWAVE -> "🔥";
            case COLD_SNAP -> "❄️";
            case SEVERE_THUNDERSTORM -> "⛈️";
            case DROUGHT -> "🏜️";
            default -> "?";
        };
    }

    /**
     * Broadcast event warning 3 days in advance
     */
    private static void broadcastEventWarning(MinecraftServer server, ClimateEvent event, int day) {
        String dayName = day == 1 ? "Tomorrow" : day == 2 ? "Day After" : "Day " + day;
        String eventColor = getEventColor(event);
        String eventEmoji = getEventEmoji(event);
        
        // Event warning message
        server.getPlayerList().broadcastSystemMessage(
            net.minecraft.network.chat.Component.literal("§e⚠ EVENT WARNING §r" + eventColor + event.getDisplayName() + " " + eventEmoji),
            false
        );
        server.getPlayerList().broadcastSystemMessage(
            net.minecraft.network.chat.Component.literal("§7Expected " + dayName + "§r" + eventColor + getDayWarningText(event)),
            false
        );
    }
    
    /**
     * Get warning text for event
     */
    private static String getDayWarningText(ClimateEvent event) {
        return switch (event) {
            case BLIZZARD -> "❄️ Blizzards bring severe cold and visibility issues.";
            case TROPICAL_STORM -> "🌊 Tropical storms cause flooding and strong winds.";
            case FLOOD -> "💧 Flooding affects low-lying areas and transportation.";
            case HEATWAVE -> "🔥 Heatwaves increase fire risk and heat exhaustion.";
            case COLD_SNAP -> "❄️ Cold snaps cause rapid temperature drops.";
            case SEVERE_THUNDERSTORM -> "⛈️ Thunderstorms bring lightning and wind damage.";
            case DROUGHT -> "🏜️ Drought conditions increase fire risk and water scarcity.";
            default -> "Unknown event effects.";
        };
    }

    /**
     * Get list of upcoming events
     */
    public static List<ClimateEvent> getUpcomingEvents() {
        return new ArrayList<>(upcomingEvents);
    }

    /**
     * Get today's predicted event
     */
    public static ClimateEvent getTodayEvent() {
        return upcomingEvents.isEmpty() ? null : upcomingEvents.get(0);
    }
}