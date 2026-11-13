package com.dreamtvs.fabric;

import com.dreamtvs.DreamTvs;
import com.dreamtvs.compat.CompatRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.Nullable;

/**
 * {@code "fabric:load_conditions": [{"condition": "dreamtvs:recipe_source", "source": "mekanism", "method":
 * "crafting"}]}: the recipe loads only when it's the active compat recipe (docs/COMPAT_RECIPES_PLAN.md §5).
 */
public record RecipeSourceCondition(String source, String method) implements ResourceCondition {
    public static final MapCodec<RecipeSourceCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("source").forGetter(RecipeSourceCondition::source),
            Codec.STRING.fieldOf("method").forGetter(RecipeSourceCondition::method)
    ).apply(i, RecipeSourceCondition::new));
    public static final ResourceConditionType<RecipeSourceCondition> TYPE =
            ResourceConditionType.create(DreamTvs.id("recipe_source"), CODEC);

    @Override
    public ResourceConditionType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean test(@Nullable HolderLookup.Provider registryLookup) {
        return CompatRecipes.isSelected(source, method);
    }
}
