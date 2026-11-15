package com.dreamtvs.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The recipe sources in priority order, read from {@code dreamtvs.compat.json} at the jar root. That file is
 * written by {@code gradlew :common:generateCompatRecipes} from {@code common/src/compat}, so the priority list
 * and each source's items live in one place.
 */
public final class CompatIndex {
    public static final String RESOURCE = "/dreamtvs.compat.json";
    /** Transitional items for sequenced assembly are named {@code dreamtvs:incomplete_<tv>}. */
    private static final String INCOMPLETE_PREFIX = "dreamtvs:incomplete_";

    private static volatile List<CompatSource> loaded;

    private CompatIndex() {}

    /** The sources shipped in the jar, highest priority first. {@code vanilla} is always last. */
    public static List<CompatSource> sources() {
        List<CompatSource> sources = loaded;
        if (sources == null) {
            try (InputStream in = CompatIndex.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IllegalStateException(RESOURCE + " is missing from the jar");
                sources = parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException("Couldn't read " + RESOURCE, e);
            }
            loaded = sources;
        }
        return sources;
    }

    /** Paths (without the namespace) of the in-progress items the sequenced assemblies use. */
    public static Set<String> transitionalItems(List<CompatSource> sources) {
        Set<String> paths = new LinkedHashSet<>();
        for (CompatSource source : sources) {
            CompatSource.Requirements assembly = source.requirements(RecipeMethod.SEQUENCED_ASSEMBLY);
            if (assembly == null) continue;
            for (String item : assembly.items()) {
                if (item.startsWith(INCOMPLETE_PREFIX)) paths.add(item.substring("dreamtvs:".length()));
            }
        }
        return paths;
    }

    static List<CompatSource> parse(String text) {
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        List<CompatSource> sources = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("sources")) {
            JsonObject source = element.getAsJsonObject();
            Map<RecipeMethod, CompatSource.Requirements> methods = new EnumMap<>(RecipeMethod.class);
            for (Map.Entry<String, JsonElement> entry : source.getAsJsonObject("methods").entrySet()) {
                RecipeMethod method = RecipeMethod.byId(entry.getKey());
                if (method == null) continue; // a newer generator's method; this build can't use it
                JsonObject requirements = entry.getValue().getAsJsonObject();
                methods.put(method, new CompatSource.Requirements(
                        strings(requirements, "mods"), strings(requirements, "items")));
            }
            sources.add(new CompatSource(source.get("id").getAsString(), source.get("name").getAsString(),
                    Map.copyOf(methods)));
        }
        if (sources.isEmpty() || !sources.get(sources.size() - 1).id().equals(CompatSource.VANILLA)) {
            throw new IllegalStateException(RESOURCE + " must end with the vanilla source");
        }
        return List.copyOf(sources);
    }

    private static List<String> strings(JsonObject object, String key) {
        List<String> values = new ArrayList<>();
        object.getAsJsonArray(key).forEach(e -> values.add(e.getAsString()));
        return List.copyOf(values);
    }
}
