package com.dreamtvs.item;

import com.dreamtvs.McCompat;
import com.dreamtvs.block.ScreenBlock;
import com.dreamtvs.block.TvClientHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//? if <1.21.2
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The TV remote. Right-clicking a TV binds the remote to it (sneak + right-click to bind a remote that already
 * has a TV to another one). A bound remote glints, opens its TV's Dream Displays menu on right-click from
 * anywhere in range, and pauses or resumes the TV on left-click (client side, see {@code RemoteClient}).
 *
 * <p>The binding is the TV block's position and dimension, kept in the stack's custom data. A position rather
 * than the screen's id, so the remote keeps working when a wall of panels grows, shrinks or forms again.
 */
public class RemoteItem extends Item {
    /** Key in the stack's {@code minecraft:custom_data}: {@code {dimension: "minecraft:overworld", x, y, z}}. */
    private static final String TAG = "dreamtvs_tv";

    /** The TV a remote is bound to: any block of it, and the dimension it's in. */
    public record Binding(String dimension, BlockPos pos) {}

    public RemoteItem(Properties properties) {
        super(properties);
    }

    public static @Nullable Binding binding(ItemStack stack) {
        if (!(stack.getItem() instanceof RemoteItem)) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = McCompat.nbtCompound(data.copyTag(), TAG);
        String dimension = McCompat.nbtString(tag, "dimension");
        if (dimension.isEmpty()) return null;
        return new Binding(dimension, new BlockPos(McCompat.nbtInt(tag, "x"), McCompat.nbtInt(tag, "y"), McCompat.nbtInt(tag, "z")));
    }

    public static boolean isBound(ItemStack stack) {
        return binding(stack) != null;
    }

    public static void bind(ItemStack stack, String dimension, BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension);
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(TAG, tag));
    }

    /** Forgets the TV. Leaves no empty custom data behind, so the stack equals a freshly crafted remote. */
    public static void unbind(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;
        CompoundTag root = data.copyTag();
        root.remove(TAG);
        if (root.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        }
    }

    /** A bound remote has the enchantment glint. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return isBound(stack) || super.isFoil(stack);
    }

    /** Right-click on a block: a TV binds the remote; anything else is the same as right-clicking the air. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        boolean rebind = player != null && player.isSecondaryUseActive();
        if (level.getBlockState(pos).getBlock() instanceof ScreenBlock && (rebind || !isBound(stack))) {
            if (McCompat.isClientSide(level)) return InteractionResult.SUCCESS;
            bind(stack, McCompat.dimensionId(level), pos);
            if (player != null) {
                McCompat.overlay(player, Component.translatable("message.dreamtvs.remote.bound", pos.getX(), pos.getY(), pos.getZ()));
            }
            return InteractionResult.CONSUME;
        }
        return player == null ? InteractionResult.PASS : openMenu(level, player, stack);
    }

    /** Right-click in the air: open the bound TV's menu. */
    @Override
    //? if >=1.21.2 {
    /*public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return openMenu(level, player, player.getItemInHand(hand));
    }
    *///?} else {
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return new InteractionResultHolder<>(openMenu(level, player, stack), stack);
    }
    //?}

    private static InteractionResult openMenu(Level level, Player player, ItemStack stack) {
        Binding binding = binding(stack);
        if (!McCompat.isClientSide(level)) return binding == null ? InteractionResult.PASS : InteractionResult.CONSUME;
        if (binding == null) {
            McCompat.overlay(player, Component.translatable("message.dreamtvs.remote.unbound"));
            return InteractionResult.PASS;
        }
        Component problem = TvClientHooks.useRemote(binding);
        if (problem != null) McCompat.overlay(player, problem);
        return InteractionResult.SUCCESS;
    }
}
