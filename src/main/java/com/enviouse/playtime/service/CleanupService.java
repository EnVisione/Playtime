package com.enviouse.playtime.service;

import com.enviouse.playtime.Config;
import com.enviouse.playtime.config.RankConfig;
import com.enviouse.playtime.data.PlayerDataRepository;
import com.enviouse.playtime.data.PlayerRecord;
import com.enviouse.playtime.data.RankDefinition;
import com.enviouse.playtime.integration.OpacBridge;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Auto-wipes claims for players who exceed their rank's inactivity limit.
 */
public class CleanupService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MS_PER_DAY = 24 * 60 * 60 * 1000L;

    private final PlayerDataRepository repository;
    private final RankConfig rankConfig;
    private final OpacBridge opacBridge;

    public CleanupService(PlayerDataRepository repository, RankConfig rankConfig, OpacBridge opacBridge) {
        this.repository = repository;
        this.rankConfig = rankConfig;
        this.opacBridge = opacBridge;
    }

    /**
     * Run the cleanup check. If sender is non-null, send feedback messages.
     *
     * @param server    the server
     * @param sender    optional command source for feedback (null for automated runs)
     * @param dryRun    if true, don't actually wipe — just report
     */
    public void runCleanup(MinecraftServer server, @Nullable CommandSourceStack sender, boolean dryRun) {
        if (!Config.opacEnabled) {
            msg(sender, "§c[ClaimCleanup] Skipping: OpenPAC integration disabled in config.");
            return;
        }
        if (!opacBridge.isAvailable(server)) {
            msg(sender, "§c[ClaimCleanup] Skipping: OpenPAC API not available.");
            return;
        }
        if (!repository.isLoaded()) {
            msg(sender, "§c[ClaimCleanup] Skipping: playtime data not loaded.");
            return;
        }

        msg(sender, "§e[ClaimCleanup] Starting claim cleanup check" + (dryRun ? " (DRY RUN)" : "") + "...");

        long now = System.currentTimeMillis();
        int wipedCount = 0;
        int skippedCount = 0;
        int alreadyProcessed = 0;

        // Snapshot to avoid ConcurrentModification
        List<PlayerRecord> snapshot = new ArrayList<>(repository.getAllPlayers());

        for (PlayerRecord record : snapshot) {
            RankDefinition rank = rankConfig.getRankById(record.getCurrentRankId());
            if (rank == null) rank = rankConfig.getFirstRank();
            if (rank == null) continue;

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
            msg(sender, "§7[ClaimCleanup] " + name + " (" + rank.getDisplayName() + ") inactive " +
                    daysSinceLastSeen + "d (limit: " + inactivityLimit + "d)");

            if (!dryRun) {
                int removed = opacBridge.wipePlayerClaims(server, record.getUuid(), false);
                msg(sender, removed > 0
                        ? "§a[ClaimCleanup] Wiped " + removed + " claims for " + name
                        : "§7[ClaimCleanup] No claims found for " + name);

                record.setClaimsWipedAtMs(now);
                record.setClaimsWipeLastSeenMs(record.getLastSeenEpochMs());
                repository.markDirty();
            } else {
                msg(sender, "§7[ClaimCleanup] (dry run) Would wipe claims for " + name);
            }

            wipedCount++;
        }

        msg(sender, "§a[ClaimCleanup] Complete. Processed=" + wipedCount +
                ", already=" + alreadyProcessed + ", skipped=" + skippedCount);

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

