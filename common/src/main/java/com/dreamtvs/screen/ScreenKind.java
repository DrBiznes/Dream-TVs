package com.dreamtvs.screen;

/**
 * The kinds of TV. A kind decides whether neighbouring blocks merge into one screen, and where on the block
 * face the picture goes (the client moves and trims the Dream Displays picture to fit, see {@code PanelPicture}).
 * The {@link #id} is stored on each screen ({@code ManagedTag}), so never renumber.
 *
 * <p>Margins are in pixels (1/16 block) from the edges of the whole screen, as the viewer sees it. They put the
 * picture's edges half a pixel under the bezel so no sliver shows at the rim.
 */
public enum ScreenKind {
    /** Thin panels that merge into walls. Glass 4 pixels back, 1-pixel bezel around the wall. */
    LCD_PANEL(0, true, 4F, 0.5F, 0.5F, 0.5F, 0.5F),
    /** One block, one TV. Glass 2 pixels back; a 14x10 tube above a 3-pixel control bar. */
    CRT(1, false, 2F, 0.5F, 0.5F, 3.5F, 1.5F),
    /** Full blocks that merge into walls. No bezel: the picture covers the whole front face. */
    JUMBOTRON(2, true, 0F, 0F, 0F, 0F, 0F);

    private final int id;
    private final boolean merges;
    private final float depth;
    private final float left;
    private final float right;
    private final float bottom;
    private final float top;

    ScreenKind(int id, boolean merges, float depth, float left, float right, float bottom, float top) {
        this.id = id;
        this.merges = merges;
        this.depth = depth / 16F;
        this.left = left / 16F;
        this.right = right / 16F;
        this.bottom = bottom / 16F;
        this.top = top / 16F;
    }

    public int id() {
        return id;
    }

    /** Whether same-facing neighbours on one wall join into a single screen. */
    public boolean merges() {
        return merges;
    }

    /** How far the glass sits behind the block's front face, in blocks. */
    public float depth() {
        return depth;
    }

    public float left() {
        return left;
    }

    public float right() {
        return right;
    }

    public float bottom() {
        return bottom;
    }

    public float top() {
        return top;
    }

    /** Whether a frame stands in front of the glass (and hides the picture from the side). */
    public boolean hasBezel() {
        return depth > 0F;
    }

    public static ScreenKind byId(int id) {
        for (ScreenKind kind : values()) if (kind.id == id) return kind;
        return LCD_PANEL;
    }
}
