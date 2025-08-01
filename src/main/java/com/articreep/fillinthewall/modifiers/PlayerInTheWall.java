package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.game.Judgement;
import com.articreep.fillinthewall.game.PlayingFieldScorer;
import com.articreep.fillinthewall.game.Wall;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.javatuples.Pair;

import java.util.HashMap;
import java.util.Set;

/**
 * Not compatible with the Scale event
 */
public class PlayerInTheWall extends ModifierEvent {
    public PlayerInTheWall() {
        super();
        modifyWalls = true;
        overrideScoreCalculation = true;
        overrideBonusCalculation = true;
        overrideScoreTitle = true;
    }

    @Override
    public void activate() {
        super.activate();
        setBarriers(Material.BARRIER);
        field.sendTitleToPlayers(miniMessage.deserialize("<green>Player in the Wall"),
                miniMessage.deserialize("Fit yourself into the holes for bonus points!"), 0, 40, 10);
        for (Player player : field.getPlayers()) {
            player.setAllowFlight(false);
            player.teleport(field.getReferencePoint().setDirection(field.getIncomingDirection().multiply(-1)));
        }
    }

    @Override
    public int calculateScore(Wall wall) {
        return super.calculateScore(wall) + countPlayerBlocksInHoles(wall) - countPlayerBlocksNotInHoles(wall);
    }

    @Override
    public HashMap<PlayingFieldScorer.BonusType, Integer> evaluateBonus(double percent, Wall wall) {
        HashMap<PlayingFieldScorer.BonusType, Integer> bonusMap = super.evaluateBonus(percent, wall);

        bonusMap.put(PlayingFieldScorer.BonusType.PLAYER, countPlayerBlocksInHoles(wall));
        return bonusMap;
    }

    @Override
    public void displayScoreTitle(Judgement judgement, int score, HashMap<PlayingFieldScorer.BonusType, Integer> bonus) {
        int playerBonus = bonus.get(PlayingFieldScorer.BonusType.PLAYER);
        Title title = Title.title(judgement.getFormattedText(),
                Component.text(score + bonus.get(PlayingFieldScorer.BonusType.PERFECT), judgement.getColor())
                        .append(Component.text("+" + playerBonus, NamedTextColor.AQUA))
                        .append(Component.text(" points", judgement.getColor())),
                PlayingFieldScorer.getScoreTitleTimes());
        field.sendTitleToPlayers(title);
    }

    private int countPlayerBlocksInHoles(Wall wall) {
        int count = 0;
        for (Player player : field.getPlayers()) {
            Block block = player.getLocation().getBlock();
            Pair<Integer, Integer> coords1 = field.blockToCoordinates(block);
            Pair<Integer, Integer> coords2 = Pair.with(coords1.getValue0(), coords1.getValue1() + 1);

            if (wall.getHoles().contains(coords1)) count++;
            if (wall.getHoles().contains(coords2)) count++;
        }
        return count;
    }

    private int countPlayerBlocksNotInHoles(Wall wall) {
        int count = 0;
        for (Player player : field.getPlayers()) {
            Block block = player.getLocation().getBlock();
            Pair<Integer, Integer> coords1 = field.blockToCoordinates(block);
            Pair<Integer, Integer> coords2 = Pair.with(coords1.getValue0(), coords1.getValue1() + 1);

            if (!wall.getHoles().contains(coords1)) {
                count++;
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1, 1);
            }
            if (!wall.getHoles().contains(coords2)) {
                count++;
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1, 1);
            }
        }
        return count;
    }

    @Override
    public void end() {
        super.end();
        setBarriers(Material.AIR);
        field.sendTitleToPlayers(Component.empty(),
                miniMessage.deserialize("You're free!"), 0, 20, 10);
        for (Player player : field.getPlayers()) {
            player.teleport(field.getSpawnLocation());
            player.setAllowFlight(true);
        }
    }

    private void setBarriers(Material material) {
        for (int z = -1; z <= 1; z += 2) {
            for (int x = 0; x < field.getLength(); x++) {
                for (int y = 0; y < field.getHeight(); y++) {
                    Location loc = field.getReferencePoint()
                            .add(field.getFieldDirection().multiply(x))
                            .add(0, y, 0)
                            .add(field.getIncomingDirection().multiply(z));
                    loc.getBlock().setType(material);
                }
            }
        }
    }

    @Override
    public void modifyWall(Wall wall) {
        Set<Pair<Integer, Integer>> holes = wall.getHoles();
        // goal is to guarantee that there is a 2-block tall hole in this wall

        // check if there already exists one
        for (Pair<Integer, Integer> hole : holes) {
            if (holes.contains(Pair.with(hole.getValue0(), hole.getValue1() + 1))) {
                return;
            }
        }

        // if not, attempt to create one using an existing hole (not randomized)
        for (Pair<Integer, Integer> hole : holes) {
            if (hole.getValue1() + 1 < field.getHeight()) {
                wall.insertHole(Pair.with(hole.getValue0(), hole.getValue1() + 1));
            } else {
                wall.insertHole(Pair.with(hole.getValue0(), hole.getValue1() - 1));
            }
            return;
        }

        // if we reach here, the wall has no holes... that's a troll moment
    }

    public PlayerInTheWall copy() {
        return new PlayerInTheWall();
    }

    @Override
    public void playActivateSound() {
        field.playSoundToPlayers(Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);
    }

    @Override
    public void playDeactivateSound() {
        field.playSoundToPlayers(Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);
    }
}
