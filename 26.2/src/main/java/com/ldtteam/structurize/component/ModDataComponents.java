package com.ldtteam.structurize.component;

import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.items.AbstractItemWithPosSelector.PosSelection;
import com.ldtteam.structurize.items.ItemTagTool.TagData;
import com.ldtteam.structurize.util.ScanToolData;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data component types of Structurize.
 *
 * <p>Port note (contract C1): NeoForge returned a {@code DeferredHolder} here and patched
 * {@code ItemStack}/{@code Item.Properties} to accept it. Vanilla only accepts the {@link DataComponentType}
 * itself, and no call site of these fields ever used {@code .get()}, so the fields simply became the
 * component types — every existing usage compiles unchanged.</p>
 */
public class ModDataComponents
{
    public static final DataComponentType<PosSelection> POS_SELECTION =
        savedSynced("pos_selection", PosSelection.CODEC, PosSelection.STREAM_CODEC);
    public static final DataComponentType<TagData> TAGS_DATA =
        savedSynced("tags", TagData.CODEC, TagData.STREAM_CODEC);
    public static final DataComponentType<ScanToolData> SCAN_TOOL =
        savedSynced("scan_tool", ScanToolData.CODEC, ScanToolData.STREAM_CODEC);
    public static final DataComponentType<CapturedBlock> CAPTURED_BLOCK =
        savedSynced("captured_block", CapturedBlock.CODEC, CapturedBlock.STREAM_CODEC);

    /**
     * Forces the static initialiser. Must run before {@link com.ldtteam.structurize.items.ModItems#init()}:
     * item properties reference these component types while the items are being constructed.
     */
    public static void init()
    {
        // intentionally empty
    }

    private static <D> DataComponentType<D> savedSynced(final String name,
        final Codec<D> codec,
        final StreamCodec<RegistryFriendlyByteBuf, D> streamCodec)
    {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Constants.resLocStruct(name),
            DataComponentType.<D>builder().persistent(codec).networkSynchronized(streamCodec).build());
    }
}
