package com.dreamtvs.dd;

import com.dreamdisplays.api.CommandResult;
import com.dreamdisplays.api.display.model.property.DisplayFacing;
import com.dreamdisplays.api.server.DreamDisplaysServer;
import com.dreamdisplays.api.server.ServerDisplay;
import com.dreamtvs.DreamTvs;
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

    /** Creates a managed display over the box {@code a..b}. Returns {@code null} when DD refuses. */
    public static ServerDisplay create(UUID owner, ServerLevel level, BlockPos a, BlockPos b, Direction facing) {
        BlockPos min = BlockPos.min(a, b);
        BlockPos max = BlockPos.max(a, b);
        ServerDisplay display = DreamDisplaysServer.getDisplays().create(
                owner, worldKey(level),
                min.getX(), min.getY(), min.getZ(),
                max.getX(), max.getY(), max.getZ(),
                toFacing(facing));
        if (display == null) return null;
        CommandResult tagged = ManagedTag.write(display, new ManagedTag.Bounds(min, max, facing));
        if (tagged != CommandResult.OK) {
            DreamTvs.LOGGER.warn("Could not tag display {} as managed: {}", display.getId(), tagged);
        }
        return display;
    }

    /** All displays Dream TVs manages in {@code level}. */
    public static List<ServerDisplay> managed(ServerLevel level) {
        String world = worldKey(level);
        List<ServerDisplay> out = new ArrayList<>();
        for (ServerDisplay display : DreamDisplaysServer.getDisplays()) {
            if (display.getWorld().equals(world) && ManagedTag.isManaged(display)) out.add(display);
        }
        return out;
    }

    /** The managed display whose box contains {@code pos}, or {@code null}. */
    public static ServerDisplay managedAt(ServerLevel level, BlockPos pos) {
        for (ServerDisplay display : managed(level)) {
            ManagedTag.Bounds bounds = ManagedTag.read(display);
            if (bounds != null && bounds.contains(pos)) return display;
        }
        return null;
    }
}
