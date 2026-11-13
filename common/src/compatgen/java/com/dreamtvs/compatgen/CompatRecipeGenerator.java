package com.dreamtvs.compatgen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Builds the TV compat recipes (docs/COMPAT_RECIPES_PLAN.md) from {@code common/src/compat/sources.json} and
 * {@code tvs.json}. Run with {@code gradlew :common:generateCompatRecipes}; the output is committed under
 * {@code common/src/generated/resources} and a unit test fails when it's out of date.
 *
 * <p>Each recipe file carries both loaders' condition blocks ({@code neoforge:conditions} and
 * {@code fabric:load_conditions}); each loader ignores the other's key, so one file serves both.
 *
 * <p>Output format: Minecraft 1.21.1 recipes and Create 6 sequenced assembly. When porting to a version with a
 * different recipe format, only {@link #crafting} and {@link #sequencedAssembly} need to change.
 */
public final class CompatRecipeGenerator {
    public static final String MOD_ID = "dreamtvs";
    public static final String VANILLA = "vanilla";
    public static final String CRAFTING = "crafting";
    public static final String SEQUENCED_ASSEMBLY = "sequenced_assembly";
    /** The runtime index of sources, read by {@code com.dreamtvs.compat.CompatIndex}. */
    public static final String INDEX_PATH = "dreamtvs.compat.json";
    /** Folder under the resources root that holds every generated recipe. */
    public static final String RECIPE_DIR = "data/" + MOD_ID + "/recipe";

    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");
    private static final Pattern RESOURCE_ID = Pattern.compile("#?[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private CompatRecipeGenerator() {}

    /** {@code args[0]}: folder with sources.json and tvs.json. {@code args[1]}: generated resources root. */
    public static void main(String[] args) throws IOException {
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        Map<String, String> files = generate(
                Files.readString(input.resolve("sources.json"), StandardCharsets.UTF_8),
                Files.readString(input.resolve("tvs.json"), StandardCharsets.UTF_8));

        // The folder holds only generated files: start clean so removed sources or TVs don't linger.
        deleteRecursively(output);
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = output.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
        }
        System.out.println("Wrote " + files.size() + " files to " + output);
    }

    /** Every generated file, by path relative to the resources root. Throws on a mistake in the sources. */
    public static Map<String, String> generate(String sourcesJson, String tvsJson) {
        List<Source> sources = readSources(JsonParser.parseString(sourcesJson).getAsJsonObject());
        List<Tv> tvs = readTvs(JsonParser.parseString(tvsJson).getAsJsonObject());

        Map<String, String> files = new TreeMap<>();
        JsonArray index = new JsonArray();
        for (Source source : sources) {
            JsonObject methods = new JsonObject();
            boolean vanilla = source.id.equals(VANILLA);

            Set<String> craftingItems = new LinkedHashSet<>();
            for (Tv tv : tvs) {
                String path = vanilla ? RECIPE_DIR + "/" + tv.id + ".json"
                        : RECIPE_DIR + "/compat/" + source.id + "/" + CRAFTING + "/" + tv.id + ".json";
                files.put(path, json(crafting(source, tv, craftingItems)));
            }
            methods.add(CRAFTING, method(source.mods, craftingItems));

            if (!vanilla) {
                List<String> mods = new ArrayList<>(source.mods);
                if (!mods.contains("create")) mods.add(0, "create");
                Set<String> assemblyItems = new LinkedHashSet<>();
                checkAssembliesDistinct(source, tvs);
                for (Tv tv : tvs) {
                    String path = RECIPE_DIR + "/compat/" + source.id + "/" + SEQUENCED_ASSEMBLY + "/" + tv.id + ".json";
                    files.put(path, json(sequencedAssembly(source, mods, tv, assemblyItems)));
                }
                methods.add(SEQUENCED_ASSEMBLY, method(mods, assemblyItems));
            }

            JsonObject entry = new JsonObject();
            entry.addProperty("id", source.id);
            entry.addProperty("name", source.name);
            entry.add("methods", methods);
            index.add(entry);
        }

        // Models for the in-progress items: they look like the finished TV for now.
        for (Tv tv : tvs) {
            JsonObject model = new JsonObject();
            model.addProperty("parent", MOD_ID + ":item/" + tv.id);
            files.put("assets/" + MOD_ID + "/models/item/incomplete_" + tv.id + ".json", json(model));
        }

        JsonObject root = new JsonObject();
        root.addProperty("//", "Generated by gradlew :common:generateCompatRecipes from common/src/compat. "
                + "Sources in priority order; each method lists the mods and items it needs.");
        root.add("sources", index);
        files.put(INDEX_PATH, json(root));
        return files;
    }

    // --- recipe output (1.21.1 / Create 6 formats) -----------------------------------------------------------

    private static JsonObject crafting(Source source, Tv tv, Set<String> items) {
        JsonObject recipe = conditions(source.id, CRAFTING, source.mods);
        recipe.addProperty("type", "minecraft:crafting_shaped");
        recipe.addProperty("category", "redstone");
        recipe.add("pattern", strings(tv.pattern));
        JsonObject key = new JsonObject();
        for (Map.Entry<String, String> entry : tv.key.entrySet()) {
            key.add(entry.getKey(), ingredient(source.material(entry.getValue(), tv.id), items));
        }
        recipe.add("key", key);
        recipe.add("result", stack(tv.result(), tv.craftingCount));
        return recipe;
    }

    private static JsonObject sequencedAssembly(Source source, List<String> mods, Tv tv, Set<String> items) {
        String transitional = MOD_ID + ":incomplete_" + tv.id;
        JsonObject recipe = conditions(source.id, SEQUENCED_ASSEMBLY, mods);
        recipe.addProperty("type", "create:sequenced_assembly");
        recipe.add("ingredient", ingredient(source.material(tv.assemblyInput, tv.id), items));
        recipe.addProperty("loops", tv.loops);
        JsonArray results = new JsonArray();
        results.add(stack(tv.result(), tv.assemblyCount));
        recipe.add("results", results);

        JsonArray sequence = new JsonArray();
        for (Step step : tv.steps) {
            JsonObject entry = new JsonObject();
            entry.addProperty("type", "create:" + step.type);
            JsonArray ingredients = new JsonArray();
            ingredients.add(ingredient(transitional, items));
            if (step.role != null) ingredients.add(ingredient(source.material(step.role, tv.id), items));
            entry.add("ingredients", ingredients);
            JsonArray stepResults = new JsonArray();
            stepResults.add(stack(transitional, 1));
            entry.add("results", stepResults);
            sequence.add(entry);
        }
        recipe.add("sequence", sequence);
        recipe.add("transitional_item", stack(transitional, 1));
        return recipe;
    }

    /** A new recipe object that starts with both loaders' load conditions. */
    private static JsonObject conditions(String source, String method, List<String> mods) {
        JsonArray neo = new JsonArray();
        for (String mod : mods) {
            JsonObject loaded = new JsonObject();
            loaded.addProperty("type", "neoforge:mod_loaded");
            loaded.addProperty("modid", mod);
            neo.add(loaded);
        }
        neo.add(selected("type", source, method));

        JsonArray fabric = new JsonArray();
        if (!mods.isEmpty()) {
            JsonObject loaded = new JsonObject();
            loaded.addProperty("condition", "fabric:all_mods_loaded");
            loaded.add("values", strings(mods));
            fabric.add(loaded);
        }
        fabric.add(selected("condition", source, method));

        JsonObject recipe = new JsonObject();
        recipe.add("neoforge:conditions", neo);
        recipe.add("fabric:load_conditions", fabric);
        return recipe;
    }

    private static JsonObject selected(String typeKey, String source, String method) {
        JsonObject condition = new JsonObject();
        condition.addProperty(typeKey, MOD_ID + ":recipe_source");
        condition.addProperty("source", source);
        condition.addProperty("method", method);
        return condition;
    }

    private static JsonObject ingredient(String value, Set<String> items) {
        JsonObject ingredient = new JsonObject();
        if (value.startsWith("#")) {
            ingredient.addProperty("tag", value.substring(1));
        } else {
            ingredient.addProperty("item", value);
            items.add(value);
        }
        return ingredient;
    }

    private static JsonObject stack(String id, int count) {
        JsonObject stack = new JsonObject();
        stack.addProperty("id", id);
        if (count != 1) stack.addProperty("count", count);
        return stack;
    }

    private static JsonObject method(List<String> mods, Set<String> items) {
        JsonObject method = new JsonObject();
        method.add("mods", strings(mods));
        method.add("items", strings(items.stream().sorted().toList()));
        return method;
    }

    /** Create picks a sequenced assembly by its input and first step, so no two TVs may share both. */
    private static void checkAssembliesDistinct(Source source, List<Tv> tvs) {
        Map<String, String> seen = new HashMap<>();
        for (Tv tv : tvs) {
            Step first = tv.steps.get(0);
            String key = source.material(tv.assemblyInput, tv.id) + " / " + first.type + " "
                    + (first.role == null ? "" : source.material(first.role, tv.id));
            String other = seen.put(key, tv.id);
            if (other != null) {
                throw new IllegalArgumentException("Source " + source.id + ": the sequenced assemblies for " + other
                        + " and " + tv.id + " both start with " + key + ", so Create can't tell them apart. Change "
                        + "the input or first step in tvs.json, or the materials in sources.json.");
            }
        }
    }

    // --- input -----------------------------------------------------------------------------------------------

    private record Source(String id, String name, List<String> mods, Map<String, String> materials) {
        /** A role's material, or the value itself when it's already an item or tag id. */
        String material(String roleOrId, String tv) {
            if (roleOrId.contains(":")) return roleOrId;
            String value = materials.get(roleOrId);
            if (value == null) {
                throw new IllegalArgumentException("Source " + id + " has no material for role '" + roleOrId
                        + "' (used by " + tv + "), and neither do its parents.");
            }
            return value;
        }
    }

    private record Step(String type, String role) {}

    private record Tv(String id, List<String> pattern, Map<String, String> key, int craftingCount,
                      String assemblyInput, List<Step> steps, int loops, int assemblyCount) {
        String result() {
            return MOD_ID + ":" + id;
        }
    }

    private static List<Source> readSources(JsonObject root) {
        List<JsonObject> raw = new ArrayList<>();
        root.getAsJsonArray("sources").forEach(e -> raw.add(e.getAsJsonObject()));
        Map<String, JsonObject> byId = new LinkedHashMap<>();
        for (JsonObject source : raw) {
            String id = source.get("id").getAsString();
            if (!ID.matcher(id).matches() || id.equals("auto")) {
                throw new IllegalArgumentException("Bad source id '" + id + "': use a-z, 0-9 and _, and not 'auto'.");
            }
            if (byId.put(id, source) != null) throw new IllegalArgumentException("Duplicate source id " + id);
        }
        if (!byId.containsKey(VANILLA) || !raw.get(raw.size() - 1).get("id").getAsString().equals(VANILLA)) {
            throw new IllegalArgumentException("The last source must be 'vanilla', the fallback.");
        }

        List<Source> sources = new ArrayList<>();
        for (JsonObject source : raw) {
            String id = source.get("id").getAsString();
            Map<String, String> materials = new LinkedHashMap<>();
            Set<String> visited = new HashSet<>();
            // Walk the parent chain from the far end so nearer sources override.
            List<JsonObject> chain = new ArrayList<>();
            for (JsonObject at = source; at != null; ) {
                String atId = at.get("id").getAsString();
                if (!visited.add(atId)) throw new IllegalArgumentException("Parent loop at source " + atId);
                chain.add(0, at);
                String parent = at.has("parent") ? at.get("parent").getAsString()
                        : atId.equals(VANILLA) ? null : VANILLA;
                if (parent != null && !byId.containsKey(parent)) {
                    throw new IllegalArgumentException("Source " + atId + " has unknown parent " + parent);
                }
                at = parent == null ? null : byId.get(parent);
            }
            for (JsonObject at : chain) {
                for (Map.Entry<String, JsonElement> m : at.getAsJsonObject("materials").entrySet()) {
                    String value = m.getValue().getAsString();
                    if (!RESOURCE_ID.matcher(value).matches()) {
                        throw new IllegalArgumentException("Source " + at.get("id").getAsString() + ", role "
                                + m.getKey() + ": '" + value + "' isn't an item id or #tag id.");
                    }
                    materials.put(m.getKey(), value);
                }
            }
            List<String> mods = new ArrayList<>();
            source.getAsJsonArray("mods").forEach(e -> mods.add(e.getAsString()));
            sources.add(new Source(id, source.get("name").getAsString(), List.copyOf(mods), materials));
        }
        return sources;
    }

    private static List<Tv> readTvs(JsonObject root) {
        List<Tv> tvs = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("tvs")) {
            JsonObject tv = element.getAsJsonObject();
            String id = tv.get("id").getAsString();
            JsonObject crafting = tv.getAsJsonObject("crafting");
            List<String> pattern = new ArrayList<>();
            crafting.getAsJsonArray("pattern").forEach(e -> pattern.add(e.getAsString()));
            Map<String, String> key = new LinkedHashMap<>();
            crafting.getAsJsonObject("key").entrySet().forEach(e -> key.put(e.getKey(), e.getValue().getAsString()));

            JsonObject assembly = tv.getAsJsonObject(SEQUENCED_ASSEMBLY);
            List<Step> steps = new ArrayList<>();
            for (JsonElement s : assembly.getAsJsonArray("steps")) steps.add(step(id, s.getAsString()));
            if (steps.isEmpty()) throw new IllegalArgumentException(id + ": a sequenced assembly needs steps");
            tvs.add(new Tv(id, List.copyOf(pattern), key, count(crafting),
                    assembly.get("input").getAsString(), List.copyOf(steps),
                    assembly.has("loops") ? assembly.get("loops").getAsInt() : 1, count(assembly)));
        }
        return tvs;
    }

    private static Step step(String tv, String text) {
        String[] words = text.trim().split("\\s+");
        return switch (words[0]) {
            case "deploy" -> {
                if (words.length != 2) throw new IllegalArgumentException(tv + ": write 'deploy <role or item>'");
                yield new Step("deploying", words[1]);
            }
            case "press" -> new Step("pressing", null);
            case "cut" -> new Step("cutting", null);
            default -> throw new IllegalArgumentException(tv + ": unknown step '" + text
                    + "' (use 'deploy <role>', 'press' or 'cut')");
        };
    }

    private static int count(JsonObject object) {
        return object.has("count") ? object.get("count").getAsInt() : 1;
    }

    private static JsonArray strings(Iterable<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static String json(JsonElement element) {
        return GSON.toJson(element) + "\n";
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
