package com.enviouse.playtime.client;

import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.network.PlaytimeDataS2CPacket;
import com.enviouse.playtime.util.ColorUtil;
import com.enviouse.playtime.util.TimeParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.UUID;

/**
 * Custom GUI screen shown when a player types /playtime.
 * Renders Background.png with:
 *   - Player head (75% size, centered in cutout)
 *   - Live playtime counter (counts up) and next-rank counter (counts down)
 *   - Top 3 leaderboard with 2D paperdoll skin models in podium layout
 */
@OnlyIn(Dist.CLIENT)
public class PlaytimeScreen extends Screen {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(Playtime.MODID, "textures/gui/background.png");

    private static final int TEX_WIDTH = 512;
    private static final int TEX_HEIGHT = 256;

    // ── Data from the server packet ────────────────────────────────────────────
    private final String playerName;
    private final UUID playerUuid;
    private final long totalTicks;
    private final String currentRankName;
    private final String currentRankColor;
    private final String nextRankName;
    private final String nextRankColor;
    private final long ticksToNextRank;
    private final boolean isAfk;
    private final int claims;
    private final int forceloads;
    private final int inactivityDays;
    private final boolean claimsEnabled;
    private final boolean forceloadsEnabled;
    private final boolean isMaxRank;

    // ── Top 3 data ─────────────────────────────────────────────────────────────
    private final int top3Count;
    private final String[] top3Names;
    private final UUID[] top3Uuids;
    private final long[] top3Ticks;
    private final String[] top3RankNames;
    private final String[] top3RankColors;

    // ── Live counter tracking ──────────────────────────────────────────────────
    private long screenOpenedAtMs;

    public PlaytimeScreen(PlaytimeDataS2CPacket packet) {
        super(Component.literal("Playtime"));
        this.playerName = packet.getPlayerName();
        this.playerUuid = packet.getPlayerUuid();
        this.totalTicks = packet.getTotalTicks();
        this.currentRankName = packet.getCurrentRankName();
        this.currentRankColor = packet.getCurrentRankColor();
        this.nextRankName = packet.getNextRankName();
        this.nextRankColor = packet.getNextRankColor();
        this.ticksToNextRank = packet.getTicksToNextRank();
        this.isAfk = packet.isAfk();
        this.claims = packet.getClaims();
        this.forceloads = packet.getForceloads();
        this.inactivityDays = packet.getInactivityDays();
        this.claimsEnabled = packet.isClaimsEnabled();
        this.forceloadsEnabled = packet.isForceloadsEnabled();
        this.isMaxRank = packet.isMaxRank();
        this.top3Count = packet.getTop3Count();
        this.top3Names = packet.getTop3Names();
        this.top3Uuids = packet.getTop3Uuids();
        this.top3Ticks = packet.getTop3Ticks();
        this.top3RankNames = packet.getTop3RankNames();
        this.top3RankColors = packet.getTop3RankColors();
    }

    @Override
    protected void init() {
        super.init();
        this.screenOpenedAtMs = System.currentTimeMillis();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        float scaleX = (float) this.width / TEX_WIDTH;
        float scaleY = (float) this.height / TEX_HEIGHT;
        float scale = Math.min(scaleX, scaleY);
        scale = Math.min(scale, 1.0f);

        int scaledW = (int) (TEX_WIDTH * scale);
        int scaledH = (int) (TEX_HEIGHT * scale);
        int left = (this.width - scaledW) / 2;
        int top = (this.height - scaledH) / 2;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(left, top, 0);
        guiGraphics.pose().scale(scale, scale, 1.0f);

        // ── 1. Background texture ──────────────────────────────────────────────
        guiGraphics.blit(BACKGROUND, 0, 0, 0, 0, TEX_WIDTH, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);

        // ── 2. Player head at 75% (24×24 centered in 32×31 cutout) ─────────────
        int cutoutX = 469, cutoutY = 212, cutoutW = 32, cutoutH = 31;
        int headSize = 24; // 75% of 32
        int headX = cutoutX + (cutoutW - headSize) / 2;
        int headY = cutoutY + (cutoutH - headSize) / 2;
        renderPlayerHead(guiGraphics, getSkinTexture(playerUuid), headX, headY, headSize);

        // ── 3. Playtime text with live counters ─────────────────────────────────
        renderPlaytimeText(guiGraphics, 218, 212);

        // ── 4. Top 3 leaderboard (x14,y75 to x202,y197) ────────────────────────
        renderTop3(guiGraphics, 14, 75, 202, 197);

        guiGraphics.pose().popPose();

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    // ── Player Head ─────────────────────────────────────────────────────────────

    private void renderPlayerHead(GuiGraphics g, ResourceLocation skin, int x, int y, int size) {
        // Face layer: UV (8,8), 8×8 region
        g.blit(skin, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
        // Hat overlay: UV (40,8), 8×8 region
        g.blit(skin, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
    }

    // ── Skin Texture Lookup ─────────────────────────────────────────────────────

    private ResourceLocation getSkinTexture(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            PlayerInfo info = mc.player.connection.getPlayerInfo(uuid);
            if (info != null) {
                return info.getSkinLocation();
            }
            // If checking our own UUID and no PlayerInfo, use our local skin
            if (uuid.equals(mc.player.getUUID())) {
                return mc.player.getSkinTextureLocation();
            }
        }
        return DefaultPlayerSkin.getDefaultSkin(uuid);
    }

    // ── Playtime Text with Live Counters ────────────────────────────────────────

    private void renderPlaytimeText(GuiGraphics g, int textX, int textY) {
        int lineH = 8;

        // Calculate elapsed ticks since screen opened (for live counter)
        long elapsedMs = System.currentTimeMillis() - screenOpenedAtMs;
        long elapsedTicks = (elapsedMs * 20) / 1000; // 20 ticks per second

        // Line 1: Username + status
        MutableComponent line1 = Component.literal("§f" + playerName + " ");
        line1.append(isAfk ? Component.literal("§c[AFK]") : Component.literal("§a[Active]"));
        g.drawString(this.font, line1, textX, textY, 0xFFFFFF, false);

        // Line 2: Total playtime (counts UP live)
        long liveTotal = totalTicks + (isAfk ? 0 : elapsedTicks);
        g.drawString(this.font, "§7Playtime: §f" + TimeParser.formatTicks(liveTotal), textX, textY + lineH, 0xFFFFFF, false);

        // Line 3: Current rank
        MutableComponent line3 = Component.literal("§7Rank: ");
        line3.append(ColorUtil.rankDisplay(currentRankColor, currentRankName));
        g.drawString(this.font, line3, textX, textY + lineH * 2, 0xFFFFFF, false);

        // Line 4: Next rank (counts DOWN live) or max
        if (isMaxRank) {
            g.drawString(this.font, "§a\u2713 Max rank!", textX, textY + lineH * 3, 0xFFFFFF, false);
        } else {
            long liveRemaining = Math.max(0, ticksToNextRank - (isAfk ? 0 : elapsedTicks));
            MutableComponent line4 = Component.literal("§7Next: ");
            line4.append(ColorUtil.rankDisplay(nextRankColor, nextRankName));
            line4.append(Component.literal(" §7(" + TimeParser.formatTicks(liveRemaining) + ")"));
            g.drawString(this.font, line4, textX, textY + lineH * 3, 0xFFFFFF, false);
        }
    }

    // ── Top 3 Leaderboard ───────────────────────────────────────────────────────

    /**
     * Renders the top 3 players in a podium layout within the given bounds.
     * Order: #2 left, #1 center (raised), #3 right.
     * Each player: name above, 2D paperdoll skin, rank below, hours below rank.
     */
    private void renderTop3(GuiGraphics g, int x1, int y1, int x2, int y2) {
        if (top3Count == 0) return;

        int areaW = x2 - x1; // 188
        int colW = areaW / 3; // ~62

        // Podium display order:  index 1 (#2) left,  index 0 (#1) center,  index 2 (#3) right
        int[] displayOrder = {1, 0, 2}; // column 0=left(#2), column 1=center(#1), column 2=right(#3)
        int[] podiumYOffset = {12, 0, 12}; // #1 raised, #2/#3 lower

        for (int col = 0; col < 3; col++) {
            int idx = displayOrder[col];
            if (idx >= top3Count) continue;

            int colCenterX = x1 + col * colW + colW / 2;
            int colTopY = y1 + podiumYOffset[col];

            String name = top3Names[idx];
            UUID uuid = top3Uuids[idx];
            long ticks = top3Ticks[idx];
            String rankName = top3RankNames[idx];
            String rankColor = top3RankColors[idx];

            // Layout from top: name (8px) → 2px gap → skin (64px) → 2px gap → rank (8px) → 1px → hours (8px)
            int skinScale = 2; // each skin pixel = 2 screen pixels
            int skinH = 32 * skinScale; // 64px total height for paperdoll
            int skinW = 16 * skinScale; // 32px total width

            // Name (centered above skin)
            int nameW = this.font.width(name);
            int nameX = colCenterX - nameW / 2;
            g.drawString(this.font, "§f" + name, nameX, colTopY, 0xFFFFFF, false);

            // Paperdoll skin (centered)
            int skinX = colCenterX - skinW / 2;
            int skinY = colTopY + 10;
            ResourceLocation skinTex = getSkinTexture(uuid);
            renderPaperdoll(g, skinTex, skinX, skinY, skinScale);

            // Rank (centered below skin)
            MutableComponent rankComp = ColorUtil.rankDisplay(rankColor, rankName);
            int rankW = this.font.width(rankComp);
            int rankX = colCenterX - rankW / 2;
            g.drawString(this.font, rankComp, rankX, skinY + skinH + 2, 0xFFFFFF, false);

            // Hours (centered below rank)
            String hoursStr = TimeParser.formatTicks(ticks);
            int hoursW = this.font.width(hoursStr);
            int hoursX = colCenterX - hoursW / 2;
            g.drawString(this.font, "§7" + hoursStr, hoursX, skinY + skinH + 12, 0xFFFFFF, false);
        }
    }

    // ── 2D Paperdoll Skin Renderer ──────────────────────────────────────────────

    /**
     * Renders a front-facing 2D assembled player skin (head, body, arms, legs).
     *
     * Skin UV layout (64×64 texture):
     *   Head front:      u=8,  v=8,  w=8, h=8
     *   Hat overlay:     u=40, v=8,  w=8, h=8
     *   Body front:      u=20, v=20, w=8, h=12
     *   Right Arm front: u=44, v=20, w=4, h=12  (appears LEFT when facing viewer)
     *   Left Arm front:  u=36, v=52, w=4, h=12  (appears RIGHT when facing viewer)
     *   Right Leg front: u=4,  v=20, w=4, h=12  (appears LEFT when facing viewer)
     *   Left Leg front:  u=20, v=52, w=4, h=12  (appears RIGHT when facing viewer)
     *
     * Total assembled size: 16×32 skin pixels, rendered at s pixels per skin pixel.
     *
     * @param g      GuiGraphics context
     * @param skin   skin texture ResourceLocation
     * @param x      top-left X of the rendered figure
     * @param y      top-left Y of the rendered figure
     * @param s      scale: screen pixels per skin pixel
     */
    private void renderPaperdoll(GuiGraphics g, ResourceLocation skin, int x, int y, int s) {
        // Head (8×8) — centered at top, offset by 4s from left edge
        blitSkin(g, skin, x + 4 * s, y, 8 * s, 8 * s, 8, 8, 8, 8);
        // Hat overlay
        blitSkin(g, skin, x + 4 * s, y, 8 * s, 8 * s, 40, 8, 8, 8);

        // Body (8×12) — below head
        blitSkin(g, skin, x + 4 * s, y + 8 * s, 8 * s, 12 * s, 20, 20, 8, 12);

        // Right Arm (4×12) — left side visually
        blitSkin(g, skin, x, y + 8 * s, 4 * s, 12 * s, 44, 20, 4, 12);

        // Left Arm (4×12) — right side visually
        blitSkin(g, skin, x + 12 * s, y + 8 * s, 4 * s, 12 * s, 36, 52, 4, 12);

        // Right Leg (4×12) — left side visually
        blitSkin(g, skin, x + 4 * s, y + 20 * s, 4 * s, 12 * s, 4, 20, 4, 12);

        // Left Leg (4×12) — right side visually
        blitSkin(g, skin, x + 8 * s, y + 20 * s, 4 * s, 12 * s, 20, 52, 4, 12);
    }

    /** Helper to blit a region of a 64×64 skin texture. */
    private void blitSkin(GuiGraphics g, ResourceLocation skin,
                          int destX, int destY, int destW, int destH,
                          int srcU, int srcV, int srcW, int srcH) {
        g.blit(skin, destX, destY, destW, destH, (float) srcU, (float) srcV, srcW, srcH, 64, 64);
    }
}
