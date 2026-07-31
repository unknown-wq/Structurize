package com.ldtteam.structurize.compat.common.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Re-implementation of {@code com.ldtteam.common.codec.Codecs} (contract C8).
 *
 * <p>Only {@code forEnum} is used by Structurize ({@code api/RotationMirror}). The original encoded the
 * constant by its lower-cased name, which is also what {@link net.minecraft.util.StringRepresentable} based
 * vanilla enums do, so the on-disk form is unchanged.</p>
 */
public final class Codecs
{
    private Codecs()
    {
    }

    /**
     * Name based codec for a plain java enum.
     *
     * @param clazz the enum class.
     * @param <E>   the enum type.
     * @return a codec that reads and writes the lower-cased constant name.
     */
    public static <E extends Enum<E>> Codec<E> forEnum(final Class<E> clazz)
    {
        final Map<String, E> byName = Arrays.stream(clazz.getEnumConstants())
            .collect(Collectors.toMap(value -> value.name().toLowerCase(Locale.ROOT), Function.identity()));

        return Codec.STRING.comapFlatMap(name -> {
            final E value = byName.get(name.toLowerCase(Locale.ROOT));
            return value == null
                ? DataResult.error(() -> "Unknown " + clazz.getSimpleName() + ": " + name)
                : DataResult.success(value);
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }
}
