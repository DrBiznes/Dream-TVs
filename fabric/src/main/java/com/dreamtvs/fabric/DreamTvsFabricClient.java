package com.dreamtvs.fabric;

import com.dreamtvs.block.TvClientHooks;
import com.dreamtvs.client.DreamDisplaysMenu;
import net.fabricmc.api.ClientModInitializer;

public final class DreamTvsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TvClientHooks.installMenuOpener(DreamDisplaysMenu::open);
    }
}
