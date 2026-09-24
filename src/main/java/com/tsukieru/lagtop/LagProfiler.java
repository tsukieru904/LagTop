package com.tsukieru.lagtop;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class LagProfiler {
    private final Plugin plugin;
    private final ConcurrentMap<ChunkKey, ChunkStats> stats = new ConcurrentHashMap<>();
    private final ConcurrentMap<ChunkKey, Boolean> loaded = new ConcurrentHashMap<>();
    private final AtomicInteger cursor = new AtomicInteger();
    private final AtomicLong scans = new AtomicLong();

    private volatile double redstoneWeight;
    private volatile double pistonEventWeight;
    private volatile double hopperMoveWeight;
    private volatile double entityWeight;
    private volatile double redstoneBlockWeight;
    private volatile double pistonBlockWeight;
    private volatile double hopperBlockWeight;
    private volatile double observerWeight;
    private volatile long retentionNanos;
    private volatile double activityDecay = 0.5;
    private volatile HashSet<Material> redstoneMaterials = new HashSet<>();

    public LagProfiler(Plugin plugin) {
        this.plugin = plugin;
    }

    public void configure(double redstoneWeight,
                          double pistonEventWeight,
                          double hopperMoveWeight,
                          double entityWeight,
                          double redstoneBlockWeight,
                          double pistonBlockWeight,
                          double hopperBlockWeight,
                          double observerWeight,
                          long retentionSeconds,
                          double activityDecay,
                          HashSet<Material> redstoneMaterials) {
        this.redstoneWeight = redstoneWeight;
        this.pistonEventWeight = pistonEventWeight;
        this.hopperMoveWeight = hopperMoveWeight;
        this.entityWeight = entityWeight;
        this.redstoneBlockWeight = redstoneBlockWeight;
        this.pistonBlockWeight = pistonBlockWeight;
        this.hopperBlockWeight = hopperBlockWeight;
        this.observerWeight = observerWeight;
        this.retentionNanos = retentionSeconds * 1_000_000_000L;
        this.activityDecay = Math.max(0.0, Math.min(0.99, activityDecay));
        this.redstoneMaterials = new HashSet<>(redstoneMaterials);
    }

    public void registerLoaded(Chunk chunk) {
        ChunkKey key = key(chunk);
        loaded.put(key, Boolean.TRUE);
        stats.computeIfAbsent(key, ignored -> new ChunkStats());
    }

    public void registerUnloaded(Chunk chunk) {
        ChunkKey key = key(chunk);
        loaded.remove(key);
        stats.remove(key);
    }

    public void addRedstoneEvent(Chunk chunk) {
        statsFor(chunk).addRedstoneEvent();
    }

    public void addPistonEvent(Chunk chunk) {
        statsFor(chunk).addPistonEvent();
    }

    public void addHopperMove(World world, int chunkX, int chunkZ) {
        ChunkKey key = new ChunkKey(world.getUID(), chunkX, chunkZ);
        stats.computeIfAbsent(key, ignored -> new ChunkStats()).addHopperMove();
    }

    public void scheduleInitialScan(Chunk chunk, long delayTicks) {
        Bukkit.getRegionScheduler().runDelayed(plugin, chunk.getWorld(), chunk.getX(), chunk.getZ(), task -> scanChunk(chunk), delayTicks);
    }

    public void scheduleRoundRobinScan(int amount) {
        List<ChunkKey> keys = new ArrayList<>(loaded.keySet());
        if (keys.isEmpty() || amount <= 0) {
            rotateActivity();
            cleanupExpired();
            return;
        }

        rotateActivity();
        int start = Math.floorMod(cursor.getAndAdd(amount), keys.size());
        int count = Math.min(amount, keys.size());
        for (int i = 0; i < count; i++) {
            ChunkKey key = keys.get((start + i) % keys.size());
            scheduleOnChunkKey(key);
        }
        cleanupExpired();
    }

    public java.util.Optional<LagSnapshot> find(ChunkKey key) {
        ChunkStats stat = stats.get(key);
        if (stat == null || !loaded.containsKey(key)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(snapshot(key, stat, System.nanoTime()));
    }

    public List<LagSnapshot> top(int limit) {
        long now = System.nanoTime();
        return stats.entrySet().stream()
                .filter(entry -> loaded.containsKey(entry.getKey()))
                .map(entry -> snapshot(entry.getKey(), entry.getValue(), now))
                .filter(snapshot -> snapshot.ageMillis() <= retentionNanos / 1_000_000L || snapshot.score() > 0.0)
                .sorted(Comparator.comparingDouble(LagSnapshot::score).reversed())
                .limit(limit)
                .toList();
    }

    public int loadedChunkCount() {
        return loaded.size();
    }

    public long scanCount() {
        return scans.get();
    }

    public void requestScanFromStart() {
        cursor.set(0);
        scheduleRoundRobinScan(1);
    }

    private void rotateActivity() {
        for (ChunkStats stat : stats.values()) {
            stat.rotateActivity(redstoneWeight, pistonEventWeight, hopperMoveWeight, activityDecay);
        }
    }

    private ChunkStats statsFor(Chunk chunk) {
        ChunkKey key = key(chunk);
        loaded.putIfAbsent(key, Boolean.TRUE);
        return stats.computeIfAbsent(key, ignored -> new ChunkStats());
    }

    private void scanChunkKey(ChunkKey key) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null || !world.isChunkLoaded(key.x(), key.z())) {
            return;
        }
        scanChunk(world.getChunkAt(key.x(), key.z()));
    }

    private void scanChunk(Chunk chunk) {
        ChunkKey key = key(chunk);
        if (!loaded.containsKey(key)) {
            return;
        }
        try {
            World world = chunk.getWorld();
            int entities = chunk.isEntitiesLoaded() ? chunk.getEntities().length : 0;
            var snapshot = chunk.getChunkSnapshot(false, false, false, false);

            int redstone = 0;
            int pistons = 0;
            int hoppers = 0;
            int observers = 0;

            int minSection = world.getMinHeight() >> 4;
            int maxSection = (world.getMaxHeight() - 1) >> 4;
            for (int section = minSection; section <= maxSection; section++) {
                if (snapshot.isSectionEmpty(section)) {
                    continue;
                }
                int minY = Math.max(world.getMinHeight(), section << 4);
                int maxY = Math.min(world.getMaxHeight(), minY + 16);
                for (int x = 0; x < 16; x++) {
                    for (int y = minY; y < maxY; y++) {
                        for (int z = 0; z < 16; z++) {
                            Material material = snapshot.getBlockType(x, y, z);
                            if (redstoneMaterials.contains(material)) {
                                redstone++;
                            } else if (material == Material.PISTON || material == Material.STICKY_PISTON) {
                                pistons++;
                            } else if (material == Material.HOPPER) {
                                hoppers++;
                            } else if (material == Material.OBSERVER) {
                                observers++;
                            }
                        }
                    }
                }
            }

            ChunkStats stat = stats.computeIfAbsent(key, ignored -> new ChunkStats());
            stat.updateScan(entities, redstone, pistons, hoppers, observers);
            scans.incrementAndGet();
        } catch (Throwable throwable) {
            plugin.getLogger().fine("Could not scan " + key + ": " + throwable.getClass().getSimpleName());
        }
    }

    private LagSnapshot snapshot(ChunkKey key, ChunkStats stat, long now) {
        long redstoneEvents = stat.recentRedstoneEvents();
        long pistonEvents = stat.recentPistonEvents();
        long hopperMoves = stat.recentHopperMoves();

        double score = stat.rollingActivityScore()
                + stat.entities() * entityWeight
                + stat.redstoneBlocks() * redstoneBlockWeight
                + stat.pistons() * pistonBlockWeight
                + stat.hoppers() * hopperBlockWeight
                + stat.observers() * observerWeight;

        long ageMillis = stat.lastScanNanos() == 0L
                ? Long.MAX_VALUE
                : Math.max(0L, (now - stat.lastScanNanos()) / 1_000_000L);
        return new LagSnapshot(key, score, redstoneEvents, pistonEvents, hopperMoves,
                stat.entities(), stat.redstoneBlocks(), stat.pistons(), stat.hoppers(), stat.observers(), ageMillis);
    }

    private void cleanupExpired() {
        long cutoff = System.nanoTime() - retentionNanos;
        for (Map.Entry<ChunkKey, ChunkStats> entry : stats.entrySet()) {
            if (!loaded.containsKey(entry.getKey())
                    || (entry.getValue().lastScanNanos() > 0 && entry.getValue().lastScanNanos() < cutoff
                    && entry.getValue().entities() == 0
                    && entry.getValue().redstoneBlocks() == 0
                    && entry.getValue().pistons() == 0
                    && entry.getValue().hoppers() == 0
                    && entry.getValue().observers() == 0)) {
                stats.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private void scheduleOnChunkKey(ChunkKey key) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null) {
            return;
        }
        Bukkit.getRegionScheduler().run(plugin, world, key.x(), key.z(), task -> scanChunkKey(key));
    }

    private static ChunkKey key(Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }
}
