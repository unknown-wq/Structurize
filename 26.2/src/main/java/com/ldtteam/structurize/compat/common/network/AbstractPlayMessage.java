package com.ldtteam.structurize.compat.common.network;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Common ancestor of every Structurize play payload — the re-implementation of
 * {@code com.ldtteam.common.network.AbstractPlayMessage} (contract C8).
 *
 * <p>On NeoForge this rode on {@code PayloadRegistrar}. On Fabric a message <em>is</em> a
 * {@link CustomPacketPayload}: {@link PlayMessageType} owns the {@link CustomPacketPayload.Type} and the
 * {@link net.minecraft.network.codec.StreamCodec} that calls {@link #toBytes(RegistryFriendlyByteBuf)} for
 * writing and the message's {@code (buf, type)} constructor for reading.</p>
 */
public abstract class AbstractPlayMessage implements CustomPacketPayload
{
    /**
     * The type this message was created from. Holds the payload id and the codec.
     */
    private final PlayMessageType<?> messageType;

    /**
     * Outgoing message.
     *
     * @param type the static TYPE constant of the concrete message class.
     */
    protected AbstractPlayMessage(final PlayMessageType<?> type)
    {
        this.messageType = type;
    }

    /**
     * Incoming message. The buffer is read by the subclass constructor, not here.
     *
     * @param buf  network buffer, positioned right after the payload id.
     * @param type the static TYPE constant of the concrete message class.
     */
    protected AbstractPlayMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        this.messageType = type;
    }

    /**
     * Serialize everything the matching {@code (buf, type)} constructor reads back.
     *
     * @param buf network buffer.
     */
    protected abstract void toBytes(RegistryFriendlyByteBuf buf);

    /**
     * @return the message type.
     */
    public PlayMessageType<?> getMessageType()
    {
        return messageType;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
    {
        return messageType.payloadType();
    }

    /**
     * Executed on the logical client. Overridden by {@code AbstractClientPlayMessage} and by both-sided messages.
     *
     * @param context message context.
     * @param player  the local player.
     */
    protected void onClientExecute(final PlayMessageContext context, final Player player)
    {
        // no-op by default
    }

    /**
     * Executed on the logical server. Overridden by {@code AbstractServerPlayMessage} and by both-sided messages.
     *
     * @param context message context.
     * @param player  the sending player.
     */
    protected void onServerExecute(final PlayMessageContext context, final ServerPlayer player)
    {
        // no-op by default
    }

    /**
     * Entry point used by the receivers registered in {@link PlayMessageType}.
     *
     * @param context message context.
     * @param player  the player of the receiving side.
     */
    final void execute(final PlayMessageContext context, final Player player)
    {
        if (context.isClientSide())
        {
            onClientExecute(context, player);
        }
        else if (player instanceof final ServerPlayer serverPlayer)
        {
            onServerExecute(context, serverPlayer);
        }
    }

    /**
     * Client to server. Only callable from the logical client.
     */
    public void sendToServer()
    {
        ClientMessageSender.sendToServer(this);
    }

    /**
     * Server to one client.
     *
     * @param player the receiver.
     */
    public void sendToPlayer(final ServerPlayer player)
    {
        ServerPlayNetworking.send(player, this);
    }

    /**
     * Server to every connected client.
     */
    public void sendToAllClients()
    {
        final MinecraftServer server = NetworkContext.getServer();
        if (server == null)
        {
            return;
        }
        for (final ServerPlayer player : PlayerLookup.all(server))
        {
            ServerPlayNetworking.send(player, this);
        }
    }
}
