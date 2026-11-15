package com.dreamtvs.client;

import com.dreamdisplays.api.DreamDisplays;
import com.dreamdisplays.api.Display;
import com.dreamdisplays.platform.client.displays.DisplayRegistry;
import com.dreamdisplays.platform.client.displays.DisplayScreen;
import com.dreamdisplays.platform.client.ui.DisplayMenu;
import com.dreamtvs.DreamTvs;
import com.dreamtvs.McCompat;
import com.dreamtvs.dd.ManagedTag;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.UUID;

/**
 * Client only. Opens Dream Displays' own display menu for the TV screen at a block position.
 *
 * <p>DD's public API has no "open menu" call, so this uses two DD client internals: {@code DisplayRegistry.screens}
 * and {@code DisplayMenu.open(screen)}. Any failure falls back to telling the player DD's built-in gesture
 * (sneak + right-click). TODO: ask upstream for {@code DreamDisplays.openMenu(DisplayId)}.
 */
public final class DreamDisplaysMenu {
    /** {@code DisplayRegistry} has two {@code getScreens()} differing only by return type; pick the map one. */
    private static final MethodHandle SCREENS_MAP = findScreensMap();

    private DreamDisplaysMenu() {}

    public static boolean open(BlockPos pos) {
        try {
            Display display = displayAt(pos);
            if (display == null) return false;
            DisplayScreen screen = screens().get(display.getId().getUuid());
            if (screen == null) return false;
            DisplayMenu.Companion.open(screen);
            return true;
        } catch (Throwable t) {
            DreamTvs.LOGGER.warn("Couldn't open the Dream Displays menu directly", t);
            var player = Minecraft.getInstance().player;
            if (player != null) McCompat.overlay(player, Component.translatable("message.dreamtvs.menu_fallback"));
            return true;
        }
    }

    /** The managed display whose stored bounds contain {@code pos}, via the public client API. */
    static Display displayAt(BlockPos pos) {
        if (!DreamDisplays.isAvailable()) return null;
        for (Display display : DreamDisplays.getDisplays()) {
            ManagedTag.Bounds bounds = ManagedTag.read(display.getCustomData().get(ManagedTag.KEY));
            if (bounds != null && bounds.contains(pos)) return display;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, DisplayScreen> screens() throws Throwable {
        if (SCREENS_MAP == null) throw new IllegalStateException("DisplayRegistry.getScreens() not found");
        return (Map<UUID, DisplayScreen>) SCREENS_MAP.invoke(DisplayRegistry.INSTANCE);
    }

    /** Returns {@code null} (never throws) so a DD change can't crash class loading; {@link #open} then falls back. */
    private static MethodHandle findScreensMap() {
        try {
            return MethodHandles.publicLookup().findVirtual(DisplayRegistry.class, "getScreens",
                    MethodType.methodType(java.util.concurrent.ConcurrentHashMap.class));
        } catch (Throwable t) {
            DreamTvs.LOGGER.warn("Dream Displays client internals changed; TV right-click menu disabled", t);
            return null;
        }
    }
}
