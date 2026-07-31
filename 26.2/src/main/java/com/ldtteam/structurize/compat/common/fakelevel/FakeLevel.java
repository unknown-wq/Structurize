package com.ldtteam.structurize.compat.common.fakelevel;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.LevelTickAccess;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Re-implementation of {@code com.ldtteam.common.fakelevel.FakeLevel} (contract C8).
 *
 * <p>A read-only {@link Level} whose blocks come from an {@link IFakeLevelBlockGetter} instead of from
 * chunks, used to render and inspect blueprints that are not placed in the world. Everything the real level
 * can answer (registries, dimension, ticks, world border …) is delegated to a real {@code Level}; everything
 * that would mutate the world is a no-op.</p>
 *
 * <p>TODO(port-26.2): DEGRADED — 26.2 dropped {@code Level#getShade(Direction, boolean)} and
 * {@code BlockAndTintGetter} altogether (block shading moved into the client model lighter,
 * {@code net.minecraft.world.level.BlockAndLightGetter} is what remains), so the light provider only
 * overrides {@link LightLayer} brightness. Effect: blueprint previews use vanilla face shading instead of
 * the forced flat shading the 1.21.1 renderer could ask for. Nothing in the mod called {@code getShade}
 * through the fake level directly.</p>
 *
 * @param <T> the block source type.
 */
public class FakeLevel<T extends IFakeLevelBlockGetter> extends Level
{
    /**
     * Where the fake level's origin sits in the real world; blueprint-local positions are offset by this.
     */
    protected BlockPos worldPos = BlockPos.ZERO;

    /**
     * Where the blocks come from.
     */
    protected T levelSource;

    /**
     * Overrides the reported light level.
     */
    protected IFakeLevelLightProvider lightProvider;

    /**
     * The real level everything non-block is delegated to.
     */
    protected @Nullable Level realLevel;

    /**
     * Scoreboard handed out to block entities that ask for one.
     */
    protected Scoreboard scoreboard;

    /**
     * Whether block entities of the source should be exposed at all.
     */
    protected boolean supportsBlockEntities;

    /**
     * Extra block entities the renderer wants visible, keyed by fake-level-local position.
     */
    protected Map<BlockPos, BlockEntity> blockEntities = Map.of();

    /**
     * Extra entities the renderer wants visible.
     */
    protected List<Entity> entities = List.of();

    /**
     * @param levelSource           where the blocks come from.
     * @param lightProvider         light override.
     * @param realLevel             the real level to delegate to; must not be null at construction time.
     * @param scoreboard            scoreboard for block entities.
     * @param supportsBlockEntities whether block entities are exposed.
     */
    public FakeLevel(final T levelSource,
        final IFakeLevelLightProvider lightProvider,
        final Level realLevel,
        final Scoreboard scoreboard,
        final boolean supportsBlockEntities)
    {
        super(dataOf(realLevel),
            realLevel.dimension(),
            realLevel.registryAccess(),
            realLevel.dimensionTypeRegistration(),
            realLevel.isClientSide(),
            false,
            0L,
            0);
        this.levelSource = levelSource;
        this.lightProvider = lightProvider;
        this.realLevel = realLevel;
        this.scoreboard = scoreboard;
        this.supportsBlockEntities = supportsBlockEntities;
    }

    private static WritableLevelData dataOf(final Level realLevel)
    {
        final LevelData delegate = realLevel.getLevelData();
        return new WritableLevelData()
        {
            @Override
            public void setSpawn(final LevelData.RespawnData respawnData)
            {
                // read only
            }

            @Override
            public LevelData.RespawnData getRespawnData()
            {
                return delegate.getRespawnData();
            }

            @Override
            public long getGameTime()
            {
                return delegate.getGameTime();
            }

            @Override
            public boolean isHardcore()
            {
                return delegate.isHardcore();
            }

            @Override
            public net.minecraft.world.Difficulty getDifficulty()
            {
                return delegate.getDifficulty();
            }

            @Override
            public boolean isDifficultyLocked()
            {
                return delegate.isDifficultyLocked();
            }
        };
    }

    // ------------------------------------------------------------- accessors

    /**
     * @return the block source.
     */
    public T getLevelSource()
    {
        return levelSource;
    }

    /**
     * @param levelSource the new block source.
     */
    public void setLevelSource(final T levelSource)
    {
        this.levelSource = levelSource;
    }

    /**
     * @return the origin of this fake level in the real world.
     */
    public BlockPos getWorldPos()
    {
        return worldPos;
    }

    /**
     * @param worldPos the origin of this fake level in the real world.
     */
    public void setWorldPos(final BlockPos worldPos)
    {
        this.worldPos = worldPos;
    }

    /**
     * @param blockEntities block entities to expose, keyed by fake-level-local position.
     */
    public void setBlockEntities(final Map<BlockPos, BlockEntity> blockEntities)
    {
        this.blockEntities = blockEntities == null ? Map.of() : blockEntities;
    }

    /**
     * @param entities entities to expose.
     */
    public void setEntities(final List<Entity> entities)
    {
        this.entities = entities == null ? List.of() : entities;
    }

    /**
     * @param realLevel the real level to delegate to.
     */
    public void setRealLevel(final @Nullable Level realLevel)
    {
        this.realLevel = realLevel;
    }

    /**
     * @return the real level, or null.
     */
    @Nullable
    public Level getRealLevel()
    {
        return realLevel;
    }

    // ------------------------------------------------------------ block data

    @Override
    public BlockState getBlockState(final BlockPos pos)
    {
        return levelSource.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(final BlockPos pos)
    {
        return getBlockState(pos).getFluidState();
    }

    @Override
    @Nullable
    public BlockEntity getBlockEntity(final BlockPos pos)
    {
        if (!supportsBlockEntities)
        {
            return null;
        }
        final BlockEntity cached = blockEntities.get(pos);
        return cached != null ? cached : levelSource.getBlockEntity(pos);
    }

    @Override
    public boolean setBlock(final BlockPos pos, final BlockState state, final int flags, final int recursionLeft)
    {
        return false;
    }

    @Override
    public boolean removeBlock(final BlockPos pos, final boolean isMoving)
    {
        return false;
    }

    @Override
    public boolean destroyBlock(final BlockPos pos, final boolean dropBlock, final @Nullable Entity entity, final int recursionLeft)
    {
        return false;
    }

    @Override
    public int getHeight()
    {
        return levelSource.getHeight();
    }

    @Override
    public int getMinY()
    {
        return 0;
    }

    // ----------------------------------------------------------------- light

    @Override
    public int getBrightness(final LightLayer layer, final BlockPos pos)
    {
        return lightProvider.getLightValue(realLevel, worldPos.offset(pos), layer);
    }

    @Override
    public int getRawBrightness(final BlockPos pos, final int skyDarken)
    {
        return lightProvider.hasCustomLight()
            ? lightProvider.getLightValue(realLevel, worldPos.offset(pos), LightLayer.BLOCK)
            : (realLevel == null ? 15 : realLevel.getRawBrightness(worldPos.offset(pos), skyDarken));
    }

    // ------------------------------------------------------------- delegated

    @Override
    public RegistryAccess registryAccess()
    {
        return realLevel == null ? RegistryAccess.EMPTY : realLevel.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures()
    {
        return realLevel == null ? FeatureFlagSet.of() : realLevel.enabledFeatures();
    }

    @Override
    public EnvironmentAttributeSystem environmentAttributes()
    {
        return requireReal().environmentAttributes();
    }

    @Override
    public ChunkSource getChunkSource()
    {
        return requireReal().getChunkSource();
    }

    @Override
    public LevelTickAccess<net.minecraft.world.level.block.Block> getBlockTicks()
    {
        return requireReal().getBlockTicks();
    }

    @Override
    public LevelTickAccess<net.minecraft.world.level.material.Fluid> getFluidTicks()
    {
        return requireReal().getFluidTicks();
    }

    @Override
    public int getSeaLevel()
    {
        return realLevel == null ? 63 : realLevel.getSeaLevel();
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(final int x, final int y, final int z)
    {
        return requireReal().getUncachedNoiseBiome(x, y, z);
    }

    @Override
    public WorldBorder getWorldBorder()
    {
        return requireReal().getWorldBorder();
    }

    @Override
    public List<? extends Player> players()
    {
        return realLevel == null ? List.of() : realLevel.players();
    }

    @Override
    public RandomSource getRandom()
    {
        return realLevel == null ? RandomSource.create() : realLevel.getRandom();
    }

    @Override
    public TickRateManager tickRateManager()
    {
        return requireReal().tickRateManager();
    }

    @Override
    public ClockManager clockManager()
    {
        return requireReal().clockManager();
    }

    @Override
    public RecipeAccess recipeAccess()
    {
        return requireReal().recipeAccess();
    }

    @Override
    public PotionBrewing potionBrewing()
    {
        return requireReal().potionBrewing();
    }

    @Override
    public FuelValues fuelValues()
    {
        return requireReal().fuelValues();
    }

    @Override
    public Scoreboard getScoreboard()
    {
        return scoreboard;
    }

    @Override
    public LevelData.RespawnData getRespawnData()
    {
        return realLevel == null ? LevelData.RespawnData.DEFAULT : realLevel.getRespawnData();
    }

    @Override
    @Nullable
    public MapItemSavedData getMapData(final MapId id)
    {
        return realLevel == null ? null : realLevel.getMapData(id);
    }

    @Override
    protected LevelEntityGetter<Entity> getEntities()
    {
        return EMPTY_ENTITY_GETTER;
    }

    @Override
    @Nullable
    public Entity getEntity(final int id)
    {
        for (final Entity entity : entities)
        {
            if (entity.getId() == id)
            {
                return entity;
            }
        }
        return null;
    }

    @Override
    public Collection<EnderDragonPart> dragonParts()
    {
        return List.of();
    }

    // --------------------------------------------------------------- no-ops

    @Override
    public void sendBlockUpdated(final BlockPos pos, final BlockState old, final BlockState current, final int updateFlags)
    {
        // read only
    }

    @Override
    public void playSeededSound(final @Nullable Entity entity,
        final double x,
        final double y,
        final double z,
        final Holder<SoundEvent> sound,
        final SoundSource source,
        final float volume,
        final float pitch,
        final long seed)
    {
        // silent
    }

    @Override
    public void playSeededSound(final @Nullable Entity entity,
        final Entity target,
        final Holder<SoundEvent> sound,
        final SoundSource source,
        final float volume,
        final float pitch,
        final long seed)
    {
        // silent
    }

    @Override
    public void explode(final @Nullable Entity source,
        final @Nullable DamageSource damageSource,
        final @Nullable ExplosionDamageCalculator calculator,
        final double x,
        final double y,
        final double z,
        final float radius,
        final boolean fire,
        final Level.ExplosionInteraction interaction,
        final ParticleOptions smallParticle,
        final ParticleOptions largeParticle,
        final WeightedList<net.minecraft.core.particles.ExplosionParticleInfo> particles,
        final Holder<SoundEvent> sound)
    {
        // no world to blow up
    }

    @Override
    public String gatherChunkSourceStats()
    {
        return "FakeLevel";
    }

    @Override
    public void setRespawnData(final LevelData.RespawnData respawnData)
    {
        // read only
    }

    @Override
    public void destroyBlockProgress(final int id, final BlockPos pos, final int progress)
    {
        // read only
    }

    @Override
    public void levelEvent(final @Nullable Entity entity, final int type, final BlockPos pos, final int data)
    {
        // read only
    }

    @Override
    public void gameEvent(final Holder<GameEvent> event, final Vec3 position, final GameEvent.Context context)
    {
        // read only
    }

    /**
     * Adds the block source's own detail to a crash report raised while this level was active.
     *
     * @param report the crash report.
     * @return the category that was filled.
     */
    public CrashReportCategory fillReportDetails(final CrashReport report)
    {
        final CrashReportCategory category = report.addCategory("FakeLevel details");
        levelSource.describeSelfInCrashReport(category);
        return category;
    }

    /**
     * @return the real level, never null.
     */
    protected Level requireReal()
    {
        if (realLevel == null)
        {
            throw new IllegalStateException("FakeLevel has no real level to delegate to");
        }
        return realLevel;
    }

    /**
     * Empty entity index — the fake level never owns entities, the renderer hands them over separately.
     */
    private static final LevelEntityGetter<Entity> EMPTY_ENTITY_GETTER = new LevelEntityGetter<>()
    {
        @Override
        @Nullable
        public Entity get(final int id)
        {
            return null;
        }

        @Override
        @Nullable
        public Entity get(final java.util.UUID uuid)
        {
            return null;
        }

        @Override
        public Iterable<Entity> getAll()
        {
            return List.of();
        }

        @Override
        public <U extends Entity> void get(final net.minecraft.world.level.entity.EntityTypeTest<Entity, U> type,
            final net.minecraft.util.AbortableIterationConsumer<U> consumer)
        {
            // nothing
        }

        @Override
        public void get(final net.minecraft.world.phys.AABB box, final java.util.function.Consumer<Entity> output)
        {
            // nothing
        }

        @Override
        public <U extends Entity> void get(final net.minecraft.world.level.entity.EntityTypeTest<Entity, U> type,
            final net.minecraft.world.phys.AABB box,
            final net.minecraft.util.AbortableIterationConsumer<U> consumer)
        {
            // nothing
        }
    };

    /**
     * Unused placeholder kept so the class does not lose the reference to {@link Blocks} / {@link Supplier}
     * imports if the block source ever needs a default.
     *
     * @return air.
     */
    protected static BlockState defaultState()
    {
        return Blocks.AIR.defaultBlockState();
    }

    /**
     * Rethrows a crash with the block source's details attached.
     *
     * @param throwable the original failure.
     * @param message   crash report title.
     * @return never returns.
     */
    protected ReportedException crash(final Throwable throwable, final String message)
    {
        final CrashReport report = CrashReport.forThrowable(throwable, message);
        levelSource.describeSelfInCrashReport(report.addCategory("FakeLevel details"));
        return new ReportedException(report);
    }
}
