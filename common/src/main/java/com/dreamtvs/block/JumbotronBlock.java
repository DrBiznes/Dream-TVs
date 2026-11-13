package com.dreamtvs.block;

import com.dreamtvs.screen.ScreenKind;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The Jumbotron: a full block whose whole front face is screen, with no bezel. Same-facing Jumbotrons on one
 * wall that fill a rectangle form one seamless screen, like LCD panels do.
 */
public class JumbotronBlock extends ScreenBlock {
    public static final MapCodec<JumbotronBlock> CODEC = simpleCodec(JumbotronBlock::new);

    public JumbotronBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false));
    }

    public static Properties defaultProperties() {
        return Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(3.5F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                .pushReaction(PushReaction.BLOCK);
    }

    @Override
    public ScreenKind kind() {
        return ScreenKind.JUMBOTRON;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED);
    }
}
