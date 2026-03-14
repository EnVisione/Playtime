package com.enviouse.playtime.command;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Registers all Playtime mod commands on the Forge event bus.
 */
public class CommandRegistration {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        PlaytimeCommand.register(event.getDispatcher());
        RanksCommand.register(event.getDispatcher());
        ClaimsCommand.register(event.getDispatcher());
        PlaytimeAdminCommand.register(event.getDispatcher());
    }
}
