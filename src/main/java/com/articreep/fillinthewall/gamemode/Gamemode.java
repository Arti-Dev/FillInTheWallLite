package com.articreep.fillinthewall.gamemode;

import com.articreep.fillinthewall.game.DisplayType;
import com.articreep.fillinthewall.game.PlayingFieldScorer;
import com.articreep.fillinthewall.modifiers.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;

public enum Gamemode {

    ENDLESS("<gradient:#5e4fa2:#f79459:red>Endless</gradient>", "Step off the playing field to stop playing.", Material.WAXED_EXPOSED_CUT_COPPER),
    SCORE_ATTACK("<gold>Score Attack", "Score as much as you can in 2 minutes!", Material.GOLD_BLOCK),
    RUSH_SCORE_ATTACK("<red>Rush Score Attack", "Use Rush Attacks to score as much as you can!", Material.REDSTONE_BLOCK),
    SPRINT("<aqua>Sprint", "Perfect clear 20 walls as fast as you can!", Material.DIAMOND_BLOCK),
    CAPPED_MARATHON("<gray>Marathon", "Aim to clear level 15 as quickly as possible!", Material.COBBLESTONE),
    MARATHON("<gray>Survival", "Survive as long as you can!", Material.COBBLED_DEEPSLATE),
    SANDBOX("<gradient:green:dark_green>Sandbox", "...like the video game Minecraft?", Material.CHAIN_COMMAND_BLOCK),
    MEGA("<dark_aqua>Mega", "Work with others to fill 200 holes!", Material.LIGHT_BLUE_CONCRETE),
    TUTORIAL("Tutorial", "Learn how to play!", Material.END_STONE);

    static {
        ENDLESS.addAttribute(GamemodeAttribute.CONSISTENT_HOLE_COUNT, false);
        ENDLESS.addAttribute(GamemodeAttribute.RANDOM_HOLE_COUNT, 2);
        ENDLESS.addAttribute(GamemodeAttribute.CONNECTED_HOLE_COUNT, 4);
        ENDLESS.addAttribute(GamemodeAttribute.STARTING_WALL_ACTIVE_TIME, 160);
        ENDLESS.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        ENDLESS.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        ENDLESS.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.SPEED);
        ENDLESS.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        ENDLESS.addAttribute(GamemodeAttribute.ACTIONBAR_DISPLAY, PlayingFieldScorer.ActionBarType.ENDLESS_LEVEL_PROGRESS);

        SCORE_ATTACK.addAttribute(GamemodeAttribute.TIME_LIMIT, 20 * 120);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DO_LEVELS, true);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.LEVEL);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.WALL_TIME_DECREASE_AMOUNT, 17);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.ACTIONBAR_DISPLAY, PlayingFieldScorer.ActionBarType.LEVEL_PROGRESS);

        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.DO_LEVELS, false);
        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.MODIFIER_EVENT_CAP, 5);
        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.RANDOM_HOLE_COUNT, 1);
        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.CONNECTED_HOLE_COUNT, 0);
        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.EVENTS);
        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.SPEED);
        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.ACTIONBAR_DISPLAY, PlayingFieldScorer.ActionBarType.NONE);

        SPRINT.addAttribute(GamemodeAttribute.CONSISTENT_HOLE_COUNT, true);
        SPRINT.addAttribute(GamemodeAttribute.RANDOM_HOLE_COUNT, 1);
        SPRINT.addAttribute(GamemodeAttribute.CONNECTED_HOLE_COUNT, 4);
        SPRINT.addAttribute(GamemodeAttribute.STARTING_WALL_ACTIVE_TIME, 160);
        SPRINT.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        SPRINT.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.NONE);
        SPRINT.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.SPEED);
        SPRINT.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.NONE);
        SPRINT.addAttribute(GamemodeAttribute.PERFECT_WALL_CAP, 20);
        SPRINT.addAttribute(GamemodeAttribute.SCORE_BY_TIME, true);
        SPRINT.addAttribute(GamemodeAttribute.REFUSE_IMPERFECT_WALLS, true);
        SPRINT.addAttribute(GamemodeAttribute.ACTIONBAR_DISPLAY, PlayingFieldScorer.ActionBarType.PERFECT_WALLS);

        MEGA.addAttribute(GamemodeAttribute.STARTING_WALL_ACTIVE_TIME, 20 * 60 * 5);
        MEGA.addAttribute(GamemodeAttribute.RANDOM_HOLE_COUNT, 50);
        MEGA.addAttribute(GamemodeAttribute.CONNECTED_HOLE_COUNT, 150);
        MEGA.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        MEGA.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.NONE);
        MEGA.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.SPEED);
        MEGA.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.NONE);
        MEGA.addAttribute(GamemodeAttribute.PERFECT_WALL_CAP, 1);
        MEGA.addAttribute(GamemodeAttribute.SCORE_BY_TIME, true);
        MEGA.addAttribute(GamemodeAttribute.HIGHLIGHT_INCORRECT_BLOCKS, true);
        MEGA.addAttribute(GamemodeAttribute.REFUSE_IMPERFECT_WALLS, true);
        MEGA.addAttribute(GamemodeAttribute.TEAM_EFFORT, true);
        MEGA.addAttribute(GamemodeAttribute.ACTIONBAR_DISPLAY, PlayingFieldScorer.ActionBarType.NONE);

        TUTORIAL.addAttribute(GamemodeAttribute.STARTING_WALL_ACTIVE_TIME, 20 * 30);
        TUTORIAL.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.NONE);
        TUTORIAL.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.NONE);
        TUTORIAL.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.TIME);
        TUTORIAL.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        TUTORIAL.addAttribute(GamemodeAttribute.HIGHLIGHT_INCORRECT_BLOCKS, true);
        TUTORIAL.addAttribute(GamemodeAttribute.SINGULAR_EVENT, ModifierEvent.Type.TUTORIAL);

        MARATHON.addAttribute(GamemodeAttribute.DO_LEVELS, true);
        MARATHON.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        MARATHON.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        MARATHON.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.LEVEL);
        MARATHON.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        MARATHON.addAttribute(GamemodeAttribute.WALL_TIME_DECREASE_AMOUNT, 12);
        MARATHON.addAttribute(GamemodeAttribute.DO_GARBAGE_WALLS, true);
        MARATHON.addAttribute(GamemodeAttribute.ACTIONBAR_DISPLAY, PlayingFieldScorer.ActionBarType.LEVEL_PROGRESS);

        CAPPED_MARATHON.addAttribute(GamemodeAttribute.DO_LEVELS, true);
        CAPPED_MARATHON.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        CAPPED_MARATHON.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        CAPPED_MARATHON.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.LEVEL);
        CAPPED_MARATHON.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        CAPPED_MARATHON.addAttribute(GamemodeAttribute.WALL_TIME_DECREASE_AMOUNT, 12);
        CAPPED_MARATHON.addAttribute(GamemodeAttribute.ACTIONBAR_DISPLAY, PlayingFieldScorer.ActionBarType.LEVEL_PROGRESS);
        CAPPED_MARATHON.addAttribute(GamemodeAttribute.LEVEL_CAP, 15);
        CAPPED_MARATHON.addAttribute(GamemodeAttribute.DO_GARBAGE_WALLS, true);
        CAPPED_MARATHON.addAttribute(GamemodeAttribute.SCORE_BY_TIME, true);

        SANDBOX.addAttribute(GamemodeAttribute.ACTIONBAR_DISPLAY, PlayingFieldScorer.ActionBarType.NONE);
    }

    final String title;
    final String description;
    final GamemodeSettings settings = new GamemodeSettings();
    /**
     * The block that represents the gamemode in the pregame menu
     **/
    final Material block;

    Gamemode(String title, String description, Material block) {
        this.title = title;
        this.description = description;
        this.block = block;
    }

    public Component getTitle() {
        return MiniMessage.miniMessage().deserialize(title);
    }

    public Component getDescription() {
        return MiniMessage.miniMessage().deserialize(description);
    }

    private void addAttribute(GamemodeAttribute attribute, Object value) {
        settings.setAttribute(attribute, value);
    }

    public GamemodeSettings getDefaultSettings() {
        return settings.copy();
    }

    public Material getBlock() {
        return block;
    }

}