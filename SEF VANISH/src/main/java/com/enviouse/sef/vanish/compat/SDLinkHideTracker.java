package com.enviouse.sef.vanish.compat;

import com.enviouse.sef.ServerEssentialsForge;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reference-counting layer on top of SDLink's HiddenPlayersManager.
 *
 * SDLink's hidePlayer/unhidePlayer is a single-entry-per-UUID store:
 * unhiding always removes the entry regardless of the original hide type.
 *
 * Multiple systems (vanish, admin chat toggle, private message toggle) need to
 * independently hide a player from Discord. This tracker keeps an EnumSet of
 * active reasons per UUID and only calls the real SDLink hide/unhide when
 * transitioning between zero and at least one reason (or vice versa).
 *
 * On player logout, {@link #clearAll(UUID)} must be called to remove all
 * reasons and unhide the player, preventing stale hidden-player entries.
 */
public class SDLinkHideTracker {
    private static final Logger LOGGER = LogManager.getLogger("SEF/SDLinkHideTracker");

    public enum HideReason {
        /** Player is vanished via /v */
        VANISH,
        /** Player has admin chat toggled via /chat admin */
        ADMIN_CHAT,
        /** Player has private chat toggled via /pchat */
        PRIVATE_MSG
    }

    /** Active hide reasons per player UUID. Thread-safe. */
    private static final ConcurrentHashMap<UUID, EnumSet<HideReason>> activeReasons = new ConcurrentHashMap<>();

    /**
     * Add a hide reason for the given player.
     * If this is the FIRST reason (player was previously not hidden), calls SDLink hidePlayer.
     */
    public static void addReason(ServerPlayer player, HideReason reason) {
        if (!ServerEssentialsForge.sdlinkDetected) return;
        addReasonInternal(player, reason);
    }

    /**
     * Remove a hide reason for the given player.
     * If this was the LAST reason (player now has zero reasons), calls SDLink unhidePlayer.
     */
    public static void removeReason(ServerPlayer player, HideReason reason) {
        if (!ServerEssentialsForge.sdlinkDetected) return;
        removeReasonInternal(player.getUUID(), player, reason);
    }

    /**
     * Remove a hide reason by UUID (for cases where ServerPlayer isn't available, e.g. logout cleanup).
     */
    public static void removeReason(UUID uuid, HideReason reason) {
        if (!ServerEssentialsForge.sdlinkDetected) return;
        removeReasonInternal(uuid, null, reason);
    }

    /**
     * Clear ALL hide reasons for a player (used on logout).
     * If the player had any reasons, unhides from SDLink.
     */
    public static void clearAll(UUID uuid) {
        if (!ServerEssentialsForge.sdlinkDetected) return;
        EnumSet<HideReason> removed = activeReasons.remove(uuid);
        if (removed != null && !removed.isEmpty()) {
            SDLinkCompat.setHidden(uuid.toString(), null, false);
            LOGGER.debug("Cleared all hide reasons for {} on logout (had: {})", uuid, removed);
        }
    }

    /**
     * Check if a player has any active hide reasons.
     */
    public static boolean isHidden(UUID uuid) {
        EnumSet<HideReason> reasons = activeReasons.get(uuid);
        return reasons != null && !reasons.isEmpty();
    }

    /**
     * Check if a player has a specific hide reason.
     */
    public static boolean hasReason(UUID uuid, HideReason reason) {
        EnumSet<HideReason> reasons = activeReasons.get(uuid);
        return reasons != null && reasons.contains(reason);
    }

    // --- Internal ---

    private static void addReasonInternal(ServerPlayer player, HideReason reason) {
        UUID uuid = player.getUUID();
        final boolean[] needsHide = {false};

        activeReasons.compute(uuid, (key, existing) -> {
            if (existing == null || existing.isEmpty()) {
                needsHide[0] = true;
                return EnumSet.of(reason);
            }
            existing.add(reason);
            return existing;
        });

        if (needsHide[0]) {
            SDLinkCompat.setHidden(player.getStringUUID(), player.getName().getString(), true);
            LOGGER.debug("Hiding {} from SDLink (first reason: {})", player.getGameProfile().getName(), reason);
        } else {
            LOGGER.debug("Added hide reason {} for {} (already hidden)", reason, player.getGameProfile().getName());
        }
    }

    private static void removeReasonInternal(UUID uuid, ServerPlayer player, HideReason reason) {
        final boolean[] needsUnhide = {false};

        activeReasons.compute(uuid, (key, existing) -> {
            if (existing == null) return null;
            existing.remove(reason);
            if (existing.isEmpty()) {
                needsUnhide[0] = true;
                return null; // remove empty set from map
            }
            return existing;
        });

        if (needsUnhide[0]) {
            SDLinkCompat.setHidden(uuid.toString(), null, false);
            String name = player != null ? player.getGameProfile().getName() : uuid.toString();
            LOGGER.debug("Unhiding {} from SDLink (last reason {} removed)", name, reason);
        } else {
            String name = player != null ? player.getGameProfile().getName() : uuid.toString();
            LOGGER.debug("Removed hide reason {} for {} (still hidden for other reasons)", reason, name);
        }
    }
}


