package com.articreep.fillinthewall.game;

import com.articreep.fillinthewall.FillInTheWallLite;
import com.articreep.fillinthewall.utils.WorldBoundingBox;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public class PlayingFieldManager implements Listener {
    public static Map<Player, PlayingField> activePlayingFields = new HashMap<>();
    public static Map<WorldBoundingBox, PlayingField> playingFieldLocations = new HashMap<>();
    private static final Map<Player, BukkitTask> removalTasks = new HashMap<>();

    @EventHandler
    public void onPlayerEnterField(PlayerMoveEvent event) {
        // One game per player.
        if (activePlayingFields.containsKey(event.getPlayer())) {
            PlayingField field = activePlayingFields.get(event.getPlayer());
            // If player is outside of bounding box and players are not bound to the field, start a 2-second timer before removing.
            if (!field.getBoundingBox().isinBoundingBox(event.getPlayer().getLocation())) {
                removeTimer(event.getPlayer(), field);
            }
        } else {
            for (WorldBoundingBox box : playingFieldLocations.keySet()) {
                if (box.isinBoundingBox(event.getPlayer().getLocation())) {
                    PlayingField field = playingFieldLocations.get(box);
                    field.addPlayer(event.getPlayer());
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeGame(event.getPlayer(), true);
    }

    @EventHandler
    public void onPlayerChangeGamemode(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SPECTATOR) {
            removeGame(event.getPlayer());
        }
    }

    public void removeTimer(Player player, PlayingField field) {
        if (removalTasks.containsKey(player)) return;

        // Immediately remove game if it's stopped
        if (!field.hasStarted()) {
            removeGame(player);
            return;
        }

        BukkitTask task = new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (!field.getBoundingBox().isinBoundingBox(player.getLocation())) {
                    if (i < 2) player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                    if (i == 2) {
                        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1, 2);
                        removeGame(player);
                        cancel();
                    }

                    i++;
                } else {
                    cancel();
                }
            }

            @Override
            public void cancel() {
                removalTasks.remove(player);
                super.cancel();
            }
        }.runTaskTimer(FillInTheWallLite.getInstance(), 0, 20);

        removalTasks.put(player, task);
    }

    /**
     * Attempts to remove a player from their game and mark their game as incomplete.
     *
     * @param player Player to check
     */
    public static void removeGame(Player player, boolean force) {
        PlayingField field = activePlayingFields.get(player);
        if (field != null) {
            field.removePlayer(player);
        }
    }

    public static void removeGame(Player player) {
        removeGame(player, false);
    }

    public static boolean isInGame(Player player) {
        return activePlayingFields.containsKey(player);
    }

    public static void removeAllGames() {
        for (Player player : activePlayingFields.keySet()) {
            removeGame(player);
        }
        activePlayingFields.clear();
    }

    public static void parseConfig(FileConfiguration config) {
        // Clear ALL references to any playing fields
        // todo also need to clear all end screens and menus and such
        playingFieldLocations.clear();

        Map<String, Object> map = config.getValues(false);
        for (String key : map.keySet()) {

            // Create a bounding box
            Location refPoint = config.getLocation(key + ".location");
            Vector incomingDirection = BlockFace.valueOf(config.getString(key + ".incoming_direction")).getDirection();
            Vector fieldDirection = BlockFace.valueOf(config.getString(key + ".field_direction")).getDirection();
            int standingDistance = config.getInt(key + ".standing_distance");
            int queueLength = config.getInt(key + ".queue_length");
            int fieldLength = config.getInt(key + ".field_length");
            int fieldHeight = config.getInt(key + ".field_height");
            boolean hideBottomBorder = config.getBoolean(key + ".hide_bottom_border");
            boolean addBackBorder = config.getBoolean(key + ".add_back_border");
            String wallMaterialName = config.getString(key + ".wall_material");
            String playerMaterialName = config.getString(key + ".player_material");
            String borderMaterialName = config.getString(key + ".border_material");
            Material borderMaterial;
            Material wallMaterial;
            Material playerMaterial;

            if (wallMaterialName == null || Material.getMaterial(wallMaterialName) == null) {
                wallMaterial = Material.BLUE_CONCRETE;
            } else {
                wallMaterial = Material.getMaterial(wallMaterialName);
            }

            if (playerMaterialName == null || Material.getMaterial(playerMaterialName) == null) {
                playerMaterial = Material.TINTED_GLASS;
            } else {
                playerMaterial = Material.getMaterial(playerMaterialName);
            }

            if (borderMaterialName == null || Material.getMaterial(borderMaterialName) == null) {
                borderMaterial = Material.GRAY_CONCRETE;
            } else {
                borderMaterial = Material.getMaterial(borderMaterialName);
            }

            WorldBoundingBox box = playingFieldActivationBox(refPoint.clone().subtract(0, 1, 0), incomingDirection, fieldDirection, standingDistance, queueLength, fieldLength, fieldHeight);

            PlayingField field = new PlayingField(
                    refPoint, fieldDirection, incomingDirection, standingDistance, box, fieldLength, fieldHeight, wallMaterial, playerMaterial, borderMaterial, hideBottomBorder, addBackBorder);
            playingFieldLocations.put(box, field);
        }
    }

    public static WorldBoundingBox playingFieldActivationBox(Location refPoint,
                                                             Vector incomingDirection,
                                                             Vector fieldDirection,
                                                             int standingDistance,
                                                             int queueLength,
                                                             int fieldLength,
                                                             int fieldHeight) {
        // todo these bounding box coordinates are subject to change
        Location corner1 = refPoint.clone()
                .add(incomingDirection.clone().multiply(standingDistance));
                //.subtract(fieldDirection.clone().multiply(2));
        Location corner2 = refPoint.clone()
                .subtract(incomingDirection.clone().multiply(queueLength))
                .add(fieldDirection.clone().multiply(fieldLength))
                .add(new Vector(0, fieldHeight, 0));

        WorldBoundingBox box = new WorldBoundingBox(corner1, corner2);
        box.getBoundingBox().expand(fieldDirection, 2);
        box.getBoundingBox().expand(new Vector(0, fieldHeight, 0), 2);
        box.getBoundingBox().expand(fieldDirection.clone().multiply(-1), 2);

        return box;

    }
}
