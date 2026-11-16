package com.dreamtvs.config;

import com.dreamtvs.config.DreamTvsConfig.ExtendPolicy;
import com.dreamtvs.screen.ScreenKind;
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
        original.lcdMaxWidth = 10;
        original.lcdMaxHeight = 5;
        original.jumbotronMaxWidth = 40;
        original.jumbotronMaxHeight = 30;
        original.maxScreensPerPlayer = 3;
        original.remoteRange = 48;
        original.extendPolicy = ExtendPolicy.ANYONE;
        original.showFormMessages = false;
        original.recipeSource = "mekanism";
        original.createSequencedAssembly = false;

        DreamTvsConfig read = parse(original.toFileText());

        assertEquals(List.of(), warnings);
        assertEquals(10, read.lcdMaxWidth);
        assertEquals(5, read.lcdMaxHeight);
        assertEquals(40, read.jumbotronMaxWidth);
        assertEquals(30, read.jumbotronMaxHeight);
        assertEquals(3, read.maxScreensPerPlayer);
        assertEquals(48, read.remoteRange);
        assertEquals(ExtendPolicy.ANYONE, read.extendPolicy);
        assertFalse(read.showFormMessages);
        assertEquals("mekanism", read.recipeSource);
        assertFalse(read.createSequencedAssembly);
    }

    @Test
    void missingOptionsKeepTheirDefaults() {
        DreamTvsConfig read = parse("{ \"jumbotron\": { \"maxWidth\": 8 } }");
        assertEquals(List.of(), warnings);
        assertEquals(8, read.jumbotronMaxWidth);
        assertEquals(24, read.jumbotronMaxHeight);
        assertEquals(28, read.lcdMaxWidth);
        assertEquals(21, read.lcdMaxHeight);
        assertEquals(ExtendPolicy.OWNER, read.extendPolicy);
        assertTrue(read.showFormMessages);
        assertEquals(0, read.remoteRange);
    }

    @Test
    void outOfRangeNumbersAreClamped() {
        DreamTvsConfig read = parse("""
                { "lcdPanel": { "maxWidth": 500, "maxHeight": 0 }, "maxScreensPerPlayer": -4, "remoteRange": 5000 }
                """);
        assertEquals(DreamTvsConfig.MAX_SIDE, read.lcdMaxWidth);
        assertEquals(1, read.lcdMaxHeight);
        assertEquals(0, read.maxScreensPerPlayer);
        assertEquals(DreamTvsConfig.MAX_REMOTE_RANGE, read.remoteRange);
        assertEquals(4, warnings.size());
    }

    @Test
    void wrongTypesFallBackPerOption() {
        DreamTvsConfig read = parse("""
                { "lcdPanel": { "maxWidth": "big", "maxHeight": 2.5 }, "maxScreensPerPlayer": [1],
                  "extendPolicy": "everyone", "showFormMessages": "yes" }
                """);
        assertEquals(28, read.lcdMaxWidth);
        assertEquals(21, read.lcdMaxHeight);
        assertEquals(0, read.maxScreensPerPlayer);
        assertEquals(ExtendPolicy.OWNER, read.extendPolicy);
        assertTrue(read.showFormMessages);
        assertEquals(5, warnings.size());
    }

    @Test
    void wholeNumbersWrittenAsDecimalsAreAccepted() {
        assertEquals(16, parse("{ \"lcdPanel\": { \"maxWidth\": 16.0 } }").lcdMaxWidth);
        assertEquals(List.of(), warnings);
    }

    @Test
    void policyIsCaseInsensitive() {
        assertEquals(ExtendPolicy.ANYONE, parse("{ \"extendPolicy\": \" anyone \" }").extendPolicy);
        assertEquals(List.of(), warnings);
    }

    @Test
    void unknownOptionsAreReported() {
        parse("{ \"maxWidht\": 8, \"jumbotron\": { \"width\": 8 } }");
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0).contains("maxWidht"));
        assertTrue(warnings.get(1).contains("jumbotron.width"));
    }

    @Test
    void lcdPanelsDefaultToSlightlySmallerThanJumbotrons() {
        DreamTvsConfig c = new DreamTvsConfig();
        assertTrue(c.lcdMaxWidth < c.jumbotronMaxWidth && c.lcdMaxHeight < c.jumbotronMaxHeight);
        assertEquals(c.lcdMaxWidth, c.maxWidth(ScreenKind.LCD_PANEL));
        assertEquals(c.jumbotronMaxHeight, c.maxHeight(ScreenKind.JUMBOTRON));
        assertEquals(28 * 21 + 1, c.scanCap(ScreenKind.LCD_PANEL));
    }

    @Test
    void notAnObjectIsAnError() {
        assertThrows(JsonParseException.class, () -> parse(""));
        assertThrows(JsonParseException.class, () -> parse("[1, 2]"));
        assertThrows(JsonParseException.class, () -> parse("{ \"remoteRange\": "));
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
        edited.lcdMaxWidth = 12;
        edited.lcdMaxHeight = 500; // out of range: fixed like a hand-edited file
        edited.recipeSource = "mekanism";

        DreamTvsConfig.save(file, edited);

        assertEquals(12, DreamTvsConfig.get().lcdMaxWidth);
        assertEquals(DreamTvsConfig.MAX_SIDE, DreamTvsConfig.get().lcdMaxHeight);
        DreamTvsConfig read = DreamTvsConfig.readForEditing(file);
        assertNotNull(read);
        assertEquals(12, read.lcdMaxWidth);
        assertEquals(DreamTvsConfig.MAX_SIDE, read.lcdMaxHeight);
        assertEquals("mekanism", read.recipeSource);
        DreamTvsConfig.save(file, new DreamTvsConfig()); // put the defaults back for the other tests
    }

    @Test
    void readForEditingGivesDefaultsForAMissingFileAndNullForABrokenOne(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("dreamtvs.json");
        DreamTvsConfig missing = DreamTvsConfig.readForEditing(file);
        assertNotNull(missing);
        assertEquals(32, missing.jumbotronMaxWidth);

        Files.writeString(file, "{ \"remoteRange\": ", StandardCharsets.UTF_8);
        assertNull(DreamTvsConfig.readForEditing(file));
    }
}
