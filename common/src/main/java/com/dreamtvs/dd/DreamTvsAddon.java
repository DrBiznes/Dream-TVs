package com.dreamtvs.dd;

import com.dreamdisplays.api.addon.Addon;
import com.dreamdisplays.api.addon.AddonContext;
import com.dreamdisplays.api.addon.AddonLifecycle;
import com.dreamdisplays.api.addon.AddonSide;
import com.dreamdisplays.api.server.DreamDisplaysServer;
import com.dreamdisplays.api.server.ServerDisplayEvent;
import com.dreamtvs.DreamTvs;

/** The server-side Dream Displays addon. Enabled by DD once its server API is ready. */
@Addon(
        id = "dreamtvs",
        name = "Dream TVs",
        version = "0.1.0",
        description = "Survival TV blocks that form Dream Displays screens.",
        side = AddonSide.SERVER
)
public final class DreamTvsAddon implements AddonLifecycle {
    private volatile boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void onEnable(AddonContext context) {
        enabled = true;
        DreamTvs.LOGGER.info("Dream TVs addon enabled (side={})", context.getSide());

        context.own(DreamDisplaysServer.getEvents().on(ServerDisplayEvent.Created.class, e ->
                DreamTvs.LOGGER.debug("display created {} managed={}",
                        e.getDisplay().getId(), ManagedTag.isManaged(e.getDisplay()))));
        context.own(DreamDisplaysServer.getEvents().on(ServerDisplayEvent.Removed.class, e ->
                DreamTvs.LOGGER.debug("display removed {}", e.getDisplay().getId())));
        context.own(DreamDisplaysServer.getEvents().on(ServerDisplayEvent.Moved.class, e ->
                DreamTvs.LOGGER.debug("display moved {} -> {}x{}",
                        e.getDisplay().getId(), e.getDisplay().getWidth(), e.getDisplay().getHeight())));
    }

    @Override
    public void onDisable() {
        enabled = false;
        DreamTvs.LOGGER.info("Dream TVs addon disabled");
    }
}
