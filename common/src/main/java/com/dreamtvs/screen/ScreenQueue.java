package com.dreamtvs.screen;

import com.dreamtvs.DreamTvs;
import com.dreamtvs.dd.DreamDisplaysBridge;
import com.dreamtvs.screen.solve.Cell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * End-of-tick debounce (plan §5.3). TV changes only mark their wall dirty; once per server tick every dirty wall
 * is reconciled once, so {@code /fill} and other bulk edits cost one pass per wall instead of one per block.
 * Server thread only.
 */
public final class ScreenQueue {
    private static final class Pending {
        final Set<Cell> changed = new LinkedHashSet<>();
        @Nullable UUID actor;
        @Nullable Cell actorCell;
    }

    private static Map<ResourceKey<Level>, Map<ScreenPlane, Pending>> pending = new LinkedHashMap<>();

    private ScreenQueue() {}

    /** A TV appeared, disappeared or turned at {@code pos}. {@code actor} is the player who placed it, if any. */
    public static void mark(ServerLevel level, BlockPos pos, Direction facing, @Nullable ServerPlayer actor) {
        if (facing.getAxis() == Direction.Axis.Y) return;
        ScreenPlane plane = ScreenPlane.of(pos, facing);
        Pending p = pending.computeIfAbsent(level.dimension(), k -> new LinkedHashMap<>())
                .computeIfAbsent(plane, k -> new Pending());
        Cell cell = plane.cell(pos);
        p.changed.add(cell);
        if (actor != null) {
            p.actor = actor.getUUID();
            p.actorCell = cell;
        }
    }

    /** Called at the end of every server tick. */
    public static void flush(MinecraftServer server) {
        if (pending.isEmpty()) return;
        Map<ResourceKey<Level>, Map<ScreenPlane, Pending>> work = pending;
        pending = new LinkedHashMap<>();
        boolean ready = DreamDisplaysBridge.isReady();

        for (Map.Entry<ResourceKey<Level>, Map<ScreenPlane, Pending>> world : work.entrySet()) {
            ServerLevel level = server.getLevel(world.getKey());
            if (level == null) continue;
            for (Map.Entry<ScreenPlane, Pending> wall : world.getValue().entrySet()) {
                Pending p = wall.getValue();
                ServerPlayer actor = p.actor == null ? null : server.getPlayerList().getPlayer(p.actor);
                if (!ready) {
                    if (actor != null) actor.displayClientMessage(Component.translatable("message.dreamtvs.not_ready"), true);
                    continue;
                }
                try {
                    ScreenReconciler.reconcile(level, wall.getKey(), p.changed, actor, actor == null ? null : p.actorCell, null);
                } catch (RuntimeException e) {
                    // Never take the server tick down; the next change to this wall reconciles it again.
                    DreamTvs.LOGGER.error("Couldn't update TV screens on wall {} in {}", wall.getKey(), world.getKey().location(), e);
                }
            }
        }
    }

    public static void clear() {
        pending = new LinkedHashMap<>();
    }
}
