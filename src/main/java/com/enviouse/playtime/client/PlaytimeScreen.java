package com.enviouse.playtime.client;

import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.network.PlaytimeDataS2CPacket;
import com.enviouse.playtime.util.ColorUtil;
import com.enviouse.playtime.util.TimeParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Custom GUI screen shown when a player types /playtime.
 * Renders the Background.png texture with the player's head and stats overlay.
 */
@OnlyIn(Dist.CLIENT)
public class PlaytimeScreen extends Screen {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(Playtime.MODID, "textures/gui/background.png");

    /** Native size of the background texture. */
    private static final int TEX_WIDTH = 512;
    private static final int TEX_HEIGHT = 256;

    // ── Data from the server packet ────────────────────────────────────────────
    private final String playerName;
    private final java.util.UUID playerUuid;
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
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Dark overlay behind the GUI
        this.renderBackground(guiGraphics);

        // Calculate scale so the 512x256 texture fits the screen
        float scaleX = (float) this.width / TEX_WIDTH;
        float scaleY = (float) this.height / TEX_HEIGHT;
        float scale = Math.min(scaleX, scaleY);
        scale = Math.min(scale, 1.0f); // never scale above 100%

        int scaledW = (int) (TEX_WIDTH * scale);
        int scaledH = (int) (TEX_HEIGHT * scale);
        int left = (this.width - scaledW) / 2;
        int top = (this.height - scaledH) / 2;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(left, top, 0);
        guiGraphics.pose().scale(scale, scale, 1.0f);

        // ── 1. Background texture ──────────────────────────────────────────────
        guiGraphics.blit(BACKGROUND, 0, 0, 0, 0, TEX_WIDTH, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);

        // ── 2. Player head (x469,y212 to x501,y243 — 32×31 area) ──────────────
        renderPlayerHead(guiGraphics, 469, 212, 32);

        // ── 3. Playtime text (x216,y210 to x448,y244 — 232×34 area) ───────────
        renderPlaytimeText(guiGraphics, 218, 212);

        guiGraphics.pose().popPose();

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /**
     * Renders the player's Minecraft face (skin face layer + hat overlay).
     * The skin face is at UV (8,8)→(16,16) on the 64×64 skin texture.
     * The hat overlay is at UV (40,8)→(48,16).
     */
    private void renderPlayerHead(GuiGraphics guiGraphics, int x, int y, int size) {
        ResourceLocation skinTexture = getSkinTexture();

        // Face layer: UV (8, 8), 8×8 source region, rendered at size×size
        guiGraphics.blit(skinTexture, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);

        // Hat overlay layer: UV (40, 8), same region size
        guiGraphics.blit(skinTexture, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
    }

    /**
     * Get the skin texture for the player. Uses the network player info
     * to get the actual skin, falling back to the default skin.
     */
    private ResourceLocation getSkinTexture() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            PlayerInfo info = mc.player.connection.getPlayerInfo(playerUuid);
            if (info != null) {
                return info.getSkinLocation();
            }
            // Fallback: if it's our own player
            return mc.player.getSkinTextureLocation();
        }
        return new ResourceLocation("textures/entity/player/wide/steve.png");
    }

    /**
     * Renders playtime stats text in the bottom status bar area.
     * Fits within the 232×34 pixel region starting at (textX, textY).
     *
     * Layout (4 lines, 8px line height):
     *   Line 1: PlayerName [Active/AFK]
     *   Line 2: Playtime: Xh Xm Xs
     *   Line 3: Rank: CurrentRankName
     *   Line 4: Next: NextRankName (time left) / ✓ Max rank!
     */
    private void renderPlaytimeText(GuiGraphics guiGraphics, int textX, int textY) {
        int lineH = 8;

        // Line 1: Username + status
        MutableComponent line1 = Component.literal("§f" + playerName + " ");
        if (isAfk) {
            line1.append(Component.literal("§c[AFK]"));
        } else {
            line1.append(Component.literal("§a[Active]"));
        }
        guiGraphics.drawString(this.font, line1, textX, textY, 0xFFFFFF, false);

        // Line 2: Total playtime
        String timeStr = TimeParser.formatTicks(totalTicks);
        guiGraphics.drawString(this.font, "§7Playtime: §f" + timeStr, textX, textY + lineH, 0xFFFFFF, false);

        // Line 3: Current rank (with color)
        MutableComponent line3 = Component.literal("§7Rank: ");
        line3.append(ColorUtil.rankDisplay(currentRankColor, currentRankName));
        guiGraphics.drawString(this.font, line3, textX, textY + lineH * 2, 0xFFFFFF, false);

        // Line 4: Next rank or max
        if (isMaxRank) {
            guiGraphics.drawString(this.font, "§a✓ Max rank!", textX, textY + lineH * 3, 0xFFFFFF, false);
        } else {
            MutableComponent line4 = Component.literal("§7Next: ");
            line4.append(ColorUtil.rankDisplay(nextRankColor, nextRankName));
            line4.append(Component.literal(" §7(" + TimeParser.formatTicks(ticksToNextRank) + ")"));
            guiGraphics.drawString(this.font, line4, textX, textY + lineH * 3, 0xFFFFFF, false);
        }
    }
}

