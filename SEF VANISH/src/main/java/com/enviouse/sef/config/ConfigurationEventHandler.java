package com.enviouse.sef.config;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class ConfigurationEventHandler {
    private static final List<IReloadable> reloadables = new ArrayList<>();

    public static void registerReloadable(IReloadable rel) {
        reloadables.add(rel);
    }

    public static void reloadConfigOptions() {
        for(IReloadable reloadable : reloadables)
            if(reloadable != null)
                reloadable.reloadConfigOptions();
    }

    @SubscribeEvent
    public static void onModConfigReloadingEvent(ModConfigEvent.Reloading e) {
        reloadConfigOptions();
    }
    @SubscribeEvent
    public static void onModConfigLoadingEvent(ModConfigEvent.Loading e) {
        reloadConfigOptions();
    }
}
