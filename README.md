# Fill in the Wall Lite

A modern remake of Hypixel's Hole in the Wall minigame

This is a stripped-down version of the [full server](https://github.com/Arti-Dev/FillInTheWall), designed as a hub attraction for any Minecraft server!

[Technical Writeup/Blog Post](https://arti-dev.github.io/2024/08/08/fillinthewall.html)

Originally for [HooHacks 2024](https://devpost.com/software/hole-in-the-wall-rush)

# Video
[![Fill in the Wall Video](https://img.youtube.com/vi/ARJ5J_cZsdk/0.jpg)](https://www.youtube.com/watch?v=ARJ5J_cZsdk)

# Installation/Setup

*At the time of writing this, this plugin is for 1.21.7 Paper servers.*

Simply download the latest release and place it in your server's `plugins` folder.

On its own, the plugin won't do anything. You need to define a playing field first using `/registerplayingfield`.
Follow the instructions in chat. Alternatively, you can copy and adapt the example playing field data in `playingfields.yml`.

<img width="2880" height="1800" alt="471157398-4ae7943e-564d-4cbf-8627-6b87541bda03" src="https://github.com/user-attachments/assets/bb657229-88ff-44ec-a59b-29df1473639e" />

Once that's complete, you can walk up to the playing field and a menu to start the game should appear.


# Config

`simple_mode`: If enabled, the gamemode selection menu will be skipped and either an Endless game or Mega game
will start, depending on the playing field's total area.

# Commands

- `/registerplayingfield`: Starts a wizard to register a playing field. You can leave with `/registerplayingfield cancel`.
- `/fitw reload`: Stops all running games and reloads the plugin and config.
- `/fitw custom`: Clears all queued walls and replaces them with the provided custom wall bundle
- `/fitw toggle`: Toggles playing fields on and off. Can be helpful if you're trying to build around a playing field.
- `/fitw hotbar`: Restores the default hotbar items while in a game.

# Custom Walls

Custom walls are grouped into "wall bundles". You can find a few examples in the `custom` folder `(plugins/FillInTheWall/custom)`.

You can load these in the Sandbox gamemode with `/fitw custom <bundle_name>`.

There are two components to a wall bundle YAML file:
- The dimensions of each wall
  - Length
  - Height
- The list of walls
  - Wall name
    - Wall holes
    - Time override (in ticks)

The hole formatting are just numbers separated by commas. In a way, these are just ordered pairs, except without any parentheses around them.
For example, the wall with holes shaped like an Among Us character has the holes: `2,0,4,0,1,1,2,1,3,1,4,1,1,2,2,2,2,3,3,3,4,3`
