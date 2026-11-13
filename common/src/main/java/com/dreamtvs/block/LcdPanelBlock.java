package com.dreamtvs.block;

import com.dreamtvs.screen.Screens;
import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The LCD panel: a thin slab (the size of Create's display board) whose front ({@link #FACING}) points at the
 * player who placed it. Same-facing panels on one wall that fill a rectangle form one screen (see
 * {@code ScreenReconciler}); {@link #FORMED} says whether a Dream Displays screen currently covers this block.
 *
 * <p>{@link #UP}/{@link #DOWN}/{@link #LEFT}/{@link #RIGHT} say whether a same-facing panel sits on that side
 * (left and right as seen by someone looking at the screen). They only pick the model: the bezel is drawn on
 * the sides with no neighbour, so a wall of panels gets one frame around it.
 */
public class LcdPanelBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<LcdPanelBlock> CODEC = simpleCodec(LcdPanelBlock::new);
    /** Set by the reconciler: true while a Dream Displays screen covers this panel. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    /**
     * How far the screen surface sits behind the block's front face, in blocks. The bezel starts 3 pixels in
     * and the glass is 1 pixel behind it. The client moves the Dream Displays picture back by this much.
     */
    public static final float SCREEN_INSET = 4 / 16F;

    private static final VoxelShape SHAPE_Z = Block.box(0, 0, 3, 16, 16, 13);
    private static final VoxelShape SHAPE_X = Block.box(3, 0, 0, 13, 16, 16);

    public LcdPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false)
                .setValue(UP, false).setValue(DOWN, false).setValue(LEFT, false).setValue(RIGHT, false));
    }

    public static Properties defaultProperties() {
        return Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(3.5F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED, UP, DOWN, LEFT, RIGHT);
    }

    /** Server side, placing is refused when the panel would join a screen the player may not change (plan §5.7). */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        if (context.getLevel() instanceof ServerLevel level && context.getPlayer() instanceof ServerPlayer player
                && !Screens.canPlace(level, context.getClickedPos(), state.getValue(FACING), player)) {
            // The client already predicted the placement; resend the inventory so the panel shows up again.
            player.inventoryMenu.sendAllDataToRemote();
            return null;
        }
        return connect(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_Z : SHAPE_X;
    }

    /** The side of the wall {@code property} looks at, for a panel facing {@code facing}. */
    private static Direction side(Direction facing, BooleanProperty property) {
        if (property == UP) return Direction.UP;
        if (property == DOWN) return Direction.DOWN;
        return property == LEFT ? facing.getClockWise() : facing.getCounterClockWise();
    }

    private static final BooleanProperty[] SIDES = {UP, DOWN, LEFT, RIGHT};

    /** {@code state} with all four connection flags read from the world. */
    private static BlockState connect(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        for (BooleanProperty property : SIDES) {
            state = state.setValue(property, samePanel(state, level.getBlockState(pos.relative(side(facing, property)))));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        for (BooleanProperty property : SIDES) {
            if (side(facing, property) == direction) return state.setValue(property, samePanel(state, neighborState));
        }
        return state;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && placer instanceof ServerPlayer player) {
            Screens.onPlaced(serverLevel, pos, state.getValue(FACING), player);
        }
    }

    /** Any new or turned panel marks its wall for the end-of-tick reconcile. FORMED-only changes don't. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel && !samePanel(oldState, state)) {
            // level.setBlock (commands, other mods) doesn't go through getStateForPlacement.
            BlockState connected = connect(state, level, pos);
            if (connected != state) level.setBlock(pos, connected, Block.UPDATE_CLIENTS);
            Screens.onAdded(serverLevel, pos, state.getValue(FACING));
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !samePanel(state, newState)) {
            Screens.onRemoved(serverLevel, pos, state.getValue(FACING));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static boolean samePanel(BlockState a, BlockState b) {
        return a.getBlock() instanceof LcdPanelBlock && b.getBlock() instanceof LcdPanelBlock && a.getValue(FACING) == b.getValue(FACING);
    }

    /** Holding a block: skip our click so it gets placed (e.g. stacking panels). Anything else: fall through to the menu. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof BlockItem) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Right-click on any side opens the Dream Displays menu for this panel's screen (client side). */
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
        if (mirror == Mirror.NONE) return state;
        // A mirrored wall keeps its picture side, but what was on the left is now on the right.
        return state.rotate(mirror.getRotation(state.getValue(FACING)))
                .setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT));
    }
}
