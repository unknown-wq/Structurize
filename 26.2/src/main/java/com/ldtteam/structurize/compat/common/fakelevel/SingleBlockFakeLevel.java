package com.ldtteam.structurize.compat.common.fakelevel;

import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Re-implementation of {@code com.ldtteam.common.fakelevel.SingleBlockFakeLevel} (contract C8).
 *
 * <p>A {@link FakeLevel} holding exactly one block at {@link BlockPos#ZERO}, used to render a single block
 * with its block entity outside the world ({@code client/TagSubstitutionRenderer}) and to read the inventory
 * of a serialised block entity ({@code api/ItemStackUtils}). The API is reconstructed from those two call
 * sites: {@code withFakeLevelContext} / {@code useFakeLevelContext} set the block for the duration of one
 * call, and {@code getLevelSource().blockEntity} exposes the block entity to the renderer.</p>
 */
public class SingleBlockFakeLevel extends FakeLevel<SingleBlockFakeLevel.SingleBlockGetter>
{
    /**
     * Shared scoreboard — block entities occasionally ask for one and never write to it here.
     */
    private static final Scoreboard SCOREBOARD = new Scoreboard();

    /**
     * Light provider that simply follows the real level.
     */
    private static final IFakeLevelLightProvider PASSTHROUGH = new IFakeLevelLightProvider()
    {
        @Override
        public boolean hasCustomLight()
        {
            return false;
        }

        @Override
        public int getLightValue(final @Nullable Level realLevel, final BlockPos pos, final LightLayer layer)
        {
            return realLevel == null ? 15 : realLevel.getBrightness(layer, pos);
        }
    };

    /**
     * @param realLevel the real level to delegate everything non-block to.
     */
    public SingleBlockFakeLevel(final Level realLevel)
    {
        super(new SingleBlockGetter(), PASSTHROUGH, realLevel, SCOREBOARD, true);
    }

    /**
     * Runs {@code action} with this fake level holding the given block.
     *
     * @param state       the block state.
     * @param blockEntity its block entity, or null.
     * @param realLevel   the real level to delegate to.
     * @param action      what to run.
     */
    public void withFakeLevelContext(final BlockState state,
        final @Nullable BlockEntity blockEntity,
        final Level realLevel,
        final Consumer<SingleBlockFakeLevel> action)
    {
        useFakeLevelContext(state, blockEntity, realLevel, fakeLevel -> {
            action.accept(fakeLevel);
            return null;
        });
    }

    /**
     * Runs {@code action} with this fake level holding the given block and returns its result.
     *
     * @param state       the block state.
     * @param blockEntity its block entity, or null.
     * @param realLevel   the real level to delegate to.
     * @param action      what to run.
     * @param <R>         result type.
     * @return whatever {@code action} returned.
     */
    public <R> R useFakeLevelContext(final BlockState state,
        final @Nullable BlockEntity blockEntity,
        final Level realLevel,
        final Function<SingleBlockFakeLevel, R> action)
    {
        final SingleBlockGetter source = getLevelSource();
        final BlockState oldState = source.blockState;
        final BlockEntity oldBlockEntity = source.blockEntity;
        final Level oldRealLevel = getRealLevel();

        source.blockState = state;
        source.blockEntity = blockEntity;
        setRealLevel(realLevel);
        if (blockEntity != null)
        {
            blockEntity.setLevel(this);
        }

        try
        {
            return action.apply(this);
        }
        finally
        {
            source.blockState = oldState;
            source.blockEntity = oldBlockEntity;
            setRealLevel(oldRealLevel);
        }
    }

    /**
     * The one-block source.
     */
    public static class SingleBlockGetter implements IFakeLevelBlockGetter
    {
        /**
         * The single block state, air when nothing is set.
         */
        public BlockState blockState = Blocks.AIR.defaultBlockState();

        /**
         * The single block entity, may be null. Public because {@code client/TagSubstitutionRenderer} reads it
         * back to feed the block entity renderer.
         */
        public @Nullable BlockEntity blockEntity;

        @Override
        public BlockState getBlockState(final BlockPos pos)
        {
            return BlockPos.ZERO.equals(pos) ? blockState : Blocks.AIR.defaultBlockState();
        }

        @Override
        @Nullable
        public BlockEntity getBlockEntity(final BlockPos pos)
        {
            return BlockPos.ZERO.equals(pos) ? blockEntity : null;
        }

        @Override
        public int getHeight()
        {
            return 1;
        }

        @Override
        public void describeSelfInCrashReport(final CrashReportCategory category)
        {
            category.setDetail("Single block state", () -> String.valueOf(blockState));
            category.setDetail("Single block entity", () -> String.valueOf(blockEntity));
        }

        @Override
        public Function<BlockPos, BlockState> getRawBlockStateFunction()
        {
            return this::getBlockState;
        }
    }

    /**
     * Re-implementation of {@code SingleBlockFakeLevel.SidedSingleBlockFakeLevel}: one lazily created fake
     * level per logical side, because a fake level caches the real level it delegates to and the client and
     * server threads must not share one.
     */
    public static class SidedSingleBlockFakeLevel
    {
        private @Nullable SingleBlockFakeLevel client;
        private @Nullable SingleBlockFakeLevel server;

        /**
         * @param realLevel the real level the caller is working in.
         * @return the fake level for that side.
         */
        public SingleBlockFakeLevel get(final Level realLevel)
        {
            if (realLevel.isClientSide())
            {
                if (client == null)
                {
                    client = new SingleBlockFakeLevel(realLevel);
                }
                return client;
            }
            if (server == null)
            {
                server = new SingleBlockFakeLevel(realLevel);
            }
            return server;
        }
    }
}
