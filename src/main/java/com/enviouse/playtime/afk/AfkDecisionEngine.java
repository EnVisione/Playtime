package com.enviouse.playtime.afk;

import com.enviouse.playtime.Config;

/**
 * Combines outputs from all heuristic analyzers into a single composite suspicion score.
 * <p>
 * The engine only runs when the basic signal bitmask says the player IS active
 * (i.e. they're producing enough signal types). The heuristics can then OVERRIDE
 * that verdict to "AFK" if the patterns look robotic/automated.
 * <p>
 * This prevents false positives (genuinely idle players are already caught by the bitmask)
 * while catching sophisticated bypass techniques (AFK pools, macros, auto-clickers).
 */
public class AfkDecisionEngine {

    /** Result of a heuristic evaluation. */
    public static class HeuristicResult {
        public final float movementScore;
        public final float cameraScore;
        public final float interactionScore;
        public final float timingScore;
        public final float compositeScore;
        public final boolean flaggedAfk;

        public HeuristicResult(float movement, float camera, float interaction, float timing,
                               float composite, boolean flagged) {
            this.movementScore = movement;
            this.cameraScore = camera;
            this.interactionScore = interaction;
            this.timingScore = timing;
            this.compositeScore = composite;
            this.flaggedAfk = flagged;
        }
    }

    /**
     * Run all heuristic analyzers on the player's state and produce a verdict.
     *
     * @param state the player's heuristic buffer state
     * @return evaluation result with per-analyzer scores and AFK verdict
     */
    public static HeuristicResult evaluate(AfkHeuristicState state) {
        if (!Config.afkHeuristicsEnabled) {
            return new HeuristicResult(0, 0, 0, 0, 0, false);
        }

        int minSamples = Config.afkHeuristicMinSamples;

        float movement = MovementEntropyAnalyzer.analyze(state, minSamples);
        float camera = CameraEntropyAnalyzer.analyze(state, minSamples);
        float interaction = InteractionDiversityAnalyzer.analyze(state, minSamples);
        float timing = TimingPatternAnalyzer.analyze(state, minSamples);

        // Weighted sum using configurable weights
        float totalWeight = Config.afkWeightMovement + Config.afkWeightCamera
                + Config.afkWeightInteraction + Config.afkWeightTiming;

        float composite;
        if (totalWeight > 0.001f) {
            composite = (movement * Config.afkWeightMovement
                    + camera * Config.afkWeightCamera
                    + interaction * Config.afkWeightInteraction
                    + timing * Config.afkWeightTiming) / totalWeight;
        } else {
            composite = 0f;
        }

        // Scale by buffer fill ratio — don't make strong judgments on thin data
        float confidence = state.fillRatio();
        if (confidence < 0.5f) {
            composite *= confidence * 2; // Linear ramp: 0 at 0%, full at 50%+
        }

        boolean flagged = composite >= Config.afkHeuristicThreshold;

        return new HeuristicResult(movement, camera, interaction, timing, composite, flagged);
    }

    /**
     * Get a human-readable description of the most suspicious signal.
     */
    public static String getMostSuspiciousSignal(HeuristicResult result) {
        float max = 0;
        String signal = "none";

        if (result.movementScore > max) { max = result.movementScore; signal = "Movement (repetitive pattern / AFK pool)"; }
        if (result.cameraScore > max) { max = result.cameraScore; signal = "Camera (mouse macro / wiggle)"; }
        if (result.interactionScore > max) { max = result.interactionScore; signal = "Interaction (auto-clicker / low diversity)"; }
        if (result.timingScore > max) { max = result.timingScore; signal = "Timing (robotic regularity)"; }

        return signal + String.format(" [%.0f%%]", max * 100);
    }
}

