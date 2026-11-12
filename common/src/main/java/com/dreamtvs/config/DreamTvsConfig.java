package com.dreamtvs.config;

import com.dreamtvs.DreamTvs;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server config, {@code config/dreamtvs.json} (plan §6). Loaded when the server starts; a missing file is
 * written with the defaults. Values outside sensible bounds are clamped.
 */
public final class DreamTvsConfig {
    /** Who may grow, shrink, break or merge a screen they don't own (plan §5.7). */
    public enum ExtendPolicy { OWNER, ANYONE }

    public int maxWidth = 32;
    public int maxHeight = 24;
    /** 0 = unlimited. */
    public int maxScreensPerPlayer = 0;
    public ExtendPolicy extendPolicy = ExtendPolicy.OWNER;
    public boolean showFormMessages = true;

    private static final int MAX_SIDE = 128;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile DreamTvsConfig current = new DreamTvsConfig();

    public static DreamTvsConfig get() {
        return current;
    }

    public static void load(Path file) {
        DreamTvsConfig config = new DreamTvsConfig();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                DreamTvsConfig read = GSON.fromJson(reader, DreamTvsConfig.class);
                if (read != null) config = read;
            } catch (IOException | JsonParseException e) {
                DreamTvs.LOGGER.error("Couldn't read {}, using defaults", file, e);
            }
        }
        config.clamp();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            DreamTvs.LOGGER.warn("Couldn't write {}", file, e);
        }
        current = config;
        DreamTvs.LOGGER.info("Config: max {}x{}, {} screens per player, extendPolicy {}",
                config.maxWidth, config.maxHeight,
                config.maxScreensPerPlayer == 0 ? "unlimited" : config.maxScreensPerPlayer, config.extendPolicy);
    }

    private void clamp() {
        maxWidth = Math.clamp(maxWidth, 1, MAX_SIDE);
        maxHeight = Math.clamp(maxHeight, 1, MAX_SIDE);
        maxScreensPerPlayer = Math.max(0, maxScreensPerPlayer);
        if (extendPolicy == null) extendPolicy = ExtendPolicy.OWNER;
    }

    /** The scanner's flood-fill cap: one more cell than the largest allowed screen. */
    public int scanCap() {
        return maxWidth * maxHeight + 1;
    }
}
