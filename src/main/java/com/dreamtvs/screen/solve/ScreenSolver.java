package com.dreamtvs.screen.solve;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure multiblock rules. No Minecraft imports: everything works on {@link Cell}s in one plane.
 *
 * <p>The v1 rule is simple: a connected group of TVs forms a screen only if it's a completely filled
 * axis-aligned rectangle within the size limits.
 */
public final class ScreenSolver {
    private ScreenSolver() {}

    public enum Status { VALID, EMPTY, NOT_RECTANGLE, TOO_WIDE, TOO_TALL }

    /** The verdict for one group. {@link #rect()} is set only when {@link #status()} is {@link Status#VALID}. */
    public record Solution(Status status, Rect rect, int width, int height) {
        public boolean valid() {
            return status == Status.VALID;
        }

        static Solution valid(Rect rect) {
            return new Solution(Status.VALID, rect, rect.width(), rect.height());
        }

        static Solution invalid(Status status, int width, int height) {
            return new Solution(status, null, width, height);
        }
    }

    /**
     * Solves one group. Size problems are reported before shape problems, so a huge wall that was cut short by
     * the scanner's cap (more cells than any allowed screen) always comes back as {@code TOO_WIDE}/{@code TOO_TALL}.
     */
    public static Solution solve(Collection<Cell> cells, int maxWidth, int maxHeight) {
        if (cells.isEmpty()) return Solution.invalid(Status.EMPTY, 0, 0);
        int minU = Integer.MAX_VALUE, minV = Integer.MAX_VALUE, maxU = Integer.MIN_VALUE, maxV = Integer.MIN_VALUE;
        for (Cell c : cells) {
            minU = Math.min(minU, c.u());
            minV = Math.min(minV, c.v());
            maxU = Math.max(maxU, c.u());
            maxV = Math.max(maxV, c.v());
        }
        int width = maxU - minU + 1;
        int height = maxV - minV + 1;
        if (width > maxWidth) return Solution.invalid(Status.TOO_WIDE, width, height);
        if (height > maxHeight) return Solution.invalid(Status.TOO_TALL, width, height);
        // Callers pass sets (no duplicates), so "as many cells as the bounding box" means "filled".
        int distinct = cells instanceof Set<?> ? cells.size() : new HashSet<>(cells).size();
        if (distinct != width * height) return Solution.invalid(Status.NOT_RECTANGLE, width, height);
        return Solution.valid(new Rect(minU, minV, width, height));
    }

    /** Splits a set of cells into its 4-connected groups, largest first (ties: lowest v, then lowest u). */
    public static List<Set<Cell>> components(Set<Cell> cells) {
        Set<Cell> left = new HashSet<>(cells);
        List<Set<Cell>> out = new ArrayList<>();
        while (!left.isEmpty()) {
            Cell seed = left.iterator().next();
            Set<Cell> group = floodFill(seed, left::contains, Integer.MAX_VALUE).cells();
            left.removeAll(group);
            out.add(group);
        }
        out.sort(Comparator.<Set<Cell>>comparingInt(Set::size).reversed().thenComparing(ScreenSolver::anchor, CELL_ORDER));
        return out;
    }

    /** Result of a capped flood fill. {@code truncated} means the cap was hit and more cells may exist. */
    public record Fill(Set<Cell> cells, boolean truncated) {}

    /**
     * 4-neighbourhood flood fill from {@code seed} over cells where {@code filled} is true, stopping after
     * {@code cap} cells. An unfilled seed gives an empty result.
     */
    public static Fill floodFill(Cell seed, Predicate<Cell> filled, int cap) {
        Set<Cell> seen = new HashSet<>();
        if (!filled.test(seed)) return new Fill(seen, false);
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        seen.add(seed);
        queue.add(seed);
        while (!queue.isEmpty()) {
            Cell c = queue.poll();
            for (Cell n : neighbours(c)) {
                if (seen.contains(n) || !filled.test(n)) continue;
                if (seen.size() >= cap) return new Fill(seen, true);
                seen.add(n);
                queue.add(n);
            }
        }
        return new Fill(seen, false);
    }

    public static List<Cell> neighbours(Cell c) {
        return List.of(c.offset(1, 0), c.offset(-1, 0), c.offset(0, 1), c.offset(0, -1));
    }

    /**
     * The largest rectangle inside {@code bounds} made only of cells where {@code filled} is true, or
     * {@code null} if there is none. Ties go to the lowest, then left-most rectangle, so results are stable.
     * Classic "largest rectangle in a histogram", row by row: O(width × height).
     */
    public static Rect largestFilled(Rect bounds, Predicate<Cell> filled) {
        int w = bounds.width();
        int[] heights = new int[w];
        Rect best = null;
        for (int row = 0; row < bounds.height(); row++) {
            int v = bounds.minV() + row;
            for (int i = 0; i < w; i++) {
                heights[i] = filled.test(new Cell(bounds.minU() + i, v)) ? heights[i] + 1 : 0;
            }
            // Monotonic stack over column indices; a sentinel 0 at i == w flushes the stack.
            int[] stack = new int[w + 1];
            int top = 0;
            for (int i = 0; i <= w; i++) {
                int h = i == w ? 0 : heights[i];
                while (top > 0 && heights[stack[top - 1]] >= h) {
                    int height = heights[stack[--top]];
                    if (height == 0) continue;
                    int start = top == 0 ? 0 : stack[top - 1] + 1;
                    int width = i - start;
                    Rect candidate = new Rect(bounds.minU() + start, v - height + 1, width, height);
                    if (better(candidate, best)) best = candidate;
                }
                stack[top++] = i;
            }
        }
        return best;
    }

    private static boolean better(Rect a, Rect b) {
        if (b == null) return true;
        if (a.area() != b.area()) return a.area() > b.area();
        if (a.minV() != b.minV()) return a.minV() < b.minV();
        return a.minU() < b.minU();
    }

    static final Comparator<Cell> CELL_ORDER = Comparator.comparingInt(Cell::v).thenComparingInt(Cell::u);

    static Cell anchor(Set<Cell> cells) {
        return cells.stream().min(CELL_ORDER).orElseThrow();
    }
}
