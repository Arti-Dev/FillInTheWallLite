package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.FillInTheWallLite;
import com.articreep.fillinthewall.game.Wall;
import com.articreep.fillinthewall.game.WallBundle;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.javatuples.Pair;

import java.util.*;

// Just the O piece for now.
public class Multiplace extends ModifierEvent implements Listener {
    Map<Player, Set<BlockDisplay>> blockDisplays = new HashMap<>();
    WallBundle priorityWallBundle;

    public Multiplace() {
        super();
    }

    @Override
    public void activate() {
        super.activate();
        Bukkit.getPluginManager().registerEvents(this, FillInTheWallLite.getInstance());
        field.sendTitleToPlayers(miniMessage.deserialize("<gold>Multiplace!"),
                Component.text("Your blocks are 2x2 now!"), 0, 40, 10);
        if (priorityWallBundle == null) {
            priorityWallBundle = generatePriorityWallBundle(field.getLength(), field.getHeight());
        }
        priorityWallBundle.getWalls().forEach(field.getQueue()::addPriorityWall);
    }

    @EventHandler (priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        if (field.getPlayers().contains(event.getPlayer())) {
            Set<Pair<Integer, Integer>> blockPlacements = calculateBlockPlacements(event.getBlock());

            for (Pair<Integer, Integer> coords : blockPlacements) {
                field.coordinatesToBlock(coords).setType(event.getBlock().getType());
            }
        }
    }

    private Set<Pair<Integer, Integer>> calculateBlockPlacements(Block pivot) {
        Set<Pair<Integer, Integer>> blocksToPlace = new HashSet<>();
        if (!field.isInField(pivot.getLocation())) return blocksToPlace;

        Pair<Integer, Integer> pivotCoords = field.blockToCoordinates(pivot);
        blocksToPlace.add(pivotCoords);
        blocksToPlace.add(Pair.with(pivotCoords.getValue0() + 1, pivotCoords.getValue1()));
        blocksToPlace.add(Pair.with(pivotCoords.getValue0(), pivotCoords.getValue1() + 1));
        blocksToPlace.add(Pair.with(pivotCoords.getValue0() + 1, pivotCoords.getValue1() + 1));

        if (isNotOccupiedByOtherBlocks(blocksToPlace, pivot)) return blocksToPlace;

        int[] x = {-1, 0, -1};
        int[] y = {0, -1, -1};

        // Tetris Super Rotation System style
        for (int i = 0; i < 3; i++) {
            // Deep-copy set
            Set<Pair<Integer, Integer>> shiftedBlocksToPlace = new HashSet<>();
            for (Pair<Integer, Integer> coords : blocksToPlace) {
                shiftedBlocksToPlace.add(Pair.with(coords.getValue0() + x[i], coords.getValue1() + y[i]));
            }
            if (isNotOccupiedByOtherBlocks(shiftedBlocksToPlace, pivot)) return shiftedBlocksToPlace;
        }

        // If none work, simply return the pivot point
        blocksToPlace.clear();
        blocksToPlace.add(pivotCoords);
        return blocksToPlace;

    }

    private boolean isNotOccupiedByOtherBlocks(Set<Pair<Integer, Integer>> blockSet, Block pivot) {
        for (Pair<Integer, Integer> coords : blockSet) {
            Block block = field.coordinatesToBlock(coords);
            if (field.coordinatesToBlock(coords).getType() != Material.AIR && !block.equals(pivot)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void end() {
        super.end();
        for (Set<BlockDisplay> displays : blockDisplays.values()) {
            displays.forEach(BlockDisplay::remove);
        }
        blockDisplays.clear();

        HandlerList.unregisterAll(this);
        field.sendTitleToPlayers(Component.empty(), Component.text("Block placements are back to normal!"), 0, 20, 10);
        field.getQueue().clearPriorityHiddenWalls();
    }

    public Multiplace copy() {
        Multiplace copy = new Multiplace();
        copy.priorityWallBundle = priorityWallBundle;
        return copy;
    }

    @Override
    public void playActivateSound() {
        field.playSoundToPlayers(Sound.ITEM_MACE_SMASH_GROUND, 1);
    }

    @Override
    public void playDeactivateSound() {
        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (i >= 4) {
                    cancel();
                    return;
                }
                field.playSoundToPlayers(Sound.BLOCK_HEAVY_CORE_PLACE, 1);
                i++;
            }
        }.runTaskTimer(FillInTheWallLite.getInstance(), 0, 2);
    }

    public WallBundle generatePriorityWallBundle(int length, int height) {
        WallBundle bundle = new WallBundle();
        Random random = new Random();
        int wallsToGenerate = 3;
        if (doublePriorityWalls) wallsToGenerate *= 2;
        // A simpler algorithm without wall kicking
        for (int i = 0; i < wallsToGenerate; i++) {
            // Choose a random hole
            // Expand it to a 2x2 hole
            // 50/50 chance to remove one of the holes at random
            // Insert into wall

            Wall wall = new Wall(length, height);
            for (int j = 0; j < 3; j++) {
                ArrayList<Pair<Integer, Integer>> holes = new ArrayList<>();
                Pair<Integer, Integer> randomCoords = Pair.with(random.nextInt(length - 2), random.nextInt(height - 2));
                holes.add(randomCoords);
                holes.add(Pair.with(randomCoords.getValue0() + 1, randomCoords.getValue1()));
                holes.add(Pair.with(randomCoords.getValue0(), randomCoords.getValue1() + 1));
                holes.add(Pair.with(randomCoords.getValue0() + 1, randomCoords.getValue1() + 1));

                if (random.nextBoolean()) {
                    holes.remove((int) (Math.random() * holes.size()));
                }

                wall.insertHoles(holes);
            }
            bundle.addWall(wall);
        }
        return bundle;
    }

    // todo this needs better docs since you have to remember to run this
    @Override
    public void additionalInit(int length, int height) {
        priorityWallBundle = generatePriorityWallBundle(length, height);
    }
}
