package com.dreamtvs.block;

import com.dreamtvs.screen.ScreenKind;
import com.dreamtvs.screen.Screens;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * What every TV block shares. Its front ({@link #FACING}) points at the player who placed it, and that's the
 * side the picture shows on. Block changes are reported to {@link Screens}, which keeps a Dream Displays screen
 * on the block (or on the wall it's part of, for kinds that merge); {@link #FORMED} says whether one currently
 * covers this block. Right-clicking opens the Dream Displays menu.
 *
 * <p>Subclasses add {@link #FACING} and {@link #FORMED} to their state definition and default state.
 */
public abstract class ScreenBlock extends HorizontalDirectionalBlock {
    /** Set by the reconciler: true while a Dream Displays screen covers this block. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    protected ScreenBlock(Properties properties) {
        super(properties);
    }

    public abstract ScreenKind kind();

    /**
     * Every block of the TV that the block at {@code pos} belongs to, for kinds that don't merge. Just {@code pos}
     * unless the TV spans several blocks (see {@link FlatScreenBlock}).
     */
    public List<BlockPos> unit(BlockState state, BlockPos pos) {
        return List.of(pos);
    }

    /**
     * Whether all blocks of this TV are in place, so it may show a picture. {@code lookup} gives the block at a
     * position, or {@code null} if it can't be read (an unloaded chunk, or a block that is about to break).
     */
    public boolean isWhole(BlockState state, BlockPos pos, Function<BlockPos, @Nullable BlockState> lookup) {
        return true;
    }

    /** {@code state} adjusted to its surroundings (e.g. connected bezels). Called for every new block. */
    protected BlockState settle(BlockState state, BlockGetter level, BlockPos pos) {
        return state;
    }

    /** Server side, placing is refused when the block would join a screen the player may not change (plan §5.7). */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        if (kind().merges() && context.getLevel() instanceof ServerLevel level
                && context.getPlayer() instanceof ServerPlayer player
                && !Screens.canPlace(level, context.getClickedPos(), state.getValue(FACING), kind(), player)) {
            // The client already predicted the placement; resend the inventory so the item shows up again.
            player.inventoryMenu.sendAllDataToRemote();
            return null;
        }
        return settle(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && placer instanceof ServerPlayer player) {
            Screens.onPlaced(serverLevel, pos, state.getValue(FACING), kind(), player);
        }
    }

    /** Any new or turned TV marks its wall for the end-of-tick reconcile. Other state changes don't. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel && !sameTv(oldState, state)) {
            // level.setBlock (commands, other mods) doesn't go through getStateForPlacement.
            BlockState settled = settle(state, level, pos);
            if (settled != state) level.setBlock(pos, settled, Block.UPDATE_CLIENTS);
            Screens.onAdded(serverLevel, pos, state.getValue(FACING), kind());
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !sameTv(state, newState)) {
            Screens.onRemoved(serverLevel, pos, state.getValue(FACING), kind());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** The same block facing the same way: only its other properties differ. */
    protected static boolean sameTv(BlockState a, BlockState b) {
        return a.getBlock() instanceof ScreenBlock && a.getBlock() == b.getBlock() && a.getValue(FACING) == b.getValue(FACING);
    }

    /** Holding a block: skip our click so it gets placed (e.g. stacking TVs). Anything else: fall through to the menu. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof BlockItem) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Right-click on any side opens the Dream Displays menu for this TV's screen (client side). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return TvClientHooks.openMenu(pos) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
