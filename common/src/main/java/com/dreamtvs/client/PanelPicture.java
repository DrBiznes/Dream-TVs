package com.dreamtvs.client;

import com.dreamdisplays.api.display.model.property.DisplayFacing;
import com.dreamtvs.block.LcdPanelBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Client only. Fits the Dream Displays picture to an LCD panel wall: back onto the recessed glass, and pulled
 * in a little at the edges so it ends under the bezel instead of exactly on the wall's outer faces (where a
 * sliver of it showed from the side). Called from the two DD render mixins, on the render thread.
 */
public final class PanelPicture {
    /** How far the picture's edges are pulled in, in blocks. The bezel is twice as wide, so it stays hidden. */
    private static final float EDGE_INSET = 0.5F / 16F;

    /** True while Dream Displays is drawing one of our screens. */
    private static boolean drawingPanel;

    private PanelPicture() {}

    /**
     * The picture isn't drawn unless the camera is at least this far in front of the glass, in blocks. Closer
     * than that (or behind it) the bezel, which stands 1 pixel proud, hides the whole picture anyway, but DD's
     * quad seen exactly edge-on still bled through the bezel as a thin line.
     */
    private static final float MIN_VIEW_HEIGHT = 0.04F;

    /**
     * Before DD draws a screen. The pose is at the screen's anchor block, pushed by DD.
     *
     * @return false if the picture can't be seen from here and shouldn't be drawn at all
     */
    public static boolean begin(boolean ours, DisplayFacing facing, PoseStack stack) {
        drawingPanel = false;
        if (!ours) return true;
        float d = LcdPanelBlock.SCREEN_INSET;
        // A point on the glass and the direction the picture faces, in the anchor block's frame.
        Vector3f point = new Vector3f();
        Vector3f normal = new Vector3f();
        switch (facing) {
            case NORTH -> { point.set(0F, 0F, d); normal.set(0F, 0F, -1F); }
            case SOUTH -> { point.set(0F, 0F, 1F - d); normal.set(0F, 0F, 1F); }
            case WEST -> { point.set(d, 0F, 0F); normal.set(-1F, 0F, 0F); }
            case EAST -> { point.set(1F - d, 0F, 0F); normal.set(1F, 0F, 0F); }
            default -> { return true; }
        }
        // The camera is the origin of the space the pose maps into, so this is its height above the glass.
        Matrix4f pose = stack.last().pose();
        pose.transformPosition(point);
        pose.transformDirection(normal);
        float length = normal.length();
        if (length > 1.0E-6F && -point.dot(normal) / length < MIN_VIEW_HEIGHT) return false;

        drawingPanel = true;
        stack.translate(-normalX(facing) * d, 0F, -normalZ(facing) * d);
        return true;
    }

    private static float normalX(DisplayFacing facing) {
        return facing == DisplayFacing.EAST ? 1F : facing == DisplayFacing.WEST ? -1F : 0F;
    }

    private static float normalZ(DisplayFacing facing) {
        return facing == DisplayFacing.SOUTH ? 1F : facing == DisplayFacing.NORTH ? -1F : 0F;
    }

    public static void end() {
        drawingPanel = false;
    }

    /** After DD's screen transform: the picture is the unit square here, whatever the facing. */
    public static void shrinkUnderBezel(PoseStack stack, int width, int height) {
        if (!drawingPanel || width <= 0 || height <= 0) return;
        float ex = EDGE_INSET / width;
        float ey = EDGE_INSET / height;
        stack.translate(ex, ey, 0F);
        stack.scale(1F - 2F * ex, 1F - 2F * ey, 1F);
    }
}
