package me.yankaree.serverseasons.season;

import me.yankaree.serverseasons.ServerSeasons;
import me.yankaree.serverseasons.config.ConfigLoader;
import me.yankaree.serverseasons.config.ClimateConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

public class SeasonManager {
    private static Season currentSeason = Season.SPRING;
    private static MinecraftServer serverInstance;

    public static Season getCurrentSeason() {
        return currentSeason;
    }

    public static void setCurrentSeason(Season season) {
        currentSeason = season;
        saveSeason();
    }

    public static void init(MinecraftServer server) {
        serverInstance = server;
        loadSeason();
    }

    private static File getSaveFile() {
        if (serverInstance == null) return null;
        Path savePath = serverInstance.getWorldPath(LevelResource.ROOT);
        return new File(savePath.toFile(), "serverseasons_state.properties");
    }

    public static void loadSeason() {
        File file = getSaveFile();
        if (file != null && file.exists()) {
            Properties props = new Properties();
            try (FileReader reader = new FileReader(file)) {
                props.load(reader);
                String seasonId = props.getProperty("current_season", "spring");
                currentSeason = Season.fromId(seasonId);
                ServerSeasons.LOGGER.info("Loaded current season: " + currentSeason.getDisplayName());
            } catch (IOException e) {
                ServerSeasons.LOGGER.error("Failed to load season state", e);
            }
        } else {
            currentSeason = Season.SPRING;
            ServerSeasons.LOGGER.info("No saved season state found, defaulting to Spring.");
        }
    }

    public static void saveSeason() {
        File file = getSaveFile();
        if (file == null) return;
        Properties props = new Properties();
        props.setProperty("current_season", currentSeason.getId());
        try (FileWriter writer = new FileWriter(file)) {
            props.store(writer, "ServerSeasons Persistent State");
        } catch (IOException e) {
            ServerSeasons.LOGGER.error("Failed to save season state", e);
        }
    }

    public static String getIcon() {
        ClimateConfig cfg = ConfigLoader.getConfig();
        if (cfg.seasons.containsKey(currentSeason.getId())) {
            String icon = cfg.seasons.get(currentSeason.getId()).icon;
            if (icon != null && !icon.isEmpty()) return icon;
        }
        return currentSeason.getDefaultIcon();
    }

    public static double getTemperatureModifier() {
        ClimateConfig cfg = ConfigLoader.getConfig();
        ClimateConfig.SeasonConfig sCfg = cfg.seasons.get(currentSeason.getId());
        if (sCfg != null) {
            return (sCfg.tempModifierMin + sCfg.tempModifierMax) / 2.0;
        }
        switch (currentSeason) {
            case SUMMER: return 3.5;
            case AUTUMN: return -2.0;
            case WINTER: return -7.0;
            default: return 0.0;
        }
    }
}
