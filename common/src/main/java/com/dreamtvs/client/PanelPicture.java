package com.dreamtvs.client;

import com.dreamdisplays.api.display.model.property.DisplayFacing;
import com.dreamtvs.dd.ManagedTag;
import com.dreamtvs.screen.ScreenKind;
import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Client only. Fits the Dream Displays picture to our TVs. DD draws a flat picture over the whole front face of
 * its block box; each {@link ScreenKind} says how far back its glass is and how much of the face it covers, and
 * the picture is moved onto the glass and trimmed so its edges end under the bezel. Called from the two DD
 * render mixins, on the render thread.
 */
public final class PanelPicture {
    /**
     * The picture isn't drawn unless the camera is at least this far in front of the glass, in blocks. Closer
     * than that (or behind it) the bezel, which stands 1 pixel proud, hides the whole picture anyway, but DD's
     * quad seen exactly edge-on still bled through the bezel as a thin line.
     */
    private static final float MIN_VIEW_HEIGHT = 0.04F;

    /** The kind of the screen Dream Displays is drawing right now, or null if it isn't one of ours. */
    private static @Nullable ScreenKind drawing;

    private PanelPicture() {}

    /**
     * Before DD draws a screen. The pose is at the screen's anchor block, pushed by DD.
     *
     * @param tag our tag on the screen, or null if it isn't ours
     * @return false if the picture can't be seen from here and shouldn't be drawn at all
     */
    public static boolean begin(byte @Nullable [] tag, DisplayFacing facing, PoseStack stack) {
        drawing = null;
        ManagedTag.Bounds bounds = ManagedTag.read(tag);
        if (bounds == null) return true;
        ScreenKind kind = bounds.kind();
        float d = kind.depth();
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
        // Move the picture back onto the glass (against the direction it faces).
        float backX = -normal.x * d;
        float backZ = -normal.z * d;

        // The camera is the origin of the space the pose maps into, so this is its height above the glass.
        Matrix4f pose = stack.last().pose();
        pose.transformPosition(point);
        pose.transformDirection(normal);
        float length = normal.length();
        // Without a bezel the picture shows from any angle in front, so only a view from behind is skipped.
        float minHeight = kind.hasBezel() ? MIN_VIEW_HEIGHT : 0F;
        if (length > 1.0E-6F && -point.dot(normal) / length < minHeight) return false;

        stack.translate(backX, 0F, backZ);
        drawing = kind;
        return true;
    }

    public static void end() {
        drawing = null;
    }

    /** After DD's screen transform: the picture is the unit square here (x to the viewer's right, y up). */
    public static void fitToGlass(PoseStack stack, int width, int height) {
        ScreenKind kind = drawing;
        if (kind == null || width <= 0 || height <= 0) return;
        float left = kind.left() / width;
        float bottom = kind.bottom() / height;
        stack.translate(left, bottom, 0F);
        stack.scale(1F - left - kind.right() / width, 1F - bottom - kind.top() / height, 1F);
    }
}
