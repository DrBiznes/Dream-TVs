package com.dreamtvs;

import com.dreamtvs.dd.DreamDisplaysBridge;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/** Loader-agnostic entry point. Each loader calls {@link #init()} once during mod construction. */
public final class DreamTvs {
    public static final String MOD_ID = "dreamtvs";
    public static final Logger LOGGER = LogUtils.getLogger();

    private DreamTvs() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void init() {
        LOGGER.info("Dream TVs initializing");
        DreamDisplaysBridge.init();
    }
}
