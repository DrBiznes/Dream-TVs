package com.dreamtvs.dd;

import com.dreamdisplays.platform.server.datatypes.display.DisplayData;
import com.dreamdisplays.platform.server.datatypes.display.VanillaDisplayData;
import com.dreamdisplays.platform.server.managers.DisplayManager;
import com.dreamtvs.DreamTvs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The few things we need that Dream Displays' public API doesn't expose: where a display actually is (its box
 * and facing) after someone else moved it. These are DD internals, so every call is guarded and callers must
 * cope with {@code null}. Keep all such access in this class (plan R2).
 */
public final class DdInternals {
    private static boolean broken;

    private DdInternals() {}

    /** A display's live world and box as Dream Displays has it right now. */
    public record LiveBox(String world, BlockPos min, BlockPos max, Direction facing) {}

    public static @Nullable LiveBox liveBox(UUID id) {
        if (broken) return null;
        try {
            DisplayData data = DisplayManager.getDisplayData(id);
            if (!(data instanceof VanillaDisplayData vanilla)) return null;
            return new LiveBox(vanilla.getWorldKey(),
                    new BlockPos(vanilla.getMinX(), vanilla.getMinY(), vanilla.getMinZ()),
                    new BlockPos(vanilla.getMaxX(), vanilla.getMaxY(), vanilla.getMaxZ()),
                    vanilla.getFacing());
        } catch (LinkageError | RuntimeException e) {
            broken = true;
            DreamTvs.LOGGER.warn("Can't read Dream Displays' display data (internal API changed?); "
                    + "screens moved by other mods won't be followed", e);
            return null;
        }
    }
}
