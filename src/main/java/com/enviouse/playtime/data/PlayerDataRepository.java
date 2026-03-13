package com.enviouse.playtime.data;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.UUID;

/**
 * Abstraction over player data persistence.
 * Phase 1 uses JSON; Phase 3 may add SQLite.
 */
public interface PlayerDataRepository {

    /** Load all player data from disk. Returns true on success. */
    boolean load();

    /** Save all dirty data to disk. */
    void save(boolean force);

    /** Get a player record by UUID, or null if not found. */
    @Nullable PlayerRecord getPlayer(UUID uuid);

    /** Find a player record by username (case-insensitive), or null. */
    @Nullable PlayerRecord getPlayerByName(String username);

    /** Store or update a player record. */
    void putPlayer(PlayerRecord record);

    /** Remove a player record. */
    void removePlayer(UUID uuid);

    /** Get all stored player records. */
    Collection<PlayerRecord> getAllPlayers();

    /** Mark data as needing a save on next save() call. */
    void markDirty();

    /** Whether data was loaded successfully. */
    boolean isLoaded();
}

