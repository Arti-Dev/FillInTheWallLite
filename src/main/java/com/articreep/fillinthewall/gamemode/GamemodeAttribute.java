package com.articreep.fillinthewall.gamemode;

import com.articreep.fillinthewall.game.DisplayType;
import com.articreep.fillinthewall.game.PlayingFieldScorer;
import com.articreep.fillinthewall.modifiers.ModifierEvent;

public enum GamemodeAttribute {
    // todo I'm not going to bother with enforcing types for now, but the types of these attributes are listed here
    TIME_LIMIT(Integer.class, 0),
    DO_LEVELS(Boolean.class, false),
    WALL_TIME_DECREASE_AMOUNT(Integer.class, 20),
    CONSISTENT_HOLE_COUNT(Boolean.class, true),
    RANDOM_HOLE_COUNT(Integer.class, 2),
    CONNECTED_HOLE_COUNT(Integer.class, 4),
    STARTING_WALL_ACTIVE_TIME(Integer.class, 160),
    DISPLAY_SLOT_0(DisplayType.class, DisplayType.TIME),
    DISPLAY_SLOT_1(DisplayType.class, DisplayType.PERFECT_WALLS),
    DISPLAY_SLOT_2(DisplayType.class, DisplayType.LEVEL),
    DISPLAY_SLOT_3(DisplayType.class, DisplayType.SCORE),
    ACTIONBAR_DISPLAY(PlayingFieldScorer.ActionBarType.class, PlayingFieldScorer.ActionBarType.NONE),
    DO_GARBAGE_WALLS(Boolean.class, false),
    GARBAGE_WALL_HARDNESS(Integer.class, 3),
  /**
     * Amount of modifier events that can be activated until the game ends.
     */
    MODIFIER_EVENT_CAP(Integer.class, -1),
    HIGHLIGHT_INCORRECT_BLOCKS(Boolean.class, false),
    INFINITE_BLOCK_REACH(Boolean.class, false),
    SINGULAR_EVENT(ModifierEvent.Type.class, ModifierEvent.Type.NONE),
    /**
     * Whether to generate co-op walls. If enabled, the consistent and random hole counts are combined to form
     * a single hole count, which is fed into a different wall algorithm.
     */
    PERFECT_WALL_CAP(Integer.class, -1),
    LEVEL_CAP(Integer.class, -1),
    SCORE_BY_TIME(Boolean.class, false),
    REFUSE_IMPERFECT_WALLS(Boolean.class, false),
    /**
     * Whether to update everyone's personal bests if more than one player was on the playing field
     */
    TEAM_EFFORT(Boolean.class, false);

    private final Class<?> type;
    private final Object defaultValue;
    GamemodeAttribute(Class<?> type, Object defaultValue) {
        this.type = type;
        this.defaultValue = defaultValue;
    }

    public Class<?> getType() {
        return type;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }
}
