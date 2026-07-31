package com.ldtteam.structurize.compat.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Re-implementation of {@code com.ldtteam.common.util.BlockToItemHelper} (contract C8).
 *
 * <p>Turns a block state (optionally with its block entity) into the item a player would get by
 * middle-clicking it. Reconstructed from the two call sites that survive the port
 * ({@code client/gui/WindowBlockGetterContents}, and the javadoc references in
 * {@code blueprints/v1/Blueprint} and {@code util/BlockUtils}).</p>
 */
public final class BlockToItemHelper
{
    private BlockToItemHelper()
    {
    }

    /**
     * @param state       the block state.
     * @param blockEntity its block entity, or null.
     * @param player      the player asking, or null.
     * @return the pick-block stack, or {@link ItemStack#EMPTY}.
     */
    public static ItemStack getItemStack(final BlockState state,
        final @Nullable BlockEntity blockEntity,
        final @Nullable Player player)
    {
        // 26.2: BlockState#getCloneItemStack(LevelReader, BlockPos, boolean includeData) — the old
        // (BlockState, HitResult, LevelReader, BlockPos, Player) overload is gone, see
        // /opt/mc-src/net/minecraft/world/level/block/state/BlockBehaviour.java:893
        if (blockEntity != null && blockEntity.getLevel() != null)
        {
            final ItemStack fromBlockEntity = state.getCloneItemStack(blockEntity.getLevel(), blockEntity.getBlockPos(), true);
            if (!fromBlockEntity.isEmpty())
            {
                return fromBlockEntity;
            }
        }

        if (player != null)
        {
            final Level level = player.level();
            final BlockPos pos = blockEntity != null ? blockEntity.getBlockPos() : player.blockPosition();
            final ItemStack picked = state.getCloneItemStack(level, pos, true);
            if (!picked.isEmpty())
            {
                return picked;
            }
        }

        return getItem(state).getDefaultInstance();
    }

    /**
     * @param state the block state.
     * @return the plain item form of the block, without any block entity data.
     */
    public static Item getItem(final BlockState state)
    {
        return state.getBlock().asItem();
    }
}
