package com.tsukieru.lagtop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class LagTopCommand implements CommandExecutor, TabCompleter {
    private final LagTopPlugin plugin;

    public LagTopCommand(LagTopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lagtop.use")) {
            sender.sendMessage(Component.text("你沒有權限使用這個指令。", NamedTextColor.RED));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("lagtop.admin")) {
                sender.sendMessage(Component.text("你沒有權限重載 LagTop。", NamedTextColor.RED));
                return true;
            }
            plugin.reloadPluginConfig();
            sender.sendMessage(Component.text("LagTop 設定已重新載入。", NamedTextColor.GREEN));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("scan")) {
            if (!sender.hasPermission("lagtop.admin")) {
                sender.sendMessage(Component.text("你沒有權限執行掃描。", NamedTextColor.RED));
                return true;
            }
            plugin.triggerFullKnownScan();
            sender.sendMessage(Component.text("已排程目前已知載入區塊的掃描。", NamedTextColor.GREEN));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("gui")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("GUI 只能由玩家開啟。", NamedTextColor.RED));
                return true;
            }
            plugin.openMainMenu(player);
            return true;
        }

        if (sender instanceof Player player) {
            plugin.openMainMenu(player);
        } else {
            plugin.sendReport(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("lagtop.admin")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("gui", "scan", "reload").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        if (args.length == 1 && sender.hasPermission("lagtop.use")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("gui").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
