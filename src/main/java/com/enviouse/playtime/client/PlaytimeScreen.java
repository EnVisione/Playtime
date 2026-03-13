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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;

/**
 * Custom GUI screen shown when a player types /playtime.
 * Features:
 *   - Player head (75%, centered in cutout)
 *   - Live playtime counter (up) and next-rank counter (down)
 *   - Top 3 leaderboard with paperdoll skins and live ticking hours
 *   - Paginated rank grid with Box.png, item icons, rank names, and hours
 */
@OnlyIn(Dist.CLIENT)
public class PlaytimeScreen extends Screen {

    // ── Textures ────────────────────────────────────────────────────────────────
    private static final ResourceLocation BACKGROUND    = new ResourceLocation(Playtime.MODID, "textures/gui/background.png");
    private static final ResourceLocation BOX_TEX       = new ResourceLocation(Playtime.MODID, "textures/gui/box.png");
    private static final ResourceLocation GREEN_ARROW   = new ResourceLocation(Playtime.MODID, "textures/gui/green_arrow.png");
    private static final ResourceLocation RED_ARROW     = new ResourceLocation(Playtime.MODID, "textures/gui/red_arrow.png");

    private static final int TEX_WIDTH = 512;
    private static final int TEX_HEIGHT = 256;

    // Box.png native size
    private static final int BOX_SIZE = 33;
    // Arrow native sizes
    private static final int ARROW_W = 25;
    private static final int ARROW_H = 19;

    // ── Rank grid layout constants (in texture-space pixels) ────────────────────
    private static final int RANK_X1 = 219, RANK_Y1 = 49, RANK_X2 = 499, RANK_Y2 = 197;
    // Each slot: rank name (8px) + 1px + box (24px rendered) + 1px + hours (8px) = 42px tall
    private static final int RENDERED_BOX = 24;
    private static final int SLOT_H = 42;
    private static final int SLOT_W = 40; // box + horizontal spacing
    private static final int ARROW_AREA_H = 22; // reserved at bottom for arrows + page text

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
    private final boolean isMaxRank;

    // ── Top 3 data ─────────────────────────────────────────────────────────────
    private final int top3Count;
    private final String[] top3Names;
    private final UUID[] top3Uuids;
    private final long[] top3Ticks;
    private final String[] top3RankNames;
    private final String[] top3RankColors;
    private final boolean[] top3IsAfk;

    // ── Full rank list ─────────────────────────────────────────────────────────
    private final List<PlaytimeDataS2CPacket.RankEntry> allRanks;

    // ── Pagination state ───────────────────────────────────────────────────────
    private int rankPage = 0;
    private int ranksPerPage;
    private int rankCols;
    private int rankRows;
    private int totalRankPages;

    // ── Live counter tracking ──────────────────────────────────────────────────
    private long screenOpenedAtMs;

    // ── Cached scale/offset for click handling ─────────────────────────────────
    private float guiScale;
    private int guiLeft, guiTop;

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
        this.isMaxRank = packet.isMaxRank();
        this.top3Count = packet.getTop3Count();
        this.top3Names = packet.getTop3Names();
        this.top3Uuids = packet.getTop3Uuids();
        this.top3Ticks = packet.getTop3Ticks();
        this.top3RankNames = packet.getTop3RankNames();
        this.top3RankColors = packet.getTop3RankColors();
        this.top3IsAfk = packet.getTop3IsAfk();
        this.allRanks = packet.getAllRanks();
    }

    @Override
    protected void init() {
        super.init();
        this.screenOpenedAtMs = System.currentTimeMillis();

        // Calculate rank grid layout
        int areaW = RANK_X2 - RANK_X1; // 280
        int areaH = RANK_Y2 - RANK_Y1 - ARROW_AREA_H; // usable height for grid
        rankCols = Math.max(1, areaW / SLOT_W);
        rankRows = Math.max(1, areaH / SLOT_H);
        ranksPerPage = rankCols * rankRows;
        totalRankPages = Math.max(1, (allRanks.size() + ranksPerPage - 1) / ranksPerPage);
        if (rankPage >= totalRankPages) rankPage = totalRankPages - 1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        float scaleX = (float) this.width / TEX_WIDTH;
        float scaleY = (float) this.height / TEX_HEIGHT;
        float scale = Math.min(scaleX, scaleY);
        scale = Math.min(scale, 1.0f);
        this.guiScale = scale;

        int scaledW = (int) (TEX_WIDTH * scale);
        int scaledH = (int) (TEX_HEIGHT * scale);
        int left = (this.width - scaledW) / 2;
        int top = (this.height - scaledH) / 2;
        this.guiLeft = left;
        this.guiTop = top;

        g.pose().pushPose();
        g.pose().translate(left, top, 0);
        g.pose().scale(scale, scale, 1.0f);

        // 1. Background
        g.blit(BACKGROUND, 0, 0, 0, 0, TEX_WIDTH, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);

        // 2. Player head (75% = 24×24 centered in 32×31 cutout)
        int headSize = 24;
        int headX = 469 + (32 - headSize) / 2;
        int headY = 212 + (31 - headSize) / 2;
        renderPlayerHead(g, getSkinTexture(playerUuid), headX, headY, headSize);

        // 3. Playtime text with live counters
        renderPlaytimeText(g, 218, 212);

        // 4. Top 3 with live ticking
        renderTop3(g, 14, 75, 202, 197);

        // 5. Ranks panel with pagination
        renderRanksPanel(g, RANK_X1, RANK_Y1, RANK_X2, RANK_Y2);

        g.pose().popPose();
        super.render(g, mouseX, mouseY, partialTick);
    }

    // ── Click handling for pagination arrows ────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && totalRankPages > 1) {
            // Convert screen coords to texture coords
            float tx = (float) (mouseX - guiLeft) / guiScale;
            float ty = (float) (mouseY - guiTop) / guiScale;

            int arrowY = RANK_Y2 - ARROW_AREA_H + 2;

            // Red back arrow (bottom-left of rank area)
            if (rankPage > 0) {
                int redX = RANK_X1;
                if (tx >= redX && tx <= redX + ARROW_W && ty >= arrowY && ty <= arrowY + ARROW_H) {
                    rankPage--;
                    return true;
                }
            }
            // Green next arrow (right of red arrow)
            if (rankPage < totalRankPages - 1) {
                int greenX = RANK_X1 + ARROW_W + 4;
                if (tx >= greenX && tx <= greenX + ARROW_W && ty >= arrowY && ty <= arrowY + ARROW_H) {
                    rankPage++;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ── Player Head ─────────────────────────────────────────────────────────────

    private void renderPlayerHead(GuiGraphics g, ResourceLocation skin, int x, int y, int size) {
        g.blit(skin, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
        g.blit(skin, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
    }

    // ── Skin Texture Lookup ─────────────────────────────────────────────────────

    private ResourceLocation getSkinTexture(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            PlayerInfo info = mc.player.connection.getPlayerInfo(uuid);
            if (info != null) return info.getSkinLocation();
            if (uuid.equals(mc.player.getUUID())) return mc.player.getSkinTextureLocation();
        }
        return DefaultPlayerSkin.getDefaultSkin(uuid);
    }

    // ── Elapsed ticks helper ────────────────────────────────────────────────────

    private long getElapsedTicks() {
        long elapsedMs = System.currentTimeMillis() - screenOpenedAtMs;
        return (elapsedMs * 20) / 1000;
    }

    // ── Playtime Text with Live Counters ────────────────────────────────────────

    private void renderPlaytimeText(GuiGraphics g, int textX, int textY) {
        int lineH = 8;
        long elapsed = getElapsedTicks();

        MutableComponent line1 = Component.literal("§f" + playerName + " ");
        line1.append(isAfk ? Component.literal("§c[AFK]") : Component.literal("§a[Active]"));
        g.drawString(this.font, line1, textX, textY, 0xFFFFFF, false);

        long liveTotal = totalTicks + (isAfk ? 0 : elapsed);
        g.drawString(this.font, "§7Playtime: §f" + TimeParser.formatTicks(liveTotal), textX, textY + lineH, 0xFFFFFF, false);

        MutableComponent line3 = Component.literal("§7Rank: ");
        line3.append(ColorUtil.rankDisplay(currentRankColor, currentRankName));
        g.drawString(this.font, line3, textX, textY + lineH * 2, 0xFFFFFF, false);

        if (isMaxRank) {
            g.drawString(this.font, "§a\u2713 Max rank!", textX, textY + lineH * 3, 0xFFFFFF, false);
        } else {
            long liveRemaining = Math.max(0, ticksToNextRank - (isAfk ? 0 : elapsed));
            MutableComponent line4 = Component.literal("§7Next: ");
            line4.append(ColorUtil.rankDisplay(nextRankColor, nextRankName));
            line4.append(Component.literal(" §7(" + TimeParser.formatTicks(liveRemaining) + ")"));
            g.drawString(this.font, line4, textX, textY + lineH * 3, 0xFFFFFF, false);
        }
    }

    // ── Top 3 Leaderboard (live ticking) ────────────────────────────────────────

    private void renderTop3(GuiGraphics g, int x1, int y1, int x2, int y2) {
        if (top3Count == 0) return;

        int areaW = x2 - x1;
        int colW = areaW / 3;
        long elapsed = getElapsedTicks();

        int[] displayOrder = {1, 0, 2};
        int[] podiumYOffset = {12, 0, 12};

        for (int col = 0; col < 3; col++) {
            int idx = displayOrder[col];
            if (idx >= top3Count) continue;

            int colCenterX = x1 + col * colW + colW / 2;
            int colTopY = y1 + podiumYOffset[col];

            String name = top3Names[idx];
            UUID uuid = top3Uuids[idx];
            // Live-tick the top 3 hours
            long liveTicks = top3Ticks[idx] + (top3IsAfk[idx] ? 0 : elapsed);
            String rankName = top3RankNames[idx];
            String rankColor = top3RankColors[idx];

            int skinScale = 2;
            int skinH = 32 * skinScale;
            int skinW = 16 * skinScale;

            int nameW = this.font.width(name);
            g.drawString(this.font, "§f" + name, colCenterX - nameW / 2, colTopY, 0xFFFFFF, false);

            int skinX = colCenterX - skinW / 2;
            int skinY = colTopY + 10;
            renderPaperdoll(g, getSkinTexture(uuid), skinX, skinY, skinScale);

            MutableComponent rankComp = ColorUtil.rankDisplay(rankColor, rankName);
            int rankW = this.font.width(rankComp);
            g.drawString(this.font, rankComp, colCenterX - rankW / 2, skinY + skinH + 2, 0xFFFFFF, false);

            String hoursStr = TimeParser.formatTicks(liveTicks);
            int hoursW = this.font.width(hoursStr);
            g.drawString(this.font, "§7" + hoursStr, colCenterX - hoursW / 2, skinY + skinH + 12, 0xFFFFFF, false);
        }
    }

    // ── Ranks Panel with Pagination ─────────────────────────────────────────────

    private void renderRanksPanel(GuiGraphics g, int x1, int y1, int x2, int y2) {
        if (allRanks.isEmpty()) return;

        int areaW = x2 - x1;
        int gridH = y2 - y1 - ARROW_AREA_H;

        // Center the grid within the available area
        int totalGridW = rankCols * SLOT_W;
        int gridOffsetX = (areaW - totalGridW) / 2;

        int startIdx = rankPage * ranksPerPage;
        int endIdx = Math.min(startIdx + ranksPerPage, allRanks.size());

        for (int i = startIdx; i < endIdx; i++) {
            int slot = i - startIdx;
            int col = slot % rankCols;
            int row = slot / rankCols;

            PlaytimeDataS2CPacket.RankEntry rank = allRanks.get(i);

            int slotX = x1 + gridOffsetX + col * SLOT_W + (SLOT_W - RENDERED_BOX) / 2;
            int slotY = y1 + row * SLOT_H;

            // Rank name above box (centered on box)
            MutableComponent nameComp = ColorUtil.rankDisplay(rank.color, rank.displayName);
            int nameW = this.font.width(nameComp);
            int boxCenterX = slotX + RENDERED_BOX / 2;
            g.drawString(this.font, nameComp, boxCenterX - nameW / 2, slotY, 0xFFFFFF, false);

            // Box.png
            int boxY = slotY + 9;
            g.blit(BOX_TEX, slotX, boxY, RENDERED_BOX, RENDERED_BOX, 0, 0, BOX_SIZE, BOX_SIZE, BOX_SIZE, BOX_SIZE);

            // Item inside box (centered, 16×16 item at center of 24×24 box)
            if (!rank.defaultItem.isEmpty()) {
                ItemStack stack = getItemStack(rank.defaultItem);
                if (!stack.isEmpty()) {
                    int itemX = slotX + (RENDERED_BOX - 16) / 2;
                    int itemY = boxY + (RENDERED_BOX - 16) / 2;
                    g.renderItem(stack, itemX, itemY);
                }
            }

            // Hours below box
            long hours = rank.thresholdTicks / 72_000L;
            String hoursStr = hours + "h";
            int hoursW = this.font.width(hoursStr);
            g.drawString(this.font, "§7" + hoursStr, boxCenterX - hoursW / 2, boxY + RENDERED_BOX + 1, 0xFFFFFF, false);
        }

        // ── Pagination arrows and page text ─────────────────────────────────────
        if (totalRankPages > 1) {
            int arrowY = y2 - ARROW_AREA_H + 2;

            // Red back arrow (left)
            if (rankPage > 0) {
                g.blit(RED_ARROW, x1, arrowY, ARROW_W, ARROW_H, 0, 0, ARROW_W, ARROW_H, ARROW_W, ARROW_H);
            }

            // Green next arrow (next to red)
            if (rankPage < totalRankPages - 1) {
                g.blit(GREEN_ARROW, x1 + ARROW_W + 4, arrowY, ARROW_W, ARROW_H, 0, 0, ARROW_W, ARROW_H, ARROW_W, ARROW_H);
            }

            // Page X/Y text (centered in area)
            String pageStr = "Page " + (rankPage + 1) + "/" + totalRankPages;
            int pageW = this.font.width(pageStr);
            int pageCenterX = x1 + areaW / 2;
            g.drawString(this.font, "§7" + pageStr, pageCenterX - pageW / 2, arrowY + (ARROW_H - 8) / 2, 0xFFFFFF, false);
        }
    }

    /** Resolve an item ID string to an ItemStack. */
    private ItemStack getItemStack(String itemId) {
        try {
            ResourceLocation rl = new ResourceLocation(itemId);
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item != null && item != Items.AIR) {
                return new ItemStack(item);
            }
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }

    // ── 2D Paperdoll Skin Renderer ──────────────────────────────────────────────

    private void renderPaperdoll(GuiGraphics g, ResourceLocation skin, int x, int y, int s) {
        blitSkin(g, skin, x + 4 * s, y, 8 * s, 8 * s, 8, 8, 8, 8);
        blitSkin(g, skin, x + 4 * s, y, 8 * s, 8 * s, 40, 8, 8, 8);
        blitSkin(g, skin, x + 4 * s, y + 8 * s, 8 * s, 12 * s, 20, 20, 8, 12);
        blitSkin(g, skin, x, y + 8 * s, 4 * s, 12 * s, 44, 20, 4, 12);
        blitSkin(g, skin, x + 12 * s, y + 8 * s, 4 * s, 12 * s, 36, 52, 4, 12);
        blitSkin(g, skin, x + 4 * s, y + 20 * s, 4 * s, 12 * s, 4, 20, 4, 12);
        blitSkin(g, skin, x + 8 * s, y + 20 * s, 4 * s, 12 * s, 20, 52, 4, 12);
    }

    private void blitSkin(GuiGraphics g, ResourceLocation skin,
                          int destX, int destY, int destW, int destH,
                          int srcU, int srcV, int srcW, int srcH) {
        g.blit(skin, destX, destY, destW, destH, (float) srcU, (float) srcV, srcW, srcH, 64, 64);
    }
}
