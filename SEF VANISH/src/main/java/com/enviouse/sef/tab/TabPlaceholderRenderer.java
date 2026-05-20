package com.enviouse.sef.tab;

import com.enviouse.sef.ServerEssentialsForge;
import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Renders custom tab-list header/footer with placeholder replacement.
 * Placeholders: {server_ip}, {online}, {max}
 */
public class TabPlaceholderRenderer {

    public static void applyHeaderFooter(MinecraftServer server) {
        if (!ConfigHandler.config.enableCustomTabHeaderFooter.get()) return;

        String headerFormat = ConfigHandler.config.tabHeaderFormat.get();
        String footerFormat = ConfigHandler.config.tabFooterFormat.get();

        if (headerFormat.isEmpty() && footerFormat.isEmpty()) return;

        String online = String.valueOf(server.getPlayerCount());
        String max = String.valueOf(server.getMaxPlayers());
        String ip = server.getLocalIp() != null && !server.getLocalIp().isEmpty() ? server.getLocalIp() : "localhost";

        String header = headerFormat
            .replace("{server_ip}", ip)
            .replace("{online}", online)
            .replace("{max}", max);

        String footer = footerFormat
            .replace("{server_ip}", ip)
            .replace("{online}", online)
            .replace("{max}", max);

        Component headerComponent = header.isEmpty() ? Component.empty() : TextFormatter.stringToFormattedText(header);
        Component footerComponent = footer.isEmpty() ? Component.empty() : TextFormatter.stringToFormattedText(footer);

        ClientboundTabListPacket packet = new ClientboundTabListPacket(headerComponent, footerComponent);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }
}

