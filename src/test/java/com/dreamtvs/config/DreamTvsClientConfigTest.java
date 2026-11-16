package com.dreamtvs.config;

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

class DreamTvsClientConfigTest {
    private final List<String> warnings = new ArrayList<>();

    @Test
    void crtOverlayIsOnByDefault() {
        assertTrue(DreamTvsClientConfig.parse("{}", warnings).crtOverlay);
        assertEquals(List.of(), warnings);
    }

    @Test
    void writtenFileReadsBackWithoutWarnings() {
        DreamTvsClientConfig original = new DreamTvsClientConfig();
        original.crtOverlay = false;
        assertFalse(DreamTvsClientConfig.parse(original.toFileText(), warnings).crtOverlay);
        assertEquals(List.of(), warnings);
    }

    @Test
    void badValuesFallBackAndUnknownOptionsAreReported() {
        DreamTvsClientConfig read = DreamTvsClientConfig.parse("{ \"crtOverlay\": \"off\", \"scanlines\": 1 }", warnings);
        assertTrue(read.crtOverlay);
        assertEquals(2, warnings.size());
        assertThrows(JsonParseException.class, () -> DreamTvsClientConfig.parse("[]", warnings));
    }

    @Test
    void loadWritesTheDefaultsAndSaveUsesTheValues(@TempDir Path dir) throws IOException {
        DreamTvsClientConfig.load(dir);
        Path file = dir.resolve(DreamTvsClientConfig.FILE_NAME);
        assertEquals(file, DreamTvsClientConfig.file());
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("\"crtOverlay\": true"));

        DreamTvsClientConfig edited = DreamTvsClientConfig.get().copy();
        edited.crtOverlay = false;
        DreamTvsClientConfig.save(edited);
        assertFalse(DreamTvsClientConfig.get().crtOverlay);
        DreamTvsClientConfig.load(dir);
        assertFalse(DreamTvsClientConfig.get().crtOverlay);
    }
}
