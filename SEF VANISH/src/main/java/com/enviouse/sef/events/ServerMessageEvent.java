package com.enviouse.sef.events;

import com.enviouse.sef.vanish.VanishUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class ServerMessageEvent {
    public static void sendMessage(Player player, MutableComponent message) {
        sendMessage(player, message, false);
    }
    public static void sendMessage(Player player, MutableComponent message, boolean emptyline) {
        if (emptyline) {
            player.sendSystemMessage(Component.literal(""));
        }
        player.sendSystemMessage(message);
    }
    public static void broadcastMessage(Level world, MutableComponent message) {
        MinecraftServer server = world.getServer();
        if (server != null) {

            for (ServerPlayer serverPlayer : server.getPlayerList().getPlayers()) {
                sendMessage((Player) serverPlayer, message);
            }

        }
    }

    /**
     * Broadcast a chat message with vanish awareness.
     * If the sender is vanished, only players who can see the sender receive the message.
     * If the sender is not vanished, sends to all players (normal behavior).
     */
    public static void broadcastMessageVanishAware(Level world, MutableComponent message, ServerPlayer sender) {
        MinecraftServer server = world.getServer();
        if (server == null) return;

        boolean senderVanished = VanishUtil.isVanished(sender);

        for (ServerPlayer receiver : server.getPlayerList().getPlayers()) {
            if (senderVanished) {
                // Only send to the sender themselves or players who can see them
                boolean receiverVanished = VanishUtil.isVanished(receiver);
                if (receiver.equals(sender) || VanishUtil.playerAllowedToSeeOther(receiver, sender, receiverVanished, true)) {
                    sendMessage((Player) receiver, message);
                }
            } else {
                sendMessage((Player) receiver, message);
            }
        }
    }
}