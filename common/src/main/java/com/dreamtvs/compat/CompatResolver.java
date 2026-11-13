package com.dreamtvs.compat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Picks the one recipe source and method the TVs use (docs/COMPAT_RECIPES_PLAN.md §5). Pure: no Minecraft or
 * loader classes, so it's unit-tested without a game.
 *
 * <ol>
 *   <li>{@code "vanilla"}: the vanilla crafting recipes.</li>
 *   <li>A source id: that source, if its mods are loaded and its items exist. Otherwise a warning, then auto.</li>
 *   <li>{@code "auto"} (or an unusable value): the highest-priority usable source, vanilla if none.</li>
 * </ol>
 * With {@code sequencedAssembly} on, a Create sequenced assembly is preferred over crafting: every source is
 * tried as an assembly first, then every source as a crafting recipe. A source counts as usable only when
 * <em>all</em> its items exist, so a renamed or missing item (another game version, an older mod) moves on to
 * the next source instead of leaving a TV with no recipe.
 */
public final class CompatResolver {
    public static final String AUTO = "auto";

    /** The chosen source and method, and a short reason for the log. */
    public record Selection(String source, RecipeMethod method, String reason) {
        public boolean matches(String source, String method) {
            return this.source.equals(source) && this.method.id().equals(method);
        }
    }

    private CompatResolver() {}

    /**
     * @param configured        {@code compat.recipeSource} from the config ({@code null} means auto)
     * @param sequencedAssembly {@code compat.createSequencedAssembly}
     * @param sources           the sources, highest priority first, ending with vanilla
     * @param warnings          receives a line for each config problem, and for each installed mod whose
     *                          recipe can't be used because items are missing
     */
    public static Selection resolve(String configured, boolean sequencedAssembly, List<CompatSource> sources,
                                    Predicate<String> modLoaded, Predicate<String> itemExists, List<String> warnings) {
        Check check = new Check(modLoaded, itemExists);
        List<RecipeMethod> methods = sequencedAssembly
                ? List.of(RecipeMethod.SEQUENCED_ASSEMBLY, RecipeMethod.CRAFTING)
                : List.of(RecipeMethod.CRAFTING);
        String wanted = configured == null ? AUTO : configured.trim().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) wanted = AUTO;

        try {
            if (wanted.equals(CompatSource.VANILLA)) {
                return new Selection(CompatSource.VANILLA, RecipeMethod.CRAFTING, "recipeSource is \"vanilla\"");
            }
            if (!wanted.equals(AUTO)) {
                CompatSource forced = find(sources, wanted);
                if (forced == null) {
                    warnings.add("recipeSource \"" + wanted + "\" isn't a known source. Use \"auto\" or one of: "
                            + String.join(", ", ids(sources)) + ". Using \"auto\".");
                } else {
                    for (RecipeMethod method : methods) {
                        if (check.usable(forced, method)) {
                            return new Selection(forced.id(), method, "recipeSource is \"" + wanted + "\"");
                        }
                    }
                    warnings.add("recipeSource is \"" + wanted + "\" but " + forced.name() + " can't be used ("
                            + check.whyNot(forced, methods.get(methods.size() - 1)) + "). Using \"auto\".");
                }
            }

            for (RecipeMethod method : methods) {
                for (CompatSource source : sources) {
                    if (source.id().equals(CompatSource.VANILLA)) continue;
                    if (check.usable(source, method)) {
                        return new Selection(source.id(), method, "auto, installed: "
                                + String.join(", ", installed(sources, modLoaded)));
                    }
                }
            }
            return new Selection(CompatSource.VANILLA, RecipeMethod.CRAFTING, "auto, no supported mod installed");
        } finally {
            warnings.addAll(check.notes);
        }
    }

    private static CompatSource find(List<CompatSource> sources, String id) {
        for (CompatSource source : sources) {
            if (source.id().equals(id)) return source;
        }
        return null;
    }

    private static List<String> ids(List<CompatSource> sources) {
        return sources.stream().map(CompatSource::id).toList();
    }

    /** Non-vanilla sources whose mods are all loaded for at least one method. */
    private static List<String> installed(List<CompatSource> sources, Predicate<String> modLoaded) {
        List<String> ids = new ArrayList<>();
        for (CompatSource source : sources) {
            if (source.id().equals(CompatSource.VANILLA)) continue;
            boolean any = source.methods().values().stream()
                    .anyMatch(r -> r.mods().stream().allMatch(modLoaded));
            if (any) ids.add(source.id());
        }
        return ids;
    }

    private static final class Check {
        final Predicate<String> modLoaded;
        final Predicate<String> itemExists;
        final Set<String> notes = new LinkedHashSet<>();

        Check(Predicate<String> modLoaded, Predicate<String> itemExists) {
            this.modLoaded = modLoaded;
            this.itemExists = itemExists;
        }

        boolean usable(CompatSource source, RecipeMethod method) {
            CompatSource.Requirements needs = source.requirements(method);
            if (needs == null || !needs.mods().stream().allMatch(modLoaded)) return false;
            List<String> missing = needs.items().stream().filter(itemExists.negate()).toList();
            if (missing.isEmpty()) return true;
            notes.add(source.name() + " is installed, but its " + method.id() + " recipes use items that don't "
                    + "exist: " + String.join(", ", missing) + ". Skipping it.");
            return false;
        }

        String whyNot(CompatSource source, RecipeMethod method) {
            CompatSource.Requirements needs = source.requirements(method);
            if (needs == null) return "it has no " + method.id() + " recipes";
            List<String> mods = needs.mods().stream().filter(modLoaded.negate()).toList();
            if (!mods.isEmpty()) return "needs " + String.join(", ", mods);
            return "items missing";
        }
    }
}
