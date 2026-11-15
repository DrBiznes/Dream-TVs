package com.dreamtvs.config;

import com.dreamtvs.DreamTvs;
import com.dreamtvs.compat.CompatIndex;
import com.dreamtvs.compat.CompatSource;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
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

    /** The largest {@link #maxWidth} and {@link #maxHeight} we accept. */
    public static final int MAX_SIDE = 128;

    public int maxWidth = 32;
    public int maxHeight = 24;
    /** 0 = unlimited. */
    public int maxScreensPerPlayer = 0;
    public ExtendPolicy extendPolicy = ExtendPolicy.OWNER;
    public boolean showFormMessages = true;
    /**
     * {@code compat.recipeSource}: whose parts the TV recipes use, {@code "auto"}, {@code "vanilla"} or a source
     * id (docs/DEVELOPMENT.md). Only the format is checked here; {@code CompatResolver} checks the id.
     */
    public String recipeSource = "auto";
    /** {@code compat.createSequencedAssembly}: with Create installed, make TVs in a sequenced assembly. */
    public boolean createSequencedAssembly = true;

    private static final Set<String> KNOWN = Set.of(
            "maxWidth", "maxHeight", "maxScreensPerPlayer", "extendPolicy", "showFormMessages", "compat");
    private static final Set<String> KNOWN_COMPAT = Set.of("recipeSource", "createSequencedAssembly");
    private static final Pattern SOURCE_ID = Pattern.compile("[a-z0-9_]+");
    private static volatile DreamTvsConfig current = new DreamTvsConfig();

    public static DreamTvsConfig get() {
        return current;
    }

    public static void load(Path file) {
        DreamTvsConfig config = new DreamTvsConfig();
        boolean write = true;
        if (Files.exists(file)) {
            List<String> warnings = new ArrayList<>();
            try {
                config = parse(Files.readString(file, StandardCharsets.UTF_8), warnings);
            } catch (IOException | JsonParseException e) {
                DreamTvs.LOGGER.error("Couldn't read {} ({}). Using the defaults; the file is left as it is so it "
                        + "can be fixed.", file, e.getMessage());
                write = false;
            }
            for (String warning : warnings) DreamTvs.LOGGER.warn("{}: {}", file.getFileName(), warning);
        }
        if (write) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, config.toFileText(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                DreamTvs.LOGGER.warn("Couldn't write {}", file, e);
            }
        }
        current = config;
        DreamTvs.LOGGER.info("Config: screens up to {}x{}, {} per player, extendPolicy {}, form messages {}",
                config.maxWidth, config.maxHeight,
                config.maxScreensPerPlayer == 0 ? "unlimited" : config.maxScreensPerPlayer,
                config.extendPolicy, config.showFormMessages ? "on" : "off");
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
        Files.createDirectories(file.getParent());
        Files.writeString(file, checked.toFileText(), StandardCharsets.UTF_8);
        current = checked;
    }

    /**
     * Reads the file's text. Missing options keep their defaults; bad values are fixed and described in
     * {@code warnings}. Throws {@link JsonParseException} only when the text isn't a JSON object at all.
     */
    static DreamTvsConfig parse(String text, List<String> warnings) {
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setLenient(true); // allows our // comments
        JsonElement root = JsonParser.parseReader(reader);
        if (!root.isJsonObject()) throw new JsonParseException("expected a JSON object { ... }");
        JsonObject json = root.getAsJsonObject();

        for (String key : json.keySet()) {
            if (!KNOWN.contains(key)) warnings.add("unknown option \"" + key + "\" is ignored");
        }
        DreamTvsConfig c = new DreamTvsConfig();
        c.maxWidth = readInt(json, "maxWidth", c.maxWidth, 1, MAX_SIDE, warnings);
        c.maxHeight = readInt(json, "maxHeight", c.maxHeight, 1, MAX_SIDE, warnings);
        c.maxScreensPerPlayer = readInt(json, "maxScreensPerPlayer", c.maxScreensPerPlayer, 0, Integer.MAX_VALUE, warnings);
        c.extendPolicy = readPolicy(json, c.extendPolicy, warnings);
        c.showFormMessages = readBoolean(json, "showFormMessages", c.showFormMessages, warnings);
        readCompat(json.get("compat"), c, warnings);
        return c;
    }

    private static void readCompat(JsonElement element, DreamTvsConfig c, List<String> warnings) {
        if (element == null || element.isJsonNull()) return;
        if (!element.isJsonObject()) {
            warnings.add("compat must be an object { ... }, not " + element + ". Using its defaults.");
            return;
        }
        JsonObject compat = element.getAsJsonObject();
        for (String key : compat.keySet()) {
            if (!KNOWN_COMPAT.contains(key)) warnings.add("unknown option \"compat." + key + "\" is ignored");
        }
        JsonPrimitive source = primitive(compat, "recipeSource");
        if (source != null) {
            String id = source.isString() ? source.getAsString().trim().toLowerCase(Locale.ROOT) : "";
            if (SOURCE_ID.matcher(id).matches()) {
                c.recipeSource = id;
            } else {
                warnings.add("compat.recipeSource must be \"auto\", \"vanilla\" or a mod's source id, not "
                        + source + ". Using \"" + c.recipeSource + "\".");
            }
        }
        c.createSequencedAssembly = readBoolean(compat, "createSequencedAssembly", c.createSequencedAssembly,
                warnings);
    }

    private static int readInt(JsonObject json, String key, int fallback, int min, int max, List<String> warnings) {
        JsonPrimitive value = primitive(json, key);
        if (value == null) return fallback;
        BigDecimal number;
        try {
            if (!value.isNumber()) throw new NumberFormatException();
            number = value.getAsBigDecimal();
            if (number.stripTrailingZeros().scale() > 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            warnings.add(key + " must be a whole number, not " + value + ". Using " + fallback + ".");
            return fallback;
        }
        if (number.compareTo(BigDecimal.valueOf(min)) < 0) {
            warnings.add(key + " can't be below " + min + ". Using " + min + ".");
            return min;
        }
        if (number.compareTo(BigDecimal.valueOf(max)) > 0) {
            warnings.add(key + " can't be above " + max + ". Using " + max + ".");
            return max;
        }
        return number.intValue();
    }

    private static boolean readBoolean(JsonObject json, String key, boolean fallback, List<String> warnings) {
        JsonPrimitive value = primitive(json, key);
        if (value == null) return fallback;
        if (value.isBoolean()) return value.getAsBoolean();
        warnings.add(key + " must be true or false, not " + value + ". Using " + fallback + ".");
        return fallback;
    }

    private static ExtendPolicy readPolicy(JsonObject json, ExtendPolicy fallback, List<String> warnings) {
        JsonPrimitive value = primitive(json, "extendPolicy");
        if (value == null) return fallback;
        if (value.isString()) {
            for (ExtendPolicy policy : ExtendPolicy.values()) {
                if (policy.name().equalsIgnoreCase(value.getAsString().trim())) return policy;
            }
        }
        warnings.add("extendPolicy must be \"OWNER\" or \"ANYONE\", not " + value + ". Using \"" + fallback + "\".");
        return fallback;
    }

    /** The option's value, or {@code null} when it's missing or null (so its default is used without a warning). */
    private static JsonPrimitive primitive(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive()) return element.getAsJsonPrimitive();
        // An object or array: make it a string primitive so the caller reports it as a wrong type.
        return new JsonPrimitive(element.toString());
    }

    /** The file as written to disk: every option with a comment saying what it does and what it accepts. */
    String toFileText() {
        return """
                // Dream TVs server config. Changes apply the next time the server starts
                // (the "compat" options also on /reload).
                // Lines starting with // are comments. A value that's out of range or of the wrong type
                // is fixed when the server starts, and the server log says what was changed.
                {
                  // The largest screen LCD Panels and Jumbotrons can build, in blocks (1 to %d).
                  // CRTs and Flat Screens have a fixed size and aren't limited by these.
                  "maxWidth": %d,
                  "maxHeight": %d,

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
                """.formatted(MAX_SIDE, maxWidth, maxHeight, maxScreensPerPlayer, extendPolicy.name(),
                showFormMessages, sourceList(), recipeSource, createSequencedAssembly);
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

    /** The scanner's flood-fill cap: one more cell than the largest allowed screen. */
    public int scanCap() {
        return maxWidth * maxHeight + 1;
    }
}
