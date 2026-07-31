package com.ldtteam.structurize.compat.common.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Client half of the re-implemented {@code com.ldtteam.common} network layer (contract C8).
 *
 * <p>Everything that touches {@code net.fabricmc.fabric.api.client.networking.v1} lives here and nowhere
 * else: on a dedicated server the fabric-api client modules are absent, so this class must never be
 * class-loaded there. The only references to it are {@link AbstractPlayMessage#sendToServer()} (only ever
 * called from the logical client) and {@link PlayMessageType#registerClientReceivers()} (only ever called
 * from the client entrypoint).</p>
 */
final class ClientMessageSender
{
    private ClientMessageSender()
    {
    }

    /**
     * @param message the message to send to the server.
     */
    static void sendToServer(final AbstractPlayMessage message)
    {
        ClientPlayNetworking.send(message);
    }

    /**
     * @param type the clientbound payload type to install a receiver for.
     * @param <T>  concrete message type.
     */
    static <T extends AbstractPlayMessage> void registerReceiver(final CustomPacketPayload.Type<T> type)
    {
        ClientPlayNetworking.registerGlobalReceiver(type,
            (payload, context) -> payload.execute(new ClientContext(context.client()), context.player()));
    }

    /**
     * Client side context handed to {@code onClientExecute}.
     */
    private record ClientContext(Minecraft client) implements PlayMessageContext
    {
        @Override
        public @Nullable Player player()
        {
            return client.player;
        }

        @Override
        public boolean isClientSide()
        {
            return true;
        }

        @Override
        public @Nullable MinecraftServer server()
        {
            return client.getSingleplayerServer();
        }
    }
}
