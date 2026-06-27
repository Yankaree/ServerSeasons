package me.yankaree.serverseasons.engine;

import me.yankaree.serverseasons.config.ClimateConfig;
import me.yankaree.serverseasons.config.ConfigLoader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;

public class CaveSystem {

    public static class CaveState {
        public final boolean isUnderground;
        public final double caveModifier;
        public final double lavaModifier;
        public final Double overrideTemp;

        public CaveState(boolean isUnderground, double caveModifier, double lavaModifier, Double overrideTemp) {
            this.isUnderground = isUnderground;
            this.caveModifier = caveModifier;
            this.lavaModifier = lavaModifier;
            this.overrideTemp = overrideTemp;
        }
    }

    public static CaveState getCaveState(ServerLevel world, BlockPos pos) {
        ClimateConfig cfg = ConfigLoader.getConfig();

        int skyLight = world.getBrightness(LightLayer.SKY, pos);
        boolean isUnderground = skyLight == 0 && pos.getY() < 55;

        if (!isUnderground) {
            return new CaveState(false, 0.0, 0.0, null);
        }

        double lavaMod = 0.0;
        int radius = cfg.caveLavaRange;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        outer:
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dy = -2; dy <= 4; dy += 2) {
                for (int dz = -radius; dz <= radius; dz += 2) {
                    mutablePos.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (world.hasChunkAt(mutablePos) && world.getBlockState(mutablePos).is(Blocks.LAVA)) {
                        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (dist < radius) {
                            lavaMod = ((radius - dist) / radius) * cfg.caveLavaHeatModifier;
                            break outer;
                        }
                    }
                }
            }
        }

        int y = pos.getY();
        if (y <= 15) {
            double deepTemp = (cfg.caveDeepMin + cfg.caveDeepMax) / 2.0;
            return new CaveState(true, 0.0, lavaMod, deepTemp);
        } else {
            double progress = (y - 15.0) / 40.0;
            double shallowOffset = cfg.caveShallowOffset * progress;
            return new CaveState(true, shallowOffset, lavaMod, null);
        }
    }
}
