package com.dreamtvs.block;

import com.dreamtvs.screen.ScreenKind;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** The Flat Screen 2x1: a wall-mounted TV 2 blocks wide and one tall (see {@link FlatScreenBlock}). */
public class FlatScreen2x1Block extends FlatScreenBlock {
    public static final MapCodec<FlatScreen2x1Block> CODEC = simpleCodec(FlatScreen2x1Block::new);
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 1);

    public FlatScreen2x1Block(Properties properties) {
        super(properties);
    }

    @Override
    public ScreenKind kind() {
        return ScreenKind.FLAT_SCREEN_2X1;
    }

    @Override
    public IntegerProperty part() {
        return PART;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }
}
