package com.enviouse.sef.disablebuilding;

import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Event handler that blocks building-disabled players from:
 * - Breaking blocks
 * - Placing blocks
 * - Using block interactions that could result in placement
 */
@Mod.EventBusSubscriber
public class DisableBuildingEventHandler {

    /** Block breaking blocks */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!ConfigHandler.config.enableDisableBuilding.get()) return;
        if (event.getPlayer() instanceof ServerPlayer player) {
            if (DisableBuildingManager.isDisabled(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(TextFormatter.stringToFormattedText(
                    ConfigHandler.config.dbBlockedMsg.get()));
            }
        }
    }

    /** Block placing blocks */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!ConfigHandler.config.enableDisableBuilding.get()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            if (DisableBuildingManager.isDisabled(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(TextFormatter.stringToFormattedText(
                    ConfigHandler.config.dbBlockedMsg.get()));
            }
        }
    }

    /** Block left-click on blocks (mining) */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!ConfigHandler.config.enableDisableBuilding.get()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            if (DisableBuildingManager.isDisabled(player.getUUID())) {
                event.setCanceled(true);
            }
        }
    }
}


