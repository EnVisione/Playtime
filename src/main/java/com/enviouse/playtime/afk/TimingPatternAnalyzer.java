package com.enviouse.playtime.afk;

/**
 * Analyzes the timing pattern of activity events to detect robotic behaviour.
 * <p>
 * Humans produce activity in bursts with variable gaps. Bots/macros produce
 * activity at suspiciously regular intervals. This analyzer examines:
 * <ul>
 *   <li><b>Inter-activity interval variance</b> — low CV = robotic regularity.</li>
 *   <li><b>Activity density</b> — what fraction of samples have activity?
 *       Too consistent a density (every sample or perfectly every Nth) is suspicious.</li>
 *   <li><b>Gap distribution</b> — humans have a mix of short bursts and long gaps.
 *       Bots have uniform gaps.</li>
 * </ul>
 *
 * @return 0.0 (human timing) to 1.0 (robotic timing)
 */
public class TimingPatternAnalyzer {

    /**
     * Analyze timing patterns and return suspicion score.
     */
    public static float analyze(AfkHeuristicState state, int minSamples) {
        int n = state.getSampleCount();
        if (n < minSamples) return 0f;

        // Collect inter-activity gaps
        int[] gaps = new int[n];
        int gapCount = 0;
        int lastActive = -1;
        int activeCount = 0;

        for (int i = 0; i < n; i++) {
            int idx = state.indexAt(i);
            if (idx >= 0 && state.activityTimestamps[idx] != 0) {
                activeCount++;
                if (lastActive >= 0) {
                    int gap = i - lastActive;
                    if (gapCount < gaps.length) {
                        gaps[gapCount++] = gap;
                    }
                }
                lastActive = i;
            }
        }

        // Not enough data points to judge
        if (gapCount < 5) return 0f;

        float intervalRegularity = analyzeIntervalRegularity(gaps, gapCount);
        float densityScore = analyzeDensityPattern(state, n, activeCount);
        float gapDistribution = analyzeGapDistribution(gaps, gapCount);

        return Math.min(1.0f, intervalRegularity * 0.45f + densityScore * 0.25f + gapDistribution * 0.30f);
    }

    /**
     * Low coefficient of variation in gaps = robotic regularity.
     */
    private static float analyzeIntervalRegularity(int[] gaps, int count) {
        float sum = 0, sumSq = 0;
        for (int i = 0; i < count; i++) {
            sum += gaps[i];
            sumSq += (float) gaps[i] * gaps[i];
        }

        float mean = sum / count;
        float variance = (sumSq / count) - (mean * mean);
        float stdDev = (float) Math.sqrt(Math.max(0, variance));
        float cv = mean > 0.001f ? stdDev / mean : 1.0f;

        // CV < 0.1 = suspiciously regular
        if (cv < 0.05f) return 1.0f;
        if (cv < 0.10f) return 0.8f;
        if (cv < 0.20f) return 0.4f;
        if (cv < 0.35f) return 0.15f;
        return 0f;
    }

    /**
     * Analyze if activity density is suspiciously uniform across window segments.
     * Divide window into 4 quarters and compare activity counts.
     */
    private static float analyzeDensityPattern(AfkHeuristicState state, int n, int totalActive) {
        if (n < 20) return 0f;

        int quarterSize = n / 4;
        int[] quarterCounts = new int[4];

        for (int i = 0; i < n; i++) {
            int idx = state.indexAt(i);
            if (idx >= 0 && state.activityTimestamps[idx] != 0) {
                int quarter = Math.min(i / quarterSize, 3);
                quarterCounts[quarter]++;
            }
        }

        // Check how similar the quarters are
        float qMean = totalActive / 4.0f;
        if (qMean < 1.0f) return 0f;

        float qVariance = 0;
        for (int q = 0; q < 4; q++) {
            float diff = quarterCounts[q] - qMean;
            qVariance += diff * diff;
        }
        qVariance /= 4;
        float qCv = (float) Math.sqrt(qVariance) / qMean;

        // Very uniform across quarters = suspicious
        // (Humans tend to have active and quiet periods)
        if (qCv < 0.10f) return 0.8f;
        if (qCv < 0.20f) return 0.4f;
        if (qCv < 0.35f) return 0.15f;
        return 0f;
    }

    /**
     * Analyze gap distribution — humans have diverse gap lengths.
     * Bots have most gaps at one or two specific lengths.
     */
    private static float analyzeGapDistribution(int[] gaps, int count) {
        if (count < 8) return 0f;

        // Bucket gaps: 1, 2, 3, 4, 5, 6-10, 11-20, 21+
        int[] buckets = new int[8];
        for (int i = 0; i < count; i++) {
            int g = gaps[i];
            if (g <= 5) buckets[Math.min(g - 1, 4)]++;
            else if (g <= 10) buckets[5]++;
            else if (g <= 20) buckets[6]++;
            else buckets[7]++;
        }

        // Find the dominant bucket
        int maxBucket = 0;
        for (int b : buckets) if (b > maxBucket) maxBucket = b;

        float dominance = (float) maxBucket / count;

        // If > 80% of gaps fall in one bucket = very suspicious
        if (dominance > 0.85f) return 1.0f;
        if (dominance > 0.70f) return 0.6f;
        if (dominance > 0.55f) return 0.3f;
        return 0f;
    }
}

