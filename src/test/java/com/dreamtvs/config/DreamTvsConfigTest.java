package com.dreamtvs.config;

import com.dreamtvs.config.DreamTvsConfig.ExtendPolicy;
import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DreamTvsConfigTest {
    private final List<String> warnings = new ArrayList<>();

    private DreamTvsConfig parse(String text) {
        return DreamTvsConfig.parse(text, warnings);
    }

    @Test
    void writtenFileReadsBackWithoutWarnings() {
        DreamTvsConfig original = new DreamTvsConfig();
        original.maxWidth = 10;
        original.maxHeight = 5;
        original.maxScreensPerPlayer = 3;
        original.extendPolicy = ExtendPolicy.ANYONE;
        original.showFormMessages = false;
        original.recipeSource = "mekanism";
        original.createSequencedAssembly = false;

        DreamTvsConfig read = parse(original.toFileText());

        assertEquals(List.of(), warnings);
        assertEquals(10, read.maxWidth);
        assertEquals(5, read.maxHeight);
        assertEquals(3, read.maxScreensPerPlayer);
        assertEquals(ExtendPolicy.ANYONE, read.extendPolicy);
        assertFalse(read.showFormMessages);
        assertEquals("mekanism", read.recipeSource);
        assertFalse(read.createSequencedAssembly);
    }

    @Test
    void missingOptionsKeepTheirDefaults() {
        DreamTvsConfig read = parse("{ \"maxWidth\": 8 }");
        assertEquals(List.of(), warnings);
        assertEquals(8, read.maxWidth);
        assertEquals(24, read.maxHeight);
        assertEquals(ExtendPolicy.OWNER, read.extendPolicy);
        assertTrue(read.showFormMessages);
    }

    @Test
    void outOfRangeNumbersAreClamped() {
        DreamTvsConfig read = parse("{ \"maxWidth\": 500, \"maxHeight\": 0, \"maxScreensPerPlayer\": -4 }");
        assertEquals(DreamTvsConfig.MAX_SIDE, read.maxWidth);
        assertEquals(1, read.maxHeight);
        assertEquals(0, read.maxScreensPerPlayer);
        assertEquals(3, warnings.size());
    }

    @Test
    void wrongTypesFallBackPerOption() {
        DreamTvsConfig read = parse("""
                { "maxWidth": "big", "maxHeight": 2.5, "maxScreensPerPlayer": [1],
                  "extendPolicy": "everyone", "showFormMessages": "yes" }
                """);
        assertEquals(32, read.maxWidth);
        assertEquals(24, read.maxHeight);
        assertEquals(0, read.maxScreensPerPlayer);
        assertEquals(ExtendPolicy.OWNER, read.extendPolicy);
        assertTrue(read.showFormMessages);
        assertEquals(5, warnings.size());
    }

    @Test
    void wholeNumbersWrittenAsDecimalsAreAccepted() {
        assertEquals(16, parse("{ \"maxWidth\": 16.0 }").maxWidth);
        assertEquals(List.of(), warnings);
    }

    @Test
    void policyIsCaseInsensitive() {
        assertEquals(ExtendPolicy.ANYONE, parse("{ \"extendPolicy\": \" anyone \" }").extendPolicy);
        assertEquals(List.of(), warnings);
    }

    @Test
    void unknownOptionsAreReported() {
        parse("{ \"maxWidht\": 8 }");
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("maxWidht"));
    }

    @Test
    void notAnObjectIsAnError() {
        assertThrows(JsonParseException.class, () -> parse(""));
        assertThrows(JsonParseException.class, () -> parse("[1, 2]"));
        assertThrows(JsonParseException.class, () -> parse("{ \"maxWidth\": "));
    }

    @Test
    void compatDefaultsWhenMissing() {
        DreamTvsConfig read = parse("{}");
        assertEquals("auto", read.recipeSource);
        assertTrue(read.createSequencedAssembly);
        assertEquals(List.of(), warnings);
    }

    @Test
    void compatOptionsAreReadAndNormalized() {
        DreamTvsConfig read = parse("{ \"compat\": { \"recipeSource\": \" Power_Grid \", \"createSequencedAssembly\": false } }");
        assertEquals("power_grid", read.recipeSource);
        assertFalse(read.createSequencedAssembly);
        assertEquals(List.of(), warnings);
    }

    @Test
    void badCompatValuesFallBack() {
        DreamTvsConfig read = parse("{ \"compat\": { \"recipeSource\": 3, \"createSequencedAssembly\": \"no\", \"extra\": 1 } }");
        assertEquals("auto", read.recipeSource);
        assertTrue(read.createSequencedAssembly);
        assertEquals(3, warnings.size());
        warnings.clear();
        parse("{ \"compat\": \"mekanism\" }");
        assertEquals(1, warnings.size());
    }

    @Test
    void fileListsTheRecipeSources() {
        String text = new DreamTvsConfig().toFileText();
        assertTrue(text.contains("mekanism"), text);
        assertTrue(text.contains("Create: Power Grid"), text);
    }

    @Test
    void saveWritesTheFileAndUsesTheValues(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config").resolve("dreamtvs.json");
        DreamTvsConfig edited = new DreamTvsConfig();
        edited.maxWidth = 12;
        edited.maxHeight = 500; // out of range: fixed like a hand-edited file
        edited.recipeSource = "mekanism";

        DreamTvsConfig.save(file, edited);

        assertEquals(12, DreamTvsConfig.get().maxWidth);
        assertEquals(DreamTvsConfig.MAX_SIDE, DreamTvsConfig.get().maxHeight);
        DreamTvsConfig read = DreamTvsConfig.readForEditing(file);
        assertNotNull(read);
        assertEquals(12, read.maxWidth);
        assertEquals(DreamTvsConfig.MAX_SIDE, read.maxHeight);
        assertEquals("mekanism", read.recipeSource);
        DreamTvsConfig.save(file, new DreamTvsConfig()); // put the defaults back for the other tests
    }

    @Test
    void readForEditingGivesDefaultsForAMissingFileAndNullForABrokenOne(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("dreamtvs.json");
        DreamTvsConfig missing = DreamTvsConfig.readForEditing(file);
        assertNotNull(missing);
        assertEquals(32, missing.maxWidth);

        Files.writeString(file, "{ \"maxWidth\": ", StandardCharsets.UTF_8);
        assertNull(DreamTvsConfig.readForEditing(file));
    }
}
