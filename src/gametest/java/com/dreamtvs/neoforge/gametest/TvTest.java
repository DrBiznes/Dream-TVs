package com.dreamtvs.neoforge.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Dream TVs GameTest: a {@code public static void name(GameTestHelper)} method in one of
 * {@link TvGameTests#CLASSES}. Every test runs in the empty 9×6×5 structure {@code dreamtvs:empty}. Our own
 * annotation, because Minecraft 1.21.5 replaced the {@code @GameTest} one; {@link TvGameTests} registers the
 * tests in whichever way the running version expects.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TvTest {
    /** Tests in another batch never run at the same time as the default batch (e.g. ones that change config). */
    String batch() default "";

    int timeoutTicks() default 100;
}
