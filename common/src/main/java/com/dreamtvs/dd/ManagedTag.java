package com.dreamtvs.dd;

import com.dreamdisplays.api.CommandResult;
import com.dreamdisplays.api.server.ServerDisplay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.nio.ByteBuffer;

/**
 * What Dream TVs stores in a display's {@code customData}. DD doesn't expose a display's corners or facing,
 * so we keep our own copy: 6 ints (min x/y/z, max x/y/z), one facing byte (3D data value), then the time the
 * screen was first formed (epoch millis, used to break merge ties: the oldest screen wins).
 *
 * <p>The first builds wrote the same thing without the timestamp; those still read, as "formed at time 0".
 */
public final class ManagedTag {
    public static final String KEY = "dreamtvs:screen";
    private static final int LEGACY_SIZE = 6 * Integer.BYTES + 1;
    private static final int SIZE = LEGACY_SIZE + Long.BYTES;

    private ManagedTag() {}

    public record Bounds(BlockPos min, BlockPos max, Direction facing, long createdAt) {
        public boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }

        public Bounds withBox(BlockPos newMin, BlockPos newMax) {
            return new Bounds(newMin, newMax, facing, createdAt);
        }
    }

    public static boolean isManaged(ServerDisplay display) {
        return display.getCustomData().get(KEY) != null;
    }

    public static CommandResult write(ServerDisplay display, Bounds bounds) {
        ByteBuffer buf = ByteBuffer.allocate(SIZE)
                .putInt(bounds.min().getX()).putInt(bounds.min().getY()).putInt(bounds.min().getZ())
                .putInt(bounds.max().getX()).putInt(bounds.max().getY()).putInt(bounds.max().getZ())
                .put((byte) bounds.facing().get3DDataValue())
                .putLong(bounds.createdAt());
        return display.getCustomData().set(KEY, buf.array());
    }

    /** The stored bounds, or {@code null} if the display isn't ours or the data is malformed. */
    public static Bounds read(ServerDisplay display) {
        return read(display.getCustomData().get(KEY));
    }

    /** Decodes raw tag bytes (also used on the client, where DD syncs customData). */
    public static Bounds read(byte[] raw) {
        if (raw == null || (raw.length != SIZE && raw.length != LEGACY_SIZE)) return null;
        ByteBuffer buf = ByteBuffer.wrap(raw);
        BlockPos min = new BlockPos(buf.getInt(), buf.getInt(), buf.getInt());
        BlockPos max = new BlockPos(buf.getInt(), buf.getInt(), buf.getInt());
        Direction facing = Direction.from3DDataValue(buf.get());
        long createdAt = buf.remaining() >= Long.BYTES ? buf.getLong() : 0L;
        return new Bounds(min, max, facing, createdAt);
    }
}
