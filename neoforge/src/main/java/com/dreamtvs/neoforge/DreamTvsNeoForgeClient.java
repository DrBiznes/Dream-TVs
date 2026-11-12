package com.dreamtvs.neoforge;

import com.dreamtvs.DreamTvs;
import com.dreamtvs.block.TvClientHooks;
import com.dreamtvs.client.DreamDisplaysMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/** Client-only entry point; NeoForge only constructs it on the physical client. */
@Mod(value = DreamTvs.MOD_ID, dist = Dist.CLIENT)
public final class DreamTvsNeoForgeClient {
    public DreamTvsNeoForgeClient() {
        TvClientHooks.installMenuOpener(DreamDisplaysMenu::open);
    }
}
