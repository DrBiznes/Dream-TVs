package com.dreamtvs.neoforge.gametest;

import com.dreamtvs.McCompat;
import com.dreamtvs.block.LcdPanelBlock;
import com.dreamtvs.item.RemoteItem;
import com.dreamtvs.neoforge.DreamTvsNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/** The TV remote: binding by right-click, and the copy and unbind crafting recipes. */
public final class RemoteGameTests {
    private static final BlockPos TV = new BlockPos(2, 1, 2);

    private static ItemStack remote() {
        return new ItemStack(DreamTvsNeoForge.REMOTE.get());
    }

    private static ItemStack boundRemote(GameTestHelper h) {
        ItemStack stack = remote();
        RemoteItem.bind(stack, McCompat.dimensionId(h.getLevel()), h.absolutePos(TV));
        return stack;
    }

    private static Optional<RecipeHolder<CraftingRecipe>> recipe(GameTestHelper h, CraftingInput input) {
        return h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel());
    }

    private static ItemStack craft(GameTestHelper h, CraftingRecipe recipe, CraftingInput input) {
        //? if >=26.1 {
        /*return recipe.assemble(input);
        *///?} else
        return recipe.assemble(input, h.getLevel().registryAccess());
    }

    /** Right-clicks {@code pos} with {@code stack} in the main hand, the way the game calls the item. */
    private static void click(GameTestHelper h, ServerPlayer player, ItemStack stack, BlockPos pos) {
        BlockPos abs = h.absolutePos(pos);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false)));
    }

    @TvTest
    public static void remoteBindsToTheTvItClicks(GameTestHelper h) {
        h.setBlock(TV, DreamTvsNeoForge.LCD_PANEL.get().defaultBlockState().setValue(LcdPanelBlock.FACING, Direction.NORTH));
        h.setBlock(TV.east(3), Blocks.STONE);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);

        ItemStack stack = remote();
        h.assertTrue(!RemoteItem.isBound(stack) && !stack.hasFoil(), "a new remote is unbound and doesn't glint");
        click(h, player, stack, TV.east(3));
        h.assertTrue(!RemoteItem.isBound(stack), "clicking a block that isn't a TV doesn't bind");

        click(h, player, stack, TV);
        RemoteItem.Binding binding = RemoteItem.binding(stack);
        h.assertTrue(binding != null && binding.pos().equals(h.absolutePos(TV))
                && binding.dimension().equals(McCompat.dimensionId(h.getLevel())), "bound to the clicked TV, got " + binding);
        h.assertTrue(stack.hasFoil(), "a bound remote glints");

        // A bound remote keeps its TV when it clicks another one, unless the player sneaks.
        h.setBlock(TV.above(2), DreamTvsNeoForge.CRT_TV.get().defaultBlockState());
        click(h, player, stack, TV.above(2));
        h.assertTrue(RemoteItem.binding(stack).pos().equals(h.absolutePos(TV)), "still bound to the first TV");
        player.setShiftKeyDown(true);
        click(h, player, stack, TV.above(2));
        h.assertTrue(RemoteItem.binding(stack).pos().equals(h.absolutePos(TV.above(2))), "sneak-click binds to the other TV");

        RemoteItem.unbind(stack);
        h.assertTrue(!RemoteItem.isBound(stack) && !stack.has(DataComponents.CUSTOM_DATA),
                "an unbound remote is the same as a new one");
        h.succeed();
    }

    @TvTest
    public static void boundAndUnboundRemoteCraftACopy(GameTestHelper h) {
        ItemStack bound = boundRemote(h);
        CraftingInput input = CraftingInput.of(2, 1, List.of(remote(), bound));
        Optional<RecipeHolder<CraftingRecipe>> recipe = recipe(h, input);
        h.assertTrue(recipe.isPresent(), "a bound and an unbound remote should craft");

        ItemStack result = craft(h, recipe.get().value(), input);
        h.assertTrue(result.getCount() == 1 && RemoteItem.binding(bound).equals(RemoteItem.binding(result)),
                "the result is bound to the same TV, got " + RemoteItem.binding(result));
        NonNullList<ItemStack> remaining = recipe.get().value().getRemainingItems(input);
        h.assertTrue(remaining.get(0).isEmpty() && RemoteItem.binding(bound).equals(RemoteItem.binding(remaining.get(1))),
                "the bound remote stays in the grid, the unbound one is used up: " + remaining);

        h.assertTrue(recipe(h, CraftingInput.of(2, 1, List.of(remote(), remote()))).isEmpty(), "two unbound remotes don't craft");
        h.assertTrue(recipe(h, CraftingInput.of(2, 1, List.of(boundRemote(h), boundRemote(h)))).isEmpty(), "two bound remotes don't craft");
        h.assertTrue(recipe(h, CraftingInput.of(3, 1, List.of(remote(), bound, remote()))).isEmpty(), "one copy at a time");
        h.succeed();
    }

    @TvTest
    public static void boundRemoteAloneCraftsAnUnboundOne(GameTestHelper h) {
        CraftingInput input = CraftingInput.of(1, 1, List.of(boundRemote(h)));
        Optional<RecipeHolder<CraftingRecipe>> recipe = recipe(h, input);
        h.assertTrue(recipe.isPresent(), "a bound remote on its own should craft");

        ItemStack result = craft(h, recipe.get().value(), input);
        h.assertTrue(result.getItem() instanceof RemoteItem && !RemoteItem.isBound(result)
                && ItemStack.isSameItemSameComponents(result, remote()), "the result is a plain unbound remote");
        h.assertTrue(recipe.get().value().getRemainingItems(input).get(0).isEmpty(), "the bound remote is used up");
        h.assertTrue(recipe(h, CraftingInput.of(1, 1, List.of(remote()))).isEmpty(), "an unbound remote on its own doesn't craft");
        h.succeed();
    }
}
