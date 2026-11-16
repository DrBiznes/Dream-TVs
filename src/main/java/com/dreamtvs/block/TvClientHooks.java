package com.dreamtvs.block;

import com.dreamtvs.item.RemoteItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Client-only behaviour the blocks and the remote need, installed by each loader's client entry point. Kept
 * behind this hook so they never reference client classes (they also load on dedicated servers).
 */
public final class TvClientHooks {
    private static volatile Predicate<BlockPos> menuOpener = pos -> false;
    private static volatile Function<RemoteItem.Binding, Component> remoteUser = binding -> null;

    private TvClientHooks() {}

    public static void installMenuOpener(Predicate<BlockPos> opener) {
        menuOpener = opener;
    }

    public static void installRemoteUser(Function<RemoteItem.Binding, Component> user) {
        remoteUser = user;
    }

    /**
     * The remote's right-click: opens its TV's Dream Displays menu if the remote reaches it.
     * @return what to tell the player, or {@code null} when the menu opened
     */
    public static Component useRemote(RemoteItem.Binding binding) {
        return remoteUser.apply(binding);
    }

    /** Opens the Dream Displays menu for the screen at {@code pos}. Returns false if there's none. */
    public static boolean openMenu(BlockPos pos) {
        return menuOpener.test(pos);
    }
}
