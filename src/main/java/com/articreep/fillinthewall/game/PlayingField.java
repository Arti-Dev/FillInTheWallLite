package com.articreep.fillinthewall.game;

import com.articreep.fillinthewall.FillInTheWallLite;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.gamemode.GamemodeSettings;
import com.articreep.fillinthewall.menu.EndScreen;
import com.articreep.fillinthewall.menu.SandboxMenu;
import com.articreep.fillinthewall.menu.SelectMenu;
import com.articreep.fillinthewall.modifiers.ModifierEvent;
import com.articreep.fillinthewall.modifiers.Rush;
import com.articreep.fillinthewall.multiplayer.WallGenerator;
import com.articreep.fillinthewall.utils.Utils;
import com.articreep.fillinthewall.utils.WorldBoundingBox;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.javatuples.Pair;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.*;

public class PlayingField implements Listener {
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Set<Player> players = new HashSet<>();
    private final ArrayList<UUID> playerOrder = new ArrayList<>();
    private final HashMap<Player, GameMode> previousGamemodes = new HashMap<>();
    /**
     * Must be the bottom left corner of the playing field (NOT including the border blocks)
     * The location is situated in the CENTER of the target block when it is set by the constructor.
     */
    private final Location fieldReferencePoint;
    /** parallel to the field, pointing left relative to the field */
    private final Vector fieldDirection;
    /** normal to the field, pointing the direction incoming walls go */
    private final Vector incomingDirection; // normal to the field
    private final int height;
    private final int length;
    private final int standingDistance;
    /**
     * Amount of ticks to show wall results after clearing a wall
     */
    private final int clearDelay = 10;
    private boolean clearDelayActive = false;

    private final List<Block> borderBlocks = new ArrayList<>();
    private final Material defaultBorderMaterial;
    private final Material playerMaterial;
    /**
     * Used to (somewhat) certify that this item was issued by the server
     */
    public static final NamespacedKey gameKey = new NamespacedKey(FillInTheWallLite.getInstance(), "GAME_ITEM");
    public static final NamespacedKey variableKey = new NamespacedKey(FillInTheWallLite.getInstance(), "VARIABLE_ITEM");
    private final WorldBoundingBox boundingBox;

    private final int displaySlotsLength = 6;
    private final DisplayType[] displaySlots = new DisplayType[displaySlotsLength];
    private final TextDisplay[] textDisplays = new TextDisplay[displaySlotsLength];
    private final Set<DisplayType> displayOverrides = new HashSet<>();

    private PlayingFieldScorer scorer;
    private ModifierEvent event = null;

    private WallQueue queue;
    private final Material wallMaterial;
    private final boolean hideBottomBorder;
    private final boolean addBackBorder;

    /** The scorer and queue should be reset before each game starts. */
    private boolean resetRecently = false;

    private BukkitTask countdown = null;
    private BukkitTask task = null;
    private SelectMenu selectMenu = null;
    private EndScreen endScreen = null;
    private boolean confirmOnCooldown = false;

    private final HashMap<Block, BlockDisplay> incorrectBlockHighlights = new HashMap<>();

    public static final String DEFAULT_HOTBAR = "PVC______";
    private final Map<Player, ItemStack[]> savedInventories = new HashMap<>();

    private boolean infiniteReach = false;
    private static final NamespacedKey infiniteReachKey = new NamespacedKey(FillInTheWallLite.getInstance(), "infinite_reach");

    private boolean highlightIncorrectBlocks = false;

    public PlayingField(Location referencePoint, Vector direction, Vector incomingDirection, int standingDistance,
                        WorldBoundingBox boundingBox, int length, int height,
                        Material wallMaterial, Material playerMaterial, Material borderMaterial, boolean hideBottomBorder, boolean addBackBorder) {
        // define playing field in a very scuffed way
        this.fieldReferencePoint = Utils.centralizeLocation(referencePoint);
        this.fieldDirection = direction;
        this.incomingDirection = incomingDirection;
        if (!fieldDirection.isZero()) fieldDirection.normalize();
        if (!incomingDirection.isZero()) incomingDirection.normalize();
        this.scorer = new PlayingFieldScorer(this);
        this.queue = new WallQueue(this, wallMaterial, WallGenerator.defaultGenerator(length, height), hideBottomBorder, addBackBorder);
        this.boundingBox = boundingBox;
        this.playerMaterial = playerMaterial;
        this.height = height;
        this.length = length;
        this.hideBottomBorder = hideBottomBorder;
        this.addBackBorder = addBackBorder;
        this.wallMaterial = wallMaterial;
        this.standingDistance = standingDistance;
        this.defaultBorderMaterial = borderMaterial;
        setDefaultDisplaySlots();

        // Track border blocks
        for (int x = 0; x < length + 2; x++) {
            for (int y = 0; y < height + 1; y++) {
                Location loc = getReferencePoint().clone()
                        // Move to the block right to the left of the reference block
                        .subtract(fieldDirection)
                        .add(fieldDirection.clone().multiply(x))
                        .add(0, y, 0);
                // exclude corners
                if ((x == 0 || x == length + 1 || y == height) &&
                        !((x == 0 || x == length + 1) && y == height)) {
                    borderBlocks.add(loc.getBlock());
                }
            }
        }
    }

    public void createMenu() {
        if (players.isEmpty()) return;
        if (hasMenu()) removeMenu();
        if (hasEndScreen()) removeEndScreen();
        selectMenu = new SelectMenu(getCenter(true, false), this);
        selectMenu.display();
    }

    private static final Title.Times titleTimes = Title.Times.times(
            Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO);

    public void countdownStart(Gamemode mode) {
        countdown = new BukkitRunnable() {
            int i = 3;
            @Override
            public void run() {
                if (i == 3) {
                    sendTitleToPlayers(Title.title(miniMessage.deserialize("<green>③"), Component.empty(), titleTimes));
                    playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1);
                } else if (i == 2) {
                    sendTitleToPlayers(Title.title(miniMessage.deserialize("<yellow>②"), Component.empty(), titleTimes));
                    playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1);
                } else if (i == 1) {
                    sendTitleToPlayers(Title.title(miniMessage.deserialize("<red>①"), Component.empty(), titleTimes));
                    playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1);
                } else if (i == 0) {
                    // It's important that the countdown reference is removed before we start the game
                    // so that hasStarted() returns false
                    countdown = null;
                    start(mode);
                    Title.Times goTimes = Title.Times.times(
                            Duration.ZERO, Duration.ofMillis(250), Duration.ofMillis(150));
                    sendTitleToPlayers(Title.title(miniMessage.deserialize("<green>GO!"), Component.empty(), goTimes));
                    playSoundToPlayers(Sound.BLOCK_BELL_USE, 0.5f);
                    cancel();
                }
                i--;
            }
        }.runTaskTimer(FillInTheWallLite.getInstance(), 0, 10);
    }

    public void reset() {
        scorer = new PlayingFieldScorer(this);
        queue = new WallQueue(this, wallMaterial, WallGenerator.defaultGenerator(length, height), hideBottomBorder, addBackBorder);
        resetRecently = true;
    }

    // Running this method will create new scorer and queue objects
    public void start(Gamemode mode, GamemodeSettings settings) {
        // Log fail if this is already running
        if (hasStarted()) {
            FillInTheWallLite.getInstance().getSLF4JLogger().error("Tried to start game that's already been started");
            return;
        }
        if (players.isEmpty()) {
            throw new IllegalStateException("There are no players!");
        }
        if (mode == null) {
            throw new IllegalArgumentException("Gamemode cannot be null");
        }
        Bukkit.getPluginManager().registerEvents(this, FillInTheWallLite.getInstance());
        if (!resetRecently) reset();
        scorer.setGamemode(mode, settings);
        if (scorer.getSettings().getBooleanAttribute(GamemodeAttribute.INFINITE_BLOCK_REACH)) infiniteReach = true;
        if (scorer.getSettings().getBooleanAttribute(GamemodeAttribute.HIGHLIGHT_INCORRECT_BLOCKS)) highlightIncorrectBlocks = true;
        setDisplaySlots(settings);
        removeMenu();
        clearField();
        removeEndScreen();
        spawnTextDisplays();
        for (Player player : players) {
            addSpecialGamemodeItems(player);
            player.setGameMode(GameMode.CREATIVE);
            if (infiniteReach) giveInfiniteReach(player);
        }
        task = tickLoop();
    }

    public void start(Gamemode gamemode) {
        start(gamemode, gamemode.getDefaultSettings());
    }

    /**
     * Adds a player to the game.
     *
     * @param player Player to add
     */
    public void addPlayer(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        players.add(player);
        playerOrder.add(player.getUniqueId());
        player.setInvulnerable(true);
        player.setAllowFlight(true);
        previousGamemodes.put(player, player.getGameMode());
        formatInventory(player);
        Utils.addToNoCollisionScoreboard(player);
        if (!hasStarted() && !hasMenu()) {
            if (FillInTheWallLite.isSimpleMode()) {
                if (length * height >= 400) start(Gamemode.MEGA);
                else start(Gamemode.ENDLESS);
            } else {
                createMenu();
            }
        } else if (hasStarted()) {
            addSpecialGamemodeItems(player);
            player.setGameMode(GameMode.CREATIVE);
            if (infiniteReach) giveInfiniteReach(player);
        }

        // Register with the playing field manager
        PlayingFieldManager.activePlayingFields.put(player, this);

    }

    public int playerCount() {
        return players.size();
    }

    public void removePlayer(Player player) {
        // If this will be our last player, shut the game down
        if (playerCount() == 1) {
            if (hasStarted()) {
                stop();
            }
            else removeMenu();
        }

        players.remove(player);
        playerOrder.remove(player.getUniqueId());
        loadSavedInventory(player);
        // things not to do if the player was a spectator
        if (previousGamemodes.containsKey(player) && player.getGameMode() != GameMode.SPECTATOR) {
            GameMode previousGamemode = previousGamemodes.get(player);
            if (previousGamemode != null) player.setGameMode(previousGamemode);
            if (previousGamemode != GameMode.CREATIVE) player.setAllowFlight(false);
        }
        previousGamemodes.remove(player);
        removeInfiniteReach(player);
        player.setInvulnerable(false);
        Utils.removeFromNoCollisionScoreboard(player);

        // Remove from playing field manager
        PlayingFieldManager.activePlayingFields.remove(player);
    }

    public UUID getEarliestPlayerUUID() {
        if (players.isEmpty() || playerOrder.isEmpty()) return null;
        return playerOrder.getFirst();
    }

    public void saveInventory(Player player) {
        if (player == null || !players.contains(player)) return;
        if (savedInventories.containsKey(player)) return;
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] clonedContents = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] == null) clonedContents[i] = null;
            else clonedContents[i] = contents[i].clone();
        }
        savedInventories.put(player, clonedContents);
    }

    public void loadSavedInventory(Player player) {
        if (player == null || !savedInventories.containsKey(player)) return;
        ItemStack[] contents = savedInventories.get(player);
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] == null) {
                player.getInventory().setItem(i, new ItemStack(Material.AIR));
            } else {
                player.getInventory().setItem(i, contents[i].clone());
            }
        }
        player.setItemOnCursor(null);
        savedInventories.remove(player);
    }

    public void formatInventory(Player player) {
        saveInventory(player);
        player.getInventory().clear();
        player.setItemOnCursor(null);
        ItemStack supportBlock;
        supportBlock = copperSupportItem();

        for (int i = 0; i < PlayingField.DEFAULT_HOTBAR.length(); i++) {
            char c = PlayingField.DEFAULT_HOTBAR.charAt(i);
            if (c == 'P') {
                player.getInventory().setItem(i, buildingItem(playerMaterial));
            } else if (c == 'V') {
                player.getInventory().setItem(i, variableItem());
            } else if (c == 'C') {
                player.getInventory().setItem(i, supportBlock);
            } else {
                player.getInventory().setItem(i, new ItemStack(Material.AIR));
            }
        }
    }

    private void addSpecialGamemodeItems(Player player) {
        if (scorer.getGamemode() == Gamemode.SANDBOX) {
            player.getInventory().setItem(7, sandboxMenuItem());
            Bukkit.getScheduler().runTaskLater(FillInTheWallLite.getInstance(), () ->
                    sendTitleToPlayers(miniMessage.deserialize("<gradient:green:dark_green>Sandbox Mode"),
                            Component.text("Right click the nether star in your inventory to customize!"),
                            10, 80, 20), 40);
        }
    }

    private void giveInfiniteReach(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        attribute.addModifier(new AttributeModifier(infiniteReachKey, 64, AttributeModifier.Operation.ADD_NUMBER));
    }

    private void removeInfiniteReach(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        attribute.removeModifier(infiniteReachKey);
    }

    public void setInfiniteReach(boolean enabled) {
        infiniteReach = enabled;
        for (Player player : getPlayers()) {
            if (enabled) {
                giveInfiniteReach(player);
            } else {
                removeInfiniteReach(player);
            }
        }
    }

    public boolean infiniteReachEnabled() {
        return infiniteReach;
    }

    public void setHighlightIncorrectBlocks(boolean enabled) {
        highlightIncorrectBlocks = enabled;
        if (enabled) refreshIncorrectBlockHighlights(queue.getFrontmostWall()); // todo this doesn't work
        else {
            for (BlockDisplay display : incorrectBlockHighlights.values()) {
                display.remove();
            }
            incorrectBlockHighlights.clear();
        }
    }

    public boolean highlightIncorrectBlocksEnabled() {
        return highlightIncorrectBlocks;
    }

    public void stop(boolean submitFinalWall, boolean showEndScreen) {
        if (!hasStarted()) {
            return;
        }

        if (submitFinalWall) {
            queue.instantSend(true);
        }
        else clearField();

        task.cancel();
        task = null;
        for (TextDisplay display : textDisplays) {
            display.remove();
        }
        queue.clearAllWalls();
        queue.allowMultipleWalls(false);
        for (Player player : getPlayers()) {
            removeInfiniteReach(player);
        }
        infiniteReach = false;
        highlightIncorrectBlocks = false;
        if (event != null) {
            event.end();
            event = null;
        }
        scorer.announceFinalScore();
        if (showEndScreen) {
            endScreen = scorer.createEndScreen();
            endScreen.display();
            // I forgot if this is really necessary
            EndScreen thisSpecificEndScreen = endScreen;
            Bukkit.getScheduler().runTaskLater(FillInTheWallLite.getInstance(),
                    () -> despawnEndScreenAndSpawnMenu(thisSpecificEndScreen), 20 * 10);
        }

        resetRecently = false;

        HandlerList.unregisterAll(this);
    }

    private void despawnEndScreenAndSpawnMenu(EndScreen screen) {
        if (screen == null) return;
        if (!players.isEmpty() && !hasMenu() && !hasStarted()) {
            screen.despawn();
            createMenu();
        }
    }

    public void stop() {
        stop(true, true);
    }

    // Listeners
    @EventHandler
    public void onLeverFlick(PlayerInteractEvent event) {
        if (!players.contains(event.getPlayer())) return;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK &&
                event.getClickedBlock().getType() == Material.LEVER) {
            queue.instantSend();
        }
    }

    @EventHandler
    public void onSwitchToOffhand(PlayerSwapHandItemsEvent event) {
        if (!players.contains(event.getPlayer())) return;
        event.setCancelled(true);

        if (confirmOnCooldown) return;
        queue.instantSend();
        confirmOnCooldown = true;
        int pauseTime = this.clearDelay;
        if (eventActive()) pauseTime = this.event.clearDelayOverride;
        Bukkit.getScheduler().runTaskLater(FillInTheWallLite.getInstance(), () -> confirmOnCooldown = false, pauseTime);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!players.contains(event.getPlayer())) return;

        Player player = event.getPlayer();
        if (!isInField(event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendMessage(miniMessage.deserialize("<red>Can't place blocks here!"));
            return;
        } else {
            event.setCancelled(false);
        }

        if (highlightIncorrectBlocks) {
            Block block = event.getBlockPlaced();
            if (block.getType() != copperSupportItem().getType()) {
                Pair<Integer, Integer> coordinates = blockToCoordinates(block);
                if (queue.getFrontmostWall() != null && !queue.getFrontmostWall().getHoles().contains(coordinates)) {
                    addIncorrectBlockHighlight(block);
                }
            }
        }

        scorer.increaseBlocksPlaced();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!players.contains(event.getPlayer())) return;
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!isInField(block.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(miniMessage.deserialize("<red>Can't break blocks here!"));
        } else {
            event.setCancelled(false);
            if (incorrectBlockHighlights.containsKey(block)) {
                incorrectBlockHighlights.get(block).remove();
                incorrectBlockHighlights.remove(block);
            }
        }
    }

    @EventHandler
    public void onClick(PlayerInteractEvent event) {
        if (!players.contains(event.getPlayer())) return;
        Player player = event.getPlayer();
        // Player must click with an item in hand
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (!hasStarted()) event.setCancelled(true);
            if (event.getClickedBlock().getType() == Material.CRACKED_STONE_BRICKS) {
                Block clickedBlock = event.getClickedBlock();
                // Check to make sure the block placement wasn't an accident
                Location newBlock = clickedBlock.getLocation().add(event.getBlockFace().getDirection());
                if (isInField(newBlock)) {
                    // Despawn the cracked stone bricks
                    getWorld().spawnParticle(Particle.BLOCK,
                            clickedBlock.getLocation().add(0.5, 0.5, 0.5),
                            100, 0.5, 0.5, 0.5, 0.1,
                            Material.CRACKED_STONE_BRICKS.createBlockData());
                    Bukkit.getScheduler().runTask(FillInTheWallLite.getInstance(),
                            () -> clickedBlock.breakNaturally(new ItemStack(Material.LEAD)));
                    if (incorrectBlockHighlights.containsKey(clickedBlock)) {
                        incorrectBlockHighlights.get(clickedBlock).remove();
                        incorrectBlockHighlights.remove(clickedBlock);
                    }
                    player.playSound(player.getLocation(), Sound.BLOCK_DEEPSLATE_BREAK, 0.7f, 1);
                }
            }
        }

        if (item.getType() == Material.NETHER_STAR && scorer.getGamemode() == Gamemode.SANDBOX) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                    || event.getAction() == Action.LEFT_CLICK_BLOCK
                    || event.getAction() == Action.RIGHT_CLICK_AIR
                    || event.getAction() == Action.LEFT_CLICK_AIR) {
                event.setCancelled(true);
                SandboxMenu.sandboxInventory(event.getPlayer(), this);
            }
        }
    }

    public void refreshIncorrectBlockHighlights(Wall wall) {
        for (BlockDisplay display : incorrectBlockHighlights.values()) {
            display.remove();
        }
        incorrectBlockHighlights.clear();
        if (!scorer.getSettings().getBooleanAttribute(GamemodeAttribute.HIGHLIGHT_INCORRECT_BLOCKS)) return;
        if (clearDelayActive) return;


        for (Pair<Integer, Integer> coordinates : getPlayingFieldBlocks(false).keySet()) {
            if (!wall.getHoles().contains(coordinates) && coordinatesToBlock(coordinates).getType() != copperSupportItem().getType()) {
                addIncorrectBlockHighlight(coordinatesToBlock(coordinates));
            }
        }
    }

    /**
     * Returns all blocks in the playing field excluding air blocks and copper support blocks (which don't count)
     * @return all blocks in coordinate to block map
     */
    public Map<Pair<Integer, Integer>, Block> getPlayingFieldBlocks(boolean includeCopperSupports) {
        HashMap<Pair<Integer, Integer>, Block> blocks = new HashMap<>();
        // y direction loop
        for (int y = 0; y < height; y++) {
            Location loc = getReferencePoint().add(0, y, 0);
            for (int x = 0; x < length; x++) {
                if (!loc.getBlock().isEmpty()) {
                    if (includeCopperSupports || loc.getBlock().getType() != copperSupportItem().getType()) {
                        blocks.put(Pair.with(x, y), loc.getBlock());
                    }
                }
                loc.add(fieldDirection);
            }
        }
        return blocks;
    }

    // todo add the option to get this reference point in the center of the block or in the natural corner
    /**
     * Returns the corner of the playing field (NOT including the border blocks)
     * The location is situated in the CENTER of the target block.
     */
    public Location getReferencePoint() {
        return fieldReferencePoint.clone();
    }

    public Vector getIncomingDirection() {
        return incomingDirection.clone();
    }

    public Vector getFieldDirection() {
        return fieldDirection.clone();
    }

    public Block coordinatesToBlock(Pair<Integer, Integer> coordinates) {
        return fieldReferencePoint.clone().add(fieldDirection.clone()
                        .multiply(coordinates.getValue0())).add(0, coordinates.getValue1(), 0)
                .getBlock();

    }

    // Generated with GitHub Copilot, then adjusted
    public Pair<Integer, Integer> blockToCoordinates(Block block) {
        Vector relative = block.getLocation().subtract(getReferencePoint().subtract(0.5, 0.5, 0.5)).toVector();
        int x = (int) relative.dot(fieldDirection);
        int y = (int) relative.getY();
        return Pair.with(x, y);
    }

    public Set<Player> getPlayers() {
        return players;
    }

    public WallQueue getQueue() {
        return queue;
    }

    /**
     * Matches the blocks in the playing field with the blocks in the wall and scores the player
     * @param wall Wall to check against
     */
    public void matchAndScore(Wall wall) {
        // An event may want to do something other than override scoring
        if (eventActive()) {
            event.onWallScore(wall);
        }

        // Events can override scoring
        if (!eventActive() || !event.overrideCompleteScoring) {
            Judgement judgement = scorer.scoreWall(wall, this);
            changeBorderBlocks(judgement.getBorder());
        } else {
            event.score(wall);
        }

        // Spawn copper break particles - we're not actually breaking them, they're just getting replaced
        for (Block block : getPlayingFieldBlocks(true).values()) {
            if (block.getType() == copperSupportItem().getType()) {
                getWorld().spawnParticle(Particle.BLOCK,
                        block.getLocation().add(0.5, 0.5, 0.5),
                        100, 0.5, 0.5, 0.5, 0.1,
                        copperSupportItem().getType().createBlockData());
            }
        }

        int pauseTime = this.clearDelay;
        if (eventActive() && event.clearDelayOverride > 0) {
            pauseTime = event.clearDelayOverride;
        }

        clearDelayActive = true;
        if (eventActive() && event.overrideCorrectBlocksVisual) {
            event.correctBlocksVisual(wall);
        } else {
            correctBlocksVisual(wall);
        }

        // Clear the field after the pauseTime
        Bukkit.getScheduler().runTaskLater(FillInTheWallLite.getInstance(), () -> {
            if (eventActive() && event.fillFieldAfterSubmission) fillField(playerMaterial);
            else clearField();

            resetBorder();
            clearDelayActive = false;

            if (eventActive()) {
                if (event.getTicksRemaining() <= 0) {
                    endEvent();
                }
            }

            // Rush jank
            // todo might move to rush class
            if (eventActive() && event instanceof Rush rush) {
                if (rush.hasFirstWallCleared()) {
                    for (Pair<Integer, Integer> hole : wall.getHoles()) {
                        coordinatesToBlock(hole).setType(playerMaterial);
                    }
                } else {
                    rush.setFirstWallCleared(true);
                }
            }
        }, pauseTime);

        if (scorer.getSettings().getIntAttribute(GamemodeAttribute.PERFECT_WALL_CAP) > 0) {
            if (scorer.getPerfectWallsCleared() >= scorer.getSettings().getIntAttribute(GamemodeAttribute.PERFECT_WALL_CAP)) {
                scorer.playGameEnd();
                stop(false, true);
            }
        }
    }

    // Block-related methods

    /**
     * Fills the playing field with the given material
     * @param material the material to fill the field with
     */
    public void fillField(Material material) {
        for (int x = 0; x < length; x++) {
            for (int y = 0; y < height; y++) {
                Location loc = getReferencePoint().clone().add(fieldDirection.clone().multiply(x)).add(0, y, 0);
                loc.getBlock().setType(material);
            }
        }
    }

    public void correctBlocksVisual(Wall wall) {
        Map<Pair<Integer, Integer>, Block> extraBlocks = wall.getExtraBlocks(this);
        Map<Pair<Integer, Integer>, Block> correctBlocks = wall.getCorrectBlocks(this);
        Map<Pair<Integer, Integer>, Block> missingBlocks = wall.getMissingBlocks(this);

        // Visually display what blocks were correct and what were wrong
        fillField(wall.getMaterial());
        for (Block block : extraBlocks.values()) {
            block.setType(Material.RED_WOOL);
        }
        for (Block block : correctBlocks.values()) {
            block.setType(Material.GREEN_WOOL);
        }
        for (Block block : missingBlocks.values()) {
            block.setType(Material.AIR);
        }
    }

    public boolean isInField(Location location) {
        // decentralize the reference point for this
        Location bottomLeft = getReferencePoint().subtract(0.5, 0.5, 0.5);
        Location topRight = bottomLeft.clone()
                .add(fieldDirection.clone().multiply(length-0.5))
                .add(new Vector(0, height-0.5, 0));
        // haha copilot go brr
        return (Utils.withinBounds(bottomLeft.getX(), topRight.getX(), location.getX()) &&
                Utils.withinBounds(bottomLeft.getY(), topRight.getY(), location.getY()) &&
                Utils.withinBounds(bottomLeft.getZ(), topRight.getZ(), location.getZ()));
    }

    public void clearField() {
        fillField(Material.AIR);
        for (BlockDisplay display : incorrectBlockHighlights.values()) {
            display.remove();
        }
        incorrectBlockHighlights.clear();
    }

    public void changeBorderBlocks(Material material) {
        for (Block block : borderBlocks) {
            block.setType(material);
        }
    }

    public void critParticles() {
        for (Block block : borderBlocks) {
            block.getWorld().spawnParticle(Particle.CRIT, block.getLocation(), 7, 0.5, 0.5, 0.5, 0.1);
        }
    }

    public void resetBorder() {
        changeBorderBlocks(defaultBorderMaterial);
    }

    // Events, ticking, and effects

    public BukkitTask tickLoop() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                updateTextDisplays();

                if (eventActive() && event.actionBarOverride() != null) {
                    sendActionBarToPlayers(event.actionBarOverride());
                } else {
                    actionBar();
                }
                queue.tick();
                scorer.tick();
                if (eventActive()) {
                    event.tick();
                }
            }
        }.runTaskTimer(FillInTheWallLite.getInstance(), 0, 1);
    }

    public void actionBar() {
        PlayingFieldScorer.ActionBarType type = scorer.getSettings().getActionBarTypeAttribute(GamemodeAttribute.ACTIONBAR_DISPLAY);
        switch (type) {
            case LEVEL_PROGRESS -> sendActionBarToPlayers(scorer.getLevelProgressActionbar());
            case ENDLESS_LEVEL_PROGRESS -> sendActionBarToPlayers(scorer.getEndlessLevelProgressActionbar());
            case PERFECT_WALLS -> sendActionBarToPlayers(scorer.getPerfectWallsActionbar());
        }
    }

    // Generally, don't use this method: create an event and then use its built-in activate()
    // bad code design moment
    public void setEvent(ModifierEvent newEvent) {
        if (event != null && newEvent != null) {
            if (newEvent.shelveEvent) {
                newEvent.setShelvedEvent(event);
                event.end();
            } else {
                endEvent();
            }
        }
        this.event = newEvent;
    }

    public void endEvent() {
        if (event == null) return;
        event.end();
        if (event.shelveEvent) {
            // todo this line prevents infinite recursion - kind of scary
            event = event.getShelvedEvent();

            if (event != null) {
                event.activate();
            }
        } else {
            event = null;
        }
        if (scorer.getSettings().getIntAttribute(GamemodeAttribute.MODIFIER_EVENT_CAP) > 0) {
            if (scorer.getEventCount() >= (int) scorer.getSettings().getAttribute(GamemodeAttribute.MODIFIER_EVENT_CAP)) {
                scorer.playGameEnd();
                stop();
            }
        }
    }

    public void setDefaultDisplaySlots() {
        displaySlots[0] = DisplayType.TIME;
        displaySlots[1] = DisplayType.PERFECT_WALLS;
        displaySlots[2] = DisplayType.LEVEL;
        displaySlots[3] = DisplayType.SCORE;
        displaySlots[4] = DisplayType.NAME;
        displaySlots[5] = DisplayType.GAMEMODE;
    }

    public void setDisplaySlots(GamemodeSettings settings) {
        displaySlots[0] = settings.getDisplayTypeAttribute(GamemodeAttribute.DISPLAY_SLOT_0);
        displaySlots[1] = settings.getDisplayTypeAttribute(GamemodeAttribute.DISPLAY_SLOT_1);
        displaySlots[2] = settings.getDisplayTypeAttribute(GamemodeAttribute.DISPLAY_SLOT_2);
        displaySlots[3] = settings.getDisplayTypeAttribute(GamemodeAttribute.DISPLAY_SLOT_3);
    }

    public void spawnTextDisplays() {
        Location slot0 = getReferencePoint().subtract(fieldDirection.clone().multiply(1.5))
                .subtract(incomingDirection.clone().multiply(3))
                .add(new Vector(0, 1.5, 0));
        Location slot1 = slot0.clone().subtract(new Vector(0, 1, 0));
        Location slot2 = slot0.clone().add(fieldDirection.clone().multiply(length + 2));
        Location slot3 = slot2.clone().subtract(new Vector(0, 1, 0));
        Location slot4 = getCenter()
                .add(new Vector(0, 1, 0).multiply(height/2 + 3));
        Location slot5 = slot4.clone().subtract(new Vector(0, 1, 0).multiply(0.5));

        for (int i = 0; i < displaySlotsLength; i++) {
            float size = switch (i) {
                case 4 -> 3f;
                case 5 -> 1f;
                default -> 1.5f;
            };

            Location loc = switch (i) {
                case 0 -> slot0;
                case 1 -> slot1;
                case 2 -> slot2;
                case 3 -> slot3;
                case 4 -> slot4;
                case 5 -> slot5;
                default -> throw new IllegalStateException("Unexpected value: " + i);
            };

            textDisplays[i] = (TextDisplay) slot1.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
            textDisplays[i].setBillboard(Display.Billboard.CENTER);
            textDisplays[i].setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(size, size, size),
                    new AxisAngle4f(0, 0, 0, 1)));
            textDisplays[i].text(miniMessage.deserialize("<gray>Loading..."));
        }
    }

    public void updateTextDisplays() {
        // todo in theory we don't need to tick the gamemode/name displays, but we can for now
        for (int i = 0; i < displaySlotsLength; i++) {
            Component component = null;
            ArrayList<Component> compArray = new ArrayList<>();
            DisplayType type = displaySlots[i];
            if (displayOverrides.contains(type)) continue;
            switch (type) {
                case NONE -> component = Component.empty();
                case SCORE -> component = Component.text(scorer.getScore());
                case ACCURACY -> component = Component.text("null");
                case SPEED -> component = Component.text(scorer.getFormattedBlocksPerSecond());
                case PERFECT_WALLS -> {
                    compArray.add(Component.text(scorer.getPerfectWallsCleared()));
                    if (scorer.getSettings().getIntAttribute(GamemodeAttribute.PERFECT_WALL_CAP) > 0) {
                        compArray.add(miniMessage.deserialize(
                                "/" + scorer.getSettings().getAttribute(GamemodeAttribute.PERFECT_WALL_CAP)));
                    } else {
                        compArray.add(Component.empty());
                    }
                }
                case TIME -> component = scorer.getFormattedTime();
                case LEVEL -> component = Component.text(scorer.getLevel());
                case NAME -> component = Component.text(Utils.playersToString(players));
                case GAMEMODE -> component = scorer.getGamemode().getTitle();
                case EVENTS -> {
                    compArray.add(Component.text(scorer.getEventCount()));
                    if (scorer.getSettings().getIntAttribute(GamemodeAttribute.MODIFIER_EVENT_CAP) > 0) {
                        compArray.add(miniMessage.deserialize(
                                "/" + scorer.getSettings().getAttribute(GamemodeAttribute.MODIFIER_EVENT_CAP)));
                    } else {
                        compArray.add(Component.empty());
                    }
                }
                case SCORE_TO_NEXT_LEVEL -> component = Component.text(scorer.getScoreToNextLevel());
            }
            if (!compArray.isEmpty()) {
                textDisplays[i].text(type.getFormattedText(compArray));
            } else {
                textDisplays[i].text(type.getFormattedText(component));
            }
        }
    }

    public PlayingFieldScorer getScorer() {
        return scorer;
    }

    public ModifierEvent getEvent() {
        return event;
    }

    public boolean eventActive() {
        if (event == null) return false;
        return event.isActive();
    }

    /**
     * Overrides specific displays with a custom message. This message will be held for the specified amount of ticks.
     * You are free to change what is displayed using PlayingField#modifyOverridenDisplayText during this time.
     * @param type DisplayType to affect
     * @param ticks Amount of ticks to override
     * @param message Message to display
     */
    public void overrideDisplay(DisplayType type, int ticks, Component message) {
        displayOverrides.add(type);
        for (int i = 0; i < displaySlotsLength; i++) {
            if (displaySlots[i] == type) {
                textDisplays[i].text(message);
            }
        }
        Bukkit.getScheduler().runTaskLater(FillInTheWallLite.getInstance(), () -> displayOverrides.remove(type), ticks);
    }

    /**
     * To be used in conjunction with PlayingField#overrideDisplay
     * If the type is not currently being overriden, this method will immediately return
     * @param type DisplayType to affect
     * @param message Message to display
     */
    public void modifyOverridenDisplayText(DisplayType type, Component message) {
        if (!displayOverrides.contains(type)) return;
        for (int i = 0; i < displaySlotsLength; i++) {
            if (displaySlots[i] == type) {
                textDisplays[i].text(message);
            }
        }
    }

    public WorldBoundingBox getBoundingBox() {
        return boundingBox;
    }

    public boolean hasStarted() {
        return task != null || countdown != null;
    }

    public int getLength() {
        return length;
    }

    public int getHeight() {
        return height;
    }

    public int getClearDelay() {
        return clearDelay;
    }

    public void removeMenu() {
        if (selectMenu != null) this.selectMenu.despawn();
        this.selectMenu = null;
    }

    public void forceRemoveMenu() {
        if (selectMenu != null) this.selectMenu.despawn(true);
        this.selectMenu = null;
    }

    public boolean hasMenu() {
        return selectMenu != null;
    }

    public void removeEndScreen() {
        if (endScreen != null) this.endScreen.despawn();
        this.endScreen = null;
    }

    public boolean hasEndScreen() {
        return endScreen != null;
    }

    /**
     * Returns a location that is the center of the playing field, either on the horizontal axis (on the bottom), vertical axis (on the left side), or both.
     * @return The center location
     */
    public Location getCenter(boolean alongLength, boolean alongHeight) {
        Location location = getReferencePoint();
        if (alongLength) {
            // There's a -1 on the length because the reference point is in the center of the target block.
            location.add(fieldDirection.clone().multiply((double) (length - 1) / 2));
        }
        if (alongHeight) {
            location.add(new Vector(0, 1, 0).multiply((double) (height - 1) / 2));
        }
        return location;
    }

    public Location getCenter() {
        return getCenter(true, true);
    }

    public static ItemStack buildingItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.lore(Collections.singletonList(miniMessage.deserialize(
                "<gray>Fill the holes in the incoming wall with this!")));
        meta.getPersistentDataContainer().set(gameKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack copperSupportItem() {
        ItemStack item = new ItemStack(Material.WAXED_COPPER_GRATE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(miniMessage.deserialize("<color:#9A5F4A>Copper Support Block"));
        meta.lore(Arrays.asList(miniMessage.deserialize("<gray>- place this block on the field"),
                miniMessage.deserialize("<gray>- block breaks right before wall is submitted"),
                miniMessage.deserialize("<yellow><bold>- ???"),
                miniMessage.deserialize("<aqua>- no left clicks required")));
        meta.getPersistentDataContainer().set(gameKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack variableItem() {
        ItemStack item = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Variable Item"));
        meta.lore(Arrays.asList(miniMessage.deserialize("<gray>This is replaced with special items during specific events!"),
                miniMessage.deserialize("<dark_gray>Feel free to move this anywhere in your inventory!")));
        meta.getPersistentDataContainer().set(variableKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(gameKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;

    }

    public static ItemStack sandboxMenuItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(miniMessage.deserialize("<!italic><gradient:green:dark_green>Sandbox Settings"));
        meta.lore(List.of(miniMessage.deserialize("<!italic><gray>Right click to open!")));
        meta.getPersistentDataContainer().set(gameKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public void sendMessageToPlayers(String message) {
        for (Player player : players) {
            player.sendMessage(message);
        }
    }

    public void sendMessageToPlayers(Component message) {
        for (Player player : players) {
            player.sendMessage(message);
        }
    }

    public void sendTitleToPlayers(Title title) {
        for (Player player : players) {
            player.showTitle(title);
        }
    }

    /**
     * Sends a title to all players in the playing field
     * @param title Title
     * @param subtitle Subtitle
     * @param fadeIn Fadein time (in ticks)
     * @param stay Stay time (in ticks)
     * @param fadeOut Fadeout time (in ticks)
     */
    public void sendTitleToPlayers(Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        for (Player player : players) {
            player.showTitle(Title.title(title, subtitle, Title.Times.times(
                    Duration.ofMillis(fadeIn * 50L),
                    Duration.ofMillis(stay * 50L),
                    Duration.ofMillis(fadeOut * 50L))));
        }
    }

    public void sendActionBarToPlayers(Component component) {
        for (Player player : players) {
            player.sendActionBar(component);
        }
    }

    public void playSoundToPlayers(Sound sound, float pitch) {
        for (Player player : players) {
            player.playSound(player.getLocation(), sound, 1, pitch);
        }
    }

    public void playSoundToPlayers(Sound sound, float volume, float pitch) {
        for (Player player : players) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    public World getWorld() {
        return getReferencePoint().getWorld();
    }

    public Material getPlayerMaterial() {
        return playerMaterial;
    }

    public void flashScore(int ticks) {
        overrideDisplay(DisplayType.SCORE, ticks, Component.empty());
        final int rate = 4;
        new BukkitRunnable() {
            int elapsed = 0;
            int cover = 0;
            int flash = 5;
            final TextColor primary = NamedTextColor.AQUA;
            final TextColor accent = NamedTextColor.DARK_AQUA;
            @Override
            public void run() {
                if (elapsed >= ticks) {
                    cancel();
                    return;
                }
                Component component = DisplayType.SCORE.getFormattedText(Component.text(scorer.getScore()));
                String text = ((net.kyori.adventure.text.TextComponent) component).content();
                Component formattedComponent = flashTextFormat(text, cover, flash, primary, accent);
                modifyOverridenDisplayText(DisplayType.SCORE, formattedComponent);

                if (flash == 0) cover++;
                else flash--;
                if (cover >= text.length() - 1) {
                    flash = 5;
                    cover = 0;
                }
                elapsed += rate;
            }
        }.runTaskTimer(FillInTheWallLite.getInstance(), 0, rate);
    }

    public void flashLevel(int ticks) {
        overrideDisplay(DisplayType.LEVEL, ticks, Component.empty());
        final int rate = 4;
        new BukkitRunnable() {
            int elapsed = 0;
            int cover = 0;
            int flash = 5;
            final TextColor primary = NamedTextColor.AQUA;
            final TextColor accent = NamedTextColor.DARK_AQUA;
            @Override
            public void run() {
                if (elapsed >= ticks) {
                    cancel();
                    return;
                }
                Component component = DisplayType.LEVEL.getFormattedText(Component.text(scorer.getLevel()));
                String text = ((net.kyori.adventure.text.TextComponent) component).content();
                Component formattedComponent = flashTextFormat(text, cover, flash, primary, accent);
                modifyOverridenDisplayText(DisplayType.LEVEL, formattedComponent);

                if (flash == 0) cover++;
                else flash--;
                if (cover >= text.length()) {
                    flash = 5;
                    cover = 0;
                }
                elapsed += rate;
            }
        }.runTaskTimer(FillInTheWallLite.getInstance(), 10, rate);
    }

    private Component flashTextFormat(String text, int cover, int flash, TextColor primary, TextColor accent) {
        Component finalText;
        // Flash takes priority
        if (flash > 0) {
            if (flash % 2 == 0) finalText = Component.text(text, accent);
            else finalText = miniMessage.deserialize("<" + primary + "><bold>" + text);
        } else {
            cover = Integer.min(cover, text.length() - 1);
            String front = text.substring(0, cover);
            String back = text.substring(cover);
            finalText = miniMessage.deserialize("<" + primary + ">" + front + "<" + accent + ">" + back);
        }
        return finalText;
    }

    public Material getWallMaterial() {
        return wallMaterial;
    }

    public int getStandingDistance() {
        return standingDistance;
    }

    public Location getSpawnLocation() {
        Location spawn = getReferencePoint();
        spawn.subtract(0, 0.5, 0);
        spawn.add(getFieldDirection()
                .multiply(getLength() / 2.0 - 0.5));
        spawn.add(getIncomingDirection().multiply(getStandingDistance() / 2.0));
        spawn.setDirection(getIncomingDirection().multiply(-1));
        return spawn;
    }

    public void addIncorrectBlockHighlight(Block block) {
        BlockDisplay display = (BlockDisplay) getWorld().spawnEntity(block.getLocation(), EntityType.BLOCK_DISPLAY);
        display.setBlock(playerMaterial.createBlockData());
        display.setGlowing(true);
        display.setGlowColorOverride(Color.RED);
        incorrectBlockHighlights.put(block, display);

        // remove after 10 seconds
        Bukkit.getScheduler().runTaskLater(FillInTheWallLite.getInstance(), () -> {
            display.remove();
            incorrectBlockHighlights.remove(block);
        }, 20*10);
    }

    public boolean isClearDelayActive() {
        return clearDelayActive;
    }

    public DisplayType[] getDisplaySlots() {
        return Arrays.copyOf(displaySlots, displaySlotsLength);
    }

    public void setDisplaySlot(int index, DisplayType displayType) {
        if (displayType == null) return;
        if (index < 0 || index >= displaySlotsLength) {
            throw new IndexOutOfBoundsException("Display slot index out of bounds: " + index);
        }

        displaySlots[index] = displayType;
    }
}
