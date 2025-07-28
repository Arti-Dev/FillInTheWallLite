package com.articreep.fillinthewall.commands;

import com.articreep.fillinthewall.FillInTheWallLite;
import com.articreep.fillinthewall.game.*;
import com.articreep.fillinthewall.gamemode.Gamemode;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FITWCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        MiniMessage miniMessage = MiniMessage.miniMessage();
        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("reload") && sender.hasPermission("fitw.reload")) {
                FillInTheWallLite.getInstance().reload();
                sender.sendMessage(miniMessage.deserialize("<green>Config reloaded!"));
                return true;
            } else if (args[0].equalsIgnoreCase("toggle") && sender.hasPermission("fitw.toggle")) {
                PlayingFieldManager.disablePlayingFields = !PlayingFieldManager.disablePlayingFields;
                if (PlayingFieldManager.disablePlayingFields) {
                    sender.sendMessage(miniMessage.deserialize("<red>Playing fields have been disabled!"));
                } else {
                    sender.sendMessage(miniMessage.deserialize("<green>Playing fields have been enabled!"));
                }
            } else if (args[0].equalsIgnoreCase("custom")) {
                if (args.length == 2 && sender instanceof Player player && PlayingFieldManager.isInGame(player)) {
                    PlayingField field = PlayingFieldManager.activePlayingFields.get(player);
                    if (field.getScorer().getGamemode() == Gamemode.SANDBOX) {
                        WallBundle bundle = WallBundle.getWallBundle(args[1]);
                        if (bundle.size() == 0) {
                            sender.sendMessage(miniMessage.deserialize("<red>Something went wrong loading custom walls!"));
                        } else {
                            List<Wall> walls = bundle.getWalls();
                            field.getQueue().clearAllWalls();
                            walls.forEach(field.getQueue()::addWall);
                            sender.sendMessage(miniMessage.deserialize("<green>Imported " + walls.size() + " walls"));
                        }
                    } else {
                        sender.sendMessage(miniMessage.deserialize("<red>You can only use this command in custom mode."));
                    }
                } else {
                    sender.sendMessage("Wrong syntax... I won't tell you how though! >:)");
                }
            } else if (args[0].equalsIgnoreCase("hotbar")) {
                if (sender instanceof Player player) {
                    if (PlayingFieldManager.isInGame(player)) {
                        PlayingField field = PlayingFieldManager.activePlayingFields.get(player);
                        field.formatInventory(player);
                    } else {
                        sender.sendMessage("You are not in a game!");
                    }
                } else {
                    sender.sendMessage("This command can only be used by players!");
                }
            } else {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        ArrayList<String> strings = new ArrayList<>();
        if (args.length == 1) {
            strings.add("custom");
            strings.add("hotbar");

            // todo use permissions
            if (sender.hasPermission("fitw.reload"))
                strings.add("reload");
            if (sender.hasPermission("fitw.toggle"))
                strings.add("toggle");
            StringUtil.copyPartialMatches(args[0], strings, completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("custom")) {
                StringUtil.copyPartialMatches(args[1], WallBundle.getAvailableWallBundles(), completions);
            }
        }
        return completions;
    }
}
