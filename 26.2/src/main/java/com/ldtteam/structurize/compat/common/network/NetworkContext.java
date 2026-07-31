package com.ldtteam.structurize.compat.common.network;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps a handle on the running {@link MinecraftServer}.
 *
 * <p>NeoForge's {@code PacketDistributor.sendToAllPlayers()} could reach the server through
 * {@code ServerLifecycleHooks}; Fabric has no such global, so the common initializer wires this up once and
 * {@link AbstractPlayMessage#sendToAllClients()} reads it back.</p>
 */
public final class NetworkContext
{
    private static @Nullable MinecraftServer server;

    private NetworkContext()
    {
    }

    /**
     * Hooks the server lifecycle. Call once from the common initializer.
     */
    public static void init()
    {
        ServerLifecycleEvents.SERVER_STARTED.register(startedServer -> server = startedServer);
        ServerLifecycleEvents.SERVER_STOPPED.register(stoppedServer -> server = null);
    }

    /**
     * @return the running server, or null when there is none (remote client).
     */
    @Nullable
    public static MinecraftServer getServer()
    {
        return server;
    }
}
