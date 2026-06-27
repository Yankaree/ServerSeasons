package me.yankaree.serverseasons;

import me.yankaree.serverseasons.config.ConfigLoader;
import me.yankaree.serverseasons.command.ClimateCommandRegistry;
import me.yankaree.serverseasons.engine.TemperatureEngine;
import me.yankaree.serverseasons.event.ClimateEventManager;
import me.yankaree.serverseasons.hud.ActionbarRenderer;
import me.yankaree.serverseasons.season.SeasonManager;
import me.yankaree.serverseasons.weather.WeatherSystem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerSeasons implements ModInitializer {
	public static final String MOD_ID = "serverseasons";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private int tickCounter = 0;

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing ServerSeasons Climate Simulation system (Mojang mappings)...");

		ConfigLoader.load();

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			SeasonManager.init(server);
			ClimateEventManager.setServerInstance(server);
			ClimateEventManager.loadState(server);
			LOGGER.info("ServerSeasons state restored successfully.");
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SeasonManager.saveSeason();
			ClimateEventManager.saveState(server);
			LOGGER.info("ServerSeasons state saved successfully.");
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			TemperatureEngine.removePlayer(handler.player.getUUID());
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			ClimateCommandRegistry.register(dispatcher);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;
			if (tickCounter >= 20) {
				tickCounter = 0;
				
				ClimateEventManager.tick(server);
				WeatherSystem.tick(server);

				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					TemperatureEngine.tickPlayer(player);
					ActionbarRenderer.renderHUD(player);
				}
			}
		});

		LOGGER.info("ServerSeasons initialized successfully!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
