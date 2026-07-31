package com.ldtteam.structurize.compat.common.language;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ldtteam.structurize.api.Log;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Re-implementation of {@code com.ldtteam.common.language.LanguageHandler} (contract C8).
 *
 * <p>Purpose on NeoForge: give the <em>server</em> a translation table, because {@code net.minecraft.locale.Language}
 * is only populated on a client. The mod registers a classpath pattern with {@link #loadLangPath(String)} at
 * construction time and, once the client language manager has run, {@link #setMClanguageLoaded()} flips the
 * lookups over to vanilla so the player's chosen language wins.</p>
 *
 * <p>Structurize only ever calls those two methods, but {@link #translateKey(String)} /
 * {@link #format(String, Object...)} are kept because that is the whole point of the class and other
 * ldtteam code reaches for them.</p>
 */
public final class LanguageHandler
{
    /**
     * Fallback table, read from the mod jar. Never null after {@link #loadLangPath(String)}.
     */
    private static final Map<String, String> FALLBACK = new HashMap<>();

    /**
     * True once Minecraft's own language manager is up; from then on vanilla is authoritative.
     */
    private static boolean mcLanguageLoaded = false;

    private LanguageHandler()
    {
    }

    /**
     * Loads the fallback translation table.
     *
     * @param pathPattern classpath pattern with one {@code %s} for the language code,
     *                    e.g. {@code assets/structurize/lang/%s.json}.
     */
    public static void loadLangPath(final String pathPattern)
    {
        load(pathPattern.formatted("en_us"));
    }

    private static void load(final String path)
    {
        try (InputStream stream = LanguageHandler.class.getClassLoader().getResourceAsStream(path))
        {
            if (stream == null)
            {
                Log.getLogger().warn("Language file not found on classpath: " + path);
                return;
            }
            final JsonElement root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            if (root.isJsonObject())
            {
                final JsonObject object = root.getAsJsonObject();
                for (final Entry<String, JsonElement> entry : object.entrySet())
                {
                    if (entry.getValue().isJsonPrimitive())
                    {
                        FALLBACK.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
        }
        catch (final Exception e)
        {
            Log.getLogger().error("Could not read language file " + path, e);
        }
    }

    /**
     * Marks Minecraft's language manager as ready. Called once from the client lifecycle.
     */
    public static void setMClanguageLoaded()
    {
        mcLanguageLoaded = true;
    }

    /**
     * @return true when vanilla translations are available.
     */
    public static boolean isMcLanguageLoaded()
    {
        return mcLanguageLoaded;
    }

    /**
     * @param key translation key.
     * @return the translated string, or the key itself when unknown.
     */
    public static String translateKey(final String key)
    {
        if (mcLanguageLoaded)
        {
            final String vanilla = net.minecraft.locale.Language.getInstance().getOrDefault(key, key);
            if (!key.equals(vanilla))
            {
                return vanilla;
            }
        }
        return FALLBACK.getOrDefault(key, key);
    }

    /**
     * @param key  translation key.
     * @param args format arguments.
     * @return the formatted translation.
     */
    public static String format(final String key, final Object... args)
    {
        final String translated = translateKey(key);
        return args.length == 0 ? translated : String.format(translated, args);
    }
}
