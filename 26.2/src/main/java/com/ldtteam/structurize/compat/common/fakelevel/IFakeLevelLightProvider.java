package com.ldtteam.structurize.compat.common.fakelevel;

import com.ldtteam.structurize.compat.common.config.ModConfigSpec.IntValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.jetbrains.annotations.Nullable;

/**
 * Re-implementation of {@code com.ldtteam.common.fakelevel.IFakeLevelLightProvider} (contract C8).
 *
 * <p>Decides what light a {@link FakeLevel} reports. Reconstructed from
 * {@code client/fakelevel/BlueprintBlockAccess}, whose only use is
 * {@code new ConfigBasedLightProvider(Structurize.getConfig().getClient().rendererLightLevel)}, and from
 * {@code storage/rendering/RenderingCache}, which reads the same config value with the convention
 * "negative = follow the real level".</p>
 */
public interface IFakeLevelLightProvider
{
    /**
     * @return true when this provider overrides the real level's light.
     */
    boolean hasCustomLight();

    /**
     * @param realLevel the real level behind the fake one, may be null.
     * @param pos       the position in the real level.
     * @param layer     block or sky light.
     * @return the light value to report.
     */
    int getLightValue(@Nullable Level realLevel, BlockPos pos, LightLayer layer);

    /**
     * Reads the light level out of a config entry; a negative value means "use the real level".
     */
    class ConfigBasedLightProvider implements IFakeLevelLightProvider
    {
        private final IntValue lightLevel;

        /**
         * @param lightLevel the config entry holding the forced light level.
         */
        public ConfigBasedLightProvider(final IntValue lightLevel)
        {
            this.lightLevel = lightLevel;
        }

        @Override
        public boolean hasCustomLight()
        {
            return lightLevel.get() >= 0;
        }

        @Override
        public int getLightValue(final @Nullable Level realLevel, final BlockPos pos, final LightLayer layer)
        {
            if (hasCustomLight())
            {
                return lightLevel.get();
            }
            return realLevel == null ? 15 : realLevel.getBrightness(layer, pos);
        }
    }
}
