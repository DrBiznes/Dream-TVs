package com.dreamtvs.dd;

import com.dreamdisplays.api.CommandResult;
import com.dreamdisplays.api.display.model.property.DisplayFacing;
import com.dreamdisplays.api.server.DreamDisplaysServer;
import com.dreamdisplays.api.server.ServerDisplay;
import com.dreamtvs.DreamTvs;
import com.dreamtvs.screen.ScreenPlane;
import com.dreamtvs.screen.solve.Rect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The only class that talks to the Dream Displays API. Everything here is plain Java calls on the
 * Java-facing API surface (no Kotlin-only helpers), because DD ships its Kotlin runtime relocated.
 */
public final class DreamDisplaysBridge {
    private static final DreamTvsAddon ADDON = new DreamTvsAddon();

    private DreamDisplaysBridge() {}

    /** A managed display together with the bounds we stored on it. */
    public record Managed(ServerDisplay display, ManagedTag.Bounds bounds) {
        public UUID id() {
            return display.getId();
        }

        public UUID owner() {
            return display.getOwnerId();
        }
    }

    public static void init() {
        DreamDisplaysServer.onReady(() -> {
            DreamTvs.LOGGER.info("Dream Displays server API is ready; registering addon");
            DreamDisplaysServer.register(ADDON);
        });
    }

    public static boolean isReady() {
        return DreamDisplaysServer.isAvailable() && ADDON.isEnabled();
    }

    /** DD's world key for a level: the dimension id, e.g. {@code minecraft:overworld}. */
    public static String worldKey(ServerLevel level) {
        return level.dimension().location().toString();
    }

    public static DisplayFacing toFacing(Direction direction) {
        return switch (direction) {
            case NORTH -> DisplayFacing.NORTH;
            case SOUTH -> DisplayFacing.SOUTH;
            case EAST -> DisplayFacing.EAST;
            case WEST -> DisplayFacing.WEST;
            case UP -> DisplayFacing.UP;
            case DOWN -> DisplayFacing.DOWN;
        };
    }

    /** Creates a managed display over {@code rect}. Returns {@code null} when DD refuses (e.g. overlap). */
    public static ServerDisplay create(UUID owner, ServerLevel level, ScreenPlane plane, Rect rect) {
        BlockPos min = BlockPos.min(plane.min(rect), plane.max(rect));
        BlockPos max = BlockPos.max(plane.min(rect), plane.max(rect));
        ServerDisplay display = DreamDisplaysServer.getDisplays().create(
                owner, worldKey(level),
                min.getX(), min.getY(), min.getZ(),
                max.getX(), max.getY(), max.getZ(),
                toFacing(plane.facing()));
        if (display == null) return null;
        tag(display, new ManagedTag.Bounds(min, max, plane.facing(), System.currentTimeMillis()));
        return display;
    }

    /** Resizes/moves a managed display in place (same id, link and settings). Re-tags it on success. */
    public static CommandResult move(Managed managed, ScreenPlane plane, Rect rect) {
        BlockPos min = BlockPos.min(plane.min(rect), plane.max(rect));
        BlockPos max = BlockPos.max(plane.min(rect), plane.max(rect));
        CommandResult result = managed.display().moveTo(
                min.getX(), min.getY(), min.getZ(),
                max.getX(), max.getY(), max.getZ(),
                toFacing(plane.facing()));
        if (result == CommandResult.OK) tag(managed.display(), managed.bounds().withBox(min, max));
        return result;
    }

    public static CommandResult delete(ServerDisplay display) {
        return display.delete();
    }

    private static void tag(ServerDisplay display, ManagedTag.Bounds bounds) {
        CommandResult tagged = ManagedTag.write(display, bounds);
        if (tagged != CommandResult.OK) {
            DreamTvs.LOGGER.warn("Could not tag display {} as managed: {}", display.getId(), tagged);
        }
    }

    /** All displays Dream TVs manages in {@code level}. */
    public static List<Managed> managed(ServerLevel level) {
        String world = worldKey(level);
        List<Managed> out = new ArrayList<>();
        for (ServerDisplay display : DreamDisplaysServer.getDisplays()) {
            if (!display.isAlive() || !display.getWorld().equals(world)) continue;
            ManagedTag.Bounds bounds = ManagedTag.read(display);
            if (bounds != null) out.add(new Managed(display, bounds));
        }
        return out;
    }

    /** Managed displays lying in {@code plane} (same facing, same wall depth). */
    public static List<Managed> managedIn(ServerLevel level, ScreenPlane plane) {
        List<Managed> out = new ArrayList<>();
        for (Managed m : managed(level)) {
            if (m.bounds().facing() == plane.facing() && plane.contains(m.bounds().min()) && plane.contains(m.bounds().max())) {
                out.add(m);
            }
        }
        return out;
    }

    /** The managed display whose box contains {@code pos}, or {@code null}. */
    public static Managed managedAt(ServerLevel level, BlockPos pos) {
        for (Managed m : managed(level)) {
            if (m.bounds().contains(pos)) return m;
        }
        return null;
    }

    /** How many managed screens {@code owner} has, in every world (for {@code maxScreensPerPlayer}). */
    public static int countOwnedBy(UUID owner) {
        int count = 0;
        for (ServerDisplay display : DreamDisplaysServer.getDisplays().ownedBy(owner)) {
            if (display.isAlive() && ManagedTag.isManaged(display)) count++;
        }
        return count;
    }

    public static ServerDisplay get(UUID id) {
        return DreamDisplaysServer.display(id);
    }
}
