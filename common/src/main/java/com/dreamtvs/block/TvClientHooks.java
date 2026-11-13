package com.dreamtvs.block;

import net.minecraft.core.BlockPos;

import java.util.function.Predicate;

/**
 * Client-only behaviour the block needs, installed by each loader's client entry point. Kept behind this
 * hook so {@link LcdPanelBlock} never references client classes (it also loads on dedicated servers).
 */
public final class TvClientHooks {
    private static volatile Predicate<BlockPos> menuOpener = pos -> false;

    private TvClientHooks() {}

    public static void installMenuOpener(Predicate<BlockPos> opener) {
        menuOpener = opener;
    }

    /** Opens the Dream Displays menu for the screen at {@code pos}. Returns false if there's none. */
    static boolean openMenu(BlockPos pos) {
        return menuOpener.test(pos);
    }
}
