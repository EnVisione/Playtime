package com.enviouse.playtime.client;

import com.enviouse.playtime.network.PlaytimeDataS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Client-side handler for incoming playtime packets.
 * Isolated in its own class so server-side classloading never touches Minecraft client classes.
 */
public class ClientPacketHandler {

    public static void openPlaytimeScreen(PlaytimeDataS2CPacket packet) {
        Minecraft.getInstance().setScreen(new PlaytimeScreen(packet));
    }

    /** Called when the server pushes an AFK state change for any player. */
    public static void handleAfkSync(UUID playerUuid, boolean afk) {
        Minecraft mc = Minecraft.getInstance();

        // Update PlaytimeScreen if it's open
        if (mc.screen instanceof PlaytimeScreen screen) {
            screen.updateAfkStatus(playerUuid, afk);
        }

        // Show a toast notification if this is the LOCAL player's AFK state change
        if (mc.player != null && mc.player.getUUID().equals(playerUuid)) {
            if (afk) {
                SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastIds.TUTORIAL_HINT,
                        Component.literal("§c⚠ AFK Detected"),
                        Component.literal("§7Playtime tracking paused"));
            } else {
                SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastIds.TUTORIAL_HINT,
                        Component.literal("§a✓ Welcome Back!"),
                        Component.literal("§7Playtime tracking resumed"));
            }
        }
    }
}

