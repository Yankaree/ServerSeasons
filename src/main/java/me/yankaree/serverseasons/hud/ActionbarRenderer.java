package me.yankaree.serverseasons.hud;

import me.yankaree.serverseasons.config.ClimateConfig;
import me.yankaree.serverseasons.config.ConfigLoader;
import me.yankaree.serverseasons.engine.TemperatureEngine;
import me.yankaree.serverseasons.season.SeasonManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class ActionbarRenderer {

    public static void renderHUD(ServerPlayer player) {
        UUID uuid = player.getUUID();
        double temp = TemperatureEngine.getPlayerTemperature(uuid);
        ClimateConfig cfg = ConfigLoader.getConfig();

        String color;
        String state;

        if (temp <= cfg.coldExtreme) {
            color = "§9";
            state = "Freezing ❄";
        } else if (temp < cfg.comfortMin) {
            color = "§b";
            state = "Cool";
        } else if (temp <= cfg.comfortMax) {
            color = "§a";
            state = "Comfortable";
        } else if (temp < cfg.hotDamage) {
            color = "§6";
            state = "Hot";
        } else {
            color = "§c";
            state = "Heatstroke Risk 🔥";
        }

        String seasonIcon = SeasonManager.getIcon();
        String hudString = String.format("§7🌡 %s%s°C §7| %s%s §7| %s", color, temp, color, state, seasonIcon);

        player.sendSystemMessage(Component.literal(hudString), true);
    }
}
