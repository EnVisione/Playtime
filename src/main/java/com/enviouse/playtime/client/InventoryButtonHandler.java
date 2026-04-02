package com.enviouse.playtime.client;

import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.network.PlaytimeNetwork;
import com.enviouse.playtime.network.RequestRefreshC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Adds a small "Playtime" button to the player's inventory screen.
 * Clicking it sends a {@link RequestRefreshC2SPacket} to the server,
 * which responds with the full playtime data packet and opens the GUI.
 */
@OnlyIn(Dist.CLIENT)
public class InventoryButtonHandler {

    private static final int BTN_WIDTH = 50;
    private static final int BTN_HEIGHT = 14;

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen inv)) return;

        // Position the button in the top-left area of the inventory GUI
        // InventoryScreen has guiLeft/guiTop accessible via getGuiLeft()/getGuiTop()
        int x = inv.getGuiLeft() + inv.getXSize() + 2;
        int y = inv.getGuiTop() + 4;

        Button playtimeBtn = Button.builder(
                Component.literal("§6⏱ Playtime"),
                btn -> {
                    // Send packet to server requesting playtime data → opens GUI
                    PlaytimeNetwork.CHANNEL.sendToServer(new RequestRefreshC2SPacket());
                }
        ).bounds(x, y, BTN_WIDTH, BTN_HEIGHT).build();

        event.addListener(playtimeBtn);
    }
}

