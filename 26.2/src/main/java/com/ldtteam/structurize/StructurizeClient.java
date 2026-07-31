package com.ldtteam.structurize;

import com.ldtteam.structurize.client.ModKeyMappings;
import com.ldtteam.structurize.compat.common.network.PlayMessageType;
import com.ldtteam.structurize.event.ClientEventSubscriber;
import com.ldtteam.structurize.event.ClientLifecycleSubscriber;
import com.ldtteam.structurize.storage.ClientFutureProcessor;
import com.ldtteam.structurize.storage.ClientStructurePackLoader;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint — the {@code client} entrypoint declared in {@code fabric.mod.json} (contract C5).
 *
 * <p>New file: NeoForge only needed a {@code FMLEnvironment.dist.isClient()} branch inside the mod
 * constructor, Fabric requires a separate {@link ClientModInitializer} so that no client-only class is ever
 * loaded on a dedicated server. Everything that touches {@code net.minecraft.client} or
 * {@code net.fabricmc.fabric.api.client} must be reached from here and nowhere else.</p>
 *
 * <p>Runs after {@link Structurize#onInitialize()}, which is why the clientbound receivers can be installed
 * from the list {@link PlayMessageType} filled during common init.</p>
 */
public class StructurizeClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        PlayMessageType.registerClientReceivers();

        ClientLifecycleSubscriber.register();
        ClientEventSubscriber.register();

        ClientStructurePackLoader.register();
        ClientFutureProcessor.register();

        ModKeyMappings.init();
    }
}
