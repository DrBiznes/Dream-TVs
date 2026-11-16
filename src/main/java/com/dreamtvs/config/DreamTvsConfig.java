package com.dreamtvs.config;

import com.dreamtvs.DreamTvs;
import com.dreamtvs.compat.CompatIndex;
import com.dreamtvs.compat.CompatSource;
import com.dreamtvs.screen.ScreenKind;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Server config, {@code config/dreamtvs.json}, loaded when the server starts.
 *
 * <ul>
 *   <li>A missing file is written with the defaults and a comment for every option ({@code //} comments are
 *       allowed in the file).</li>
 *   <li>Each option is checked on its own: a value of the wrong type falls back to its default and a number
 *       outside its range is clamped, both with a warning in the log. Unknown options are reported too.</li>
 *   <li>After a good read the file is written back, so it gains new options and up-to-date comments.</li>
 *   <li>A file that isn't valid JSON is left untouched for the admin to fix; the defaults are used meanwhile.</li>
 * </ul>
 */
public final class DreamTvsConfig {
    /** Who may grow, shrink, break or merge a screen they don't own. */
    public enum ExtendPolicy { OWNER, ANYONE }

    /** The largest screen side we accept, in blocks. */
    public static final int MAX_SIDE = 128;
    /** The largest {@link #remoteRange} we accept, in blocks. */
    public static final int MAX_REMOTE_RANGE = 1024;

    /** The largest LCD Panel wall. A little smaller than a Jumbotron's by default, and the same 4:3 shape. */
    public int lcdMaxWidth = 28;
    public int lcdMaxHeight = 21;
    /** The largest Jumbotron wall. */
    public int jumbotronMaxWidth = 32;
    public int jumbotronMaxHeight = 24;
    /** 0 = unlimited. */
    public int maxScreensPerPlayer = 0;
    public ExtendPolicy extendPolicy = ExtendPolicy.OWNER;
    public boolean showFormMessages = true;
    /** How far from its TV a bound remote works, in blocks. 0 = unlimited. Checked by the client (RemoteClient). */
    public int remoteRange = 0;
    /**
     * {@code compat.recipeSource}: whose parts the TV recipes use, {@code "auto"}, {@code "vanilla"} or a source
     * id (docs/DEVELOPMENT.md). Only the format is checked here; {@code CompatResolver} checks the id.
     */
    public String recipeSource = "auto";
    /** {@code compat.createSequencedAssembly}: with Create installed, make TVs in a sequenced assembly. */
    public boolean createSequencedAssembly = true;

    private static final Set<String> KNOWN = Set.of("lcdPanel", "jumbotron", "maxScreensPerPlayer", "extendPolicy",
            "showFormMessages", "remoteRange", "compat");
    private static final Set<String> KNOWN_SIZE = Set.of("maxWidth", "maxHeight");
    private static final Set<String> KNOWN_COMPAT = Set.of("recipeSource", "createSequencedAssembly");
    private static final Pattern SOURCE_ID = Pattern.compile("[a-z0-9_]+");
    private static volatile DreamTvsConfig current = new DreamTvsConfig();

    public static DreamTvsConfig get() {
        return current;
    }

    public static void load(Path file) {
        DreamTvsConfig config = ConfigJson.load(file, DreamTvsConfig::parse, DreamTvsConfig::toFileText,
                DreamTvsConfig::new);
        current = config;
        DreamTvs.LOGGER.info("Config: LCD Panel screens up to {}x{}, Jumbotrons up to {}x{}, {} per player, "
                        + "extendPolicy {}, form messages {}, remote range {}",
                config.lcdMaxWidth, config.lcdMaxHeight, config.jumbotronMaxWidth, config.jumbotronMaxHeight,
                config.maxScreensPerPlayer == 0 ? "unlimited" : config.maxScreensPerPlayer,
                config.extendPolicy, config.showFormMessages ? "on" : "off",
                config.remoteRange == 0 ? "unlimited" : config.remoteRange);
    }

    /**
     * Reads the file without writing or logging anything, for the recipe conditions: datapacks load before
     * {@link #load} runs on server start, and again on every {@code /reload}. Defaults if the file is missing or
     * broken ({@link #load} reports problems).
     */
    public static DreamTvsConfig readQuietly(Path file) {
        try {
            if (Files.exists(file)) return parse(Files.readString(file, StandardCharsets.UTF_8), new ArrayList<>());
        } catch (IOException | RuntimeException e) {
            // fall through to the defaults
        }
        return new DreamTvsConfig();
    }

    /**
     * The file's values for the config screen, or {@code null} when the file exists but can't be read (it isn't
     * valid JSON). A missing file gives the defaults. Bad values are fixed the same way {@link #load} fixes them.
     */
    public static DreamTvsConfig readForEditing(Path file) {
        if (!Files.exists(file)) return new DreamTvsConfig();
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8), new ArrayList<>());
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * The config screen's Save: writes {@code config} to the file and makes it the config in use, so a world
     * running in this game uses it straight away (the compat recipes on the next /reload). Values are checked
     * and fixed like the file's.
     */
    public static void save(Path file, DreamTvsConfig config) throws IOException {
        DreamTvsConfig checked = parse(config.toFileText(), new ArrayList<>());
        ConfigJson.write(file, checked.toFileText());
        current = checked;
    }

    /**
     * Reads the file's text. Missing options keep their defaults; bad values are fixed and described in
     * {@code warnings}. Throws {@link JsonParseException} only when the text isn't a JSON object at all.
     */
    static DreamTvsConfig parse(String text, List<String> warnings) {
        JsonObject json = ConfigJson.parseObject(text);
        ConfigJson.reportUnknown(json, KNOWN, "", warnings);

        DreamTvsConfig c = new DreamTvsConfig();
        JsonObject lcd = ConfigJson.section(json, "lcdPanel", KNOWN_SIZE, warnings);
        if (lcd != null) {
            c.lcdMaxWidth = ConfigJson.readInt(lcd, "maxWidth", "lcdPanel.maxWidth", c.lcdMaxWidth, 1, MAX_SIDE, warnings);
            c.lcdMaxHeight = ConfigJson.readInt(lcd, "maxHeight", "lcdPanel.maxHeight", c.lcdMaxHeight, 1, MAX_SIDE, warnings);
        }
        JsonObject jumbotron = ConfigJson.section(json, "jumbotron", KNOWN_SIZE, warnings);
        if (jumbotron != null) {
            c.jumbotronMaxWidth = ConfigJson.readInt(jumbotron, "maxWidth", "jumbotron.maxWidth",
                    c.jumbotronMaxWidth, 1, MAX_SIDE, warnings);
            c.jumbotronMaxHeight = ConfigJson.readInt(jumbotron, "maxHeight", "jumbotron.maxHeight",
                    c.jumbotronMaxHeight, 1, MAX_SIDE, warnings);
        }
        c.maxScreensPerPlayer = ConfigJson.readInt(json, "maxScreensPerPlayer", c.maxScreensPerPlayer, 0,
                Integer.MAX_VALUE, warnings);
        c.extendPolicy = readPolicy(json, c.extendPolicy, warnings);
        c.showFormMessages = ConfigJson.readBoolean(json, "showFormMessages", c.showFormMessages, warnings);
        c.remoteRange = ConfigJson.readInt(json, "remoteRange", c.remoteRange, 0, MAX_REMOTE_RANGE, warnings);
        readCompat(json, c, warnings);
        return c;
    }

    private static void readCompat(JsonObject json, DreamTvsConfig c, List<String> warnings) {
        JsonObject compat = ConfigJson.section(json, "compat", KNOWN_COMPAT, warnings);
        if (compat == null) return;
        JsonPrimitive source = ConfigJson.primitive(compat, "recipeSource");
        if (source != null) {
            String id = source.isString() ? source.getAsString().trim().toLowerCase(Locale.ROOT) : "";
            if (SOURCE_ID.matcher(id).matches()) {
                c.recipeSource = id;
            } else {
                warnings.add("compat.recipeSource must be \"auto\", \"vanilla\" or a mod's source id, not "
                        + source + ". Using \"" + c.recipeSource + "\".");
            }
        }
        c.createSequencedAssembly = ConfigJson.readBoolean(compat, "createSequencedAssembly",
                "compat.createSequencedAssembly", c.createSequencedAssembly, warnings);
    }

    private static ExtendPolicy readPolicy(JsonObject json, ExtendPolicy fallback, List<String> warnings) {
        JsonPrimitive value = ConfigJson.primitive(json, "extendPolicy");
        if (value == null) return fallback;
        if (value.isString()) {
            for (ExtendPolicy policy : ExtendPolicy.values()) {
                if (policy.name().equalsIgnoreCase(value.getAsString().trim())) return policy;
            }
        }
        warnings.add("extendPolicy must be \"OWNER\" or \"ANYONE\", not " + value + ". Using \"" + fallback + "\".");
        return fallback;
    }

    /** The file as written to disk: every option with a comment saying what it does and what it accepts. */
    String toFileText() {
        return """
                // Dream TVs server config. Changes apply the next time the server starts
                // (the "compat" options also on /reload).
                // Lines starting with // are comments. A value that's out of range or of the wrong type
                // is fixed when the server starts, and the server log says what was changed.
                {
                  // The largest screen a wall of LCD Panels or Jumbotrons can build, in blocks (1 to %d).
                  // CRTs and Flat Screens have a fixed size and aren't limited by these. A screen that's
                  // already bigger than a new limit keeps working, but can't grow.
                  "lcdPanel": {
                    "maxWidth": %d,
                    "maxHeight": %d
                  },
                  "jumbotron": {
                    "maxWidth": %d,
                    "maxHeight": %d
                  },

                  // How many TV screens each player may own. 0 means unlimited.
                  // Players with dreamdisplays.create.bypass (or op level 2) can go over it.
                  "maxScreensPerPlayer": %d,

                  // Who can grow, shrink, break or merge a screen:
                  //   "OWNER"  - only the player who built it, or an admin (dreamdisplays.delete.others / op 2)
                  //   "ANYONE" - every player
                  // Who can change the video is set per screen in the Dream Displays menu.
                  "extendPolicy": "%s",

                  // Show "Screen formed" and "panels must fill a rectangle" hints on the action bar.
                  // Refusals (no permission, limit reached, someone else's screen) are always shown.
                  "showFormMessages": %s,

                  // How far from its TV a bound TV Remote works, in blocks, measured to the nearest edge of
                  // the screen (0 to %d). 0 means no limit: the remote works as long as the TV is loaded
                  // in the player's game.
                  "remoteRange": %d,

                  // Recipes that use parts from other mods. Read when the world loads and on /reload.
                  // The server log says which recipes are in use ("Dream TVs recipes: ...").
                  "compat": {
                    // Whose parts the TV recipes use:
                    //   "auto"    - the first installed mod in this list (highest priority first), else vanilla
                    //   "vanilla" - the normal crafting-table recipes, even with these mods installed
                    //   or one of these ids (if that mod isn't installed, "auto" is used):
                %s
                    "recipeSource": "%s",

                    // With Create installed, TVs are made in a Create sequenced assembly (deployers and a
                    // press) using the parts above, instead of at a crafting table. false = crafting table.
                    "createSequencedAssembly": %s
                  }
                }
                """.formatted(MAX_SIDE, lcdMaxWidth, lcdMaxHeight, jumbotronMaxWidth, jumbotronMaxHeight,
                maxScreensPerPlayer, extendPolicy.name(), showFormMessages, MAX_REMOTE_RANGE, remoteRange,
                sourceList(), recipeSource, createSequencedAssembly);
    }

    /** One comment line per recipe source in the jar, for the file. */
    private static String sourceList() {
        StringBuilder lines = new StringBuilder();
        try {
            for (CompatSource source : CompatIndex.sources()) {
                if (source.id().equals(CompatSource.VANILLA)) continue;
                if (!lines.isEmpty()) lines.append('\n');
                lines.append("    //     %-26s %s".formatted(source.id(), source.name()));
            }
        } catch (RuntimeException e) {
            lines.append("    //     (the list couldn't be read: ").append(e.getMessage()).append(')');
        }
        return lines.toString();
    }

    /** The largest screen {@code kind} can build, in blocks. Only for kinds that merge into walls. */
    public int maxWidth(ScreenKind kind) {
        return kind == ScreenKind.JUMBOTRON ? jumbotronMaxWidth : lcdMaxWidth;
    }

    public int maxHeight(ScreenKind kind) {
        return kind == ScreenKind.JUMBOTRON ? jumbotronMaxHeight : lcdMaxHeight;
    }

    /** The scanner's flood-fill cap: one more cell than the largest allowed screen of {@code kind}. */
    public int scanCap(ScreenKind kind) {
        return maxWidth(kind) * maxHeight(kind) + 1;
    }
}
