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
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The only class that talks to the Dream Displays API (plus {@link DdInternals} for the bits the API lacks).
 * Everything here is plain Java calls on the Java-facing API surface (no Kotlin-only helpers), because DD ships
 * its Kotlin runtime relocated.
 *
 * <p>Our own creates, moves, deletes and tag writes run inside {@link #selfChange()}, so the event handlers in
 * {@link DreamTvsAddon} can tell them apart from changes made by players ({@code /display delete}, the DD menu)
 * or by other addons (e.g. dreamdisplays-sable moving a screen onto a sub-level).
 */
public final class DreamDisplaysBridge {
    private static final DreamTvsAddon ADDON = new DreamTvsAddon();
    private static int selfDepth;

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

    /** True while Dream TVs itself is changing a display (so its events are ours, not someone else's). */
    public static boolean isSelfChange() {
        return selfDepth > 0;
    }

    /** DD's world key for a level: the dimension id, e.g. {@code minecraft:overworld}. */
    public static String worldKey(ServerLevel level) {
        return level.dimension().location().toString();
    }

    /** The level key for a DD world key, or {@code null} if it isn't a dimension id. */
    public static @Nullable ResourceKey<Level> levelKey(String world) {
        ResourceLocation id = ResourceLocation.tryParse(world);
        return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
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
        selfDepth++;
        try {
            ServerDisplay display = DreamDisplaysServer.getDisplays().create(
                    owner, worldKey(level),
                    min.getX(), min.getY(), min.getZ(),
                    max.getX(), max.getY(), max.getZ(),
                    toFacing(plane.facing()));
            if (display == null) return null;
            tag(display, new ManagedTag.Bounds(min, max, plane.facing(), System.currentTimeMillis()));
            return display;
        } finally {
            selfDepth--;
        }
    }

    /** Resizes/moves a managed display in place (same id, link and settings). Re-tags it on success. */
    public static CommandResult move(Managed managed, ScreenPlane plane, Rect rect) {
        BlockPos min = BlockPos.min(plane.min(rect), plane.max(rect));
        BlockPos max = BlockPos.max(plane.min(rect), plane.max(rect));
        selfDepth++;
        try {
            CommandResult result = managed.display().moveTo(
                    min.getX(), min.getY(), min.getZ(),
                    max.getX(), max.getY(), max.getZ(),
                    toFacing(plane.facing()));
            if (result == CommandResult.OK) tag(managed.display(), managed.bounds().withBox(min, max));
            return result;
        } finally {
            selfDepth--;
        }
    }

    public static CommandResult delete(ServerDisplay display) {
        selfDepth++;
        try {
            CommandResult result = display.delete();
            if (result == CommandResult.OK) ManagedIndex.remove(display.getId());
            return result;
        } finally {
            selfDepth--;
        }
    }

    /** Writes our tag (and updates the index). Also used to follow a screen another mod moved. */
    static void tag(ServerDisplay display, ManagedTag.Bounds bounds) {
        selfDepth++;
        try {
            CommandResult tagged = ManagedTag.write(display, bounds);
            if (tagged != CommandResult.OK) {
                DreamTvs.LOGGER.warn("Could not tag display {} as managed: {}", display.getId(), tagged);
                return;
            }
            ManagedIndex.put(display.getId(), display.getWorld(), bounds);
        } finally {
            selfDepth--;
        }
    }

    /** Drops our tag: the display becomes a plain Dream Displays screen we no longer manage. */
    static void untag(ServerDisplay display) {
        selfDepth++;
        try {
            display.getCustomData().remove(ManagedTag.KEY);
            ManagedIndex.remove(display.getId());
        } finally {
            selfDepth--;
        }
    }

    /** Resolves an index entry to a live display; stale entries (display gone) are dropped. */
    private static @Nullable Managed resolve(ManagedIndex.Entry entry) {
        ServerDisplay display = DreamDisplaysServer.display(entry.id());
        if (display == null || !display.isAlive()) {
            ManagedIndex.remove(entry.id());
            return null;
        }
        return new Managed(display, entry.bounds());
    }

    private static List<Managed> resolveAll(List<ManagedIndex.Entry> entries) {
        List<Managed> out = new ArrayList<>(entries.size());
        for (ManagedIndex.Entry e : entries) {
            Managed m = resolve(e);
            if (m != null) out.add(m);
        }
        return out;
    }

    /** All displays Dream TVs manages in {@code level}. */
    public static List<Managed> managed(ServerLevel level) {
        return resolveAll(ManagedIndex.inWorld(worldKey(level)));
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
    public static @Nullable Managed managedAt(ServerLevel level, BlockPos pos) {
        for (Managed m : resolveAll(ManagedIndex.at(worldKey(level), pos))) return m;
        return null;
    }

    /** True if {@code id} is a display Dream TVs manages. Safe to call from mixins. */
    public static boolean isManaged(UUID id) {
        if (ManagedIndex.contains(id)) return true;
        ServerDisplay display = DreamDisplaysServer.isAvailable() ? DreamDisplaysServer.display(id) : null;
        return display != null && ManagedTag.isManaged(display);
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
