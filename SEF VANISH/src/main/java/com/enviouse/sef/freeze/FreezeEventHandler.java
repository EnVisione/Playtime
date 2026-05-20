package com.enviouse.sef.freeze;

import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Event handler that blocks frozen players from:
 * - Using commands (except chat is allowed via ServerChatEvent which is NOT cancelled here)
 * - Breaking blocks
 * - Placing blocks
 * - Attacking entities
 * - Right-clicking / interacting
 *
 * Movement and looking are handled by FreezeManager.tick() which teleports them back.
 */
@Mod.EventBusSubscriber
public class FreezeEventHandler {

    /**
     * Block commands from frozen players.
     * We allow certain "safe" commands like chat-related ones since the requirement
     * is that frozen players can chat to respond to the admin.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        if (!ConfigHandler.config.enableFreezeSystem.get()) return;

        try {
            ServerPlayer player = event.getParseResults().getContext().getSource().getPlayerOrException();
            if (FreezeManager.isFrozen(player.getUUID())) {
                // Get the command name being executed
                String input = event.getParseResults().getReader().getString().trim();
                // Remove leading slash if present
                if (input.startsWith("/")) input = input.substring(1);
                String commandName = input.split(" ")[0].toLowerCase();

                // Allow chat-related commands if freezeAllowChat is true
                if (ConfigHandler.config.freezeAllowChat.get()) {
                    // Allow messaging commands so frozen players can talk to admins
                    if (commandName.equals("msg") || commandName.equals("r") ||
                        commandName.equals("tell") || commandName.equals("w") ||
                        commandName.equals("reply") || commandName.equals("whisper") ||
                        commandName.equals("helpop") || commandName.equals("ac")) {
                        return; // Allow these
                    }
                }

                // Block everything else
                event.setCanceled(true);
                player.sendSystemMessage(TextFormatter.stringToFormattedText(
                    ConfigHandler.config.freezeCommandBlockedMsg.get()));
            }
        } catch (Exception ignored) {
            // Not a player source, allow it
        }
    }

    /**
     * Block breaking blocks.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!ConfigHandler.config.enableFreezeSystem.get()) return;

        if (event.getPlayer() instanceof ServerPlayer player) {
            if (FreezeManager.isFrozen(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(TextFormatter.stringToFormattedText(
                    ConfigHandler.config.freezeActionBlockedMsg.get()));
            }
        }
    }

    /**
     * Block placing blocks.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!ConfigHandler.config.enableFreezeSystem.get()) return;

        if (event.getEntity() instanceof ServerPlayer player) {
            if (FreezeManager.isFrozen(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(TextFormatter.stringToFormattedText(
                    ConfigHandler.config.freezeActionBlockedMsg.get()));
            }
        }
    }

    /**
     * Block attacking entities.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!ConfigHandler.config.enableFreezeSystem.get()) return;

        if (event.getEntity() instanceof ServerPlayer player) {
            if (FreezeManager.isFrozen(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(TextFormatter.stringToFormattedText(
                    ConfigHandler.config.freezeActionBlockedMsg.get()));
            }
        }
    }

    /**
     * Block right-click interactions.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!ConfigHandler.config.enableFreezeSystem.get()) return;

        if (event.getEntity() instanceof ServerPlayer player) {
            if (FreezeManager.isFrozen(player.getUUID())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!ConfigHandler.config.enableFreezeSystem.get()) return;

        if (event.getEntity() instanceof ServerPlayer player) {
            if (FreezeManager.isFrozen(player.getUUID())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!ConfigHandler.config.enableFreezeSystem.get()) return;

        if (event.getEntity() instanceof ServerPlayer player) {
            if (FreezeManager.isFrozen(player.getUUID())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!ConfigHandler.config.enableFreezeSystem.get()) return;

        if (event.getEntity() instanceof ServerPlayer player) {
            if (FreezeManager.isFrozen(player.getUUID())) {
                event.setCanceled(true);
            }
        }
    }
}

