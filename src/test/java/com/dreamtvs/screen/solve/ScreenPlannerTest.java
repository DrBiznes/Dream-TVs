package com.dreamtvs.screen.solve;

import com.dreamtvs.screen.solve.ScreenPlanner.Action;
import com.dreamtvs.screen.solve.ScreenPlanner.Create;
import com.dreamtvs.screen.solve.ScreenPlanner.Delete;
import com.dreamtvs.screen.solve.ScreenPlanner.Existing;
import com.dreamtvs.screen.solve.ScreenPlanner.Group;
import com.dreamtvs.screen.solve.ScreenPlanner.Keep;
import com.dreamtvs.screen.solve.ScreenPlanner.Move;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.dreamtvs.screen.solve.ScreenSolverTest.grid;
import static org.junit.jupiter.api.Assertions.*;

/** Reconcile decisions (plan §8: "reconcile decisions using a fake bridge"), on pictures of a wall. */
class ScreenPlannerTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    /** Plans a wall the way the reconciler does: every 4-connected group is scanned and solved. */
    static List<Action> plan(Set<Cell> wall, Existing... existing) {
        List<Group> groups = new ArrayList<>();
        for (Set<Cell> part : ScreenSolver.components(wall)) {
            groups.add(new Group(part, ScreenSolver.solve(part, 32, 24)));
        }
        return ScreenPlanner.plan(groups, List.of(existing), wall::contains);
    }

    static Existing screen(UUID id, Rect bounds, long createdAt) {
        return new Existing(id, bounds, createdAt);
    }

    @Test
    void firstTvCreatesAScreen() {
        assertEquals(List.of(new Create(new Rect(0, 0, 1, 1), null)), plan(grid("#")));
    }

    @Test
    void unchangedScreenIsKept() {
        Rect r = new Rect(0, 0, 2, 2);
        assertEquals(List.of(new Keep(A, r)), plan(grid("##", "##"), screen(A, r, 0)));
    }

    @Test
    void completedRowGrowsTheScreen() {
        Rect old = new Rect(0, 0, 3, 2);
        assertEquals(List.of(new Move(A, old, new Rect(0, 0, 3, 3))),
                plan(grid("###", "###", "###"), screen(A, old, 0)));
    }

    @Test
    void halfBuiltRowKeepsTheScreenWhereItIs() {
        Rect old = new Rect(0, 0, 3, 2);
        assertEquals(List.of(new Keep(A, old)),
                plan(grid("#  ", "###", "###"), screen(A, old, 0)));
    }

    @Test
    void breakingARowShrinksTheScreen() {
        Rect old = new Rect(0, 0, 3, 3);
        assertEquals(List.of(new Move(A, old, new Rect(0, 0, 3, 2))),
                plan(grid("###", "###"), screen(A, old, 0)));
    }

    @Test
    void breakingACornerShrinksToTheLargestWholePart() {
        Rect old = new Rect(0, 0, 4, 3);
        // 3x3 (9) is the biggest whole rectangle left inside the old 4x3.
        assertEquals(List.of(new Move(A, old, new Rect(0, 0, 3, 3))),
                plan(grid("### ", "####", "####"), screen(A, old, 0)));
    }

    @Test
    void breakingTheLastTvDeletesTheScreen() {
        assertEquals(List.of(new Delete(A)), plan(Set.of(), screen(A, new Rect(0, 0, 1, 1), 0)));
    }

    @Test
    void splitGivesTheBiggerPartTheOriginalScreen() {
        Rect old = new Rect(0, 0, 5, 1);
        // Breaking the 4th TV of a 5x1 leaves a 3x1 and a 1x1.
        List<Action> actions = plan(grid("### #"), screen(A, old, 0));
        assertEquals(List.of(
                new Move(A, old, new Rect(0, 0, 3, 1)),
                new Create(new Rect(4, 0, 1, 1), A)), actions);
    }

    @Test
    void evenSplitIsStable() {
        Rect old = new Rect(0, 0, 3, 1);
        // Equal halves: the lower-left one keeps the screen.
        assertEquals(List.of(
                new Move(A, old, new Rect(0, 0, 1, 1)),
                new Create(new Rect(2, 0, 1, 1), A)), plan(grid("# #"), screen(A, old, 0)));
    }

    @Test
    void mergeKeepsTheLargerScreen() {
        Rect small = new Rect(0, 0, 1, 2);
        Rect big = new Rect(1, 0, 2, 2);
        assertEquals(List.of(new Move(B, big, new Rect(0, 0, 3, 2)), new Delete(A)),
                plan(grid("###", "###"), screen(A, small, 0), screen(B, big, 5)));
    }

    @Test
    void mergeTieGoesToTheOlderScreen() {
        Rect left = new Rect(0, 0, 1, 1);
        Rect right = new Rect(1, 0, 1, 1);
        assertEquals(List.of(new Move(B, right, new Rect(0, 0, 2, 1)), new Delete(A)),
                plan(grid("##"), screen(A, left, 9), screen(B, right, 3)));
    }

    @Test
    void mergeThroughABridgeTv() {
        // Two 1x2 screens with an empty column between them; filling the column makes one 3x2.
        Rect left = new Rect(0, 0, 1, 2);
        Rect right = new Rect(2, 0, 1, 2);
        List<Action> actions = plan(grid("###", "###"), screen(A, left, 1), screen(B, right, 2));
        assertEquals(List.of(new Move(A, left, new Rect(0, 0, 3, 2)), new Delete(B)), actions);
    }

    @Test
    void touchingButNotRectangularKeepsBothScreens() {
        // A 2x2 next to a 1x1: the group is an L-ish shape, so nothing merges and both screens stay.
        Rect big = new Rect(0, 0, 2, 2);
        Rect small = new Rect(2, 0, 1, 1);
        List<Action> actions = plan(grid("## ", "###"), screen(A, big, 0), screen(B, small, 0));
        assertEquals(List.of(new Keep(A, big), new Keep(B, small)), actions);
    }

    @Test
    void separateGroupsAreIndependent() {
        Rect left = new Rect(0, 0, 1, 1);
        List<Action> actions = plan(grid("# ##"), screen(A, left, 0));
        assertEquals(List.of(new Create(new Rect(2, 0, 2, 1), null), new Keep(A, left)), actions);
    }

    @Test
    void oversizedWallKeepsWhatFits() {
        Set<Cell> wall = new java.util.HashSet<>(new Rect(0, 0, 33, 1).cells());
        Rect old = new Rect(0, 0, 32, 1);
        List<Group> groups = List.of(new Group(wall, ScreenSolver.solve(wall, 32, 24)));
        assertEquals(List.of(new Keep(A, old)),
                ScreenPlanner.plan(groups, List.of(screen(A, old, 0)), wall::contains));
    }

    @Test
    void screensNeverOverlapAfterPlanning() {
        // Messy case: three screens, one row broken, a column added. Whatever happens, targets must be disjoint.
        Set<Cell> wall = grid(
                "#####",
                "## ##",
                "#####");
        List<Action> actions = plan(wall,
                screen(A, new Rect(0, 0, 2, 3), 0),
                screen(B, new Rect(2, 0, 1, 3), 1),
                screen(C, new Rect(3, 0, 2, 3), 2));
        List<Rect> targets = new ArrayList<>();
        for (Action a : actions) {
            switch (a) {
                case Keep k -> targets.add(k.rect());
                case Move m -> targets.add(m.to());
                case Create c -> targets.add(c.rect());
                case Delete d -> { }
            }
        }
        for (int i = 0; i < targets.size(); i++) {
            for (Cell c : targets.get(i).cells()) assertTrue(wall.contains(c), "target covers an empty cell " + c);
            for (int j = i + 1; j < targets.size(); j++) {
                assertFalse(targets.get(i).intersects(targets.get(j)), targets.get(i) + " overlaps " + targets.get(j));
            }
        }
        // B lost its middle cell and is a 1-wide column, so it shrinks to one of its whole halves.
        assertTrue(actions.contains(new Keep(A, new Rect(0, 0, 2, 3))));
        assertTrue(actions.contains(new Keep(C, new Rect(3, 0, 2, 3))));
        assertTrue(actions.contains(new Move(B, new Rect(2, 0, 1, 3), new Rect(2, 0, 1, 1))));
    }
}
