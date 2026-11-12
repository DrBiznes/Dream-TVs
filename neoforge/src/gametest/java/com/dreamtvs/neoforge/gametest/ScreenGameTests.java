package com.dreamtvs.neoforge.gametest;

import com.dreamtvs.DreamTvs;
import com.dreamtvs.block.TvBlock;
import com.dreamtvs.config.DreamTvsConfig;
import com.dreamtvs.dd.DreamDisplaysBridge;
import com.dreamtvs.dd.DreamDisplaysBridge.Managed;
import com.dreamtvs.neoforge.DreamTvsNeoForge;
import com.dreamtvs.screen.ScreenPermissions;
import com.dreamtvs.screen.ScreenPlane;
import com.dreamtvs.screen.Screens;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * End-to-end checks against the real Dream Displays server API (plan §8). Every test builds a north-facing wall
 * at z = 2 inside an empty 9×6×5 area. Run with {@code gradlew :neoforge:runGameTestServer}.
 */
@GameTestHolder(DreamTvs.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScreenGameTests {
    private static final String EMPTY = "empty";
    private static final int Z = 2;
    private static final int TIMEOUT = 100;

    // --- helpers ---------------------------------------------------------------------------------------------

    private static BlockState tv() {
        return DreamTvsNeoForge.TV.get().defaultBlockState().setValue(TvBlock.FACING, Direction.NORTH);
    }

    private static BlockPos at(int x, int y) {
        return new BlockPos(x, y, Z);
    }

    /** Places a {@code w}×{@code h} block of TVs with no player (like {@code /fill}). */
    private static void wall(GameTestHelper h, int x0, int y0, int w, int ht) {
        for (int x = x0; x < x0 + w; x++) for (int y = y0; y < y0 + ht; y++) h.setBlock(at(x, y), tv());
    }

    /** Places a TV the way a player does: block first, then {@code setPlacedBy} with the player. */
    private static void placeAs(GameTestHelper h, ServerPlayer player, int x, int y) {
        BlockPos abs = h.absolutePos(at(x, y));
        BlockState state = tv();
        h.getLevel().setBlock(abs, state, Block.UPDATE_ALL);
        state.getBlock().setPlacedBy(h.getLevel(), abs, state, player, ItemStack.EMPTY);
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /** Our screens inside this test's area. */
    private static List<Managed> screens(GameTestHelper h) {
        BlockPos lo = BlockPos.min(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(8, 5, 4)));
        BlockPos hi = BlockPos.max(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(8, 5, 4)));
        ScreenPlane plane = ScreenPlane.of(h.absolutePos(at(0, 0)), Direction.NORTH);
        List<Managed> out = new ArrayList<>();
        for (Managed m : DreamDisplaysBridge.managedIn(h.getLevel(), plane)) {
            BlockPos min = m.bounds().min();
            if (min.getX() >= lo.getX() && min.getX() <= hi.getX() && min.getY() >= lo.getY() && min.getY() <= hi.getY()) {
                out.add(m);
            }
        }
        return out;
    }

    /** The single screen in this area, asserting there is exactly one with this size and relative min corner. */
    private static Managed onlyScreen(GameTestHelper h, int x, int y, int w, int ht) {
        List<Managed> all = screens(h);
        h.assertTrue(all.size() == 1, "expected 1 screen, found " + all.size());
        Managed m = all.get(0);
        assertScreen(h, m, x, y, w, ht);
        return m;
    }

    /** Compares absolute boxes: the test area may be placed rotated, so relative corners can swap. */
    private static void assertScreen(GameTestHelper h, Managed m, int x, int y, int w, int ht) {
        BlockPos a = h.absolutePos(at(x, y));
        BlockPos b = h.absolutePos(at(x + w - 1, y + ht - 1));
        BlockPos min = m.bounds().min();
        BlockPos max = m.bounds().max();
        String got = (max.getX() - min.getX() + 1) + "x" + (max.getY() - min.getY() + 1) + " at " + min.toShortString();
        h.assertTrue(min.equals(BlockPos.min(a, b)) && max.equals(BlockPos.max(a, b)),
                "expected " + w + "x" + ht + " at " + BlockPos.min(a, b).toShortString() + ", got " + got);
        h.assertTrue(m.display().getWidth() == w && m.display().getHeight() == ht,
                "Dream Displays reports " + m.display().getWidth() + "x" + m.display().getHeight());
    }

    private static Managed screenAt(GameTestHelper h, int x, int y) {
        BlockPos abs = h.absolutePos(at(x, y));
        for (Managed m : screens(h)) if (m.bounds().contains(abs)) return m;
        return null;
    }

    private static void assertFormed(GameTestHelper h, int x, int y, boolean formed) {
        BlockState state = h.getBlockState(at(x, y));
        h.assertTrue(state.is(DreamTvsNeoForge.TV.get()), "no TV at " + x + "," + y);
        h.assertTrue(state.getValue(TvBlock.FORMED) == formed, "TV at " + x + "," + y + " formed=" + !formed);
    }

    private static void requireReady(GameTestHelper h) {
        h.assertTrue(DreamDisplaysBridge.isReady(), "Dream Displays server API / Dream TVs addon not ready");
    }

    // --- tests -----------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void rectangleFormsOneScreen(GameTestHelper h) {
        requireReady(h);
        wall(h, 1, 1, 3, 2);
        h.succeedWhen(() -> {
            Managed m = onlyScreen(h, 1, 1, 3, 2);
            h.assertTrue(m.owner().equals(ScreenPermissions.SERVER_OWNER), "player-less wall should be server-owned");
            for (int x = 1; x <= 3; x++) for (int y = 1; y <= 2; y++) assertFormed(h, x, y, true);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void lShapeStaysUnformed(GameTestHelper h) {
        requireReady(h);
        h.setBlock(at(1, 1), tv());
        h.setBlock(at(2, 1), tv());
        h.setBlock(at(1, 2), tv());
        h.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    h.assertTrue(screens(h).isEmpty(), "an L shape must not form a screen");
                    assertFormed(h, 1, 1, false);
                    assertFormed(h, 2, 1, false);
                    assertFormed(h, 1, 2, false);
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void growingKeepsTheSameScreen(GameTestHelper h) {
        requireReady(h);
        AtomicReference<UUID> id = new AtomicReference<>();
        wall(h, 1, 1, 2, 2);
        h.startSequence()
                .thenWaitUntil(() -> id.set(onlyScreen(h, 1, 1, 2, 2).id()))
                // Half a row: not a rectangle, so the screen stays 2x2 and the new TV is unformed.
                .thenExecute(() -> h.setBlock(at(1, 3), tv()))
                .thenIdle(2)
                .thenExecute(() -> {
                    h.assertTrue(onlyScreen(h, 1, 1, 2, 2).id().equals(id.get()), "screen replaced while half-built");
                    assertFormed(h, 1, 3, false);
                })
                .thenExecute(() -> h.setBlock(at(2, 3), tv()))
                .thenWaitUntil(() -> {
                    h.assertTrue(onlyScreen(h, 1, 1, 2, 3).id().equals(id.get()), "grown screen has a new id");
                    assertFormed(h, 1, 3, true);
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void splitGivesTheBiggerPartTheScreen(GameTestHelper h) {
        requireReady(h);
        AtomicReference<UUID> id = new AtomicReference<>();
        wall(h, 1, 1, 4, 1);
        h.startSequence()
                .thenWaitUntil(() -> id.set(onlyScreen(h, 1, 1, 4, 1).id()))
                .thenExecute(() -> h.setBlock(at(3, 1), Blocks.AIR.defaultBlockState()))
                .thenWaitUntil(() -> {
                    h.assertTrue(screens(h).size() == 2, "expected 2 screens after the split, found " + screens(h).size());
                    Managed big = screenAt(h, 1, 1);
                    Managed small = screenAt(h, 4, 1);
                    h.assertTrue(big != null && small != null, "a part has no screen");
                    assertScreen(h, big, 1, 1, 2, 1);
                    assertScreen(h, small, 4, 1, 1, 1);
                    h.assertTrue(big.id().equals(id.get()), "the bigger part should keep the original screen");
                    h.assertTrue(!small.id().equals(id.get()), "the smaller part needs a new screen");
                    h.assertTrue(small.owner().equals(big.owner()), "split-off screen should keep the owner");
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void mergeKeepsTheOlderScreen(GameTestHelper h) {
        requireReady(h);
        AtomicReference<UUID> first = new AtomicReference<>();
        wall(h, 1, 1, 1, 2);
        h.startSequence()
                .thenWaitUntil(() -> first.set(onlyScreen(h, 1, 1, 1, 2).id()))
                .thenIdle(2) // make sure the second screen is created at a later millisecond
                .thenExecute(() -> wall(h, 3, 1, 1, 2))
                .thenWaitUntil(() -> h.assertTrue(screens(h).size() == 2, "expected two separate screens"))
                .thenExecute(() -> wall(h, 2, 1, 1, 2))
                .thenWaitUntil(() -> h.assertTrue(onlyScreen(h, 1, 1, 3, 2).id().equals(first.get()),
                        "merged screen should be the older one"))
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void ownerCanBreakAndTheScreenShrinks(GameTestHelper h) {
        requireReady(h);
        ServerPlayer player = player(h);
        AtomicReference<UUID> id = new AtomicReference<>();
        placeAs(h, player, 1, 1);
        placeAs(h, player, 2, 1);
        h.startSequence()
                .thenWaitUntil(() -> {
                    Managed m = onlyScreen(h, 1, 1, 2, 1);
                    h.assertTrue(m.owner().equals(player.getUUID()), "the placing player should own the screen");
                    id.set(m.id());
                })
                .thenExecute(() -> h.assertTrue(player.gameMode.destroyBlock(h.absolutePos(at(2, 1))),
                        "owner's break was cancelled (Dream Displays protection?)"))
                .thenWaitUntil(() -> {
                    h.assertBlockNotPresent(DreamTvsNeoForge.TV.get(), at(2, 1));
                    h.assertTrue(onlyScreen(h, 1, 1, 1, 1).id().equals(id.get()), "shrunk screen has a new id");
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void strangersCannotBreakOrJoin(GameTestHelper h) {
        requireReady(h);
        ServerPlayer stranger = player(h);
        wall(h, 1, 1, 2, 1); // server-owned
        h.startSequence()
                .thenWaitUntil(() -> onlyScreen(h, 1, 1, 2, 1))
                .thenExecute(() -> {
                    h.assertFalse(stranger.hasPermissions(2), "mock players must not be ops for this test");
                    h.assertFalse(stranger.gameMode.destroyBlock(h.absolutePos(at(2, 1))), "a stranger broke a protected TV");
                    h.assertBlockPresent(DreamTvsNeoForge.TV.get(), at(2, 1));
                    h.assertFalse(Screens.canPlace(h.getLevel(), h.absolutePos(at(3, 1)), Direction.NORTH, stranger),
                            "a stranger may not attach TVs to someone else's screen");
                    h.assertTrue(Screens.canPlace(h.getLevel(), h.absolutePos(at(5, 1)), Direction.NORTH, stranger),
                            "placing away from the screen must be allowed");
                })
                .thenIdle(2)
                .thenExecute(() -> onlyScreen(h, 1, 1, 2, 1))
                .thenSucceed();
    }

    /** Own batch: it changes the global config, so it must not run alongside the other tests. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT, batch = "screen_limit")
    public static void perPlayerLimit(GameTestHelper h) {
        requireReady(h);
        DreamTvsConfig config = DreamTvsConfig.get();
        int before = config.maxScreensPerPlayer;
        config.maxScreensPerPlayer = 1;
        ServerPlayer player = player(h);
        placeAs(h, player, 1, 1);
        h.startSequence()
                .thenWaitUntil(() -> onlyScreen(h, 1, 1, 1, 1))
                .thenExecute(() -> placeAs(h, player, 4, 1))
                .thenIdle(3)
                .thenExecute(() -> {
                    config.maxScreensPerPlayer = before;
                    onlyScreen(h, 1, 1, 1, 1);
                    assertFormed(h, 4, 1, false);
                })
                // Growing an existing screen doesn't count as a new one.
                .thenExecute(() -> {
                    config.maxScreensPerPlayer = 1;
                    placeAs(h, player, 2, 1);
                })
                .thenWaitUntil(() -> onlyScreen(h, 1, 1, 2, 1))
                .thenExecute(() -> config.maxScreensPerPlayer = before)
                .thenSucceed();
    }
}
