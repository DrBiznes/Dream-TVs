package com.dreamtvs.net;

import com.dreamtvs.DreamTvs;
import com.dreamtvs.config.DreamTvsConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client, when a player joins: the server's {@code remoteRange}. The remote works on the client (it goes
 * through Dream Displays' client API), so the client is the one that checks the range ({@code RemoteClient}).
 * Each loader registers the payload and sends it; this class holds what the client last received.
 */
public record RemoteRangePayload(int range) implements CustomPacketPayload {
    public static final Type<RemoteRangePayload> TYPE = new Type<>(DreamTvs.id("remote_range"));
    public static final StreamCodec<ByteBuf, RemoteRangePayload> CODEC =
            ByteBufCodecs.VAR_INT.map(RemoteRangePayload::new, RemoteRangePayload::range);

    /** The range the client uses, in blocks; 0 = unlimited. */
    private static volatile int clientRange;

    /** What the server sends: its config's range. */
    public static RemoteRangePayload fromConfig() {
        return new RemoteRangePayload(DreamTvsConfig.get().remoteRange);
    }

    /** On the client, when the payload arrives (or the settings screen changed this game's own world). */
    public static void receive(RemoteRangePayload payload) {
        clientRange = Math.max(0, payload.range());
    }

    public static int clientRange() {
        return clientRange;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
