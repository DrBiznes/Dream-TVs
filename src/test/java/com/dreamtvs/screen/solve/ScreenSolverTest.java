package com.dreamtvs.screen.solve;

import com.dreamtvs.screen.solve.ScreenSolver.Solution;
import com.dreamtvs.screen.solve.ScreenSolver.Status;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ScreenSolverTest {
    private static final int MAX_W = 32;
    private static final int MAX_H = 24;

    /** Parses a picture of the wall: {@code #} is a TV, anything else is empty. The top line is the highest v. */
    static Set<Cell> grid(String... rows) {
        Set<Cell> cells = new HashSet<>();
        for (int r = 0; r < rows.length; r++) {
            int v = rows.length - 1 - r;
            for (int u = 0; u < rows[r].length(); u++) if (rows[r].charAt(u) == '#') cells.add(new Cell(u, v));
        }
        return cells;
    }

    static Set<Cell> filled(int w, int h) {
        return new HashSet<>(new Rect(0, 0, w, h).cells());
    }

    @Test
    void singleTvIsOneByOne() {
        Solution s = ScreenSolver.solve(Set.of(new Cell(5, 7)), MAX_W, MAX_H);
        assertTrue(s.valid());
        assertEquals(new Rect(5, 7, 1, 1), s.rect());
    }

    @Test
    void filledRectangles() {
        Solution s = ScreenSolver.solve(grid(
                "####",
                "####",
                "####"), MAX_W, MAX_H);
        assertEquals(Status.VALID, s.status());
        assertEquals(new Rect(0, 0, 4, 3), s.rect());

        assertEquals(new Rect(0, 0, 1, 5), ScreenSolver.solve(filled(1, 5), MAX_W, MAX_H).rect());
        assertEquals(new Rect(0, 0, 7, 1), ScreenSolver.solve(filled(7, 1), MAX_W, MAX_H).rect());
    }

    @Test
    void negativeCoordinates() {
        Set<Cell> cells = new HashSet<>(new Rect(-3, -2, 2, 2).cells());
        assertEquals(new Rect(-3, -2, 2, 2), ScreenSolver.solve(cells, MAX_W, MAX_H).rect());
    }

    @Test
    void lShapeIsNotARectangle() {
        Solution s = ScreenSolver.solve(grid(
                "#  ",
                "#  ",
                "###"), MAX_W, MAX_H);
        assertEquals(Status.NOT_RECTANGLE, s.status());
        assertNull(s.rect());
        assertEquals(3, s.width());
        assertEquals(3, s.height());
    }

    @Test
    void partialNewRowIsNotARectangle() {
        assertEquals(Status.NOT_RECTANGLE, ScreenSolver.solve(grid(
                "#   ",
                "####",
                "####"), MAX_W, MAX_H).status());
    }

    @Test
    void holeIsNotARectangle() {
        assertEquals(Status.NOT_RECTANGLE, ScreenSolver.solve(grid(
                "###",
                "# #",
                "###"), MAX_W, MAX_H).status());
    }

    @Test
    void maximumSizeIsAllowed() {
        assertTrue(ScreenSolver.solve(filled(MAX_W, MAX_H), MAX_W, MAX_H).valid());
    }

    @Test
    void oversized() {
        assertEquals(Status.TOO_WIDE, ScreenSolver.solve(filled(MAX_W + 1, 1), MAX_W, MAX_H).status());
        assertEquals(Status.TOO_TALL, ScreenSolver.solve(filled(1, MAX_H + 1), MAX_W, MAX_H).status());
        // Width is reported first when both are too big.
        assertEquals(Status.TOO_WIDE, ScreenSolver.solve(filled(MAX_W + 1, MAX_H + 1), MAX_W, MAX_H).status());
        // Size beats shape: an oversized L is "too wide", not "not a rectangle".
        Set<Cell> bigL = filled(MAX_W + 2, 1);
        bigL.add(new Cell(0, 1));
        assertEquals(Status.TOO_WIDE, ScreenSolver.solve(bigL, MAX_W, MAX_H).status());
    }

    @Test
    void customLimits() {
        assertEquals(Status.TOO_WIDE, ScreenSolver.solve(filled(3, 1), 2, 2).status());
        assertTrue(ScreenSolver.solve(filled(2, 2), 2, 2).valid());
    }

    @Test
    void emptyGroup() {
        assertEquals(Status.EMPTY, ScreenSolver.solve(Set.of(), MAX_W, MAX_H).status());
    }

    @Test
    void splitIntoComponents() {
        // Breaking the middle column of a 5x2 leaves a 3x2 and a 1x2.
        List<Set<Cell>> parts = ScreenSolver.components(grid(
                "### #",
                "### #"));
        assertEquals(2, parts.size());
        assertEquals(new Rect(0, 0, 3, 2), ScreenSolver.solve(parts.get(0), MAX_W, MAX_H).rect());
        assertEquals(new Rect(4, 0, 1, 2), ScreenSolver.solve(parts.get(1), MAX_W, MAX_H).rect());
    }

    @Test
    void diagonalCellsAreNotConnected() {
        assertEquals(2, ScreenSolver.components(grid(
                "# ",
                " #")).size());
    }

    @Test
    void floodFillFindsTheGroup() {
        Set<Cell> wall = grid(
                "##  #",
                "##  #");
        ScreenSolver.Fill fill = ScreenSolver.floodFill(new Cell(0, 0), wall::contains, 100);
        assertFalse(fill.truncated());
        assertEquals(grid("##", "##"), fill.cells());
    }

    @Test
    void floodFillStopsAtTheCap() {
        ScreenSolver.Fill fill = ScreenSolver.floodFill(new Cell(0, 0), c -> true, 50);
        assertTrue(fill.truncated());
        assertEquals(50, fill.cells().size());
        // A capped wall bigger than any allowed screen is reported as oversized, never as a valid rectangle.
        ScreenSolver.Fill capped = ScreenSolver.floodFill(new Cell(0, 0), c -> true, 4 * 3 + 1);
        Status status = ScreenSolver.solve(capped.cells(), 4, 3).status();
        assertTrue(status == Status.TOO_WIDE || status == Status.TOO_TALL, status.toString());
    }

    @Test
    void floodFillFromEmptySeed() {
        ScreenSolver.Fill fill = ScreenSolver.floodFill(new Cell(0, 0), c -> false, 10);
        assertTrue(fill.cells().isEmpty());
        assertFalse(fill.truncated());
    }

    @Test
    void floodFillExactlyAtTheCapIsNotTruncated() {
        Set<Cell> wall = filled(3, 2);
        ScreenSolver.Fill fill = ScreenSolver.floodFill(new Cell(0, 0), wall::contains, 6);
        assertFalse(fill.truncated());
        assertEquals(6, fill.cells().size());
    }

    @Test
    void largestFilledRectangle() {
        Set<Cell> wall = grid(
                "### ",
                "####",
                "####");
        // The top-right corner is missing: 3x3 (9 cells) beats 4x2 (8 cells).
        assertEquals(new Rect(0, 0, 3, 3), ScreenSolver.largestFilled(new Rect(0, 0, 4, 3), wall::contains));

        Set<Cell> ring = grid(
                "###",
                "# #",
                "###");
        // Ties (3x1 bottom row, 3x1 top row, 1x3 columns) go to the lowest, then left-most.
        assertEquals(new Rect(0, 0, 3, 1), ScreenSolver.largestFilled(new Rect(0, 0, 3, 3), ring::contains));

        assertNull(ScreenSolver.largestFilled(new Rect(0, 0, 3, 3), c -> false));
        assertEquals(new Rect(0, 0, 3, 3), ScreenSolver.largestFilled(new Rect(0, 0, 3, 3), c -> true));
    }

    @Test
    void largestFilledHonoursOffsetBounds() {
        Set<Cell> wall = new HashSet<>(new Rect(10, 20, 2, 2).cells());
        wall.add(new Cell(12, 20));
        assertEquals(new Rect(10, 20, 2, 2), ScreenSolver.largestFilled(new Rect(10, 20, 3, 2), wall::contains));
        assertEquals(new Rect(10, 20, 3, 1), ScreenSolver.largestFilled(new Rect(10, 20, 3, 1), wall::contains));
    }
}
