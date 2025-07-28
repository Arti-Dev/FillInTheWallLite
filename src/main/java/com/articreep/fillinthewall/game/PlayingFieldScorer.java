package com.articreep.fillinthewall.game;

import com.articreep.fillinthewall.FillInTheWallLite;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.gamemode.GamemodeSettings;
import com.articreep.fillinthewall.menu.EndScreen;
import com.articreep.fillinthewall.modifiers.*;
import com.articreep.fillinthewall.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.javatuples.Pair;

import java.time.Duration;
import java.util.*;

public class PlayingFieldScorer {
    PlayingField field;
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    private int score = 0;
    private int perfectWallsCleared = 0;
    private int perfectWallChain = 0;
    private double blocksPlaced = 0;
    // time in ticks (this is displayed on the text display)
    private int time = 0;
    /** for calculating blocks per second */
    private int absoluteTimeElapsed = 0;
    private Gamemode gamemode = Gamemode.ENDLESS;
    private GamemodeSettings settings = Gamemode.ENDLESS.getDefaultSettings();
    private int eventCount = 0;
    public boolean penalizeEmptyField = true;
    private int garbagePoints = 0;
    private final Deque<Wall> garbageQueue = new ArrayDeque<>();

    // Levels (if enabled)
    // this is only for score attack/marathon
    boolean doLevels = false;
    private int level = 1;
    private int levelProgressMax = 10;
    private double levelProgress = 0;
    private int wallTimeDecreaseAmount = 20;

    private EndlessRun endlessRun = null;

    public PlayingFieldScorer(PlayingField field) {
        this.field = field;
    }

    public enum BonusType {
        PERFECT, FIRE, STRIPE, PLAYER, CHAIN, GIMMICKLESS
    }

    public Judgement scoreWall(Wall wall, PlayingField field) {
        ModifierEvent event = null;
        if (field.eventActive()) event = field.getEvent();

        int score;
        double percent;
        HashMap<BonusType, Integer> bonusMap;

        if (event != null && event.overrideScoreCalculation) {
            score = field.getEvent().calculateScore(wall);
        } else score = calculateScore(wall, field);

        if (event != null && event.overridePercentCalculation) {
            percent = field.getEvent().calculatePercent(wall);
        } else percent = calculatePercent(wall, score);

        if (event != null && event.overrideBonusCalculation) {
            bonusMap = field.getEvent().evaluateBonus(percent, wall);
        } else bonusMap = evaluateBonus(percent);

        int totalBonus = sumBonus(bonusMap);
        this.score += score + totalBonus;

        Judgement judgement = Judgement.MISS;

        // Determine judgement
        for (Judgement j : Judgement.values()) {
            if (percent >= j.getPercent()) {
                judgement = j;
                break;
            }
        }

        if (event != null && event.overrideScoreTitle) event.displayScoreTitle(judgement, score, bonusMap);
        else displayScoreTitle(judgement, score, bonusMap);
        playJudgementSound(judgement);

        // Add/subtract to bonus
        if ((!field.eventActive() || field.getEvent().allowMeterAccumulation)) {
            awardLevelPoints(percent);
        }

        // Start Rush on Rush score attack if perfect
        if (gamemode == Gamemode.RUSH_SCORE_ATTACK && (!field.eventActive()) && judgement == Judgement.PERFECT) {
            activateEvent(ModifierEvent.Type.RUSH);
        }

        // Garbage wall rules
        if (settings.getBooleanAttribute(GamemodeAttribute.DO_GARBAGE_WALLS)) {
            if (percent >= Judgement.COOL.getPercent()) {
                awardGarbagePoints(judgement);
            } else {
                // miss
                Wall garbageWall = createMissGarbageWall(wall);
                field.getQueue().hardenWall(garbageWall,
                        (int) settings.getAttribute(GamemodeAttribute.GARBAGE_WALL_HARDNESS));
            }
        } else if (field.getQueue().countHardenedWalls() > 0) {
            awardGarbagePoints(judgement);
        }

        // Check if we are at endless score threshold
        if (endlessRun != null && this.score >= endlessRun.scoreToNextLevel) {
            field.playSoundToPlayers(Sound.ITEM_TRIDENT_THROW, 1, 1);
            Bukkit.getScheduler().runTaskLater(FillInTheWallLite.getInstance(),
                    () -> endlessRun.nextPhase(), 10);
        }

        return judgement;
    }

    public HashMap<BonusType, Integer> evaluateBonus(double percent) {
        HashMap<BonusType, Integer> bonusMap = new HashMap<>();
        if (percent >= 1) {
            perfectWallsCleared++;
            perfectWallChain++;
            bonusMap.put(BonusType.PERFECT, 1);
        } else {
            perfectWallChain = 0;
            bonusMap.put(BonusType.PERFECT, 0);
        }
        return bonusMap;
    }

    private static int sumBonus(HashMap<BonusType, Integer> map) {
        int bonus = 0;
        for (Integer i : map.values()) {
            bonus += i;
        }
        return bonus;
    }

    /**
     * Takes a % accuracy on the scored wall and awards level points based on that.
     * @param percent Percent score of the last wall
     */
    private void awardLevelPoints(double percent) {
        if (percent >= Judgement.COOL.getPercent()) {
            levelProgress += percent;
            if (levelProgress > levelProgressMax) {
                levelProgress = levelProgressMax;
            }
        }

        // Activate meter/level up
        if (doLevels && levelProgress >= levelProgressMax) {
            setLevel(level + 1);
            field.flashLevel(80);
        }
    }

    private void levelUpSound() {
        new BukkitRunnable() {
            final float CSHARP = (float) Math.pow(2, -5f/12);
            final float D = (float) Math.pow(2, -4f/12);
            final float DSHARP = (float) Math.pow(2, -3f/12);
            int i = 0;
            @Override
            public void run() {
                float pitch;
                if (i == 0) pitch = CSHARP;
                else if (i == 1) pitch = D;
                else {
                    pitch = DSHARP;
                    cancel();
                }
                for (Player player : field.getPlayers()) {
                    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, pitch);
                }
                i++;
            }
        }.runTaskTimer(FillInTheWallLite.getInstance(), 0, 2);
    }

    private final static float E1 = (float) Math.pow(2, (float) -2/12);
    private final static float C1 = (float) Math.pow(2, (float) -6/12);

    private final static float B1 = (float) Math.pow(2, (float) -7/12);
    private final static float D1 = (float) Math.pow(2, (float) -4/12);
    private final static float F1 = (float) Math.pow(2, (float) -1/12);
    private final static float G2 = (float) Math.pow(2, (float) 1/12);
    private final static float A2 = (float) Math.pow(2, (float) 3/12);

    // Taken from advancement bingo on FACT MC which I contributed to
    public void playGameEnd() {
        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (i >= 8) {
                    cancel();
                } else if (i == 0 || i == 1 || i == 3 || i == 7) {
                    field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, C1);
                    field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, E1);
                    field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, G2);
                } else if (i == 2) {
                    field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, D1);
                    field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, F1);
                    field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, A2);
                } else if (i == 5) {
                    field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, B1);
                    field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, D1);
                    field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, F1);
                }
                i++;
            }
        }.runTaskTimer(FillInTheWallLite.getInstance(), 0, 3);
    }

    private void awardGarbagePoints(Judgement judgement) {
        if (field.getQueue().countHardenedWalls() > 0) {
            if (judgement == Judgement.COOL) {
                garbagePoints += 1;
            } else if (judgement == Judgement.PERFECT) {
                garbagePoints += 2;
            }

            // If we have enough garbage points, crack a hardened wall
            if (garbagePoints >= (int) settings.getAttribute(GamemodeAttribute.GARBAGE_WALL_HARDNESS)) {
                field.getQueue().crackHardenedWall(garbagePoints);
                garbagePoints = 0;
            }
        }
    }

    private Wall createMissGarbageWall(Wall wall) {
        Wall copy = wall.copy();
        Set<Pair<Integer, Integer>> correctBlocks = wall.getCorrectBlocks(field).keySet();

        for (Pair<Integer, Integer> hole : correctBlocks) {
            copy.removeHole(hole);
        }

        // If all holes are filled in and it's still a miss, randomly insert holes from the original wall
        // todo subject to change
        if (copy.getHoles().isEmpty()) {
            Iterator<Pair<Integer, Integer>> iterator = correctBlocks.iterator();
            for (int i = 0; i < wall.getExtraBlocks(field).size(); i++) {
                if (iterator.hasNext()) {
                    Pair<Integer, Integer> correctHole = iterator.next();
                    copy.insertHole(correctHole);
                }
            }
        }
        return copy;
    }

    /**
     * Attempts to activate the event associated with the current gamemode.
     */
    public ModifierEvent activateEvent(ModifierEvent.Type type) {
        if (type == null || type == ModifierEvent.Type.NONE) {
            return null;
        }
        ModifierEvent event = type.createEvent();
        return activateEvent(event);
    }

    public ModifierEvent activateEvent(ModifierEvent event) {
        Bukkit.getScheduler().runTask(FillInTheWallLite.getInstance(), () -> {
            event.setPlayingField(field);
            event.activate();
            eventCount++;
        });
        return event;
    }

    public void displayScoreTitle(Judgement judgement, int score, Map<BonusType, Integer> bonusMap) {
        Title title = Title.title(judgement.getFormattedText(),
                // todo I shouldn't have to manually use the bonus map, I should just know what the x+y score is
                Component.text(score + bonusMap.get(BonusType.PERFECT) + bonusMap.get(BonusType.GIMMICKLESS) + " points", judgement.getColor()),
                getScoreTitleTimes());
        field.sendTitleToPlayers(title);
    }

    public static Title.Times getScoreTitleTimes() {
        return Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(250));
    }

    public void playJudgementSound(Judgement judgement) {
        for (Player player : field.getPlayers()) {
            player.playSound(player.getLocation(), judgement.getSound(), 0.7f, 1);
        }
    }

    public int calculateScore(Wall wall, PlayingField field) {
        Map<Pair<Integer, Integer>, Block> extraBlocks = wall.getExtraBlocks(field);
        Map<Pair<Integer, Integer>, Block> correctBlocks = wall.getCorrectBlocks(field);

        // Check score
        int points = correctBlocks.size() - extraBlocks.size();
        if (points < 0) points = 0;
        if (penalizeEmptyField && !wall.isEmpty() && extraBlocks.isEmpty() && correctBlocks.isEmpty()) points = -1;
        return points;
    }

    public void scoreEvent(ModifierEvent event) {
        if (event instanceof Rush rush) {
            // (x/2)^2
            int rushResults = (int) Math.pow(((double) rush.getBoardsCleared() / 2), 2);
            field.overrideDisplay(DisplayType.SCORE, 80, miniMessage.deserialize("<red>+<bold>" + rushResults + " points from Rush!!!"));
            score += rushResults;
        }
    }

    public double calculatePercent(Wall wall, int score) {
        if (wall.getHoles().isEmpty() && score == 0) return 1;
        if (score < 0) return 0;
        return (double) score / wall.getHoles().size();
    }

    public double calculatePercent(Wall wall, PlayingField field) {
        int score = calculateScore(wall, field);
        if (wall.getHoles().isEmpty() && score == 0) return 1;
        if (score < 0) return 0;
        return (double) score / wall.getHoles().size();
    }

    public int getPerfectWallsCleared() {
        return perfectWallsCleared;
    }

    public int getScore() {
        return score;
    }

    public Component getFormattedTime() {
        return Component.text(Utils.getFormattedTime(time));
    }

    public void tick() {
        absoluteTimeElapsed++;

        // if a timefreeze modifier event is active and we're in a singleplayer game, pause the timer
        if (field.eventActive() && field.getEvent().timeFreeze) return;
        // if we're in a score attack game, decrement the time
        if (settings.getIntAttribute(GamemodeAttribute.TIME_LIMIT) > 0) time--;
        else time++;

        if (settings.getIntAttribute(GamemodeAttribute.TIME_LIMIT) > 0) {
            Title.Times warningTimes = Title.Times.times(Duration.ZERO, Duration.ofMillis(2000), Duration.ofMillis(250));
            Title.Times finalCountdownTimes = Title.Times.times(Duration.ZERO, Duration.ofMillis(1000), Duration.ofMillis(250));
            if ((int) settings.getAttribute(GamemodeAttribute.TIME_LIMIT) >= 120 * 20) {
                if (time <= 0) {
                    field.sendMessageToPlayers(miniMessage.deserialize("<red>Time's up!"));
                    field.stop();
                } else if (time == 20 * 60) {
                    field.sendTitleToPlayers(Title.title(
                            Component.empty(),
                            miniMessage.deserialize("<yellow>1 minute remaining!"),
                            warningTimes));
                } else if (time == 20 * 30) {
                    field.sendTitleToPlayers(Title.title(
                            Component.empty(),
                            miniMessage.deserialize("<yellow>30 seconds remaining!"),
                            warningTimes));
                } else if (time <= 20 * 10 && time % 20 == 0) {
                    field.sendTitleToPlayers(Title.title(
                            Component.empty(),
                            miniMessage.deserialize("<red>" + time / 20),
                            finalCountdownTimes));
                }
            } else {
                if (time <= 0) {
                    field.sendMessageToPlayers(miniMessage.deserialize("<red>Time's up!"));
                    field.stop();
                }
                if (time == 20 * 20) {
                    field.sendTitleToPlayers(Title.title(
                            Component.empty(),
                            miniMessage.deserialize("<yellow>20 seconds remaining!"),
                            warningTimes));
                } else if (time <= 20 * 10 && time % 20 == 0) {
                    field.sendTitleToPlayers(Title.title(
                            Component.empty(),
                            miniMessage.deserialize("<red>" + time / 20),
                            finalCountdownTimes));
                }
            }
        }
    }

    public void announceFinalScore() {
        boolean scoreByTime = gamemode.getDefaultSettings().getBooleanAttribute(GamemodeAttribute.SCORE_BY_TIME);
        if (scoreByTime) {
            field.sendMessageToPlayers(miniMessage.deserialize("<aqua>Your final time is <bold>" +
                    Utils.getPreciseFormattedTime(time)));
        } else {
            field.sendMessageToPlayers(miniMessage.deserialize("<green>Your final score is <bold>" + score));
        }
    }

    public EndScreen createEndScreen() {
        EndScreen endScreen = new EndScreen(field.getCenter(true, false).add(0, 1, 0));
        endScreen.addLine(Component.text(Utils.playersToString(field.getPlayers())));
        endScreen.addLine(gamemode.getTitle());
        endScreen.addLine(Component.empty());
        endScreen.addLine(miniMessage.deserialize("<green>Final score: <bold>" + score + "</bold>"));
        if (settings.getIntAttribute(GamemodeAttribute.TIME_LIMIT) <= 0) {
            endScreen.addLine(miniMessage.deserialize("<aqua>Time: <bold>" + Utils.getPreciseFormattedTime(time) + "</bold>"));
        }
        endScreen.addLine(miniMessage.deserialize("<gold>Perfect Walls cleared: <bold>" + perfectWallsCleared + "</bold>"));
        endScreen.addLine(miniMessage.deserialize("<red>" + getFormattedBlocksPerSecond() + " blocks per second"));
        return endScreen;
    }

    // Called by the PlayingField when the game starts
    public void setGamemode(Gamemode gamemode, GamemodeSettings settings) {
        this.gamemode = gamemode;
        this.settings = settings;
        for (GamemodeAttribute attribute : GamemodeAttribute.values()) {
            Object value = settings.getAttribute(attribute);
            
            // todo decide whether to cast here or use the methods that cast beforehand

            switch (attribute) {
                case TIME_LIMIT -> setTime((int) value);
                case DO_LEVELS -> {
                    doLevels = (boolean) value;
                    if (doLevels) setLevel(1);
                }
                case CONSISTENT_HOLE_COUNT -> {
                    if (!doLevels) field.getQueue().setRandomizeFurther(!(boolean) value);
                }
                case RANDOM_HOLE_COUNT -> {
                    if (!doLevels) field.getQueue().setRandomHoleCount((int) value);
                }
                case CONNECTED_HOLE_COUNT -> {
                    if (!doLevels) field.getQueue().setConnectedHoleCount((int) value);
                }
                case STARTING_WALL_ACTIVE_TIME -> {
                    if (!doLevels) field.getQueue().setWallActiveTime((int) value);
                }
                case WALL_TIME_DECREASE_AMOUNT -> {
                    if (doLevels) wallTimeDecreaseAmount = (int) value;
                }
            }
        }
        if (settings.getModifierEventTypeAttribute(GamemodeAttribute.SINGULAR_EVENT) != null
                && settings.getModifierEventTypeAttribute(GamemodeAttribute.SINGULAR_EVENT) != ModifierEvent.Type.NONE) {

            activateEvent(settings.getModifierEventTypeAttribute(GamemodeAttribute.SINGULAR_EVENT)).setInfinite(true);

        } else if (gamemode == Gamemode.SANDBOX) {
            WallBundle bundle = WallBundle.getWallBundle("amogus");
            // todo hardcoded dimension check
            if (bundle.size() == 0 || field.getLength() != 7 || field.getHeight() != 4) {
                field.sendMessageToPlayers(miniMessage.deserialize("<red>Loading custom walls failed"));
            } else {
                List<Wall> walls = bundle.getWalls();
                field.getQueue().clearAllWalls();
                walls.forEach(field.getQueue()::addWall);
            }
        } else if (gamemode == Gamemode.ENDLESS) {
            endlessRun = new EndlessRun(this);
        }
    }
    
    // Default gamemode settings
    public void setGamemode(Gamemode gamemode) {
        setGamemode(gamemode, gamemode.getDefaultSettings());
    }

    // levels
    public void setLevelProgressMax(int meterMax) {
        this.levelProgressMax = meterMax;
    }

    public enum ActionBarType {
        LEVEL_PROGRESS, PERFECT_WALLS, ENDLESS_LEVEL_PROGRESS, NONE
    }

    public Component getLevelProgressActionbar() {
        double percentFilled = levelProgress / levelProgressMax;

        TextColor color;
        String modifier = "";
        if (percentFilled <= 0.3) {
            color = NamedTextColor.GRAY;
        } else if (percentFilled <= 0.7) {
            color = NamedTextColor.YELLOW;
        } else {
            color = NamedTextColor.GREEN;
        }
        return Component.text(modifier + " Next level: " + String.format("%.2f", levelProgress) + "/" + levelProgressMax, color);
    }

    public Component getPerfectWallsActionbar() {
        int perfectWallsRequired = settings.getIntAttribute(GamemodeAttribute.PERFECT_WALL_CAP);
        return miniMessage.deserialize("<aqua>Perfect Walls: " + perfectWallsCleared + "/" + perfectWallsRequired);
    }

    public Component getEndlessLevelProgressActionbar() {
        if (endlessRun == null) return Component.empty();
        int pointsRemaining = Math.max(endlessRun.scoreToNextLevel - score, 0);
        String color = "<gray>";
        if (pointsRemaining < 10) color = "<green>";
        return miniMessage.deserialize(color + pointsRemaining + " points to next level");
    }

    public void setLevel(int level) {
        levelProgress = 0;
        int maxLevel = settings.getIntAttribute(GamemodeAttribute.LEVEL_CAP);
        field.getQueue().setRandomizeFurther(false);
        this.level = level;

        if (maxLevel > 0 && level > maxLevel) {
            field.sendMessageToPlayers(miniMessage.deserialize("<gold>Congratulations!"));
            playGameEnd();
            field.stop(false, true);
            return;
        }
        setDifficulty(level);
        setLevelProgressMax(level);
        if (level != 1) levelUpSound();
        // when we level up, delete all pending walls in the queue which forces a new wall to be made.
        field.getQueue().clearHiddenWalls();
    }

    private void setDifficulty(int level) {
        WallQueue queue = field.getQueue();
        queue.setWallActiveTime(Math.max(200 - level * wallTimeDecreaseAmount, 40));

        if (level == 1) {
            queue.setRandomHoleCount(1);
            queue.setConnectedHoleCount(0);
        } else if (level == 2) {
            queue.setRandomHoleCount(1);
            queue.setConnectedHoleCount(1);
        } else {
            int remainingHoles = (level/2) + 1;
            if (level < 5) {
                queue.setRandomHoleCount(1);
                remainingHoles -= 1;

            } else if (level < 9) {
                queue.setRandomHoleCount(2);
                remainingHoles -= 2;
            } else {
                queue.setRandomHoleCount(3);
                remainingHoles -= 3;
            }
            queue.setConnectedHoleCount(remainingHoles);
        }
    }

    public int getLevel() {
        return level;
    }

    public Gamemode getGamemode() {
        return gamemode;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public void increaseBlocksPlaced() {
        blocksPlaced++;
    }

    public double getBlocksPerSecond() {
        if (absoluteTimeElapsed == 0) return 0;
        return blocksPlaced / ((double) absoluteTimeElapsed / 20);
    }

    public String getFormattedBlocksPerSecond() {
        return String.format("%.2f", getBlocksPerSecond());
    }

    public void addGarbageToQueue(Wall wall) {
        if (garbageQueue.size() < field.getLength() * 2) {
            garbageQueue.push(wall);
        }
    }

    public boolean isGarbageQueueEmpty() {
        return garbageQueue.isEmpty();
    }

    public Wall removeFirstGarbageFromQueue() {
        return garbageQueue.pollFirst();
    }

    public int getEventCount() {
        return eventCount;
    }

    public GamemodeSettings getSettings() {
        return settings;
    }

    public int getPerfectWallChain() {
        return perfectWallChain;
    }

    public void breakPerfectWallChain() {
        perfectWallChain = 0;
    }

    public int getScoreToNextLevel() {
        if (endlessRun == null) return -1;
        else return endlessRun.scoreToNextLevel;
    }
}
