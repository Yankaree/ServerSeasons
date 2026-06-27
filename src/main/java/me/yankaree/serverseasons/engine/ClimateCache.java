package me.yankaree.serverseasons.engine;

import me.yankaree.serverseasons.config.ClimateConfig;
import me.yankaree.serverseasons.config.ConfigLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ClimateCache {
    private static final Map<String, ClimateConfig.BiomeConfig> cache = new HashMap<>();

    public static void clear() {
        cache.clear();
    }

    public static ClimateConfig.BiomeConfig getBiomeConfig(ServerLevel world, BlockPos pos) {
        Holder<Biome> biomeHolder = world.getBiome(pos);
        String biomeKey = getBiomeKey(world, biomeHolder);

        return cache.computeIfAbsent(biomeKey, key -> {
            ClimateConfig config = ConfigLoader.getConfig();
            for (Map.Entry<String, ClimateConfig.BiomeConfig> entry : config.biomes.entrySet()) {
                if (key.contains(entry.getKey().toLowerCase())) {
                    return entry.getValue();
                }
            }
            double vanillaTemp = biomeHolder.value().getBaseTemperature();
            double defaultHumidity = 0.5;
            String terrainType = "plains";

            if (key.contains("mountain") || key.contains("hills") || key.contains("peaks")) {
                terrainType = "mountain";
            } else if (key.contains("valley") || key.contains("river") || key.contains("swamp")) {
                terrainType = "valley";
            }

            double estimatedTemp = 10.0 + (vanillaTemp * 15.0);
            return new ClimateConfig.BiomeConfig(estimatedTemp, defaultHumidity, 1.0, terrainType);
        });
    }

    private static String getBiomeKey(ServerLevel world, Holder<Biome> biomeHolder) {
        Optional<ResourceKey<Biome>> keyOpt = biomeHolder.unwrapKey();
        if (keyOpt.isPresent()) {
            return keyOpt.get().identifier().toString();
        }
        Identifier id = world.registryAccess().lookupOrThrow(Registries.BIOME).getKey(biomeHolder.value());
        return id != null ? id.toString() : "minecraft:plains";
    }
}
