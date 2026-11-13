package com.dreamtvs.neoforge;

import com.dreamtvs.DreamTvs;
import com.dreamtvs.block.TvClientHooks;
import com.dreamtvs.client.DreamDisplaysMenu;
import com.dreamtvs.client.DreamTvsConfigScreen;
import com.dreamtvs.client.ItemDescription;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Client-only entry point; NeoForge only constructs it on the physical client. */
@Mod(value = DreamTvs.MOD_ID, dist = Dist.CLIENT)
public final class DreamTvsNeoForgeClient {
    public DreamTvsNeoForgeClient(ModContainer container) {
        TvClientHooks.installMenuOpener(DreamDisplaysMenu::open);
        // The Config button in the Mods list.
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new DreamTvsConfigScreen(parent));
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent event) -> ItemDescription.appendTo(event.getItemStack(), event.getToolTip()));
    }
}
