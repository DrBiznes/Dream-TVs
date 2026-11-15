package com.dreamtvs.compat;

import com.dreamtvs.compat.CompatResolver.Selection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static com.dreamtvs.compat.RecipeMethod.CRAFTING;
import static com.dreamtvs.compat.RecipeMethod.SEQUENCED_ASSEMBLY;
import static org.junit.jupiter.api.Assertions.*;

class CompatResolverTest {
    // A small stand-in for the real index: a Create addon, a standalone tech mod, Create, vanilla.
    private static final List<CompatSource> SOURCES = List.of(
            source("power_grid", List.of("create", "powergrid"), List.of("create", "powergrid"), "powergrid:wire"),
            source("mekanism", List.of("mekanism"), List.of("create", "mekanism"), "mekanism:ingot_steel"),
            source("create", List.of("create"), List.of("create"), "create:iron_sheet"),
            new CompatSource("vanilla", "Vanilla", Map.of(CRAFTING,
                    new CompatSource.Requirements(List.of(), List.of("minecraft:iron_ingot")))));

    private final List<String> warnings = new ArrayList<>();

    private static CompatSource source(String id, List<String> craftingMods, List<String> assemblyMods, String item) {
        return new CompatSource(id, "Name of " + id, Map.of(
                CRAFTING, new CompatSource.Requirements(craftingMods, List.of(item)),
                SEQUENCED_ASSEMBLY, new CompatSource.Requirements(assemblyMods,
                        List.of(item, "dreamtvs:incomplete_lcd_panel"))));
    }

    /** Resolve with these mods loaded; every item of a loaded mod (and dreamtvs/minecraft) exists. */
    private Selection resolve(String configured, boolean assembly, String... mods) {
        Set<String> loaded = Set.of(mods);
        Predicate<String> items = id -> {
            String ns = id.substring(0, id.indexOf(':'));
            return ns.equals("minecraft") || ns.equals("dreamtvs") || loaded.contains(ns);
        };
        return CompatResolver.resolve(configured, assembly, SOURCES, loaded::contains, items, warnings);
    }

    private static void assertSelected(String source, RecipeMethod method, Selection selection) {
        assertEquals(source, selection.source(), selection.toString());
        assertEquals(method, selection.method(), selection.toString());
    }

    @Test
    void noModsGivesVanilla() {
        assertSelected("vanilla", CRAFTING, resolve("auto", true));
        assertEquals(List.of(), warnings);
    }

    @Test
    void oneTechModWithoutCreateGivesItsCraftingRecipe() {
        assertSelected("mekanism", CRAFTING, resolve("auto", true, "mekanism"));
    }

    @Test
    void createAloneGivesCreateSequencedAssembly() {
        assertSelected("create", SEQUENCED_ASSEMBLY, resolve("auto", true, "create"));
    }

    @Test
    void createPlusTechModGivesAnAssemblyWithThatModsParts() {
        assertSelected("mekanism", SEQUENCED_ASSEMBLY, resolve("auto", true, "create", "mekanism"));
    }

    @Test
    void highestPriorityWinsRegardlessOfOrder() {
        assertSelected("power_grid", SEQUENCED_ASSEMBLY, resolve("auto", true, "mekanism", "powergrid", "create"));
    }

    @Test
    void sequencedAssemblyOffGivesCrafting() {
        assertSelected("mekanism", CRAFTING, resolve("auto", false, "create", "mekanism"));
        assertSelected("create", CRAFTING, resolve("auto", false, "create"));
    }

    @Test
    void configNamesALowerPrioritySource() {
        assertSelected("mekanism", SEQUENCED_ASSEMBLY, resolve("mekanism", true, "create", "powergrid", "mekanism"));
        assertEquals(List.of(), warnings);
    }

    @Test
    void configuredSourceWithoutCreateFallsBackToItsCrafting() {
        assertSelected("mekanism", CRAFTING, resolve("mekanism", true, "mekanism"));
        assertEquals(List.of(), warnings);
    }

    @Test
    void configNamesAModThatIsNotInstalled() {
        assertSelected("create", SEQUENCED_ASSEMBLY, resolve("mekanism", true, "create"));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("mekanism"), warnings.get(0));
    }

    @Test
    void unknownIdAndWrongCaseFallBackToAuto() {
        assertSelected("create", SEQUENCED_ASSEMBLY, resolve("mekansim", true, "create"));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("isn't a known source"), warnings.get(0));
        warnings.clear();
        assertSelected("mekanism", CRAFTING, resolve("  MEKANISM ", true, "mekanism"));
        assertEquals(List.of(), warnings);
    }

    @Test
    void vanillaWinsEvenWithModsInstalled() {
        assertSelected("vanilla", CRAFTING, resolve("vanilla", true, "create", "mekanism"));
    }

    @Test
    void missingOrEmptyConfigMeansAuto() {
        assertSelected("create", SEQUENCED_ASSEMBLY, resolve(null, true, "create"));
        assertSelected("create", SEQUENCED_ASSEMBLY, resolve("", true, "create"));
    }

    @Test
    void installedModWithMissingItemsIsSkippedWithAWarning() {
        // Mekanism is "loaded" but its item doesn't exist (renamed in this version): fall through to Create.
        Set<String> loaded = Set.of("create", "mekanism");
        Selection selection = CompatResolver.resolve("auto", true, SOURCES, loaded::contains,
                id -> !id.startsWith("mekanism:"), warnings);
        assertSelected("create", SEQUENCED_ASSEMBLY, selection);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("mekanism:ingot_steel"), warnings.get(0));
    }

    @Test
    void missingTransitionalItemsFallBackToCrafting() {
        // Create loaded but our in-progress items didn't register: no assembly can work, so craft instead.
        Set<String> loaded = Set.of("create");
        Selection selection = CompatResolver.resolve("auto", true, SOURCES, loaded::contains,
                id -> !id.startsWith("dreamtvs:"), warnings);
        assertSelected("create", CRAFTING, selection);
    }

    @Test
    void selectionMatchesByIds() {
        Selection selection = resolve("auto", true, "create");
        assertTrue(selection.matches("create", "sequenced_assembly"));
        assertFalse(selection.matches("create", "crafting"));
        assertFalse(selection.matches("vanilla", "crafting"));
    }
}
