package com.dreamtvs.screen;

import com.dreamtvs.DreamTvs;
import com.dreamtvs.dd.DreamDisplaysBridge;
import com.dreamtvs.dd.ManagedIndex;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Keeps TV walls and their screens in step when something other than a TV block changes:
 * <ul>
 *   <li><b>Deleted on purpose</b> ({@code /display delete}, the DD menu, another addon's {@code delete()}): the TVs
 *       go dark and stay unformed until the wall changes again.</li>
 *   <li><b>Removed silently</b> (no delete request, e.g. Dream Displays' own cleanup if our guard against it isn't
 *       active): the wall is reconciled, so a still-whole rectangle gets a fresh screen.</li>
 *   <li><b>Moved by another mod</b> (dreamdisplays-sable carrying a screen onto a sub-level): we follow it and
 *       re-check both the old and the new place.</li>
 *   <li><b>Orphans</b> (a managed screen whose TVs are gone, e.g. a world edited offline): every managed screen
 *       is re-checked once after the server starts, and again whenever one of its chunks loads.</li>
 * </ul>
 * All of it goes through {@link ScreenQueue}, so the actual work happens at the end of the tick.
 */
public final class ScreenSync {
    private static boolean sweepPending = true;

    private ScreenSync() {}

    /** Server start/stop or the addon (re)enabled: re-check every screen once Dream Displays is ready. */
    public static void reset() {
        sweepPending = true;
    }

    public static void externallyRemoved(ManagedIndex.Entry entry, boolean deliberate) {
        if (deliberate) {
            ScreenQueue.unform(entry.world(), entry.bounds());
        } else {
            ScreenQueue.markBox(entry.world(), entry.bounds());
        }
    }

    public static void externallyMoved(ManagedIndex.Entry from, ManagedIndex.Entry to) {
        ScreenQueue.markBox(from.world(), from.bounds());
        ScreenQueue.markBox(to.world(), to.bounds());
    }

    public static void onChunkLoaded(ServerLevel level, ChunkPos chunk) {
        // Before the first build the startup sweep covers everything.
        if (!ManagedIndex.isBuilt()) return;
        for (ManagedIndex.Entry e : ManagedIndex.inChunk(DreamDisplaysBridge.worldKey(level), chunk)) {
            ScreenQueue.markBox(e.world(), e.bounds());
        }
    }

    /** Runs every tick before the queue flushes. */
    public static void tick() {
        if (!sweepPending || !DreamDisplaysBridge.isReady()) return;
        sweepPending = false;
        ManagedIndex.ensureBuilt();
        int count = 0;
        for (ManagedIndex.Entry e : ManagedIndex.all()) {
            ScreenQueue.markBox(e.world(), e.bounds());
            count++;
        }
        DreamTvs.LOGGER.info("Checking {} TV screen(s) against their blocks", count);
    }
}
