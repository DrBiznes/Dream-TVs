package com.dreamtvs.screen;

import com.dreamdisplays.api.CommandResult;
import com.dreamdisplays.api.server.ServerDisplay;
import com.dreamtvs.DreamTvs;
import com.dreamtvs.McCompat;
import com.dreamtvs.block.ScreenBlock;
import com.dreamtvs.config.DreamTvsConfig;
import com.dreamtvs.dd.DreamDisplaysBridge;
import com.dreamtvs.dd.DreamDisplaysBridge.Managed;
import com.dreamtvs.screen.solve.Cell;
import com.dreamtvs.screen.solve.Rect;
import com.dreamtvs.screen.solve.ScreenPlanner;
import com.dreamtvs.screen.solve.ScreenPlanner.Action;
import com.dreamtvs.screen.solve.ScreenPlanner.Create;
import com.dreamtvs.screen.solve.ScreenPlanner.Delete;
import com.dreamtvs.screen.solve.ScreenPlanner.Existing;
import com.dreamtvs.screen.solve.ScreenPlanner.Group;
import com.dreamtvs.screen.solve.ScreenPlanner.Keep;
import com.dreamtvs.screen.solve.ScreenPlanner.Move;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Makes the Dream Displays screens on one wall match the TV blocks on it. The world is the source
 * of truth and every run is idempotent, so a missed update or a crash heals on the next change to the wall.
 *
 * <p>Steps: scan the groups around the changed cells, collect our screens on that wall, ask
 * {@link ScreenPlanner} what to do, apply it (deletes, then moves, then creates, so DD's overlap checks pass),
 * then set {@link ScreenBlock#FORMED} on the blocks and tell the acting player what happened.
 */
public final class ScreenReconciler {
    private ScreenReconciler() {}

    /** What happened to a group's rectangle, for the action-bar message. */
    private enum Outcome { FORMED, UNCHANGED, NO_PERMISSION, LIMIT, REFUSED }

    /**
     * @param changed   cells that changed on this wall since the last run
     * @param actor     player to report to (the one who placed a TV), or {@code null}
     * @param actorCell the cell that player changed
     * @param ignored   a TV that is about to be broken: treated as already gone
     */
    public static void reconcile(ServerLevel level, ScreenPlane plane, Collection<Cell> changed,
                                 @Nullable ServerPlayer actor, @Nullable Cell actorCell, @Nullable BlockPos ignored) {
        ScreenScanner.View view = new ScreenScanner.View(level, plane, ignored);
        List<Group> groups = ScreenScanner.groupsAround(view, changed);

        Set<Cell> scope = new HashSet<>(changed);
        for (Group g : groups) scope.addAll(g.cells());

        Map<UUID, Managed> byId = new HashMap<>();
        List<Existing> existing = new ArrayList<>();
        for (Managed m : DreamDisplaysBridge.managedIn(level, plane)) {
            Rect bounds = plane.rect(m.bounds().min(), m.bounds().max());
            if (!touches(bounds, scope)) continue;
            byId.put(m.id(), m);
            existing.add(new Existing(m.id(), bounds, m.bounds().createdAt()));
        }

        List<Action> actions = ScreenPlanner.plan(groups, existing, view::filled);
        if (view.hitUnloaded()) {
            // Part of this wall is in an unloaded chunk; acting now could shrink a screen that's really whole.
            DreamTvs.LOGGER.debug("Skipping wall {}: it reaches into an unloaded chunk", plane);
            return;
        }

        List<Rect> live = new ArrayList<>();
        Map<Rect, Outcome> outcomes = new HashMap<>();
        apply(level, plane, actions, byId, actor, live, outcomes);

        Set<Cell> touched = new LinkedHashSet<>(scope);
        for (Existing e : existing) touched.addAll(e.bounds().cells());
        updateFormed(level, view, touched, live);

        if (actor != null && actorCell != null) report(actor, actorCell, groups, outcomes);
    }

    private static void apply(ServerLevel level, ScreenPlane plane, List<Action> actions, Map<UUID, Managed> byId,
                              @Nullable ServerPlayer actor, List<Rect> live, Map<Rect, Outcome> outcomes) {
        List<Move> moves = new ArrayList<>();
        List<Create> creates = new ArrayList<>();
        for (Action action : actions) {
            switch (action) {
                case Keep keep -> {
                    live.add(keep.rect());
                    outcomes.put(keep.rect(), Outcome.UNCHANGED);
                }
                case Delete delete -> {
                    Managed m = byId.get(delete.id());
                    CommandResult result = DreamDisplaysBridge.delete(m.display());
                    if (result != CommandResult.OK) {
                        DreamTvs.LOGGER.warn("Dream Displays refused to remove screen {}: {}", delete.id(), result);
                        live.add(plane.rect(m.bounds().min(), m.bounds().max()));
                    }
                }
                case Move move -> moves.add(move);
                case Create create -> creates.add(create);
            }
        }

        // A move can be refused while another screen still sits where it's going (e.g. two screens trading
        // space after a split). Retry until nothing more succeeds.
        boolean progress = true;
        while (!moves.isEmpty() && progress) {
            progress = false;
            for (Iterator<Move> it = moves.iterator(); it.hasNext(); ) {
                Move move = it.next();
                if (DreamDisplaysBridge.move(byId.get(move.id()), plane, move.to()) == CommandResult.OK) {
                    live.add(move.to());
                    outcomes.put(move.to(), Outcome.FORMED);
                    it.remove();
                    progress = true;
                }
            }
        }
        for (Move stuck : moves) {
            DreamTvs.LOGGER.warn("Dream Displays refused to resize screen {} from {} to {}", stuck.id(), stuck.from(), stuck.to());
            live.add(stuck.from());
            outcomes.put(stuck.to(), Outcome.REFUSED);
        }

        for (Create create : creates) {
            outcomes.put(create.rect(), create(level, plane, create, byId, actor, live));
        }
    }

    private static Outcome create(ServerLevel level, ScreenPlane plane, Create create, Map<UUID, Managed> byId,
                                  @Nullable ServerPlayer actor, List<Rect> live) {
        UUID owner;
        if (create.parent() != null && byId.containsKey(create.parent())) {
            owner = byId.get(create.parent()).owner(); // split off an existing screen: same owner
        } else if (actor != null) {
            if (!ScreenPermissions.canCreate(actor)) return Outcome.NO_PERMISSION;
            owner = actor.getUUID();
        } else {
            owner = ScreenPermissions.SERVER_OWNER; // e.g. /fill or /setblock
        }

        int limit = DreamTvsConfig.get().maxScreensPerPlayer;
        if (limit > 0 && !owner.equals(ScreenPermissions.SERVER_OWNER) && DreamDisplaysBridge.countOwnedBy(owner) >= limit) {
            ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
            if (ownerPlayer == null || !ScreenPermissions.canBypassLimit(ownerPlayer)) return Outcome.LIMIT;
        }

        ServerDisplay display = DreamDisplaysBridge.create(owner, level, plane, create.rect());
        if (display == null) {
            DreamTvs.LOGGER.warn("Dream Displays refused to create a screen at {} on {}", create.rect(), plane);
            return Outcome.REFUSED;
        }
        live.add(create.rect());
        return Outcome.FORMED;
    }

    /** Sets FORMED on every TV we looked at: on if a live screen covers it, off otherwise. */
    private static void updateFormed(ServerLevel level, ScreenScanner.View view, Set<Cell> cells, List<Rect> live) {
        for (Cell cell : cells) {
            if (!view.filled(cell)) continue;
            BlockPos pos = view.plane().pos(cell);
            BlockState state = level.getBlockState(pos);
            boolean formed = covered(live, cell);
            if (state.getValue(ScreenBlock.FORMED) != formed) {
                level.setBlock(pos, state.setValue(ScreenBlock.FORMED, formed), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void report(ServerPlayer actor, Cell actorCell, List<Group> groups, Map<Rect, Outcome> outcomes) {
        Group group = null;
        for (Group g : groups) if (g.cells().contains(actorCell)) group = g;
        if (group == null) return;
        DreamTvsConfig config = DreamTvsConfig.get();
        boolean chatty = config.showFormMessages;

        Component message = null;
        if (group.solution().valid()) {
            Rect rect = group.solution().rect();
            switch (outcomes.getOrDefault(rect, Outcome.UNCHANGED)) {
                case FORMED -> message = chatty ? Component.translatable("message.dreamtvs.formed", rect.width(), rect.height()) : null;
                case NO_PERMISSION -> message = Component.translatable("message.dreamtvs.no_permission");
                case LIMIT -> message = Component.translatable("message.dreamtvs.limit_reached", config.maxScreensPerPlayer);
                case REFUSED -> message = Component.translatable("message.dreamtvs.refused");
                case UNCHANGED -> { }
            }
        } else if (chatty) {
            message = switch (group.solution().status()) {
                case NOT_RECTANGLE -> Component.translatable("message.dreamtvs.not_rectangle");
                case TOO_WIDE -> Component.translatable("message.dreamtvs.too_wide", config.maxWidth);
                case TOO_TALL -> Component.translatable("message.dreamtvs.too_tall", config.maxHeight);
                case EMPTY, VALID -> null;
            };
        }
        if (message != null) McCompat.overlay(actor, message);
    }

    private static boolean touches(Rect bounds, Set<Cell> cells) {
        if (bounds.area() <= cells.size()) {
            for (Cell c : bounds.cells()) if (cells.contains(c)) return true;
            return false;
        }
        for (Cell c : cells) if (bounds.contains(c)) return true;
        return false;
    }

    private static boolean covered(List<Rect> rects, Cell cell) {
        for (Rect r : rects) if (r.contains(cell)) return true;
        return false;
    }
}
