package com.enviouse.playtime.client;

import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.network.PlaytimeNetwork;
import com.enviouse.playtime.network.RequestRefreshC2SPacket;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
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

/**
 * Adds a small "Playtime" button to the player's inventory screen.
 * Clicking it sends a {@link RequestRefreshC2SPacket} to the server,
 * which responds with the full playtime data packet and opens the GUI.
 * <p>
 * The button is <b>moveable</b>: hover over it and hold middle-click (mouse3)
 * to drag it anywhere on screen. The position is saved to
 * {@code config/playtime-button.json} and persists across sessions.
 */
@OnlyIn(Dist.CLIENT)
public class InventoryButtonHandler {

    private static final int BTN_WIDTH = 50;
    private static final int BTN_HEIGHT = 14;

    // ── Persisted button position ───────────────────────────────────────────
    // Stored as absolute screen coordinates. -1 = use default (relative to inventory).
    private static int savedX = -1;
    private static int savedY = -1;
    private static boolean positionLoaded = false;

    // ── Drag state ──────────────────────────────────────────────────────────
    private static boolean dragging = false;
    private static double dragOffsetX = 0;  // offset from mouse to button top-left
    private static double dragOffsetY = 0;

    // ── Active button reference (set during screen init) ────────────────────
    private static Button activeButton = null;

    // ── Config file ─────────────────────────────────────────────────────────
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "playtime-button.json";

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen inv)) return;

        loadPosition();

        int x, y;
        if (savedX >= 0 && savedY >= 0) {
            // Use saved absolute position, clamped to screen bounds
            x = Math.max(0, Math.min(savedX, inv.width - BTN_WIDTH));
            y = Math.max(0, Math.min(savedY, inv.height - BTN_HEIGHT));
        } else {
            // Default: right of inventory
            x = inv.getGuiLeft() + inv.getXSize() + 2;
            y = inv.getGuiTop() + 4;
        }

        Button playtimeBtn = Button.builder(
                Component.literal("§6⏱ Playtime"),
                btn -> {
                    // Don't trigger action if we just finished dragging
                    if (!dragging) {
                        PlaytimeNetwork.CHANNEL.sendToServer(new RequestRefreshC2SPacket());
                    }
                }
        ).bounds(x, y, BTN_WIDTH, BTN_HEIGHT).build();

        event.addListener(playtimeBtn);
        activeButton = playtimeBtn;
        dragging = false;
    }

    // ── Middle-click drag handling ──────────────────────────────────────────

    @SubscribeEvent
    public void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen)) return;
        if (activeButton == null) return;

        // Middle click = button 2
        if (event.getButton() != 2) return;

        double mx = event.getMouseX();
        double my = event.getMouseY();

        // Check if mouse is over the button
        if (mx >= activeButton.getX() && mx <= activeButton.getX() + activeButton.getWidth()
                && my >= activeButton.getY() && my <= activeButton.getY() + activeButton.getHeight()) {
            dragging = true;
            dragOffsetX = mx - activeButton.getX();
            dragOffsetY = my - activeButton.getY();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen inv)) return;
        if (!dragging || activeButton == null) return;

        // Only continue drag on middle button
        if (event.getMouseButton() != 2) return;

        double mx = event.getMouseX();
        double my = event.getMouseY();

        // Calculate new position, clamped to screen bounds
        int newX = (int) Math.max(0, Math.min(mx - dragOffsetX, inv.width - BTN_WIDTH));
        int newY = (int) Math.max(0, Math.min(my - dragOffsetY, inv.height - BTN_HEIGHT));

        activeButton.setX(newX);
        activeButton.setY(newY);

        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen)) return;

        // Middle click release = button 2
        if (event.getButton() != 2) return;
        if (!dragging || activeButton == null) return;

        // Save the new position
        savedX = activeButton.getX();
        savedY = activeButton.getY();
        savePosition();

        dragging = false;
        event.setCanceled(true);
    }

    // ── Persistence ─────────────────────────────────────────────────────────

    private static void loadPosition() {
        if (positionLoaded) return;
        positionLoaded = true;

        Path configPath = getConfigPath();
        if (!Files.exists(configPath)) return;

        try (Reader reader = Files.newBufferedReader(configPath)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null) {
                if (json.has("x")) savedX = json.get("x").getAsInt();
                if (json.has("y")) savedY = json.get("y").getAsInt();
            }
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            // Ignore — use default position
        }
    }

    private static void savePosition() {
        Path configPath = getConfigPath();
        try {
            Files.createDirectories(configPath.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("x", savedX);
            json.addProperty("y", savedY);
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            // Ignore — position won't persist but that's non-critical
        }
    }

    private static Path getConfigPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(CONFIG_FILE);
    }
}
