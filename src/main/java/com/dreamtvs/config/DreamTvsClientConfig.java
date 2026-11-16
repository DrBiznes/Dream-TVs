package com.dreamtvs.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Client config, {@code config/dreamtvs-client.json}: how this game draws TVs, whatever server it's on. Loaded
 * when the client starts, saved from the settings screen, and read and written by the same rules as the server
 * config ({@link DreamTvsConfig}).
 */
public final class DreamTvsClientConfig {
    public static final String FILE_NAME = "dreamtvs-client.json";

    /** The CRT look (scanlines, glow, glare and the rolling bar) over the picture on CRT TVs. */
    public boolean crtOverlay = true;

    private static final Set<String> KNOWN = Set.of("crtOverlay");
    private static volatile DreamTvsClientConfig current = new DreamTvsClientConfig();
    private static volatile Path file = Path.of("config", FILE_NAME);

    public static DreamTvsClientConfig get() {
        return current;
    }

    /** The file {@link #load} read, for the settings screen to save to. */
    public static Path file() {
        return file;
    }

    /** @param configDir the loader's config folder */
    public static void load(Path configDir) {
        file = configDir.resolve(FILE_NAME);
        current = ConfigJson.load(file, DreamTvsClientConfig::parse, DreamTvsClientConfig::toFileText,
                DreamTvsClientConfig::new);
    }

    /** The settings screen's Save: writes the file and uses the values straight away. */
    public static void save(DreamTvsClientConfig config) throws IOException {
        DreamTvsClientConfig checked = parse(config.toFileText(), new ArrayList<>());
        ConfigJson.write(file, checked.toFileText());
        current = checked;
    }

    public DreamTvsClientConfig copy() {
        DreamTvsClientConfig copy = new DreamTvsClientConfig();
        copy.crtOverlay = crtOverlay;
        return copy;
    }

    /** Like {@link DreamTvsConfig#parse}: throws {@link JsonParseException} only when the text isn't a JSON object. */
    static DreamTvsClientConfig parse(String text, List<String> warnings) {
        JsonObject json = ConfigJson.parseObject(text);
        ConfigJson.reportUnknown(json, KNOWN, "", warnings);
        DreamTvsClientConfig c = new DreamTvsClientConfig();
        c.crtOverlay = ConfigJson.readBoolean(json, "crtOverlay", c.crtOverlay, warnings);
        return c;
    }

    String toFileText() {
        return """
                // Dream TVs client config: how this game draws TVs, on any server. Also in the settings screen
                // (Mod Menu on Fabric, the Config button in the Mods list on NeoForge).
                // Lines starting with // are comments.
                {
                  // The CRT look on CRT TVs: scanlines, phosphor glow, glass glare and a slowly rolling bar of
                  // light over the picture. false = a plain picture.
                  "crtOverlay": %s
                }
                """.formatted(crtOverlay);
    }
}
