package com.dreamtvs.neoforge.gametest;

import com.dreamtvs.DreamTvs;
import com.dreamtvs.McCompat;
import com.dreamtvs.compat.CompatRecipes;
import com.dreamtvs.compat.CompatResolver;
import com.dreamtvs.compat.CompatSource;
import com.dreamtvs.compat.RecipeMethod;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Compat recipes (docs/COMPAT_RECIPES_PLAN.md): exactly one recipe per TV loads, from the selected source. The
 * plain GameTest run has no tech mods, so that's vanilla with the original ids; with Create in
 * {@code run/neoforge-<version>-gametest/mods} it's Create's sequenced assemblies.
 */
public final class CompatRecipeGameTests {
    private static final List<String> TVS = List.of("lcd_panel", "jumbotron", "crt_tv", "flat_screen_2x1", "flat_screen_3x2");

    @TvTest
    public static void oneRecipePerTvFromTheSelectedSource(GameTestHelper h) {
        CompatResolver.Selection selection = CompatRecipes.selection();
        if (!ModList.get().isLoaded("create")) {
            h.assertTrue(selection.source().equals(CompatSource.VANILLA) && selection.method() == RecipeMethod.CRAFTING,
                    "without tech mods expected vanilla crafting, got " + selection);
        }

        Set<String> expected = TVS.stream()
                .map(tv -> selection.source().equals(CompatSource.VANILLA) ? DreamTvs.id(tv).toString()
                        : DreamTvs.id("compat/" + selection.source() + "/" + selection.method().id() + "/" + tv).toString())
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> loaded = h.getLevel().getServer().getRecipeManager().getRecipes().stream()
                .map(CompatRecipeGameTests::id)
                .filter(id -> id.startsWith(DreamTvs.MOD_ID + ":"))
                .collect(Collectors.toCollection(TreeSet::new));
        h.assertTrue(loaded.equals(expected), "for " + selection + " expected recipes " + expected + ", got " + loaded);
        h.succeed();
    }

    private static String id(RecipeHolder<?> recipe) {
        //? if >=1.21.2 {
        /*return McCompat.keyId(recipe.id());
        *///?} else
        return recipe.id().toString();
    }
}
