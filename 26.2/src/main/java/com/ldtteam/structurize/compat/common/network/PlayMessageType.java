package com.ldtteam.structurize.compat.common.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Re-implementation of {@code com.ldtteam.common.network.PlayMessageType} (contract C8).
 *
 * <p>On NeoForge this wrapped {@code PayloadRegistrar}. On Fabric it owns three things per message class:
 * the {@link CustomPacketPayload.Type} (id), the {@link StreamCodec} that bridges the message's
 * {@code toBytes} / {@code (buf, type)} pair, and the direction. {@link #register()} publishes the codec to
 * {@link PayloadTypeRegistry} and — for serverbound messages — installs the server receiver;
 * {@link #registerClientReceivers()} installs the client receivers and must be called from the client
 * entrypoint, because {@code ClientPlayNetworking} does not exist on a dedicated server.</p>
 *
 * @param <M> concrete message type.
 */
public class PlayMessageType<M extends AbstractPlayMessage>
{
    /**
     * Every type that went through {@link #register()}, in registration order. Used by
     * {@link #registerClientReceivers()}.
     */
    private static final List<PlayMessageType<?>> REGISTERED = new ArrayList<>();

    /**
     * Reads a message off the wire.
     *
     * @param <M> concrete message type.
     */
    @FunctionalInterface
    public interface MessageFactory<M extends AbstractPlayMessage>
    {
        /**
         * @param buf  network buffer, positioned right after the payload id.
         * @param type the type this message belongs to.
         * @return the decoded message.
         */
        M create(RegistryFriendlyByteBuf buf, PlayMessageType<?> type);
    }

    private final CustomPacketPayload.Type<M> payloadType;
    private final StreamCodec<RegistryFriendlyByteBuf, M> streamCodec;
    private final boolean serverbound;
    private final boolean clientbound;

    private PlayMessageType(final String modId,
        final String name,
        final MessageFactory<M> factory,
        final boolean serverbound,
        final boolean clientbound)
    {
        this.payloadType = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(modId, name));
        this.streamCodec = StreamCodec.of((buf, msg) -> msg.toBytes(buf), buf -> factory.create(buf, this));
        this.serverbound = serverbound;
        this.clientbound = clientbound;
    }

    /**
     * Client to server message.
     *
     * @param modId   owning mod id.
     * @param name    payload path.
     * @param factory the {@code (buf, type)} constructor of the message class.
     * @param <M>     concrete message type.
     * @return the type.
     */
    public static <M extends AbstractServerPlayMessage> PlayMessageType<M> forServer(final String modId,
        final String name,
        final MessageFactory<M> factory)
    {
        return new PlayMessageType<>(modId, name, factory, true, false);
    }

    /**
     * Server to client message.
     *
     * @param modId   owning mod id.
     * @param name    payload path.
     * @param factory the {@code (buf, type)} constructor of the message class.
     * @param <M>     concrete message type.
     * @return the type.
     */
    public static <M extends AbstractClientPlayMessage> PlayMessageType<M> forClient(final String modId,
        final String name,
        final MessageFactory<M> factory)
    {
        return new PlayMessageType<>(modId, name, factory, false, true);
    }

    /**
     * Message that travels in both directions.
     *
     * @param modId   owning mod id.
     * @param name    payload path.
     * @param factory the {@code (buf, type)} constructor of the message class.
     * @param <M>     concrete message type.
     * @return the type.
     */
    public static <M extends AbstractPlayMessage> PlayMessageType<M> forBothSides(final String modId,
        final String name,
        final MessageFactory<M> factory)
    {
        return new PlayMessageType<>(modId, name, factory, true, true);
    }

    /**
     * @return the vanilla payload type.
     */
    public CustomPacketPayload.Type<M> payloadType()
    {
        return payloadType;
    }

    /**
     * @return the payload id.
     */
    public Identifier id()
    {
        return payloadType.id();
    }

    /**
     * @return true when this message may travel client to server.
     */
    public boolean isServerbound()
    {
        return serverbound;
    }

    /**
     * @return true when this message may travel server to client.
     */
    public boolean isClientbound()
    {
        return clientbound;
    }

    /**
     * Publishes the codec and installs the server receiver. Call once per message type from the common
     * initializer, before any packet can be sent.
     */
    public void register()
    {
        if (serverbound)
        {
            PayloadTypeRegistry.serverboundPlay().register(payloadType, streamCodec);
            ServerPlayNetworking.registerGlobalReceiver(payloadType,
                (payload, context) -> payload.execute(new ServerContext(context.server(), context.player()), context.player()));
        }
        if (clientbound)
        {
            PayloadTypeRegistry.clientboundPlay().register(payloadType, streamCodec);
        }
        REGISTERED.add(this);
    }

    /**
     * Installs the client receivers for every clientbound type registered so far. Called from the client
     * entrypoint only — see {@link ClientMessageSender}.
     */
    public static void registerClientReceivers()
    {
        for (final PlayMessageType<?> type : REGISTERED)
        {
            if (type.clientbound)
            {
                ClientMessageSender.registerReceiver(type.payloadType);
            }
        }
    }

    /**
     * Server side context handed to {@code onServerExecute}.
     */
    private record ServerContext(MinecraftServer minecraftServer, ServerPlayer serverPlayer) implements PlayMessageContext
    {
        @Override
        public @Nullable Player player()
        {
            return serverPlayer;
        }

        @Override
        public boolean isClientSide()
        {
            return false;
        }

        @Override
        public @Nullable MinecraftServer server()
        {
            return minecraftServer;
        }
    }
}
