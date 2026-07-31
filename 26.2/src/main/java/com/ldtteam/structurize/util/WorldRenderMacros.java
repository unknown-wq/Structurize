package com.ldtteam.structurize.util;

import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.client.BlueprintHandler;
import com.ldtteam.structurize.storage.rendering.types.BlueprintPreviewData;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Collection;
import java.util.List;

public abstract class WorldRenderMacros
{
    /**
     * Replacement for NeoForge's {@code RenderLevelStageEvent.Stage}. 26.2 draws the level from a submit
     * queue, so there are no "stages" to hook any more: every mod submission happens inside a single
     * {@link LevelRenderEvents#COLLECT_SUBMITS} callback and the actual draw order is decided by the
     * render type (see {@code SubmitNodeCollection#submitCustomGeometry}). The enum survives only so the
     * call sites keep choosing <i>what</i> to draw the way they used to.
     */
    // TODO(port-26.2): DEGRADED — RenderLevelStageEvent.Stage is NeoForge-only and has no 26.2 counterpart;
    //  all three stages now fire back to back from one COLLECT_SUBMITS callback, ordering is up to the render type.
    public enum Stage
    {
        AFTER_ENTITIES,
        AFTER_BLOCK_ENTITIES,
        AFTER_TRANSLUCENT_BLOCKS
    }

    // 4 chunks squared
    public static final int MAX_DEBUG_TEXT_RENDER_DIST_SQUARED = Mth.square(4 * 16);
    public static final RenderType LINES = RenderTypes.LINES;
    public static final RenderType LINES_WITH_WIDTH = RenderTypes.LINES_WITH_WIDTH;
    public static final RenderType LINES_WITH_WIDTH_DEPTH_INVERT = RenderTypes.LINES_WITH_WIDTH_DEPTH_INVERT;
    public static final RenderType GLINT_LINES = RenderTypes.GLINT_LINES;
    public static final RenderType GLINT_LINES_WITH_WIDTH = RenderTypes.GLINT_LINES_WITH_WIDTH;
    public static final RenderType COLORED_TRIANGLES = RenderTypes.COLORED_TRIANGLES;
    public static final RenderType COLORED_TRIANGLES_NC_ND = RenderTypes.COLORED_TRIANGLES_NC_ND;
    public static final Stage STAGE_FOR_LINES = Stage.AFTER_ENTITIES;
    public static final float DEFAULT_LINE_WIDTH = 0.025f;

    public Minecraft mc;
    public LocalPlayer clientPlayer;
    /**
     * Where all world geometry goes in 26.2; replaces the old {@code MultiBufferSource.BufferSource}.
     */
    public SubmitNodeCollector submitNodeCollector;
    public PoseStack poseStack;
    public LevelRenderState levelRenderState;
    public CameraRenderState cameraRenderState;
    public Frustum frustum;
    public DeltaTracker deltaTracker;
    public ClientLevel clientLevel;
    public ItemStack mainHandItem;
    public Vec3 cameraPosition;
    /**
     * In chunks
     */
    public int clientRenderDist;

    /**
     * Hooks this instance into the level renderer. Call once from the client entry point; replaces the
     * NeoForge {@code @SubscribeEvent} on {@code RenderLevelStageEvent}.
     */
    public final void registerLevelRenderCallbacks()
    {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::renderWorldLastEvent);
    }

    /**
     * Call this from the level render callback.
     *
     * @param ctx fabric level render context
     */
    public void renderWorldLastEvent(final LevelRenderContext ctx)
    {
        mc = Minecraft.getInstance();
        clientPlayer = mc.player;
        if (clientPlayer == null) // server login phase
        {
            return;
        }

        submitNodeCollector = ctx.submitNodeCollector();
        poseStack = ctx.poseStack();
        levelRenderState = ctx.levelState();
        cameraRenderState = levelRenderState.cameraRenderState;
        frustum = cameraRenderState.cullFrustum;
        cameraPosition = cameraRenderState.pos;
        deltaTracker = mc.getDeltaTracker();
        clientLevel = mc.level;
        mainHandItem = clientPlayer.getMainHandItem();
        clientRenderDist = mc.options.renderDistance().get();

        // TODO(port-26.2): DEGRADED — the model view matrix is no longer pushed by hand; 26.2 feeds the
        //  pose through the submit node and RenderSystem#applyModelViewMatrix no longer exists.
        for (final Stage stage : Stage.values())
        {
            renderWithinContext(stage);
        }
    }

    /**
     * This is called with properly prepared context. Do here what you want
     *
     * @param stage render world stage
     */
    protected abstract void renderWithinContext(Stage stage);

    /**
     * Moved pose context to camera and given pos
     * 
     * @see #popPose()
     */
    public final void pushPoseCameraToPos(final BlockPos pos)
    {
        poseStack.pushPose();
        poseStack.translate(pos.getX() - cameraPosition.x(), pos.getY() - cameraPosition.y(), pos.getZ() - cameraPosition.z());
    }

    public final void popPose()
    {
        poseStack.popPose();
    }

    // TODO(port-26.2): DISABLED — RenderSystem#applyModelViewMatrix is gone in 26.2; the model view matrix
    //  is owned by the submit pipeline and cannot be pushed from mod code. No caller inside Structurize.
    public void pushShaderMvMatrixFromPose()
    {
        /*
        final Matrix4fStack mvMatrix = RenderSystem.getModelViewStack();
        mvMatrix.pushMatrix();
        mvMatrix.mul(poseStack.last().pose());
        RenderSystem.applyModelViewMatrix();
        */
    }

    // TODO(port-26.2): DISABLED — see pushShaderMvMatrixFromPose
    public void popShaderMvMatrix()
    {
        /*
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();
        */
    }

    /**
     * @return true if given aabb can be in any way seen by camera
     */
    public final boolean isVisible(final AABB aabb)
    {
        return frustum.isVisible(aabb);
    }

    /**
     * @return true if given pos can be in any way seen by camera
     */
    public final boolean isVisible(final BlockPos pos)
    {
        return isVisible(pos, pos);
    }
    
    /**
     * @return true if given box can be in any way seen by camera
     */
    public final boolean isVisible(final BlockPos posA, final BlockPos posB)
    {
        // Frustum#cubeInFrustum(DDDDDD) is private in 26.2 AND returns an int (-1/-2 mean visible), so the
        // public AABB overload is both cheaper to reach and less error prone.
        return frustum.isVisible(new AABB(Math.min(posA.getX(), posB.getX()),
            Math.min(posA.getY(), posB.getY()),
            Math.min(posA.getZ(), posB.getZ()),
            Math.max(posA.getX(), posB.getX()) + 1,
            Math.max(posA.getY(), posB.getY()) + 1,
            Math.max(posA.getZ(), posB.getZ()) + 1));
    }

    /**
     * Draw a blueprint at given pos.
     *
     * @param previewData the blueprint and context to draw.
     * @param pos         position to render at
     */
    public final void renderBlueprint(final BlueprintPreviewData blueprint, final BlockPos pos)
    {
        BlueprintHandler.getInstance().draw(blueprint, pos, this);
    }

    /**
     * Draw a blueprint at list of given pos.
     *
     * @param previewData the blueprint and context to draw.
     * @param points      list of positions to render at
     */
    public final void renderBlueprint(final BlueprintPreviewData blueprint, final Collection<BlockPos> points)
    {
        BlueprintHandler.getInstance().drawAtListOfPositions(blueprint, points, this);
    }

    /**
     * Render a black box around two positions
     *
     * @param posA The first Position
     * @param posB The second Position
     */
    public final void renderBlackLineBox(final BlockPos posA, final BlockPos posB, final float lineWidth)
    {
        renderLineBox(LINES_WITH_WIDTH, posA, posB, 0x00, 0x00, 0x00, 0xff, lineWidth);
    }

    /**
     * Render a red glint box around two positions
     *
     * @param posA The first Position
     * @param posB The second Position
     */
    public final void renderRedGlintLineBox(final BlockPos posA, final BlockPos posB, final float lineWidth)
    {
        renderLineBox(GLINT_LINES_WITH_WIDTH, posA, posB, 0xff, 0x0, 0x0, 0xff, lineWidth);
    }

    /**
     * Render a white box around two positions
     *
     * @param posA The first Position
     * @param posB The second Position
     */
    public final void renderWhiteLineBox(final BlockPos posA, final BlockPos posB, final float lineWidth)
    {
        renderLineBox(LINES_WITH_WIDTH, posA, posB, 0xff, 0xff, 0xff, 0xff, lineWidth);
    }

    /**
     * Render a colored box around from aabb
     *
     * @param aabb the box
     */
    public final void renderLineAABB(final RenderType renderType, final AABB aabb, final int argbColor, final float lineWidth)
    {
        renderLineAABB(renderType,
            aabb,
            (argbColor >> 16) & 0xff,
            (argbColor >> 8) & 0xff,
            argbColor & 0xff,
            (argbColor >> 24) & 0xff,
            lineWidth);
    }

    /**
     * Render a colored box around from aabb
     *
     * @param aabb the box
     */
    public final void renderLineAABB(final RenderType renderType,
        final AABB aabb,
        final int red,
        final int green,
        final int blue,
        final int alpha,
        final float lineWidth)
    {
        renderLineBox(renderType,
            (float) aabb.minX,
            (float) aabb.minY,
            (float) aabb.minZ,
            (float) aabb.maxX,
            (float) aabb.maxY,
            (float) aabb.maxZ,
            red,
            green,
            blue,
            alpha,
            lineWidth);
    }

    /**
     * Render a colored box around position
     *
     * @param pos The Position
     */
    public final void renderLineBox(final RenderType renderType,
        final BlockPos pos,
        final int argbColor,
        final float lineWidth)
    {
        renderLineBox(renderType,
            pos,
            pos,
            (argbColor >> 16) & 0xff,
            (argbColor >> 8) & 0xff,
            argbColor & 0xff,
            (argbColor >> 24) & 0xff,
            lineWidth);
    }

    /**
     * Render a colored box around two positions
     *
     * @param posA The first Position
     * @param posB The second Position
     */
    public final void renderLineBox(final RenderType renderType,
        final BlockPos posA,
        final BlockPos posB,
        final int argbColor,
        final float lineWidth)
    {
        renderLineBox(renderType,
            posA,
            posB,
            (argbColor >> 16) & 0xff,
            (argbColor >> 8) & 0xff,
            argbColor & 0xff,
            (argbColor >> 24) & 0xff,
            lineWidth);
    }

    /**
     * Render a box around two positions
     *
     * @param posA First position
     * @param posB Second position
     */
    public final void renderLineBox(final RenderType renderType,
        final BlockPos posA,
        final BlockPos posB,
        final int red,
        final int green,
        final int blue,
        final int alpha,
        final float lineWidth)
    {
        renderLineBox(renderType,
            Math.min(posA.getX(), posB.getX()),
            Math.min(posA.getY(), posB.getY()),
            Math.min(posA.getZ(), posB.getZ()),
            Math.max(posA.getX(), posB.getX()) + 1,
            Math.max(posA.getY(), posB.getY()) + 1,
            Math.max(posA.getZ(), posB.getZ()) + 1,
            red,
            green,
            blue,
            alpha,
            lineWidth);
    }

    /**
     * Render a box around two positions
     *
     * @param posA First position
     * @param posB Second position
     */
    public final void renderLineBox(final RenderType renderType,
        float minX,
        float minY,
        float minZ,
        float maxX,
        float maxY,
        float maxZ,
        final int red,
        final int green,
        final int blue,
        final int alpha,
        final float lineWidth)
    {
        if (alpha == 0)
        {
            return;
        }

        final float halfLine = lineWidth / 2.0f;
        minX -= halfLine;
        minY -= halfLine;
        minZ -= halfLine;
        final float minX2 = minX + lineWidth;
        final float minY2 = minY + lineWidth;
        final float minZ2 = minZ + lineWidth;

        maxX += halfLine;
        maxY += halfLine;
        maxZ += halfLine;
        final float maxX2 = maxX - lineWidth;
        final float maxY2 = maxY - lineWidth;
        final float maxZ2 = maxZ - lineWidth;

        // effectively final copies for the lambda: the parameters above are reassigned
        final float fMinX = minX, fMinY = minY, fMinZ = minZ, fMaxX = maxX, fMaxY = maxY, fMaxZ = maxZ;

        submitNodeCollector.submitCustomGeometry(poseStack,
            renderType,
            (pose, buffer) -> populateRenderLineBox(fMinX, fMinY, fMinZ, minX2, minY2, minZ2, fMaxX, fMaxY, fMaxZ, maxX2, maxY2, maxZ2,
                red, green, blue, alpha, pose.pose(), buffer));
    }

    // TODO: ebo this, does vanilla have any ebo things?
    protected final void populateRenderLineBox(final float minX,
        final float minY,
        final float minZ,
        final float minX2,
        final float minY2,
        final float minZ2,
        final float maxX,
        final float maxY,
        final float maxZ,
        final float maxX2,
        final float maxY2,
        final float maxZ2,
        final int red,
        final int green,
        final int blue,
        final int alpha,
        final Matrix4f m,
        final VertexConsumer buf)
    {
        // z plane

        buf.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY2, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, minZ).setColor(red, green, blue, alpha);

        //

        buf.addVertex(m, minX, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY, minZ2).setColor(red, green, blue, alpha);

        //

        buf.addVertex(m, minX, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX2, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX2, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY2, maxZ2).setColor(red, green, blue, alpha);

        //

        buf.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY2, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);

        // x plane

        buf.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);

        //

        buf.addVertex(m, minX2, maxY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX2, maxY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, maxZ2).setColor(red, green, blue, alpha);

        //

        buf.addVertex(m, maxX2, maxY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX2, maxY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY, maxZ2).setColor(red, green, blue, alpha);

        //

        buf.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY2, maxZ2).setColor(red, green, blue, alpha);

        // y plane

        buf.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY, maxZ2).setColor(red, green, blue, alpha);

        //

        buf.addVertex(m, maxX2, minY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX2, minY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX2, minY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX2, minY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, minY2, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY2, maxZ2).setColor(red, green, blue, alpha);

        //

        buf.addVertex(m, maxX2, maxY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX2, maxY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY2, maxZ2).setColor(red, green, blue, alpha);

        //

        buf.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX2, maxY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX2, maxY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
    }

    public final void renderBox(final RenderType renderType,
        final BlockPos posA,
        final BlockPos posB,
        final int argbColor)
    {
        renderBox(renderType,
            posA,
            posB,
            (argbColor >> 16) & 0xff,
            (argbColor >> 8) & 0xff,
            argbColor & 0xff,
            (argbColor >> 24) & 0xff);
    }

    public final void renderBox(final RenderType renderType,
        final BlockPos posA,
        final BlockPos posB,
        final int red,
        final int green,
        final int blue,
        final int alpha)
    {
        if (alpha == 0)
        {
            return;
        }

        final float minX = Math.min(posA.getX(), posB.getX());
        final float minY = Math.min(posA.getY(), posB.getY());
        final float minZ = Math.min(posA.getZ(), posB.getZ());

        final float maxX = Math.max(posA.getX(), posB.getX()) + 1;
        final float maxY = Math.max(posA.getY(), posB.getY()) + 1;
        final float maxZ = Math.max(posA.getZ(), posB.getZ()) + 1;

        submitNodeCollector.submitCustomGeometry(poseStack,
            renderType,
            (pose, buffer) -> populateCuboid(minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha, pose.pose(), buffer));
    }

    protected final void populateCuboid(final float minX,
        final float minY,
        final float minZ,
        final float maxX,
        final float maxY,
        final float maxZ,
        final int red,
        final int green,
        final int blue,
        final int alpha,
        final Matrix4f m,
        final VertexConsumer buf)
    {
        // z plane

        buf.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);

        // y plane

        buf.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);

        // x plane

        buf.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
    }

    public final void renderFillRectangle(final int x,
        final int y,
        final int z,
        final int w,
        final int h,
        final int argbColor)
    {
        submitNodeCollector.submitCustomGeometry(poseStack,
            COLORED_TRIANGLES_NC_ND,
            (pose, buffer) -> populateRectangle(x,
                y,
                z,
                w,
                h,
                (argbColor >> 16) & 0xff,
                (argbColor >> 8) & 0xff,
                argbColor & 0xff,
                (argbColor >> 24) & 0xff,
                buffer,
                pose.pose()));
    }

    protected final void populateRectangle(final int x,
        final int y,
        final int z,
        final int w,
        final int h,
        final int red,
        final int green,
        final int blue,
        final int alpha,
        final VertexConsumer buffer,
        final Matrix4f m)
    {
        if (alpha == 0)
        {
            return;
        }

        buffer.addVertex(m, x, y, z).setColor(red, green, blue, alpha);
        buffer.addVertex(m, x, y + h, z).setColor(red, green, blue, alpha);
        buffer.addVertex(m, x + w, y + h, z).setColor(red, green, blue, alpha);
        
        buffer.addVertex(m, x, y, z).setColor(red, green, blue, alpha);
        buffer.addVertex(m, x + w, y + h, z).setColor(red, green, blue, alpha);
        buffer.addVertex(m, x + w, y, z).setColor(red, green, blue, alpha);
    }

    /**
     * Renders the given list of strings, 3 elements a row.
     *
     * @param pos                     position to render at
     * @param text                    text list
     * @param matrixStack             stack to use
     * @param buffer                  render buffer
     * @param forceWhite              force white for no depth rendering
     * @param mergeEveryXListElements merge every X elements of text list using a tostring call
     */
    public final void renderDebugText(final BlockPos pos,
        final List<String> text,
        final boolean forceWhite,
        final int mergeEveryXListElements)
    {
        renderDebugText(pos, pos, text, forceWhite, mergeEveryXListElements);
    }

    /**
     * Renders the given list of strings, 3 elements a row.
     *
     * @param renderPos               position to render at
     * @param worldPos                (logic) position in world
     * @param text                    text list
     * @param matrixStack             stack to use
     * @param buffer                  render buffer
     * @param forceWhite              force white for no depth rendering
     * @param mergeEveryXListElements merge every X elements of text list using a tostring call
     */
    @SuppressWarnings("resource")
    public final void renderDebugText(final BlockPos renderPos,
        final BlockPos worldPos,
        final List<String> text,
        final boolean forceWhite,
        final int mergeEveryXListElements)
    {
        if (mergeEveryXListElements < 1)
        {
            throw new IllegalArgumentException("mergeEveryXListElements is less than 1");
        }

        final int cap = text.size();
        // EntityRenderDispatcher#distanceToSqr(double,double,double) is gone in 26.2 (only the Entity overload
        // survives), so the distance is measured against the extracted camera position instead.
        if (cap > 0 && cameraPosition.distanceToSqr(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5)
            <= MAX_DEBUG_TEXT_RENDER_DIST_SQUARED)
        {
            final Font fontrenderer = Minecraft.getInstance().font;

            poseStack.pushPose();
            poseStack.translate(renderPos.getX() + 0.5d, renderPos.getY() + 0.6d, renderPos.getZ() + 0.5d);
            poseStack.mulPose(cameraRenderState.orientation);
            poseStack.scale(0.014f, -0.014f, 0.014f);

            final float backgroundTextOpacity = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
            final int alphaMask = (int) (backgroundTextOpacity * 255.0F) << 24;

            for (int i = 0; i < cap; i += mergeEveryXListElements)
            {
                final MutableComponent renderText = Component.literal(
                    mergeEveryXListElements == 1 ? text.get(i) : text.subList(i, Math.min(i + mergeEveryXListElements, cap)).toString());
                final float textCenterShift = (float) (-fontrenderer.width(renderText) / 2);

                submitNodeCollector.submitText(poseStack,
                    textCenterShift,
                    0,
                    renderText.getVisualOrderText(),
                    false,
                    Font.DisplayMode.SEE_THROUGH,
                    0x00f000f0,
                    forceWhite ? 0xffffffff : 0x20ffffff,
                    alphaMask,
                    0);
                if (!forceWhite)
                {
                    submitNodeCollector.submitText(poseStack,
                        textCenterShift,
                        0,
                        renderText.getVisualOrderText(),
                        false,
                        Font.DisplayMode.NORMAL,
                        0x00f000f0,
                        0xffffffff,
                        0,
                        0);
                }
                poseStack.translate(0.0d, fontrenderer.lineHeight + 1, 0.0d);
            }

            poseStack.popPose();
        }
    }

    /**
     * Structurize's own render types.
     *
     * <p>Port note: in 1.21.1 this class extended {@link RenderType} to reach the protected
     * {@code RenderStateShard} constants and built each type with {@code CompositeState.builder()}. Both are
     * gone in 26.2 — {@code RenderStateShard} does not exist any more and {@code RenderType} has a private
     * constructor. The only factory left is {@code RenderType.create(String, RenderSetup)} where
     * {@link RenderSetup} wraps a Blaze3D {@link RenderPipeline}, so every shard (transparency, depth test,
     * cull, write mask) is now a pipeline property.</p>
     *
     * <p>Two traps worth remembering: 26.2 uses a <b>reversed depth buffer</b>, so the equivalent of the old
     * {@code LEQUAL_DEPTH_TEST} is {@link CompareOp#GREATER_THAN_OR_EQUAL} (that is what
     * {@code DepthStencilState.DEFAULT} is), and the old {@code COLOR_WRITE} / {@code COLOR_DEPTH_WRITE}
     * shards are now just the {@code writeDepth} flag of {@link DepthStencilState}.</p>
     */
    public static final class RenderTypes
    {
        private RenderTypes()
        {
            throw new IllegalStateException();
        }

        private static RenderPipeline pipeline(final String name,
            final PrimitiveTopology topology,
            final BlendFunction blend,
            final CompareOp depthTest,
            final boolean writeDepth,
            final boolean cull)
        {
            return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pipeline/" + name))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(topology)
                .withColorTargetState(new ColorTargetState(blend))
                .withDepthStencilState(new DepthStencilState(depthTest, writeDepth))
                .withCull(cull)
                .build());
        }

        // TODO(port-26.2): DEGRADED — the old NEVER_DEPTH_TEST shard called RenderSystem.depthFunc(GL_NEVER),
        //  which can never pass the depth test at all. Mapped literally onto CompareOp.NEVER_PASS; both users
        //  (GLINT_LINES, COLORED_TRIANGLES_NC_ND) are unreferenced inside Structurize.
        static final RenderType GLINT_LINES = RenderType.create("structurize_glint_lines",
            RenderSetup.builder(pipeline("structurize_glint_lines",
                PrimitiveTopology.DEBUG_LINES, BlendFunction.GLINT, CompareOp.NEVER_PASS, false, false)).createRenderSetup());

        static final RenderType GLINT_LINES_WITH_WIDTH = RenderType.create("structurize_glint_lines_with_width",
            RenderSetup.builder(pipeline("structurize_glint_lines_with_width",
                PrimitiveTopology.TRIANGLES, BlendFunction.GLINT, CompareOp.ALWAYS_PASS, true, true)).createRenderSetup());

        static final RenderType LINES = RenderType.create("structurize_lines",
            RenderSetup.builder(pipeline("structurize_lines",
                PrimitiveTopology.DEBUG_LINES, BlendFunction.TRANSLUCENT, CompareOp.GREATER_THAN_OR_EQUAL, false, false)).createRenderSetup());

        static final RenderType LINES_WITH_WIDTH = RenderType.create("structurize_lines_with_width",
            RenderSetup.builder(pipeline("structurize_lines_with_width",
                PrimitiveTopology.TRIANGLES, BlendFunction.TRANSLUCENT, CompareOp.GREATER_THAN_OR_EQUAL, true, true)).createRenderSetup());

        // depth inverted: 26.2 depth is reversed, so the old GREATER_DEPTH_TEST becomes LESS_THAN_OR_EQUAL
        static final RenderType LINES_WITH_WIDTH_DEPTH_INVERT = RenderType.create("structurize_lines_with_width_depth_invert",
            RenderSetup.builder(pipeline("structurize_lines_with_width_depth_invert",
                PrimitiveTopology.TRIANGLES, BlendFunction.TRANSLUCENT, CompareOp.LESS_THAN_OR_EQUAL, false, true)).createRenderSetup());

        static final RenderType COLORED_TRIANGLES = RenderType.create("structurize_colored_triangles",
            RenderSetup.builder(pipeline("structurize_colored_triangles",
                PrimitiveTopology.TRIANGLES, BlendFunction.TRANSLUCENT, CompareOp.GREATER_THAN_OR_EQUAL, true, true)).createRenderSetup());

        static final RenderType COLORED_TRIANGLES_NC_ND = RenderType.create("structurize_colored_triangles_nc_nd",
            RenderSetup.builder(pipeline("structurize_colored_triangles_nc_nd",
                PrimitiveTopology.TRIANGLES, BlendFunction.TRANSLUCENT, CompareOp.NEVER_PASS, false, false)).createRenderSetup());

        // TODO(port-26.2): DISABLED — RegisterRenderBuffersEvent is NeoForge-only and 26.2 has no mod owned
        //  MultiBufferSource at all: geometry goes to SubmitNodeCollector#submitCustomGeometry, which batches
        //  by render type on its own. Both registerBuffer(...) and finishBuffer(...) lost their reason to exist.
        /*
        public static void registerBuffer(final RegisterRenderBuffersEvent event) { ... }
        public static void finishBuffer(final RenderLevelStageEvent event) { ... }
        */
    }
}
