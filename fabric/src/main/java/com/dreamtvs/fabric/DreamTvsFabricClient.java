package com.dreamtvs.fabric;

import com.dreamtvs.block.TvClientHooks;
import com.dreamtvs.client.DreamDisplaysMenu;
import com.dreamtvs.client.ItemDescription;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

public final class DreamTvsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TvClientHooks.installMenuOpener(DreamDisplaysMenu::open);
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> ItemDescription.appendTo(stack, lines));
    }
}
