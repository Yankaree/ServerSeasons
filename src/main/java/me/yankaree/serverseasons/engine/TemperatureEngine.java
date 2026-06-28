package me.yankaree.serverseasons.engine;

import me.yankaree.serverseasons.config.ClimateConfig;
import me.yankaree.serverseasons.config.ConfigLoader;
import me.yankaree.serverseasons.season.SeasonManager;
import me.yankaree.serverseasons.event.ClimateEventManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TemperatureEngine {
    private static final Map<UUID, Double> playerTemperatures = new HashMap<>();

    public static double getPlayerTemperature(UUID uuid) {
        return playerTemperatures.getOrDefault(uuid, ConfigLoader.getConfig().equilibrium);
    }

    public static void setPlayerTemperature(UUID uuid, double temp) {
        playerTemperatures.put(uuid, temp);
    }

    public static void removePlayer(UUID uuid) {
        playerTemperatures.remove(uuid);
    }

    public static double computeTargetTemperature(ServerPlayer player) {
        ServerLevel world = (ServerLevel) player.level();
        BlockPos pos = new BlockPos((int) player.getX(), (int) player.getY(), (int) player.getZ());
        ClimateConfig cfg = ConfigLoader.getConfig();

        CaveSystem.CaveState cave = CaveSystem.getCaveState(world, pos);

        double targetTemp;
        if (cave.isUnderground && cave.overrideTemp != null) {
            targetTemp = cave.overrideTemp + cave.lavaModifier;
        } else {
            ClimateConfig.BiomeConfig biomeCfg = ClimateCache.getBiomeConfig(world, pos);
            double baseTemp = biomeCfg.baseTemp;

            long timeOfDay = world.getGameTime() % 24000;
            double timeCurve = Math.cos((timeOfDay - 6000.0) * (2 * Math.PI / 24000.0)) * 3.0;

            double weatherMod = 0.0;
            if (world.isThundering()) {
                weatherMod = -4.0;
            } else if (world.isRaining()) {
                weatherMod = -2.0;
            }

            double altMod = (pos.getY() - cfg.altitudeBaseY) * cfg.altitudeLapseRate;
            altMod = Math.max(cfg.altitudeMinClamp, Math.min(cfg.altitudeMaxClamp, altMod));

            double seasonMod = SeasonManager.getTemperatureModifier();

            double noise = Math.sin(pos.getX() * 0.01) * Math.cos(pos.getZ() * 0.01) * 1.5 * biomeCfg.noiseMultiplier;

            double eventMod = ClimateEventManager.getTemperatureModifier();

            targetTemp = baseTemp + timeCurve + weatherMod + altMod + seasonMod + noise + eventMod;

            if (cave.isUnderground) {
                targetTemp += cave.caveModifier + cave.lavaModifier;
            }
        }

        if (player.isInWaterOrRain()) {
            targetTemp -= 5.0;
        }

        double inventoryTempMod = 0.0;
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        inventoryTempMod += getHandItemTempModifier(mainHand);
        inventoryTempMod += getHandItemTempModifier(offHand);

        targetTemp += inventoryTempMod;

        double blockInfluence = 0.0;
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();
        
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    checkPos.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (world.hasChunkAt(checkPos)) {
                        BlockState state = world.getBlockState(checkPos);
                        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        
                        if (dist < 4.0) {
                            if (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) {
                                blockInfluence += (4.0 - dist) * 1.5;
                            } else if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                                blockInfluence += (3.0 - dist) * 1.0;
                            } else if (state.is(Blocks.MAGMA_BLOCK)) {
                                blockInfluence += (3.0 - dist) * 0.5;
                            } else if (state.is(Blocks.BLUE_ICE)) {
                                blockInfluence -= (4.0 - dist) * 1.5;
                            } else if (state.is(Blocks.PACKED_ICE)) {
                                blockInfluence -= (3.0 - dist) * 1.0;
                            } else if (state.is(Blocks.ICE)) {
                                blockInfluence -= (3.0 - dist) * 0.7;
                            }
                        }
                    }
                }
            }
        }
        
        blockInfluence = Math.max(-12.0, Math.min(12.0, blockInfluence));
        targetTemp += blockInfluence;

        double humidity = HumiditySystem.getHumidity(world, pos);
        double feelsLikeOffset = HumiditySystem.getFeelsLikeOffset(humidity);
        
        double eventHumMod = ClimateEventManager.getHumidityModifier();
        double finalFeelsLikeTemp = targetTemp + feelsLikeOffset + (eventHumMod * 10.0);

        return finalFeelsLikeTemp;
    }

    private static double getHandItemTempModifier(ItemStack stack) {
        if (stack.is(Items.LAVA_BUCKET)) {
            return 4.0;
        } else if (stack.is(Items.GLOWSTONE)) {
            return 2.0;
        } else if (stack.is(Items.BLUE_ICE)) {
            return -4.0;
        } else if (stack.is(Items.PACKED_ICE) || stack.is(Items.ICE)) {
            return -3.0;
        } else if (stack.is(Items.POWDER_SNOW_BUCKET)) {
            return -2.5;
        }
        return 0.0;
    }

    public static void tickPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        ClimateConfig cfg = ConfigLoader.getConfig();
        double currentTemp = getPlayerTemperature(uuid);

        double targetTemp = computeTargetTemperature(player);

        double newTemp = currentTemp + (targetTemp - currentTemp) * cfg.defaultSmoothingFactor;
        newTemp = Math.round(newTemp * 10.0) / 10.0;
        
        setPlayerTemperature(uuid, newTemp);
        applyGameplayEffects(player, newTemp, cfg);
    }

    private static void applyGameplayEffects(ServerPlayer player, double temp, ClimateConfig cfg) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        // Hot effects
        if (temp > cfg.hotDamage) {
            // Weakness II at 35°C+ (Vietnamese climate: stronger heat effect)
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1, true, false));
        }
        if (temp >= 40.0) {
            // REMOVED: heat damage - only nausea at 40°C+
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, true, false));
        }

        // Cold effects
        if (temp < 15.0 && temp >= 8.0) {
            // Slowness I in 8–15°C range
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, true, false));
        }
        
        if (temp < 8.0) {
            // Extreme cold below 8°C: Slowness II + damage + freeze
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, true, false));
            player.hurt(player.damageSources().generic(), 1.0f);
            player.setTicksFrozen(Math.min(player.getTicksFrozen() + 15, 140));
        } else {
            if (player.getTicksFrozen() > 0) {
                player.setTicksFrozen(Math.max(0, player.getTicksFrozen() - 15));
            }
        }
    }
}
