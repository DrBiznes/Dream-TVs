package com.dreamtvs.fabric;

import com.dreamtvs.client.DreamTvsConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu's Configure button. Mod Menu is optional: this class is only named in the {@code modmenu} entry point
 * of fabric.mod.json, which nothing loads unless Mod Menu is installed.
 */
public final class DreamTvsModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return DreamTvsConfigScreen::new;
    }
}
