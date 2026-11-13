package com.dreamtvs.compat;

import java.util.List;
import java.util.Map;

/**
 * One recipe source from the generated index: whose parts the TV recipes use (a tech mod, Create, or vanilla).
 *
 * @param id      the id used in the config and in the {@code dreamtvs:recipe_source} condition
 * @param name    a display name for log lines
 * @param methods what each method needs; a source without a method has no recipes for it
 */
public record CompatSource(String id, String name, Map<RecipeMethod, Requirements> methods) {
    public static final String VANILLA = "vanilla";

    /**
     * @param mods  mod ids that must all be loaded
     * @param items item ids that must all exist (tags aren't listed: they can't be checked this early)
     */
    public record Requirements(List<String> mods, List<String> items) {}

    public Requirements requirements(RecipeMethod method) {
        return methods.get(method);
    }
}
