package com.enviouse.playtime.afk;

import net.minecraft.core.BlockPos;

/**
 * Per-player rolling buffer state for advanced AFK heuristic analysis.
 * Stores the last N samples of movement, camera, interaction, and timing data.
 * Fed each AFK check interval (~1s) from {@link com.enviouse.playtime.service.SessionTracker}.
 */
public class AfkHeuristicState {

    /** Maximum buffer window (number of samples). Configurable via Config. */
    private final int windowSize;

    // ── Movement buffers ────────────────────────────────────────────────────
    /** Position delta magnitudes (blocks moved per sample). */
    public final float[] moveDelta;
    /** Heading angle (atan2 of dx,dz) per sample, in degrees 0-360. */
    public final float[] moveHeading;

    // ── Camera buffers ──────────────────────────────────────────────────────
    /** Yaw delta per sample (signed, degrees). */
    public final float[] yawDelta;
    /** Pitch delta per sample (signed, degrees). */
    public final float[] pitchDelta;

    // ── Interaction buffers ─────────────────────────────────────────────────
    /**
     * Rolling set of recent interaction "fingerprints".
     * Each fingerprint encodes action type + block position or entity id
     * as a hash to track diversity without storing full objects.
     */
    public final long[] interactionHashes;
    /** Action type codes for diversity counting (0=none,1=break,2=place,3=attack,4=use,5=chat,6=hotbar,7=sprint). */
    public final byte[] actionTypes;

    // ── Timing buffers ──────────────────────────────────────────────────────
    /** Epoch millis of each activity event (any signal). 0 = no activity in that sample. */
    public final long[] activityTimestamps;

    // ── Write cursor ────────────────────────────────────────────────────────
    private int cursor = 0;
    private int sampleCount = 0;

    // ── Pending interaction data (accumulated between ticks) ─────────────────
    private long pendingInteractionHash = 0;
    private byte pendingActionType = 0;
    private int pendingActionCount = 0;
    private boolean pendingHasActivity = false;

    // ── Unique interaction tracking for current window ──────────────────────
    private int uniquePositionCount = 0;

    public AfkHeuristicState(int windowSize) {
        this.windowSize = windowSize;
        this.moveDelta = new float[windowSize];
        this.moveHeading = new float[windowSize];
        this.yawDelta = new float[windowSize];
        this.pitchDelta = new float[windowSize];
        this.interactionHashes = new long[windowSize];
        this.actionTypes = new byte[windowSize];
        this.activityTimestamps = new long[windowSize];
    }

    /**
     * Record a full sample from the current tick.
     * Called once per afkCheckInterval from SessionTracker.
     */
    public void recordSample(float moveDist, float heading,
                             float yawDelt, float pitchDelt,
                             long timestampMs) {
        moveDelta[cursor] = moveDist;
        moveHeading[cursor] = heading;
        yawDelta[cursor] = yawDelt;
        pitchDelta[cursor] = pitchDelt;

        // Write pending interaction data
        interactionHashes[cursor] = pendingInteractionHash;
        actionTypes[cursor] = pendingActionType;
        activityTimestamps[cursor] = pendingHasActivity ? timestampMs : 0;

        // Clear pending
        pendingInteractionHash = 0;
        pendingActionType = 0;
        pendingActionCount = 0;
        pendingHasActivity = false;

        cursor = (cursor + 1) % windowSize;
        if (sampleCount < windowSize) sampleCount++;
    }

    /**
     * Called from interaction events between ticks to accumulate data.
     *
     * @param actionType 1=break, 2=place, 3=attack, 4=use, 5=chat, 6=hotbar, 7=sprint
     * @param posHash    hash of the block position or entity id (0 if N/A)
     */
    public void recordInteraction(byte actionType, long posHash) {
        // Keep the most "diverse" action type (highest code) and combine hashes
        if (actionType > pendingActionType) {
            pendingActionType = actionType;
        }
        pendingInteractionHash = pendingInteractionHash * 31 + posHash;
        pendingActionCount++;
        pendingHasActivity = true;
    }

    /** Mark that some activity happened this tick (for timing analysis). */
    public void markActivity() {
        pendingHasActivity = true;
    }

    // ── Accessors ───────────────────────────────────────────────────────────

    public int getWindowSize() { return windowSize; }
    public int getSampleCount() { return sampleCount; }
    public int getCursor() { return cursor; }

    /**
     * Get a sample at offset (0 = most recent, 1 = one before, etc).
     * Returns the buffer index, or -1 if not enough samples.
     */
    public int indexAt(int offset) {
        if (offset >= sampleCount) return -1;
        return (cursor - 1 - offset + windowSize) % windowSize;
    }

    /** How full the buffer is (0.0 to 1.0). Analyzers should scale confidence by this. */
    public float fillRatio() {
        return (float) sampleCount / windowSize;
    }

    /**
     * Hash a BlockPos for the interaction buffer.
     */
    public static long hashBlockPos(BlockPos pos) {
        if (pos == null) return 0;
        return (long) pos.getX() * 73856093L ^ (long) pos.getY() * 19349669L ^ (long) pos.getZ() * 83492791L;
    }

    /** Hash an entity ID. */
    public static long hashEntity(int entityId) {
        return entityId * 2654435761L;
    }
}


