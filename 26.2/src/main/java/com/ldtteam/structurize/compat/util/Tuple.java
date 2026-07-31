package com.ldtteam.structurize.compat.util;

/**
 * Replacement for {@code net.minecraft.util.Tuple}, which no longer exists in 26.2
 * (0 hits for {@code class Tuple} in /opt/mc-src).
 *
 * <p>Fourteen Structurize files use it as a plain pair. Keeping the {@code getA()} / {@code getB()} shape
 * means the port is a single import-line change per file.</p>
 *
 * @param <A> first element type.
 * @param <B> second element type.
 */
public class Tuple<A, B>
{
    private final A a;
    private final B b;

    /**
     * @param a first element.
     * @param b second element.
     */
    public Tuple(final A a, final B b)
    {
        this.a = a;
        this.b = b;
    }

    /**
     * @return the first element.
     */
    public A getA()
    {
        return a;
    }

    /**
     * @return the second element.
     */
    public B getB()
    {
        return b;
    }

    @Override
    public boolean equals(final Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (!(obj instanceof final Tuple<?, ?> other))
        {
            return false;
        }
        return java.util.Objects.equals(a, other.a) && java.util.Objects.equals(b, other.b);
    }

    @Override
    public int hashCode()
    {
        return java.util.Objects.hash(a, b);
    }

    @Override
    public String toString()
    {
        return "Tuple[" + a + ", " + b + "]";
    }
}
