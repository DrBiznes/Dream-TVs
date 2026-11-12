package com.dreamtvs.screen.solve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Decides what to do with the Dream Displays screens in one plane after TV blocks changed (plan §5.3).
 * Pure: it only sees cells and rectangles, and the result is a list of actions for the caller to apply.
 *
 * <p>Rules:
 * <ul>
 *   <li>Every <b>valid</b> group (filled rectangle) gets exactly one screen. Of the screens that overlap it, the
 *       largest is kept (ties: oldest, then lowest id) and moved to fit, so its link survives growing, shrinking
 *       and merging. If none overlaps, a new one is created. Larger groups choose first, which is what makes
 *       the bigger half of a split keep the original screen.</li>
 *   <li>Every other screen in the area (merge losers, screens of now-invalid groups) shrinks to the largest
 *       rectangle of TVs left inside its old bounds that no other screen claimed, stays put if its bounds are
 *       still whole, or is deleted when nothing is left. So half-built rows or a broken corner don't throw the
 *       link away.</li>
 * </ul>
 */
public final class ScreenPlanner {
    private ScreenPlanner() {}

    /** A group found by the scanner, already solved. */
    public record Group(Set<Cell> cells, ScreenSolver.Solution solution) {}

    /** A managed Dream Displays screen in this plane, as we last tagged it. */
    public record Existing(UUID id, Rect bounds, long createdAt) {}

    public sealed interface Action permits Keep, Move, Create, Delete {}

    /** Screen {@code id} already fits {@code rect}. Nothing to send to Dream Displays. */
    public record Keep(UUID id, Rect rect) implements Action {}

    public record Move(UUID id, Rect from, Rect to) implements Action {}

    /**
     * Make a new screen over {@code rect}. {@code parent} is the screen this group was split off from (its owner
     * should own the new one too), or {@code null} for a brand new screen.
     */
    public record Create(Rect rect, UUID parent) implements Action {}

    public record Delete(UUID id) implements Action {}

    private static final Comparator<Existing> KEEP_ORDER = Comparator
            .comparingInt((Existing e) -> e.bounds().area()).reversed()
            .thenComparingLong(Existing::createdAt)
            .thenComparing(Existing::id);

    private static final Comparator<Group> GROUP_ORDER = Comparator
            .comparingInt((Group g) -> g.cells().size()).reversed()
            .thenComparing(g -> ScreenSolver.anchor(g.cells()), ScreenSolver.CELL_ORDER);

    /**
     * @param groups   groups touched by the change (each solved); empty groups are ignored
     * @param existing managed screens in this plane that overlap the changed area or those groups
     * @param filled   whether a cell currently holds a TV of this plane's facing (for shrinking leftovers)
     */
    public static List<Action> plan(List<Group> groups, List<Existing> existing, Predicate<Cell> filled) {
        List<Action> actions = new ArrayList<>();
        Set<UUID> claimed = new HashSet<>();
        List<Rect> taken = new ArrayList<>();

        List<Group> valid = new ArrayList<>();
        for (Group g : groups) if (!g.cells().isEmpty() && g.solution().valid()) valid.add(g);
        valid.sort(GROUP_ORDER);

        for (Group group : valid) {
            Rect rect = group.solution().rect();
            Existing keeper = existing.stream()
                    .filter(e -> !claimed.contains(e.id()) && e.bounds().intersects(rect))
                    .min(KEEP_ORDER).orElse(null);
            if (keeper != null) {
                claimed.add(keeper.id());
                actions.add(keeper.bounds().equals(rect) ? new Keep(keeper.id(), rect) : new Move(keeper.id(), keeper.bounds(), rect));
            } else {
                Existing parent = existing.stream().filter(e -> e.bounds().intersects(rect)).min(KEEP_ORDER).orElse(null);
                actions.add(new Create(rect, parent == null ? null : parent.id()));
            }
            taken.add(rect);
        }

        List<Existing> rest = new ArrayList<>();
        for (Existing e : existing) if (!claimed.contains(e.id())) rest.add(e);
        rest.sort(KEEP_ORDER);
        for (Existing e : rest) {
            Rect fit = ScreenSolver.largestFilled(e.bounds(), c -> filled.test(c) && !inAny(taken, c));
            if (fit == null) {
                actions.add(new Delete(e.id()));
                continue;
            }
            actions.add(fit.equals(e.bounds()) ? new Keep(e.id(), fit) : new Move(e.id(), e.bounds(), fit));
            taken.add(fit);
        }
        return actions;
    }

    private static boolean inAny(List<Rect> rects, Cell c) {
        for (Rect r : rects) if (r.contains(c)) return true;
        return false;
    }
}
