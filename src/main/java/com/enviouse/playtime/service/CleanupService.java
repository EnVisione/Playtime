package com.enviouse.playtime.service;

import com.enviouse.playtime.Config;
import com.enviouse.playtime.config.RankConfig;
import com.enviouse.playtime.data.InactivityAction;
import com.enviouse.playtime.data.PlayerDataRepository;
import com.enviouse.playtime.data.PlayerRecord;
import com.enviouse.playtime.data.RankDefinition;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Auto-runs inactivity actions for players who exceed their rank's inactivity limits.
 * Supports modular command-based actions per rank, with fallback to legacy OPAC claim wipe.
 */
public class CleanupService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MS_PER_DAY = 24 * 60 * 60 * 1000L;

    private final PlayerDataRepository repository;
    private final RankConfig rankConfig;

    public CleanupService(PlayerDataRepository repository, RankConfig rankConfig) {
        this.repository = repository;
        this.rankConfig = rankConfig;
    }

    /**
     * Run the cleanup check. If sender is non-null, send feedback messages.
     *
     * @param server    the server
     * @param sender    optional command source for feedback (null for automated runs)
     * @param dryRun    if true, don't actually execute — just report
     */
    public void runCleanup(MinecraftServer server, @Nullable CommandSourceStack sender, boolean dryRun) {
        if (!repository.isLoaded()) {
            msg(sender, "§c[Cleanup] Skipping: playtime data not loaded.");
            return;
        }

        msg(sender, "§e[Cleanup] Starting cleanup check" + (dryRun ? " (DRY RUN)" : "") + "...");

        long now = System.currentTimeMillis();
        int processedCount = 0;
        int skippedCount = 0;
        int alreadyProcessed = 0;
        int actionsExecuted = 0;

        // Snapshot to avoid ConcurrentModification
        List<PlayerRecord> snapshot = new ArrayList<>(repository.getAllPlayers());

        for (PlayerRecord record : snapshot) {
            RankDefinition rank = rankConfig.getRankById(record.getCurrentRankId());
            if (rank == null) rank = rankConfig.getFirstRank();
            if (rank == null) continue;

            List<InactivityAction> actions = rank.getInactivityActions();
            boolean hasModularActions = actions != null && !actions.isEmpty();

            // If no modular actions, fall back to legacy inactivityDays + OPAC wipe
            if (!hasModularActions) {
                int inactivityLimit = rank.getInactivityDays();
                if (inactivityLimit == -1) continue; // immune

                long daysSinceLastSeen = (now - record.getLastSeenEpochMs()) / MS_PER_DAY;
                if (daysSinceLastSeen < inactivityLimit) continue;

                // Skip if already wiped since last login
                if (record.getClaimsWipedAtMs() > 0 && record.getClaimsWipeLastSeenMs() == record.getLastSeenEpochMs()) {
                    alreadyProcessed++;
                    continue;
                }

                String name = record.getLastUsername() != null ? record.getLastUsername() : record.getUuid().toString();

                // Legacy fallback: wipe claims via /openpac-wipe command (run 3 times for reliability)
                if (Config.opacEnabled) {
                    msg(sender, "§7[Cleanup] " + name + " (" + rank.getDisplayName() + ") inactive " +
                            daysSinceLastSeen + "d (limit: " + inactivityLimit + "d)");

                    if (!dryRun) {
                        String wipeTarget = record.getLastUsername() != null
                                ? record.getLastUsername()
                                : record.getUuid().toString();
                        int successCount = 0;
                        for (int attempt = 1; attempt <= 3; attempt++) {
                            try {
                                server.getCommands().performPrefixedCommand(
                                        server.createCommandSourceStack().withSuppressedOutput(),
                                        "openpac-wipe " + wipeTarget);
                                successCount++;
                                LOGGER.info("[Playtime] /openpac-wipe {} — pass {}/3 completed", wipeTarget, attempt);
                            } catch (Exception e) {
                                LOGGER.warn("[Playtime] /openpac-wipe {} — pass {}/3 failed: {}",
                                        wipeTarget, attempt, e.getMessage());
                            }
                        }
                        msg(sender, "§a[Cleanup] Ran /openpac-wipe " + successCount + "/3 times for " + name);
                        record.setClaimsWipedAtMs(now);
                        record.setClaimsWipeLastSeenMs(record.getLastSeenEpochMs());
                        repository.markDirty();
                    } else {
                        msg(sender, "§7[Cleanup] (dry run) Would run /openpac-wipe 3x for " + name);
                    }
                    processedCount++;
                } else {
                    skippedCount++;
                }
                continue;
            }

            // ── Modular inactivity actions ───────────────────────────────────────
            long daysSinceLastSeen = (now - record.getLastSeenEpochMs()) / MS_PER_DAY;

            // Skip if already processed since last login
            if (record.getClaimsWipedAtMs() > 0 && record.getClaimsWipeLastSeenMs() == record.getLastSeenEpochMs()) {
                alreadyProcessed++;
                continue;
            }

            String name = record.getLastUsername() != null ? record.getLastUsername() : record.getUuid().toString();
            boolean anyActionTriggered = false;

            for (InactivityAction action : actions) {
                if (action.getDelayDays() < 0) continue; // safety
                if (daysSinceLastSeen < action.getDelayDays()) continue;

                String resolved = action.resolveCommand(record.getUuid(), record.getLastUsername(), record.getCurrentRankId());
                // Strip leading / if present — performPrefixedCommand doesn't want it
                if (resolved.startsWith("/")) resolved = resolved.substring(1);

                msg(sender, "§7[Cleanup] " + name + " (" + rank.getDisplayName() + ") inactive " +
                        daysSinceLastSeen + "d ≥ " + action.getDelayDays() + "d → " + action.getCommand());

                if (!dryRun) {
                    try {
                        server.getCommands().performPrefixedCommand(
                                server.createCommandSourceStack().withSuppressedOutput(), resolved);
                        actionsExecuted++;
                    } catch (Exception e) {
                        LOGGER.warn("[Playtime] Inactivity action failed for {}: {}", name, e.getMessage());
                        msg(sender, "§c[Cleanup] Action failed for " + name + ": " + e.getMessage());
                    }
                } else {
                    msg(sender, "§7[Cleanup] (dry run) Would execute: /" + resolved);
                }
                anyActionTriggered = true;
            }

            if (anyActionTriggered) {
                if (!dryRun) {
                    record.setClaimsWipedAtMs(now);
                    record.setClaimsWipeLastSeenMs(record.getLastSeenEpochMs());
                    repository.markDirty();
                }
                processedCount++;
            }
        }

        msg(sender, "§a[Cleanup] Complete. Processed=" + processedCount +
                ", already=" + alreadyProcessed + ", skipped=" + skippedCount +
                (actionsExecuted > 0 ? ", actions=" + actionsExecuted : ""));

        if (!dryRun) {
            repository.save(false);
        }
    }

    private void msg(@Nullable CommandSourceStack sender, String text) {
        LOGGER.info(text.replaceAll("§.", ""));
        if (sender != null) {
            sender.sendSystemMessage(Component.literal(text));
        }
    }
}
