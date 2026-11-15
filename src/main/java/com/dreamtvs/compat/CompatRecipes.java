package com.dreamtvs.compat;

import com.dreamtvs.DreamTvs;
import com.dreamtvs.config.DreamTvsConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Answers the {@code dreamtvs:recipe_source} load condition that every TV recipe carries, so exactly one source
 * and method is active (docs/COMPAT_RECIPES_PLAN.md §5). The choice is made once per data load: the first
 * condition test resolves it, and it's resolved again after {@link #invalidate} (each loader calls that when a
 * reload starts) or when the config file changes.
 */
public final class CompatRecipes {
    private static Predicate<String> modLoaded = id -> false;
    private static Path configFile;

    private static CompatResolver.Selection selection;
    private static Object selectionStamp;

    private CompatRecipes() {}

    /** Called once by each loader at mod construction. */
    public static void init(Predicate<String> modLoaded) {
        CompatRecipes.modLoaded = Objects.requireNonNull(modLoaded);
        configFile = DreamTvs.configFile();
    }

    /** The condition's test: is this recipe's source and method the active one? */
    public static boolean isSelected(String source, String method) {
        return selection().matches(source, method);
    }

    /** Forget the choice; the next condition test makes it again and logs it. */
    public static synchronized void invalidate() {
        selection = null;
    }

    /** Whether Create is installed, so the in-progress items for sequenced assembly should be registered. */
    public static boolean createLoaded() {
        return modLoaded.test("create");
    }

    /** Whether the source's mods are installed, for the config screen. */
    public static boolean isInstalled(CompatSource source) {
        return CompatResolver.isInstalled(source, modLoaded);
    }

    public static synchronized CompatResolver.Selection selection() {
        Object stamp = stamp();
        if (selection == null || !stamp.equals(selectionStamp)) {
            DreamTvsConfig config = configFile == null ? new DreamTvsConfig() : DreamTvsConfig.readQuietly(configFile);
            List<String> warnings = new ArrayList<>();
            List<CompatSource> sources;
            try {
                sources = CompatIndex.sources();
            } catch (RuntimeException e) {
                DreamTvs.LOGGER.error("Dream TVs: couldn't read the recipe sources; only the vanilla recipes will load", e);
                sources = List.of();
            }
            CompatResolver.Selection chosen = sources.isEmpty()
                    ? new CompatResolver.Selection(CompatSource.VANILLA, RecipeMethod.CRAFTING, "no sources")
                    : CompatResolver.resolve(config.recipeSource, config.createSequencedAssembly, sources,
                            modLoaded, CompatRecipes::itemExists, warnings);
            for (String warning : warnings) DreamTvs.LOGGER.warn("Dream TVs recipes: {}", warning);
            DreamTvs.LOGGER.info("Dream TVs recipes: {} ({}) [{}]", chosen.source(), chosen.method().id(),
                    chosen.reason());
            selection = chosen;
            selectionStamp = stamp;
        }
        return selection;
    }

    private static boolean itemExists(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location != null && BuiltInRegistries.ITEM.containsKey(location);
    }

    /** Changes when the config file is edited, created or deleted. */
    private static Object stamp() {
        if (configFile == null) return List.of();
        try {
            if (!Files.exists(configFile)) return List.of();
            return List.of(Files.getLastModifiedTime(configFile), Files.size(configFile));
        } catch (IOException e) {
            return List.of();
        }
    }
}
