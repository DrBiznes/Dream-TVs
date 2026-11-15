package com.dreamtvs.neoforge;

//? if neoforge {
import com.dreamtvs.compat.CompatRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * {@code "neoforge:conditions": [{"type": "dreamtvs:recipe_source", "source": "mekanism", "method":
 * "crafting"}]}: the recipe loads only when it's the active compat recipe (docs/COMPAT_RECIPES_PLAN.md §5).
 */
public record RecipeSourceCondition(String source, String method) implements ICondition {
    public static final MapCodec<RecipeSourceCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("source").forGetter(RecipeSourceCondition::source),
            Codec.STRING.fieldOf("method").forGetter(RecipeSourceCondition::method)
    ).apply(i, RecipeSourceCondition::new));

    @Override
    public boolean test(IContext context) {
        return CompatRecipes.isSelected(source, method);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "dreamtvs:recipe_source(" + source + ", " + method + ")";
    }
}
//?}
