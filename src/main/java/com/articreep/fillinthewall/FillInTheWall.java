package com.articreep.fillinthewall;

import com.articreep.fillinthewall.commands.FITWCommand;
import com.articreep.fillinthewall.commands.RegisterPlayingField;
import com.articreep.fillinthewall.game.PlayingField;
import com.articreep.fillinthewall.game.PlayingFieldManager;
import com.articreep.fillinthewall.menu.SandboxMenu;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;

public final class FillInTheWall extends JavaPlugin implements Listener {
    public static final String NO_COLLISION_TEAM_NAME = "fitw_no_collision";
    private static Scoreboard blankScoreboard;
    private static FillInTheWall instance = null;
    private FileConfiguration playingFieldConfig;

    @Override
    public void onEnable() {
        instance = this;
        RegisterPlayingField registerPlayingField = new RegisterPlayingField();
        getCommand("fillinthewall").setExecutor(new FITWCommand());
        getCommand("registerplayingfield").setExecutor(registerPlayingField);
        getServer().getPluginManager().registerEvents(new PlayingFieldManager(), this);
        getServer().getPluginManager().registerEvents(registerPlayingField, this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new GlobalListeners(), this);
        getServer().getPluginManager().registerEvents(new SandboxMenu(), this);

        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            loadPlayingFieldConfig();
            saveDefaultConfig();

            blankScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            Team team = blankScoreboard.registerNewTeam(FillInTheWall.NO_COLLISION_TEAM_NAME);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

            // Create directories
            File customWallFolder = new File(getDataFolder(), "custom");
            customWallFolder.mkdirs();

            PlayingFieldManager.parseConfig(getPlayingFieldConfig());
        }, 1);

        getSLF4JLogger().info("FillInTheWallLite has been enabled!");

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        for (PlayingField field : PlayingFieldManager.playingFieldLocations.values()) {
            if (field.hasStarted()) field.stop(false, false);
            else {
                field.forceRemoveMenu();
                field.removeEndScreen();
            }
        }
    }

    public static FillInTheWall getInstance() {
        return instance;
    }

    public FileConfiguration getPlayingFieldConfig() {
        return playingFieldConfig;
    }

    public void savePlayingFieldConfig() {
        try {
            playingFieldConfig.save(new File(getDataFolder(), "playingfields.yml"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void reload() {
        super.reloadConfig();
        loadPlayingFieldConfig();
        PlayingFieldManager.removeAllGames();
        PlayingFieldManager.parseConfig(getPlayingFieldConfig());
    }

    private void loadPlayingFieldConfig() {
        File playingFieldFile = new File(getDataFolder(), "playingfields.yml");
        if (!playingFieldFile.exists()) {
            playingFieldFile.getParentFile().mkdirs();
            saveResource("playingfields.yml", false);
        }
        playingFieldConfig = YamlConfiguration.loadConfiguration(playingFieldFile);
    }

    public static Scoreboard getBlankScoreboard() {
        return blankScoreboard;
    }
}
