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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class PlaytimeScreen extends Screen {

    // ── Textures ────────────────────────────────────────────────────────────────
    private static final ResourceLocation BACKGROUND   = new ResourceLocation(Playtime.MODID, "textures/gui/background.png");
    private static final ResourceLocation BOX_TEX      = new ResourceLocation(Playtime.MODID, "textures/gui/box.png");
    private static final ResourceLocation GREEN_ARROW  = new ResourceLocation(Playtime.MODID, "textures/gui/green_arrow.png");
    private static final ResourceLocation RED_ARROW    = new ResourceLocation(Playtime.MODID, "textures/gui/red_arrow.png");
    private static final ResourceLocation LIST_BG_TEX  = new ResourceLocation(Playtime.MODID, "textures/gui/listbackground.png");
    private static final ResourceLocation ENTRY_TEX    = new ResourceLocation(Playtime.MODID, "textures/gui/playerlist.png");
    private static final ResourceLocation SCROLLBAR    = new ResourceLocation(Playtime.MODID, "textures/gui/scrollbar.png");

    private static final int TEX_W = 512, TEX_H = 256;

    // Box / Arrow native sizes
    private static final int BOX_N = 33, RBOX = 24;
    private static final int ARROW_W = 25, ARROW_H = 19;

    // Rank grid layout
    private static final int RK_X1 = 219, RK_Y1 = 49, RK_X2 = 499, RK_Y2 = 197;
    private static final int SLOT_H = 42, SLOT_W = 40, ARROW_AREA = 22;

    // Toggle arrow area (main background coords)
    private static final int TGL_X1 = 8, TGL_Y1 = 209, TGL_X2 = 45, TGL_Y2 = 247;

    // List background position & native size
    private static final int LBG_X = 4, LBG_Y = 4, LBG_W = 201, LBG_H = 201;
    // Entry area inside listbackground
    private static final int LE_X = 5, LE_Y = 89, LE_W = 175, LE_H = 105;
    // PlayerList.png native size
    private static final int ENT_W = 174, ENT_H = 23, ENT_GAP = 2, ENT_STRIDE = 25;
    // Scrollbar track inside listbackground
    private static final int SB_X = 193, SB_Y = 87, SB_W = 4, SB_H = 110;
    private static final int SBT_W = 4, SBT_H = 5; // thumb size
    // Search field inside listbackground
    private static final int SEARCH_X = 8, SEARCH_Y = 68, SEARCH_W = 177, SEARCH_H = 14;

    // ── Data ────────────────────────────────────────────────────────────────────
    private final String playerName;
    private final UUID playerUuid;
    private final long totalTicks;
    private final String currentRankName, currentRankColor;
    private final String nextRankName, nextRankColor;
    private final long ticksToNextRank;
    private final boolean isAfk, isMaxRank;

    private final int top3Count;
    private final String[] top3Names;
    private final UUID[] top3Uuids;
    private final long[] top3Ticks;
    private final String[] top3RankNames, top3RankColors;
    private final boolean[] top3IsAfk;

    private final List<PlaytimeDataS2CPacket.RankEntry> allRanks;
    private final List<PlaytimeDataS2CPacket.PlayerListEntry> allPlayers;

    // ── State ───────────────────────────────────────────────────────────────────
    private long screenOpenedAtMs;
    private boolean listMode = false;
    private float scrollOffset = 0;
    private boolean draggingScrollbar = false;
    private String searchText = "";
    private boolean searchFocused = false;
    private List<PlaytimeDataS2CPacket.PlayerListEntry> filteredPlayers;

    // Rank pagination
    private int rankPage, ranksPerPage, rankCols, rankRows, totalRankPages;

    // Cached GUI transform
    private float guiScale;
    private int guiLeft, guiTop;

    public PlaytimeScreen(PlaytimeDataS2CPacket p) {
        super(Component.literal("Playtime"));
        this.playerName = p.getPlayerName();
        this.playerUuid = p.getPlayerUuid();
        this.totalTicks = p.getTotalTicks();
        this.currentRankName = p.getCurrentRankName();
        this.currentRankColor = p.getCurrentRankColor();
        this.nextRankName = p.getNextRankName();
        this.nextRankColor = p.getNextRankColor();
        this.ticksToNextRank = p.getTicksToNextRank();
        this.isAfk = p.isAfk();
        this.isMaxRank = p.isMaxRank();
        this.top3Count = p.getTop3Count();
        this.top3Names = p.getTop3Names();
        this.top3Uuids = p.getTop3Uuids();
        this.top3Ticks = p.getTop3Ticks();
        this.top3RankNames = p.getTop3RankNames();
        this.top3RankColors = p.getTop3RankColors();
        this.top3IsAfk = p.getTop3IsAfk();
        this.allRanks = p.getAllRanks();
        this.allPlayers = p.getPlayerList();
        this.filteredPlayers = new ArrayList<>(allPlayers);
    }

    @Override
    protected void init() {
        super.init();
        screenOpenedAtMs = System.currentTimeMillis();
        int aW = RK_X2 - RK_X1;
        int aH = RK_Y2 - RK_Y1 - ARROW_AREA;
        rankCols = Math.max(1, aW / SLOT_W);
        rankRows = Math.max(1, aH / SLOT_H);
        ranksPerPage = rankCols * rankRows;
        totalRankPages = Math.max(1, (allRanks.size() + ranksPerPage - 1) / ranksPerPage);
        if (rankPage >= totalRankPages) rankPage = totalRankPages - 1;
        applySearch();
    }

    @Override public boolean isPauseScreen() { return false; }

    // ── Render ──────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);

        float sx = (float) width / TEX_W, sy = (float) height / TEX_H;
        float sc = Math.min(Math.min(sx, sy), 1f);
        guiScale = sc;
        int sw = (int)(TEX_W * sc), sh = (int)(TEX_H * sc);
        int l = (width - sw) / 2, t = (height - sh) / 2;
        guiLeft = l; guiTop = t;

        g.pose().pushPose();
        g.pose().translate(l, t, 0);
        g.pose().scale(sc, sc, 1f);

        // Background
        g.blit(BACKGROUND, 0, 0, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);

        // Player head (75%)
        int hs = 24, hx = 469 + (32 - hs) / 2, hy = 212 + (31 - hs) / 2;
        renderHead(g, getSkin(playerUuid), hx, hy, hs);

        // Playtime text
        renderPlaytimeText(g, 218, 212);

        // Ranks panel
        renderRanksPanel(g);

        // Toggle arrow in (8,209)-(45,247)
        int arrowX = TGL_X1 + (TGL_X2 - TGL_X1 - ARROW_W) / 2;
        int arrowY = TGL_Y1 + (TGL_Y2 - TGL_Y1 - ARROW_H) / 2;
        if (listMode) {
            g.blit(RED_ARROW, arrowX, arrowY, ARROW_W, ARROW_H, 0, 0, ARROW_W, ARROW_H, ARROW_W, ARROW_H);
        } else {
            g.blit(GREEN_ARROW, arrowX, arrowY, ARROW_W, ARROW_H, 0, 0, ARROW_W, ARROW_H, ARROW_W, ARROW_H);
        }

        // Left panel: TOP 3 or LIST
        if (listMode) {
            renderListView(g);
        } else {
            renderTop3(g, 14, 75, 202, 197);
        }

        g.pose().popPose();
        super.render(g, mx, my, pt);
    }

    // ── Input ───────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        float tx = (float)(mx - guiLeft) / guiScale;
        float ty = (float)(my - guiTop) / guiScale;

        // Toggle arrow
        if (tx >= TGL_X1 && tx <= TGL_X2 && ty >= TGL_Y1 && ty <= TGL_Y2) {
            listMode = !listMode;
            scrollOffset = 0;
            searchText = "";
            searchFocused = false;
            applySearch();
            return true;
        }

        if (listMode) {
            // Search field click
            float sfx = LBG_X + SEARCH_X, sfy = LBG_Y + SEARCH_Y;
            if (tx >= sfx && tx <= sfx + SEARCH_W && ty >= sfy && ty <= sfy + SEARCH_H) {
                searchFocused = true;
                return true;
            } else {
                searchFocused = false;
            }
            // Scrollbar drag start
            float sbAbsX = LBG_X + SB_X, sbAbsY = LBG_Y + SB_Y;
            if (tx >= sbAbsX && tx <= sbAbsX + SB_W && ty >= sbAbsY && ty <= sbAbsY + SB_H) {
                draggingScrollbar = true;
                updateScrollFromMouse(ty);
                return true;
            }
        } else {
            // Rank pagination arrows
            if (totalRankPages > 1) {
                int aY = RK_Y2 - ARROW_AREA + 2;
                if (rankPage > 0 && tx >= RK_X1 && tx <= RK_X1 + ARROW_W && ty >= aY && ty <= aY + ARROW_H) { rankPage--; return true; }
                if (rankPage < totalRankPages - 1 && tx >= RK_X1 + ARROW_W + 4 && tx <= RK_X1 + ARROW_W * 2 + 4 && ty >= aY && ty <= aY + ARROW_H) { rankPage++; return true; }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        draggingScrollbar = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (draggingScrollbar && btn == 0) {
            float ty = (float)(my - guiTop) / guiScale;
            updateScrollFromMouse(ty);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (listMode) {
            scrollOffset -= (float)(delta * ENT_STRIDE);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean charTyped(char c, int mod) {
        if (searchFocused && listMode) {
            searchText += c;
            applySearch();
            scrollOffset = 0;
            return true;
        }
        return super.charTyped(c, mod);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (searchFocused && listMode) {
            if (key == 259 && !searchText.isEmpty()) { // Backspace
                searchText = searchText.substring(0, searchText.length() - 1);
                applySearch();
                scrollOffset = 0;
                return true;
            }
            if (key == 256) { // Escape
                searchFocused = false;
                return true;
            }
            // Consume all keys while typing so the screen doesn't close
            if (key != 256) return true;
        }
        return super.keyPressed(key, scan, mod);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private long elapsed() {
        return (System.currentTimeMillis() - screenOpenedAtMs) * 20 / 1000;
    }

    private ResourceLocation getSkin(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            PlayerInfo info = mc.player.connection.getPlayerInfo(uuid);
            if (info != null) return info.getSkinLocation();
            if (uuid.equals(mc.player.getUUID())) return mc.player.getSkinTextureLocation();
        }
        return DefaultPlayerSkin.getDefaultSkin(uuid);
    }

    private void renderHead(GuiGraphics g, ResourceLocation skin, int x, int y, int s) {
        g.blit(skin, x, y, s, s, 8f, 8f, 8, 8, 64, 64);
        g.blit(skin, x, y, s, s, 40f, 8f, 8, 8, 64, 64);
    }

    private void applySearch() {
        filteredPlayers = new ArrayList<>();
        String q = searchText.toLowerCase(Locale.ROOT);
        for (PlaytimeDataS2CPacket.PlayerListEntry e : allPlayers) {
            if (q.isEmpty() || e.name.toLowerCase(Locale.ROOT).contains(q)) {
                filteredPlayers.add(e);
            }
        }
    }

    private int maxScroll() {
        int total = filteredPlayers.size() * ENT_STRIDE;
        return Math.max(0, total - LE_H);
    }

    private void clampScroll() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
    }

    private void updateScrollFromMouse(float texY) {
        float trackTop = LBG_Y + SB_Y;
        float trackBot = trackTop + SB_H - SBT_H;
        float pct = (texY - trackTop) / (trackBot - trackTop);
        pct = Math.max(0, Math.min(1, pct));
        scrollOffset = pct * maxScroll();
    }

    /** Look up a player's status (0=online, 1=afk, 2=offline) from the full player list. */
    private byte getPlayerStatus(UUID uuid) {
        for (PlaytimeDataS2CPacket.PlayerListEntry pe : allPlayers) {
            if (pe.uuid.equals(uuid)) {
                return pe.status;
            }
        }
        return 2; // default to offline if not found
    }

    private ItemStack getStack(String id) {
        try {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            if (item != null && item != Items.AIR) return new ItemStack(item);
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }

    // ── Playtime Text ───────────────────────────────────────────────────────────

    private void renderPlaytimeText(GuiGraphics g, int x, int y) {
        int h = 8;
        long e = elapsed();

        MutableComponent l1 = Component.literal("§f" + playerName + " ");
        l1.append(isAfk ? Component.literal("§c[AFK]") : Component.literal("§a[Active]"));
        g.drawString(font, l1, x, y, 0xFFFFFF, false);

        g.drawString(font, "§7Playtime: §f" + TimeParser.formatTicks(totalTicks + (isAfk ? 0 : e)), x, y + h, 0xFFFFFF, false);

        MutableComponent l3 = Component.literal("§7Rank: ");
        l3.append(ColorUtil.rankDisplay(currentRankColor, currentRankName));
        g.drawString(font, l3, x, y + h * 2, 0xFFFFFF, false);

        if (isMaxRank) {
            g.drawString(font, "§a\u2713 Max rank!", x, y + h * 3, 0xFFFFFF, false);
        } else {
            long rem = Math.max(0, ticksToNextRank - (isAfk ? 0 : e));
            MutableComponent l4 = Component.literal("§7Next: ");
            l4.append(ColorUtil.rankDisplay(nextRankColor, nextRankName));
            l4.append(Component.literal(" §7(" + TimeParser.formatTicks(rem) + ")"));
            g.drawString(font, l4, x, y + h * 3, 0xFFFFFF, false);
        }
    }

    // ── Top 3 ───────────────────────────────────────────────────────────────────

    private void renderTop3(GuiGraphics g, int x1, int y1, int x2, int y2) {
        if (top3Count == 0) return;
        int cw = (x2 - x1) / 3;
        long e = elapsed();
        int[] ord = {1, 0, 2};
        int[] yOff = {12, 0, 12};

        for (int c = 0; c < 3; c++) {
            int i = ord[c];
            if (i >= top3Count) continue;
            int cx = x1 + c * cw + cw / 2;
            int cy = y1 + yOff[c];
            // Counter freezes when player is offline or AFK (top3IsAfk is true for both)
            long live = top3Ticks[i] + (top3IsAfk[i] ? 0 : e);

            int nw = font.width(top3Names[i]);
            g.drawString(font, "§f" + top3Names[i], cx - nw / 2, cy, 0xFFFFFF, false);

            int skinX = cx - 16, skinY = cy + 10;
            renderPaperdoll(g, getSkin(top3Uuids[i]), skinX, skinY, 2);

            MutableComponent rc = ColorUtil.rankDisplay(top3RankColors[i], top3RankNames[i]);
            g.drawString(font, rc, cx - font.width(rc) / 2, skinY + 64 + 2, 0xFFFFFF, false);

            String hs = TimeParser.formatTicks(live);
            g.drawString(font, "§7" + hs, cx - font.width(hs) / 2, skinY + 64 + 12, 0xFFFFFF, false);

            // Status indicator: Online(Green) / AFK(Yellow) / Offline(Red)
            byte status = getPlayerStatus(top3Uuids[i]);
            String statusLabel, statusColor;
            if (status == 0) { statusLabel = "[Online]"; statusColor = "§a"; }
            else if (status == 1) { statusLabel = "[AFK]"; statusColor = "§e"; }
            else { statusLabel = "[Offline]"; statusColor = "§c"; }
            g.drawString(font, statusColor + statusLabel, cx - font.width(statusLabel) / 2, skinY + 64 + 22, 0xFFFFFF, false);
        }
    }

    // ── Ranks Panel ─────────────────────────────────────────────────────────────

    private void renderRanksPanel(GuiGraphics g) {
        if (allRanks.isEmpty()) return;
        int aW = RK_X2 - RK_X1;
        int tgw = rankCols * SLOT_W;
        int offX = (aW - tgw) / 2;
        int si = rankPage * ranksPerPage;
        int ei = Math.min(si + ranksPerPage, allRanks.size());

        for (int i = si; i < ei; i++) {
            int s = i - si;
            int col = s % rankCols, row = s / rankCols;
            PlaytimeDataS2CPacket.RankEntry r = allRanks.get(i);
            int sx = RK_X1 + offX + col * SLOT_W + (SLOT_W - RBOX) / 2;
            int sy = RK_Y1 + row * SLOT_H;
            int bcx = sx + RBOX / 2;

            MutableComponent nm = ColorUtil.rankDisplay(r.color, r.displayName);
            g.drawString(font, nm, bcx - font.width(nm) / 2, sy, 0xFFFFFF, false);

            int by = sy + 9;
            g.blit(BOX_TEX, sx, by, RBOX, RBOX, 0, 0, BOX_N, BOX_N, BOX_N, BOX_N);

            if (!r.defaultItem.isEmpty()) {
                ItemStack st = getStack(r.defaultItem);
                if (!st.isEmpty()) g.renderItem(st, sx + (RBOX - 16) / 2, by + (RBOX - 16) / 2);
            }

            String hrs = (r.thresholdTicks / 72_000L) + "h";
            g.drawString(font, "§7" + hrs, bcx - font.width(hrs) / 2, by + RBOX + 1, 0xFFFFFF, false);
        }

        if (totalRankPages > 1) {
            int ay = RK_Y2 - ARROW_AREA + 2;
            if (rankPage > 0) g.blit(RED_ARROW, RK_X1, ay, ARROW_W, ARROW_H, 0, 0, ARROW_W, ARROW_H, ARROW_W, ARROW_H);
            if (rankPage < totalRankPages - 1) g.blit(GREEN_ARROW, RK_X1 + ARROW_W + 4, ay, ARROW_W, ARROW_H, 0, 0, ARROW_W, ARROW_H, ARROW_W, ARROW_H);
            String ps = "Page " + (rankPage + 1) + "/" + totalRankPages;
            g.drawString(font, "§7" + ps, RK_X1 + (RK_X2 - RK_X1) / 2 - font.width(ps) / 2, ay + (ARROW_H - 8) / 2, 0xFFFFFF, false);
        }
    }

    // ── List View ───────────────────────────────────────────────────────────────

    private void renderListView(GuiGraphics g) {
        // Draw list background over left panel
        g.blit(LIST_BG_TEX, LBG_X, LBG_Y, LBG_W, LBG_H, 0, 0, LBG_W, LBG_H, LBG_W, LBG_H);

        // Search field
        int sfx = LBG_X + SEARCH_X, sfy = LBG_Y + SEARCH_Y;
        g.fill(sfx, sfy, sfx + SEARCH_W, sfy + SEARCH_H, searchFocused ? 0xFF555555 : 0xFF3A3A3A);
        g.fill(sfx + 1, sfy + 1, sfx + SEARCH_W - 1, sfy + SEARCH_H - 1, 0xFF1A1A1A);
        String displayText = searchText.isEmpty() && !searchFocused ? "Search..." : searchText + (searchFocused ? "_" : "");
        g.drawString(font, "§7" + displayText, sfx + 3, sfy + 3, 0xFFFFFF, false);

        // Scissor for entries
        int absX1 = (int)(guiLeft + (LBG_X + LE_X) * guiScale);
        int absY1 = (int)(guiTop + (LBG_Y + LE_Y) * guiScale);
        int absX2 = (int)(guiLeft + (LBG_X + LE_X + LE_W) * guiScale);
        int absY2 = (int)(guiTop + (LBG_Y + LE_Y + LE_H) * guiScale);
        g.enableScissor(absX1, absY1, absX2, absY2);

        long e = elapsed();
        int baseX = LBG_X + LE_X;
        int baseY = LBG_Y + LE_Y;

        for (int i = 0; i < filteredPlayers.size(); i++) {
            int entryY = baseY + i * ENT_STRIDE - (int) scrollOffset;
            if (entryY + ENT_H < baseY - ENT_STRIDE || entryY > baseY + LE_H + ENT_STRIDE) continue;

            PlaytimeDataS2CPacket.PlayerListEntry p = filteredPlayers.get(i);

            // Entry background
            g.blit(ENTRY_TEX, baseX, entryY, ENT_W, ENT_H, 0, 0, ENT_W, ENT_H, ENT_W, ENT_H);

            // Player head (3,3)-(19,19) within entry → 16x16
            renderHead(g, getSkin(p.uuid), baseX + 3, entryY + 3, 16);

            // Info text (23,3)-(170,19) within entry — centered vertically (16px area, 8px text)
            int infoX = baseX + 23;
            int infoY = entryY + 3 + (16 - 8) / 2; // y3 + centered offset = y7

            // Build info line: {Rank} {Name} then right-align hours+status
            MutableComponent rankC = ColorUtil.rankDisplay(p.rankColor, p.rankName);
            g.drawString(font, rankC, infoX, infoY, 0xFFFFFF, false);

            int rankTextW = font.width(rankC);
            g.drawString(font, "§f " + p.name, infoX + rankTextW, infoY, 0xFFFFFF, false);

            // Hours (live if online)
            long liveTicks = p.totalTicks + (p.status == 0 ? e : 0);
            String hrs = TimeParser.formatTicks(liveTicks);

            // Status dot
            String statusDot;
            if (p.status == 0) statusDot = "§a●";
            else if (p.status == 1) statusDot = "§e●";
            else statusDot = "§c●";

            String rightText = hrs + " " + statusDot;
            // Can't easily calculate width of §-coded string, so just draw separately
            int rightEdge = baseX + 170;
            int hrsW = font.width(hrs);
            int dotW = font.width("●");
            g.drawString(font, "§f" + hrs, rightEdge - hrsW - dotW - 4, infoY, 0xFFFFFF, false);
            g.drawString(font, statusDot, rightEdge - dotW, infoY, 0xFFFFFF, false);
        }

        g.disableScissor();

        // Scrollbar
        if (maxScroll() > 0) {
            int trackX = LBG_X + SB_X, trackY = LBG_Y + SB_Y;
            float pct = scrollOffset / maxScroll();
            int thumbY = trackY + (int)(pct * (SB_H - SBT_H));
            g.blit(SCROLLBAR, trackX, thumbY, SBT_W, SBT_H, 0, 0, SBT_W, SBT_H, SBT_W, SBT_H);
        }
    }

    // ── Paperdoll ───────────────────────────────────────────────────────────────

    private void renderPaperdoll(GuiGraphics g, ResourceLocation skin, int x, int y, int s) {
        bs(g, skin, x+4*s, y, 8*s, 8*s, 8, 8, 8, 8);
        bs(g, skin, x+4*s, y, 8*s, 8*s, 40, 8, 8, 8);
        bs(g, skin, x+4*s, y+8*s, 8*s, 12*s, 20, 20, 8, 12);
        bs(g, skin, x, y+8*s, 4*s, 12*s, 44, 20, 4, 12);
        bs(g, skin, x+12*s, y+8*s, 4*s, 12*s, 36, 52, 4, 12);
        bs(g, skin, x+4*s, y+20*s, 4*s, 12*s, 4, 20, 4, 12);
        bs(g, skin, x+8*s, y+20*s, 4*s, 12*s, 20, 52, 4, 12);
    }

    private void bs(GuiGraphics g, ResourceLocation sk, int dx, int dy, int dw, int dh, int su, int sv, int sw, int sh) {
        g.blit(sk, dx, dy, dw, dh, (float)su, (float)sv, sw, sh, 64, 64);
    }
}
