package com.pvpbot;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class PvpBotMod implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                BotCommands.register(dispatcher));
        ServerTickEvents.END_WORLD_TICK.register(BotManager::tickWorld);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> BotManager.BOTS.clear());
    }
}
