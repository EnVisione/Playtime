package com.enviouse.sef.vanish.compat;

import com.enviouse.sef.ServerEssentialsForge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Vanish ↔ Playtime integration.
 *
 * <p>When a player vanishes we call {@code SessionTracker.pauseSession(UUID)}
 * to stop their playtime clock without dragging them through the heavy
 * leave/join lifecycle (which would broadcast first-join messages,
 * re-evaluate ranks, re-trigger LP sync, etc.). Unvanish calls
 * {@code resumeSession(UUID)}.
 *
 * <p>All Playtime access is reflective so SEF builds without a compile-time
 * dependency on Playtime, and gracefully no-ops when:
 * <ul>
 *   <li>Playtime is not installed at all (silent — normal case),</li>
 *   <li>Playtime is installed but predates the pause/resume API (single WARN
 *       on first attempt, telling the user to update),</li>
 *   <li>The session tracker isn't ready yet (e.g. server still starting).</li>
 * </ul>
 */
public final class PlaytimeCompat {
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("SEF/Playtime");

    private static final String PLAYTIME_MODID = "playtime";

    /** Lifecycle of the reflection probe. */
    private enum State { UNCHECKED, READY, MOD_ABSENT, API_TOO_OLD, BROKEN }
    private static volatile State state = State.UNCHECKED;

    private static Method getSessionTracker;
    private static Method pauseSessionMethod;
    private static Method resumeSessionMethod;

    private PlaytimeCompat() {}

    private static synchronized void init() {
        if (state != State.UNCHECKED) return;

        if (!ModList.get().isLoaded(PLAYTIME_MODID)) {
            state = State.MOD_ABSENT;
            return;
        }

        try {
            Class<?> playtimeClass = Class.forName("com.enviouse.playtime.Playtime");
            getSessionTracker = playtimeClass.getMethod("getSessionTracker");
            Class<?> sessionTrackerClass =
                    Class.forName("com.enviouse.playtime.service.SessionTracker");
            try {
                pauseSessionMethod = sessionTrackerClass.getMethod("pauseSession", UUID.class);
                resumeSessionMethod = sessionTrackerClass.getMethod("resumeSession", UUID.class);
            } catch (NoSuchMethodException e) {
                state = State.API_TOO_OLD;
                LOGGER.warn(
                    "Playtime is installed but does not expose pauseSession(UUID) / "
                  + "resumeSession(UUID). Vanish integration is disabled until Playtime "
                  + "is updated. Missing method: {}", e.getMessage());
                return;
            }
            state = State.READY;
            LOGGER.info("Playtime pause/resume API bound — vanish integration active.");
        } catch (Throwable t) {
            state = State.BROKEN;
            LOGGER.warn("Playtime API binding failed; vanish integration disabled: {}",
                    t.toString());
            ServerEssentialsForge.LOGGER.trace("Playtime binding stack trace", t);
        }
    }

    /** Called by VanishingHandler when a player's vanish state changes. */
    public static void onVanishChange(ServerPlayer player, boolean vanished) {
        init();
        if (state != State.READY) return;
        if (player == null) return;

        try {
            Object tracker = getSessionTracker.invoke(null);
            if (tracker == null) return; // Playtime not ready yet (server still starting)
            UUID uuid = player.getUUID();
            if (vanished) {
                pauseSessionMethod.invoke(tracker, uuid);
            } else {
                resumeSessionMethod.invoke(tracker, uuid);
            }
        } catch (Throwable t) {
            LOGGER.warn("Playtime tracking update failed for {}: {}",
                    player.getGameProfile().getName(), t.toString());
            ServerEssentialsForge.LOGGER.trace("Playtime invocation stack trace", t);
        }
    }
}
