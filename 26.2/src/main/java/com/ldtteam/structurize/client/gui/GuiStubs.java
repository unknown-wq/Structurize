package com.ldtteam.structurize.client.gui;

import com.ldtteam.structurize.util.ScanToolData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import com.ldtteam.structurize.compat.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Single seam between Structurize and its parked user interface (contract C9).
 *
 * <p>Every window of the mod is built on {@code com.ldtteam.blockui}, a separate ldtteam library whose 26.2
 * port is not available yet, so the eleven window classes plus {@code AbstractWindowSkeleton} are excluded
 * from the source set in {@code build.gradle}. The nine call sites that used to reach into them go through
 * this class instead; each method keeps its original body next to it in a comment, so phase 4 is: delete the
 * excludes, uncomment the bodies, delete this javadoc paragraph.</p>
 *
 * <p>Everything here is client-only and must never be touched from common code.</p>
 */
public final class GuiStubs
{
    /**
     * Mirrors {@code WindowUndoRedo.lastOperations} so the network layer has somewhere to put the operation
     * history while the window itself does not exist. Read back by the window in phase 4.
     */
    private static List<Tuple<String, Integer>> lastOperations = new ArrayList<>();

    private GuiStubs()
    {
    }

    /**
     * Opens the extended build tool. Was {@code items/ItemBuildTool.java:62}.
     *
     * @param pos          anchor position, or null when opened from thin air.
     * @param groundstyle  one of the {@code Constants.GROUNDSTYLE_*} values.
     * @param provider     registry access of the level the tool was used in.
     */
    public static void openBuildToolWindow(final @Nullable BlockPos pos,
        final int groundstyle,
        final HolderLookup.Provider provider)
    {
        // TODO(port-26.2): DISABLED — BlockUI port pending
        /* new WindowExtendedBuildTool(pos, groundstyle, null, WindowExtendedBuildTool.BLOCK_BLUEPRINT_REQUIREMENT, provider).open(); */
    }

    /**
     * Opens the scan tool window. Was {@code items/ItemScanTool.java:112}.
     *
     * @param data the scan tool data of the held stack.
     */
    public static void openScanToolWindow(final ScanToolData data)
    {
        // TODO(port-26.2): DISABLED — BlockUI port pending
        /* new WindowScan(data).open(); */
    }

    /**
     * Opens the shape tool window. Was {@code items/ItemShapeTool.java:29} and {@code :42}.
     *
     * @param pos      anchor position, or null when opened from thin air.
     * @param provider registry access of the level the tool was used in.
     */
    public static void openShapeToolWindow(final @Nullable BlockPos pos, final HolderLookup.Provider provider)
    {
        // TODO(port-26.2): DISABLED — BlockUI port pending
        /* new WindowShapeTool(pos, provider).open(); */
    }

    /**
     * Opens the tag tool window. Was {@code items/ItemTagTool.java:74}.
     *
     * @param currentTag the tag currently selected in the tool.
     * @param anchorPos  the anchor block the tool is bound to.
     * @param level      the level the tool was used in.
     * @param stack      the tool stack itself.
     */
    public static void openTagToolWindow(final String currentTag,
        final BlockPos anchorPos,
        final Level level,
        final ItemStack stack)
    {
        // TODO(port-26.2): DISABLED — BlockUI port pending
        /* new WindowTagTool(currentTag, anchorPos, level, stack).open(); */
    }

    /**
     * Stores the undo/redo history received from the server.
     * Was {@code network/messages/OperationHistoryMessage.java:54}.
     *
     * @param operations operation name and id pairs, newest first.
     */
    public static void setLastOperations(final List<Tuple<String, Integer>> operations)
    {
        // TODO(port-26.2): DISABLED — BlockUI port pending
        /* WindowUndoRedo.lastOperations = operations; */
        lastOperations = operations;
    }

    /**
     * @return the last operation history received from the server; empty while the window is parked.
     */
    public static List<Tuple<String, Integer>> getLastOperations()
    {
        return lastOperations;
    }

    /**
     * @return true when the extended build tool window is the screen currently on top.
     *         Was {@code event/ClientEventSubscriber.java:39}.
     */
    public static boolean isBuildToolScreenOpen()
    {
        // TODO(port-26.2): DISABLED — BlockUI port pending
        /* return Minecraft.getInstance().gui.screen() instanceof BOScreen screen
                 && screen.getWindow() instanceof WindowExtendedBuildTool; */
        return false;
    }

    /**
     * Drops the build tool's client side caches on disconnect.
     * Was {@code event/ClientEventSubscriber.java:171}.
     */
    public static void clearBuildToolStaticData()
    {
        // TODO(port-26.2): DISABLED — BlockUI port pending
        /* WindowExtendedBuildTool.clearStaticData(); */
        lastOperations = new ArrayList<>();
    }

    /**
     * @return true when a blueprint manipulation window is the screen currently on top; drives the
     *         keybinding conflict context. Was {@code client/ModKeyMappings.java:25}.
     */
    public static boolean isBlueprintManipulationScreenOpen()
    {
        // TODO(port-26.2): DISABLED — BlockUI port pending
        /* return Minecraft.getInstance().gui.screen() instanceof BOScreen screen
                 && screen.getWindow() instanceof AbstractBlueprintManipulationWindow; */
        return false;
    }

    /**
     * @return true when any BlockUI window is the screen currently on top.
     */
    public static boolean isAnyBlockUiScreenOpen()
    {
        // TODO(port-26.2): DISABLED — BlockUI port pending
        /* return Minecraft.getInstance().gui.screen() instanceof BOScreen; */
        return false;
    }
}
