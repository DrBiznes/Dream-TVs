package com.dreamtvs.screen;

import com.dreamtvs.block.LcdPanelBlock;
import com.dreamtvs.config.DreamTvsConfig;
import com.dreamtvs.dd.DreamDisplaysBridge;
import com.dreamtvs.dd.DreamDisplaysBridge.Managed;
import com.dreamtvs.dd.DreamTvsAddon;
import com.dreamtvs.dd.ManagedIndex;
import com.dreamtvs.screen.solve.Cell;
import com.dreamtvs.screen.solve.Rect;
import com.dreamtvs.screen.solve.ScreenSolver;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Entry points from the block and the loaders. Block changes go through {@link ScreenQueue} and are reconciled
 * at the end of the tick; the two checks that must answer right away (may this TV be placed, may this TV be
 * broken) run here directly.
 */
public final class Screens {
    private Screens() {}

    // --- lifecycle -------------------------------------------------------------------------------------------

    public static void onServerStarting(MinecraftServer server) {
        DreamTvsConfig.load(server.getServerDirectory().resolve("config").resolve("dreamtvs.json"));
        ScreenQueue.clear();
        ManagedIndex.invalidate();
        ScreenSync.reset();
    }

    public static void onServerTickEnd(MinecraftServer server) {
        ScreenSync.tick();
        ScreenQueue.flush(server);
        DreamTvsAddon.endOfTick();
    }

    public static void onServerStopped() {
        ScreenQueue.clear();
        ManagedIndex.invalidate();
        ScreenSync.reset();
    }

    /** A chunk finished loading: re-check managed screens that reach into it (orphans, offline edits). */
    public static void onChunkLoaded(ServerLevel level, ChunkPos chunk) {
        ScreenSync.onChunkLoaded(level, chunk);
    }

    // --- block changes ---------------------------------------------------------------------------------------

    /** A TV appeared or turned at {@code pos} (any cause: player, {@code /setblock}, {@code /fill}, structures). */
    public static void onAdded(ServerLevel level, BlockPos pos, Direction facing) {
        ScreenQueue.mark(level, pos, facing, null);
    }

    /** {@code player} placed the TV at {@code pos}; they get told how the screen turned out. */
    public static void onPlaced(ServerLevel level, BlockPos pos, Direction facing, ServerPlayer player) {
        ScreenQueue.mark(level, pos, facing, player);
    }

    /** A TV left {@code pos} or turned away from {@code facing}. */
    public static void onRemoved(ServerLevel level, BlockPos pos, Direction facing) {
        ScreenQueue.mark(level, pos, facing, null);
    }

    // --- permission checks -----------------------------------------------------------------------------------

    /**
     * May {@code player} put a TV facing {@code facing} at {@code pos}? Not if it would join or merge a screen they
     * aren't allowed to change (plan §5.7); they get told whose screen it is.
     */
    public static boolean canPlace(ServerLevel level, BlockPos pos, Direction facing, ServerPlayer player) {
        if (!DreamDisplaysBridge.isReady() || facing.getAxis() == Direction.Axis.Y) return true;
        ScreenPlane plane = ScreenPlane.of(pos, facing);
        ScreenScanner.View view = new ScreenScanner.View(level, plane, null);
        Set<Cell> joined = new HashSet<>();
        for (Cell n : ScreenSolver.neighbours(plane.cell(pos))) {
            if (!joined.contains(n) && view.filled(n)) joined.addAll(ScreenScanner.group(view, n).cells());
        }
        if (joined.isEmpty()) return true;
        for (Managed m : DreamDisplaysBridge.managedIn(level, plane)) {
            Rect bounds = plane.rect(m.bounds().min(), m.bounds().max());
            if (!intersects(bounds, joined)) continue;
            if (!ScreenPermissions.canModify(player, m.owner())) {
                player.displayClientMessage(Component.translatable("message.dreamtvs.not_yours", ownerName(level, m.owner())), true);
                return false;
            }
        }
        return true;
    }

    /**
     * Runs before Dream Displays' break protection (plan §5.4). If the player may change this screen, it's
     * shrunk, split or removed right now as if the TV were already gone, so DD finds nothing to protect at
     * {@code pos} and the break goes through. If they may not, the screen stays and DD cancels the break.
     */
    public static void beforeBreak(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        if (!(state.getBlock() instanceof LcdPanelBlock) || !DreamDisplaysBridge.isReady()) return;
        Managed m = DreamDisplaysBridge.managedAt(level, pos);
        if (m == null) return;
        if (player instanceof ServerPlayer serverPlayer && !ScreenPermissions.canModify(serverPlayer, m.owner())) {
            serverPlayer.displayClientMessage(Component.translatable("message.dreamtvs.not_yours", ownerName(level, m.owner())), true);
            return;
        }
        Direction facing = state.getValue(LcdPanelBlock.FACING);
        ScreenPlane plane = ScreenPlane.of(pos, facing);
        ScreenReconciler.reconcile(level, plane, List.of(plane.cell(pos)), null, null, pos);
        // If something else cancels the break after us, the end-of-tick pass puts the screen back.
        ScreenQueue.mark(level, pos, facing, null);
    }

    private static boolean intersects(Rect bounds, Set<Cell> cells) {
        for (Cell c : cells) if (bounds.contains(c)) return true;
        return false;
    }

    private static Component ownerName(ServerLevel level, UUID owner) {
        if (owner.equals(ScreenPermissions.SERVER_OWNER)) return Component.translatable("message.dreamtvs.server_owner");
        GameProfileCache cache = level.getServer().getProfileCache();
        String name = cache == null ? owner.toString() : cache.get(owner).map(GameProfile::getName).orElse(owner.toString());
        return Component.literal(name);
    }
}
