package me.yankaree.serverseasons.engine;

import me.yankaree.serverseasons.config.ClimateConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class HumiditySystem {

    public static double getHumidity(ServerLevel world, BlockPos pos) {
        ClimateConfig.BiomeConfig biomeCfg = ClimateCache.getBiomeConfig(world, pos);
        double baseHumidity = biomeCfg.baseHumidity;

        double noise = Math.sin(pos.getX() * 0.002) * Math.cos(pos.getZ() * 0.002) * 0.1 * biomeCfg.noiseMultiplier;
        double finalHumidity = baseHumidity + noise;

        return Math.max(0.0, Math.min(1.0, finalHumidity));
    }

    public static double getFeelsLikeOffset(double humidity) {
        if (humidity > 0.70) {
            return 2.0 + ((humidity - 0.70) / 0.30) * 4.0;
        } else if (humidity < 0.30) {
            return -1.0 - ((0.30 - humidity) / 0.30) * 2.0;
        }
        return 0.0;
    }
}
