package com.dreamtvs.screen;

import com.dreamtvs.screen.solve.Cell;
import com.dreamtvs.screen.solve.Rect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * One wall plane for one facing: every TV whose front points {@link #facing} and whose coordinate along that
 * axis is {@link #depth}. Maps block positions to the solver's 2D {@link Cell}s and back.
 * For north/south walls {@code u = x}; for east/west walls {@code u = z}. {@code v} is always {@code y}.
 */
public record ScreenPlane(Direction facing, int depth) {
    public ScreenPlane {
        if (facing.getAxis() == Direction.Axis.Y) throw new IllegalArgumentException("floor/ceiling screens aren't supported");
    }

    public static ScreenPlane of(BlockPos pos, Direction facing) {
        return new ScreenPlane(facing, facing.getAxis() == Direction.Axis.Z ? pos.getZ() : pos.getX());
    }

    private boolean alongX() {
        return facing.getAxis() == Direction.Axis.Z;
    }

    public boolean contains(BlockPos pos) {
        return (alongX() ? pos.getZ() : pos.getX()) == depth;
    }

    public Cell cell(BlockPos pos) {
        return new Cell(alongX() ? pos.getX() : pos.getZ(), pos.getY());
    }

    public BlockPos pos(Cell cell) {
        return alongX() ? new BlockPos(cell.u(), cell.v(), depth) : new BlockPos(depth, cell.v(), cell.u());
    }

    public BlockPos min(Rect rect) {
        return pos(rect.min());
    }

    public BlockPos max(Rect rect) {
        return pos(rect.max());
    }

    public Rect rect(BlockPos a, BlockPos b) {
        return Rect.of(cell(a), cell(b));
    }
}
