package com.enviouse.sef.invlock;

import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Event handler that blocks locked players from:
 * - Opening containers
 * - Picking up items
 * - Dropping items (via interact events)
 * - Right-clicking items/blocks (to prevent item usage)
 */
@Mod.EventBusSubscriber
public class InvLockEventHandler {

    /** Block opening containers */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!ConfigHandler.config.enableInvLock.get()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            if (InvLockManager.isLocked(player.getUUID())) {
                // Close the container immediately
                player.closeContainer();
                player.sendSystemMessage(TextFormatter.stringToFormattedText(
                    ConfigHandler.config.invLockBlockedMsg.get()));
            }
        }
    }

    /** Block item pickup */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!ConfigHandler.config.enableInvLock.get()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            if (InvLockManager.isLocked(player.getUUID())) {
                event.setCanceled(true);
            }
        }
    }

    /** Block right-click interactions to prevent item usage */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!ConfigHandler.config.enableInvLock.get()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            if (InvLockManager.isLocked(player.getUUID())) {
                event.setCanceled(true);
            }
        }
    }

    /** Block right-click blocks to prevent container opening via interact */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!ConfigHandler.config.enableInvLock.get()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            if (InvLockManager.isLocked(player.getUUID())) {
                event.setCanceled(true);
            }
        }
    }
}

