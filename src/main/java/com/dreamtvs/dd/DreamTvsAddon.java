package com.dreamtvs.dd;

import com.dreamdisplays.api.addon.Addon;
import com.dreamdisplays.api.addon.AddonContext;
import com.dreamdisplays.api.addon.AddonLifecycle;
import com.dreamdisplays.api.addon.AddonSide;
import com.dreamdisplays.api.server.DisplayVeto;
import com.dreamdisplays.api.server.DreamDisplaysServer;
import com.dreamdisplays.api.server.ServerDisplay;
import com.dreamdisplays.api.server.ServerDisplayEvent;
import com.dreamtvs.DreamTvs;
import com.dreamtvs.screen.ScreenSync;
import net.minecraft.core.Direction;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The server-side Dream Displays addon. Enabled by DD once its server API is ready. Listens for changes to our
 * screens that Dream TVs didn't make itself and hands them to {@link ScreenSync} (plan §5.5).
 */
@Addon(
        id = "dreamtvs",
        name = "Dream TVs",
        version = "0.1.0",
        description = "Survival TV blocks that form Dream Displays screens.",
        side = AddonSide.SERVER
)
public final class DreamTvsAddon implements AddonLifecycle {
    /**
     * Managed screens someone asked Dream Displays to delete this tick. A removal that follows one is deliberate
     * ({@code /display delete}, the menu, an addon's {@code delete()}); one without is DD cleaning up on its own.
     */
    private static final Set<UUID> deleteRequested = new HashSet<>();

    private volatile boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void onEnable(AddonContext context) {
        enabled = true;
        ManagedIndex.invalidate();
        ScreenSync.reset();
        DreamTvs.LOGGER.info("Dream TVs addon enabled (side={})", context.getSide());

        context.own(DreamDisplaysServer.getVetoes().on(DisplayVeto.Delete.class, veto -> guard(() -> onDeleteRequested(veto))));
        context.own(DreamDisplaysServer.getEvents().on(ServerDisplayEvent.Removed.class, e -> guard(() -> onRemoved(e.getDisplay()))));
        context.own(DreamDisplaysServer.getEvents().on(ServerDisplayEvent.Moved.class, e -> guard(() -> onMoved(e.getDisplay()))));
        context.own(DreamDisplaysServer.getEvents().on(ServerDisplayEvent.DataChanged.class, e -> guard(() -> {
            if (e.getKeys().contains(ManagedTag.KEY)) onTagChanged(e.getDisplay());
        })));
        context.own(DreamDisplaysServer.getEvents().on(ServerDisplayEvent.Created.class, e -> guard(() -> onTagChanged(e.getDisplay()))));
    }

    @Override
    public void onDisable() {
        enabled = false;
        ManagedIndex.invalidate();
        deleteRequested.clear();
        DreamTvs.LOGGER.info("Dream TVs addon disabled");
    }

    /** Called once per tick: a delete request and its removal always happen within the same call. */
    public static void endOfTick() {
        deleteRequested.clear();
    }

    private static void onDeleteRequested(DisplayVeto.Delete veto) {
        if (DreamDisplaysBridge.isSelfChange()) return;
        UUID id = veto.getDisplay().getId();
        if (ManagedIndex.contains(id)) deleteRequested.add(id);
    }

    private static void onRemoved(ServerDisplay display) {
        boolean deliberate = deleteRequested.remove(display.getId());
        ManagedIndex.Entry entry = ManagedIndex.remove(display.getId());
        if (entry == null || DreamDisplaysBridge.isSelfChange()) return;
        DreamTvs.LOGGER.debug("Managed screen {} removed by someone else (deliberate={})", display.getId(), deliberate);
        ScreenSync.externallyRemoved(entry, deliberate);
    }

    /** Someone else moved one of our screens (there's no /display resize; this is other addons, e.g. Sable). */
    private static void onMoved(ServerDisplay display) {
        if (DreamDisplaysBridge.isSelfChange()) return;
        ManagedIndex.Entry old = ManagedIndex.get(display.getId());
        if (old == null) return;
        DdInternals.LiveBox live = DdInternals.liveBox(display.getId());
        if (live == null || live.facing().getAxis() == Direction.Axis.Y) {
            // We can't tell where it went (or it's on a floor/ceiling now): stop managing it and let the TVs it
            // left behind, if any, form again.
            DreamTvs.LOGGER.info("Screen {} was moved by another mod to a place Dream TVs can't follow; unmanaging it", display.getId());
            DreamDisplaysBridge.untag(display);
            ScreenSync.externallyRemoved(old, false);
            return;
        }
        ManagedTag.Bounds bounds = new ManagedTag.Bounds(live.min(), live.max(), live.facing(), old.bounds().createdAt(), old.bounds().kind());
        if (bounds.equals(old.bounds()) && live.world().equals(old.world())) return;
        DreamTvs.LOGGER.debug("Following screen {} moved by another mod: {} -> {}", display.getId(), old.bounds(), bounds);
        DreamDisplaysBridge.tag(display, bounds);
        ScreenSync.externallyMoved(old, new ManagedIndex.Entry(display.getId(), display.getWorld(), bounds));
    }

    /** Our tag was written or removed by someone else (or a tagged screen appeared): refresh the index. */
    private static void onTagChanged(ServerDisplay display) {
        if (DreamDisplaysBridge.isSelfChange() || !ManagedIndex.isBuilt()) return;
        ManagedTag.Bounds bounds = display.isAlive() ? ManagedTag.read(display) : null;
        if (bounds == null) {
            ManagedIndex.remove(display.getId());
        } else {
            ManagedIndex.put(display.getId(), display.getWorld(), bounds);
        }
    }

    /** Event handlers must never throw back into Dream Displays. */
    private static void guard(Runnable handler) {
        try {
            handler.run();
        } catch (RuntimeException e) {
            DreamTvs.LOGGER.error("Dream TVs failed to handle a Dream Displays event", e);
        }
    }
}
