package com.articreep.fillinthewall.game;

import com.articreep.fillinthewall.FillInTheWallLite;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.multiplayer.WallGenerator;
import com.articreep.fillinthewall.utils.Utils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;
import org.javatuples.Pair;

import java.util.*;

public class WallQueue {

    /**
     * This is in ticks.
     */
    private final int defaultWallActiveTime = 160;
    private final PlayingField field;
    private final int fullLength = 20;
    // todo this number will change if "garbage walls" accumulate in the queue
    private int effectiveLength = 20;

    /**
     * Walls that are spawned but are invisible.
     */
    private final List<Wall> hiddenWalls = new LinkedList<>();
    private final List<Wall> priorityHiddenWalls = new LinkedList<>();
    private Wall animatingWall = null;
    private final List<Wall> activeWalls = new ArrayList<>();
    private final Deque<Wall> hardenedWalls = new ArrayDeque<>();
    private int pauseTickLoop = 0;
    private boolean allowMultipleWalls = false;
    private int maxSpawnCooldown = 80;
    private int spawnCooldown = 80;

    private Wall frontmostWall = null;

    // Wall generation settings
    private WallGenerator generator;
    boolean hideBottomBorder = false;
    boolean addBackBorder = false;
    private Material wallMaterial = Material.BLUE_CONCRETE;

    public WallQueue(PlayingField field, Material defaultWallMaterial, WallGenerator generator, boolean hideBottomBorder, boolean addBackBorder) {
        setWallMaterial(defaultWallMaterial);
        setHideBottomBorder(hideBottomBorder);
        this.field = field;
        this.generator = generator;
        this.generator.addQueue(this);
        this.addBackBorder = addBackBorder;
    }

    public void addWall(Wall wall) {
        hiddenWalls.add(wall);
        if (wall.getTimeRemaining() == -1) {
            // default wall speed
            wall.setTimeRemaining(defaultWallActiveTime);
        }
    }

    public void addPriorityWall(Wall wall) {
        priorityHiddenWalls.add(wall);
        if (wall.getTimeRemaining() == -1) {
            // default wall speed
            wall.setTimeRemaining(defaultWallActiveTime);
        }
    }

    public void spawnNextWall() {
        if (animatingWall != null) return;
        if (!field.getScorer().isGarbageQueueEmpty()) {
            spawnGarbageWall();
            return;
        }

        // If we cannot spawn a new active wall, end the game
        if (effectiveLength <= 0) {
            field.stop(false, true);
        } else {
            animateNextWall();
        }
    }

    public void spawnGarbageWall() {
        if (field.getScorer().isGarbageQueueEmpty()) return;
        Wall wall = field.getScorer().removeFirstGarbageFromQueue();
        field.playSoundToPlayers(Sound.BLOCK_NETHER_BRICKS_STEP, 0.5F);
        int hardness = (int) field.getScorer().getSettings().getAttribute(GamemodeAttribute.GARBAGE_WALL_HARDNESS);
        hardenWall(wall, hardness);
        pauseTickLoop = 5;
    }

    public void animateNextWall() {
        if (hiddenWalls.isEmpty() && priorityHiddenWalls.isEmpty()) return;

        if (!hardenedWalls.isEmpty() && hardenedWalls.peek().getHardness() <= 0) {
            field.playSoundToPlayers(Sound.ENTITY_GOAT_HORN_BREAK, 0.5f, 0.5f);
            animatingWall = hardenedWalls.poll();
            updateEffectiveLength();
            int baseTime = generator.getWallActiveTime();
            animatingWall.setTimeRemaining(calculateWallActiveTime(baseTime));
        } else {
            if (!priorityHiddenWalls.isEmpty()) {
                animatingWall = priorityHiddenWalls.removeFirst();
            } else {
                animatingWall = hiddenWalls.removeFirst();
            }
            if (field.eventActive() && field.getEvent().modifyWalls) {
                field.getEvent().modifyWall(animatingWall);
            }
            updateEffectiveLength();
            // Recalculate wall time
            animatingWall.setTimeRemaining(calculateWallActiveTime(animatingWall.getTimeRemaining()));
            animatingWall.spawnWall(field, this, WallState.ANIMATING, hideBottomBorder, addBackBorder);
        }

        animatingWall.setDistanceToTraverse(effectiveLength);
        animatingWall.activateWall(field.getPlayers(), wallMaterial, Utils.getAlternateMaterial(field.getPlayerMaterial()));

        new BukkitRunnable() {
            @Override
            public void run() {
                // Wait for wall status to be visible
                if (animatingWall == null) return;
                if (animatingWall.getWallState() == WallState.VISIBLE) {
                    activeWalls.add(animatingWall);
                    animatingWall = null;
                    this.cancel();
                }
            }
        }.runTaskTimer(FillInTheWallLite.getInstance(), 0, 1);
    }

    public void tick() {
        if (pauseTickLoop > 0) {
            pauseTickLoop--;
            return;
        }

        if (hiddenWalls.isEmpty()) {
            // If there's an event going on that overrides generation, don't generate a new wall
            if (!(field.eventActive() && field.getEvent().overrideGeneration)) {
                // Tell generator to add a new wall to all queues
                generator.addNewWallToQueues();
            }
        }

        boolean frozen = field.eventActive() && field.getEvent().wallFreeze;

        // Animate the next wall when possible
        if (activeWalls.isEmpty() && !hiddenWalls.isEmpty()) {
            spawnCooldown = maxSpawnCooldown;
            spawnNextWall();
        // only decrement spawnCooldown if allowMultipleWalls is true
        } else if (!frozen && allowMultipleWalls && spawnCooldown-- <= 0) {
            spawnCooldown = maxSpawnCooldown;
            spawnNextWall();
        }

        // If walls are frozen, make particles and return
        if (frozen) {
            for (Wall wall : activeWalls) {
                wall.frozenParticles();
            }
            return;
        }

        sortActiveWalls();

        // Tick all visible walls
        Iterator<Wall> it = activeWalls.iterator();
        while (it.hasNext()) {
            Wall wall = it.next();
            int remaining = wall.tick();
            // If wall runs out of time -
            if (remaining <= 0 && wall.getWallState() == WallState.VISIBLE) {
                wall.despawn();
                it.remove();
                field.matchAndScore(wall);
                pauseTickLoop = field.getClearDelay();
            } else if (wall.getWallState() != WallState.VISIBLE) {
                FillInTheWallLite.getInstance().getSLF4JLogger().error("Attempted to tick wall before spawned..");
            }
        }
    }

    /**
     * Insta-sends the current wall to the playing field for matching.
     * @param force Whether to forcefully send the wall. If this is the case, no penalty for an empty field will be applied
     */
    public void instantSend(boolean force) {
        if (activeWalls.isEmpty()) return;
        // sort walls by time remaining
        sortActiveWalls();

        if (!force && field.getScorer().getSettings().getBooleanAttribute(GamemodeAttribute.REFUSE_IMPERFECT_WALLS)) {
            Map<Pair<Integer, Integer>, Block> extraBlocks = frontmostWall.getExtraBlocks(field);
            Map<Pair<Integer, Integer>, Block> correctBlocks = frontmostWall.getCorrectBlocks(field);
            Map<Pair<Integer, Integer>, Block> missingBlocks = frontmostWall.getMissingBlocks(field);

            if (!(correctBlocks.size() == frontmostWall.getHoles().size() && extraBlocks.isEmpty() && missingBlocks.isEmpty())) {
                field.playSoundToPlayers(Sound.ENTITY_VILLAGER_NO, 1);
                return;
            }
        }

        boolean originalPenalize = field.getScorer().penalizeEmptyField;
        if (force) field.getScorer().penalizeEmptyField = false;
        field.matchAndScore(frontmostWall);
        field.getScorer().penalizeEmptyField = originalPenalize;
        activeWalls.remove(frontmostWall);
        frontmostWall.despawn();
    }

    public void instantSend() {
        instantSend(false);
    }

    public void sortActiveWalls() {
        activeWalls.sort(Comparator.comparingInt(Wall::getTimeRemaining));
        if (!activeWalls.isEmpty()) {
            if (activeWalls.getFirst() != frontmostWall) {
                field.refreshIncorrectBlockHighlights(activeWalls.getFirst());
            }
            frontmostWall = activeWalls.getFirst();
        } else {
            frontmostWall = null;
        }
    }

    public int getFullLength() {
        return fullLength;
    }

    public int getEffectiveLength() {
        return effectiveLength;
    }

    public void clearAllWalls() {
        for (Wall wall : activeWalls) {
            wall.despawn();
        }
        for (Wall wall : hardenedWalls) {
            wall.despawn();
        }
        activeWalls.clear();
        if (animatingWall != null) {
            animatingWall.despawn();
            animatingWall = null;
        }
        hiddenWalls.clear();
        hardenedWalls.clear();
        priorityHiddenWalls.clear();
    }

    public void setWallActiveTime(int wallActiveTime) {
        generator.setWallActiveTime(wallActiveTime);
    }

    public int getWallActiveTime() {
        return generator.getWallActiveTime();
    }

    public void setRandomHoleCount(int randomHoleCount) {
        generator.setRandomHoleCount(randomHoleCount);
    }

    public int getRandomHoleCount() {
        return generator.getRandomHoleCount();
    }

    public int getConnectedHoleCount() {
        return generator.getConnectedHoleCount();
    }

    public boolean isRandomizeFurther() {
        return generator.isRandomizeFurther();
    }

    public void setConnectedHoleCount(int connectedHoleCount) {
        generator.setConnectedHoleCount(connectedHoleCount);
    }

    public void setRandomizeFurther(boolean randomizeFurther) {
        generator.setRandomizeFurther(randomizeFurther);
    }

    public void setMinimumHoleCount(int minimumHoleCount) {
        generator.setWallHolesMin(minimumHoleCount);
    }

    public void clearHiddenWalls() {
        hiddenWalls.clear();
    }

    public void clearPriorityHiddenWalls() {
        priorityHiddenWalls.clear();
    }

    /**
     * Counts all walls that are active, including the wall currently being animated, and excluding garbage walls.
     * @return The number of active walls.
     */
    public int countActiveWalls() {
        return activeWalls.size() + (animatingWall != null ? 1 : 0);
    }

    public int countHiddenWalls() {
        return hiddenWalls.size();
    }

    public int countHardenedWalls() {
        return hardenedWalls.size();
    }

    public void setHideBottomBorder(boolean hideBottomBorder) {
        this.hideBottomBorder = hideBottomBorder;
    }

    public void setGenerator(WallGenerator generator) {
        this.generator = generator;
        generator.addQueue(this);
    }

    public void resetGenerator() {
        this.generator = new WallGenerator(field.getLength(), field.getHeight(), 2, 4, 160);
        generator.addQueue(this);
    }

    public void allowMultipleWalls(boolean allow) {
        allowMultipleWalls = allow;
    }

    public void setMaxSpawnCooldown(int maxSpawnCooldown) {
        this.maxSpawnCooldown = maxSpawnCooldown;
        spawnCooldown = maxSpawnCooldown;
    }

    public void setWallMaterial(Material wallMaterial) {
        this.wallMaterial = wallMaterial;
    }

    public Material getWallMaterial() {
        return wallMaterial;
    }

    public void correctAllWalls() {
        for (Wall wall : activeWalls) {
            wall.correct();
        }
    }

    // Wall hardening

    /**
     * Takes a new wall and hardens it at the end of the queue.
     * This is generally an internal method, use PlayingFieldScorer#addGarbageToQueue
     * (bad code design again)
     * @param wall Wall to harden
     * @param hardness Resistance to positive judgements (perfect = 2, cool = 1)
     */
    public void hardenWall(Wall wall, int hardness) {
        if (wall.getWallState() != WallState.HIDDEN) {
            FillInTheWallLite.getInstance().getSLF4JLogger().error(
                    "Attempted to harden wall that is not hidden/new..");
            return;
        }

        // Tell wall where to spawn
        // Add to hardened walls list
        // Update effective length

        wall.spawnWall(field, this, WallState.HARDENED, hideBottomBorder, addBackBorder);
        wall.setHardness(hardness);

        hardenedWalls.push(wall);

        updateEffectiveLength();
    }

    /**
     * Attempts to break the next hardened wall in the queue.
     * @param power How much to crack the wall by
     */
    public void crackHardenedWall(int power) {
        if (hardenedWalls.isEmpty()) return;
        Wall wall = hardenedWalls.getFirst();
        wall.decreaseHardness(power);
    }

    public int calculateWallActiveTime(int baseTime) {
        // This is the ratio between the effective length and the full length,
        // but it linearly scales to a minimum of 0.5x the base time.
        double ratio = (effectiveLength + ((fullLength - effectiveLength) / 2.0)) / fullLength;
        return (int) (baseTime * ratio);
    }

    public void updateEffectiveLength() {
        effectiveLength = fullLength - hardenedWalls.size();
    }

    public Wall getFrontmostWall() {
        return frontmostWall;
    }

    public List<Wall> getActiveWalls() {
        return activeWalls;
    }

    public void pauseTicking(int ticks) {
        pauseTickLoop = ticks;
    }
}
