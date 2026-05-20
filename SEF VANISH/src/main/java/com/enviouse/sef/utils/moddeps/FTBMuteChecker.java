package com.enviouse.sef.utils.moddeps;

import com.enviouse.sef.ServerEssentialsForge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * Checks if a player is muted via FTB Essentials.
 * All FTB Essentials API calls are isolated in this class so that the mod
 * runs correctly even when FTB Essentials is not installed.
 */
public class FTBMuteChecker {

    /**
     * Returns true if FTB Essentials is loaded AND the player is currently muted.
     * Returns false if FTB Essentials is not present or any error occurs.
     */
    public static boolean isMuted(ServerPlayer player) {
        if (!ModList.get().isLoaded("ftbessentials")) {
            return false;
        }
        try {
            return checkMuteStatus(player);
        } catch (Throwable e) {
            ServerEssentialsForge.LOGGER.trace("[SEF] Error checking FTB mute status", e);
            return false;
        }
    }

    /**
     * Isolated method that actually touches FTB Essentials classes.
     * Must only be called when ftbessentials is confirmed loaded.
     */
    private static boolean checkMuteStatus(ServerPlayer player) {
        return dev.ftb.mods.ftbessentials.util.FTBEPlayerData
                .getOrCreate(player)
                .map(dev.ftb.mods.ftbessentials.util.FTBEPlayerData::isMuted)
                .orElse(false);
    }
}

