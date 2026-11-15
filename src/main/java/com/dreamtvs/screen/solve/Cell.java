package com.dreamtvs.screen.solve;

/**
 * One TV position inside a screen plane. {@code u} runs along the wall (left/right as seen from the world axes),
 * {@code v} is height. Pure value type: no Minecraft classes, so the solver stays unit-testable.
 */
public record Cell(int u, int v) {
    public Cell offset(int du, int dv) {
        return new Cell(u + du, v + dv);
    }
}
