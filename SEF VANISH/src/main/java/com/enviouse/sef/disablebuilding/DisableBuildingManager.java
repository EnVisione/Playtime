package com.enviouse.sef.disablebuilding;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages players with building disabled. Runtime-only (clears on server restart).
 * Players with building disabled cannot place or break blocks.
 */
public class DisableBuildingManager {
    private static final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();

    public static boolean isDisabled(UUID uuid) {
        return disabledPlayers.contains(uuid);
    }

    public static boolean toggle(UUID uuid) {
        if (disabledPlayers.contains(uuid)) {
            disabledPlayers.remove(uuid);
            return false; // building re-enabled
        } else {
            disabledPlayers.add(uuid);
            return true; // building disabled
        }
    }

    public static Set<UUID> getDisabledPlayers() {
        return Collections.unmodifiableSet(disabledPlayers);
    }

    public static void clear() {
        disabledPlayers.clear();
    }
}

