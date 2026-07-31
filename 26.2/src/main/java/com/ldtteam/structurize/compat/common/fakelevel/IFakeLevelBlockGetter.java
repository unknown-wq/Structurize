package com.ldtteam.structurize.compat.common.fakelevel;

import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Re-implementation of {@code com.ldtteam.common.fakelevel.IFakeLevelBlockGetter} (contract C8).
 *
 * <p>The block source a {@link FakeLevel} reads from. Reconstructed from its only implementor,
 * {@code blueprints/v1/Blueprint}, and from the one caller that goes through
 * {@code getRawBlockStateFunction()}, {@code client/fakelevel/BlueprintBlockAccess}.</p>
 */
public interface IFakeLevelBlockGetter
{
    /**
     * @param pos position local to the fake level.
     * @return the block state there.
     */
    BlockState getBlockState(BlockPos pos);

    /**
     * @param pos position local to the fake level.
     * @return the block entity there, or null.
     */
    @Nullable
    BlockEntity getBlockEntity(BlockPos pos);

    /**
     * @return the vertical size of the source.
     */
    int getHeight();

    /**
     * Adds source specific detail to a crash report raised while the fake level was active.
     *
     * @param category the crash report category to fill.
     */
    void describeSelfInCrashReport(CrashReportCategory category);

    /**
     * @return the plain state lookup, for callers that need to compose it with a position transform.
     */
    default Function<BlockPos, BlockState> getRawBlockStateFunction()
    {
        return this::getBlockState;
    }
}
