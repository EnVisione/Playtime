package com.enviouse.sef.invlock;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages inventory-locked players. Runtime-only (clears on server restart).
 * Locked players cannot open containers, drop items, or pick up items.
 */
public class InvLockManager {
    private static final Set<UUID> lockedPlayers = ConcurrentHashMap.newKeySet();

    public static boolean isLocked(UUID uuid) {
        return lockedPlayers.contains(uuid);
    }

    public static boolean toggle(UUID uuid) {
        if (lockedPlayers.contains(uuid)) {
            lockedPlayers.remove(uuid);
            return false; // now unlocked
        } else {
            lockedPlayers.add(uuid);
            return true; // now locked
        }
    }

    public static void lock(UUID uuid) {
        lockedPlayers.add(uuid);
    }

    public static void unlock(UUID uuid) {
        lockedPlayers.remove(uuid);
    }

    public static Set<UUID> getLockedPlayers() {
        return Collections.unmodifiableSet(lockedPlayers);
    }

    public static void clear() {
        lockedPlayers.clear();
    }
}

