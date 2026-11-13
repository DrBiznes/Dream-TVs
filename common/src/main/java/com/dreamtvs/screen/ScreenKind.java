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
    LCD_PANEL(0, 0, 0, 4F, 0.5F, 0.5F, 0.5F, 0.5F),
    /** One block, one TV. Glass 2 pixels back; a 14x10 tube above a 3-pixel control bar. */
    CRT(1, 1, 1, 2F, 0.5F, 0.5F, 3.5F, 1.5F),
    /** Full blocks that merge into walls. No bezel: the picture covers the whole front face. */
    JUMBOTRON(2, 0, 0, 0F, 0F, 0F, 0F, 0F),
    /**
     * A wall-mounted TV two blocks wide and one tall, placed in one go. Thin panel against the back of the
     * block: glass 14 pixels back, 1-pixel bezel at the top and sides, 2 pixels at the bottom.
     */
    FLAT_SCREEN_2X1(3, 2, 1, 14F, 0.5F, 0.5F, 1.5F, 0.5F),
    /** The same TV three blocks wide and two tall. */
    FLAT_SCREEN_3X2(4, 3, 2, 14F, 0.5F, 0.5F, 1.5F, 0.5F);

    private final int id;
    private final int width;
    private final int height;
    private final boolean merges;
    private final float depth;
    private final float left;
    private final float right;
    private final float bottom;
    private final float top;

    /** @param width, height size in blocks of a TV of a fixed size, or 0 for kinds that merge into walls */
    ScreenKind(int id, int width, int height, float depth, float left, float right, float bottom, float top) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.merges = width == 0;
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

    /**
     * How many blocks wide one TV of this kind is, for kinds that don't merge. Each such TV is exactly one screen
     * of {@link #width()} by {@link #height()}. 0 for kinds that merge, where the wall decides the size.
     */
    public int width() {
        return width;
    }

    /** How many blocks tall one TV of this kind is; see {@link #width()}. */
    public int height() {
        return height;
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
