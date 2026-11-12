package com.dreamtvs.dd;

import com.dreamdisplays.api.CommandResult;
import com.dreamdisplays.api.server.ServerDisplay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.nio.ByteBuffer;

/**
 * What Dream TVs stores in a display's {@code customData}. DD doesn't expose a display's corners or facing,
 * so we keep our own copy: 6 ints (min x/y/z, max x/y/z) followed by one facing byte (3D data value).
 */
public final class ManagedTag {
    public static final String KEY = "dreamtvs:screen";
    private static final int SIZE = 6 * Integer.BYTES + 1;

    private ManagedTag() {}

    public record Bounds(BlockPos min, BlockPos max, Direction facing) {
        public boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }
    }

    public static boolean isManaged(ServerDisplay display) {
        return display.getCustomData().get(KEY) != null;
    }

    public static CommandResult write(ServerDisplay display, Bounds bounds) {
        ByteBuffer buf = ByteBuffer.allocate(SIZE)
                .putInt(bounds.min().getX()).putInt(bounds.min().getY()).putInt(bounds.min().getZ())
                .putInt(bounds.max().getX()).putInt(bounds.max().getY()).putInt(bounds.max().getZ())
                .put((byte) bounds.facing().get3DDataValue());
        return display.getCustomData().set(KEY, buf.array());
    }

    /** The stored bounds, or {@code null} if the display isn't ours or the data is malformed. */
    public static Bounds read(ServerDisplay display) {
        return read(display.getCustomData().get(KEY));
    }

    /** Decodes raw tag bytes (also used on the client, where DD syncs customData). */
    public static Bounds read(byte[] raw) {
        if (raw == null || raw.length != SIZE) return null;
        ByteBuffer buf = ByteBuffer.wrap(raw);
        BlockPos min = new BlockPos(buf.getInt(), buf.getInt(), buf.getInt());
        BlockPos max = new BlockPos(buf.getInt(), buf.getInt(), buf.getInt());
        return new Bounds(min, max, Direction.from3DDataValue(buf.get()));
    }
}
