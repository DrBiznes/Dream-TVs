package com.dreamtvs.screen.solve;

import java.util.ArrayList;
import java.util.List;

/** An axis-aligned rectangle of cells in a screen plane. {@code width} and {@code height} are at least 1. */
public record Rect(int minU, int minV, int width, int height) {
    public Rect {
        if (width < 1 || height < 1) throw new IllegalArgumentException("empty rect " + width + "x" + height);
    }

    public static Rect of(Cell a, Cell b) {
        int minU = Math.min(a.u(), b.u());
        int minV = Math.min(a.v(), b.v());
        return new Rect(minU, minV, Math.abs(a.u() - b.u()) + 1, Math.abs(a.v() - b.v()) + 1);
    }

    public int maxU() {
        return minU + width - 1;
    }

    public int maxV() {
        return minV + height - 1;
    }

    public int area() {
        return width * height;
    }

    public Cell min() {
        return new Cell(minU, minV);
    }

    public Cell max() {
        return new Cell(maxU(), maxV());
    }

    public boolean contains(Cell c) {
        return c.u() >= minU && c.u() <= maxU() && c.v() >= minV && c.v() <= maxV();
    }

    public boolean contains(Rect r) {
        return r.minU >= minU && r.maxU() <= maxU() && r.minV >= minV && r.maxV() <= maxV();
    }

    public boolean intersects(Rect r) {
        return r.minU <= maxU() && r.maxU() >= minU && r.minV <= maxV() && r.maxV() >= minV;
    }

    public List<Cell> cells() {
        List<Cell> out = new ArrayList<>(area());
        for (int v = minV; v <= maxV(); v++) {
            for (int u = minU; u <= maxU(); u++) out.add(new Cell(u, v));
        }
        return out;
    }

    @Override
    public String toString() {
        return width + "x" + height + "@(" + minU + "," + minV + ")";
    }
}
