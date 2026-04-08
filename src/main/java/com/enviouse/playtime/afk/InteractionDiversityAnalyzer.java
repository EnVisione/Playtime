package com.enviouse.playtime.afk;

import java.util.HashSet;
import java.util.Set;

/**
 * Analyzes interaction diversity to detect auto-clickers and macro-based interactions.
 * <p>
 * Detects:
 * <ul>
 *   <li><b>Position repetition</b> — auto-clickers interact with the same block position
 *       repeatedly. Real players interact with many different positions.</li>
 *   <li><b>Action type monotony</b> — macros typically only produce one action type
 *       (e.g., only right-click). Real players mix break/place/attack/use/chat.</li>
 *   <li><b>Interaction hash uniqueness</b> — measures how many unique interaction targets
 *       appear in the window vs total interactions.</li>
 * </ul>
 *
 * @return 0.0 (diverse, human-like) to 1.0 (monotonous, bot-like)
 */
public class InteractionDiversityAnalyzer {

    /**
     * Analyze interaction diversity and return suspicion score.
     */
    public static float analyze(AfkHeuristicState state, int minSamples) {
        int n = state.getSampleCount();
        if (n < minSamples) return 0f;

        // Count interactions
        int interactionCount = 0;
        for (int i = 0; i < n; i++) {
            int idx = state.indexAt(i);
            if (idx >= 0 && state.actionTypes[idx] != 0) interactionCount++;
        }

        // If very few interactions in the window, can't judge diversity
        if (interactionCount < 5) return 0f;

        float hashDiversity = analyzeHashDiversity(state, n, interactionCount);
        float typeDiversity = analyzeTypeDiversity(state, n);
        float burstPattern = analyzeBurstPattern(state, n);

        // Weighted combination
        return Math.min(1.0f, hashDiversity * 0.40f + typeDiversity * 0.35f + burstPattern * 0.25f);
    }

    /**
     * How many unique interaction hashes appear vs total interactions?
     * Auto-clickers hit the same spot → very few unique hashes.
     */
    private static float analyzeHashDiversity(AfkHeuristicState state, int n, int interactionCount) {
        Set<Long> uniqueHashes = new HashSet<>();

        for (int i = 0; i < n; i++) {
            int idx = state.indexAt(i);
            if (idx >= 0 && state.interactionHashes[idx] != 0) {
                uniqueHashes.add(state.interactionHashes[idx]);
            }
        }

        if (uniqueHashes.isEmpty()) return 0f;

        float uniqueRatio = (float) uniqueHashes.size() / interactionCount;

        // uniqueRatio < 0.1 = same position over and over (auto-clicker)
        // uniqueRatio > 0.5 = diverse interactions (human)
        if (uniqueRatio < 0.05f) return 1.0f;
        if (uniqueRatio < 0.10f) return 0.8f;
        if (uniqueRatio < 0.20f) return 0.5f;
        if (uniqueRatio < 0.35f) return 0.2f;
        return 0f;
    }

    /**
     * How many distinct action types (break/place/attack/use/chat/hotbar/sprint)?
     * Real players produce diverse action types. Macros produce 1-2.
     */
    private static float analyzeTypeDiversity(AfkHeuristicState state, int n) {
        boolean[] seenTypes = new boolean[8]; // 0-7

        for (int i = 0; i < n; i++) {
            int idx = state.indexAt(i);
            if (idx >= 0 && state.actionTypes[idx] > 0 && state.actionTypes[idx] < 8) {
                seenTypes[state.actionTypes[idx]] = true;
            }
        }

        int typeCount = 0;
        for (int t = 1; t < 8; t++) if (seenTypes[t]) typeCount++;

        // 1 type = very suspicious
        // 3+ types = fine
        if (typeCount <= 1) return 0.8f;
        if (typeCount == 2) return 0.3f;
        return 0f;
    }

    /**
     * Check if interactions come in suspiciously regular bursts.
     * Auto-clickers tend to interact at perfectly regular intervals.
     */
    private static float analyzeBurstPattern(AfkHeuristicState state, int n) {
        // Find intervals between interactions
        int[] intervals = new int[n];
        int intervalCount = 0;
        int lastInteraction = -1;

        for (int i = 0; i < n; i++) {
            int idx = state.indexAt(i);
            if (idx >= 0 && state.actionTypes[idx] != 0) {
                if (lastInteraction >= 0) {
                    int gap = i - lastInteraction;
                    if (intervalCount < intervals.length) {
                        intervals[intervalCount++] = gap;
                    }
                }
                lastInteraction = i;
            }
        }

        if (intervalCount < 5) return 0f;

        // Coefficient of variation of intervals
        float sum = 0, sumSq = 0;
        for (int i = 0; i < intervalCount; i++) {
            sum += intervals[i];
            sumSq += (float) intervals[i] * intervals[i];
        }

        float mean = sum / intervalCount;
        float variance = (sumSq / intervalCount) - (mean * mean);
        float stdDev = (float) Math.sqrt(Math.max(0, variance));
        float cv = mean > 0.001f ? stdDev / mean : 1.0f;

        // Very regular intervals (cv < 0.1) = auto-clicker
        if (cv < 0.05f) return 1.0f;
        if (cv < 0.15f) return 0.7f;
        if (cv < 0.30f) return 0.3f;
        return 0f;
    }
}

