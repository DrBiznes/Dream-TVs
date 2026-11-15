package com.dreamtvs.item;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

//? if >=26.1 {
/*import com.mojang.serialization.MapCodec;
import net.minecraft.network.codec.StreamCodec;
*///?} else {
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.CraftingBookCategory;
//?}
//? if <1.21.2
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

/**
 * The two crafting-grid tricks for remotes, as special recipes ({@code dreamtvs:remote_copy} and
 * {@code dreamtvs:remote_unbind}):
 * <ul>
 * <li>copy: a bound remote and an unbound one give a second remote bound to the same TV; the original stays
 * in the grid, like copying a written book.</li>
 * <li>unbind: a bound remote on its own gives an unbound remote.</li>
 * </ul>
 */
public final class RemoteRecipe extends CustomRecipe {
    public enum Kind { COPY, UNBIND }

    //? if >=26.1 {
    /*public static final RecipeSerializer<RemoteRecipe> COPY_SERIALIZER = serializer(new RemoteRecipe(Kind.COPY));
    public static final RecipeSerializer<RemoteRecipe> UNBIND_SERIALIZER = serializer(new RemoteRecipe(Kind.UNBIND));

    private static RecipeSerializer<RemoteRecipe> serializer(RemoteRecipe recipe) {
        return new RecipeSerializer<>(MapCodec.unit(recipe), StreamCodec.unit(recipe));
    }
    *///?} elif >=1.21.2 {
    /*public static final RecipeSerializer<RemoteRecipe> COPY_SERIALIZER =
            new CustomRecipe.Serializer<>(category -> new RemoteRecipe(category, Kind.COPY));
    public static final RecipeSerializer<RemoteRecipe> UNBIND_SERIALIZER =
            new CustomRecipe.Serializer<>(category -> new RemoteRecipe(category, Kind.UNBIND));
    *///?} else {
    public static final RecipeSerializer<RemoteRecipe> COPY_SERIALIZER =
            new SimpleCraftingRecipeSerializer<>(category -> new RemoteRecipe(category, Kind.COPY));
    public static final RecipeSerializer<RemoteRecipe> UNBIND_SERIALIZER =
            new SimpleCraftingRecipeSerializer<>(category -> new RemoteRecipe(category, Kind.UNBIND));
    //?}

    private final Kind kind;

    //? if >=26.1 {
    /*private RemoteRecipe(Kind kind) {
        this.kind = kind;
    }
    *///?} else {
    private RemoteRecipe(CraftingBookCategory category, Kind kind) {
        super(category);
        this.kind = kind;
    }
    //?}

    /** What's in the grid, if it's nothing but remotes. */
    private record Grid(int boundSlot, int bound, int unbound) {
        static Grid of(CraftingInput input) {
            int boundSlot = -1, bound = 0, unbound = 0;
            for (int slot = 0; slot < input.size(); slot++) {
                ItemStack stack = input.getItem(slot);
                if (stack.isEmpty()) continue;
                if (!(stack.getItem() instanceof RemoteItem)) return new Grid(-1, 0, 0);
                if (RemoteItem.isBound(stack)) {
                    bound++;
                    boundSlot = slot;
                } else {
                    unbound++;
                }
            }
            return new Grid(boundSlot, bound, unbound);
        }

        boolean matches(Kind kind) {
            return bound == 1 && unbound == (kind == Kind.COPY ? 1 : 0);
        }
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return Grid.of(input).matches(kind);
    }

    @Override
    //? if >=26.1 {
    /*public ItemStack assemble(CraftingInput input) {
    *///?} else
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Grid grid = Grid.of(input);
        if (!grid.matches(kind)) return ItemStack.EMPTY;
        ItemStack result = input.getItem(grid.boundSlot()).copyWithCount(1);
        if (kind == Kind.UNBIND) RemoteItem.unbind(result);
        return result;
    }

    /** Copying keeps the bound remote in the grid, so one craft turns one bound remote into two. */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        Grid grid = Grid.of(input);
        if (kind == Kind.COPY && grid.matches(kind)) {
            remaining.set(grid.boundSlot(), input.getItem(grid.boundSlot()).copyWithCount(1));
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<RemoteRecipe> getSerializer() {
        return kind == Kind.COPY ? COPY_SERIALIZER : UNBIND_SERIALIZER;
    }

    //? if <1.21.2 {
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= (kind == Kind.COPY ? 2 : 1);
    }
    //?}
}
