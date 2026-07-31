package com.ldtteam.structurize.compat.common.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Fabric stand-in for NeoForge's {@code net.neoforged.neoforge.common.ModConfigSpec} (contract C8).
 *
 * <p>Only the shapes the two Structurize configuration classes actually use are reproduced: a
 * {@link Builder} that collects typed {@link ConfigValue}s under dotted category paths, and the four value
 * subclasses the field declarations name ({@link BooleanValue}, {@link IntValue}, {@link DoubleValue},
 * {@link EnumValue}). Persistence lives in {@link Configurations}; this class is pure structure, so it is
 * safe to touch from either side.</p>
 */
public final class ModConfigSpec
{
    private final Map<String, ConfigValue<?>> values;

    private ModConfigSpec(final Map<String, ConfigValue<?>> values)
    {
        this.values = values;
    }

    /**
     * @return every value in this spec, keyed by its full dotted path.
     */
    public Map<String, ConfigValue<?>> getValues()
    {
        return values;
    }

    /**
     * Collects the values of one configuration file.
     */
    public static final class Builder
    {
        private final Map<String, ConfigValue<?>> values = new LinkedHashMap<>();
        private final List<String> categories = new ArrayList<>();

        /**
         * Enter a nested category.
         *
         * @param name category name.
         */
        public void push(final String name)
        {
            categories.add(name);
        }

        /**
         * Leave the innermost category.
         */
        public void pop()
        {
            if (!categories.isEmpty())
            {
                categories.remove(categories.size() - 1);
            }
        }

        /**
         * @param name value name.
         * @return the full dotted path of a value declared right now.
         */
        public String path(final String name)
        {
            return categories.isEmpty() ? name : String.join(".", categories) + "." + name;
        }

        /**
         * Registers a value under the current category.
         *
         * @param value the value.
         * @param <V>   value holder type.
         * @return the same value, for chaining.
         */
        public <V extends ConfigValue<?>> V add(final V value)
        {
            values.put(value.getPath(), value);
            return value;
        }

        /**
         * @return the finished spec.
         */
        public ModConfigSpec build()
        {
            return new ModConfigSpec(values);
        }
    }

    /**
     * One configuration entry.
     *
     * @param <T> value type.
     */
    public static class ConfigValue<T> implements Supplier<T>
    {
        private final String path;
        private final T defaultValue;
        private T value;

        ConfigValue(final String path, final T defaultValue)
        {
            this.path = path;
            this.defaultValue = defaultValue;
            this.value = defaultValue;
        }

        /**
         * @return full dotted path, used as the key in the config file.
         */
        public String getPath()
        {
            return path;
        }

        /**
         * @return the value as declared in code.
         */
        public T getDefault()
        {
            return defaultValue;
        }

        @Override
        public T get()
        {
            return value;
        }

        /**
         * Raw setter. Prefer {@link Configurations#set(ConfigValue, Object)} — it also fires watchers and saves.
         *
         * @param newValue the new value.
         */
        public void setRaw(final T newValue)
        {
            this.value = newValue;
        }

        /**
         * Clamp / sanity check applied when a value is loaded from disk.
         *
         * @param candidate the parsed value.
         * @return the value that should actually be stored.
         */
        public T correct(final T candidate)
        {
            return candidate;
        }
    }

    /**
     * Boolean entry.
     */
    public static final class BooleanValue extends ConfigValue<Boolean>
    {
        BooleanValue(final String path, final boolean defaultValue)
        {
            super(path, defaultValue);
        }
    }

    /**
     * Ranged integer entry.
     */
    public static final class IntValue extends ConfigValue<Integer>
    {
        private final int min;
        private final int max;

        IntValue(final String path, final int defaultValue, final int min, final int max)
        {
            super(path, defaultValue);
            this.min = min;
            this.max = max;
        }

        @Override
        public Integer correct(final Integer candidate)
        {
            return Math.max(min, Math.min(max, candidate));
        }
    }

    /**
     * Ranged double entry.
     */
    public static final class DoubleValue extends ConfigValue<Double>
    {
        private final double min;
        private final double max;

        DoubleValue(final String path, final double defaultValue, final double min, final double max)
        {
            super(path, defaultValue);
            this.min = min;
            this.max = max;
        }

        @Override
        public Double correct(final Double candidate)
        {
            return Math.max(min, Math.min(max, candidate));
        }
    }

    /**
     * Enum entry.
     *
     * @param <E> enum type.
     */
    public static final class EnumValue<E extends Enum<E>> extends ConfigValue<E>
    {
        private final Class<E> enumClass;

        EnumValue(final String path, final E defaultValue)
        {
            super(path, defaultValue);
            this.enumClass = defaultValue.getDeclaringClass();
        }

        /**
         * @return the enum class, used when parsing the config file.
         */
        public Class<E> getEnumClass()
        {
            return enumClass;
        }
    }
}
