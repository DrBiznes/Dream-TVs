package com.dreamtvs.config;

import com.dreamtvs.config.DreamTvsConfig.ExtendPolicy;
import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

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

        DreamTvsConfig read = parse(original.toFileText());

        assertEquals(List.of(), warnings);
        assertEquals(10, read.maxWidth);
        assertEquals(5, read.maxHeight);
        assertEquals(3, read.maxScreensPerPlayer);
        assertEquals(ExtendPolicy.ANYONE, read.extendPolicy);
        assertFalse(read.showFormMessages);
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
}
