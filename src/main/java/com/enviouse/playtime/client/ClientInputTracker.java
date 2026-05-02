package com.enviouse.playtime.client;

import com.enviouse.playtime.network.ClientActivitySignalC2SPacket;
import com.enviouse.playtime.network.PlaytimeNetwork;
import com.enviouse.playtime.service.SessionTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

/**
 * Client-side input tracker for the AFK detection system.
 *
 * <p>Subscribes to Forge input events and once per second flushes a batched
 * {@link ClientActivitySignalC2SPacket} to the server. Signals captured here
 * are inputs the server can't see directly:
 * <ul>
 *   <li><b>Keyboard</b> — any key press (in-game OR in a screen). Tracks distinct
 *       key codes so the server can defeat single-key macros.</li>
 *   <li><b>Mouse movement</b> — raw cursor delta polled from GLFW each tick.
 *       Picks up sub-threshold motion the server's rotation sampler ignores.</li>
 *   <li><b>Mouse button</b> — left/right/middle/extra clicks (in-game and GUI).</li>
 *   <li><b>GUI</b> — any new screen opened (chat, inventory, container, menus).</li>
 *   <li><b>Scroll</b> — mouse wheel events.</li>
 *   <li><b>Inventory</b> — clicks inside a container/inventory screen.</li>
 *   <li><b>Window focus</b> — whether the OS treats the Minecraft window as
 *       foreground (defeats jiggler scripts that act on a minimised game).</li>
 * </ul>
 *
 * <p>Vanilla servers (no Playtime mod) will silently drop the unknown packet
 * — no protocol-version handshake errors because {@link PlaytimeNetwork} uses
 * {@code acceptMissingOr}.
 */
@OnlyIn(Dist.CLIENT)
public class ClientInputTracker {

    /** Send a packet at most this often (in client ticks). 20 ticks = 1 s. */
    private static final int FLUSH_INTERVAL_TICKS = 20;

    /**
     * Pixel distance the cursor must move within a flush window to count as
     * a SIG_MOUSE_MOVE event. Small enough to catch real human jitter, large
     * enough to ignore floating-point noise.
     */
    private static final double MOUSE_MOVE_PIXEL_THRESHOLD = 2.0;

    /** OR of {@code SessionTracker.SIG_*} client bits accumulated since last flush. */
    private int signalBits = 0;

    /** Distinct key codes pressed since last flush (sized at flush time). */
    private final Set<Integer> uniqueKeys = new HashSet<>();

    /** Last sampled cursor position, used for raw-mouse-move delta. */
    private double lastCursorX = Double.NaN;
    private double lastCursorY = Double.NaN;

    /** Tick counter — flushes when this hits {@link #FLUSH_INTERVAL_TICKS}. */
    private int ticksSinceFlush = 0;

    /**
     * Last sent values — used to suppress redundant packets when nothing
     * has changed (player is genuinely idle). We still send a heartbeat at
     * least every 5s so the server knows the client is alive.
     */
    private int lastSentBits = -1;
    private boolean lastSentFocused = false;
    private int ticksSinceLastSend = 0;
    private static final int HEARTBEAT_INTERVAL_TICKS = 100; // 5 s

    // ═══════════════════════════════════════════════════════════════════════
    //  In-game input events
    // ═══════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onKey(InputEvent.Key event) {
        if (!isConnectedAndAlive()) return;
        // Only count actual presses (not repeats or releases) to keep
        // uniqueKey counts honest against held-W macros.
        if (event.getAction() == GLFW.GLFW_PRESS) {
            signalBits |= SessionTracker.SIG_KEYBOARD;
            uniqueKeys.add(event.getKey());
        }
    }

    @SubscribeEvent
    public void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!isConnectedAndAlive()) return;
        if (event.getAction() == GLFW.GLFW_PRESS) {
            signalBits |= SessionTracker.SIG_MOUSE_CLICK;
        }
    }

    @SubscribeEvent
    public void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!isConnectedAndAlive()) return;
        if (event.getScrollDelta() != 0.0) {
            signalBits |= SessionTracker.SIG_SCROLL;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Screen events — fire even when an inventory/menu is open
    // ═══════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onScreenOpening(ScreenEvent.Opening event) {
        if (!isConnectedAndAlive()) return;
        if (event.getNewScreen() != null) {
            signalBits |= SessionTracker.SIG_GUI;
        }
    }

    @SubscribeEvent
    public void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!isConnectedAndAlive()) return;
        // Mouse click inside any screen — also fires SIG_MOUSE_CLICK because
        // InputEvent.MouseButton doesn't fire while in a Screen.
        signalBits |= SessionTracker.SIG_MOUSE_CLICK;
        // Clicks inside a container screen specifically count as inventory activity.
        if (event.getScreen() instanceof AbstractContainerScreen<?>) {
            signalBits |= SessionTracker.SIG_INVENTORY;
        }
    }

    @SubscribeEvent
    public void onScreenScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!isConnectedAndAlive()) return;
        if (event.getScrollDelta() != 0.0) {
            signalBits |= SessionTracker.SIG_SCROLL;
        }
    }

    @SubscribeEvent
    public void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        if (!isConnectedAndAlive()) return;
        signalBits |= SessionTracker.SIG_KEYBOARD;
        uniqueKeys.add(event.getKeyCode());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Per-tick: raw cursor delta sampling + flush
    // ═══════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isConnectedAndAlive()) {
            // Reset flush state so we don't blast a stale signal on reconnect.
            signalBits = 0;
            uniqueKeys.clear();
            ticksSinceFlush = 0;
            ticksSinceLastSend = 0;
            lastCursorX = Double.NaN;
            lastCursorY = Double.NaN;
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        // Poll raw cursor position from GLFW directly — this catches movement
        // even when the Minecraft input handler isn't routing it (e.g. some
        // overlays). Skip the very first sample (no previous reference).
        long window = mc.getWindow().getWindow();
        double[] cx = new double[1];
        double[] cy = new double[1];
        GLFW.glfwGetCursorPos(window, cx, cy);
        if (!Double.isNaN(lastCursorX)) {
            double dx = cx[0] - lastCursorX;
            double dy = cy[0] - lastCursorY;
            if (dx * dx + dy * dy >= MOUSE_MOVE_PIXEL_THRESHOLD * MOUSE_MOVE_PIXEL_THRESHOLD) {
                signalBits |= SessionTracker.SIG_MOUSE_MOVE;
            }
        }
        lastCursorX = cx[0];
        lastCursorY = cy[0];

        // Window focus is a passive signal — set every tick the window is
        // foreground. The server uses both this bit AND requireFocus to gate
        // other signals.
        boolean focused = mc.isWindowActive();
        if (focused) {
            signalBits |= SessionTracker.SIG_WINDOW_FOCUS;
        }

        ticksSinceFlush++;
        ticksSinceLastSend++;
        if (ticksSinceFlush >= FLUSH_INTERVAL_TICKS) {
            flush(focused);
            ticksSinceFlush = 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Flush
    // ═══════════════════════════════════════════════════════════════════════

    private void flush(boolean focused) {
        // Skip the packet entirely if nothing changed AND we're inside the
        // heartbeat window — saves bandwidth when the player is genuinely idle.
        boolean changed = signalBits != lastSentBits || focused != lastSentFocused;
        boolean heartbeatDue = ticksSinceLastSend >= HEARTBEAT_INTERVAL_TICKS;
        if (!changed && !heartbeatDue && signalBits == 0) {
            uniqueKeys.clear();
            return;
        }

        try {
            PlaytimeNetwork.CHANNEL.sendToServer(
                    new ClientActivitySignalC2SPacket(signalBits, focused, uniqueKeys.size()));
            lastSentBits = signalBits;
            lastSentFocused = focused;
            ticksSinceLastSend = 0;
        } catch (Throwable ignored) {
            // Server may not have the mod — silently drop. SimpleChannel will
            // already have logged at debug level if the channel is unregistered.
        }

        signalBits = 0;
        uniqueKeys.clear();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * True only when we have a live game connection (so packets actually go
     * somewhere) and a player exists. Avoids burning packets on the title
     * screen, in the world-loading screen, or after disconnect.
     */
    private static boolean isConnectedAndAlive() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null
                && mc.player != null
                && mc.getConnection() != null;
    }
}
