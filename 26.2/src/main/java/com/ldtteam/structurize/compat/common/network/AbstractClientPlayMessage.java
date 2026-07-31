package com.ldtteam.structurize.compat.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * A play payload that only ever travels server to client — re-implementation of
 * {@code com.ldtteam.common.network.AbstractClientPlayMessage} (contract C8).
 */
public abstract class AbstractClientPlayMessage extends AbstractPlayMessage
{
    /**
     * Outgoing message.
     *
     * @param type the static TYPE constant of the concrete message class.
     */
    protected AbstractClientPlayMessage(final PlayMessageType<?> type)
    {
        super(type);
    }

    /**
     * Incoming message.
     *
     * @param buf  network buffer.
     * @param type the static TYPE constant of the concrete message class.
     */
    protected AbstractClientPlayMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
    }

    /**
     * Handle this message on the client.
     *
     * @param context message context.
     * @param player  the local player.
     */
    protected abstract void onExecute(PlayMessageContext context, Player player);

    @Override
    protected final void onClientExecute(final PlayMessageContext context, final Player player)
    {
        onExecute(context, player);
    }

    @Override
    protected final void onServerExecute(final PlayMessageContext context, final ServerPlayer player)
    {
        throw new UnsupportedOperationException(getClass().getName() + " is clientbound only");
    }
}
