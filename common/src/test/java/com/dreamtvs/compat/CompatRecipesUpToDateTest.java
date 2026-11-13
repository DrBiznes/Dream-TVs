package com.dreamtvs.compat;

import com.dreamtvs.compatgen.CompatRecipeGenerator;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The committed files in src/generated/resources must match what the generator makes from src/compat. */
class CompatRecipesUpToDateTest {
    private static final Path SOURCES = Path.of("src/compat");
    private static final Path GENERATED = Path.of("src/generated/resources");
    private static final String FIX = " Run 'gradlew :common:generateCompatRecipes' and commit the result.";

    private static Map<String, String> generate() throws IOException {
        return CompatRecipeGenerator.generate(
                Files.readString(SOURCES.resolve("sources.json"), StandardCharsets.UTF_8),
                Files.readString(SOURCES.resolve("tvs.json"), StandardCharsets.UTF_8));
    }

    @Test
    void generatedFilesAreUpToDate() throws IOException {
        Map<String, String> expected = generate();
        Set<String> actual = new TreeSet<>();
        try (Stream<Path> files = Files.walk(GENERATED)) {
            files.filter(Files::isRegularFile)
                    .forEach(p -> actual.add(GENERATED.relativize(p).toString().replace('\\', '/')));
        }
        assertEquals(new TreeSet<>(expected.keySet()), actual, "Generated file list is out of date." + FIX);
        for (Map.Entry<String, String> file : expected.entrySet()) {
            String onDisk = Files.readString(GENERATED.resolve(file.getKey()), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            assertEquals(file.getValue(), onDisk, file.getKey() + " is out of date." + FIX);
        }
    }

    @Test
    void everyRecipeHasExactlyOneConditionBlockPerLoader() throws IOException {
        for (Map.Entry<String, String> file : generate().entrySet()) {
            if (!file.getKey().startsWith(CompatRecipeGenerator.RECIPE_DIR)) continue;
            JsonObject recipe = JsonParser.parseString(file.getValue()).getAsJsonObject();
            for (String key : List.of("neoforge:conditions", "fabric:load_conditions")) {
                long ours = recipe.getAsJsonArray(key).asList().stream()
                        .map(e -> e.getAsJsonObject())
                        .filter(c -> "dreamtvs:recipe_source".equals(
                                (c.has("type") ? c.get("type") : c.get("condition")).getAsString()))
                        .count();
                assertEquals(1, ours, file.getKey() + " " + key);
            }
        }
    }

    @Test
    void vanillaRecipesKeepTheirOriginalIds() throws IOException {
        Map<String, String> files = generate();
        for (String tv : List.of("lcd_panel", "jumbotron", "crt_tv", "flat_screen_2x1", "flat_screen_3x2")) {
            assertTrue(files.containsKey("data/dreamtvs/recipe/" + tv + ".json"), tv);
        }
    }

    @Test
    void shippedIndexParsesAndEndsWithVanilla() {
        List<CompatSource> sources = CompatIndex.sources();
        assertEquals("power_grid", sources.get(0).id());
        assertEquals("vanilla", sources.get(sources.size() - 1).id());
        assertNull(sources.get(sources.size() - 1).requirements(RecipeMethod.SEQUENCED_ASSEMBLY));
        for (CompatSource source : sources) {
            CompatSource.Requirements assembly = source.requirements(RecipeMethod.SEQUENCED_ASSEMBLY);
            if (assembly != null) assertTrue(assembly.mods().contains("create"), source.id());
        }
        assertEquals(Set.of("incomplete_lcd_panel", "incomplete_jumbotron", "incomplete_crt_tv",
                        "incomplete_flat_screen_2x1", "incomplete_flat_screen_3x2"),
                CompatIndex.transitionalItems(sources));
    }

    @Test
    void sharedAssemblyStartIsRejected() {
        String sources = """
                { "sources": [ { "id": "vanilla", "name": "V", "mods": [],
                  "materials": { "frame": "minecraft:iron_ingot", "screen": "minecraft:glass_pane" } } ] }
                """;
        String vanillaOnly = """
                { "tvs": [
                  { "id": "a", "crafting": { "pattern": ["I"], "key": { "I": "frame" } },
                    "sequenced_assembly": { "input": "frame", "steps": ["deploy screen"] } } ] }
                """;
        // Vanilla has no assemblies, so even clashing templates are fine there.
        assertDoesNotThrow(() -> CompatRecipeGenerator.generate(sources, vanillaOnly));

        String withCreate = """
                { "sources": [
                  { "id": "create", "name": "C", "mods": ["create"], "materials": {} },
                  { "id": "vanilla", "name": "V", "mods": [],
                    "materials": { "frame": "minecraft:iron_ingot", "screen": "minecraft:glass_pane" } } ] }
                """;
        String clashing = """
                { "tvs": [
                  { "id": "a", "crafting": { "pattern": ["I"], "key": { "I": "frame" } },
                    "sequenced_assembly": { "input": "frame", "steps": ["deploy screen"] } },
                  { "id": "b", "crafting": { "pattern": ["II"], "key": { "I": "frame" } },
                    "sequenced_assembly": { "input": "frame", "steps": ["deploy screen", "press"] } } ] }
                """;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CompatRecipeGenerator.generate(withCreate, clashing));
        assertTrue(e.getMessage().contains("can't tell them apart"), e.getMessage());
    }
}
