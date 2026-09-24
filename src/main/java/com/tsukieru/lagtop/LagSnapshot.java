package com.tsukieru.lagtop;

public record LagSnapshot(
        ChunkKey key,
        double score,
        long redstoneEvents,
        long pistonEvents,
        long hopperMoves,
        int livingEntities,
        int passiveEntities,
        int redstoneBlocks,
        int pistons,
        int hoppers,
        int observers,
        long ageMillis
) {
    public int totalEntities() {
        return livingEntities + passiveEntities;
    }
}
