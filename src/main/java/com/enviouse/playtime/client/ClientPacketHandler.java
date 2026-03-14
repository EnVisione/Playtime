package com.enviouse.playtime.client;

import com.enviouse.playtime.network.PlaytimeDataS2CPacket;
import net.minecraft.client.Minecraft;

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
        if (mc.screen instanceof PlaytimeScreen screen) {
            screen.updateAfkStatus(playerUuid, afk);
        }
    }
}

