package me.yankaree.serverseasons.config;

import me.yankaree.serverseasons.ServerSeasons;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class ConfigLoader {
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "climate-config.yml");
    private static ClimateConfig currentConfig = new ClimateConfig();

    public static ClimateConfig getConfig() {
        return currentConfig;
    }

    public static boolean load() {
        if (!CONFIG_FILE.exists()) {
            writeDefaultConfig();
        }

        try {
            Map<String, Object> parsed = parseYaml(CONFIG_FILE);
            applyConfig(parsed);
            me.yankaree.serverseasons.engine.ClimateCache.clear();
            ServerSeasons.LOGGER.info("Climate simulation config successfully loaded/reloaded.");
            return true;
        } catch (Exception e) {
            ServerSeasons.LOGGER.error("Failed to load climate-config.yml, using default config.", e);
            return false;
        }
    }

    private static void applyConfig(Map<String, Object> map) {
        ClimateConfig cfg = new ClimateConfig();

        cfg.defaultSmoothingFactor = getDouble(map, "defaultSmoothingFactor", cfg.defaultSmoothingFactor);
        cfg.comfortMin = getDouble(map, "comfortMin", cfg.comfortMin);
        cfg.comfortMax = getDouble(map, "comfortMax", cfg.comfortMax);
        cfg.coldSlowness = getDouble(map, "coldSlowness", cfg.coldSlowness);
        cfg.coldExtreme = getDouble(map, "coldExtreme", cfg.coldExtreme);
        cfg.hotDamage = getDouble(map, "hotDamage", cfg.hotDamage);
        cfg.hotExtreme = getDouble(map, "hotExtreme", cfg.hotExtreme);
        cfg.equilibrium = getDouble(map, "equilibrium", cfg.equilibrium);

        cfg.altitudeBaseY = getInt(map, "altitudeBaseY", cfg.altitudeBaseY);
        cfg.altitudeLapseRate = getDouble(map, "altitudeLapseRate", cfg.altitudeLapseRate);
        cfg.altitudeMinClamp = getDouble(map, "altitudeMinClamp", cfg.altitudeMinClamp);
        cfg.altitudeMaxClamp = getDouble(map, "altitudeMaxClamp", cfg.altitudeMaxClamp);

        cfg.caveShallowOffset = getDouble(map, "caveShallowOffset", cfg.caveShallowOffset);
        cfg.caveDeepMin = getDouble(map, "caveDeepMin", cfg.caveDeepMin);
        cfg.caveDeepMax = getDouble(map, "caveDeepMax", cfg.caveDeepMax);
        cfg.caveLavaHeatModifier = getDouble(map, "caveLavaHeatModifier", cfg.caveLavaHeatModifier);
        cfg.caveLavaRange = getInt(map, "caveLavaRange", cfg.caveLavaRange);

        cfg.hudFormat = getString(map, "hudFormat", cfg.hudFormat);
        cfg.hudUpdateTicks = getInt(map, "hudUpdateTicks", cfg.hudUpdateTicks);

        // Parse seasons
        Map<String, Object> seasonsMap = getMap(map, "seasons");
        if (seasonsMap != null) {
            for (Map.Entry<String, Object> entry : seasonsMap.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    Map<String, Object> sObj = (Map<String, Object>) entry.getValue();
                    double tMin = getDouble(sObj, "tempModifierMin", 0.0);
                    double tMax = getDouble(sObj, "tempModifierMax", 0.0);
                    double pClear = getDouble(sObj, "probClear", 25.0);
                    double pRain = getDouble(sObj, "probRain", 25.0);
                    double pThunder = getDouble(sObj, "probThunder", 25.0);
                    double pSnow = getDouble(sObj, "probSnow", 25.0);
                    String icon = getString(sObj, "icon", "");
                    cfg.seasons.put(entry.getKey().toLowerCase(), new ClimateConfig.SeasonConfig(tMin, tMax, pClear, pRain, pThunder, pSnow, icon));
                }
            }
        }

        // Parse biomes
        Map<String, Object> biomesMap = getMap(map, "biomes");
        if (biomesMap != null) {
            for (Map.Entry<String, Object> entry : biomesMap.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    Map<String, Object> bObj = (Map<String, Object>) entry.getValue();
                    double bTemp = getDouble(bObj, "baseTemp", 20.0);
                    double bHum = getDouble(bObj, "baseHumidity", 0.5);
                    double nMult = getDouble(bObj, "noiseMultiplier", 1.0);
                    String tType = getString(bObj, "terrainType", "plains");
                    cfg.biomes.put(entry.getKey().toLowerCase(), new ClimateConfig.BiomeConfig(bTemp, bHum, nMult, tType));
                }
            }
        }

        // Parse events
        Map<String, Object> eventsMap = getMap(map, "events");
        if (eventsMap != null) {
            for (Map.Entry<String, Object> entry : eventsMap.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    Map<String, Object> eObj = (Map<String, Object>) entry.getValue();
                    ClimateConfig.EventConfig eCfg = new ClimateConfig.EventConfig();
                    eCfg.enabled = getBoolean(eObj, "enabled", true);
                    eCfg.chancePerDay = getDouble(eObj, "chance_per_day", 0.05);
                    eCfg.minDurationTicks = getInt(eObj, "minimum_duration", 24000);
                    eCfg.maxDurationTicks = getInt(eObj, "maximum_duration", 72000);
                    eCfg.cooldownTicks = getInt(eObj, "cooldown", 24000);
                    eCfg.tempModifier = getDouble(eObj, "temperature_modifier", 0.0);
                    eCfg.humidityModifier = getDouble(eObj, "humidity_modifier", 0.0);
                    eCfg.windModifier = getDouble(eObj, "wind_modifier", 0.0);
                    eCfg.weatherOverride = getString(eObj, "weather_override", "none");
                    cfg.events.put(entry.getKey().toLowerCase(), eCfg);
                }
            }
        }

        // Parse event chaining
        Map<String, Object> chainingMap = getMap(map, "eventChaining");
        if (chainingMap != null) {
            for (Map.Entry<String, Object> entry : chainingMap.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    Map<String, Object> innerMap = (Map<String, Object>) entry.getValue();
                    Map<String, Double> finalInner = new HashMap<>();
                    for (Map.Entry<String, Object> innerEntry : innerMap.entrySet()) {
                        finalInner.put(innerEntry.getKey().toLowerCase(), getDouble(innerMap, innerEntry.getKey(), 0.0));
                    }
                    cfg.eventChaining.put(entry.getKey().toLowerCase(), finalInner);
                }
            }
        }

        currentConfig = cfg;
    }

    private static double getDouble(Map<String, Object> map, String key, double def) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try {
                return Double.parseDouble((String) val);
            } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try {
                return Integer.parseInt((String) val);
            } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        Object val = map.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof String) return Boolean.parseBoolean((String) val);
        return def;
    }

    private static String getString(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return val != null ? val.toString() : def;
    }

    private static Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Map) return (Map<String, Object>) val;
        return null;
    }

    private static Map<String, Object> parseYaml(File file) throws IOException {
        Map<String, Object> root = new HashMap<>();
        Stack<Map<String, Object>> stack = new Stack<>();
        Stack<Integer> indents = new Stack<>();

        stack.push(root);
        indents.push(-1);

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Remove comments
                int commentIndex = line.indexOf('#');
                if (commentIndex >= 0) {
                    line = line.substring(0, commentIndex);
                }

                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                // Check indent
                int indent = 0;
                while (indent < line.length() && line.charAt(indent) == ' ') {
                    indent++;
                }

                while (!indents.isEmpty() && indent <= indents.peek()) {
                    stack.pop();
                    indents.pop();
                }

                int colonIndex = trimmed.indexOf(':');
                if (colonIndex < 0) continue;

                String key = trimmed.substring(0, colonIndex).trim();
                String valueStr = trimmed.substring(colonIndex + 1).trim();

                Map<String, Object> currentMap = stack.peek();

                if (valueStr.isEmpty()) {
                    // It is a nested block
                    Map<String, Object> newMap = new HashMap<>();
                    currentMap.put(key, newMap);
                    stack.push(newMap);
                    indents.push(indent);
                } else {
                    // Simple value
                    Object value = parseValue(valueStr);
                    currentMap.put(key, value);
                }
            }
        }
        return root;
    }

    private static Object parseValue(String val) {
        if (val.equalsIgnoreCase("true")) return true;
        if (val.equalsIgnoreCase("false")) return false;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            try {
                return Double.parseDouble(val);
            } catch (NumberFormatException e2) {
                if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                    return val.substring(1, val.length() - 1);
                }
                if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
                    return val.substring(1, val.length() - 1);
                }
                return val;
            }
        }
    }

    private static void writeDefaultConfig() {
        CONFIG_FILE.getParentFile().mkdirs();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CONFIG_FILE))) {
            writer.write("# Climate Simulation System Configuration\n");
            writer.write("defaultSmoothingFactor: 0.05\n");
            writer.write("comfortMin: 23.0\n");
            writer.write("comfortMax: 33.0\n");
            writer.write("coldSlowness: 18.0\n");
            writer.write("coldExtreme: 12.0\n");
            writer.write("hotDamage: 38.0\n");
            writer.write("hotExtreme: 42.0\n");
            writer.write("equilibrium: 36.5\n\n");

            writer.write("# Altitude parameters\n");
            writer.write("altitudeBaseY: 64\n");
            writer.write("altitudeLapseRate: -0.05\n");
            writer.write("altitudeMinClamp: -15.0\n");
            writer.write("altitudeMaxClamp: 15.0\n\n");

            writer.write("# Cave/underground parameters\n");
            writer.write("caveShallowOffset: -3.0\n");
            writer.write("caveDeepMin: 15.0\n");
            writer.write("caveDeepMax: 22.0\n");
            writer.write("caveLavaHeatModifier: 8.0\n");
            writer.write("caveLavaRange: 8\n\n");

            writer.write("# HUD update interval (ticks)\n");
            writer.write("hudFormat: \"\uD83C\uDF21 %s°C | %s | %s\"\n");
            writer.write("hudUpdateTicks: 20\n\n");

            writer.write("# Seasons configuration\n");
            writer.write("seasons:\n");
            writer.write("  spring:\n");
            writer.write("    tempModifierMin: 0.0\n");
            writer.write("    tempModifierMax: 0.0\n");
            writer.write("    probClear: 50.0\n");
            writer.write("    probRain: 30.0\n");
            writer.write("    probThunder: 15.0\n");
            writer.write("    probSnow: 5.0\n");
            writer.write("    icon: \"\uD83C\uDF31\"\n");
            writer.write("  summer:\n");
            writer.write("    tempModifierMin: 2.0\n");
            writer.write("    tempModifierMax: 5.0\n");
            writer.write("    probClear: 60.0\n");
            writer.write("    probRain: 20.0\n");
            writer.write("    probThunder: 20.0\n");
            writer.write("    probSnow: 0.0\n");
            writer.write("    icon: \"\u2600\uFE0F\"\n");
            writer.write("  autumn:\n");
            writer.write("    tempModifierMin: -3.0\n");
            writer.write("    tempModifierMax: -1.0\n");
            writer.write("    probClear: 40.0\n");
            writer.write("    probRain: 45.0\n");
            writer.write("    probThunder: 10.0\n");
            writer.write("    probSnow: 5.0\n");
            writer.write("    icon: \"\uD83C\uDF42\"\n");
            writer.write("  winter:\n");
            writer.write("    tempModifierMin: -10.0\n");
            writer.write("    tempModifierMax: -4.0\n");
            writer.write("    probClear: 30.0\n");
            writer.write("    probRain: 10.0\n");
            writer.write("    probThunder: 5.0\n");
            writer.write("    probSnow: 55.0\n");
            writer.write("    icon: \"\u2744\uFE0F\"\n\n");

            writer.write("# Biomes override\n");
            writer.write("biomes:\n");
            writer.write("  desert:\n");
            writer.write("    baseTemp: 38.0\n");
            writer.write("    baseHumidity: 0.05\n");
            writer.write("    noiseMultiplier: 0.5\n");
            writer.write("    terrainType: \"plains\"\n");
            writer.write("  plains:\n");
            writer.write("    baseTemp: 22.0\n");
            writer.write("    baseHumidity: 0.5\n");
            writer.write("    noiseMultiplier: 1.0\n");
            writer.write("    terrainType: \"plains\"\n");
            writer.write("  forest:\n");
            writer.write("    baseTemp: 18.0\n");
            writer.write("    baseHumidity: 0.75\n");
            writer.write("    noiseMultiplier: 1.0\n");
            writer.write("    terrainType: \"plains\"\n");
            writer.write("  swamp:\n");
            writer.write("    baseTemp: 24.0\n");
            writer.write("    baseHumidity: 0.95\n");
            writer.write("    noiseMultiplier: 0.8\n");
            writer.write("    terrainType: \"valley\"\n");
            writer.write("  mountains:\n");
            writer.write("    baseTemp: 10.0\n");
            writer.write("    baseHumidity: 0.4\n");
            writer.write("    noiseMultiplier: 2.0\n");
            writer.write("    terrainType: \"mountain\"\n\n");

            writer.write("# Climate Events\n");
            writer.write("events:\n");
            writer.write("  blizzard:\n");
            writer.write("    enabled: true\n");
            writer.write("    chance_per_day: 0.05\n");
            writer.write("    minimum_duration: 24000\n");
            writer.write("    maximum_duration: 72000\n");
            writer.write("    cooldown: 48000\n");
            writer.write("    temperature_modifier: -8.0\n");
            writer.write("    humidity_modifier: 0.2\n");
            writer.write("    wind_modifier: 2.0\n");
            writer.write("    weather_override: \"snow\"\n");
            writer.write("  tropical_storm:\n");
            writer.write("    enabled: true\n");
            writer.write("    chance_per_day: 0.07\n");
            writer.write("    minimum_duration: 24000\n");
            writer.write("    maximum_duration: 96000\n");
            writer.write("    cooldown: 48000\n");
            writer.write("    temperature_modifier: -3.0\n");
            writer.write("    humidity_modifier: 0.3\n");
            writer.write("    wind_modifier: 2.5\n");
            writer.write("    weather_override: \"thunder\"\n");
            writer.write("  flood:\n");
            writer.write("    enabled: true\n");
            writer.write("    chance_per_day: 0.05\n");
            writer.write("    minimum_duration: 12000\n");
            writer.write("    maximum_duration: 48000\n");
            writer.write("    cooldown: 24000\n");
            writer.write("    temperature_modifier: -1.0\n");
            writer.write("    humidity_modifier: 0.4\n");
            writer.write("    wind_modifier: 0.5\n");
            writer.write("    weather_override: \"rain\"\n");
            writer.write("  heatwave:\n");
            writer.write("    enabled: true\n");
            writer.write("    chance_per_day: 0.06\n");
            writer.write("    minimum_duration: 24000\n");
            writer.write("    maximum_duration: 72000\n");
            writer.write("    cooldown: 48000\n");
            writer.write("    temperature_modifier: 6.0\n");
            writer.write("    humidity_modifier: -0.2\n");
            writer.write("    wind_modifier: -0.2\n");
            writer.write("    weather_override: \"clear\"\n");
            writer.write("  cold_snap:\n");
            writer.write("    enabled: true\n");
            writer.write("    chance_per_day: 0.06\n");
            writer.write("    minimum_duration: 24000\n");
            writer.write("    maximum_duration: 72000\n");
            writer.write("    cooldown: 48000\n");
            writer.write("    temperature_modifier: -6.0\n");
            writer.write("    humidity_modifier: -0.1\n");
            writer.write("    wind_modifier: 0.2\n");
            writer.write("    weather_override: \"clear\"\n");
            writer.write("  severe_thunderstorm:\n");
            writer.write("    enabled: true\n");
            writer.write("    chance_per_day: 0.01\n");
            writer.write("    minimum_duration: 12000\n");
            writer.write("    maximum_duration: 36000\n");
            writer.write("    cooldown: 72000\n");
            writer.write("    temperature_modifier: -2.0\n");
            writer.write("    humidity_modifier: 0.2\n");
            writer.write("    wind_modifier: 1.2\n");
            writer.write("    weather_override: \"thunder\"\n");
            writer.write("  drought:\n");
            writer.write("    enabled: true\n");
            writer.write("    chance_per_day: 0.05\n");
            writer.write("    minimum_duration: 48000\n");
            writer.write("    maximum_duration: 144000\n");
            writer.write("    cooldown: 96000\n");
            writer.write("    temperature_modifier: 4.0\n");
            writer.write("    humidity_modifier: -0.4\n");
            writer.write("    wind_modifier: -0.5\n");
            writer.write("    weather_override: \"clear\"\n\n");

            writer.write("# Event chaining probabilities\n");
            writer.write("eventChaining:\n");
            writer.write("  summer_tropical_storm:\n");
            writer.write("    flood: 0.35\n");
            writer.write("  winter_cold_snap:\n");
            writer.write("    blizzard: 0.40\n");
            writer.write("  heatwave_drought:\n");
            writer.write("    drought: 0.50\n");
        } catch (IOException e) {
            ServerSeasons.LOGGER.error("Failed to write default climate-config.yml", e);
        }
    }

    public static boolean save() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CONFIG_FILE))) {
            ClimateConfig cfg = currentConfig;

            writer.write("defaultSmoothingFactor: " + cfg.defaultSmoothingFactor + "\n");
            writer.write("comfortMin: " + cfg.comfortMin + "\n");
            writer.write("comfortMax: " + cfg.comfortMax + "\n");
            writer.write("coldSlowness: " + cfg.coldSlowness + "\n");
            writer.write("coldExtreme: " + cfg.coldExtreme + "\n");
            writer.write("hotDamage: " + cfg.hotDamage + "\n");
            writer.write("hotExtreme: " + cfg.hotExtreme + "\n");
            writer.write("equilibrium: " + cfg.equilibrium + "\n\n");

            writer.write("altitudeBaseY: " + cfg.altitudeBaseY + "\n");
            writer.write("altitudeLapseRate: " + cfg.altitudeLapseRate + "\n");
            writer.write("altitudeMinClamp: " + cfg.altitudeMinClamp + "\n");
            writer.write("altitudeMaxClamp: " + cfg.altitudeMaxClamp + "\n\n");

            writer.write("caveShallowOffset: " + cfg.caveShallowOffset + "\n");
            writer.write("caveDeepMin: " + cfg.caveDeepMin + "\n");
            writer.write("caveDeepMax: " + cfg.caveDeepMax + "\n");
            writer.write("caveLavaHeatModifier: " + cfg.caveLavaHeatModifier + "\n");
            writer.write("caveLavaRange: " + cfg.caveLavaRange + "\n\n");

            writer.write("hudFormat: \"" + cfg.hudFormat.replace("\"", "\\\"") + "\"\n");
            writer.write("hudUpdateTicks: " + cfg.hudUpdateTicks + "\n\n");

            writer.write("seasons:\n");
            for (Map.Entry<String, ClimateConfig.SeasonConfig> entry : cfg.seasons.entrySet()) {
                ClimateConfig.SeasonConfig s = entry.getValue();
                writer.write("  " + entry.getKey() + ":\n");
                writer.write("    tempModifierMin: " + s.tempModifierMin + "\n");
                writer.write("    tempModifierMax: " + s.tempModifierMax + "\n");
                writer.write("    probClear: " + s.probClear + "\n");
                writer.write("    probRain: " + s.probRain + "\n");
                writer.write("    probThunder: " + s.probThunder + "\n");
                writer.write("    probSnow: " + s.probSnow + "\n");
                writer.write("    icon: \"" + (s.icon != null ? s.icon.replace("\"", "\\\"") : "") + "\"\n");
            }

            writer.write("\nbiomes:\n");
            for (Map.Entry<String, ClimateConfig.BiomeConfig> entry : cfg.biomes.entrySet()) {
                ClimateConfig.BiomeConfig b = entry.getValue();
                writer.write("  " + entry.getKey() + ":\n");
                writer.write("    baseTemp: " + b.baseTemp + "\n");
                writer.write("    baseHumidity: " + b.baseHumidity + "\n");
                writer.write("    noiseMultiplier: " + b.noiseMultiplier + "\n");
                writer.write("    terrainType: \"" + (b.terrainType != null ? b.terrainType : "plains") + "\"\n");
            }

            writer.write("\nevents:\n");
            for (Map.Entry<String, ClimateConfig.EventConfig> entry : cfg.events.entrySet()) {
                ClimateConfig.EventConfig e = entry.getValue();
                writer.write("  " + entry.getKey() + ":\n");
                writer.write("    enabled: " + e.enabled + "\n");
                writer.write("    chance_per_day: " + e.chancePerDay + "\n");
                writer.write("    minimum_duration: " + e.minDurationTicks + "\n");
                writer.write("    maximum_duration: " + e.maxDurationTicks + "\n");
                writer.write("    cooldown: " + e.cooldownTicks + "\n");
                writer.write("    temperature_modifier: " + e.tempModifier + "\n");
                writer.write("    humidity_modifier: " + e.humidityModifier + "\n");
                writer.write("    wind_modifier: " + e.windModifier + "\n");
                writer.write("    weather_override: \"" + (e.weatherOverride != null ? e.weatherOverride : "none") + "\"\n");
            }

            writer.write("\neventChaining:\n");
            for (Map.Entry<String, Map<String, Double>> entry : cfg.eventChaining.entrySet()) {
                writer.write("  " + entry.getKey() + ":\n");
                for (Map.Entry<String, Double> inner : entry.getValue().entrySet()) {
                    writer.write("    " + inner.getKey() + ": " + inner.getValue() + "\n");
                }
            }

            ServerSeasons.LOGGER.info("Climate configuration saved.");
            return true;
        } catch (IOException e) {
            ServerSeasons.LOGGER.error("Failed to save climate-config.yml", e);
            return false;
        }
    }
}
