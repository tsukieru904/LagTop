package com.tsukieru.lagtop;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.block.Hopper;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public final class LagTopPlugin extends JavaPlugin implements Listener {
    private LagProfiler profiler;
    private CpuMonitor cpuMonitor;
    private int chunksPerScan;
    private long scanPeriodTicks;
    private long initialScanDelayTicks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        profiler = new LagProfiler(this);
        cpuMonitor = new CpuMonitor();
        reloadPluginConfig();

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new LagTopInventoryListener(), this);
        LagTopCommand command = new LagTopCommand(this);
        var registration = getCommand("lagtop");
        if (registration != null) {
            registration.setExecutor(command);
            registration.setTabCompleter(command);
        }

        bootstrapLoadedChunks();
        startCoordinator();
        getLogger().info("LagTop enabled. Paper/Folia-compatible chunk activity profiler started.");
    }

    @Override
    public void onDisable() {
        if (Bukkit.getGlobalRegionScheduler() != null) {
            Bukkit.getGlobalRegionScheduler().cancelTasks(this);
        }
    }

    public void reloadPluginConfig() {
        reloadConfig();
        chunksPerScan = Math.max(1, getConfig().getInt("chunks-per-scan", 8));
        scanPeriodTicks = Math.max(20L, getConfig().getLong("scan-period-seconds", 15L) * 20L);
        initialScanDelayTicks = Math.max(1L, getConfig().getLong("initial-scan-delay-ticks", 20L));

        ConfigurationSection weights = getConfig().getConfigurationSection("weights");
        double redstoneEvents = weights == null ? 1.0 : weights.getDouble("redstone-events", 1.0);
        double pistonEvents = weights == null ? 6.0 : weights.getDouble("piston-events", 6.0);
        double hopperMoves = weights == null ? 3.0 : weights.getDouble("hopper-moves", 3.0);
        double livingEntities = weights == null ? 0.50 : weights.getDouble("living-entities", 0.50);
        double passiveEntities = weights == null ? 0.02 : weights.getDouble("passive-entities", 0.02);
        double redstoneBlocks = weights == null ? 0.03 : weights.getDouble("redstone-blocks", 0.03);
        double pistons = weights == null ? 0.50 : weights.getDouble("pistons", 0.50);
        double hoppers = weights == null ? 0.35 : weights.getDouble("hoppers", 0.35);
        double observers = weights == null ? 0.35 : weights.getDouble("observers", 0.35);
        long retention = Math.max(30L, getConfig().getLong("retention-seconds", 120L));
        double activityDecay = getConfig().getDouble("activity-decay", 0.5);
        int redstoneSampleRate = Math.max(1, getConfig().getInt("weights.redstone-sample-rate", 4));

        HashSet<Material> redstoneMaterials = new HashSet<>();
        for (String name : getConfig().getStringList("redstone-materials")) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                getLogger().warning("Unknown redstone material in config: " + name);
                continue;
            }
            redstoneMaterials.add(material);
        }

        profiler.configure(redstoneEvents, pistonEvents, hopperMoves, livingEntities, passiveEntities,
                redstoneBlocks, pistons, hoppers, observers, retention, activityDecay,
                redstoneSampleRate, redstoneMaterials);
    }

    public void triggerFullKnownScan() {
        profiler.requestScanFromStart();
    }

    public void openMainMenu(org.bukkit.entity.Player player) {
        LagTopMenu.openMain(player, this);
    }

    public LagProfiler getProfiler() {
        return profiler;
    }

    public CpuMonitor getCpuMonitor() {
        return cpuMonitor;
    }

    public String getWorldName(ChunkKey key) {
        World world = Bukkit.getWorld(key.worldId());
        return world == null ? key.worldId().toString() : world.getName();
    }

    public void sendReport(CommandSender sender) {
        double processCpu = cpuMonitor.getProcessCpuPercent();
        double systemCpu = cpuMonitor.getSystemCpuPercent();

        sender.sendMessage(Component.text("━━━━━━━━━━━━ LagTop ━━━━━━━━━━━━", NamedTextColor.AQUA, TextDecoration.BOLD));
        if (processCpu >= 0) {
            sender.sendMessage(Component.text("CPU（伺服器程序）: ", NamedTextColor.GRAY)
                    .append(Component.text(String.format(Locale.ROOT, "%.1f%%", processCpu), NamedTextColor.YELLOW))
                    .append(Component.text("  系統: ", NamedTextColor.GRAY))
                    .append(Component.text(String.format(Locale.ROOT, "%.1f%%", systemCpu), NamedTextColor.YELLOW)));
        } else {
            sender.sendMessage(Component.text("CPU: 無法取得", NamedTextColor.RED));
        }
        sender.sendMessage(Component.text("載入中 Chunk: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(profiler.loadedChunkCount()), NamedTextColor.WHITE))
                .append(Component.text("  已完成掃描: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(profiler.scanCount()), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Lag Score 為活動量估算，不是 per-chunk CPU 時間。", NamedTextColor.DARK_GRAY));

        List<LagSnapshot> top = profiler.top(5);
        if (top.isEmpty()) {
            sender.sendMessage(Component.text("尚未取得足夠的區塊採樣資料。請稍等一段時間。", NamedTextColor.YELLOW));
            return;
        }

        for (int i = 0; i < top.size(); i++) {
            LagSnapshot s = top.get(i);
            World world = Bukkit.getWorld(s.key().worldId());
            String worldName = world == null ? s.key().worldId().toString() : world.getName();
            Component header = Component.text("#" + (i + 1) + " ", NamedTextColor.AQUA, TextDecoration.BOLD)
                    .append(Component.text(worldName, NamedTextColor.WHITE))
                    .append(Component.text(" [" + s.key().x() + ", " + s.key().z() + "] ", NamedTextColor.GRAY))
                    .append(Component.text(String.format(Locale.ROOT, "Score %.1f", s.score()), NamedTextColor.RED));
            sender.sendMessage(header);
            sender.sendMessage(Component.text("  生物 ", NamedTextColor.GRAY).append(Component.text(String.valueOf(s.livingEntities()), NamedTextColor.WHITE))
                    .append(Component.text(" | 掉落物 ", NamedTextColor.GRAY)).append(Component.text(String.valueOf(s.passiveEntities()), NamedTextColor.WHITE))
                    .append(Component.text(" | 紅石 ", NamedTextColor.GRAY)).append(Component.text(String.valueOf(s.redstoneBlocks()), NamedTextColor.WHITE))
                    .append(Component.text(" | 活塞 ", NamedTextColor.GRAY)).append(Component.text(String.valueOf(s.pistons()), NamedTextColor.WHITE))
                    .append(Component.text(" | 漏斗 ", NamedTextColor.GRAY)).append(Component.text(String.valueOf(s.hoppers()), NamedTextColor.WHITE))
                    .append(Component.text(" | 偵測器 ", NamedTextColor.GRAY)).append(Component.text(String.valueOf(s.observers()), NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("  近期事件：紅石 " + s.redstoneEvents()
                    + " / 活塞 " + s.pistonEvents() + " / 漏斗搬運 " + s.hopperMoves(), NamedTextColor.DARK_GRAY));
        }
    }

    private void startCoordinator() {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> profiler.scheduleRoundRobinScan(chunksPerScan),
                scanPeriodTicks, scanPeriodTicks);
    }

    private void bootstrapLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                profiler.registerLoaded(chunk);
                profiler.scheduleInitialScan(chunk, initialScanDelayTicks);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        profiler.registerLoaded(event.getChunk());
        profiler.scheduleInitialScan(event.getChunk(), initialScanDelayTicks);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        profiler.registerUnloaded(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent event) {
        profiler.addRedstoneEvent(event.getBlock().getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        profiler.addPistonEvent(event.getBlock().getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        profiler.addPistonEvent(event.getBlock().getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        var holder = event.getInitiator().getHolder();
        var location = holder instanceof Hopper hopper
                ? hopper.getLocation()
                : holder instanceof HopperMinecart minecart ? minecart.getLocation() : null;
        if (location == null || location.getWorld() == null) {
            return;
        }
        Chunk chunk = location.getChunk();
        profiler.addHopperMove(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

}
