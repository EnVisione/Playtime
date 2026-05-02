package com.enviouse.playtime.network;

import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.service.SessionTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S packet sent ~once per second by {@code ClientInputTracker} on the
 * physical client. Reports a batched bitmask of input signals observed since
 * the last packet (keyboard, raw mouse movement, GUI screens, scroll wheel,
 * mouse buttons, inventory slot changes, OS-window focus).
 *
 * <p>The server uses these to satisfy {@code Config.afkMinSignals} alongside
 * its own server-derived signals (rotation, position, hotbar, sprint, world
 * interaction). Per-type config toggles, rate-limiting, focus requirements,
 * and anti-spoof correlation are all enforced by
 * {@link SessionTracker#onClientSignal}.
 *
 * <p>Wire format:
 * <ul>
 *   <li>{@code int signalBits}    — OR of {@code SessionTracker.SIG_*} client bits</li>
 *   <li>{@code boolean focused}   — true if the Minecraft window was OS-focused
 *       at packet send time</li>
 *   <li>{@code byte uniqueKeys}   — distinct key codes pressed in this window
 *       (clamped to 0–127), used to defeat single-key macros</li>
 * </ul>
 */
public class ClientActivitySignalC2SPacket {

    /** Mask of bits the server is willing to accept from the client. */
    private static final int VALID_BITS_MASK =
            SessionTracker.SIG_KEYBOARD | SessionTracker.SIG_MOUSE_MOVE
                    | SessionTracker.SIG_MOUSE_CLICK | SessionTracker.SIG_GUI
                    | SessionTracker.SIG_SCROLL | SessionTracker.SIG_INVENTORY
                    | SessionTracker.SIG_WINDOW_FOCUS;

    private final int signalBits;
    private final boolean windowFocused;
    private final int uniqueKeys;

    public ClientActivitySignalC2SPacket(int signalBits, boolean windowFocused, int uniqueKeys) {
        // Strip any junk bits client-side too — defence in depth.
        this.signalBits = signalBits & VALID_BITS_MASK;
        this.windowFocused = windowFocused;
        this.uniqueKeys = Math.max(0, Math.min(127, uniqueKeys));
    }

    public ClientActivitySignalC2SPacket(FriendlyByteBuf buf) {
        this.signalBits = buf.readInt() & VALID_BITS_MASK;
        this.windowFocused = buf.readBoolean();
        this.uniqueKeys = Math.max(0, Math.min(127, buf.readByte() & 0xFF));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(signalBits);
        buf.writeBoolean(windowFocused);
        buf.writeByte(uniqueKeys);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;
            SessionTracker tracker = Playtime.getSessionTracker();
            if (tracker == null) return;
            tracker.onClientSignal(sender.getUUID(), signalBits, windowFocused, uniqueKeys);
        });
        ctx.setPacketHandled(true);
    }
}
