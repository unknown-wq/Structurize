package com.ldtteam.structurize.compat.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ldtteam.structurize.api.Log;
import com.ldtteam.structurize.compat.common.config.ModConfigSpec.Builder;
import com.ldtteam.structurize.compat.common.config.ModConfigSpec.ConfigValue;
import com.ldtteam.structurize.compat.common.config.ModConfigSpec.EnumValue;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Re-implementation of {@code com.ldtteam.common.config.Configurations} (contract C8).
 *
 * <p>NeoForge registered {@code ModConfigSpec}s on the mod bus and persisted them as TOML. Fabric has no
 * config system at all, so this class owns the whole cycle: build the specs, read/write one JSON file per
 * side under the loader's config directory, and fire the watchers registered by
 * {@link AbstractConfiguration#addWatcher}.</p>
 *
 * <p>TODO(port-26.2): DEGRADED — the server configuration is not synced to connecting clients. On NeoForge
 * {@code ModConfigSpec} of type SERVER was shipped to the client on login; here every side reads its own
 * file, so a client evaluating {@code getServer()} sees its local values. Everything Structurize decides
 * server-side (placement limits, undo cache, teleport) is evaluated on the server and is therefore correct;
 * only client-side previews of those numbers can disagree with a remote server.</p>
 *
 * @param <C> client configuration type.
 * @param <S> server configuration type.
 * @param <W> common ("world") configuration type; Structurize passes none.
 */
public class Configurations<C extends AbstractConfiguration, S extends AbstractConfiguration, W extends AbstractConfiguration>
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String modId;

    private final @Nullable C client;
    private final @Nullable S server;
    private final @Nullable W common;

    private final @Nullable ModConfigSpec clientSpec;
    private final @Nullable ModConfigSpec serverSpec;
    private final @Nullable ModConfigSpec commonSpec;

    /**
     * Builds, loads and saves every configuration of one mod.
     *
     * @param modId         owning mod id, also the config file name prefix.
     * @param clientFactory client configuration constructor, or null.
     * @param serverFactory server configuration constructor, or null.
     * @param commonFactory common configuration constructor, or null.
     */
    public Configurations(final String modId,
        final @Nullable Function<Builder, C> clientFactory,
        final @Nullable Function<Builder, S> serverFactory,
        final @Nullable Function<Builder, W> commonFactory)
    {
        this.modId = modId;

        final Builder clientBuilder = new Builder();
        final Builder serverBuilder = new Builder();
        final Builder commonBuilder = new Builder();

        this.client = clientFactory == null ? null : clientFactory.apply(clientBuilder);
        this.server = serverFactory == null ? null : serverFactory.apply(serverBuilder);
        this.common = commonFactory == null ? null : commonFactory.apply(commonBuilder);

        this.clientSpec = client == null ? null : clientBuilder.build();
        this.serverSpec = server == null ? null : serverBuilder.build();
        this.commonSpec = common == null ? null : commonBuilder.build();

        load("client", clientSpec);
        load("server", serverSpec);
        load("common", commonSpec);
    }

    /**
     * @return the client configuration, or null when the mod declares none.
     */
    @Nullable
    public C getClient()
    {
        return client;
    }

    /**
     * @return the server configuration, or null when the mod declares none.
     */
    @Nullable
    public S getServer()
    {
        return server;
    }

    /**
     * @return the common configuration, or null when the mod declares none.
     */
    @Nullable
    public W getCommon()
    {
        return common;
    }

    /**
     * Change a value, fire its watchers and persist the owning file.
     *
     * @param value    the value to change.
     * @param newValue the new value.
     * @param <T>      value type.
     */
    public <T> void set(final ConfigValue<T> value, final T newValue)
    {
        final T oldValue = value.get();
        if (java.util.Objects.equals(oldValue, newValue))
        {
            return;
        }
        value.setRaw(newValue);

        for (final AbstractConfiguration configuration : configurations())
        {
            configuration.fireWatchers(value, oldValue, newValue);
        }

        if (clientSpec != null && clientSpec.getValues().containsKey(value.getPath()))
        {
            save("client", clientSpec);
        }
        if (serverSpec != null && serverSpec.getValues().containsKey(value.getPath()))
        {
            save("server", serverSpec);
        }
        if (commonSpec != null && commonSpec.getValues().containsKey(value.getPath()))
        {
            save("common", commonSpec);
        }
    }

    private List<AbstractConfiguration> configurations()
    {
        final List<AbstractConfiguration> list = new ArrayList<>(3);
        if (client != null)
        {
            list.add(client);
        }
        if (server != null)
        {
            list.add(server);
        }
        if (common != null)
        {
            list.add(common);
        }
        return list;
    }

    private Path fileOf(final String side)
    {
        return FabricLoader.getInstance().getConfigDir().resolve(modId + "-" + side + ".json");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void load(final String side, final @Nullable ModConfigSpec spec)
    {
        if (spec == null)
        {
            return;
        }

        final Path path = fileOf(side);
        if (Files.exists(path))
        {
            try (Reader reader = Files.newBufferedReader(path))
            {
                final JsonElement root = JsonParser.parseReader(reader);
                if (root.isJsonObject())
                {
                    final JsonObject object = root.getAsJsonObject();
                    for (final Map.Entry<String, ConfigValue<?>> entry : spec.getValues().entrySet())
                    {
                        final JsonElement element = object.get(entry.getKey());
                        if (element != null && !element.isJsonNull())
                        {
                            applyJson((ConfigValue) entry.getValue(), element);
                        }
                    }
                }
            }
            catch (final IOException | RuntimeException e)
            {
                Log.getLogger().error("Could not read config file " + path + ", falling back to defaults", e);
            }
        }

        save(side, spec);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applyJson(final ConfigValue value, final JsonElement element)
    {
        final Object defaultValue = value.getDefault();
        final Object parsed;
        if (defaultValue instanceof Boolean)
        {
            parsed = element.getAsBoolean();
        }
        else if (defaultValue instanceof Integer)
        {
            parsed = element.getAsInt();
        }
        else if (defaultValue instanceof Double)
        {
            parsed = element.getAsDouble();
        }
        else if (value instanceof final EnumValue enumValue)
        {
            parsed = Enum.valueOf(enumValue.getEnumClass(), element.getAsString());
        }
        else
        {
            parsed = element.getAsString();
        }
        value.setRaw(value.correct(parsed));
    }

    private void save(final String side, final @Nullable ModConfigSpec spec)
    {
        if (spec == null)
        {
            return;
        }

        final Path path = fileOf(side);
        final JsonObject object = new JsonObject();
        for (final Map.Entry<String, ConfigValue<?>> entry : spec.getValues().entrySet())
        {
            final Object current = entry.getValue().get();
            if (current instanceof final Boolean bool)
            {
                object.addProperty(entry.getKey(), bool);
            }
            else if (current instanceof final Number number)
            {
                object.addProperty(entry.getKey(), number);
            }
            else if (current instanceof final Enum<?> enumValue)
            {
                object.addProperty(entry.getKey(), enumValue.name());
            }
            else
            {
                object.addProperty(entry.getKey(), String.valueOf(current));
            }
        }

        try
        {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path))
            {
                GSON.toJson(object, writer);
            }
        }
        catch (final IOException e)
        {
            Log.getLogger().error("Could not write config file " + path, e);
        }
    }
}
