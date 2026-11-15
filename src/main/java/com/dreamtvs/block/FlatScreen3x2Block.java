package com.dreamtvs.block;

import com.dreamtvs.McCompat;
import com.dreamtvs.screen.ScreenKind;
//? if <26.1
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** The Flat Screen 3x2: a wall-mounted TV 3 blocks wide and 2 tall (see {@link FlatScreenBlock}). */
public class FlatScreen3x2Block extends FlatScreenBlock {
    // Blocks lost their codecs in 26.x.
    //? if <26.1
    public static final MapCodec<FlatScreen3x2Block> CODEC = simpleCodec(FlatScreen3x2Block::new);
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 5);

    public FlatScreen3x2Block(Properties properties) {
        super(properties);
    }

    @Override
    public ScreenKind kind() {
        return ScreenKind.FLAT_SCREEN_3X2;
    }

    @Override
    public IntegerProperty part() {
        return PART;
    }

    //? if <26.1 {
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }
    //?}
}
