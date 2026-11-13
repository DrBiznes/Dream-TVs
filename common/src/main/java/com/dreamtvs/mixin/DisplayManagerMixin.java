package com.dreamtvs.mixin;

import com.dreamdisplays.platform.server.datatypes.display.DisplayData;
import com.dreamdisplays.platform.server.managers.DisplayManager;
import com.dreamtvs.dd.DreamDisplaysBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Dream Displays checks every screen 2 seconds after a player joins and deletes the ones whose box holds no
 * base-material block (black concrete by default). TV walls never do, so without this every TV screen, and its
 * link, would be wiped on the next join. We keep our screens out of DD's removal list; Dream TVs does its own
 * orphan cleanup ({@code ScreenSync}).
 *
 * <p>This targets DD internals (1.10.9). If the method changes, the redirect just doesn't apply ({@code require = 0})
 * and the fallback in {@code ScreenSync} gives the still-standing TVs a fresh screen instead. Asked upstream to
 * make this an API option (docs/UPSTREAM.md).
 */
@Mixin(value = DisplayManager.class, remap = false)
public abstract class DisplayManagerMixin {
    @Redirect(
            method = "validateDisplaysAndCleanup",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"),
            require = 0,
            remap = false
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean dreamtvs$keepTvScreens(List removals, Object display) {
        if (display instanceof DisplayData data && DreamDisplaysBridge.isManaged(data.getId())) return false;
        return removals.add(display);
    }
}
