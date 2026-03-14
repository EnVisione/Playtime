package com.enviouse.playtime.client;

import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.network.AdminModifyTimeC2SPacket;
import com.enviouse.playtime.network.AdminSetRankC2SPacket;
import com.enviouse.playtime.network.ClaimRankC2SPacket;
import com.enviouse.playtime.network.PlaytimeDataS2CPacket;
import com.enviouse.playtime.network.PlaytimeNetwork;
import com.enviouse.playtime.util.ColorUtil;
import com.enviouse.playtime.util.TimeParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
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

import java.text.SimpleDateFormat;
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
    // Rank pagination arrows scaled to 70% (30% smaller)
    private static final int RK_ARR_W = (int)(ARROW_W * 0.70f), RK_ARR_H = (int)(ARROW_H * 0.7f);
    // Toggle arrow scaled up by 25% (= 125%)
    private static final int TGL_ARR_W = (int)(ARROW_W * 1.25f), TGL_ARR_H = (int)(ARROW_H * 1.25f);

    // Rank grid layout — 5 columns, 3 rows
    private static final int RK_X1 = 223, RK_Y1 = 51, RK_X2 = 492, RK_Y2 = 172;
    private static final int SLOT_H = 40, SLOT_W = 54, ARROW_AREA = 0;
    private static final int RANK_MAX_COLS = 5;
    private static final int RANK_MAX_ROWS = 3;

    // Rank pagination arrow positions (fixed)
    private static final int PG_ARR_X = 236, PG_ARR_Y = 189;
    private static final int PG_PAGE_X = 362, PG_PAGE_Y = 189;

    // Toggle arrow area (main background coords)
    private static final int TGL_X1 = 8, TGL_Y1 = 209, TGL_X2 = 45, TGL_Y2 = 247;

    // List background position & native size — shifted right
    private static final int LBG_X = 7, LBG_Y = 4, LBG_W = 201, LBG_H = 201;
    // Entry area inside listbackground
    private static final int LE_X = 5, LE_Y = 89, LE_W = 175, LE_H = 105;
    // PlayerList.png native size
    private static final int ENT_W = 174, ENT_H = 23, ENT_GAP = 2, ENT_STRIDE = 25;
    // Scrollbar track inside listbackground
    private static final int SB_X = 193, SB_Y = 87, SB_W = 4, SB_H = 110;
    private static final int SBT_W = 4, SBT_H = 5; // thumb size
    // Search field inside listbackground — top-left at x1, y66, extended 2px down + 10px right
    private static final int SEARCH_X = 1, SEARCH_Y = 66, SEARCH_W = 199, SEARCH_H = 16;

    // ── Data ────────────────────────────────────────────────────────────────────
    private final String playerName;
    private final UUID playerUuid;
    private final long totalTicks;
    private final String currentRankName, currentRankColor;
    private final String nextRankName, nextRankColor;
    private final long ticksToNextRank;
    private final boolean isAfk, isMaxRank;
    private final boolean claimsEnabled, forceloadsEnabled;
    private final boolean isOperator;

    private final int top3Count;
    private final String[] top3Names;
    private final UUID[] top3Uuids;
    private final long[] top3Ticks;
    private final String[] top3RankNames, top3RankColors;
    private final boolean[] top3IsAfk;

    private final List<PlaytimeDataS2CPacket.RankEntry> allRanks;
    private final List<PlaytimeDataS2CPacket.PlayerListEntry> allPlayers;

    // ── State ───────────────────────────────────────────────────────────────────
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

    // Player detail popup (-1 = none)
    private int detailPlayerIndex = -1;
    private float adminRankScroll = 0; // scroll offset for rank list in admin panel

        // Admin time input field (replaces 4 buttons)
    private String timeInputText = "";
    private boolean timeInputFocused = false;
    private long timeInputErrorMs = 0; // timestamp for red flash on parse error

    // UUID copy feedback
    private long uuidCopiedMs = 0; // timestamp when UUID was copied

    // Local claimed ranks (for immediate visual feedback before server confirms)
    private final Set<String> localClaimed = new HashSet<>();

    // Hovered rank index for tooltip
    private int hoveredRankIndex = -1;

    // Hover animation state
    private int lastHoveredElement = -1;   // unique ID of hovered element (-1 = none)
    private long hoverStartMs = 0;         // when the current hover started
    private static final int HOVER_ID_TOGGLE = -100;
    private static final int HOVER_ID_PAGE_PREV = -101;
    private static final int HOVER_ID_PAGE_NEXT = -102;
    // Rank slots use their absolute index (0..n) as hover ID

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
        this.isOperator = p.isOperator();
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
        rankCols = RANK_MAX_COLS;
        rankRows = RANK_MAX_ROWS;
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

        // Right panel: player detail, rank detail, or ranks grid
        hoveredRankIndex = -1;
        if (detailPlayerIndex >= 0 && detailPlayerIndex < filteredPlayers.size()) {
            renderPlayerDetailPopup(g, filteredPlayers.get(detailPlayerIndex), tmx, tmy);
        } else if (detailRankIndex >= 0 && detailRankIndex < allRanks.size()) {
            renderRankDetailPopup(g, allRanks.get(detailRankIndex));
        } else {
            renderRanksPanel(g, tmx, tmy);
        }

        // Toggle arrow in (8,209)-(45,247) — scaled up by 25%, with hover animation
        boolean tglHover = tmx >= TGL_X1 && tmx <= TGL_X2 && tmy >= TGL_Y1 && tmy <= TGL_Y2;
        int arrowX = TGL_X1 + (TGL_X2 - TGL_X1 - TGL_ARR_W) / 2;
        int arrowY = TGL_Y1 + (TGL_Y2 - TGL_Y1 - TGL_ARR_H) / 2;
        ResourceLocation tglTex = listMode ? RED_ARROW : GREEN_ARROW;
        renderHoveredBlit(g, tglTex, arrowX, arrowY, TGL_ARR_W, TGL_ARR_H, ARROW_W, ARROW_H, tglHover, HOVER_ID_TOGGLE);

        // Rank pagination arrows — only when no detail popup is open
        if (totalRankPages > 1 && detailRankIndex < 0 && detailPlayerIndex < 0) {
            int pgArrX = PG_ARR_X;
            int pgArrY = PG_ARR_Y;
            if (rankPage > 0) {
                boolean prevHover = tmx >= pgArrX && tmx <= pgArrX + RK_ARR_W && tmy >= pgArrY && tmy <= pgArrY + RK_ARR_H;
                renderHoveredBlit(g, RED_ARROW, pgArrX, pgArrY, RK_ARR_W, RK_ARR_H, ARROW_W, ARROW_H, prevHover, HOVER_ID_PAGE_PREV);
            }
            int pgNxtX = pgArrX + RK_ARR_W + 4;
            if (rankPage < totalRankPages - 1) {
                boolean nextHover = tmx >= pgNxtX && tmx <= pgNxtX + RK_ARR_W && tmy >= pgArrY && tmy <= pgArrY + RK_ARR_H;
                renderHoveredBlit(g, GREEN_ARROW, pgNxtX, pgArrY, RK_ARR_W, RK_ARR_H, ARROW_W, ARROW_H, nextHover, HOVER_ID_PAGE_NEXT);
            }
            String ps = (rankPage + 1) + "/" + totalRankPages;
            g.drawString(font, "\u00A77" + ps, PG_PAGE_X, PG_PAGE_Y + (RK_ARR_H - 8) / 2, 0xFFFFFF, false);
        }

        // Left panel: TOP 3 or LIST
        if (listMode) {
            renderListView(g);
        } else {
            renderTop3(g, 14, 75, 202, 197);
        }

        // Reset hover if nothing was hovered this frame
        boolean anyHovered = hoveredRankIndex >= 0 || tglHover;
        // Pagination arrows hover check
        if (totalRankPages > 1 && detailRankIndex < 0 && detailPlayerIndex < 0) {
            int pgArrX = PG_ARR_X;
            int pgArrY = PG_ARR_Y;
            int pgEndX = pgArrX + RK_ARR_W * 2 + 4;
            if (tmx >= pgArrX && tmx <= pgEndX && tmy >= pgArrY && tmy <= pgArrY + RK_ARR_H) anyHovered = true;
        }
        if (!anyHovered && lastHoveredElement != -1) {
            lastHoveredElement = -1;
        }


        g.pose().popPose();

        // Rank tooltip (rendered in screen space, after popPose)
        if (hoveredRankIndex >= 0 && hoveredRankIndex < allRanks.size() && detailRankIndex < 0 && detailPlayerIndex < 0) {
            renderRankTooltip(g, allRanks.get(hoveredRankIndex), mx, my);
        }

        super.render(g, mx, my, pt);
    }

    // ── Input ───────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        float tx = (float)(mx - guiLeft) / guiScale;
        float ty = (float)(my - guiTop) / guiScale;

        // ── Player detail popup interaction (right panel area) ─────────────────────
        if (detailPlayerIndex >= 0 && detailPlayerIndex < filteredPlayers.size()) {
            if (btn == 0) {
                if (handlePlayerDetailClick(tx, ty)) return true;
            }
            // Consume clicks within the right panel area only
            if (tx >= RK_X1 && tx <= RK_X2 && ty >= RK_Y1 && ty <= 204) return true;
        }

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
                if (tx >= RK_X1 && tx <= RK_X2 && ty >= RK_Y1 && ty <= 204) {
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

            // List mode: search field & scrollbar (left panel only)
            if (listMode) {
                float sfx = LBG_X + SEARCH_X, sfy = LBG_Y + SEARCH_Y;
                if (tx >= sfx && tx <= sfx + SEARCH_W && ty >= sfy && ty <= sfy + SEARCH_H) {
                    searchFocused = true;
                    return true;
                } else {
                    searchFocused = false;
                }
                float sbAbsX = LBG_X + SB_X, sbAbsY = LBG_Y + SB_Y;
                if (tx >= sbAbsX && tx <= sbAbsX + SB_W && ty >= sbAbsY && ty <= sbAbsY + SB_H) {
                    draggingScrollbar = true;
                    updateScrollFromMouse(ty);
                    return true;
                }
            }

            // Rank left-click — always available (right panel)
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
            if (totalRankPages > 1 && detailRankIndex < 0 && detailPlayerIndex < 0) {
                int pgArrX = PG_ARR_X;
                int pgArrY = PG_ARR_Y;
                if (rankPage > 0 && tx >= pgArrX && tx <= pgArrX + RK_ARR_W && ty >= pgArrY && ty <= pgArrY + RK_ARR_H) { rankPage--; return true; }
                int pgNxtX = pgArrX + RK_ARR_W + 4;
                if (rankPage < totalRankPages - 1 && tx >= pgNxtX && tx <= pgNxtX + RK_ARR_W && ty >= pgArrY && ty <= pgArrY + RK_ARR_H) { rankPage++; return true; }
            }
        }

        // Right click — rank detail popup (always available)
        if (btn == 1) {
            // Right-click on list entries opens player detail popup
            if (listMode && detailPlayerIndex < 0) {
                int clickedPlayer = getListEntryAt(tx, ty);
                if (clickedPlayer >= 0) {
                    detailPlayerIndex = clickedPlayer;
                    adminRankScroll = 0;
                    return true;
                }
            }
            if (detailRankIndex < 0) {
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
        if (detailPlayerIndex >= 0 && isOperator) {
            adminRankScroll -= (float)(delta * 12);
            int maxScroll = Math.max(0, allRanks.size() * 12 - 36); // 3 ranks visible
            adminRankScroll = Math.max(0, Math.min(adminRankScroll, maxScroll));
            return true;
        }
        if (listMode) {
            scrollOffset -= (float)(delta * ENT_STRIDE);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean charTyped(char c, int mod) {
        if (timeInputFocused && detailPlayerIndex >= 0 && isOperator) {
            // Allow digits, h, m, s, d, +, -
            if (Character.isDigit(c) || "hHmMsSdD+-".indexOf(c) >= 0) {
                timeInputText += c;
            }
            return true;
        }
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
        if (timeInputFocused && detailPlayerIndex >= 0 && isOperator) {
            if (key == 259 && !timeInputText.isEmpty()) { // Backspace
                timeInputText = timeInputText.substring(0, timeInputText.length() - 1);
                return true;
            }
            if (key == 256) { // Escape
                timeInputFocused = false;
                return true;
            }
            if (key == 257 || key == 335) { // Enter / Numpad Enter
                submitTimeInput();
                return true;
            }
            // Consume all keys while typing so screen doesn't close
            if (key != 256) return true;
        }
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
        // Escape closes detail popups
        if (key == 256 && detailPlayerIndex >= 0) {
            detailPlayerIndex = -1;
            timeInputFocused = false;
            timeInputText = "";
            return true;
        }
        if (key == 256 && detailRankIndex >= 0) {
            detailRankIndex = -1;
            return true;
        }
        return super.keyPressed(key, scan, mod);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    // Cached skins for offline players (persists across screen instances)
    private static final Map<UUID, ResourceLocation> SKIN_CACHE = new HashMap<>();

    private ResourceLocation getSkin(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            PlayerInfo info = mc.player.connection.getPlayerInfo(uuid);
            if (info != null) {
                ResourceLocation skin = info.getSkinLocation();
                SKIN_CACHE.put(uuid, skin); // cache for when they go offline
                return skin;
            }
            if (uuid.equals(mc.player.getUUID())) {
                ResourceLocation skin = mc.player.getSkinTextureLocation();
                SKIN_CACHE.put(uuid, skin);
                return skin;
            }
        }
        // Try cached skin first
        ResourceLocation cached = SKIN_CACHE.get(uuid);
        if (cached != null) return cached;
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

    /** Submit the time input field for the admin time modification. */
    private void submitTimeInput() {
        if (detailPlayerIndex < 0 || detailPlayerIndex >= filteredPlayers.size()) return;
        PlaytimeDataS2CPacket.PlayerListEntry p = filteredPlayers.get(detailPlayerIndex);
        String input = timeInputText.trim();
        if (input.isEmpty()) return;

        try {
            boolean negative = input.startsWith("-");
            String absInput = input.startsWith("-") || input.startsWith("+") ? input.substring(1) : input;
            long ticks = TimeParser.parseTicks(absInput);
            if (negative) ticks = -ticks;
            PlaytimeNetwork.CHANNEL.sendToServer(new AdminModifyTimeC2SPacket(p.uuid, ticks));
            timeInputText = "";
            timeInputFocused = false;
        } catch (IllegalArgumentException e) {
            timeInputErrorMs = System.currentTimeMillis();
        }
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

        MutableComponent l1 = Component.literal("\u00A7f" + playerName + " ");
        l1.append(isAfk ? Component.literal("\u00A7c[AFK]") : Component.literal("\u00A7a[Active]"));
        g.drawString(font, l1, x, y, 0xFFFFFF, false);

        g.drawString(font, "\u00A77Playtime: \u00A7f" + TimeParser.formatTicks(totalTicks), x, y + h, 0xFFFFFF, false);

        MutableComponent l3 = Component.literal("\u00A77Rank: ");
        l3.append(ColorUtil.rankDisplay(currentRankColor, currentRankName));
        g.drawString(font, l3, x, y + h * 2, 0xFFFFFF, false);

        if (isMaxRank) {
            g.drawString(font, "\u00A7a\u2713 Max rank!", x, y + h * 3, 0xFFFFFF, false);
        } else {
            long rem = Math.max(0, ticksToNextRank);
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
        int[] ord = {1, 0, 2};
        int[] yOff = {12, 0, 12};

        for (int c = 0; c < 3; c++) {
            int i = ord[c];
            if (i >= top3Count) continue;
            int cx = x1 + c * cw + cw / 2;
            int cy = y1 + yOff[c];
            long live = top3Ticks[i];

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

            // Hover detection for this slot
            int slotX = RK_X1 + offX + col * SLOT_W;
            int slotY = RK_Y1 + row * SLOT_H;
            boolean slotHovered = tmx >= slotX && tmx <= slotX + SLOT_W && tmy >= slotY && tmy <= slotY + SLOT_H;
            if (slotHovered) {
                hoveredRankIndex = i;
                updateHover(i);
            }

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

            // Hover animation: scale-up + wiggle on the box + item
            float hScale = computeHoverScale(slotHovered);
            float hWiggle = computeHoverWiggle(slotHovered);
            int bw = (int)(RBOX * hScale), bh = (int)(RBOX * hScale);
            int bx = sx - (bw - RBOX) / 2 + (int) hWiggle;
            int byAnim = by - (bh - RBOX) / 2;

            // Draw connector box (with hover scale)
            g.blit(BOX_TEX, bx, byAnim, bw, bh, 0, 0, BOX_N, BOX_N, BOX_N, BOX_N);

            // Render item icon (centered in scaled box)
            if (!r.defaultItem.isEmpty()) {
                ItemStack st = getStack(r.defaultItem);
                if (!st.isEmpty()) {
                    g.pose().pushPose();
                    g.pose().translate(bx + (bw - 16) / 2f, byAnim + (bh - 16) / 2f, 0);
                    g.pose().scale(hScale, hScale, 1f);
                    g.pose().translate(-(bx + (bw - 16) / 2f), -(byAnim + (bh - 16) / 2f), 0);
                    g.renderItem(st, bx + (bw - 16) / 2, byAnim + (bh - 16) / 2);
                    g.pose().popPose();
                }
            }

            // Color overlay: green = claimed, light blue = available (earned but not claimed)
            if (claimed) {
                g.fill(bx, byAnim, bx + bw, byAnim + bh, 0x4400CC00);
            } else if (r.earned) {
                g.fill(bx, byAnim, bx + bw, byAnim + bh, 0x4455CCFF);
            }

            // Hover gray overlay (25% opacity light gray)
            renderHoverOverlay(g, bx, byAnim, bw, bh, slotHovered);

            // Hours text — green if claimed, light blue if available, white otherwise
            String hrs = (r.thresholdTicks / 72_000L) + "h";
            String hrsColor = claimed ? "\u00A7a" : (r.earned ? "\u00A7b" : "\u00A7f");
            g.drawString(font, hrsColor + hrs, bcx - font.width(hrs) / 2, by + RBOX + 1, 0xFFFFFF, false);
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
            lines.add(Component.literal("\u00A77Chunk Expiration in \u00A7f" + r.inactivityDays + " days"));
        } else {
            lines.add(Component.literal("\u00A77No Chunk Expiration"));
        }
        lines.add(Component.literal(""));
        if (isRankClaimed(r)) {
            lines.add(Component.literal("\u00A7aClaimed"));
        } else if (r.earned) {
            lines.add(Component.literal("\u00A7bAvailable \u2014 Click to claim!"));
        } else {
            lines.add(Component.literal("\u00A7c\u2717 Not yet earned"));
        }
        lines.add(Component.literal("\u00A78Right-click for details"));
        g.renderTooltip(font, lines, Optional.empty(), mx, my);
    }

    // ── Rank Detail Popup ───────────────────────────────────────────────────────

    private void renderRankDetailPopup(GuiGraphics g, PlaytimeDataS2CPacket.RankEntry r) {
        int px = RK_X1, py = RK_Y1, pw = RK_X2 - RK_X1, ph = 204 - RK_Y1;

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
                } else if (r.earned) {
                    g.fill(cx - 8, cy, cx + 8, cy + 16, 0x4455CCFF);
                }
            }
        }
        cy += 22;

        // Details
        g.drawString(font, "\u00A77Required Playtime:", px + 10, cy, 0xFFFFFF, false);
        cy += 10;
        String hrs = (r.thresholdTicks / 72_000L) + " hours";
        String hrsCol = isRankClaimed(r) ? "\u00A7a" : (r.earned ? "\u00A7b" : "\u00A7f");
        g.drawString(font, hrsCol + hrs, px + 16, cy, 0xFFFFFF, false);
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
            g.drawString(font, "\u00A77Chunk Expiration in \u00A7f" + r.inactivityDays + " days", px + 10, cy, 0xFFFFFF, false);
        } else {
            g.drawString(font, "\u00A77No Chunk Expiration", px + 10, cy, 0xFFFFFF, false);
        }
        cy += 14;

        // Status
        if (isRankClaimed(r)) {
            g.drawString(font, "\u00A7aRank Claimed", px + 10, cy, 0xFFFFFF, false);
        } else if (r.earned) {
            g.drawString(font, "\u00A7bAvailable \u2014 left-click to claim", px + 10, cy, 0xFFFFFF, false);
        } else {
            long remaining = r.thresholdTicks - totalTicks;
            g.drawString(font, "\u00A7c\u2717 " + TimeParser.formatTicks(Math.max(0, remaining)) + " remaining", px + 10, cy, 0xFFFFFF, false);
        }
    }

    // ── List View ───────────────────────────────────────────────────────────────

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy HH:mm");

    /** Returns the filtered player list index at the given texture-space coords, or -1. */
    private int getListEntryAt(float tx, float ty) {
        if (!listMode) return -1;
        float absX = LBG_X + LE_X, absY = LBG_Y + LE_Y;
        if (tx < absX || tx > absX + LE_W || ty < absY || ty > absY + LE_H) return -1;

        for (int i = 0; i < filteredPlayers.size(); i++) {
            int entryY = (int)(absY + i * ENT_STRIDE - scrollOffset);
            if (ty >= entryY && ty <= entryY + ENT_H) {
                // Make sure the entry is actually visible
                if (entryY + ENT_H >= absY && entryY <= absY + LE_H) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Handle clicks within the player detail popup (right panel area). Returns true if click was consumed. */
    private boolean handlePlayerDetailClick(float tx, float ty) {
        PlaytimeDataS2CPacket.PlayerListEntry p = filteredPlayers.get(detailPlayerIndex);
        int px = RK_X1, py = RK_Y1, pw = RK_X2 - RK_X1, ph = 204 - RK_Y1;

        // X button (top-right corner)
        int xBtnX = px + pw - 16, xBtnY = py + 4;
        if (tx >= xBtnX && tx <= xBtnX + 12 && ty >= xBtnY && ty <= xBtnY + 12) {
            detailPlayerIndex = -1;
            timeInputFocused = false;
            timeInputText = "";
            return true;
        }

        // MSG button — bottom of left column
        int msgW = isOperator ? pw / 2 - 12 : pw - 16;
        int msgH = 14;
        int msgX = px + 8;
        int msgY = py + ph - msgH - 4;
        if (tx >= msgX && tx <= msgX + msgW && ty >= msgY && ty <= msgY + msgH) {
            this.onClose();
            Minecraft.getInstance().setScreen(new ChatScreen("/msg " + p.name + " "));
            return true;
        }

        // Operator controls (right column)
        if (isOperator) {
            int rightX = px + pw / 2 + 4;
            int rcy = py + 32;

            // UUID click-to-copy area (2 lines of UUID text)
            int uuidY = rcy + 9; // first UUID line Y
            int uuidH = 18; // covers both lines
            int uuidW = pw / 2 - 12;
            if (tx >= rightX && tx <= rightX + uuidW && ty >= uuidY && ty <= uuidY + uuidH) {
                Minecraft.getInstance().keyboardHandler.setClipboard(p.uuid.toString());
                uuidCopiedMs = System.currentTimeMillis();
                return true;
            }

            // Time input field — after UUID + separator
            int inputY = py + 71; // matches render position
            int inputW = pw / 2 - 12 - 30; // leave room for Apply button
            int inputH = 12;
            if (tx >= rightX && tx <= rightX + inputW && ty >= inputY && ty <= inputY + inputH) {
                timeInputFocused = true;
                searchFocused = false;
                return true;
            } else if (tx >= rightX && tx <= rightX + pw / 2 - 12 && ty >= inputY && ty <= inputY + inputH) {
                // Clicked within the row — could be Apply button
                timeInputFocused = false;
            }

            // Apply button
            int applyX = rightX + inputW + 2;
            int applyW = 26;
            if (tx >= applyX && tx <= applyX + applyW && ty >= inputY && ty <= inputY + inputH) {
                submitTimeInput();
                return true;
            }

            // Rank list (3 visible at a time)
            int rkListX = rightX;
            int rkListY = py + 97; // after input + Set Rank header
            int rkListW = pw / 2 - 12;
            int rkListH = 36; // 3 ranks * 12px
            if (tx >= rkListX && tx <= rkListX + rkListW && ty >= rkListY && ty <= rkListY + rkListH) {
                float relY = ty - rkListY + adminRankScroll;
                int idx = (int)(relY / 12);
                if (idx >= 0 && idx < allRanks.size()) {
                    PlaytimeNetwork.CHANNEL.sendToServer(new AdminSetRankC2SPacket(p.uuid, allRanks.get(idx).id));
                    return true;
                }
            }
        }

        // Consume click within popup area
        return tx >= px && tx <= px + pw && ty >= py && ty <= py + ph;
    }

    /** Render player detail popup in the right panel area (extends below rank grid). */
    private void renderPlayerDetailPopup(GuiGraphics g, PlaytimeDataS2CPacket.PlayerListEntry p, float tmx, float tmy) {
        int px = RK_X1, py = RK_Y1, pw = RK_X2 - RK_X1, ph = 204 - RK_Y1;

        // Dark semi-transparent background
        g.fill(px, py, px + pw, py + ph, 0xEE1A1A2E);
        // Border
        g.fill(px, py, px + pw, py + 1, 0xFF555577);
        g.fill(px, py + ph - 1, px + pw, py + ph, 0xFF555577);
        g.fill(px, py, px + 1, py + ph, 0xFF555577);
        g.fill(px + pw - 1, py, px + pw, py + ph, 0xFF555577);

        // X button (top-right)
        g.drawString(font, "\u00A7c\u2715", px + pw - 14, py + 4, 0xFFFFFF, false);

        // ── Left column: Player info ─────────────────────────────────────────────
        int cy = py + 6;

        // Player head (20px)
        renderHead(g, getSkin(p.uuid), px + 8, cy, 20);

        // Name + rank + status right of head
        int infoX = px + 32;
        g.drawString(font, "\u00A7f" + p.name, infoX, cy + 2, 0xFFFFFF, false);
        MutableComponent rankC = ColorUtil.rankDisplay(p.rankColor, p.rankName);
        g.drawString(font, rankC, infoX, cy + 12, 0xFFFFFF, false);

        String statusLabel, statusColor;
        if (p.status == 0) { statusLabel = "Online"; statusColor = "\u00A7a"; }
        else if (p.status == 1) { statusLabel = "AFK"; statusColor = "\u00A7e"; }
        else { statusLabel = "Offline"; statusColor = "\u00A7c"; }
        g.drawString(font, statusColor + "\u25CF " + statusLabel, infoX + font.width(rankC) + 4, cy + 12, 0xFFFFFF, false);

        // Separator
        cy += 26;
        int sepRight = isOperator ? px + pw / 2 - 4 : px + pw - 6;
        g.fill(px + 6, cy, sepRight, cy + 1, 0xFF555577);
        cy += 5;

        // First join
        String firstJoin = p.firstJoinMs > 0 ? DATE_FORMAT.format(new Date(p.firstJoinMs)) : "Unknown";
        g.drawString(font, "\u00A77First Join: \u00A7f" + firstJoin, px + 8, cy, 0xFFFFFF, false);
        cy += 10;

        // Last seen
        String lastSeen;
        if (p.status != 2) { lastSeen = "Now"; }
        else { lastSeen = p.lastSeenMs > 0 ? DATE_FORMAT.format(new Date(p.lastSeenMs)) : "Unknown"; }
        g.drawString(font, "\u00A77Last Seen: \u00A7f" + lastSeen, px + 8, cy, 0xFFFFFF, false);
        cy += 10;

        // Playtime
        long liveTicks = p.totalTicks;
        long totalSecs = liveTicks / 20;
        long hours = totalSecs / 3600;
        long mins = (totalSecs % 3600) / 60;
        long secs = totalSecs % 60;
        String exactTime = String.format("%dh %dm %ds", hours, mins, secs);
        g.drawString(font, "\u00A77Playtime: \u00A7f" + exactTime, px + 8, cy, 0xFFFFFF, false);

        // ── Right column: Operator controls ──────────────────────────────────────
        if (isOperator) {
            int rightX = px + pw / 2 + 4;
            int rcy = py + 32;

            // UUID — click to copy
            boolean uuidRecentlyCopied = (System.currentTimeMillis() - uuidCopiedMs) < 1500;
            if (uuidRecentlyCopied) {
                g.drawString(font, "\u00A7a\u2713 Copied!", rightX, rcy, 0xFFFFFF, false);
            } else {
                g.drawString(font, "\u00A77UUID: \u00A78(click to copy)", rightX, rcy, 0xFFFFFF, false);
            }
            rcy += 9;
            String uuidStr = p.uuid.toString();
            String uuidColor = uuidRecentlyCopied ? "\u00A7a" : "\u00A78";
            g.drawString(font, uuidColor + uuidStr.substring(0, 18), rightX + 2, rcy, 0xFFFFFF, false);
            rcy += 8;
            g.drawString(font, uuidColor + uuidStr.substring(18), rightX + 2, rcy, 0xFFFFFF, false);
            rcy += 12;

            // Separator
            g.fill(rightX, rcy, px + pw - 6, rcy + 1, 0xFF555577);
            rcy += 5;

            // Time mod — input field + Apply button
            g.drawString(font, "\u00A7d\u00A7lModify Time:", rightX, rcy, 0xFFFFFF, false);
            rcy += 10;

            int inputW = pw / 2 - 12 - 30;
            int inputH = 12;

            // Check for error flash (red border for 1 second)
            boolean inputError = (System.currentTimeMillis() - timeInputErrorMs) < 1000;
            int borderColor = inputError ? 0xFFFF3333 : (timeInputFocused ? 0xFF555555 : 0xFF3A3A3A);
            g.fill(rightX, rcy, rightX + inputW, rcy + inputH, borderColor);
            g.fill(rightX + 1, rcy + 1, rightX + inputW - 1, rcy + inputH - 1, 0xFF1A1A1A);
            String inputDisplay = timeInputText.isEmpty() && !timeInputFocused ? "\u00A78e.g. -10h" : timeInputText + (timeInputFocused ? "_" : "");
            g.drawString(font, "\u00A7f" + inputDisplay, rightX + 3, rcy + 2, 0xFFFFFF, false);

            // Apply button
            int applyX = rightX + inputW + 2;
            int applyW = 26;
            boolean applyHover = tmx >= applyX && tmx <= applyX + applyW && tmy >= rcy && tmy <= rcy + inputH;
            g.fill(applyX, rcy, applyX + applyW, rcy + inputH, applyHover ? 0xFF33CC55 : 0xFF338855);
            g.drawString(font, "\u00A7fApply", applyX + 1, rcy + 2, 0xFFFFFF, false);
            rcy += inputH + 6;

            // Set Rank
            g.drawString(font, "\u00A7d\u00A7lSet Rank:", rightX, rcy, 0xFFFFFF, false);
            rcy += 10;

            int rkListX = rightX;
            int rkListY = rcy;
            int rkListW = pw / 2 - 12;
            int rkListH = 36; // 3 ranks * 12px

            int absRkX1 = (int)(guiLeft + rkListX * guiScale);
            int absRkY1 = (int)(guiTop + rkListY * guiScale);
            int absRkX2 = (int)(guiLeft + (rkListX + rkListW) * guiScale);
            int absRkY2 = (int)(guiTop + (rkListY + rkListH) * guiScale);
            g.enableScissor(absRkX1, absRkY1, absRkX2, absRkY2);

            for (int i = 0; i < allRanks.size(); i++) {
                PlaytimeDataS2CPacket.RankEntry rank = allRanks.get(i);
                int entryY = rkListY + i * 12 - (int) adminRankScroll;
                if (entryY + 12 < rkListY || entryY > rkListY + rkListH) continue;

                boolean hover = tmx >= rkListX && tmx <= rkListX + rkListW && tmy >= entryY && tmy <= entryY + 12;
                if (hover) g.fill(rkListX, entryY, rkListX + rkListW, entryY + 12, 0x40FFFFFF);
                MutableComponent rc2 = ColorUtil.rankDisplay(rank.color, rank.displayName);
                g.drawString(font, rc2, rkListX + 2, entryY + 2, 0xFFFFFF, false);
            }
            g.disableScissor();
        }

        // MSG button — bottom of left column
        int msgW = isOperator ? pw / 2 - 12 : pw - 16;
        int msgH = 14;
        int msgX = px + 8;
        int msgY = py + ph - msgH - 4;
        boolean msgHover = tmx >= msgX && tmx <= msgX + msgW && tmy >= msgY && tmy <= msgY + msgH;
        g.fill(msgX, msgY, msgX + msgW, msgY + msgH, msgHover ? 0xFFFF69B4 : 0xFFD84D9E);
        g.fill(msgX + 1, msgY + 1, msgX + msgW - 1, msgY + msgH - 1, msgHover ? 0xFFE050A0 : 0xFFC23890);
        String msgLabel = "\u00A7fMSG " + p.name;
        int msgLabelW = font.width(msgLabel);
        if (msgLabelW > msgW - 4) { msgLabel = "\u00A7fMSG..."; msgLabelW = font.width(msgLabel); }
        g.drawString(font, msgLabel, msgX + (msgW - msgLabelW) / 2, msgY + 3, 0xFFFFFF, false);
    }

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

            // Hours — server snapshot, no client-side adjustment
            String hrs2 = TimeParser.formatTicks(p.totalTicks);

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

    // ── Hover Animation Helpers ────────────────────────────────────────────────

    /** Update hover state; call once per frame with the current hovered element ID. */
    private void updateHover(int elementId) {
        if (elementId != lastHoveredElement) {
            lastHoveredElement = elementId;
            hoverStartMs = System.currentTimeMillis();
        }
    }

    /** Returns 1.0 when not hovered, 1.1 (10% bigger) when hovered. */
    private float computeHoverScale(boolean hovered) {
        return hovered ? 1.10f : 1.0f;
    }

    /** Horizontal wiggle offset in pixels. Wiggles after 15 seconds of hovering. */
    private float computeHoverWiggle(boolean hovered) {
        if (!hovered) return 0f;
        long elapsed = System.currentTimeMillis() - hoverStartMs;
        if (elapsed < 15_000) return 0f;
        // Sine wiggle: ±1.5 pixels, 4 Hz
        return (float)(Math.sin((elapsed - 15_000) * 0.025) * 1.5);
    }

    /** Draw a 25% opacity light gray overlay on the given area when hovered. */
    private void renderHoverOverlay(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        if (!hovered) return;
        g.fill(x, y, x + w, y + h, 0x40C0C0C0); // light gray, 25% opacity
    }

    /**
     * Render a texture with hover animation: 10% scale-up centered, wiggle after 15s, gray overlay.
     */
    private void renderHoveredBlit(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h,
                                    int srcW, int srcH, boolean hovered, int hoverId) {
        if (hovered) updateHover(hoverId);
        float scale = computeHoverScale(hovered);
        float wiggle = computeHoverWiggle(hovered);
        int dw = (int)(w * scale), dh = (int)(h * scale);
        int dx = x - (dw - w) / 2 + (int) wiggle;
        int dy = y - (dh - h) / 2;
        g.blit(tex, dx, dy, dw, dh, 0, 0, srcW, srcH, srcW, srcH);
        renderHoverOverlay(g, dx, dy, dw, dh, hovered);
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
