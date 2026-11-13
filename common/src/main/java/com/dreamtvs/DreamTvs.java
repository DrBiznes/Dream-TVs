package com.dreamtvs;

import com.dreamtvs.compat.CompatRecipes;
import com.dreamtvs.dd.DreamDisplaysBridge;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.function.Predicate;

/** Loader-agnostic entry point. Each loader calls {@link #init} once during mod construction. */
public final class DreamTvs {
    public static final String MOD_ID = "dreamtvs";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static Path configDir = Path.of("config");

    private DreamTvs() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /**
     * @param configDir the loader's config folder ({@code config/} in the game directory)
     * @param modLoaded whether a mod with this id is installed (for the compat recipes)
     */
    public static void init(Path configDir, Predicate<String> modLoaded) {
        LOGGER.info("Dream TVs initializing");
        DreamTvs.configDir = configDir;
        CompatRecipes.init(modLoaded);
        DreamDisplaysBridge.init();
    }

    /** {@code config/dreamtvs.json}. */
    public static Path configFile() {
        return configDir.resolve(MOD_ID + ".json");
    }
}
