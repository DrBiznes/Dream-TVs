package com.dreamtvs.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A Flat Screen: a thin wall-mounted TV of a fixed size, {@code kind().width()} by {@code kind().height()}
 * blocks. Like a bed, it's placed in one go and breaks as a whole. Each block knows its {@link #part()}:
 * {@code row * width + column}, with column 0 on the viewer's left and row 0 at the bottom. The TV is one screen
 * of exactly its size, and Flat Screens next to each other stay separate.
 *
 * <p>The clicked block becomes the bottom of the middle column (the left one for an even width); the rest is
 * placed to the sides and above, and placing fails if there isn't room. Only part 0 (bottom left) drops the
 * item, so a TV broken anywhere (by hand, by an explosion, by a command) gives back one item.
 */
public abstract class FlatScreenBlock extends ScreenBlock {
    /** The panel: the back 3 pixels of the block, so it hangs flat on the wall behind it. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(0, 0, 13, 16, 16, 16),
            Direction.SOUTH, Block.box(0, 0, 0, 16, 16, 3),
            Direction.WEST, Block.box(13, 0, 0, 16, 16, 16),
            Direction.EAST, Block.box(0, 0, 0, 3, 16, 16)));

    protected FlatScreenBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false)
                .setValue(part(), 0));
    }

    public static Properties defaultProperties() {
        return Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(2.0F, 3.0F)
                .sound(SoundType.METAL)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK);
    }

    /** Which block of the TV this is (see the class doc). Must be a static constant of the subclass. */
    public abstract IntegerProperty part();

    public int width() {
        return kind().width();
    }

    public int height() {
        return kind().height();
    }

    private int column(int part) {
        return part % width();
    }

    private int row(int part) {
        return part / width();
    }

    private int part(int column, int row) {
        return row * width() + column;
    }

    /** The part the clicked block becomes: the bottom of the middle column (the left of the two for even widths). */
    private int placedPart() {
        return part((width() - 1) / 2, 0);
    }

    /** The viewer's right, for a TV facing {@code facing}. */
    private static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** Where part {@code part} of the TV that {@code state} at {@code pos} belongs to is. */
    public BlockPos partPos(BlockPos pos, BlockState state, int part) {
        int self = state.getValue(part());
        return pos.relative(right(state.getValue(FACING)), column(part) - column(self))
                .relative(Direction.UP, row(part) - row(self));
    }

    private boolean isPart(@Nullable BlockState state, Direction facing, int part) {
        return state != null && state.getBlock() == this && state.getValue(FACING) == facing
                && state.getValue(part()) == part;
    }

    @Override
    public List<BlockPos> unit(BlockState state, BlockPos pos) {
        List<BlockPos> parts = new ArrayList<>(width() * height());
        for (int i = 0; i < width() * height(); i++) parts.add(partPos(pos, state, i));
        return parts;
    }

    @Override
    public boolean isWhole(BlockState state, BlockPos pos, Function<BlockPos, @Nullable BlockState> lookup) {
        Direction facing = state.getValue(FACING);
        int self = state.getValue(part());
        for (int i = 0; i < width() * height(); i++) {
            if (i != self && !isPart(lookup.apply(partPos(pos, state, i)), facing, i)) return false;
        }
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED, part());
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        state = state.setValue(part(), placedPart());
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        CollisionContext collision = context.getPlayer() == null ? CollisionContext.empty() : CollisionContext.of(context.getPlayer());
        for (int i = 0; i < width() * height(); i++) {
            if (i == placedPart()) continue;
            BlockPos other = partPos(pos, state, i);
            if (level.isOutsideBuildHeight(other) || !level.getWorldBorder().isWithinBounds(other)
                    || !level.getBlockState(other).canBeReplaced(context)
                    || !level.isUnobstructed(state.setValue(part(), i), other, collision)) {
                if (context.getPlayer() instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.translatable("message.dreamtvs.no_room", width(), height()), true);
                }
                return null;
            }
        }
        return state;
    }

    /** Places the rest of the TV around the clicked block. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide) {
            int self = state.getValue(part());
            for (int i = 0; i < width() * height(); i++) {
                if (i != self) level.setBlock(partPos(pos, state, i), state.setValue(part(), i), Block.UPDATE_ALL);
            }
        }
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    /** The part expected next to {@code part} towards {@code direction}, or -1 if that's outside the TV. */
    private int neighbour(Direction facing, int part, Direction direction) {
        int column = column(part);
        int row = row(part);
        if (direction == right(facing)) column++;
        else if (direction == right(facing).getOpposite()) column--;
        else if (direction == Direction.UP) row++;
        else if (direction == Direction.DOWN) row--;
        else return -1;
        return column < 0 || column >= width() || row < 0 || row >= height() ? -1 : part(column, row);
    }

    /** A TV missing a neighbouring part falls apart, one block after the other. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        int expected = neighbour(facing, state.getValue(part()), direction);
        if (expected >= 0 && !isPart(neighborState, facing, expected)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /**
     * In creative, breaking any block of the TV must not drop it, but part 0 (the one that drops) would still
     * fall apart with a drop. Take it away quietly first, as doors and beds do.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(part()) != 0) {
            BlockPos first = partPos(pos, state, 0);
            BlockState firstState = level.getBlockState(first);
            if (isPart(firstState, state.getValue(FACING), 0)) {
                level.setBlock(first, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, first, Block.getId(firstState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** A mirrored TV keeps its picture side, but its left column is now on the right. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        int part = state.getValue(part());
        return super.mirror(state, mirror).setValue(part(), part(width() - 1 - column(part), row(part)));
    }
}
