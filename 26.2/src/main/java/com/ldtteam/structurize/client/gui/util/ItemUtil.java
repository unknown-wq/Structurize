package com.ldtteam.structurize.client.gui.util;

import com.google.common.collect.ImmutableList;
import com.ldtteam.structurize.api.ItemStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Client-side Item utility class
 */
public class ItemUtil
{
    /**
     * TODO(port-26.2): DEGRADED — {@code BucketItem.content} is {@code protected} in 26.2 (NeoForge used to
     * publish it through an access transformer) and the mod may not widen access from its own zone. The public
     * {@code getFluidContext()} stands in: vanilla returns {@code SOURCE_ONLY} exactly when the bucket is empty.
     * A modded bucket that overrides {@code getFluidContext()} without being empty would be misjudged.
     * Original test: {@code ((BucketItem) item).content != Fluids.EMPTY}
     *
     * @param item the item to test.
     * @return true when the item is a bucket holding something.
     */
    private static boolean isFilledBucket(final Item item)
    {
        return item instanceof final BucketItem bucket && bucket.getFluidContext() != ClipContext.Fluid.SOURCE_ONLY;
    }

    /**
     * Creates a list of all items that can be picked
     *
     * @return
     */
    public static List<ItemStack> getAllItems()
    {
        return ImmutableList.copyOf(StreamSupport.stream(Spliterators.spliteratorUnknownSize(BuiltInRegistries.ITEM.iterator(), Spliterator.ORDERED), false)
            .filter(item -> item instanceof AirItem || item instanceof BlockItem || isFilledBucket(item))
            .map(ItemStack::new)
            .collect(Collectors.toList()));
    }

    /**
     * Creates a list of all items that can be picked inlcuding player items
     * Client-side
     *
     * @return
     */
    public static List<ItemStack> getAllItemsInlcudingInventory()
    {
        final Set<ItemStorage> items = new HashSet<>();
        for (final Item item : BuiltInRegistries.ITEM)
        {
            if (item instanceof AirItem || item instanceof BlockItem || isFilledBucket(item))
            {
                items.add(new ItemStorage(new ItemStack(item)));
            }
        }

        for (final ItemStack stack : Minecraft.getInstance().player.getInventory().getNonEquipmentItems())
        {
            final Item item = stack.getItem();
            if (item instanceof AirItem || item instanceof BlockItem || isFilledBucket(item))
            {
                items.add(new ItemStorage(stack.copy()));
            }
        }

        final List<ItemStack> stackList = new ArrayList<>(items.size());

        for (final ItemStorage storage : items)
        {
            stackList.add(storage.getItemStack());
        }

        return stackList;
    }
}
