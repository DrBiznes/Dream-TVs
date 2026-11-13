package com.dreamtvs.screen;

import com.dreamtvs.block.LcdPanelBlock;
import com.dreamtvs.config.DreamTvsConfig;
import com.dreamtvs.screen.solve.Cell;
import com.dreamtvs.screen.solve.ScreenPlanner.Group;
import com.dreamtvs.screen.solve.ScreenSolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads TV groups out of the world (plan §5.2): coplanar, same-facing TVs, 4-connected, with a capped flood
 * fill so a huge wall stays cheap. Never loads chunks; touching an unloaded one marks the view incomplete, and
 * callers then leave that wall alone rather than act on half the picture.
 */
public final class ScreenScanner {
    private ScreenScanner() {}

    /** The world as the solver sees it for one plane. {@code ignored} is treated as empty (a TV about to break). */
    public static final class View {
        private final ServerLevel level;
        private final ScreenPlane plane;
        private final @Nullable BlockPos ignored;
        private boolean hitUnloaded;

        public View(ServerLevel level, ScreenPlane plane, @Nullable BlockPos ignored) {
            this.level = level;
            this.plane = plane;
            this.ignored = ignored;
        }

        public ScreenPlane plane() {
            return plane;
        }

        public boolean filled(Cell cell) {
            BlockPos pos = plane.pos(cell);
            if (pos.equals(ignored)) return false;
            if (!level.isLoaded(pos)) {
                hitUnloaded = true;
                return false;
            }
            return isTv(level.getBlockState(pos), plane);
        }

        /** True if any lookup so far hit an unloaded chunk. */
        public boolean hitUnloaded() {
            return hitUnloaded;
        }
    }

    public static boolean isTv(BlockState state, ScreenPlane plane) {
        return state.getBlock() instanceof LcdPanelBlock && state.getValue(LcdPanelBlock.FACING) == plane.facing();
    }

    /** One group: the capped flood fill from {@code seed}, solved against the configured size limits. */
    public static Group group(View view, Cell seed) {
        DreamTvsConfig config = DreamTvsConfig.get();
        ScreenSolver.Fill fill = ScreenSolver.floodFill(seed, view::filled, config.scanCap());
        return new Group(fill.cells(), ScreenSolver.solve(fill.cells(), config.maxWidth, config.maxHeight));
    }

    /**
     * Every group touching the changed cells: the cells themselves (placed TVs) and their neighbours (what's
     * left around a broken TV, possibly split into several groups).
     */
    public static List<Group> groupsAround(View view, Collection<Cell> changed) {
        List<Group> groups = new ArrayList<>();
        Set<Cell> seen = new HashSet<>();
        for (Cell c : changed) {
            List<Cell> seeds = new ArrayList<>(5);
            seeds.add(c);
            seeds.addAll(ScreenSolver.neighbours(c));
            for (Cell seed : seeds) {
                if (seen.contains(seed) || !view.filled(seed)) continue;
                Group group = group(view, seed);
                seen.addAll(group.cells());
                groups.add(group);
            }
        }
        return groups;
    }
}
