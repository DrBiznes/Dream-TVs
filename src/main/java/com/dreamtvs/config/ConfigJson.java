package com.dreamtvs.config;

import com.dreamtvs.DreamTvs;
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
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What the config files share: {@code //} comments, each option checked on its own (a wrong type falls back to
 * its default, a number out of range is clamped, unknown options are reported), and the file written back after a
 * good read so it gains new options and up-to-date comments.
 */
final class ConfigJson {
    private ConfigJson() {}

    /**
     * Reads {@code file} with {@code parse} and writes it back with {@code toText}. A missing file is written with
     * the defaults. A file that isn't valid JSON is left untouched for the player or admin to fix; the defaults
     * are used meanwhile.
     */
    static <T> T load(Path file, BiFunction<String, List<String>, T> parse, Function<T, String> toText,
                      Supplier<T> defaults) {
        T config = defaults.get();
        boolean write = true;
        if (Files.exists(file)) {
            List<String> warnings = new ArrayList<>();
            try {
                config = parse.apply(Files.readString(file, StandardCharsets.UTF_8), warnings);
            } catch (IOException | JsonParseException e) {
                DreamTvs.LOGGER.error("Couldn't read {} ({}). Using the defaults; the file is left as it is so it "
                        + "can be fixed.", file, e.getMessage());
                write = false;
            }
            for (String warning : warnings) DreamTvs.LOGGER.warn("{}: {}", file.getFileName(), warning);
        }
        if (write) {
            try {
                write(file, toText.apply(config));
            } catch (IOException e) {
                DreamTvs.LOGGER.warn("Couldn't write {}", file, e);
            }
        }
        return config;
    }

    static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /** The text as a JSON object. Throws {@link JsonParseException} when it isn't one. */
    static JsonObject parseObject(String text) {
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setLenient(true); // allows our // comments
        JsonElement root = JsonParser.parseReader(reader);
        if (!root.isJsonObject()) throw new JsonParseException("expected a JSON object { ... }");
        return root.getAsJsonObject();
    }

    /** @param prefix the section's name and a dot ({@code "compat."}), or {@code ""} at the top level */
    static void reportUnknown(JsonObject json, Set<String> known, String prefix, List<String> warnings) {
        for (String key : json.keySet()) {
            if (!known.contains(key)) warnings.add("unknown option \"" + prefix + key + "\" is ignored");
        }
    }

    /**
     * The section {@code key} of {@code json}, with its unknown options reported, or {@code null} when it's missing
     * (its options keep their defaults) or isn't an object (reported).
     */
    static JsonObject section(JsonObject json, String key, Set<String> known, List<String> warnings) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) return null;
        if (!element.isJsonObject()) {
            warnings.add(key + " must be an object { ... }, not " + element + ". Using its defaults.");
            return null;
        }
        JsonObject section = element.getAsJsonObject();
        reportUnknown(section, known, key + ".", warnings);
        return section;
    }

    /** @param name the option's name in warnings, e.g. {@code "jumbotron.maxWidth"} */
    static int readInt(JsonObject json, String key, String name, int fallback, int min, int max,
                       List<String> warnings) {
        JsonPrimitive value = primitive(json, key);
        if (value == null) return fallback;
        BigDecimal number;
        try {
            if (!value.isNumber()) throw new NumberFormatException();
            number = value.getAsBigDecimal();
            if (number.stripTrailingZeros().scale() > 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            warnings.add(name + " must be a whole number, not " + value + ". Using " + fallback + ".");
            return fallback;
        }
        if (number.compareTo(BigDecimal.valueOf(min)) < 0) {
            warnings.add(name + " can't be below " + min + ". Using " + min + ".");
            return min;
        }
        if (number.compareTo(BigDecimal.valueOf(max)) > 0) {
            warnings.add(name + " can't be above " + max + ". Using " + max + ".");
            return max;
        }
        return number.intValue();
    }

    static int readInt(JsonObject json, String key, int fallback, int min, int max, List<String> warnings) {
        return readInt(json, key, key, fallback, min, max, warnings);
    }

    static boolean readBoolean(JsonObject json, String key, String name, boolean fallback, List<String> warnings) {
        JsonPrimitive value = primitive(json, key);
        if (value == null) return fallback;
        if (value.isBoolean()) return value.getAsBoolean();
        warnings.add(name + " must be true or false, not " + value + ". Using " + fallback + ".");
        return fallback;
    }

    static boolean readBoolean(JsonObject json, String key, boolean fallback, List<String> warnings) {
        return readBoolean(json, key, key, fallback, warnings);
    }

    /** The option's value, or {@code null} when it's missing or null (so its default is used without a warning). */
    static JsonPrimitive primitive(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive()) return element.getAsJsonPrimitive();
        // An object or array: make it a string primitive so the caller reports it as a wrong type.
        return new JsonPrimitive(element.toString());
    }
}
