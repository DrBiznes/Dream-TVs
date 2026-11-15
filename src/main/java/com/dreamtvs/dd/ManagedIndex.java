package com.dreamtvs.dd;

import com.dreamtvs.McCompat;
import com.dreamdisplays.api.server.DreamDisplaysServer;
import com.dreamdisplays.api.server.ServerDisplay;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side index of the displays Dream TVs manages (plan §5.3): id → world + stored bounds, plus a lookup by
 * chunk so block hooks and chunk loads don't have to walk every Dream Displays screen and decode its
 * {@code customData}. Rebuilt from {@code customData} the first time it's used after a server start, then kept
 * current by {@link DreamDisplaysBridge} (our own changes) and {@link DreamTvsAddon} (everyone else's).
 * Server thread only.
 */
public final class ManagedIndex {
    /** One managed screen as we last tagged it. */
    public record Entry(UUID id, String world, ManagedTag.Bounds bounds) {}

    private static final Map<UUID, Entry> byId = new LinkedHashMap<>();
    /** world → chunk (packed, {@link McCompat#chunkKey}) → ids whose box reaches into that chunk. */
    private static final Map<String, Map<Long, Set<UUID>>> byChunk = new HashMap<>();
    private static boolean built;

    private ManagedIndex() {}

    /** Forget everything; the next lookup rebuilds from Dream Displays. Call on server start/stop and addon enable. */
    public static void invalidate() {
        byId.clear();
        byChunk.clear();
        built = false;
    }

    /** True once the index has been built for the running server. */
    public static boolean isBuilt() {
        return built;
    }

    /**
     * Builds the index if it isn't yet (needs the Dream Displays API to be available).
     * @return true if this call did the (re)build
     */
    public static boolean ensureBuilt() {
        if (built || !DreamDisplaysServer.isAvailable()) return false;
        byId.clear();
        byChunk.clear();
        for (ServerDisplay display : DreamDisplaysServer.getDisplays()) {
            if (!display.isAlive()) continue;
            ManagedTag.Bounds bounds = ManagedTag.read(display);
            if (bounds != null) add(new Entry(display.getId(), display.getWorld(), bounds));
        }
        built = true;
        return true;
    }

    public static void put(UUID id, String world, ManagedTag.Bounds bounds) {
        ensureBuilt();
        remove(id);
        add(new Entry(id, world, bounds));
    }

    public static Entry remove(UUID id) {
        Entry old = byId.remove(id);
        if (old != null) {
            Map<Long, Set<UUID>> chunks = byChunk.get(old.world());
            if (chunks != null) {
                for (long chunk : chunks(old.bounds())) {
                    Set<UUID> ids = chunks.get(chunk);
                    if (ids != null && ids.remove(id) && ids.isEmpty()) chunks.remove(chunk);
                }
            }
        }
        return old;
    }

    public static Entry get(UUID id) {
        ensureBuilt();
        return byId.get(id);
    }

    public static boolean contains(UUID id) {
        ensureBuilt();
        return byId.containsKey(id);
    }

    public static Collection<Entry> all() {
        ensureBuilt();
        return List.copyOf(byId.values());
    }

    /** Managed screens in {@code world} whose box reaches into chunk {@code chunk}. */
    public static List<Entry> inChunk(String world, ChunkPos chunk) {
        ensureBuilt();
        Map<Long, Set<UUID>> chunks = byChunk.get(world);
        if (chunks == null) return List.of();
        Set<UUID> ids = chunks.get(McCompat.chunkKey(chunk));
        if (ids == null) return List.of();
        List<Entry> out = new ArrayList<>(ids.size());
        for (UUID id : ids) out.add(byId.get(id));
        return out;
    }

    /** Managed screens in {@code world} whose box contains {@code pos}. */
    public static List<Entry> at(String world, BlockPos pos) {
        List<Entry> out = new ArrayList<>(1);
        for (Entry e : inChunk(world, McCompat.chunkAt(pos))) if (e.bounds().contains(pos)) out.add(e);
        return out;
    }

    /** Managed screens in {@code world}. */
    public static List<Entry> inWorld(String world) {
        ensureBuilt();
        List<Entry> out = new ArrayList<>();
        for (Entry e : byId.values()) if (e.world().equals(world)) out.add(e);
        return out;
    }

    private static void add(Entry entry) {
        byId.put(entry.id(), entry);
        Map<Long, Set<UUID>> chunks = byChunk.computeIfAbsent(entry.world(), k -> new HashMap<>());
        for (long chunk : chunks(entry.bounds())) chunks.computeIfAbsent(chunk, k -> new HashSet<>()).add(entry.id());
    }

    private static List<Long> chunks(ManagedTag.Bounds bounds) {
        int x0 = bounds.min().getX() >> 4, x1 = bounds.max().getX() >> 4;
        int z0 = bounds.min().getZ() >> 4, z1 = bounds.max().getZ() >> 4;
        List<Long> out = new ArrayList<>((x1 - x0 + 1) * (z1 - z0 + 1));
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) out.add(McCompat.chunkKey(x, z));
        return out;
    }
}
