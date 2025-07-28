package com.articreep.fillinthewall.menu;

import com.articreep.fillinthewall.*;
import com.articreep.fillinthewall.game.PlayingField;
import com.articreep.fillinthewall.gamemode.Gamemode;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;

public class SelectMenu implements Listener {
    private final Location location;
    private TextDisplay title;
    private BlockDisplay block;
    private final float blockScale = 0.5f;
    private TextDisplay description;
    private TextDisplay controls;

    private final PlayingField field;
    private int gamemodeIndex = 0;
    private BukkitTask particleTask;
    private final static MiniMessage miniMessage = MiniMessage.miniMessage();
    private BukkitTask spinTask = null;

    public SelectMenu(Location location, PlayingField field) {
        this.location = location;
        this.field = field;
    }

    public void display() {
        // todo fine-tune and maybe generalize it for small playing fields
        title = (TextDisplay) location.getWorld().spawnEntity(location.clone().add(0, 1.5, 0), EntityType.TEXT_DISPLAY);
        block = (BlockDisplay) location.getWorld().spawnEntity(location.clone().add(0, 2.5, 0), EntityType.BLOCK_DISPLAY);
        block.setTransformation(new Transformation(
                new Vector3f(-blockScale/2f, -blockScale/2f, -blockScale/2f),
                new AxisAngle4f(0, 0, 0, 1), new Vector3f(blockScale, blockScale, blockScale),
                new AxisAngle4f(0, 0, 0, 1)));
        block.setInterpolationDuration(1);
        description = (TextDisplay) location.getWorld().spawnEntity(location.clone().add(0, 0.5, 0), EntityType.TEXT_DISPLAY);
        controls = (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        controls.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1), new Vector3f(0.5f, 0.5f, 0.5f),
                new AxisAngle4f(0, 0, 0, 1)));


        if (field.getHeight() * field.getLength() >= 400) {
            for (int i = 0; i < Gamemode.values().length; i++) {
                if (Gamemode.values()[i] == Gamemode.MEGA) {
                    gamemodeIndex = i;
                    break;
                }
            }
        }
        title.setBillboard(Display.Billboard.CENTER);
        description.setBillboard(Display.Billboard.CENTER);
        controls.setBillboard(Display.Billboard.CENTER);
        updateMenu(Gamemode.values()[gamemodeIndex]);
        Bukkit.getPluginManager().registerEvents(this, FillInTheWallLite.getInstance());
        particleTask = createParticleTask();
        spinTask = createSpinTask();
    }

    private BukkitTask createParticleTask() {
        return Bukkit.getScheduler().runTaskTimer(FillInTheWallLite.getInstance(), () -> {
            Player player = Bukkit.getPlayer(field.getEarliestPlayerUUID());
            if (player != null) {
                World world = player.getWorld();
                Color color = Color.fromRGB((int) (Math.random() * 255), (int) (Math.random() * 255), (int) (Math.random() * 255));
                world.spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 2,
                        0.5, 1, 0.5, 0.1, new Particle.DustOptions(color, 1F));
            }
        }, 0, 5);
    }

    private BukkitTask createSpinTask() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                Location loc = block.getLocation();
                loc.setYaw(loc.getYaw() + 10);
                block.teleport(loc);
            }
        }.runTaskTimer(FillInTheWallLite.getInstance(), 0, 1);
    }

    @EventHandler
    public void onPlayerClick(PlayerInteractEvent event) {
        UUID controller = field.getEarliestPlayerUUID();
        if (controller == null || !controller.equals(event.getPlayer().getUniqueId())) return;
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            nextGamemode();
        } else if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            previousGamemode();
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK &&
                event.getClickedBlock().getType() == Material.LEVER) {
            confirmAndDespawn();
        }
    }

    @EventHandler
    public void onPlayerSwap(PlayerSwapHandItemsEvent event) {
        if (!field.getPlayers().contains(event.getPlayer())) return;
        event.setCancelled(true);
        // Confirm gamemode
        confirmAndDespawn();
    }

    private void nextGamemode() {
        gamemodeIndex++;
        if (gamemodeIndex >= Gamemode.values().length) {
            gamemodeIndex = 0;
        }
        updateMenu(Gamemode.values()[gamemodeIndex]);
    }

    private void previousGamemode() {
        gamemodeIndex--;
        if (gamemodeIndex < 0) gamemodeIndex = Gamemode.values().length - 1;
        updateMenu(Gamemode.values()[gamemodeIndex]);
    }

    private void updateMenu(Gamemode mode) {
        String string = "<white><shadow:dark_gray:1>Select a gamemode</shadow>\n" +
                miniMessage.serialize(mode.getTitle());
        String descriptionString = miniMessage.serialize(mode.getDescription());

        controls.text(miniMessage.deserialize("<gray><key:key.mouse.left>/<key:key.mouse.right> to change gamemode\n" +
                "Press <key:key.swapOffhand> to start game"));
        title.text(miniMessage.deserialize(string));
        description.text(miniMessage.deserialize(descriptionString));
        Material blockMaterial = mode.getBlock();
        if (!blockMaterial.isBlock()) blockMaterial = Material.STONE_BUTTON;
        block.setBlock(blockMaterial.createBlockData());
    }

    public void confirmAndDespawn() {
        Gamemode mode = Gamemode.values()[gamemodeIndex];
        if (mode == Gamemode.MEGA && field.getLength() * field.getHeight() < 400) {
            field.sendMessageToPlayers(miniMessage.deserialize("<red>Your board must be at least 400 blocks in total area to play this!"));
        } else {
            field.countdownStart(Gamemode.values()[gamemodeIndex]);
        }
        despawn();
    }

    public void despawn(boolean force) {
        HandlerList.unregisterAll(this);
        if (title != null) title.remove();
        if (block != null && !force) {
            new BukkitRunnable() {
                float scale = blockScale;
                @Override
                public void run() {
                    scale -= 0.05f;
                    if (scale <= 0) {
                        block.remove();
                        this.cancel();
                        return;
                    }
                    block.setTransformation(new Transformation(
                            new Vector3f(-scale/2f, -scale/2f, -scale/2f),
                            new AxisAngle4f(0, 0, 0, 1), new Vector3f(scale, scale, scale),
                            new AxisAngle4f(0, 0, 0, 1)));
                }
            }.runTaskTimer(FillInTheWallLite.getInstance(), 0, 1);
        } else if (block != null) {
            block.remove();
        }
        if (description != null) description.remove();
        if (controls != null) controls.remove();
        if (particleTask != null) {
            particleTask.cancel();
        }
        if (spinTask != null) {
            spinTask.cancel();
        }
    }

    public void despawn() {
        despawn(false);
    }
}
