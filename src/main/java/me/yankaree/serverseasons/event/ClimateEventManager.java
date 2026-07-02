package me.yankaree.serverseasons.event;

import me.yankaree.serverseasons.ServerSeasons;
import me.yankaree.serverseasons.config.ClimateConfig;
import me.yankaree.serverseasons.config.ConfigLoader;
import me.yankaree.serverseasons.season.Season;
import me.yankaree.serverseasons.season.SeasonManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.ServerLevelData;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

public class ClimateEventManager {
    private static ClimateEvent activeEvent = null;
    private static int eventDurationRemaining = 0;
    private static final Map<ClimateEvent, Integer> cooldowns = new HashMap<>();
    private static final Random random = new Random();

    public static ClimateEvent getActiveEvent() {
        return activeEvent;
    }

    public static int getDurationRemaining() {
        return eventDurationRemaining;
    }

    public static void startEvent(ClimateEvent event, int duration) {
        ClimateConfig cfg = ConfigLoader.getConfig();
        ClimateConfig.EventConfig eCfg = cfg.events.get(event.getId());
        if (eCfg == null || !eCfg.enabled) {
            ServerSeasons.LOGGER.warn("Attempted to start disabled/unconfigured event: " + event.getDisplayName());
            return;
        }

        if (activeEvent != null && activeEvent.getPriority() < event.getPriority()) {
            ServerSeasons.LOGGER.info("Supressing event " + event.getDisplayName() + " because higher priority " + activeEvent.getDisplayName() + " is running.");
            return;
        }

        activeEvent = event;
        eventDurationRemaining = duration;
        ServerSeasons.LOGGER.info("Climate event started: " + event.getDisplayName() + " for " + duration + " ticks");
        saveState();
    }

    public static void stopActiveEvent() {
        if (activeEvent != null) {
            ServerSeasons.LOGGER.info("Climate event stopped: " + activeEvent.getDisplayName());
            ClimateConfig.EventConfig eCfg = ConfigLoader.getConfig().events.get(activeEvent.getId());
            if (eCfg != null) {
                cooldowns.put(activeEvent, eCfg.cooldownTicks);
            }
            checkChaining(activeEvent);
            activeEvent = null;
            eventDurationRemaining = 0;
            saveState();
        }
    }

    private static void checkChaining(ClimateEvent finishedEvent) {
        Season currentSeason = SeasonManager.getCurrentSeason();
        if (currentSeason == Season.SUMMER && finishedEvent == ClimateEvent.TROPICAL_STORM) {
            double floodChance = getChainingChance("summer_tropical_storm", "flood", 0.35);
            if (random.nextDouble() < floodChance) {
                triggerChainedEvent(ClimateEvent.FLOOD);
            }
        } else if (currentSeason == Season.WINTER && finishedEvent == ClimateEvent.COLD_SNAP) {
            double blizzardChance = getChainingChance("winter_cold_snap", "blizzard", 0.40);
            if (random.nextDouble() < blizzardChance) {
                triggerChainedEvent(ClimateEvent.BLIZZARD);
            }
        } else if (finishedEvent == ClimateEvent.HEATWAVE) {
            double droughtChance = getChainingChance("heatwave_drought", "drought", 0.50);
            if (random.nextDouble() < droughtChance) {
                triggerChainedEvent(ClimateEvent.DROUGHT);
            }
        }
    }

    private static double getChainingChance(String chainKey, String eventKey, double def) {
        ClimateConfig cfg = ConfigLoader.getConfig();
        if (cfg.eventChaining.containsKey(chainKey)) {
            Map<String, Double> inner = cfg.eventChaining.get(chainKey);
            if (inner.containsKey(eventKey)) {
                return inner.get(eventKey);
            }
        }
        return def;
    }

    private static void triggerChainedEvent(ClimateEvent event) {
        ClimateConfig cfg = ConfigLoader.getConfig();
        ClimateConfig.EventConfig eCfg = cfg.events.get(event.getId());
        if (eCfg != null && eCfg.enabled) {
            int duration = eCfg.minDurationTicks + random.nextInt(eCfg.maxDurationTicks - eCfg.minDurationTicks + 1);
            startEvent(event, duration);
        }
    }

    public static void tick(MinecraftServer server) {
        for (Map.Entry<ClimateEvent, Integer> entry : cooldowns.entrySet()) {
            if (entry.getValue() > 0) {
                cooldowns.put(entry.getKey(), entry.getValue() - 1);
            }
        }

        if (activeEvent != null) {
            eventDurationRemaining--;
            if (eventDurationRemaining <= 0) {
                stopActiveEvent();
            } else {
                ClimateConfig.EventConfig eCfg = ConfigLoader.getConfig().events.get(activeEvent.getId());
                if (eCfg != null && !eCfg.weatherOverride.equalsIgnoreCase("none")) {
                    applyWeatherOverride(server, eCfg.weatherOverride);
                }
            }
        } else {
            if (server.getTickCount() % 24000 == 0) {
                rollRandomEvents();
            }
        }
    }

    private static void rollRandomEvents() {
        Season currentSeason = SeasonManager.getCurrentSeason();
        ClimateConfig cfg = ConfigLoader.getConfig();

        for (ClimateEvent event : ClimateEvent.values()) {
            if (event == ClimateEvent.TROPICAL_STORM && currentSeason != Season.SUMMER) {
                continue;
            }
            if (event == ClimateEvent.BLIZZARD && currentSeason != Season.WINTER) {
                continue;
            }

            ClimateConfig.EventConfig eCfg = cfg.events.get(event.getId());
            if (eCfg != null && eCfg.enabled && cooldowns.getOrDefault(event, 0) <= 0) {
                if (random.nextDouble() < eCfg.chancePerDay) {
                    int duration = eCfg.minDurationTicks + random.nextInt(eCfg.maxDurationTicks - eCfg.minDurationTicks + 1);
                    startEvent(event, duration);
                    break;
                }
            }
        }
    }

    private static void applyWeatherOverride(MinecraftServer server, String override) {
        applyWeatherOverride(server, override, eventDurationRemaining);
    }

    private static void applyWeatherOverride(MinecraftServer server, String override, int duration) {
        boolean rain = false;
        boolean thunder = false;

        if (override.equalsIgnoreCase("rain")) {
            rain = true;
        } else if (override.equalsIgnoreCase("snow")) {
            rain = true;
        } else if (override.equalsIgnoreCase("thunder")) {
            rain = true;
            thunder = true;
        }

        int clampedDuration = Math.max(duration, 200);

        for (ServerLevel world : server.getAllLevels()) {
            net.minecraft.world.level.saveddata.WeatherData data = world.getWeatherData();
            data.setRaining(rain);
            data.setThundering(thunder);
            data.setRainTime(rain ? clampedDuration : 0);
            data.setThunderTime(thunder ? clampedDuration : 0);
            data.setClearWeatherTime(rain ? 0 : clampedDuration);
            data.setDirty();
        }
    }

    public static double getTemperatureModifier() {
        if (activeEvent != null) {
            ClimateConfig.EventConfig eCfg = ConfigLoader.getConfig().events.get(activeEvent.getId());
            return eCfg != null ? eCfg.tempModifier : 0.0;
        }
        return 0.0;
    }

    public static double getHumidityModifier() {
        if (activeEvent != null) {
            ClimateConfig.EventConfig eCfg = ConfigLoader.getConfig().events.get(activeEvent.getId());
            return eCfg != null ? eCfg.humidityModifier : 0.0;
        }
        return 0.0;
    }

    public static double getWindModifier() {
        if (activeEvent != null) {
            ClimateConfig.EventConfig eCfg = ConfigLoader.getConfig().events.get(activeEvent.getId());
            return eCfg != null ? eCfg.windModifier : 0.0;
        }
        return 0.0;
    }

    public static void loadState(MinecraftServer server) {
        File file = new File(server.getWorldPath(LevelResource.ROOT).toFile(), "serverseasons_event.properties");
        if (file.exists()) {
            Properties props = new Properties();
            try (FileReader reader = new FileReader(file)) {
                props.load(reader);
                String activeId = props.getProperty("active_event", "none");
                if (!activeId.equalsIgnoreCase("none")) {
                    activeEvent = ClimateEvent.fromId(activeId);
                    eventDurationRemaining = Integer.parseInt(props.getProperty("remaining_duration", "0"));
                }
            } catch (Exception e) {
                ServerSeasons.LOGGER.error("Failed to load climate event state", e);
            }
        }
    }

    public static void saveState() {
        if (serverInstance != null) {
            saveState(serverInstance);
        }
    }

    private static MinecraftServer serverInstance = null;
    public static void setServerInstance(MinecraftServer server) {
        serverInstance = server;
    }

    public static void saveState(MinecraftServer server) {
        File file = new File(server.getWorldPath(LevelResource.ROOT).toFile(), "serverseasons_event.properties");
        Properties props = new Properties();
        props.setProperty("active_event", activeEvent != null ? activeEvent.getId() : "none");
        props.setProperty("remaining_duration", String.valueOf(eventDurationRemaining));
        try (FileWriter writer = new FileWriter(file)) {
            props.store(writer, "ServerSeasons Climate Event State");
        } catch (IOException e) {
            ServerSeasons.LOGGER.error("Failed to save climate event state", e);
        }
    }
}
