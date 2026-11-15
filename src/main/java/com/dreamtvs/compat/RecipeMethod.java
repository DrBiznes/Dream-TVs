package com.dreamtvs.compat;

/** How a TV is made. The names match the {@code method} field of the {@code dreamtvs:recipe_source} condition. */
public enum RecipeMethod {
    /** A shaped crafting-table recipe. */
    CRAFTING("crafting"),
    /** A Create sequenced assembly (needs Create). */
    SEQUENCED_ASSEMBLY("sequenced_assembly");

    private final String id;

    RecipeMethod(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** The method with this id, or {@code null}. */
    public static RecipeMethod byId(String id) {
        for (RecipeMethod method : values()) {
            if (method.id.equals(id)) return method;
        }
        return null;
    }
}
