package me.yankaree.serverseasons.config;

import java.util.HashMap;
import java.util.Map;

public class ClimateConfig {
    // Temperature settings - Vietnamese tropical climate optimized
    public double defaultSmoothingFactor = 0.05;
    public double comfortMin = 18.0;        // Comfortable minimum: 18°C
    public double comfortMax = 32.0;        // Comfortable maximum: 32°C (Việt Nam 28-32°C)
    public double coldSlowness = 10.0;      // Slowness I starts at 10°C
    public double coldExtreme = 0.0;        // Extreme cold (freeze) at 0°C (changed from 8°C)
    public double hotDamage = 40.0;         // Weakness II starts at 40°C
    public double hotExtreme = 45.0;        // Damage + Nausea at 45°C (changed from 40°C)
    public double equilibrium = 37.0;       // Body equilibrium: 37°C

    // Altitude settings
    public int altitudeBaseY = 64;
    public double altitudeLapseRate = -0.05; // Drop per block above base
    public double altitudeMinClamp = -15.0;
    public double altitudeMaxClamp = 15.0;

    // Cave settings
    public double caveShallowOffset = -3.0;
    public double caveDeepMin = 15.0;
    public double caveDeepMax = 22.0;
    public double caveLavaHeatModifier = 8.0;
    public int caveLavaRange = 8;

    // HUD settings
    public String hudFormat = "\uD83C\uDF21 %s°C | %s | %s";
    public int hudUpdateTicks = 20;

    // Season settings
    public Map<String, SeasonConfig> seasons = new HashMap<>();

    // Biome multipliers (category mapping or name mapping)
    public Map<String, BiomeConfig> biomes = new HashMap<>();

    // Event settings
    public Map<String, EventConfig> events = new HashMap<>();
    
    // Event chaining settings
    public Map<String, Map<String, Double>> eventChaining = new HashMap<>();

    public static class SeasonConfig {
        public double tempModifierMin;
        public double tempModifierMax;
        // Probabilities sum to 100 or relative weight
        public double probClear;
        public double probRain;
        public double probThunder;
        public double probSnow;
        public String icon;

        public SeasonConfig(double tempModifierMin, double tempModifierMax, double probClear, double probRain, double probThunder, double probSnow, String icon) {
            this.tempModifierMin = tempModifierMin;
            this.tempModifierMax = tempModifierMax;
            this.probClear = probClear;
            this.probRain = probRain;
            this.probThunder = probThunder;
            this.probSnow = probSnow;
            this.icon = icon;
        }
    }

    public static class BiomeConfig {
        public double baseTemp;
        public double baseHumidity;
        public double noiseMultiplier = 1.0;
        public String terrainType = "plains"; // mountain, valley, plains

        public BiomeConfig(double baseTemp, double baseHumidity, double noiseMultiplier, String terrainType) {
            this.baseTemp = baseTemp;
            this.baseHumidity = baseHumidity;
            this.noiseMultiplier = noiseMultiplier;
            this.terrainType = terrainType;
        }
    }

    public static class EventConfig {
        public boolean enabled = true;
        public double chancePerDay = 0.05;
        public int minDurationTicks = 24000; // 1 day
        public int maxDurationTicks = 72000; // 3 days
        public int cooldownTicks = 24000;
        public double tempModifier = 0.0;
        public double humidityModifier = 0.0;
        public double windModifier = 0.0;
        public String weatherOverride = "none"; // none, clear, rain, thunder, snow

        public EventConfig() {}

        public EventConfig(boolean enabled, double chancePerDay, int minDurationTicks, int maxDurationTicks, int cooldownTicks, double tempModifier, double humidityModifier, double windModifier, String weatherOverride) {
            this.enabled = enabled;
            this.chancePerDay = chancePerDay;
            this.minDurationTicks = minDurationTicks;
            this.maxDurationTicks = maxDurationTicks;
            this.cooldownTicks = cooldownTicks;
            this.tempModifier = tempModifier;
            this.humidityModifier = humidityModifier;
            this.windModifier = windModifier;
            this.weatherOverride = weatherOverride;
        }
    }
}
