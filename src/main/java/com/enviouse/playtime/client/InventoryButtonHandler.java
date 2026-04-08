package com.enviouse.playtime.client;

import com.enviouse.playtime.network.PlaytimeNetwork;
import com.enviouse.playtime.network.RequestRefreshC2SPacket;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Adds a small "Playtime" button to the player's inventory screen.
 * The button is rendered manually (not a Button widget) to support:
 * <ul>
 *   <li><b>Auto-sizing</b> — button width adapts to text width, never clips.</li>
 *   <li><b>Scaling</b> — hold Shift and scroll while hovering to resize (0.5×–2.0×).</li>
 *   <li><b>Dragging</b> — middle-click and drag to reposition anywhere.</li>
 *   <li><b>Tooltip</b> — hover to see what it does and how to customise it.</li>
 * </ul>
 * Position and scale are persisted to {@code config/playtime-button.json}.
 */
@OnlyIn(Dist.CLIENT)
public class InventoryButtonHandler {

    private static final String BUTTON_TEXT = "§6⏱ Playtime";
    private static final String BUTTON_TEXT_PLAIN = "⏱ Playtime"; // for width measurement
    private static final int PADDING_X = 6;   // horizontal padding each side
    private static final int PADDING_Y = 3;   // vertical padding each side
    private static final int BASE_HEIGHT = 14; // base height at scale 1.0

    // ── Persisted state ─────────────────────────────────────────────────────
    private static int savedX = -1;
    private static int savedY = -1;
    private static float savedScale = 1.0f;
    private static boolean configLoaded = false;

    // ── Runtime state ───────────────────────────────────────────────────────
    private static boolean dragging = false;
    private static double dragOffsetX = 0;
    private static double dragOffsetY = 0;

    // ── Computed button bounds (screen pixels, updated each frame) ───────────
    private static int btnX, btnY, btnW, btnH;

    // ── Config file ─────────────────────────────────────────────────────────
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "playtime-button.json";

    // ═══════════════════════════════════════════════════════════════════════
    //  Screen lifecycle
    // ═══════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen)) return;
        loadConfig();
        dragging = false;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Rendering — custom button drawn on top of the inventory screen
    // ═══════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen inv)) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();

        // ── Compute button dimensions at current scale ──────────────────────
        float scale = Math.max(0.5f, Math.min(2.0f, savedScale));
        int textWidth = mc.font.width(BUTTON_TEXT_PLAIN);
        btnW = (int) ((textWidth + PADDING_X * 2) * scale);
        btnH = (int) (BASE_HEIGHT * scale);

        // ── Position ────────────────────────────────────────────────────────
        if (savedX < 0 || savedY < 0) {
            // Default: right side of inventory
            savedX = inv.getGuiLeft() + inv.getXSize() + 2;
            savedY = inv.getGuiTop() + 4;
        }
        btnX = Math.max(0, Math.min(savedX, inv.width - btnW));
        btnY = Math.max(0, Math.min(savedY, inv.height - btnH));

        // ── Hover detection ─────────────────────────────────────────────────
        boolean hovered = mouseX >= btnX && mouseX <= btnX + btnW
                && mouseY >= btnY && mouseY <= btnY + btnH;

        // ── Draw button background ──────────────────────────────────────────
        int bgColor = hovered ? 0xE0505050 : 0xD0303030;
        int borderColor = hovered ? 0xFFDEA040 : 0xFF606060;
        int borderHi = hovered ? 0xFFF0C060 : 0xFF808080;

        // Outer border
        g.fill(btnX, btnY, btnX + btnW, btnY + btnH, borderColor);
        // Inner fill
        g.fill(btnX + 1, btnY + 1, btnX + btnW - 1, btnY + btnH - 1, bgColor);
        // Top highlight
        g.fill(btnX + 1, btnY, btnX + btnW - 1, btnY + 1, borderHi);

        // ── Draw text (scaled) ──────────────────────────────────────────────
        g.pose().pushPose();
        float textCenterX = btnX + btnW / 2f;
        float textCenterY = btnY + (btnH - 8 * scale) / 2f;
        g.pose().translate(textCenterX, textCenterY, 0);
        g.pose().scale(scale, scale, 1f);
        int scaledTextW = mc.font.width(BUTTON_TEXT_PLAIN);
        g.drawString(mc.font, BUTTON_TEXT, -scaledTextW / 2, 0, 0xFFFFFF, false);
        g.pose().popPose();

        // ── Drag indicator ──────────────────────────────────────────────────
        if (dragging) {
            g.fill(btnX - 1, btnY - 1, btnX + btnW + 1, btnY, 0x80FFFFFF);
            g.fill(btnX - 1, btnY + btnH, btnX + btnW + 1, btnY + btnH + 1, 0x80FFFFFF);
            g.fill(btnX - 1, btnY, btnX, btnY + btnH, 0x80FFFFFF);
            g.fill(btnX + btnW, btnY, btnX + btnW + 1, btnY + btnH, 0x80FFFFFF);
        }

        // ── Tooltip on hover ────────────────────────────────────────────────
        if (hovered && !dragging) {
            List<Component> tooltip = List.of(
                    Component.literal("§6§l⏱ Playtime"),
                    Component.literal(""),
                    Component.literal("§fLeft-click §7to view playtime stats"),
                    Component.literal("§fMiddle-click + drag §7to reposition"),
                    Component.literal("§fShift + Scroll §7to resize"),
                    Component.literal(""),
                    Component.literal("§8Scale: " + String.format("%.0f%%", scale * 100))
            );
            g.renderTooltip(mc.font, tooltip, Optional.empty(), mouseX, mouseY);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Mouse input — click, drag, scroll
    // ═══════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen)) return;

        double mx = event.getMouseX();
        double my = event.getMouseY();
        boolean onButton = mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH;

        if (!onButton) return;

        int button = event.getButton();

        // Left click → open Playtime GUI
        if (button == 0) {
            PlaytimeNetwork.CHANNEL.sendToServer(new RequestRefreshC2SPacket());
            event.setCanceled(true);
            return;
        }

        // Middle click → start drag
        if (button == 2) {
            dragging = true;
            dragOffsetX = mx - btnX;
            dragOffsetY = my - btnY;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen inv)) return;
        if (!dragging) return;
        if (event.getMouseButton() != 2) return;

        double mx = event.getMouseX();
        double my = event.getMouseY();

        savedX = (int) Math.max(0, Math.min(mx - dragOffsetX, inv.width - btnW));
        savedY = (int) Math.max(0, Math.min(my - dragOffsetY, inv.height - btnH));

        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen)) return;
        if (event.getButton() != 2) return;
        if (!dragging) return;

        dragging = false;
        saveConfig();
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen)) return;

        double mx = event.getMouseX();
        double my = event.getMouseY();
        boolean onButton = mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH;

        if (!onButton) return;

        // Shift + Scroll → resize
        if (hasShiftDown()) {
            double delta = event.getScrollDelta();
            savedScale += (float) (delta * 0.1);
            savedScale = Math.max(0.5f, Math.min(2.0f, savedScale));
            savedScale = Math.round(savedScale * 20f) / 20f;
            saveConfig();
            event.setCanceled(true);
        }
    }

    /** Check if Shift key is held. */
    private static boolean hasShiftDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Config persistence (position + scale)
    // ═══════════════════════════════════════════════════════════════════════

    private static void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;

        Path path = getConfigPath();
        if (!Files.exists(path)) return;

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null) {
                if (json.has("x")) savedX = json.get("x").getAsInt();
                if (json.has("y")) savedY = json.get("y").getAsInt();
                if (json.has("scale")) savedScale = json.get("scale").getAsFloat();
                savedScale = Math.max(0.5f, Math.min(2.0f, savedScale));
            }
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            // Ignore — use defaults
        }
    }

    private static void saveConfig() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("x", savedX);
            json.addProperty("y", savedY);
            json.addProperty("scale", Math.round(savedScale * 100f) / 100f);
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            // Non-critical — position just won't persist
        }
    }

    private static Path getConfigPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(CONFIG_FILE);
    }
}
