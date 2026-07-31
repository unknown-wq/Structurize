package com.ldtteam.structurize.compat.common.config;

import com.ldtteam.structurize.compat.common.config.ModConfigSpec.BooleanValue;
import com.ldtteam.structurize.compat.common.config.ModConfigSpec.Builder;
import com.ldtteam.structurize.compat.common.config.ModConfigSpec.ConfigValue;
import com.ldtteam.structurize.compat.common.config.ModConfigSpec.DoubleValue;
import com.ldtteam.structurize.compat.common.config.ModConfigSpec.EnumValue;
import com.ldtteam.structurize.compat.common.config.ModConfigSpec.IntValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Re-implementation of {@code com.ldtteam.common.config.AbstractConfiguration} (contract C8).
 *
 * <p>The API is reconstructed from its two call sites, {@code config/ClientConfiguration} and
 * {@code config/ServerConfiguration}: nested categories via
 * {@link #createCategory(String)} / {@link #swapToCategory(String)} / {@link #finishCategory()}, five
 * {@code defineXxx} helpers, and two flavours of {@code addWatcher}.</p>
 */
public abstract class AbstractConfiguration
{
    /**
     * Builder handed in by {@link Configurations}.
     */
    protected final Builder builder;

    /**
     * Owning mod id — prefixes the translation keys, exactly as on NeoForge.
     */
    protected final String modId;

    /**
     * Watchers keyed by the value they listen on. Fired from {@link Configurations#set(ConfigValue, Object)}.
     */
    private final Map<ConfigValue<?>, List<BiConsumer<Object, Object>>> watchers = new HashMap<>();

    /**
     * @param builder the spec builder.
     * @param modId   owning mod id.
     */
    protected AbstractConfiguration(final Builder builder, final String modId)
    {
        this.builder = builder;
        this.modId = modId;
    }

    /**
     * Enter a nested category.
     *
     * @param name category name.
     */
    protected void createCategory(final String name)
    {
        builder.push(name);
    }

    /**
     * Leave the current category and enter a sibling one.
     *
     * @param name the sibling category name.
     */
    protected void swapToCategory(final String name)
    {
        builder.pop();
        builder.push(name);
    }

    /**
     * Leave the current category.
     */
    protected void finishCategory()
    {
        builder.pop();
    }

    /**
     * @param name         value name.
     * @param defaultValue default.
     * @return the registered value.
     */
    protected BooleanValue defineBoolean(final String name, final boolean defaultValue)
    {
        return builder.add(new BooleanValue(builder.path(name), defaultValue));
    }

    /**
     * @param name         value name.
     * @param defaultValue default.
     * @param min          inclusive lower bound.
     * @param max          inclusive upper bound.
     * @return the registered value.
     */
    protected IntValue defineInteger(final String name, final int defaultValue, final int min, final int max)
    {
        return builder.add(new IntValue(builder.path(name), defaultValue, min, max));
    }

    /**
     * @param name         value name.
     * @param defaultValue default.
     * @return the registered value.
     */
    protected IntValue defineInteger(final String name, final int defaultValue)
    {
        return defineInteger(name, defaultValue, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /**
     * @param name         value name.
     * @param defaultValue default.
     * @param min          inclusive lower bound.
     * @param max          inclusive upper bound.
     * @return the registered value.
     */
    protected DoubleValue defineDouble(final String name, final double defaultValue, final double min, final double max)
    {
        return builder.add(new DoubleValue(builder.path(name), defaultValue, min, max));
    }

    /**
     * @param name         value name.
     * @param defaultValue default.
     * @return the registered value.
     */
    protected ConfigValue<String> defineString(final String name, final String defaultValue)
    {
        return builder.add(new ConfigValue<>(builder.path(name), defaultValue));
    }

    /**
     * @param name         value name.
     * @param defaultValue default.
     * @param <E>          enum type.
     * @return the registered value.
     */
    protected <E extends Enum<E>> EnumValue<E> defineEnum(final String name, final E defaultValue)
    {
        return builder.add(new EnumValue<>(builder.path(name), defaultValue));
    }

    /**
     * Run {@code action} whenever any of {@code values} changes.
     *
     * @param action what to run.
     * @param values the values to listen on.
     */
    protected void addWatcher(final Runnable action, final ConfigValue<?>... values)
    {
        for (final ConfigValue<?> value : values)
        {
            watchers.computeIfAbsent(value, k -> new ArrayList<>()).add((oldValue, newValue) -> action.run());
        }
    }

    /**
     * Run {@code action} with (oldValue, newValue) whenever {@code value} changes.
     *
     * @param value  the value to listen on.
     * @param action the listener.
     * @param <T>    value type.
     */
    @SuppressWarnings("unchecked")
    protected <T> void addWatcher(final ConfigValue<T> value, final BiConsumer<T, T> action)
    {
        watchers.computeIfAbsent(value, k -> new ArrayList<>())
            .add((oldValue, newValue) -> action.accept((T) oldValue, (T) newValue));
    }

    /**
     * Fires the watchers of one value. Called by {@link Configurations}.
     *
     * @param value    the value that changed.
     * @param oldValue previous value.
     * @param newValue new value.
     */
    void fireWatchers(final ConfigValue<?> value, final Object oldValue, final Object newValue)
    {
        final List<BiConsumer<Object, Object>> list = watchers.get(value);
        if (list != null)
        {
            for (final BiConsumer<Object, Object> watcher : list)
            {
                watcher.accept(oldValue, newValue);
            }
        }
    }
}
