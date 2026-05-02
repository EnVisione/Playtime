package com.enviouse.playtime.network;

import com.enviouse.playtime.Playtime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ConnectionData;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Registers and manages the Playtime mod network channel.
 * Uses optional presence (accepts vanilla) so the mod can run server-only
 * without crashing non-modded clients.
 */
@SuppressWarnings({"deprecation", "removal"}) // ResourceLocation constructor is the correct API for 1.20.1
public class PlaytimeNetwork {

    private static final String PROTOCOL_VERSION = "1.2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Playtime.MODID, "main"),
            () -> PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),  // client: accept matching version or no mod (vanilla server)
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION)   // server: accept matching version or no mod (vanilla client)
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

        CHANNEL.messageBuilder(AfkSyncS2CPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AfkSyncS2CPacket::encode)
                .decoder(AfkSyncS2CPacket::new)
                .consumerMainThread(AfkSyncS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(SetDisplayRankC2SPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetDisplayRankC2SPacket::encode)
                .decoder(SetDisplayRankC2SPacket::new)
                .consumerMainThread(SetDisplayRankC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(RequestRefreshC2SPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestRefreshC2SPacket::encode)
                .decoder(RequestRefreshC2SPacket::new)
                .consumerMainThread(RequestRefreshC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(PlayerSearchC2SPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PlayerSearchC2SPacket::encode)
                .decoder(PlayerSearchC2SPacket::new)
                .consumerMainThread(PlayerSearchC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(PlayerSearchResultS2CPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlayerSearchResultS2CPacket::encode)
                .decoder(PlayerSearchResultS2CPacket::new)
                .consumerMainThread(PlayerSearchResultS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(ClientActivitySignalC2SPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientActivitySignalC2SPacket::encode)
                .decoder(ClientActivitySignalC2SPacket::new)
                .consumerMainThread(ClientActivitySignalC2SPacket::handle)
                .add();
    }

    /** Send a packet to a specific player. */
    public static void sendToPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    /**
     * Check whether a player's client has the Playtime mod network channel registered.
     * If the client is vanilla (no mod), this returns false and we should send text fallback.
     */
    public static boolean hasModChannel(ServerPlayer player) {
        try {
            ResourceLocation channelName = new ResourceLocation(Playtime.MODID, "main");
            ConnectionData data = NetworkHooks.getConnectionData(player.connection.connection);
            if (data != null) {
                return data.getChannels().containsKey(channelName);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}

