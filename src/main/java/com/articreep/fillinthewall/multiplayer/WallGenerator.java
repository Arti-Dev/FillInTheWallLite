package com.articreep.fillinthewall.multiplayer;

import com.articreep.fillinthewall.FillInTheWallLite;
import com.articreep.fillinthewall.game.Wall;
import com.articreep.fillinthewall.game.WallBundle;
import com.articreep.fillinthewall.game.WallQueue;

import java.util.HashSet;
import java.util.Set;

/**
 * Generates walls to feed into WallQueues.
 */
public class WallGenerator {
    private final Set<WallQueue> queues = new HashSet<>();

    int wallCount = 0;

    // Settings
    private final int wallLength;
    private final int wallHeight;

    private int wallHolesMin = 3;

    private int randomHoleCount;
    private int connectedHoleCount;
    private boolean randomizeFurther = true;
    private int wallActiveTime;
    private WallBundle customWallBundle = null;

    // Stats
    private int wallsSpawned = 0;

    public WallGenerator(int length, int height, int startingRandomHoleCount, int startingConnectedHoleCount, int wallActiveTime) {
        this.wallLength = length;
        this.wallHeight = height;
        this.randomHoleCount = startingRandomHoleCount;
        this.connectedHoleCount = startingConnectedHoleCount;
        this.wallActiveTime = wallActiveTime;
    }

    /**
     * Call this method whenever a queue runs out of walls.
     */
    public void addNewWallToQueues() {
        Wall wall = new Wall(wallLength, wallHeight);
        wall.generateHoles(randomHoleCount, connectedHoleCount, randomizeFurther, wallHolesMin);
        wall.setTimeRemaining(wallActiveTime);
//        // debug
//        FillInTheWall.getInstance().getSLF4JLogger().info("R{}C{}, T{}",
//                randomHoleCount, connectedHoleCount, wallActiveTime);
        if (queues.isEmpty()) {
            FillInTheWallLite.getInstance().getSLF4JLogger().warn("No queues to add walls to..?");
        } else {
            wallCount++;
            if (wallCount % 3 == 0 && customWallBundle != null) {
                Wall customWall = customWallBundle.getRandomWall();
                if (customWall != null) wall = customWall;
            }
            for (WallQueue queue : queues) {
                queue.addWall(wall.copy());
            }
        }

        wallsSpawned++;
    }

    public void addQueue(WallQueue queue) {
        queues.add(queue);
    }

    public void setRandomHoleCount(int randomHoleCount) {
        this.randomHoleCount = randomHoleCount;
    }

    public int getRandomHoleCount() {
        return randomHoleCount;
    }

    public void setWallActiveTime(int wallActiveTime) {
        this.wallActiveTime = wallActiveTime;
    }

    public void setConnectedHoleCount(int connectedHoleCount) {
        this.connectedHoleCount = connectedHoleCount;
    }

    public int getConnectedHoleCount() {
        return connectedHoleCount;
    }

    public void setRandomizeFurther(boolean randomizeFurther) {
        this.randomizeFurther = randomizeFurther;
    }

    public boolean isRandomizeFurther() {
        return randomizeFurther;
    }

    public int getLength() {
        return wallLength;
    }


    public int getHeight() {
        return wallHeight;
    }

    public void setWallHolesMin(int wallHolesMin) {
        this.wallHolesMin = wallHolesMin;
    }

    public int getWallActiveTime() {
        return wallActiveTime;
    }

    public static WallGenerator defaultGenerator(int length, int height) {
       return new WallGenerator(length, height, 2, 4, 160);
    }

    public void setCustomWallBundle(WallBundle customWallBundle) {
        this.customWallBundle = customWallBundle;
    }
}
