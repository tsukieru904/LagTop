package com.tsukieru.lagtop;

public record LagSnapshot(
        ChunkKey key,
        double score,
        long redstoneEvents,
        long pistonEvents,
        long hopperMoves,
        int entities,
        int redstoneBlocks,
        int pistons,
        int hoppers,
        int observers,
        long ageMillis
) {
}
