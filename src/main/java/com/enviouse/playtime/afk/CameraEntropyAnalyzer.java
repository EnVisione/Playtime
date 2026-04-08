package com.enviouse.playtime.afk;

/**
 * Analyzes camera rotation patterns to detect mouse macros.
 * <p>
 * Common bypass techniques detected:
 * <ul>
 *   <li><b>Alternating wiggle</b> — macro moves yaw/pitch +N then -N repeatedly.
 *       Detected by sign-change autocorrelation and magnitude consistency.</li>
 *   <li><b>Sine-wave sweep</b> — macro sweeps look angle smoothly back and forth.
 *       Detected by periodicity in delta sequence.</li>
 *   <li><b>Random-bounded jitter</b> — macro adds small random offsets each tick.
 *       Detected by unnaturally low magnitude variance and lack of sustained look directions.</li>
 * </ul>
 *
 * @return 0.0 (looks human) to 1.0 (definitely a macro)
 */
public class CameraEntropyAnalyzer {

    /**
     * Analyze camera rotation buffer and return suspicion score.
     */
    public static float analyze(AfkHeuristicState state, int minSamples) {
        int n = state.getSampleCount();
        if (n < minSamples) return 0f;

        // Count how many samples actually have rotation
        int rotatingCount = 0;
        for (int i = 0; i < n; i++) {
            int idx = state.indexAt(i);
            if (idx >= 0 && (Math.abs(state.yawDelta[idx]) > 0.5f || Math.abs(state.pitchDelta[idx]) > 0.5f)) {
                rotatingCount++;
            }
        }

        // If barely rotating, camera analysis is irrelevant
        if (rotatingCount < minSamples / 2) return 0f;

        float signAlternation = analyzeSignAlternation(state, n);
        float magnitudeConsistency = analyzeMagnitudeConsistency(state, n);
        float periodicity = analyzePeriodicity(state, n);
        float directionEntropy = analyzeDirectionEntropy(state, n);

        // Weighted combination
        float combined = signAlternation * 0.30f
                + magnitudeConsistency * 0.25f
                + periodicity * 0.25f
                + directionEntropy * 0.20f;

        return Math.min(1.0f, combined);
    }

    /**
     * Detect alternating sign patterns in yaw/pitch deltas.
     * Macros that wiggle produce: +, -, +, -, ... (near-perfect alternation).
     * Humans produce long runs of same-sign deltas when turning.
     */
    private static float analyzeSignAlternation(AfkHeuristicState state, int n) {
        int yawAlternations = 0, yawTotal = 0;
        int pitchAlternations = 0, pitchTotal = 0;

        for (int i = 0; i < n - 1; i++) {
            int idx0 = state.indexAt(i);
            int idx1 = state.indexAt(i + 1);
            if (idx0 < 0 || idx1 < 0) break;

            float y0 = state.yawDelta[idx0], y1 = state.yawDelta[idx1];
            float p0 = state.pitchDelta[idx0], p1 = state.pitchDelta[idx1];

            if (Math.abs(y0) > 0.3f && Math.abs(y1) > 0.3f) {
                yawTotal++;
                if (Math.signum(y0) != Math.signum(y1)) yawAlternations++;
            }
            if (Math.abs(p0) > 0.3f && Math.abs(p1) > 0.3f) {
                pitchTotal++;
                if (Math.signum(p0) != Math.signum(p1)) pitchAlternations++;
            }
        }

        // Normal play: ~30-40% alternation rate. Wiggle macro: 80-100%.
        float yawRate = yawTotal > 5 ? (float) yawAlternations / yawTotal : 0f;
        float pitchRate = pitchTotal > 5 ? (float) pitchAlternations / pitchTotal : 0f;
        float maxRate = Math.max(yawRate, pitchRate);

        if (maxRate > 0.85f) return 1.0f;
        if (maxRate > 0.70f) return 0.7f;
        if (maxRate > 0.55f) return 0.3f;
        return 0f;
    }

    /**
     * Check if rotation magnitudes are suspiciously consistent.
     * Macros produce near-identical delta sizes. Humans vary wildly.
     */
    private static float analyzeMagnitudeConsistency(AfkHeuristicState state, int n) {
        float sumY = 0, sumYSq = 0;
        float sumP = 0, sumPSq = 0;
        int countY = 0, countP = 0;

        for (int i = 0; i < n; i++) {
            int idx = state.indexAt(i);
            if (idx < 0) break;

            float ay = Math.abs(state.yawDelta[idx]);
            float ap = Math.abs(state.pitchDelta[idx]);

            if (ay > 0.3f) {
                sumY += ay;
                sumYSq += ay * ay;
                countY++;
            }
            if (ap > 0.3f) {
                sumP += ap;
                sumPSq += ap * ap;
                countP++;
            }
        }

        float cvY = coefficientOfVariation(sumY, sumYSq, countY);
        float cvP = coefficientOfVariation(sumP, sumPSq, countP);

        // Use the channel with more data
        float cv = (countY > countP) ? cvY : cvP;
        if (Math.max(countY, countP) < 10) return 0f;

        // CV < 0.10 = robot precision. CV > 0.5 = human.
        if (cv < 0.08f) return 1.0f;
        if (cv < 0.15f) return 0.8f;
        if (cv < 0.25f) return 0.4f;
        if (cv < 0.40f) return 0.15f;
        return 0f;
    }

    /**
     * Detect periodicity in the yaw delta sequence using autocorrelation.
     * Macros tend to have a fixed period (e.g., wiggle every 2 ticks).
     */
    private static float analyzePeriodicity(AfkHeuristicState state, int n) {
        if (n < 20) return 0f;

        float bestCorr = 0f;

        // Check periods 2 through 30
        for (int period = 2; period <= Math.min(30, n / 3); period++) {
            float corr = deltaAutocorrelation(state, n, period);
            if (corr > bestCorr) bestCorr = corr;
        }

        if (bestCorr > 0.80f) return 1.0f;
        if (bestCorr > 0.65f) return 0.6f;
        if (bestCorr > 0.50f) return 0.2f;
        return 0f;
    }

    /**
     * Compute autocorrelation of yaw deltas at a given lag.
     */
    private static float deltaAutocorrelation(AfkHeuristicState state, int n, int lag) {
        int pairs = Math.min(n - lag, 40);
        if (pairs < 8) return 0f;

        // Compute mean first
        float sumA = 0, sumB = 0;
        int count = 0;
        for (int i = 0; i < pairs; i++) {
            int idxA = state.indexAt(i);
            int idxB = state.indexAt(i + lag);
            if (idxA < 0 || idxB < 0) break;
            sumA += state.yawDelta[idxA];
            sumB += state.yawDelta[idxB];
            count++;
        }
        if (count < 8) return 0f;

        float meanA = sumA / count;
        float meanB = sumB / count;

        float num = 0, denA = 0, denB = 0;
        for (int i = 0; i < count; i++) {
            int idxA = state.indexAt(i);
            int idxB = state.indexAt(i + lag);
            if (idxA < 0 || idxB < 0) break;

            float a = state.yawDelta[idxA] - meanA;
            float b = state.yawDelta[idxB] - meanB;
            num += a * b;
            denA += a * a;
            denB += b * b;
        }

        float den = (float) Math.sqrt(denA * denB);
        return den < 0.001f ? 0f : Math.abs(num / den);
    }

    /**
     * Analyze direction entropy — how many distinct look directions are visited.
     * Quantize yaw into 8 octants and measure how many are used.
     * Macros that wiggle in one axis visit very few octants.
     */
    private static float analyzeDirectionEntropy(AfkHeuristicState state, int n) {
        if (n < 15) return 0f;

        // Track cumulative yaw to see how far the player "explores"
        boolean[] octants = new boolean[8];
        float cumulativeYaw = 0;

        for (int i = 0; i < n; i++) {
            int idx = state.indexAt(i);
            if (idx < 0) break;
            cumulativeYaw += state.yawDelta[idx];

            // Normalize to 0-360
            float normalized = ((cumulativeYaw % 360) + 360) % 360;
            int octant = (int) (normalized / 45) % 8;
            octants[octant] = true;
        }

        int usedOctants = 0;
        for (boolean b : octants) if (b) usedOctants++;

        // 1-2 octants = very suspicious (wiggling in place)
        // 5+ octants = normal play (looking around)
        if (usedOctants <= 1) return 0.9f;
        if (usedOctants <= 2) return 0.6f;
        if (usedOctants <= 3) return 0.3f;
        if (usedOctants <= 4) return 0.1f;
        return 0f;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static float coefficientOfVariation(float sum, float sumSq, int count) {
        if (count < 5) return 1.0f; // Not enough data, assume human
        float mean = sum / count;
        float variance = (sumSq / count) - (mean * mean);
        float stdDev = (float) Math.sqrt(Math.max(0, variance));
        return mean > 0.001f ? stdDev / mean : 1.0f;
    }
}

