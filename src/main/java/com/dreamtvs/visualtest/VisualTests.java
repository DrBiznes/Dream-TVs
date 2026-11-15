package com.dreamtvs.visualtest;

import com.dreamtvs.dd.DreamDisplaysBridge;
import com.dreamtvs.dd.DreamDisplaysBridge.Managed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

//? if >=1.21.11 {
/*import net.minecraft.world.level.gamerules.GameRules;
*///?} else
import net.minecraft.world.level.GameRules;

/**
 * Visual tests: checks by screenshot that Dream Displays draws its picture on every kind of TV. Only in dev runs
 * ({@code gradlew :<version>-<loader>:runVisualTest}); the package is left out of the mod jar, and nothing here
 * runs unless the {@value #PROPERTY} system property is set.
 *
 * <p>The game is driven from the client tick: once the title screen is up, a flat creative world is created, and
 * each {@link Case} is built in front of a grey wall high in the air, with the camera straight in front of it.
 * A case passes when the server has formed the expected Dream Displays screens. With no video set, a screen shows
 * DD's loading picture (black, with a blue bar moving along the bottom), which is what the screenshots are for:
 * {@code run/<loader>-<version>-visualtest/screenshots/<case>.png}. The outcome of every case is written to
 * {@code visualtest-results.txt} next to that folder, and the game exits with code 1 if any failed.
 *
 * <p>Everything in the world is done with commands, which are the same on every Minecraft version.
 */
public final class VisualTests {
    public static final String PROPERTY = "dreamtvs.visualTest";

    private static final Logger LOG = LoggerFactory.getLogger("DreamTVs/VisualTest");
    private static final String WORLD = "dreamtvs_visualtest";

    /** The bottom row of every screen, and the z of the TVs. They face north; the wall is one block behind. */
    private static final int Y = 100;
    private static final int Z = 9;

    /** tan(35°): half of the default 70° field of view. */
    private static final double TAN_HALF_FOV = 0.7002;
    private static final double ASPECT = 16.0 / 9.0;
    /** How much of the frame the TVs fill, along whichever of width and height is tighter. */
    private static final double FILL = 0.6;

    private static final int TICKS_BEFORE_WORLD = 40;
    private static final int TICKS_AFTER_JOIN = 60;
    private static final int FORM_TIMEOUT = 200;
    /** From the screens forming on the server to the screenshot: the client gets them and redraws the chunk. */
    private static final int SETTLE_TICKS = 60;
    /** DD's loading bar takes this long to cross the screen. */
    private static final long BAR_NANOS = 1_300_000_000L;
    private static final int BAR_TICKS = 26;
    private static final int SAVE_TICKS = 10;
    private static final int TOTAL_TIMEOUT = 20 * 60 * 5;

    /** A Dream Displays screen we expect, in blocks from the bottom left block as the viewer sees it. */
    private record Expected(int x, int y, int width, int height) {}

    /**
     * @param build  the commands that place the TVs
     * @param width, height the size of everything placed, for the camera
     */
    private record Case(String name, List<String> build, List<Expected> screens, int width, int height) {}

    private enum Phase { BOOT, JOINING, BUILD, FORMING, SETTLE, SAVING, DONE }

    private static final List<Case> CASES = cases();
    private static final List<String> results = new ArrayList<>();
    private static Phase phase = Phase.BOOT;
    private static int index;
    private static int wait;
    private static int total;
    private static boolean failed;
    private static CompletableFuture<String> check;
    private static String lastProblem = "";

    private VisualTests() {}

    // --- the cases ---------------------------------------------------------------------------------------------

    private static List<Case> cases() {
        List<Case> out = new ArrayList<>();
        // Walls of blocks that merge into one screen, at various sizes.
        for (String kind : List.of("lcd_panel", "jumbotron")) {
            for (int[] size : new int[][] {{1, 1}, {2, 1}, {1, 2}, {2, 2}, {3, 2}, {4, 3}, {7, 4}}) {
                out.add(wall(kind, size[0], size[1]));
            }
        }
        out.add(wall("crt_tv", 1, 1));
        // CRTs never merge: three in a row are three screens.
        out.add(new Case("crt_tv_row_of_3", List.of(fill("crt_tv", 0, 0, 3, 1)),
                List.of(new Expected(0, 0, 1, 1), new Expected(1, 0, 1, 1), new Expected(2, 0, 1, 1)), 3, 1));
        out.add(flat("flat_screen_2x1", 2, 1));
        out.add(flat("flat_screen_3x2", 3, 2));
        // Different kinds on one wall stay separate screens.
        out.add(new Case("jumbotron_2x2_beside_lcd_panel_2x2",
                List.of(fill("jumbotron", 0, 0, 2, 2), fill("lcd_panel", 2, 0, 2, 2)),
                List.of(new Expected(0, 0, 2, 2), new Expected(2, 0, 2, 2)), 4, 2));
        return out;
    }

    private static Case wall(String kind, int width, int height) {
        return new Case(kind + "_" + width + "x" + height, List.of(fill(kind, 0, 0, width, height)),
                List.of(new Expected(0, 0, width, height)), width, height);
    }

    /** A Flat Screen, block by block: part {@code row * width + column}, column 0 on the viewer's left. */
    private static Case flat(String kind, int width, int height) {
        List<String> build = new ArrayList<>();
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                BlockPos pos = pos(column, row);
                build.add("setblock %d %d %d dreamtvs:%s[facing=north,part=%d]"
                        .formatted(pos.getX(), pos.getY(), pos.getZ(), kind, row * width + column));
            }
        }
        return new Case(kind, build, List.of(new Expected(0, 0, width, height)), width, height);
    }

    private static String fill(String kind, int x, int y, int width, int height) {
        BlockPos a = pos(x, y);
        BlockPos b = pos(x + width - 1, y + height - 1);
        return "fill %d %d %d %d %d %d dreamtvs:%s[facing=north]"
                .formatted(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ(), kind);
    }

    /** The block {@code x} to the right of and {@code y} above the bottom left one. North-facing: right is -x. */
    private static BlockPos pos(int x, int y) {
        return new BlockPos(-x, Y + y, Z);
    }

    // --- the driver --------------------------------------------------------------------------------------------

    /** Every client tick. */
    public static void tick(Minecraft mc) {
        if (phase == Phase.DONE) return;
        try {
            if (++total > TOTAL_TIMEOUT) {
                fail("timeout", "gave up after " + TOTAL_TIMEOUT + " ticks in phase " + phase);
                finish(mc);
                return;
            }
            // The mouse stays free: the window may get the focus at any time, and the camera mustn't turn.
            if (mc.level != null && mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.releaseMouse();
            step(mc);
        } catch (Throwable t) {
            LOG.error("Visual test driver failed", t);
            fail("driver", t.toString());
            finish(mc);
        }
    }

    private static void step(Minecraft mc) throws IOException {
        switch (phase) {
            case BOOT -> {
                // Wait for the resource loading overlay to go and the title screen (or a first-run screen) to show.
                if (overlayShowing(mc) || screen(mc) == null) {
                    wait = 0;
                } else if (++wait >= TICKS_BEFORE_WORLD) {
                    // Without this the game pauses (and the server with it) when the window isn't focused.
                    mc.options.pauseOnLostFocus = false;
                    deleteOldOutput(mc);
                    createWorld(mc);
                    next(Phase.JOINING);
                }
            }
            case JOINING -> {
                if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
                    wait = 0;
                } else if (++wait >= TICKS_AFTER_JOIN) {
                    closeScreen(mc);
                    hideHud(mc);
                    run(mc, List.of("gamemode spectator @a", "time set noon", "weather clear",
                            "fill -12 %d %d 5 %d %d minecraft:light_gray_concrete".formatted(Y - 4, Z + 1, Y + 8, Z + 1)));
                    next(Phase.BUILD);
                }
            }
            case BUILD -> {
                if (index >= CASES.size()) {
                    finish(mc);
                    return;
                }
                Case test = CASES.get(index);
                List<String> commands = new ArrayList<>();
                commands.add("fill -12 %d %d 5 %d %d minecraft:air".formatted(Y - 4, Z, Y + 8, Z));
                commands.add("kill @e[type=minecraft:item]");
                commands.addAll(test.build());
                commands.add(camera(test));
                run(mc, commands);
                LOG.info("Case {}/{}: {}", index + 1, CASES.size(), test.name());
                check = null;
                next(Phase.FORMING);
            }
            case FORMING -> {
                Case test = CASES.get(index);
                if (check == null) {
                    IntegratedServer server = mc.getSingleplayerServer();
                    check = server.submit(() -> problem(server, test));
                } else if (check.isDone()) {
                    lastProblem = check.join();
                    check = null;
                    if (lastProblem.isEmpty()) {
                        next(Phase.SETTLE);
                        return;
                    }
                }
                if (++wait > FORM_TIMEOUT) {
                    fail(test.name(), lastProblem);
                    lastProblem = "failed";
                    next(Phase.SETTLE); // still take the screenshot, to see what's there
                }
            }
            case SETTLE -> {
                Case test = CASES.get(index);
                // Again, in case the mouse turned the camera before it was let go.
                if (++wait == SETTLE_TICKS - 10) run(mc, List.of(camera(test)));
                if (wait < SETTLE_TICKS || (!barInView() && wait < SETTLE_TICKS + 2 * BAR_TICKS)) return;
                screenshot(mc, test.name() + ".png");
                if (lastProblem.isEmpty()) results.add("PASS " + test.name());
                lastProblem = "";
                next(Phase.SAVING);
            }
            case SAVING -> {
                if (++wait < SAVE_TICKS) return;
                index++;
                next(Phase.BUILD);
            }
            case DONE -> {}
        }
    }

    /**
     * Whether the blue bar of DD's loading picture is near the middle of its track. It crosses the screen once
     * every 1.3 seconds of {@link System#nanoTime} and is out of sight at either end, so the screenshot waits
     * for the middle. (DD 1.10.9's {@code ScreenRenderer.renderPlaceholder}; if that changes, the bar is just
     * somewhere else, or missing from some screenshots.)
     */
    private static boolean barInView() {
        double cycle = System.nanoTime() % BAR_NANOS / (double) BAR_NANOS;
        return cycle > 0.4 && cycle < 0.55;
    }

    private static void next(Phase to) {
        phase = to;
        wait = 0;
    }

    /** Puts the camera straight in front of the middle of the TVs, far enough back to see all of them. */
    private static String camera(Case test) {
        double distance = Math.max(test.height() / 2.0 / TAN_HALF_FOV, test.width() / 2.0 / (TAN_HALF_FOV * ASPECT)) / FILL;
        double x = 1.0 - test.width() / 2.0;
        double eyeY = Y + test.height() / 2.0;
        // Yaw 0 looks south, at the north-facing TVs. The position is the feet; a spectator's eyes are 1.62 up.
        return String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f 0 0", x, eyeY - 1.62, Z - distance);
    }

    /** On the server thread: why {@code test}'s screens aren't all there yet, or "" if they are. */
    private static String problem(IntegratedServer server, Case test) {
        if (!DreamDisplaysBridge.isReady()) return "Dream Displays server API / Dream TVs addon not ready";
        for (Expected e : test.screens()) {
            String where = e.width() + "x" + e.height() + " screen at " + e.x() + "," + e.y();
            Managed corner = DreamDisplaysBridge.managedAt(server.overworld(), pos(e.x(), e.y()));
            Managed opposite = DreamDisplaysBridge.managedAt(server.overworld(), pos(e.x() + e.width() - 1, e.y() + e.height() - 1));
            if (corner == null || opposite == null) return "no " + where;
            if (!corner.id().equals(opposite.id())) return where + " is more than one screen";
            int width = corner.display().getWidth();
            int height = corner.display().getHeight();
            if (width != e.width() || height != e.height()) return where + " is " + width + "x" + height;
        }
        return "";
    }

    private static void run(Minecraft mc, List<String> commands) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
            for (String command : commands) server.getCommands().performPrefixedCommand(source, command);
        });
    }

    private static void fail(String name, String why) {
        failed = true;
        results.add("FAIL " + name + ": " + why);
        LOG.error("FAIL {}: {}", name, why);
    }

    private static void finish(Minecraft mc) {
        phase = Phase.DONE;
        Path file = mc.gameDirectory.toPath().resolve("visualtest-results.txt");
        try {
            Files.write(file, results);
        } catch (IOException e) {
            LOG.error("Couldn't write {}", file, e);
        }
        LOG.info("Visual tests done: {} of {} cases, {}. Screenshots are in {}", index, CASES.size(),
                failed ? "FAILED" : "all passed", mc.gameDirectory.toPath().resolve("screenshots"));
        // A normal exit is code 0 whatever happened, so a failed run ends the hard way.
        if (failed) Runtime.getRuntime().halt(1);
        mc.stop();
    }

    /** Last run's world, screenshots and results. */
    private static void deleteOldOutput(Minecraft mc) throws IOException {
        Path game = mc.gameDirectory.toPath();
        deleteTree(game.resolve("saves").resolve(WORLD));
        deleteTree(game.resolve("screenshots"));
        Files.deleteIfExists(game.resolve("visualtest-results.txt"));
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    // --- what differs between Minecraft versions ---------------------------------------------------------------

    /** A flat, peaceful creative world with commands, and straight into it. */
    private static void createWorld(Minecraft mc) {
        //? if >=26.1 {
        /*LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        *///?} else if >=1.21.11 {
        /*LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
        *///?} else {
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        //?}
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0L, false, false),
                //? if >=1.21.11 {
                /*registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                *///?} else
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                screen(mc));
    }

    private static Screen screen(Minecraft mc) {
        //? if >=26.1 {
        /*return mc.gui.screen();
        *///?} else
        return mc.screen;
    }

    private static void closeScreen(Minecraft mc) {
        //? if >=26.1 {
        /*mc.gui.setScreen(null);
        *///?} else
        mc.setScreen(null);
    }

    private static boolean overlayShowing(Minecraft mc) {
        //? if >=26.1 {
        /*return mc.gui.overlay() != null;
        *///?} else
        return mc.getOverlay() != null;
    }

    /** No hotbar, crosshair or chat in the screenshots. */
    private static void hideHud(Minecraft mc) {
        //? if >=26.1 {
        /*if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        *///?} else
        mc.options.hideGui = true;
    }

    /** Saves the frame as {@code screenshots/<name>} in the game folder. */
    private static void screenshot(Minecraft mc, String name) {
        //? if >=26.1 {
        /*Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, message -> {});
        *///?} else if >=1.21.11 {
        /*Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), 1, message -> {});
        *///?} else {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {});
        //?}
    }
}
