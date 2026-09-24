package com.tsukieru.lagtop;

import java.util.UUID;

public record ChunkKey(UUID worldId, int x, int z) {
}
