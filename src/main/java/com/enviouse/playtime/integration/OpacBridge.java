package com.enviouse.playtime.integration;

import com.enviouse.playtime.Config;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.api.IServerDimensionClaimsManagerAPI;
import xaero.pac.common.server.claims.api.IServerRegionClaimsAPI;

import java.util.List;
import java.util.UUID;

/**
 * Optional OpenPAC integration for claim wipe operations.
 * All methods are safe to call without OPAC installed — they will no-op / return 0.
 */
public class OpacBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Check whether the OPAC API is reachable. */
    public boolean isAvailable(MinecraftServer server) {
        if (!Config.opacEnabled) return false;
        try {
            OpenPACServerAPI.get(server);
            return true;
        } catch (Exception | NoClassDefFoundError e) {
            return false;
        }
    }

    /**
     * Wipe all claims belonging to a player UUID.
     *
     * @param server  the server
     * @param playerId  the player's UUID
     * @param dryRun  if true, count but don't actually remove
     * @return number of claims removed (or that would be removed)
     */
    public int wipePlayerClaims(MinecraftServer server, UUID playerId, boolean dryRun) {
        try {
            OpenPACServerAPI api = OpenPACServerAPI.get(server);
            IServerClaimsManagerAPI claims = api.getServerClaimsManager();

            if (!claims.hasPlayerInfo(playerId)) {
                LOGGER.debug("[Playtime] No OPAC claim info for {}", playerId);
                return 0;
            }

            int removed = 0;
            int checked = 0;

            List<? extends IServerDimensionClaimsManagerAPI> dimensions = claims.getDimensionStream().toList();
            for (IServerDimensionClaimsManagerAPI dimMgr : dimensions) {
                ResourceLocation dimension = dimMgr.getDimension();

                List<? extends IServerRegionClaimsAPI> regions = dimMgr.getRegionStream().toList();
                for (IServerRegionClaimsAPI region : regions) {
                    int baseX = region.getX() * 32;
                    int baseZ = region.getZ() * 32;

                    for (int dx = 0; dx < 32; dx++) {
                        for (int dz = 0; dz < 32; dz++) {
                            int cx = baseX + dx;
                            int cz = baseZ + dz;

                            IPlayerChunkClaimAPI state = claims.get(dimension, cx, cz);
                            if (state == null) continue;
                            checked++;

                            if (!state.getPlayerId().equals(playerId)) continue;

                            if (!dryRun) {
                                claims.unclaim(dimension, cx, cz);
                            }
                            removed++;
                        }
                    }
                }
            }

            LOGGER.info("[Playtime] OPAC wipe for {}: checked={}, removed={}, dryRun={}",
                    playerId, checked, removed, dryRun);
            return removed;
        } catch (Exception | NoClassDefFoundError e) {
            LOGGER.warn("[Playtime] OPAC wipe failed for {}: {}", playerId, e.getMessage());
            return 0;
        }
    }
}

