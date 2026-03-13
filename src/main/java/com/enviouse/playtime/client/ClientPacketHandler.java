package com.enviouse.playtime.client;

import com.enviouse.playtime.network.PlaytimeDataS2CPacket;
import net.minecraft.client.Minecraft;

/**
 * Client-side handler for incoming playtime packets.
 * Isolated in its own class so server-side classloading never touches Minecraft client classes.
 */
public class ClientPacketHandler {

    public static void openPlaytimeScreen(PlaytimeDataS2CPacket packet) {
        Minecraft.getInstance().setScreen(new PlaytimeScreen(packet));
    }
}

