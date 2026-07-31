package com.ldtteam.structurize.compat.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * A play payload that only ever travels client to server — re-implementation of
 * {@code com.ldtteam.common.network.AbstractServerPlayMessage} (contract C8).
 */
public abstract class AbstractServerPlayMessage extends AbstractPlayMessage
{
    /**
     * Outgoing message.
     *
     * @param type the static TYPE constant of the concrete message class.
     */
    protected AbstractServerPlayMessage(final PlayMessageType<?> type)
    {
        super(type);
    }

    /**
     * Incoming message.
     *
     * @param buf  network buffer.
     * @param type the static TYPE constant of the concrete message class.
     */
    protected AbstractServerPlayMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
    }

    /**
     * Handle this message on the server.
     *
     * @param context message context.
     * @param player  the sending player.
     */
    protected abstract void onExecute(PlayMessageContext context, ServerPlayer player);

    @Override
    protected final void onServerExecute(final PlayMessageContext context, final ServerPlayer player)
    {
        onExecute(context, player);
    }

    @Override
    protected final void onClientExecute(final PlayMessageContext context, final Player player)
    {
        throw new UnsupportedOperationException(getClass().getName() + " is serverbound only");
    }
}
