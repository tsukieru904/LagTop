package com.tsukieru.lagtop;

import java.util.concurrent.atomic.AtomicLong;

public final class ChunkStats {
    private final AtomicLong redstoneEvents = new AtomicLong();
    private final AtomicLong pistonEvents = new AtomicLong();
    private final AtomicLong hopperMoves = new AtomicLong();

    private volatile double rollingActivityScore;
    private volatile long recentRedstoneEvents;
    private volatile long recentPistonEvents;
    private volatile long recentHopperMoves;

    private volatile int entities;
    private volatile int redstoneBlocks;
    private volatile int pistons;
    private volatile int hoppers;
    private volatile int observers;
    private volatile long lastScanNanos;

    public void addRedstoneEvent() {
        redstoneEvents.incrementAndGet();
    }

    public void addPistonEvent() {
        pistonEvents.incrementAndGet();
    }

    public void addHopperMove() {
        hopperMoves.incrementAndGet();
    }

    public void rotateActivity(double redstoneWeight, double pistonWeight, double hopperWeight, double decay) {
        long redstone = redstoneEvents.getAndSet(0L);
        long pistons = pistonEvents.getAndSet(0L);
        long hoppers = hopperMoves.getAndSet(0L);

        recentRedstoneEvents = redstone;
        recentPistonEvents = pistons;
        recentHopperMoves = hoppers;

        double intervalScore = redstone * redstoneWeight
                + pistons * pistonWeight
                + hoppers * hopperWeight;
        rollingActivityScore = rollingActivityScore * decay + intervalScore;
    }

    public double rollingActivityScore() {
        return rollingActivityScore;
    }

    public long recentRedstoneEvents() {
        return recentRedstoneEvents;
    }

    public long recentPistonEvents() {
        return recentPistonEvents;
    }

    public long recentHopperMoves() {
        return recentHopperMoves;
    }

    public void updateScan(int entities, int redstoneBlocks, int pistons, int hoppers, int observers) {
        this.entities = entities;
        this.redstoneBlocks = redstoneBlocks;
        this.pistons = pistons;
        this.hoppers = hoppers;
        this.observers = observers;
        this.lastScanNanos = System.nanoTime();
    }

    public int entities() {
        return entities;
    }

    public int redstoneBlocks() {
        return redstoneBlocks;
    }

    public int pistons() {
        return pistons;
    }

    public int hoppers() {
        return hoppers;
    }

    public int observers() {
        return observers;
    }

    public long lastScanNanos() {
        return lastScanNanos;
    }
}
