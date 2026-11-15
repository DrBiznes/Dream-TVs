package com.dreamtvs.block;

import com.dreamtvs.McCompat;
import com.dreamtvs.screen.ScreenKind;
//? if <26.1
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
//? if >=1.21.2 {
/*import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
*///?} else
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The LCD panel: a thin slab (the size of Create's display board). Same-facing panels on one wall that fill a
 * rectangle form one screen (see {@code ScreenReconciler}).
 *
 * <p>{@link #UP}/{@link #DOWN}/{@link #LEFT}/{@link #RIGHT} say whether a same-facing panel sits on that side
 * (left and right as seen by someone looking at the screen). They only pick the model: the bezel is drawn on
 * the sides with no neighbour, so a wall of panels gets one frame around it.
 */
public class LcdPanelBlock extends ScreenBlock {
    // Blocks lost their codecs in 26.x.
    //? if <26.1
    public static final MapCodec<LcdPanelBlock> CODEC = simpleCodec(LcdPanelBlock::new);
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    private static final BooleanProperty[] SIDES = {UP, DOWN, LEFT, RIGHT};

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
                .pushReaction(McCompat.IMMOVABLE);
    }

    @Override
    public ScreenKind kind() {
        return ScreenKind.LCD_PANEL;
    }

    //? if <26.1 {
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }
    //?}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED, UP, DOWN, LEFT, RIGHT);
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

    /** {@code state} with all four connection flags read from the world. */
    @Override
    protected BlockState settle(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        for (BooleanProperty property : SIDES) {
            state = state.setValue(property, sameTv(state, level.getBlockState(pos.relative(side(facing, property)))));
        }
        return state;
    }

    /** Keeps the connection flag on the side that changed up to date. */
    private BlockState neighbourChanged(BlockState state, Direction direction, BlockState neighborState) {
        Direction facing = state.getValue(FACING);
        for (BooleanProperty property : SIDES) {
            if (side(facing, property) == direction) return state.setValue(property, sameTv(state, neighborState));
        }
        return state;
    }

    //? if >=1.21.2 {
    /*@Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState,
                                     RandomSource random) {
        return neighbourChanged(state, direction, neighborState);
    }
    *///?} else {
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return neighbourChanged(state, direction, neighborState);
    }
    //?}

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        // A mirrored wall keeps its picture side, but what was on the left is now on the right.
        return super.mirror(state, mirror).setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT));
    }
}
