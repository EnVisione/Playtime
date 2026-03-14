package com.enviouse.playtime.client;

import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.network.ClaimRankC2SPacket;
import com.enviouse.playtime.network.PlaytimeDataS2CPacket;
import com.enviouse.playtime.network.PlaytimeNetwork;
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

import java.util.*;

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

    // Rank grid layout — 5 columns max, wider slots to prevent text overlap
    private static final int RK_X1 = 219, RK_Y1 = 49, RK_X2 = 499, RK_Y2 = 197;
    private static final int SLOT_H = 42, SLOT_W = 56, ARROW_AREA = 22;
    private static final int RANK_MAX_COLS = 5;

    // Toggle arrow area (main background coords)
    private static final int TGL_X1 = 8, TGL_Y1 = 209, TGL_X2 = 45, TGL_Y2 = 247;

    // List background position & native size — shifted 2px right
    private static final int LBG_X = 6, LBG_Y = 4, LBG_W = 201, LBG_H = 201;
    // Entry area inside listbackground
    private static final int LE_X = 5, LE_Y = 89, LE_W = 175, LE_H = 105;
    // PlayerList.png native size
    private static final int ENT_W = 174, ENT_H = 23, ENT_GAP = 2, ENT_STRIDE = 25;
    // Scrollbar track inside listbackground
    private static final int SB_X = 193, SB_Y = 87, SB_W = 4, SB_H = 110;
    private static final int SBT_W = 4, SBT_H = 5; // thumb size
    // Search field inside listbackground — top-left at x1, y66
    private static final int SEARCH_X = 1, SEARCH_Y = 66, SEARCH_W = 189, SEARCH_H = 14;

    // ── Data ────────────────────────────────────────────────────────────────────
    private final String playerName;
    private final UUID playerUuid;
    private final long totalTicks;
    private final String currentRankName, currentRankColor;
    private final String nextRankName, nextRankColor;
    private final long ticksToNextRank;
    private final boolean isAfk, isMaxRank;
    private final boolean claimsEnabled, forceloadsEnabled;

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

    // Rank detail popup (-1 = none)
    private int detailRankIndex = -1;

    // Local claimed ranks (for immediate visual feedback before server confirms)
    private final Set<String> localClaimed = new HashSet<>();

    // Hovered rank index for tooltip
    private int hoveredRankIndex = -1;

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
        this.claimsEnabled = p.isClaimsEnabled();
        this.forceloadsEnabled = p.isForceloadsEnabled();
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
        rankCols = Math.min(RANK_MAX_COLS, Math.max(1, aW / SLOT_W));
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

        // Mouse in texture space
        float tmx = (float)(mx - guiLeft) / guiScale;
        float tmy = (float)(my - guiTop) / guiScale;

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

        // Ranks panel (or detail popup)
        hoveredRankIndex = -1;
        if (detailRankIndex >= 0 && detailRankIndex < allRanks.size()) {
            renderRankDetailPopup(g, allRanks.get(detailRankIndex));
        } else {
            renderRanksPanel(g, tmx, tmy);
        }

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

        // Rank tooltip (rendered in screen space, after popPose)
        if (hoveredRankIndex >= 0 && hoveredRankIndex < allRanks.size() && detailRankIndex < 0) {
            renderRankTooltip(g, allRanks.get(hoveredRankIndex), mx, my);
        }

        super.render(g, mx, my, pt);
    }

    // ── Input ───────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        float tx = (float)(mx - guiLeft) / guiScale;
        float ty = (float)(my - guiTop) / guiScale;

        // Left click
        if (btn == 0) {
            // Detail popup X button or click-to-close
            if (detailRankIndex >= 0) {
                int xBtnX = RK_X2 - 14, xBtnY = RK_Y1 + 4;
                if (tx >= xBtnX && tx <= xBtnX + 10 && ty >= xBtnY && ty <= xBtnY + 10) {
                    detailRankIndex = -1;
                    return true;
                }
                // Click anywhere else in the panel area closes the detail
                if (tx >= RK_X1 && tx <= RK_X2 && ty >= RK_Y1 && ty <= RK_Y2) {
                    detailRankIndex = -1;
                    return true;
                }
            }

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
                // Rank left-click — claim if earned and not yet claimed
                if (detailRankIndex < 0) {
                    int clickedRank = getRankSlotAt(tx, ty);
                    if (clickedRank >= 0) {
                        PlaytimeDataS2CPacket.RankEntry r = allRanks.get(clickedRank);
                        if (r.earned && !isRankClaimed(r)) {
                            localClaimed.add(r.id);
                            PlaytimeNetwork.CHANNEL.sendToServer(new ClaimRankC2SPacket(r.id));
                            return true;
                        }
                    }
                }
                // Rank pagination arrows
                if (totalRankPages > 1) {
                    int aY = RK_Y2 - ARROW_AREA + 2;
                    if (rankPage > 0 && tx >= RK_X1 && tx <= RK_X1 + ARROW_W && ty >= aY && ty <= aY + ARROW_H) { rankPage--; return true; }
                    if (rankPage < totalRankPages - 1 && tx >= RK_X1 + ARROW_W + 4 && tx <= RK_X1 + ARROW_W * 2 + 4 && ty >= aY && ty <= aY + ARROW_H) { rankPage++; return true; }
                }
            }
        }

        // Right click — rank detail popup
        if (btn == 1) {
            if (detailRankIndex < 0 && !listMode) {
                int clickedRank = getRankSlotAt(tx, ty);
                if (clickedRank >= 0) {
                    detailRankIndex = clickedRank;
                    return true;
                }
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
        // Escape closes detail popup
        if (key == 256 && detailRankIndex >= 0) {
            detailRankIndex = -1;
            return true;
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
        return 2;
    }

    /** Check if a rank is considered claimed (from server data or local click). */
    private boolean isRankClaimed(PlaytimeDataS2CPacket.RankEntry r) {
        return r.claimed || localClaimed.contains(r.id);
    }

    private ItemStack getStack(String id) {
        try {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            if (item != null && item != Items.AIR) return new ItemStack(item);
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }

    /** Get the absolute rank index for a rank slot at texture coordinates, or -1. */
    private int getRankSlotAt(float tx, float ty) {
        if (tx < RK_X1 || tx > RK_X2 || ty < RK_Y1 || ty > RK_Y2 - ARROW_AREA) return -1;
        int aW = RK_X2 - RK_X1;
        int tgw = rankCols * SLOT_W;
        int offX = (aW - tgw) / 2;
        int si = rankPage * ranksPerPage;

        for (int i = si; i < Math.min(si + ranksPerPage, allRanks.size()); i++) {
            int s = i - si;
            int col = s % rankCols, row = s / rankCols;
            int slotX = RK_X1 + offX + col * SLOT_W;
            int slotY = RK_Y1 + row * SLOT_H;
            if (tx >= slotX && tx <= slotX + SLOT_W && ty >= slotY && ty <= slotY + SLOT_H) {
                return i;
            }
        }
        return -1;
    }

    // ── Playtime Text ───────────────────────────────────────────────────────────

    private void renderPlaytimeText(GuiGraphics g, int x, int y) {
        int h = 8;
        long e = elapsed();

        MutableComponent l1 = Component.literal("\u00A7f" + playerName + " ");
        l1.append(isAfk ? Component.literal("\u00A7c[AFK]") : Component.literal("\u00A7a[Active]"));
        g.drawString(font, l1, x, y, 0xFFFFFF, false);

        g.drawString(font, "\u00A77Playtime: \u00A7f" + TimeParser.formatTicks(totalTicks + (isAfk ? 0 : e)), x, y + h, 0xFFFFFF, false);

        MutableComponent l3 = Component.literal("\u00A77Rank: ");
        l3.append(ColorUtil.rankDisplay(currentRankColor, currentRankName));
        g.drawString(font, l3, x, y + h * 2, 0xFFFFFF, false);

        if (isMaxRank) {
            g.drawString(font, "\u00A7a\u2713 Max rank!", x, y + h * 3, 0xFFFFFF, false);
        } else {
            long rem = Math.max(0, ticksToNextRank - (isAfk ? 0 : e));
            MutableComponent l4 = Component.literal("\u00A77Next: ");
            l4.append(ColorUtil.rankDisplay(nextRankColor, nextRankName));
            l4.append(Component.literal(" \u00A77(" + TimeParser.formatTicks(rem) + ")"));
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
            long live = top3Ticks[i] + (top3IsAfk[i] ? 0 : e);

            int nw = font.width(top3Names[i]);
            g.drawString(font, "\u00A7f" + top3Names[i], cx - nw / 2, cy, 0xFFFFFF, false);

            int skinX = cx - 16, skinY = cy + 10;
            renderPaperdoll(g, getSkin(top3Uuids[i]), skinX, skinY, 2);

            MutableComponent rc = ColorUtil.rankDisplay(top3RankColors[i], top3RankNames[i]);
            g.drawString(font, rc, cx - font.width(rc) / 2, skinY + 64 + 2, 0xFFFFFF, false);

            String hs = TimeParser.formatTicks(live);
            g.drawString(font, "\u00A77" + hs, cx - font.width(hs) / 2, skinY + 64 + 12, 0xFFFFFF, false);

            byte status = getPlayerStatus(top3Uuids[i]);
            String statusLabel, statusColor;
            if (status == 0) { statusLabel = "[Online]"; statusColor = "\u00A7a"; }
            else if (status == 1) { statusLabel = "[AFK]"; statusColor = "\u00A7e"; }
            else { statusLabel = "[Offline]"; statusColor = "\u00A7c"; }
            g.drawString(font, statusColor + statusLabel, cx - font.width(statusLabel) / 2, skinY + 64 + 22, 0xFFFFFF, false);
        }
    }

    // ── Ranks Panel ─────────────────────────────────────────────────────────────

    private void renderRanksPanel(GuiGraphics g, float tmx, float tmy) {
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

            boolean claimed = isRankClaimed(r);

            // Rank name — truncate if too wide for slot to prevent overlap
            MutableComponent nm = ColorUtil.rankDisplay(r.color, r.displayName);
            int maxTextW = SLOT_W - 2;
            String nameStr = r.displayName;
            while (font.width(nm) > maxTextW && nameStr.length() > 1) {
                nameStr = nameStr.substring(0, nameStr.length() - 1);
                nm = ColorUtil.rankDisplay(r.color, nameStr + "..");
            }
            g.drawString(font, nm, bcx - font.width(nm) / 2, sy, 0xFFFFFF, false);

            int by = sy + 9;
            // Draw connector box
            g.blit(BOX_TEX, sx, by, RBOX, RBOX, 0, 0, BOX_N, BOX_N, BOX_N, BOX_N);

            // Render item icon
            if (!r.defaultItem.isEmpty()) {
                ItemStack st = getStack(r.defaultItem);
                if (!st.isEmpty()) g.renderItem(st, sx + (RBOX - 16) / 2, by + (RBOX - 16) / 2);
            }

            // Green hue overlay + checkmark if claimed
            if (claimed) {
                g.fill(sx, by, sx + RBOX, by + RBOX, 0x4400CC00);
                g.drawString(font, "\u00A7a\u2713", sx + RBOX - 7, by + 1, 0xFFFFFF, false);
            }

            // Hours text — green if claimed, white if not
            String hrs = (r.thresholdTicks / 72_000L) + "h";
            String hrsColor = claimed ? "\u00A7a" : "\u00A7f";
            g.drawString(font, hrsColor + hrs, bcx - font.width(hrs) / 2, by + RBOX + 1, 0xFFFFFF, false);

            // Hover detection for tooltip
            int slotX = RK_X1 + offX + col * SLOT_W;
            int slotY = RK_Y1 + row * SLOT_H;
            if (tmx >= slotX && tmx <= slotX + SLOT_W && tmy >= slotY && tmy <= slotY + SLOT_H) {
                hoveredRankIndex = i;
            }
        }

        // Pagination arrows
        if (totalRankPages > 1) {
            int ay = RK_Y2 - ARROW_AREA + 2;
            if (rankPage > 0) g.blit(RED_ARROW, RK_X1, ay, ARROW_W, ARROW_H, 0, 0, ARROW_W, ARROW_H, ARROW_W, ARROW_H);
            if (rankPage < totalRankPages - 1) g.blit(GREEN_ARROW, RK_X1 + ARROW_W + 4, ay, ARROW_W, ARROW_H, 0, 0, ARROW_W, ARROW_H, ARROW_W, ARROW_H);
            String ps = "Page " + (rankPage + 1) + "/" + totalRankPages;
            g.drawString(font, "\u00A77" + ps, RK_X1 + (RK_X2 - RK_X1) / 2 - font.width(ps) / 2, ay + (ARROW_H - 8) / 2, 0xFFFFFF, false);
        }
    }

    // ── Rank Tooltip ────────────────────────────────────────────────────────────

    private void renderRankTooltip(GuiGraphics g, PlaytimeDataS2CPacket.RankEntry r, int mx, int my) {
        List<Component> lines = new ArrayList<>();
        lines.add(ColorUtil.rankDisplay(r.color, r.displayName));
        lines.add(Component.literal(""));
        lines.add(Component.literal("\u00A77Required: \u00A7f" + (r.thresholdTicks / 72_000L) + "h"));
        if (claimsEnabled) {
            lines.add(Component.literal("\u00A77Claims: \u00A7f" + r.claims));
        }
        if (forceloadsEnabled) {
            lines.add(Component.literal("\u00A77Forceloads: \u00A7f" + r.forceloads));
        }
        if (r.inactivityDays > 0) {
            lines.add(Component.literal("\u00A77Inactivity: \u00A7f" + r.inactivityDays + " days"));
        } else {
            lines.add(Component.literal("\u00A77Inactivity: \u00A7fNever expires"));
        }
        lines.add(Component.literal(""));
        if (isRankClaimed(r)) {
            lines.add(Component.literal("\u00A7a\u2713 Claimed"));
        } else if (r.earned) {
            lines.add(Component.literal("\u00A7e\u2B06 Click to claim!"));
        } else {
            lines.add(Component.literal("\u00A7c\u2717 Not yet earned"));
        }
        lines.add(Component.literal("\u00A78Right-click for details"));
        g.renderTooltip(font, lines, Optional.empty(), mx, my);
    }

    // ── Rank Detail Popup ───────────────────────────────────────────────────────

    private void renderRankDetailPopup(GuiGraphics g, PlaytimeDataS2CPacket.RankEntry r) {
        int px = RK_X1, py = RK_Y1, pw = RK_X2 - RK_X1, ph = RK_Y2 - RK_Y1;

        // Dark background
        g.fill(px, py, px + pw, py + ph, 0xDD1A1A2E);
        // Border
        g.fill(px, py, px + pw, py + 1, 0xFF555577);
        g.fill(px, py + ph - 1, px + pw, py + ph, 0xFF555577);
        g.fill(px, py, px + 1, py + ph, 0xFF555577);
        g.fill(px + pw - 1, py, px + pw, py + ph, 0xFF555577);

        int cx = px + pw / 2;
        int cy = py + 8;

        // X button (top-right)
        g.drawString(font, "\u00A7c\u2715", px + pw - 14, py + 4, 0xFFFFFF, false);

        // Rank title
        MutableComponent title = ColorUtil.rankDisplay(r.color, r.displayName);
        g.drawString(font, title, cx - font.width(title) / 2, cy, 0xFFFFFF, false);
        cy += 14;

        // Separator
        g.fill(px + 10, cy, px + pw - 10, cy + 1, 0xFF555577);
        cy += 6;

        // Item icon (centered)
        if (!r.defaultItem.isEmpty()) {
            ItemStack st = getStack(r.defaultItem);
            if (!st.isEmpty()) {
                g.renderItem(st, cx - 8, cy);
                if (isRankClaimed(r)) {
                    g.fill(cx - 8, cy, cx + 8, cy + 16, 0x4400CC00);
                }
            }
        }
        cy += 22;

        // Details
        g.drawString(font, "\u00A77Required Playtime:", px + 10, cy, 0xFFFFFF, false);
        cy += 10;
        String hrs = (r.thresholdTicks / 72_000L) + " hours";
        g.drawString(font, (isRankClaimed(r) ? "\u00A7a" : "\u00A7f") + hrs, px + 16, cy, 0xFFFFFF, false);
        cy += 14;

        if (claimsEnabled) {
            g.drawString(font, "\u00A77Claim Chunks: \u00A7f" + r.claims, px + 10, cy, 0xFFFFFF, false);
            cy += 10;
        }
        if (forceloadsEnabled) {
            g.drawString(font, "\u00A77Forceloads: \u00A7f" + r.forceloads, px + 10, cy, 0xFFFFFF, false);
            cy += 10;
        }
        if (r.inactivityDays > 0) {
            g.drawString(font, "\u00A77Inactivity: \u00A7f" + r.inactivityDays + " days", px + 10, cy, 0xFFFFFF, false);
        } else {
            g.drawString(font, "\u00A77Inactivity: \u00A7fNever expires", px + 10, cy, 0xFFFFFF, false);
        }
        cy += 14;

        // Status
        if (isRankClaimed(r)) {
            g.drawString(font, "\u00A7a\u2713 Rank Claimed", px + 10, cy, 0xFFFFFF, false);
        } else if (r.earned) {
            g.drawString(font, "\u00A7e\u2B06 Eligible \u2014 left-click to claim", px + 10, cy, 0xFFFFFF, false);
        } else {
            long remaining = r.thresholdTicks - totalTicks;
            g.drawString(font, "\u00A7c\u2717 " + TimeParser.formatTicks(Math.max(0, remaining)) + " remaining", px + 10, cy, 0xFFFFFF, false);
        }
    }

    // ── List View ───────────────────────────────────────────────────────────────

    private void renderListView(GuiGraphics g) {
        // Draw list background over left panel
        g.blit(LIST_BG_TEX, LBG_X, LBG_Y, LBG_W, LBG_H, 0, 0, LBG_W, LBG_H, LBG_W, LBG_H);

        // Search field at (x1, y66) inside listbackground
        int sfx = LBG_X + SEARCH_X, sfy = LBG_Y + SEARCH_Y;
        g.fill(sfx, sfy, sfx + SEARCH_W, sfy + SEARCH_H, searchFocused ? 0xFF555555 : 0xFF3A3A3A);
        g.fill(sfx + 1, sfy + 1, sfx + SEARCH_W - 1, sfy + SEARCH_H - 1, 0xFF1A1A1A);
        String displayText = searchText.isEmpty() && !searchFocused ? "Search..." : searchText + (searchFocused ? "_" : "");
        g.drawString(font, "\u00A77" + displayText, sfx + 3, sfy + 3, 0xFFFFFF, false);

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

            // Player head (3,3)-(19,19) within entry
            renderHead(g, getSkin(p.uuid), baseX + 3, entryY + 3, 16);

            // Info text (23,3)-(170,19) — vertically centered
            int infoX = baseX + 23;
            int infoY = entryY + 3 + (16 - 8) / 2;

            MutableComponent rankC = ColorUtil.rankDisplay(p.rankColor, p.rankName);
            g.drawString(font, rankC, infoX, infoY, 0xFFFFFF, false);

            int rankTextW = font.width(rankC);
            g.drawString(font, "\u00A7f " + p.name, infoX + rankTextW, infoY, 0xFFFFFF, false);

            // Hours (live if online, frozen for AFK/offline)
            long liveTicks = p.totalTicks + (p.status == 0 ? e : 0);
            String hrs2 = TimeParser.formatTicks(liveTicks);

            // Status dot
            String statusDot;
            if (p.status == 0) statusDot = "\u00A7a\u25CF";
            else if (p.status == 1) statusDot = "\u00A7e\u25CF";
            else statusDot = "\u00A7c\u25CF";

            int rightEdge = baseX + 170;
            int hrsW = font.width(hrs2);
            int dotW = font.width("\u25CF");
            g.drawString(font, "\u00A7f" + hrs2, rightEdge - hrsW - dotW - 4, infoY, 0xFFFFFF, false);
            g.drawString(font, statusDot, rightEdge - dotW, infoY, 0xFFFFFF, false);
        }

        g.disableScissor();

        // Scrollbar — rendered on top of listbackground after scissor is disabled
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
