package com.enviouse.playtime.network;

import com.enviouse.playtime.Playtime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Registers and manages the Playtime mod network channel.
 * Uses optional presence (accepts vanilla) so the mod can run server-only
 * without crashing non-modded clients.
 */
public class PlaytimeNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Playtime.MODID, "main"),
            () -> PROTOCOL_VERSION,
            s -> true,   // client accepts any version (optional client)
            s -> true    // server accepts any version
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.messageBuilder(PlaytimeDataS2CPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlaytimeDataS2CPacket::encode)
                .decoder(PlaytimeDataS2CPacket::new)
                .consumerMainThread(PlaytimeDataS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(ClaimRankC2SPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClaimRankC2SPacket::encode)
                .decoder(ClaimRankC2SPacket::new)
                .consumerMainThread(ClaimRankC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(AdminModifyTimeC2SPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AdminModifyTimeC2SPacket::encode)
                .decoder(AdminModifyTimeC2SPacket::new)
                .consumerMainThread(AdminModifyTimeC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(AdminSetRankC2SPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AdminSetRankC2SPacket::encode)
                .decoder(AdminSetRankC2SPacket::new)
                .consumerMainThread(AdminSetRankC2SPacket::handle)
                .add();
    }

    /** Send a packet to a specific player. */
    public static void sendToPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}

