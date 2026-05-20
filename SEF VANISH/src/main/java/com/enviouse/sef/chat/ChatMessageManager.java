package com.enviouse.sef.chat;

import com.enviouse.sef.ServerEssentialsForge;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stores chat messages so the /ans reply system can look up original senders.
 */
public class ChatMessageManager {
    private static final AtomicLong ID_COUNTER = new AtomicLong(1);
    private static final Map<Long, ChatRecord> MESSAGES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();

    public record ChatRecord(long id, UUID senderUuid, String rawName, String formattedName, String message, long timestamp) {}

    public static void init(MinecraftServer server) {
        MESSAGES.clear();
        LAST_MESSAGE.clear();
        ID_COUNTER.set(1);
        ServerEssentialsForge.LOGGER.info("[SEF] Chat message manager initialized");
    }

    /**
     * Records a chat message and returns a unique message ID.
     */
    public static long recordMessage(UUID senderUuid, String rawName, String formattedName, String message) {
        long id = ID_COUNTER.getAndIncrement();
        MESSAGES.put(id, new ChatRecord(id, senderUuid, rawName, formattedName, message, System.currentTimeMillis()));
        LAST_MESSAGE.put(senderUuid, id);
        return id;
    }

    public static ChatRecord getMessage(long id) {
        return MESSAGES.get(id);
    }

    public static ChatRecord getLastMessage(UUID uuid) {
        Long id = LAST_MESSAGE.get(uuid);
        return id != null ? MESSAGES.get(id) : null;
    }

    public static void handleLogout(UUID uuid) {
        LAST_MESSAGE.remove(uuid);
    }
}

