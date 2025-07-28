package com.articreep.fillinthewall;

import com.articreep.fillinthewall.game.PlayingField;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class GlobalListeners implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked().isOp()) return;
        ItemStack cursorItem = event.getCursor();

        checkAndDeleteItem(event, cursorItem);
    }

    private void checkAndDeleteItem(InventoryClickEvent event, ItemStack clickedItem) {
        if (clickedItem != null && clickedItem.getType() != Material.AIR) {
            if (!clickedItem.getItemMeta().getPersistentDataContainer().has(PlayingField.gameKey, PersistentDataType.BOOLEAN)) {
                event.setCancelled(true);
                // delete the item
//                clickedItem.setType(Material.AIR);
                clickedItem.setAmount(0);
                event.getWhoClicked().sendMessage(MiniMessage.miniMessage().deserialize("<gray>Can't use this item!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerSwapItem(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item == null) return;
        if (item.getType() == Material.AIR) return;
        if (!item.getItemMeta().getPersistentDataContainer().has(PlayingField.gameKey, PersistentDataType.BOOLEAN)) {
            player.getInventory().setItem(event.getNewSlot(), new ItemStack(Material.AIR));
            player.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Can't use this item!"));
        }
    }
}
