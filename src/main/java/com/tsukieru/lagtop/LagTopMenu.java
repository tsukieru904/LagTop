package com.tsukieru.lagtop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LagTopMenu implements InventoryHolder {
    private static final int MAIN_SIZE = 45;
    private static final int DETAILS_SIZE = 36;

    public enum Type {
        MAIN,
        DETAILS
    }

    private final LagTopPlugin plugin;
    private final Type type;
    private final List<LagSnapshot> snapshots;
    private final LagSnapshot selected;
    private Inventory inventory;

    private LagTopMenu(LagTopPlugin plugin, Type type, List<LagSnapshot> snapshots, LagSnapshot selected) {
        this.plugin = plugin;
        this.type = type;
        this.snapshots = snapshots;
        this.selected = selected;
    }

    public static void openMain(Player player, LagTopPlugin plugin) {
        LagTopMenu menu = new LagTopMenu(plugin, Type.MAIN, plugin.getProfiler().top(5), null);
        menu.buildMain();
        player.openInventory(menu.inventory);
    }

    public static void openDetails(Player player, LagTopPlugin plugin, LagSnapshot snapshot) {
        LagTopMenu menu = new LagTopMenu(plugin, Type.DETAILS, List.of(), snapshot);
        menu.buildDetails();
        player.openInventory(menu.inventory);
    }

    public Type type() {
        return type;
    }

    public List<LagSnapshot> snapshots() {
        return snapshots;
    }

    public LagSnapshot selected() {
        return selected;
    }

    public LagTopPlugin plugin() {
        return plugin;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void buildMain() {
        inventory = Bukkit.createInventory(this, MAIN_SIZE,
                Component.text("LagTop · 最卡區塊", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        fill(inventory);

        double processCpu = plugin.getCpuMonitor().getProcessCpuPercent();
        double systemCpu = plugin.getCpuMonitor().getSystemCpuPercent();

        inventory.setItem(4, item(Material.COMPARATOR,
                "伺服器狀態", NamedTextColor.AQUA,
                line("程序 CPU: " + formatPercent(processCpu)),
                line("系統 CPU: " + formatPercent(systemCpu)),
                line("載入 Chunk: " + plugin.getProfiler().loadedChunkCount()),
                line("已完成掃描: " + plugin.getProfiler().scanCount())));

        int[] slots = {10, 12, 14, 16, 20};
        for (int i = 0; i < snapshots.size() && i < slots.length; i++) {
            LagSnapshot snapshot = snapshots.get(i);
            inventory.setItem(slots[i], createChunkItem(i + 1, snapshot));
        }

        if (snapshots.isEmpty()) {
            inventory.setItem(22, item(Material.BARRIER,
                    "尚無足夠資料", NamedTextColor.RED,
                    line("插件需要先完成區塊採樣。"),
                    line("稍等片刻後點擊「重新掃描」。")));
        }

        inventory.setItem(31, item(Material.CLOCK,
                "掃描說明", NamedTextColor.YELLOW,
                line("Lag Score 是活動量估算值。"),
                line("不是每個 Chunk 的實際 CPU 秒數。")));
        inventory.setItem(40, item(Material.RECOVERY_COMPASS,
                "重新掃描", NamedTextColor.GREEN,
                line("重新排程已載入區塊的低速採樣。"),
                line("不會進行全地圖每 Tick 掃描。")));
        inventory.setItem(44, item(Material.BARRIER,
                "關閉", NamedTextColor.RED));
    }

    private void buildDetails() {
        inventory = Bukkit.createInventory(this, DETAILS_SIZE,
                Component.text("LagTop · Chunk 詳情", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        fill(inventory);

        String worldName = plugin.getWorldName(selected.key());
        inventory.setItem(4, item(Material.COMPASS,
                "區塊位置", NamedTextColor.AQUA,
                line("世界: " + worldName),
                line("Chunk X: " + selected.key().x()),
                line("Chunk Z: " + selected.key().z()),
                line("區塊座標: [" + selected.key().x() + ", " + selected.key().z() + "]")));

        inventory.setItem(9, counterItem(Material.ZOMBIE_HEAD, "生物", NamedTextColor.WHITE, selected.livingEntities()));
        inventory.setItem(10, counterItem(Material.PLAYER_HEAD, "掉落物/經驗球", NamedTextColor.WHITE, selected.passiveEntities()));
        inventory.setItem(11, counterItem(Material.REDSTONE_BLOCK, "紅石元件", NamedTextColor.RED, selected.redstoneBlocks()));
        inventory.setItem(12, counterItem(Material.PISTON, "活塞", NamedTextColor.YELLOW, selected.pistons()));
        inventory.setItem(13, counterItem(Material.HOPPER, "漏斗", NamedTextColor.GRAY, selected.hoppers()));
        inventory.setItem(14, counterItem(Material.OBSERVER, "偵測器", NamedTextColor.LIGHT_PURPLE, selected.observers()));

        inventory.setItem(19, counterItem(Material.REDSTONE, "近期紅石事件", NamedTextColor.RED, selected.redstoneEvents()));
        inventory.setItem(20, counterItem(Material.PISTON, "近期活塞事件", NamedTextColor.YELLOW, selected.pistonEvents()));
        inventory.setItem(21, counterItem(Material.HOPPER, "近期漏斗搬運", NamedTextColor.GRAY, selected.hopperMoves()));

        inventory.setItem(24, item(Material.EXPERIENCE_BOTTLE,
                "Lag Score", NamedTextColor.RED,
                line(String.format(Locale.ROOT, "%.1f", selected.score())),
                line("數值越高代表近期可觀測活動越集中。")));

        inventory.setItem(31, item(Material.CLOCK,
                "採樣狀態", NamedTextColor.YELLOW,
                line(selected.ageMillis() == Long.MAX_VALUE
                        ? "尚未取得採樣時間。"
                        : "距離上次採樣: " + formatAge(selected.ageMillis()))));
        inventory.setItem(32, item(Material.SPYGLASS, "重新整理", NamedTextColor.GREEN));
        inventory.setItem(35, item(Material.ARROW, "返回排行榜", NamedTextColor.AQUA));
    }

    private ItemStack createChunkItem(int rank, LagSnapshot snapshot) {
        Material icon = iconFor(snapshot);
        String worldName = plugin.getWorldName(snapshot.key());
        return item(icon,
                "#" + rank + " · " + worldName,
                rank == 1 ? NamedTextColor.RED : NamedTextColor.AQUA,
                line("Chunk: [" + snapshot.key().x() + ", " + snapshot.key().z() + "]"),
                line(String.format(Locale.ROOT, "Lag Score: %.1f", snapshot.score())),
                line("生物: " + snapshot.livingEntities()),
                line("掉落物: " + snapshot.passiveEntities()),
                line("紅石: " + snapshot.redstoneBlocks()),
                line("活塞: " + snapshot.pistons()),
                line("漏斗: " + snapshot.hoppers()),
                line("偵測器: " + snapshot.observers()),
                line(""),
                line("點擊查看詳細資料"));
    }

    private ItemStack counterItem(Material material, String name, NamedTextColor color, Number value) {
        return item(material, name, color, line("數量: " + value));
    }

    private static Material iconFor(LagSnapshot snapshot) {
        int max = Math.max(snapshot.redstoneBlocks(),
                Math.max(snapshot.pistons(), Math.max(snapshot.hoppers(), snapshot.observers())));
        if (max == snapshot.redstoneBlocks() && max > 0) return Material.REDSTONE_BLOCK;
        if (max == snapshot.pistons() && max > 0) return Material.PISTON;
        if (max == snapshot.hoppers() && max > 0) return Material.HOPPER;
        if (max == snapshot.observers() && max > 0) return Material.OBSERVER;
        return Material.PAPER;
    }

    private static void fill(Inventory inventory) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private static ItemStack item(Material material, String name, NamedTextColor color, Component... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Component.text(name, color, TextDecoration.BOLD));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>(List.of(lore));
            meta.lore(lines);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component line(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private static String formatPercent(double value) {
        return value < 0 ? "N/A" : String.format(Locale.ROOT, "%.1f%%", value);
    }

    private static String formatAge(long millis) {
        if (millis < 1000) return millis + " ms";
        return String.format(Locale.ROOT, "%.1f 秒", millis / 1000.0);
    }
}
