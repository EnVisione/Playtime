package com.enviouse.playtime.service;

import com.enviouse.playtime.Config;
import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.afk.AfkDecisionEngine;
import com.enviouse.playtime.afk.AfkHeuristicState;
import com.enviouse.playtime.data.PlayerDataRepository;
import com.enviouse.playtime.data.PlayerRecord;
import com.enviouse.playtime.data.RankDefinition;
import com.enviouse.playtime.network.AfkSyncS2CPacket;
import com.enviouse.playtime.network.PlaytimeNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player session activity and AFK state.
 * <p>
 * <b>Layer 1 — Signal Bitmask (fast check):</b><br>
 * Position, rotation, hotbar, sprint, and interaction events must all contribute
 * to prove the player is human. A player must produce at least {@code Config.afkMinSignals}
 * distinct signal types within the timeout window to be considered active.
 * <p>
 * <b>Layer 2 — Heuristic Analysis (pattern check):</b><br>
 * Even when the bitmask says "active", the heuristic engine analyses movement patterns,
 * camera rotation entropy, interaction diversity, and timing regularity to detect
 * AFK pools, mouse macros, auto-clickers, and circle-walking bots. A composite
 * suspicion score above {@code Config.afkHeuristicThreshold} overrides to AFK.
 * <p>
 * This is server-authoritative — clients cannot bypass it.
 * AFK state changes are broadcast to all clients via {@link AfkSyncS2CPacket}.
 */
public class SessionTracker {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Signal type bitmask constants
    private static final int SIG_ROTATION     = 1;
    private static final int SIG_POSITION     = 1 << 1;
    private static final int SIG_HOTBAR       = 1 << 2;
    private static final int SIG_SPRINT       = 1 << 3;
    private static final int SIG_INTERACTION  = 1 << 4;
    // Client-only signals (requires Playtime mod on the client).
    // Public so ClientActivitySignalC2SPacket can build the bitmask client-side.
    public static final int SIG_KEYBOARD      = 1 << 5;
    public static final int SIG_MOUSE_MOVE    = 1 << 6;
    public static final int SIG_MOUSE_CLICK   = 1 << 7;
    public static final int SIG_GUI           = 1 << 8;
    public static final int SIG_SCROLL        = 1 << 9;
    public static final int SIG_INVENTORY     = 1 << 10;
    public static final int SIG_WINDOW_FOCUS  = 1 << 11;

    /** Bitmask of all client-originated signals. */
    private static final int CLIENT_SIGNALS_MASK =
            SIG_KEYBOARD | SIG_MOUSE_MOVE | SIG_MOUSE_CLICK
                    | SIG_GUI | SIG_SCROLL | SIG_INVENTORY | SIG_WINDOW_FOCUS;

    /** Bitmask of server-derived signals — used for anti-spoof correlation. */
    private static final int SERVER_MOTION_MASK = SIG_ROTATION | SIG_POSITION;

    // Interaction action type codes for heuristic tracking
    public static final byte ACTION_BREAK   = 1;
    public static final byte ACTION_PLACE   = 2;
    public static final byte ACTION_ATTACK  = 3;
    public static final byte ACTION_USE     = 4;
    public static final byte ACTION_CHAT    = 5;
    public static final byte ACTION_HOTBAR  = 6;
    public static final byte ACTION_SPRINT  = 7;
    public static final byte ACTION_KEYBOARD   = 8;
    public static final byte ACTION_MOUSE_MOVE = 9;
    public static final byte ACTION_MOUSE_CLICK = 10;
    public static final byte ACTION_GUI        = 11;
    public static final byte ACTION_SCROLL     = 12;
    public static final byte ACTION_INVENTORY  = 13;

    /** Per-player in-memory session state. */
    private static class PlayerSession {
        float lastYaw, lastPitch;
        double lastX, lastY, lastZ;
        int lastHotbarSlot;
        boolean lastSprinting;
        int afkTicks;
        boolean wasAfk;
        boolean afkNotified;
        int signalMask;
        long activeSessionTicks;

        // Heuristic analysis state
        AfkHeuristicState heuristicState;
        AfkDecisionEngine.HeuristicResult lastHeuristicResult;
        int heuristicEvalCounter;
        boolean heuristicFlaggedAfk;

        /**
         * When true, this session is suspended: no playtime accumulation, no
         * AFK evaluation, and no activity-feed sampling. Toggled by external
         * integrations (e.g. SEF vanish) via {@link #pauseSession(UUID)} and
         * {@link #resumeSession(UUID)}. Java default is {@code false}.
         */
        boolean paused;

        // ── Client-signal anti-spoof + rate-limit state ─────────────────────
        /** Bitmask of which client signals fired since last tick eval. */
        int clientSignalMask;
        /** Whether the client reported the OS window as focused on its last packet. */
        boolean clientWindowFocused;
        /** Distinct keys pressed since last keyboard sample (heuristic input). */
        int clientUniqueKeyCount;
        /** Rolling 1-second packet counter for rate-limiting. */
        int clientPacketsThisSecond;
        /** Wall-clock millis when the current rate-limit window started. */
        long clientPacketWindowStartMs;
        /**
         * Suspicion counter: number of consecutive ticks where the client
         * claimed activity but the server saw no rotation/position change.
         * Resets when server motion is observed.
         */
        int clientSuspicionTicks;
        /** True once {@link #clientSuspicionTicks} crosses the configured threshold. */
        boolean clientSuspectedSpoofing;
    }

    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();
    private final PlayerDataRepository repository;
    private final RankEngine rankEngine;

    public SessionTracker(PlayerDataRepository repository, RankEngine rankEngine) {
        this.repository = repository;
        this.rankEngine = rankEngine;
    }

    /** Called when a player logs in. */
    public void onPlayerJoin(MinecraftServer server, ServerPlayer player) {
        UUID uuid = player.getUUID();
        String name = player.getGameProfile().getName();

        PlayerSession session = new PlayerSession();
        session.lastYaw = player.getYRot();
        session.lastPitch = player.getXRot();
        session.lastX = player.getX();
        session.lastY = player.getY();
        session.lastZ = player.getZ();
        session.lastHotbarSlot = player.getInventory().selected;
        session.lastSprinting = player.isSprinting();
        session.afkTicks = 0;
        session.wasAfk = false;
        session.afkNotified = false;
        session.signalMask = 0;
        session.activeSessionTicks = 0;
        session.heuristicState = new AfkHeuristicState(Config.afkHeuristicWindow);
        session.lastHeuristicResult = null;
        session.heuristicEvalCounter = 0;
        session.heuristicFlaggedAfk = false;
        session.clientSignalMask = 0;
        session.clientWindowFocused = false;
        session.clientUniqueKeyCount = 0;
        session.clientPacketsThisSecond = 0;
        session.clientPacketWindowStartMs = 0L;
        session.clientSuspicionTicks = 0;
        session.clientSuspectedSpoofing = false;
        sessions.put(uuid, session);

        PlayerRecord record = repository.getPlayer(uuid);
        boolean isFirstJoin = (record == null);

        if (isFirstJoin) {
            record = new PlayerRecord(uuid, name);
            repository.putPlayer(record);
            LOGGER.info("[Playtime] First join for {} ({})", name, uuid);

            RankDefinition initialRank = rankEngine.getCurrentRank(0);
            record.setCurrentRankId(initialRank.getId());
            repository.markDirty();

            if (Playtime.getLuckPerms() != null) {
                Playtime.getLuckPerms().syncRank(uuid, null, initialRank, server);
            }

            if (Config.firstJoinBroadcast) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("§a" + name + " has joined the server for the first time, say hi!"),
                        false
                );
            }

            if (Config.opacEnabled) {
                server.getCommands().performPrefixedCommand(
                        player.createCommandSourceStack().withSuppressedOutput(),
                        "openpac player-config set claims.color " + Config.defaultClaimColorHex
                );
            }

            extractAndStoreSkinUrl(player, record);
            repository.save(false);
        } else {
            record.setLastUsername(name);
            record.setLastSeenEpochMs(System.currentTimeMillis());
            repository.markDirty();

            rankEngine.checkAndApplyProgression(server, uuid, record.getTotalPlaytimeTicks());

            // Full LP login sync — remove all old/stale rank groups and set only the correct one.
            // Ensures imported players (from KubeJS) get properly migrated to the new rank system.
            if (Config.loginRankSync && Playtime.getLuckPerms() != null && Playtime.getRankConfig() != null) {
                com.enviouse.playtime.data.RankDefinition storedRank = null;
                if (record.getCurrentRankId() != null) {
                    storedRank = Playtime.getRankConfig().getRankById(record.getCurrentRankId());
                }
                if (storedRank == null) {
                    storedRank = rankEngine.getCurrentRank(record.getTotalPlaytimeTicks());
                }
                Playtime.getLuckPerms().fullLoginSync(uuid, Playtime.getRankConfig().getRanks(), storedRank);

                if (Config.luckpermsForceSync) {
                    server.getCommands().performPrefixedCommand(
                            server.createCommandSourceStack().withSuppressedOutput(),
                            "lp sync"
                    );
                }
            }

            extractAndStoreSkinUrl(player, record);
            repository.save(false);
        }
    }

    /**
     * Extract the skin texture URL from the player's GameProfile and store it in their record.
     * The URL is from Mojang's texture server (textures.minecraft.net) and is used by
     * the client to display skins for offline players without additional API calls.
     */
    private void extractAndStoreSkinUrl(ServerPlayer player, PlayerRecord record) {
        try {
            var textureProps = player.getGameProfile().getProperties().get("textures");
            if (textureProps != null && !textureProps.isEmpty()) {
                com.mojang.authlib.properties.Property texProp = textureProps.iterator().next();
                String decoded = new String(
                        java.util.Base64.getDecoder().decode(texProp.getValue()),
                        java.nio.charset.StandardCharsets.UTF_8
                );
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(decoded).getAsJsonObject();
                com.google.gson.JsonObject textures = json.getAsJsonObject("textures");
                if (textures != null && textures.has("SKIN")) {
                    String url = textures.getAsJsonObject("SKIN").get("url").getAsString();
                    record.setSkinUrl(url);
                }
            }
        } catch (Exception e) {
            // Ignore — skin URL is optional, client falls back to default skin
        }
    }

    /** Called when a player logs out. */
    public void onPlayerLeave(MinecraftServer server, ServerPlayer player) {
        UUID uuid = player.getUUID();
        flushSession(server, uuid);

        PlayerRecord record = repository.getPlayer(uuid);
        if (record != null) {
            record.setLastSeenEpochMs(System.currentTimeMillis());
            repository.markDirty();
            repository.save(false);
        }

        sessions.remove(uuid);
    }

    /** Called every server tick. */
    public void onServerTick(MinecraftServer server, int tickCount) {
        if (!repository.isLoaded()) return;

        if (tickCount % Config.afkCheckInterval == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tickPlayer(server, player);
            }
        }

        if (tickCount % Config.flushIntervalTicks == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                flushSession(server, player.getUUID());
            }
        }

        if (tickCount % Config.saveIntervalTicks == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                flushSession(server, player.getUUID());
            }
            repository.save(false);
        }
    }

    /**
     * Multi-signal activity check + heuristic pattern analysis.
     * <p>
     * Layer 1: Samples position, rotation, hotbar, sprint. Each changed value sets
     * a bit in signalMask. Active only when enough distinct signals have fired.
     * <p>
     * Layer 2: Even when Layer 1 says "active", feeds data into heuristic analyzers
     * that detect AFK pools, mouse macros, auto-clickers, and robotic timing.
     * If the heuristic composite score exceeds the threshold, overrides to AFK.
     */
    private void tickPlayer(MinecraftServer server, ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerSession session = sessions.get(uuid);
        if (session == null) return;
        if (session.paused) return; // suspended by an integration (e.g. SEF vanish)

        PlayerRecord record = repository.getPlayer(uuid);
        if (record == null) return;

        // ── Layer 1: Signal sampling ────────────────────────────────────────

        // Sample rotation
        float curYaw = Math.round(player.getYRot() * 10f) / 10f;
        float curPitch = Math.round(player.getXRot() * 10f) / 10f;
        float yawDelt = (float) yawDelta(curYaw, session.lastYaw);
        float pitchDelt = Math.abs(curPitch - session.lastPitch);
        // Signed deltas for heuristic (preserve direction)
        float signedYawDelta = curYaw - session.lastYaw;
        if (signedYawDelta > 180) signedYawDelta -= 360;
        if (signedYawDelta < -180) signedYawDelta += 360;
        float signedPitchDelta = curPitch - session.lastPitch;

        boolean rotated = (yawDelt >= Config.afkLookThreshold || pitchDelt >= Config.afkLookThreshold);
        if (rotated) {
            if (Config.afkSigRotation) session.signalMask |= SIG_ROTATION;
            session.lastYaw = curYaw;
            session.lastPitch = curPitch;
        }

        // Sample position
        double dx = player.getX() - session.lastX;
        double dy = player.getY() - session.lastY;
        double dz = player.getZ() - session.lastZ;
        double distSq = dx * dx + dy * dy + dz * dz;
        float moveDist = (float) Math.sqrt(distSq);
        float moveHeading = (float) Math.toDegrees(Math.atan2(dx, dz));
        if (moveHeading < 0) moveHeading += 360;

        boolean moved = (distSq >= Config.afkMoveThreshold * Config.afkMoveThreshold);
        if (moved) {
            if (Config.afkSigPosition) session.signalMask |= SIG_POSITION;
            session.lastX = player.getX();
            session.lastY = player.getY();
            session.lastZ = player.getZ();
        }

        // Sample hotbar slot
        int curSlot = player.getInventory().selected;
        if (curSlot != session.lastHotbarSlot) {
            if (Config.afkSigHotbar) session.signalMask |= SIG_HOTBAR;
            session.lastHotbarSlot = curSlot;
            // Feed to heuristic regardless of toggle (heuristic is its own layer)
            session.heuristicState.recordInteraction(ACTION_HOTBAR, curSlot);
        }

        // Sample sprint toggle
        boolean curSprint = player.isSprinting();
        if (curSprint != session.lastSprinting) {
            if (Config.afkSigSprint) session.signalMask |= SIG_SPRINT;
            session.lastSprinting = curSprint;
            session.heuristicState.recordInteraction(ACTION_SPRINT, curSprint ? 1 : 0);
        }

        // ── Merge client-reported signals (validated in onClientSignal) ─────
        // Apply per-type masking first (in case admin toggled a type off after
        // the packet arrived). Then anti-spoof check.
        int clientMask = session.clientSignalMask & enabledClientMask();
        boolean clientClaimedActivity = clientMask != 0;

        if (Config.afkAntispoofEnabled) {
            // Server motion = rotation OR position change this tick.
            boolean serverSawMotion = rotated || moved;
            if (clientClaimedActivity && !serverSawMotion) {
                session.clientSuspicionTicks++;
                if (session.clientSuspicionTicks >= Config.afkAntispoofThreshold
                        && !session.clientSuspectedSpoofing) {
                    session.clientSuspectedSpoofing = true;
                    LOGGER.warn("[Playtime] Client signal spoofing suspected for {} — " +
                                    "client claimed activity for {}s without any server-side motion.",
                            player.getGameProfile().getName(),
                            session.clientSuspicionTicks * Config.afkCheckInterval / 20);
                }
            } else if (serverSawMotion) {
                session.clientSuspicionTicks = 0;
                session.clientSuspectedSpoofing = false;
            }
        }

        // Honour the spoof gate: drop client bits if the player is suspect.
        if (session.clientSuspectedSpoofing) {
            clientMask = 0;
        }
        session.signalMask |= clientMask;
        // Reset accumulators — fresh window starts now.
        session.clientSignalMask = 0;
        session.clientUniqueKeyCount = 0;

        // ── Heuristic: record sample ────────────────────────────────────────

        // Mark activity if any signal was set this tick
        if (session.signalMask != 0) {
            session.heuristicState.markActivity();
        }

        // Always record the sample (even if player is idle — analyzers need the full picture)
        session.heuristicState.recordSample(
                moveDist, moveHeading,
                signedYawDelta, signedPitchDelta,
                System.currentTimeMillis()
        );

        // ── Layer 1: Evaluate bitmask ───────────────────────────────────────

        int signalCount = Integer.bitCount(session.signalMask);
        // Clamp the requirement to however many signals are actually enabled;
        // otherwise an admin who disables checks faster than they lower minSignals
        // would soft-lock everyone to AFK.
        int effectiveMin = Math.min(Config.afkMinSignals, enabledSignalCount());
        boolean basicActive = signalCount >= effectiveMin;

        // ── Layer 2: Heuristic override ─────────────────────────────────────

        boolean heuristicOverride = false;
        if (basicActive && Config.afkHeuristicsEnabled) {
            session.heuristicEvalCounter++;
            if (session.heuristicEvalCounter >= Config.afkHeuristicEvalInterval) {
                session.heuristicEvalCounter = 0;
                AfkDecisionEngine.HeuristicResult result = AfkDecisionEngine.evaluate(session.heuristicState);
                session.lastHeuristicResult = result;
                session.heuristicFlaggedAfk = result.flaggedAfk;

                if (result.flaggedAfk) {
                    LOGGER.debug("[Playtime] Heuristic AFK override for {} — composite={} (mv={} cam={} int={} tm={})",
                            player.getGameProfile().getName(),
                            String.format("%.2f", result.compositeScore),
                            String.format("%.2f", result.movementScore),
                            String.format("%.2f", result.cameraScore),
                            String.format("%.2f", result.interactionScore),
                            String.format("%.2f", result.timingScore));
                }
            }
            heuristicOverride = session.heuristicFlaggedAfk;
        }

        // Final verdict: active unless bitmask fails OR heuristics override
        boolean isActive = basicActive && !heuristicOverride;

        if (isActive) {
            session.signalMask = 0;
            session.afkTicks = 0;
            session.afkNotified = false;

            if (session.wasAfk) {
                session.wasAfk = false;
                player.displayClientMessage(Component.literal("§a✓ Playtime tracking resumed!"), true);
                broadcastAfkState(server, uuid, false);
            }

            session.activeSessionTicks += Config.afkCheckInterval;

            long projectedTotal = record.getTotalPlaytimeTicks() + session.activeSessionTicks;
            rankEngine.checkAndApplyProgression(server, uuid, projectedTotal);
        } else {
            session.afkTicks += Config.afkCheckInterval;

            if (session.afkTicks < Config.afkTimeoutTicks && !heuristicOverride) {
                session.activeSessionTicks += Config.afkCheckInterval;
            } else {
                if (!session.wasAfk) {
                    session.wasAfk = true;
                    broadcastAfkState(server, uuid, true);
                }
                String afkReason = heuristicOverride
                        ? "§c⚠ AFK detected (suspicious activity pattern) — Playtime tracking paused"
                        : "§c⚠ AFK detected — Playtime tracking paused";
                if (!session.afkNotified || session.afkTicks % Config.afkNotifyInterval == 0) {
                    player.displayClientMessage(Component.literal(afkReason), true);
                    session.afkNotified = true;
                }
            }
        }
    }

    /**
     * Called from Forge event handlers when a player performs a meaningful action
     * (block break/place, attack entity, chat). Sets the INTERACTION signal bit
     * and feeds detailed data to the heuristic analyzer.
     *
     * @param uuid       player UUID
     * @param actionType one of ACTION_BREAK, ACTION_PLACE, etc.
     * @param posHash    hash of the target position (use AfkHeuristicState.hashBlockPos/hashEntity)
     */
    public void onActivity(UUID uuid, byte actionType, long posHash) {
        PlayerSession session = sessions.get(uuid);
        if (session != null && !session.paused) {
            session.signalMask |= SIG_INTERACTION;
            session.heuristicState.recordInteraction(actionType, posHash);
        }
    }

    /**
     * Convenience overload for callers that don't have position data.
     * Sets the INTERACTION signal bit with action type and zero position hash.
     */
    public void onActivity(UUID uuid, byte actionType) {
        onActivity(uuid, actionType, 0L);
    }

    /**
     * Legacy overload — sets interaction signal with generic type.
     * Prefer the typed variants for better heuristic accuracy.
     */
    public void onActivity(UUID uuid) {
        onActivity(uuid, ACTION_USE, 0L);
    }

    /**
     * Receive a batched activity signal from the player's client mod.
     * Called from {@code ClientActivitySignalC2SPacket} on the server thread.
     *
     * <p>Validation order (any failure silently drops the packet):
     * <ol>
     *   <li>Session exists and is not paused (SEF vanish compat).</li>
     *   <li>Per-second rate limit not exceeded.</li>
     *   <li>{@code requireFocus} satisfied (drops all client signals when the
     *       Minecraft window is not OS-focused).</li>
     * </ol>
     *
     * <p>Surviving signal bits are masked against the per-type config (so a
     * disabled signal type cannot rescue a player from AFK), then ORed into
     * the per-tick accumulator and consumed by {@link #tickPlayer}.
     *
     * @param uuid              player UUID
     * @param signalBits        bitmask of {@code SIG_KEYBOARD|SIG_MOUSE_MOVE|...}
     * @param windowFocused     whether the client reports its window as focused
     * @param uniqueKeysSinceLast distinct key codes pressed since last packet (heuristic input)
     */
    public void onClientSignal(UUID uuid, int signalBits, boolean windowFocused,
                               int uniqueKeysSinceLast) {
        PlayerSession session = sessions.get(uuid);
        if (session == null) return;
        if (session.paused) return; // SEF vanish: silently drop

        // Rate limit: rolling 1-second window
        long now = System.currentTimeMillis();
        if (now - session.clientPacketWindowStartMs >= 1000L) {
            session.clientPacketWindowStartMs = now;
            session.clientPacketsThisSecond = 0;
        }
        if (session.clientPacketsThisSecond >= Config.afkClientRateLimitPerSecond) {
            return; // drop: noisy client
        }
        session.clientPacketsThisSecond++;

        session.clientWindowFocused = windowFocused;
        session.clientUniqueKeyCount = Math.max(session.clientUniqueKeyCount, uniqueKeysSinceLast);

        // If focus is required and the window is not focused, drop all client
        // signals EXCEPT the focus bit itself (so admins can see "client lost focus"
        // diagnostics if they ever want to).
        int sanitized = signalBits & CLIENT_SIGNALS_MASK;
        if (Config.afkClientRequireFocus && !windowFocused) {
            sanitized &= SIG_WINDOW_FOCUS; // strip everything except focus bit
        }

        // Mask out any per-type-disabled bits.
        sanitized &= enabledClientMask();

        // Defeat single-key macros: keyboard signal only counts when the player
        // has pressed at least N distinct keys recently.
        if ((sanitized & SIG_KEYBOARD) != 0
                && session.clientUniqueKeyCount < Config.afkClientMinUniqueKeys) {
            sanitized &= ~SIG_KEYBOARD;
        }

        session.clientSignalMask |= sanitized;

        // Feed any client interaction into the heuristic so the analyzers can
        // see them too. We pass the bitmask as a synthetic position hash so the
        // diversity analyzer treats different signal mixes as different inputs.
        if (sanitized != 0) {
            byte heuristicAction;
            if ((sanitized & SIG_MOUSE_CLICK) != 0)      heuristicAction = ACTION_MOUSE_CLICK;
            else if ((sanitized & SIG_KEYBOARD) != 0)    heuristicAction = ACTION_KEYBOARD;
            else if ((sanitized & SIG_GUI) != 0)         heuristicAction = ACTION_GUI;
            else if ((sanitized & SIG_INVENTORY) != 0)   heuristicAction = ACTION_INVENTORY;
            else if ((sanitized & SIG_SCROLL) != 0)      heuristicAction = ACTION_SCROLL;
            else if ((sanitized & SIG_MOUSE_MOVE) != 0)  heuristicAction = ACTION_MOUSE_MOVE;
            else                                         heuristicAction = ACTION_USE;
            session.heuristicState.recordInteraction(heuristicAction, sanitized);
        }
    }

    /** Bitmask of which client signal types are currently enabled in config. */
    private static int enabledClientMask() {
        int m = 0;
        if (Config.afkSigKeyboard)    m |= SIG_KEYBOARD;
        if (Config.afkSigMouseMove)   m |= SIG_MOUSE_MOVE;
        if (Config.afkSigMouseClick)  m |= SIG_MOUSE_CLICK;
        if (Config.afkSigGui)         m |= SIG_GUI;
        if (Config.afkSigScroll)      m |= SIG_SCROLL;
        if (Config.afkSigInventory)   m |= SIG_INVENTORY;
        if (Config.afkSigWindowFocus) m |= SIG_WINDOW_FOCUS;
        return m;
    }

    /** Number of distinct signal types currently enabled in config. */
    private static int enabledSignalCount() {
        int n = 0;
        if (Config.afkSigRotation)    n++;
        if (Config.afkSigPosition)    n++;
        if (Config.afkSigHotbar)      n++;
        if (Config.afkSigSprint)      n++;
        if (Config.afkSigInteraction) n++;
        if (Config.afkSigKeyboard)    n++;
        if (Config.afkSigMouseMove)   n++;
        if (Config.afkSigMouseClick)  n++;
        if (Config.afkSigGui)         n++;
        if (Config.afkSigScroll)      n++;
        if (Config.afkSigInventory)   n++;
        if (Config.afkSigWindowFocus) n++;
        return Math.max(1, n); // never zero — would mean "AFK regardless"
    }

    /** Is this player currently flagged as a client-signal spoofer? (admin diagnostic) */
    public boolean isClientSpoofingSuspected(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        return session != null && session.clientSuspectedSpoofing;
    }

    /** Anti-spoof suspicion counter for diagnostics. */
    public int getClientSuspicionTicks(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        return session != null ? session.clientSuspicionTicks : 0;
    }

    /** Last-reported window focus flag (admin diagnostic). */
    public boolean isClientWindowFocused(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        return session != null && session.clientWindowFocused;
    }

    /**
     * Reset all transient AFK state for a player — bitmask, AFK ticks, suspicion,
     * heuristic flag, and the rolling sample buffer. Used by
     * {@code /playtimeadmin afk reset <player>}.
     */
    public void resetAfkState(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        if (session == null) return;
        session.signalMask = 0;
        session.afkTicks = 0;
        session.afkNotified = false;
        session.wasAfk = false;
        session.heuristicFlaggedAfk = false;
        session.lastHeuristicResult = null;
        session.heuristicEvalCounter = 0;
        session.heuristicState = new AfkHeuristicState(Config.afkHeuristicWindow);
        session.clientSignalMask = 0;
        session.clientUniqueKeyCount = 0;
        session.clientSuspicionTicks = 0;
        session.clientSuspectedSpoofing = false;
    }

    /**
     * Get the last heuristic evaluation result for a player (for admin diagnostics).
     * Returns null if heuristics haven't run yet for this player.
     */
    @Nullable
    public AfkDecisionEngine.HeuristicResult getHeuristicResult(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        return session != null ? session.lastHeuristicResult : null;
    }

    /**
     * Get the heuristic state for a player (for admin diagnostics).
     */
    @Nullable
    public AfkHeuristicState getHeuristicState(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        return session != null ? session.heuristicState : null;
    }

    /**
     * Is the player currently flagged by heuristic analysis?
     */
    public boolean isHeuristicFlagged(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        return session != null && session.heuristicFlaggedAfk;
    }

    /** Flush accumulated session ticks into the player's total. */
    private void flushSession(MinecraftServer server, UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        if (session == null) return;

        long ticks = session.activeSessionTicks;
        if (ticks <= 0) return;

        PlayerRecord record = repository.getPlayer(uuid);
        if (record == null) return;

        long newTotal = record.addPlaytimeTicks(ticks);
        session.activeSessionTicks = 0;
        repository.markDirty();

        rankEngine.checkAndApplyProgression(server, uuid, newTotal);
    }

    /**
     * Suspend playtime accumulation, AFK detection, and activity sampling for the
     * given player until {@link #resumeSession(UUID)} is called. Any unflushed
     * session ticks are written to the player's record before pausing so they
     * are not lost. No-op if the player has no session or is already paused.
     *
     * <p>Designed for integrations (e.g. SEF vanish) that need to halt the clock
     * without dragging the player through the heavy join/leave lifecycle.
     */
    public void pauseSession(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        if (session == null || session.paused) return;

        // Flush whatever has already accrued so the pause point is clean.
        if (session.activeSessionTicks > 0) {
            PlayerRecord record = repository.getPlayer(uuid);
            if (record != null) {
                record.addPlaytimeTicks(session.activeSessionTicks);
                session.activeSessionTicks = 0;
                repository.markDirty();
            }
        }
        session.paused = true;
        LOGGER.debug("[Playtime] Paused session for {}", uuid);
    }

    /**
     * Resume a previously paused session. Clears the activity bitmask and AFK
     * counter so the player isn't immediately re-flagged AFK from samples that
     * went stale during the pause. No-op if the session is missing or not
     * paused.
     */
    public void resumeSession(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        if (session == null || !session.paused) return;
        session.paused = false;
        session.signalMask = 0;
        session.afkTicks = 0;
        session.afkNotified = false;
        LOGGER.debug("[Playtime] Resumed session for {}", uuid);
    }

    /** Is the given player's session currently paused (e.g. by vanish)? */
    public boolean isPaused(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        return session != null && session.paused;
    }

    /** Is the given player currently AFK? */
    public boolean isAfk(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        return session != null && session.afkTicks >= Config.afkTimeoutTicks;
    }

    /** Get the current unflushed session ticks for a player. */
    public long getSessionTicks(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        return session != null ? session.activeSessionTicks : 0;
    }

    /** Flush all online players. */
    public void flushAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            flushSession(server, player.getUUID());
        }
    }

    /** Broadcast AFK state change to all online players. */
    private void broadcastAfkState(MinecraftServer server, UUID uuid, boolean afk) {
        PlaytimeNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new AfkSyncS2CPacket(uuid, afk));
    }

    private static double yawDelta(float a, float b) {
        double d = Math.abs((a - b) % 360);
        return d > 180 ? 360 - d : d;
    }
}
