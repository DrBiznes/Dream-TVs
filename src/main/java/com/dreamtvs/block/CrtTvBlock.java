package com.dreamtvs.block;

import com.dreamtvs.McCompat;
import com.dreamtvs.screen.ScreenKind;
//? if <26.1
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * The CRT TV: a grey plastic set in the style of a late-80s flat-tube television, with a big 4:3 tube and a
 * thin control bar under it. One block is one TV; CRTs
 * next to each other stay separate screens.
 */
public class CrtTvBlock extends ScreenBlock {
    // Blocks lost their codecs in 26.x.
    //? if <26.1
    public static final MapCodec<CrtTvBlock> CODEC = simpleCodec(CrtTvBlock::new);

    /** The cabinet: 1 pixel short of the front and top of the block. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(0, 0, 1, 16, 15, 16),
            Direction.SOUTH, Block.box(0, 0, 0, 16, 15, 15),
            Direction.WEST, Block.box(1, 0, 0, 16, 15, 16),
            Direction.EAST, Block.box(0, 0, 0, 15, 15, 16)));

    public CrtTvBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false));
    }

    public static Properties defaultProperties() {
        return Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(2.0F, 3.0F)
                .sound(SoundType.METAL)
                .noOcclusion()
                .pushReaction(McCompat.IMMOVABLE);
    }

    @Override
    public ScreenKind kind() {
        return ScreenKind.CRT;
    }

    //? if <26.1 {
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }
    //?}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }
}
