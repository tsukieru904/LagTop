package com.tsukieru.lagtop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.entity.Player;

import java.util.List;

public final class LagTopInventoryListener implements Listener {
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof LagTopMenu menu)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (menu.type() == LagTopMenu.Type.MAIN) {
            if (rawSlot == 44) {
                player.closeInventory();
                return;
            }
            if (rawSlot == 40) {
                menu.plugin().triggerFullKnownScan();
                player.sendMessage(Component.text("已重新排程區塊採樣。", NamedTextColor.GREEN));
                LagTopMenu.openMain(player, menu.plugin());
                return;
            }
            if (rawSlot == 10 || rawSlot == 12 || rawSlot == 14 || rawSlot == 16 || rawSlot == 20) {
                int index = switch (rawSlot) {
                    case 10 -> 0;
                    case 12 -> 1;
                    case 14 -> 2;
                    case 16 -> 3;
                    case 20 -> 4;
                    default -> -1;
                };
                List<LagSnapshot> snapshots = menu.snapshots();
                if (index >= 0 && index < snapshots.size()) {
                    LagTopMenu.openDetails(player, menu.plugin(), snapshots.get(index));
                }
            }
            return;
        }

        if (rawSlot == 35) {
            LagTopMenu.openMain(player, menu.plugin());
        } else if (rawSlot == 32) {
            menu.plugin().getProfiler().find(menu.selected().key())
                    .ifPresentOrElse(snapshot -> LagTopMenu.openDetails(player, menu.plugin(), snapshot),
                            () -> LagTopMenu.openMain(player, menu.plugin()));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof LagTopMenu) {
            event.setCancelled(true);
        }
    }
}
