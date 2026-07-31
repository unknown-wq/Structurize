package com.ldtteam.structurize.compat.common.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Replacement for NeoForge's {@code IPayloadContext} inside the re-implemented {@code com.ldtteam.common}
 * network layer (contract C8).
 *
 * <p>Fabric hands the receiver a side-specific context object ({@code ServerPlayNetworking.Context} /
 * {@code ClientPlayNetworking.Context}); this interface is the small common denominator the 25 Structurize
 * messages actually need. None of them reads anything off the context today — the parameter is kept only so
 * that the {@code onExecute} signatures stay recognisable against the 1.21.1 sources.</p>
 */
public interface PlayMessageContext
{
    /**
     * @return the player this message belongs to; the sending player on the server, the local player on the client.
     */
    @Nullable
    Player player();

    /**
     * @return true when the handler runs on the logical client.
     */
    boolean isClientSide();

    /**
     * @return the running server, or null on a remote client.
     */
    @Nullable
    MinecraftServer server();

    /**
     * Fabric runs play-payload receivers on the main thread already, so this simply runs the task inline.
     * Kept for signature compatibility with NeoForge's {@code IPayloadContext#enqueueWork}.
     *
     * @param task the task to run.
     */
    default void enqueueWork(final Runnable task)
    {
        task.run();
    }
}
