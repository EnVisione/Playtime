package com.enviouse.playtime.afk;

/**
 * Analyzes movement patterns to detect AFK pools, circle-walking, and rail loops.
 * <p>
 * Detects:
 * <ul>
 *   <li><b>Velocity consistency</b> — AFK pools produce unnaturally consistent speed
 *       (low coefficient of variation). Real players have bursts and pauses.</li>
 *   <li><b>Heading autocorrelation</b> — circle-walking and AFK pools produce repeating
 *       heading sequences. We compare recent heading patterns to earlier ones.</li>
 *   <li><b>Path loop detection</b> — checks if the heading sequence is periodic
 *       (repeats every N samples).</li>
 * </ul>
 *
 * @return 0.0 (looks human) to 1.0 (definitely botted/AFK-pooled)
 */
public class MovementEntropyAnalyzer {

    /**
     * Analyze the movement buffer and return a suspicion score.
     *
     * @param state          the player's heuristic state
     * @param minSamples     minimum samples before producing a score (below this, returns 0)
     * @return suspicion 0.0–1.0
     */
    public static float analyze(AfkHeuristicState state, int minSamples) {
        int n = state.getSampleCount();
        if (n < minSamples) return 0f;

        float velocityScore = analyzeVelocityConsistency(state, n);
        float headingScore = analyzeHeadingRepetition(state, n);
        float loopScore = analyzePathLoop(state, n);

        // Weighted combination — velocity consistency is the strongest signal
        float combined = velocityScore * 0.40f + headingScore * 0.35f + loopScore * 0.25f;

        // Only flag if movement is actually happening (stationary = handled by bitmask)
        float avgSpeed = averageMoveDelta(state, n);
        if (avgSpeed < 0.05f) return 0f; // Not moving at all, not our concern

        return Math.min(1.0f, combined);
    }

    /**
     * Coefficient of variation of movement speed.
     * AFK pools: very low CV (constant speed). Humans: high CV (bursts/pauses).
     */
    private static float analyzeVelocityConsistency(AfkHeuristicState state, int n) {
        float sum = 0, sumSq = 0;
        int moving = 0;

        for (int offset = 0; offset < n; offset++) {
            int idx = state.indexAt(offset);
            float v = state.moveDelta[idx];
            if (v > 0.01f) {
                sum += v;
                sumSq += v * v;
                moving++;
            }
        }

        if (moving < 10) return 0f; // Not enough moving samples

        float mean = sum / moving;
        float variance = (sumSq / moving) - (mean * mean);
        float stdDev = (float) Math.sqrt(Math.max(0, variance));
        float cv = (mean > 0.001f) ? stdDev / mean : 0f;

        // CV < 0.15 = very suspicious (robot-like consistency)
        // CV > 0.5 = normal human movement
        if (cv < 0.10f) return 1.0f;
        if (cv < 0.20f) return 0.8f;
        if (cv < 0.35f) return 0.4f;
        if (cv < 0.50f) return 0.15f;
        return 0f;
    }

    /**
     * Autocorrelation of heading angles to detect repeating movement patterns.
     * Circle-walking, AFK pools, and rail loops produce high autocorrelation
     * at their loop period.
     */
    private static float analyzeHeadingRepetition(AfkHeuristicState state, int n) {
        if (n < 20) return 0f;

        int halfWindow = n / 2;
        float bestCorr = 0f;

        // Try different lag values (periods) from 5 to half-window
        for (int lag = 5; lag <= Math.min(halfWindow, 60); lag++) {
            float corr = headingAutocorrelation(state, n, lag);
            if (corr > bestCorr) bestCorr = corr;
        }

        // High autocorrelation = repeating pattern
        if (bestCorr > 0.85f) return 1.0f;
        if (bestCorr > 0.70f) return 0.7f;
        if (bestCorr > 0.55f) return 0.3f;
        return 0f;
    }

    /**
     * Compute normalized heading autocorrelation at a given lag.
     */
    private static float headingAutocorrelation(AfkHeuristicState state, int n, int lag) {
        int pairs = Math.min(n - lag, 40); // Limit computation
        if (pairs < 5) return 0f;

        float sumMatch = 0;
        for (int i = 0; i < pairs; i++) {
            int idx1 = state.indexAt(i);
            int idx2 = state.indexAt(i + lag);
            if (idx1 < 0 || idx2 < 0) break;

            float h1 = state.moveHeading[idx1];
            float h2 = state.moveHeading[idx2];
            float diff = angleDiff(h1, h2);

            // Map angle difference to similarity (0° = 1.0, 180° = 0.0)
            float similarity = 1.0f - (diff / 180.0f);
            sumMatch += similarity;
        }

        return sumMatch / pairs;
    }

    /**
     * Detect periodic loops in the path by checking if heading at sample i
     * closely matches heading at sample i+period for multiple periods.
     */
    private static float analyzePathLoop(AfkHeuristicState state, int n) {
        if (n < 30) return 0f;

        float bestScore = 0f;

        // Try loop periods from 4 to 40 samples
        for (int period = 4; period <= Math.min(40, n / 3); period++) {
            int matches = 0;
            int total = 0;

            for (int i = 0; i + period < n && total < 50; i++) {
                int idx1 = state.indexAt(i);
                int idx2 = state.indexAt(i + period);
                if (idx1 < 0 || idx2 < 0) break;

                float diff = angleDiff(state.moveHeading[idx1], state.moveHeading[idx2]);
                // Also check speed similarity
                float speedRatio = safeRatio(state.moveDelta[idx1], state.moveDelta[idx2]);

                if (diff < 15.0f && speedRatio > 0.7f) matches++;
                total++;
            }

            if (total >= 10) {
                float ratio = (float) matches / total;
                if (ratio > bestScore) bestScore = ratio;
            }
        }

        // bestScore > 0.8 means very periodic movement
        if (bestScore > 0.85f) return 1.0f;
        if (bestScore > 0.70f) return 0.6f;
        if (bestScore > 0.55f) return 0.2f;
        return 0f;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static float averageMoveDelta(AfkHeuristicState state, int n) {
        float sum = 0;
        for (int i = 0; i < n; i++) {
            int idx = state.indexAt(i);
            if (idx >= 0) sum += state.moveDelta[idx];
        }
        return sum / n;
    }

    /** Shortest angle difference in degrees (0-180). */
    private static float angleDiff(float a, float b) {
        float d = Math.abs(a - b) % 360;
        return d > 180 ? 360 - d : d;
    }

    /** Ratio of smaller to larger, safe from division by zero. */
    private static float safeRatio(float a, float b) {
        float mn = Math.min(Math.abs(a), Math.abs(b));
        float mx = Math.max(Math.abs(a), Math.abs(b));
        return mx < 0.001f ? 1.0f : mn / mx;
    }
}

