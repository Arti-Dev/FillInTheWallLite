package com.articreep.fillinthewall;

import com.articreep.fillinthewall.game.PlayingField;
import com.articreep.fillinthewall.game.PlayingFieldManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class GlobalListeners implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (PlayingFieldManager.isInGame(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (PlayingFieldManager.isInGame(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerSpawnEgg(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!PlayingFieldManager.isInGame(player)) return;
        ItemStack item = event.getItem();
        if (item == null) return;
        if (item.getType().toString().contains("SPAWN_EGG")) {
            event.setCancelled(true);
        }
        if (item.getType().toString().contains("BUCKET")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!PlayingFieldManager.isInGame(player)) return;
        ItemStack cursorItem = event.getCursor();

        checkAndDeleteItem(event, cursorItem);
    }

    private void checkAndDeleteItem(InventoryClickEvent event, ItemStack clickedItem) {
        if (clickedItem != null && clickedItem.getType() != Material.AIR) {
            if (!clickedItem.getItemMeta().getPersistentDataContainer().has(PlayingField.gameKey, PersistentDataType.BOOLEAN)) {
                event.setCancelled(true);
                clickedItem.setAmount(0);
                event.getWhoClicked().sendMessage(MiniMessage.miniMessage().deserialize("<gray>Can't use this item!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerSwapItem(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!PlayingFieldManager.isInGame(player)) return;
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item == null) return;
        if (item.getType() == Material.AIR) return;
        if (!item.getItemMeta().getPersistentDataContainer().has(PlayingField.gameKey, PersistentDataType.BOOLEAN)) {
            player.getInventory().setItem(event.getNewSlot(), new ItemStack(Material.AIR));
            player.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Can't use this item!"));
        }
    }
}
