package me.yankaree.serverseasons.weather;

import me.yankaree.serverseasons.config.ClimateConfig;
import me.yankaree.serverseasons.config.ConfigLoader;
import me.yankaree.serverseasons.season.Season;
import me.yankaree.serverseasons.season.SeasonManager;
import me.yankaree.serverseasons.event.ClimateEventManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;

import java.util.Random;

public class WeatherSystem {
    private static final Random random = new Random();
    private static int weatherTicksRemaining = 12000;

    public static void tick(MinecraftServer server) {
        if (ClimateEventManager.getActiveEvent() != null) {
            return;
        }

        weatherTicksRemaining--;
        if (weatherTicksRemaining <= 0) {
            rollWeather(server);
        }
    }

    public static void rollWeather(MinecraftServer server) {
        Season season = SeasonManager.getCurrentSeason();
        ClimateConfig cfg = ConfigLoader.getConfig();
        ClimateConfig.SeasonConfig sCfg = cfg.seasons.get(season.getId());

        double pClear = 50.0;
        double pRain = 30.0;
        double pThunder = 15.0;
        double pSnow = 5.0;

        if (sCfg != null) {
            pClear = sCfg.probClear;
            pRain = sCfg.probRain;
            pThunder = sCfg.probThunder;
            pSnow = sCfg.probSnow;
        }

        double total = pClear + pRain + pThunder + pSnow;
        double roll = random.nextDouble() * total;

        int duration = 12000 + random.nextInt(12000);
        weatherTicksRemaining = duration;

        boolean rain = false;
        boolean thunder = false;

        if (roll < pClear) {
            rain = false;
            thunder = false;
        } else if (roll < pClear + pRain) {
            rain = true;
            thunder = false;
        } else if (roll < pClear + pRain + pThunder) {
            rain = true;
            thunder = true;
        } else {
            // Snow: client-side biome effect; do not set server-side rain flag
            rain = false;
            thunder = false;
        }

        for (ServerLevel world : server.getAllLevels()) {
            net.minecraft.world.level.saveddata.WeatherData data = world.getWeatherData();
            data.setRaining(rain);
            data.setThundering(thunder);
            data.setRainTime(rain ? duration : 0);
            data.setThunderTime(thunder ? duration : 0);
            data.setClearWeatherTime(rain ? 0 : duration);
            data.setDirty();
        }
    }
}
