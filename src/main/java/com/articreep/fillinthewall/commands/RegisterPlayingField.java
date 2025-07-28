package com.articreep.fillinthewall.commands;

import com.articreep.fillinthewall.FillInTheWallLite;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class RegisterPlayingField implements CommandExecutor, Listener {
    private static final HashMap<Player, Session> activeSessions = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        MiniMessage miniMessage = MiniMessage.miniMessage();
        if (!sender.hasPermission("fitw.registerplayingfield")) {
            sender.sendMessage(miniMessage.deserialize("<red>You don't have permission to do that."));
            return true;
        }
        if (!(sender instanceof Player player)) return false;
        if (args.length == 0) {
            if (activeSessions.containsKey(player)) {
                activeSessions.get(sender).onCommandRun("");
            } else {
                Session session = new Session(player);
                session.sendInstructions();
                activeSessions.put(player, session);
            }
        } else {
            if (activeSessions.containsKey(player)) {
                activeSessions.get(player).onCommandRun(args[0]);
            } else {
                player.sendMessage(miniMessage.deserialize("<red>I'm not sure what you're trying to do."));
            }
        }
        return true;
    }

    @EventHandler
    public void onChatMessage(AsyncChatEvent event) {
        if (activeSessions.containsKey(event.getPlayer())) {
            event.setCancelled(true);
            activeSessions.get(event.getPlayer()).parseData(event.message());
        }
    }

    private static class Session {
        Player player;
        int stage = 0;
        
        String key;
        Map<String, Object> data = new HashMap<>();

        Session(Player player) {
            this.player = player;
        }

        // Ask for, in order

        // Name of this playing field
        // reference point
        // Queue length
        // Field width
        // Field height
        // Standing distance from the playing field
        // Environment
        // Incoming direction
        // Field direction
        // Whether to hide the bottom border of walls
        // Wall material (hold item)
        // Player's building material (hold item)

        public void sendInstructions() {
            MiniMessage miniMessage = MiniMessage.miniMessage();
            player.sendMessage("");
            switch (stage) {
                case 0 -> {
                    player.sendMessage("You've activated the playing field registration wizard!");
                    player.sendMessage(miniMessage.deserialize("<yellow>To leave, run /registerplayingfield cancel"));
                    player.sendMessage(miniMessage.deserialize("<red>Playing field data is stored in the playingfields.yml file."));
                    player.sendMessage(miniMessage.deserialize("<dark_gray>enjoy the GitHub Copilot generated instructions lmao"));
                    player.sendMessage("");
                    player.sendMessage("Please input the name of this playing field.");
                }
                case 1 -> {
                    player.sendMessage("Place a block in the bottom left corner of the playing field, and look at it.");
                    player.sendMessage("While looking at it, run /registerplayingfield.");
                }
                case 2 -> {
                    player.sendMessage("Please enter the queue length of this playing field.");
                    player.sendMessage("The standard queue length is 20 blocks.");
                }
                case 3 -> {
                    player.sendMessage("Please enter the field width of this playing field.");
                    player.sendMessage("The standard field width is 7 blocks.");
                }
                case 4 -> {
                    player.sendMessage("Please enter the field height of this playing field.");
                    player.sendMessage("The standard field height is 4 blocks.");
                }
                case 5 -> {
                    player.sendMessage("Please enter the standing distance from the playing field.");
                    player.sendMessage("This is the maximum distance a player can stand from the playing field.");
                    player.sendMessage("The standard standing distance is 6 blocks.");
                }
                case 6 -> {
                    player.sendMessage("Please enter the incoming direction of this playing field (NORTH, SOUTH, EAST, WEST).");
                    player.sendMessage("This is the direction walls will be moving towards.");
                }
                case 7 -> {
                    player.sendMessage("Please enter the field direction of this playing field (NORTH, SOUTH, EAST, WEST).");
                    player.sendMessage("This is the direction parallel to the playing field, from the left side to the right side.");
                }
                case 8 -> player.sendMessage("Please enter whether to hide the bottom border of walls.");
                case 9 -> player.sendMessage("Please hold the wall block material in your hand and run /registerplayingfield.");
                case 10 -> player.sendMessage("Please hold the player's building block material in your hand and run /registerplayingfield.");
                case 11 -> player.sendMessage("Please hold the playing field border's block material in your hand and run /registerplayingfield.");
            }
        }

        public void onCommandRun(String arg) {
            MiniMessage miniMessage = MiniMessage.miniMessage();
            if (stage == 1) {
                parseData(player.getTargetBlock(null, 5).getLocation());
            } else if (stage == 9 || stage == 10 || stage == 11) {
                parseData(player.getInventory().getItemInMainHand().getType());
            } else if (arg.equalsIgnoreCase("cancel")) {
                activeSessions.remove(player);
                player.sendMessage(miniMessage.deserialize("<red>Cancelled playing field registration."));
            } else {
                player.sendMessage(miniMessage.deserialize("<red>I'm not sure what you're trying to do."));
                player.sendMessage("To leave, run /registerplayingfield cancel");
            }
        }

        // is this scuffed? probably
        private void parseData(Object data) {
            if (data instanceof TextComponent component) {
                data = component.content();
            }
            try {
                switch (stage) {
                    case 0 -> {
                        key = (String) data;
                        stage++;
                    }
                    case 1 -> {
                        this.data.put("location", data);
                        stage++;
                    }
                    case 2 -> {
                        String string = (String) data;
                        int integer = Integer.parseInt(string);
                        this.data.put("queue_length", integer);
                        stage++;
                    }
                    case 3 -> {
                        String string = (String) data;
                        int integer = Integer.parseInt(string);
                        this.data.put("field_length", integer);
                        stage++;
                    }
                    case 4 -> {
                        String string = (String) data;
                        int integer = Integer.parseInt(string);
                        this.data.put("field_height", integer);
                        stage++;
                    }
                    case 5 -> {
                        String string = (String) data;
                        int integer = Integer.parseInt(string);
                        this.data.put("standing_distance", integer);
                        stage++;
                    }
                    case 6 -> {
                        String direction = (String) data;
                        direction = direction.toUpperCase();
                        BlockFace.valueOf(direction);
                        this.data.put("incoming_direction", direction);
                        stage++;
                    }
                    case 7 -> {
                        String direction = (String) data;
                        direction = direction.toUpperCase();
                        BlockFace.valueOf(direction);
                        this.data.put("field_direction", direction);
                        stage++;
                    }
                    case 8 -> {
                        if (data instanceof String bool) {
                            if (bool.equalsIgnoreCase("true") || bool.equalsIgnoreCase("false")) {
                                this.data.put("hide_bottom_border", Boolean.parseBoolean(bool));
                                stage++;
                            } else {
                                throw new IllegalArgumentException("Not a boolean");
                            }
                        } else {
                            throw new IllegalArgumentException("Not a string");
                        }
                    }
                    case 9 -> {
                        if (data instanceof Material material) {
                            this.data.put("wall_material", material.toString());
                            stage++;
                        } else {
                            throw new IllegalArgumentException("Not a material");
                        }
                    }
                    case 10 -> {
                        if (data instanceof Material material) {
                            this.data.put("player_material", material.toString());
                            stage++;
                        } else {
                            throw new IllegalArgumentException("Not a material");
                        }
                    }
                    case 11 -> {
                        if (data instanceof Material material) {
                            this.data.put("border_material", material.toString());
                            stage++;
                        } else {
                            throw new IllegalArgumentException("Not a material");
                        }
                    }
                }

                if (stage == 12) {
                    player.sendMessage("All data collected! Writing to config...");
                    writeToConfig();
                    activeSessions.remove(player);
                } else {
                    sendInstructions();
                }

            } catch (ClassCastException | IllegalArgumentException e) {
                incorrectData();
            }
       
        }

        public void incorrectData() {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Wrong data type, try again?"));
            sendInstructions();
        }

        private void writeToConfig() {
            FileConfiguration config = FillInTheWallLite.getInstance().getPlayingFieldConfig();
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                config.set(key + "." + entry.getKey(), entry.getValue());
            }
            FillInTheWallLite.getInstance().savePlayingFieldConfig();
            FillInTheWallLite.getInstance().reload();
        }

    }
}
